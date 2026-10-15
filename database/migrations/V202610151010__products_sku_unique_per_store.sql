-- =============================================================================
-- V202610151010__products_sku_unique_per_store.sql
-- Autopartco | PostgreSQL 16+ | Auditoría punto 6
--
-- Qué hace: products.sku deja de ser único en cualquier ámbito.
--   1. Elimina la restricción UNIQUE global sobre products.sku (products_sku_key
--      y cualquier UNIQUE de una columna sobre sku).
--   2. No crea ningún índice de unicidad por almacén.
--
-- Decisión de producto: el SKU es un identificador interno que cada vendedor
--   asigna como quiera. Es opcional y no es único: dos almacenes (y dos
--   productos del mismo almacén) pueden usar el mismo SKU. La unicidad, si se
--   necesita, es responsabilidad del vendedor, no de la BD.
--
-- Backend, en el MISMO despliegue:
--   - Product.java: el campo sku no lleva unique = true (ya no lo lleva).
--   - ProductCatalog: se elimina la validación PRODUCT_SKU_ALREADY_EXISTS.
--
-- Transacción: SÍ (no usa CONCURRENTLY). Rollback: U202610151010__products_sku_unique_per_store.sql
-- =============================================================================

SET LOCAL lock_timeout = '30s';

DO $$
DECLARE
    r RECORD;
BEGIN
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

-- Índice de unicidad por almacén creado por la versión anterior de este script
-- (no se aplicó nunca en producción; se elimina por si existe en algún entorno).
DROP INDEX IF EXISTS public.uq_products__store_sku;
