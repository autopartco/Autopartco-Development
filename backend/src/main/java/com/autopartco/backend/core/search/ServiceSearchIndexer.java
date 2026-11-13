package com.autopartco.backend.core.search;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.autopartco.backend.core.organization.ServiceCategory;

import lombok.extern.slf4j.Slf4j;

/**
 * Mantiene {@code search_services}: una fila por servicio×sucursal visible. Visible = servicio {@code active}, almacén
 * en estado {@code ACTIVE} (mismo criterio que el catálogo de productos) y sucursal {@code ACTIVE} enlazada en
 * {@code store_service_branches}. El documento (nombre A; categoría y sinónimos B; descripción y almacén C), la
 * ubicación (municipio y departamento de la sucursal) y la calificación del almacén se arman en SQL con un upsert
 * ({@code INSERT … ON CONFLICT (service_key) DO UPDATE}), igual que {@link SearchIndexer}.
 *
 * <p>Trabaja por lotes de {@value #BATCH_SERVICES} servicios, cada uno en su transacción: inserta o actualiza las filas
 * visibles y borra las que ya no lo son (todas las del lote que esta transacción no tocó: {@code now()} es el de la
 * transacción). {@link #reindexAll()} recorre todos los servicios y al final borra lo que no se tocó. Por ahora
 * {@code price_from = base_price_from = store_services.price_from} y {@code has_promotion = false}: las promociones de
 * servicios llegan después.</p>
 */
@Slf4j
@Service
public class ServiceSearchIndexer {
    static final int BATCH_SERVICES = 200;
    static final String IDS_PLACEHOLDER = "{ids}";
    static final String CATEGORY_TEXT_PLACEHOLDER = "{categoryText}";

    static final String UPSERT_SQL = """
            insert into search_services (service_key, service_id, branch_id, store_id, category, name, price_from,
                base_price_from, has_promotion, booking_enabled, city_code, department_code, latitude, longitude,
                store_rating, document, search_text, updated_at)
            select ss.id::text || ':' || sb.branch_id::text, ss.id, sb.branch_id, ss.store_id, ss.category, ss.name,
                   ss.price_from, ss.price_from, false, ss.booking_enabled,
                   l.municipality_code, gm.department_code,
                   case when l.latitude is not null and l.longitude is not null then l.latitude::double precision end,
                   case when l.latitude is not null and l.longitude is not null then l.longitude::double precision end,
                   rt.rating,
                   setweight(to_tsvector('public.spanish_unaccent'::regconfig, ss.name), 'A')
                   || setweight(to_tsvector('public.spanish_unaccent'::regconfig, {categoryText}), 'B')
                   || setweight(to_tsvector('public.spanish_unaccent'::regconfig,
                       concat_ws(' ', ss.description, s.business_name)), 'C'),
                   public.immutable_unaccent(lower(concat_ws(' ', ss.name, {categoryText}, s.business_name))),
                   now()
            from store_services ss
            join store s on s.id = ss.store_id and s.deleted_at is null
            join states st on st.id = s.state and st.deleted_at is null and st.code = 'ACTIVE'
            join store_service_branches sb on sb.service_id = ss.id
            join branches b on b.id = sb.branch_id and b.deleted_at is null and b.store_id = ss.store_id
            join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = 'ACTIVE'
            left join locations l on l.id = b.location_id
            left join geo_municipalities gm on gm.code = l.municipality_code
            left join lateral (
                select round(avg(r.rating)::numeric, 1) as rating
                from store_reviews r
                where r.store_id = ss.store_id and r.deleted_at is null and r.hidden_at is null
            ) rt on true
            where ss.active and ss.id in ({ids})
            on conflict (service_key) do update set
                branch_id = excluded.branch_id, store_id = excluded.store_id, category = excluded.category,
                name = excluded.name, price_from = excluded.price_from, base_price_from = excluded.base_price_from,
                has_promotion = excluded.has_promotion, booking_enabled = excluded.booking_enabled,
                city_code = excluded.city_code, department_code = excluded.department_code,
                latitude = excluded.latitude, longitude = excluded.longitude, store_rating = excluded.store_rating,
                document = excluded.document, search_text = excluded.search_text, updated_at = excluded.updated_at
            """.replace(CATEGORY_TEXT_PLACEHOLDER, categoryTextSql());

    /** Lo que esta transacción no tocó ({@code updated_at < now()}) de los servicios del lote: ya no es visible. */
    static final String DELETE_STALE_SQL =
            "delete from search_services where service_id in ({ids}) and updated_at < now()";
    static final String DELETE_UNTOUCHED_SQL = "delete from search_services where updated_at < ?";
    static final String NOW_SQL = "select now()";
    static final String ALL_SERVICES_SQL = """
            select id from store_services
            union
            select service_id from search_services
            """;
    static final String STORE_SERVICES_SQL = """
            select id from store_services where store_id = ?
            union
            select service_id from search_services where store_id = ?
            """;
    static final String BRANCH_SERVICES_SQL = """
            select service_id from store_service_branches where branch_id = ?
            union
            select service_id from search_services where branch_id = ?
            """;
    static final String NEEDS_BOOTSTRAP_SQL = """
            select not exists (select 1 from search_services)
               and exists (select 1 from store_services where active)
            """;

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactions;

    public ServiceSearchIndexer(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = jdbcClient;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /**
     * Reconstrucción completa: reindexa todos los servicios por lotes y borra las filas no tocadas en esta pasada.
     * Devuelve el número de filas servicio×sucursal indexadas.
     */
    public int reindexAll() {
        OffsetDateTime startedAt = jdbcClient.sql(NOW_SQL).query(OffsetDateTime.class).single();
        int indexed = reindexServices(jdbcClient.sql(ALL_SERVICES_SQL).query(UUID.class).list());
        int removed = Objects.requireNonNullElse(transactions.execute(status ->
                jdbcClient.sql(DELETE_UNTOUCHED_SQL).params(List.of(startedAt)).update()), 0);
        log.info("Índice de servicios reconstruido: {} filas, {} retiradas.", indexed, removed);
        return indexed;
    }

    /** Reindexa los servicios (visibles o no: los que ya no lo son se retiran del índice). */
    public int reindexServices(Collection<UUID> serviceIds) {
        List<UUID> ids = serviceIds == null ? List.of()
                : List.copyOf(new LinkedHashSet<>(serviceIds.stream().filter(Objects::nonNull).toList()));
        int indexed = 0;
        for (int from = 0; from < ids.size(); from += BATCH_SERVICES) {
            List<UUID> batch = ids.subList(from, Math.min(from + BATCH_SERVICES, ids.size()));
            indexed += Objects.requireNonNullElse(transactions.execute(status -> indexBatch(batch)), 0);
        }
        return indexed;
    }

    /** Todos los servicios del almacén y los que el índice tiene de él (estado, calificación). */
    public int reindexStore(UUID storeId) {
        return reindexServices(jdbcClient.sql(STORE_SERVICES_SQL).params(List.of(storeId, storeId))
                .query(UUID.class).list());
    }

    /** Servicios que presta la sucursal y los que el índice tiene en ella (ubicación, estado). */
    public int reindexBranch(UUID branchId) {
        return reindexServices(jdbcClient.sql(BRANCH_SERVICES_SQL).params(List.of(branchId, branchId))
                .query(UUID.class).list());
    }

    /** Hay servicios activos y el índice está vacío (primer arranque con el índice nuevo). */
    public boolean needsBootstrap() {
        try {
            return Boolean.TRUE.equals(jdbcClient.sql(NEEDS_BOOTSTRAP_SQL).query(Boolean.class).single());
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    /** Un lote en la transacción en curso: upsert de lo visible y borrado de lo que ya no lo es. */
    int indexBatch(List<UUID> serviceIds) {
        List<Object> args = new ArrayList<>(serviceIds);
        String placeholders = placeholders(serviceIds.size());
        int upserted = jdbcClient.sql(UPSERT_SQL.replace(IDS_PLACEHOLDER, placeholders)).params(args).update();
        jdbcClient.sql(DELETE_STALE_SQL.replace(IDS_PLACEHOLDER, placeholders)).params(args).update();
        return upserted;
    }

    static String key(UUID serviceId, UUID branchId) {
        return serviceId + ":" + branchId;
    }

    /** {@code case ss.category when 'AC' then '…' … end}: etiqueta y sinónimos de la categoría (constantes del código). */
    static String categoryTextSql() {
        String cases = java.util.Arrays.stream(ServiceCategory.values())
                .map(category -> "when '" + category.name() + "' then '"
                        + ServiceTerms.indexText(category).replace("'", "''") + "'")
                .collect(Collectors.joining(" "));
        return "(case ss.category " + cases + " else '' end)";
    }

    private static String placeholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }
}
