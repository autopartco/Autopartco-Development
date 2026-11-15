package com.autopartco.backend.core.search;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.autopartco.backend.core.catalog.ListingQueries;
import com.autopartco.backend.core.catalog.ProductResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Mantiene {@code search_listings}: una fila por listado publicado. Qué está publicado y a qué precio lo decide
 * {@link ListingQueries#publishedListingsOfProducts} (misma definición que el catálogo; precio general con las
 * promociones {@code ALL} vigentes); el resto del documento (texto ponderado, vehículos, ubicación, calificaciones del
 * almacén y del producto, sin reseñas borradas ni ocultas) se arma en SQL con un upsert
 * ({@code INSERT … ON CONFLICT (listing_key) DO UPDATE}).
 *
 * <p>Trabaja por lotes de {@value #BATCH_PRODUCTS} productos, cada uno en su transacción: inserta o actualiza los
 * listados publicados del lote y borra los de esos productos que ya no lo están. {@link #reindexAll()} recorre todos
 * los productos publicados y al final borra las filas que no se tocaron (lo que dejó de estar publicado).</p>
 */
@Slf4j
@Service
public class SearchIndexer {
    static final int BATCH_PRODUCTS = 200;
    static final String VALUES_PLACEHOLDER = "{values}";
    static final String IDS_PLACEHOLDER = "{ids}";
    static final String KEYS_PLACEHOLDER = "{keys}";

    static final String UPSERT_SQL = """
            insert into search_listings (listing_key, product_id, branch_id, store_id, category_id, category_path,
                manufacturer_id, name, price, stock, has_promotion, city_code, department_code, latitude, longitude,
                store_rating, product_rating, product_review_count, universal, vehicle_make_ids, vehicle_model_ids,
                vehicle_version_ids, year_from, year_to, document, search_text, published_at, updated_at,
                mentioned_make_ids, mentioned_model_ids)
            with k as materialized (
                select * from (values {values}) as k(product_id, branch_id, price, stock, has_promotion)
            ), mk as materialized (
                select x.id as make_id, x.n
                from (select id, nullif(btrim(regexp_replace(public.immutable_unaccent(lower(name)),
                         '[^a-z0-9]+', ' ', 'g')), '') as n from make where deleted_at is null) x
                where x.n is not null
            ), mn as materialized (
                select m.id as model_id, m.make_id, y.n, replace(y.n, ' ', '') as c
                from model m
                cross join lateral (select nullif(btrim(regexp_replace(public.immutable_unaccent(lower(m.name)),
                    '[^a-z0-9]+', ' ', 'g')), '') as n) y
                where m.deleted_at is null and y.n is not null
            ), hm as (
                select n from mn group by n having count(distinct make_id) > 1
            ), dw as materialized (
                -- Palabras del dominio (categorías y sinónimos): un modelo llamado así («Escape») exige su marca.
                select distinct w.n
                from (select replace(code, '_', ' ') as t from categories where deleted_at is null
                      union all
                      select concat_ws(' ', term, array_to_string(expands_to, ' ')) from search_synonyms) src
                cross join lateral regexp_split_to_table(btrim(regexp_replace(public.immutable_unaccent(lower(src.t)),
                    '[^a-z0-9]+', ' ', 'g')), ' ') w(n)
                where w.n <> ''
            ), md as materialized (
                select x.model_id, x.make_id, x.n, x.c, mk.n as mn,
                       (x.letters and not x.common) as standalone, (x.letters and x.common) as conditional
                from (select mn.*, mn.n ~ '[a-z]' as letters,
                             (length(mn.c) < 3
                                 or mn.n in (select n from hm)
                                 or mn.n in (select n from mk)
                                 or mn.n in (select n from dw)
                                 or to_tsvector('public.spanish_unaccent'::regconfig, mn.n) = ''::tsvector) as common
                      from mn) x
                join mk on mk.make_id = x.make_id
            ), vk as (
                select mk.n as key, 'M' as kind, mk.make_id, null::uuid as model_id from mk
                union all
                select s.key, case when md.standalone then 'S' else 'C' end, md.make_id, md.model_id
                from md cross join lateral (values (md.n), (md.c)) s(key)
                where md.standalone or md.conditional
                union all
                select a.key, 'A', md.make_id, md.model_id
                from md cross join lateral (values (md.mn || ' ' || md.n), (md.mn || ' ' || md.c),
                    (replace(md.mn, ' ', '') || md.c)) a(key)
                where not md.standalone
            ), vkt as (
                select distinct key, kind, make_id, model_id, split_part(key, ' ', 1) as tok1,
                       array_length(string_to_array(key, ' '), 1) as ntok
                from vk
            ), pt as materialized (
                select p.id as product_id, string_to_array(nullif(btrim(regexp_replace(
                           public.immutable_unaccent(lower(concat_ws(' ', p.name, p.part_number, p.description,
                               (select string_agg(e.value, ' ') from jsonb_each_text(p.attributes) e)))),
                           '[^a-z0-9]+', ' ', 'g')), ''), ' ') as w
                from products p where p.id in (select product_id from k)
            ), hits as materialized (
                select pt.product_id, vkt.kind, vkt.make_id, vkt.model_id
                from pt
                cross join lateral generate_subscripts(pt.w, 1) g(i)
                join vkt on vkt.tok1 = pt.w[g.i] and array_to_string(pt.w[g.i : g.i + vkt.ntok - 1], ' ') = vkt.key
            ), ment as (
                select product_id, make_id, null::uuid as model_id from hits where kind = 'M'
                union
                select h.product_id, h.make_id, h.model_id from hits h
                where h.kind in ('S', 'A')
                   or (h.kind = 'C' and exists (select 1 from hits x
                       where x.product_id = h.product_id and x.kind = 'M' and x.make_id = h.make_id))
            ), mm as (
                select product_id, array_agg(distinct make_id) as make_ids,
                       array_agg(distinct model_id) filter (where model_id is not null) as model_ids
                from ment group by product_id
            )
            select k.product_id::text || ':' || k.branch_id::text,
                   k.product_id, k.branch_id, b.store_id, p.category_id,
                   case when p.category_id is null then '{}'::uuid[] else array[p.category_id] end,
                   p.manufacturer_id, p.name, k.price, k.stock, k.has_promotion,
                   l.municipality_code, gm.department_code,
                   case when l.latitude is not null and l.longitude is not null then l.latitude::double precision end,
                   case when l.latitude is not null and l.longitude is not null then l.longitude::double precision end,
                   rt.rating, pr.rating, coalesce(pr.total, 0), p.is_universal,
                   coalesce(v.make_ids, '{}'::uuid[]), coalesce(v.model_ids, '{}'::uuid[]),
                   coalesce(v.version_ids, '{}'::uuid[]), v.year_from, v.year_to,
                   setweight(to_tsvector('public.spanish_unaccent'::regconfig,
                       concat_ws(' ', p.name, p.part_number, p.sku)), 'A')
                   || setweight(to_tsvector('public.spanish_unaccent'::regconfig,
                       concat_ws(' ', m.name, replace(c.code, '_', ' '))), 'B')
                   || setweight(to_tsvector('public.spanish_unaccent'::regconfig, coalesce(v.names, '')), 'C')
                   || setweight(to_tsvector('public.spanish_unaccent'::regconfig,
                       concat_ws(' ', p.description, attr.text)), 'D'),
                   public.immutable_unaccent(lower(concat_ws(' ', p.name, m.name, replace(c.code, '_', ' '),
                       p.part_number))),
                   p.created_at, now(), coalesce(mm.make_ids, '{}'::uuid[]), coalesce(mm.model_ids, '{}'::uuid[])
            from k
            join products p on p.id = k.product_id
            join branches b on b.id = k.branch_id
            left join manufacturers m on m.id = p.manufacturer_id
            left join categories c on c.id = p.category_id
            left join locations l on l.id = b.location_id
            left join geo_municipalities gm on gm.code = l.municipality_code
            left join lateral (
                select round(avg(r.rating)::numeric, 1) as rating
                from store_reviews r
                where r.store_id = b.store_id and r.deleted_at is null and r.hidden_at is null
            ) rt on true
            left join lateral (
                select round(avg(r.rating)::numeric, 1) as rating, count(*)::int as total
                from product_reviews r
                where r.product_id = p.id and r.deleted_at is null and r.hidden_at is null
            ) pr on true
            left join lateral (
                select array_agg(distinct r.make_id) as make_ids,
                       array_agg(distinct coalesce(r.model_id, r.make_id)) as model_ids,
                       array_agg(distinct coalesce(r.version_id, r.model_id, r.make_id)) as version_ids,
                       case when bool_or(r.version_id is null) then null else min(ver.year_from) end::smallint as year_from,
                       case when bool_or(r.version_id is null) then null else max(ver.year_to) end::smallint as year_to,
                       string_agg(distinct concat_ws(' ', mk.name, md.name, ver.version), ' ') as names
                from product_vehicle_rules r
                left join make mk on mk.id = r.make_id
                left join model md on md.id = r.model_id
                left join versions ver on ver.id = r.version_id
                where r.product_id = p.id and r.deleted_at is null
            ) v on true
            left join lateral (
                select string_agg(e.value, ' ') as text from jsonb_each_text(p.attributes) e
            ) attr on true
            left join mm on mm.product_id = p.id
            on conflict (listing_key) do update set
                store_id = excluded.store_id, category_id = excluded.category_id,
                category_path = excluded.category_path, manufacturer_id = excluded.manufacturer_id,
                name = excluded.name, price = excluded.price, stock = excluded.stock,
                has_promotion = excluded.has_promotion, city_code = excluded.city_code,
                department_code = excluded.department_code, latitude = excluded.latitude,
                longitude = excluded.longitude, store_rating = excluded.store_rating,
                product_rating = excluded.product_rating, product_review_count = excluded.product_review_count,
                universal = excluded.universal,
                vehicle_make_ids = excluded.vehicle_make_ids, vehicle_model_ids = excluded.vehicle_model_ids,
                vehicle_version_ids = excluded.vehicle_version_ids, year_from = excluded.year_from,
                year_to = excluded.year_to, document = excluded.document, search_text = excluded.search_text,
                published_at = excluded.published_at, updated_at = excluded.updated_at,
                mentioned_make_ids = excluded.mentioned_make_ids, mentioned_model_ids = excluded.mentioned_model_ids
            """;

    static final String VALUE_ROW = "(?::uuid, ?::uuid, ?::numeric, ?::int, ?::boolean)";

    static final String DELETE_PRODUCTS_SQL = "delete from search_listings where product_id in ({ids})";
    static final String DELETE_STALE_SQL = DELETE_PRODUCTS_SQL + " and listing_key not in ({keys})";
    static final String DELETE_UNTOUCHED_SQL = "delete from search_listings where updated_at < ?";
    static final String NOW_SQL = "select now()";
    static final String STORE_PRODUCTS_SQL = """
            select id from products where store_id = ?
            union
            select product_id from search_listings where store_id = ?
            """;
    static final String BRANCH_PRODUCTS_SQL = """
            select product_id from inventory where branch_id = ?
            union
            select product_id from search_listings where branch_id = ?
            """;
    static final String INVENTORY_PRODUCTS_SQL = "select distinct product_id from inventory where id in ({ids})";

    private final JdbcClient jdbcClient;
    private final ListingQueries listingQueries;
    private final TransactionTemplate transactions;

    public SearchIndexer(JdbcClient jdbcClient, ListingQueries listingQueries,
            PlatformTransactionManager transactionManager) {
        this.jdbcClient = jdbcClient;
        this.listingQueries = listingQueries;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Reconstrucción completa: reindexa todos los productos publicados por lotes y borra las filas no tocadas en esta
     * pasada (ya no publicadas). Devuelve el número de listados indexados.
     */
    public int reindexAll() {
        OffsetDateTime startedAt = jdbcClient.sql(NOW_SQL).query(OffsetDateTime.class).single();
        int indexed = reindexProducts(listingQueries.publishedProductIds());
        int removed = Objects.requireNonNullElse(transactions.execute(status ->
                jdbcClient.sql(DELETE_UNTOUCHED_SQL).params(List.of(startedAt)).update()), 0);
        log.info("Índice de búsqueda reconstruido: {} listados, {} retirados.", indexed, removed);
        return indexed;
    }

    /** Reindexa los productos (publicados o no: los no publicados se retiran del índice). */
    public int reindexProducts(Collection<UUID> productIds) {
        List<UUID> ids = productIds == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(productIds.stream().filter(Objects::nonNull).toList()));
        int indexed = 0;
        for (int from = 0; from < ids.size(); from += BATCH_PRODUCTS) {
            List<UUID> batch = ids.subList(from, Math.min(from + BATCH_PRODUCTS, ids.size()));
            indexed += Objects.requireNonNullElse(transactions.execute(status -> indexBatch(batch)), 0);
        }
        return indexed;
    }

    /** Todos los productos del almacén y los que el índice tiene de él (estado, calificación, promociones). */
    public int reindexStore(UUID storeId) {
        return reindexProducts(jdbcClient.sql(STORE_PRODUCTS_SQL).params(List.of(storeId, storeId))
                .query(UUID.class).list());
    }

    /** Productos listados en la sucursal y los que el índice tiene en ella (ubicación, estado). */
    public int reindexBranch(UUID branchId) {
        return reindexProducts(jdbcClient.sql(BRANCH_PRODUCTS_SQL).params(List.of(branchId, branchId))
                .query(UUID.class).list());
    }

    /** Productos de los listados ({@code inventory.id}) indicados. */
    public int reindexInventory(Collection<UUID> inventoryIds) {
        if (inventoryIds == null || inventoryIds.isEmpty()) {
            return 0;
        }
        List<UUID> ids = List.copyOf(new LinkedHashSet<>(inventoryIds));
        List<UUID> products = new ArrayList<>();
        for (int from = 0; from < ids.size(); from += BATCH_PRODUCTS) {
            List<UUID> batch = ids.subList(from, Math.min(from + BATCH_PRODUCTS, ids.size()));
            products.addAll(jdbcClient.sql(INVENTORY_PRODUCTS_SQL.replace(IDS_PLACEHOLDER, placeholders(batch.size())))
                    .params(List.copyOf(batch)).query(UUID.class).list());
        }
        return reindexProducts(products);
    }

    /** Un lote en la transacción en curso: upsert de lo publicado y borrado de lo que ya no lo está. */
    int indexBatch(List<UUID> productIds) {
        List<ProductResponse> listings = listingQueries.publishedListingsOfProducts(productIds);
        List<Object> deleteArgs = new ArrayList<>(productIds);
        if (listings.isEmpty()) {
            jdbcClient.sql(DELETE_PRODUCTS_SQL.replace(IDS_PLACEHOLDER, placeholders(productIds.size())))
                    .params(deleteArgs).update();
            return 0;
        }
        List<Object> args = new ArrayList<>(listings.size() * 5);
        List<String> keys = new ArrayList<>(listings.size());
        for (ProductResponse listing : listings) {
            BigDecimal price = listing.effectivePrice() != null ? listing.effectivePrice() : listing.price();
            boolean promoted = listing.discountAmount() != null && listing.discountAmount().signum() > 0;
            args.add(listing.id());
            args.add(listing.branchId());
            args.add(price);
            args.add(listing.quantity() == null ? 0 : listing.quantity());
            args.add(promoted);
            keys.add(key(listing.id(), listing.branchId()));
        }
        jdbcClient.sql(UPSERT_SQL.replace(VALUES_PLACEHOLDER,
                String.join(", ", Collections.nCopies(listings.size(), VALUE_ROW)))).params(args).update();
        deleteArgs.addAll(keys);
        jdbcClient.sql(DELETE_STALE_SQL.replace(IDS_PLACEHOLDER, placeholders(productIds.size()))
                .replace(KEYS_PLACEHOLDER, placeholders(keys.size()))).params(deleteArgs).update();
        return listings.size();
    }

    static String key(UUID productId, UUID branchId) {
        return productId + ":" + branchId;
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
