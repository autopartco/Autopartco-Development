-- flyway:executeInTransaction=false
-- =============================================================================
-- V202610130942__search_and_brin_indexes.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito
--   - GIN sobre products.search_vector: búsqueda de texto completo
--       WHERE search_vector @@ websearch_to_tsquery('spanish', immutable_unaccent(:q))
--   - Trigram sobre nombre sin acentos: búsqueda difusa y por prefijo/infijo
--       WHERE immutable_unaccent(lower(name)) % immutable_unaccent(lower(:q))
--       WHERE immutable_unaccent(lower(name)) LIKE '%' || immutable_unaccent(lower(:q)) || '%'
--   - Trigram sobre part_number_normalized: referencias con o sin guiones
--       WHERE part_number_normalized LIKE upper(regexp_replace(:q,'[^A-Za-z0-9]','','g')) || '%'
--   - GIN jsonb_path_ops sobre products.attributes (filtro por atributos):
--       WHERE attributes @> '{"posicion":"delantera"}'
--   - BRIN por fecha en tablas de solo inserción (pages_per_range = 32,
--     convención del diseño predictivo).
--   idx_products_name (GIN solo sobre name, de init.sql) queda superado por
--   idx_products__search_vector. Se conserva hasta que el backend use
--   search_vector; luego puede eliminarse.
--
-- Transacción: NO (CONCURRENTLY). Flyway: .sql.conf.
--   psql: psql -v ON_ERROR_STOP=1 -f <archivo>
-- Requiere: V202610130900 (pg_trgm, immutable_unaccent) y V202610130940.
-- Rollback: U202610130942__search_and_brin_indexes.sql
-- =============================================================================

SET lock_timeout = '30s';
SET statement_timeout = 0;
-- gin_trgm_ops vive en el esquema de pg_trgm; en Supabase suele ser "extensions".
-- Un esquema inexistente en search_path se ignora sin error.
SET search_path = public, extensions, pg_temp;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products__search_vector
    ON public.products USING gin (search_vector)
    WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products__name_trgm
    ON public.products USING gin ((public.immutable_unaccent(lower(name))) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products__part_number_normalized_trgm
    ON public.products USING gin (part_number_normalized gin_trgm_ops)
    WHERE deleted_at IS NULL AND part_number_normalized IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products__attributes
    ON public.products USING gin (attributes jsonb_path_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS brin_audit_logs__created_at
    ON public.audit_logs USING brin (created_at) WITH (pages_per_range = 32);
CREATE INDEX CONCURRENTLY IF NOT EXISTS brin_login_attempts__attempted_at
    ON public.login_attempts USING brin (attempted_at) WITH (pages_per_range = 32);
CREATE INDEX CONCURRENTLY IF NOT EXISTS brin_payment_events__created_at
    ON public.payment_events USING brin (created_at) WITH (pages_per_range = 32);
CREATE INDEX CONCURRENTLY IF NOT EXISTS brin_shipment_events__event_at
    ON public.shipment_events USING brin (event_at) WITH (pages_per_range = 32);

DO $$
DECLARE
    invalid TEXT;
BEGIN
    SELECT string_agg(c.relname, ', ') INTO invalid
    FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND NOT i.indisvalid;
    IF invalid IS NOT NULL THEN
        RAISE EXCEPTION 'Índices INVALID: %. DROP INDEX CONCURRENTLY y reintente.', invalid;
    END IF;
END $$;

RESET lock_timeout;
RESET statement_timeout;
RESET search_path;
