package com.autopartco.backend.core.search;

/**
 * SQL de la búsqueda de almacenes publicados. Publicado = almacén {@code ACTIVE} y, si tiene solicitud de vendedor,
 * solicitud {@code ACTIVE} (como {@code PublishedStoreAssets.requirePublished}). Coincidencia sin tildes ni
 * mayúsculas ({@code public.immutable_unaccent}, {@code pg_trgm}): nombre que contiene el texto o con similitud
 * >= 0,3, descripción que lo contiene o ciudad de una sucursal activa. Los parámetros ({@code :q}, {@code :contains},
 * {@code :prefix}, {@code :cityCode}) van normalizados; vacíos significan «sin filtro». Los conteos se resuelven
 * en SQL solo para la página.
 */
final class StoreSearchSql {
    private StoreSearchSql() {
    }

    /** Texto seguro para {@code LIKE}: {@link QueryParser#normalizeName} solo deja {@code [a-z0-9 ]}. */
    static String normalize(String value) {
        String normalized = QueryParser.normalizeName(value);
        if (normalized == null) {
            return "";
        }
        return normalized.length() > QueryParser.MAX_LENGTH
                ? normalized.substring(0, QueryParser.MAX_LENGTH).strip()
                : normalized;
    }

    static final String JOINS = """
            from store s
            join states st on st.id = s.state and st.deleted_at is null and st.code = 'ACTIVE'
            cross join lateral (select public.immutable_unaccent(lower(s.business_name)) as nname) n
            left join lateral (
                select count(*) as branch_count,
                       string_agg(distinct btrim(l.city), '|' order by btrim(l.city))
                           filter (where nullif(btrim(l.city), '') is not null) as cities,
                       coalesce(bool_or(public.immutable_unaccent(lower(l.city)) like :contains), false) as city_match,
                       coalesce(bool_or(l.municipality_code = :cityCode), false) as in_city
                from branches b
                join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = 'ACTIVE'
                left join locations l on l.id = b.location_id
                where b.store_id = s.id and b.deleted_at is null
            ) bi on true
            """;

    static final String RATING_JOIN = """
            left join lateral (
                select round(avg(r.rating)::numeric, 1) as average, count(*) as total
                from store_reviews r
                where r.store_id = s.id and r.deleted_at is null and r.hidden_at is null
            ) rt on true
            """;

    static final String WHERE = """
            where s.deleted_at is null
              and not exists (
                  select 1 from seller_applications sa
                  where sa.store_id = s.id and sa.deleted_at is null and sa.status <> 'ACTIVE')
              and (:cityCode = '' or bi.in_city)
              and (:q = '' or n.nname like :contains or similarity(n.nname, :q) >= 0.3
                   or public.immutable_unaccent(lower(coalesce(s.configuration ->> 'description', ''))) like :contains
                   or bi.city_match)
            """;

    /** 0 nombre exacto, 1 prefijo, 2 otra coincidencia, 3 sin texto. */
    static final String RANK = """
            case when :q = '' then 3 when n.nname = :q then 0 when n.nname like :prefix then 1 else 2 end""";

    static final String PAGE_SQL = """
            with matched as (
                select s.id, s.business_name, s.business_type, s.logo_path,
                       s.configuration ->> 'description' as description,
                       bi.cities, bi.branch_count, rt.average, rt.total as rating_count,
                       %s as match_rank, similarity(n.nname, :q) as sim
                %s%s%s
            ), page as (
                select m.*, count(*) over () as total,
                       row_number() over (order by m.match_rank, m.sim desc, m.average desc nulls last,
                                          m.rating_count desc, m.business_name) as pos
                from matched m
                order by pos
                limit :limit offset :offset
            )
            select p.*, coalesce(pc.products, 0) as published_products, coalesce(sc.services, 0) as active_services
            from page p
            left join lateral (
                select count(distinct i.product_id) as products
                from inventory i
                join branches b on b.id = i.branch_id and b.deleted_at is null
                join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = 'ACTIVE'
                join products pr on pr.id = i.product_id and pr.deleted_at is null
                join manufacturers mf on mf.id = pr.manufacturer_id and mf.deleted_at is null
                join categories c on c.id = pr.category_id and c.deleted_at is null
                join states ps on ps.id = pr.state_id and ps.deleted_at is null and ps.code = 'ACTIVE'
                where b.store_id = p.id and i.deleted_at is null and i.stock > 0
            ) pc on true
            left join lateral (
                select count(*) as services
                from store_services ss
                where ss.store_id = p.id and ss.active
                  and exists (
                      select 1 from store_service_branches sb
                      join branches b on b.id = sb.branch_id and b.deleted_at is null and b.store_id = ss.store_id
                      join states bs on bs.id = b.state and bs.deleted_at is null and bs.code = 'ACTIVE'
                      where sb.service_id = ss.id)
            ) sc on true
            order by p.pos
            """.formatted(RANK, JOINS, RATING_JOIN, WHERE);

    /** Autocompletado: mismas condiciones, sin conteos ni calificación. */
    static final String SUGGEST_SQL = """
            select s.id, s.business_name, s.logo_path, bi.cities
            %s%s
            order by %s, similarity(n.nname, :q) desc, s.business_name
            limit :limit
            """.formatted(JOINS, WHERE, RANK);
}
