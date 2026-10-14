-- flyway:executeInTransaction=false
-- REQUIERE APROBACIÓN: cambia la semántica (SKU único global -> único por almacén)
-- =============================================================================
-- V202610131010__products_sku_unique_per_store.sql
-- Autopartco | PostgreSQL 16+ | Auditoría punto 6
--
-- Qué hace
--   1. Crea uq_products__store_sku (store_id, sku) WHERE sku IS NOT NULL AND
--      deleted_at IS NULL, con CONCURRENTLY.
--   2. Elimina la unicidad global de products.sku (products_sku_key y
--      cualquier UNIQUE de una columna sobre sku creado por Hibernate).
--   Hoy dos almacenes no pueden usar el mismo SKU interno, lo cual no tiene
--   sentido en un marketplace multi-almacén.
-- Detección previa (debe dar 0 filas):
--   SELECT store_id, sku, count(*) FROM products
--   WHERE sku IS NOT NULL AND deleted_at IS NULL GROUP BY 1, 2 HAVING count(*) > 1;
-- Backend, en el MISMO despliegue:
--   Product.java: quitar unique = true de @Column(sku). Si no, ddl-auto: update
--   VOLVERÍA a crear el UNIQUE global. Si hay validación de SKU duplicado,
--   acotarla por storeId.
-- Transacción: NO (CONCURRENTLY). Flyway: crear el .sql.conf con
--   executeInTransaction=false al moverlo. psql: psql -v ON_ERROR_STOP=1 -f <archivo>
-- Respaldo: pg_dump --schema-only -t public.products "$DATABASE_URL" > products_schema.sql
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo.
-- Rollback: U202610131010__products_sku_unique_per_store.sql
-- =============================================================================

SET lock_timeout = '30s';
SET statement_timeout = 0;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_products__store_sku
    ON public.products (store_id, sku)
    WHERE sku IS NOT NULL AND deleted_at IS NULL;

DO $$
DECLARE
    r RECORD;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_index i JOIN pg_class c ON c.oid = i.indexrelid
                   WHERE c.relname = 'uq_products__store_sku' AND i.indisvalid) THEN
        RAISE EXCEPTION 'uq_products__store_sku no es válido; no se elimina la unicidad global.';
    END IF;

    FOR r IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = con.conkey[1]
        WHERE con.conrelid = 'public.products'::regclass
          AND con.contype = 'u' AND array_length(con.conkey, 1) = 1 AND a.attname = 'sku'
    LOOP
        EXECUTE format('ALTER TABLE public.products DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

RESET lock_timeout;
RESET statement_timeout;
