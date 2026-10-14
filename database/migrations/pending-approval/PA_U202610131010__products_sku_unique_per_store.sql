-- flyway:executeInTransaction=false
-- REQUIERE APROBACIÓN
-- =============================================================================
-- U202610131010__products_sku_unique_per_store.sql  (rollback)
-- Restaura UNIQUE global de products.sku. Falla si ya hay SKU repetidos entre
-- almacenes (consulta de detección abajo). En ese caso hay que corregir los
-- datos antes; no se borra nada automáticamente.
--   SELECT sku, count(DISTINCT store_id) FROM products WHERE sku IS NOT NULL
--   GROUP BY sku HAVING count(*) > 1;
-- Transacción: NO.
-- =============================================================================

SET lock_timeout = '30s';
SET statement_timeout = 0;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS products_sku_key_idx ON public.products (sku);
ALTER TABLE public.products ADD CONSTRAINT products_sku_key UNIQUE USING INDEX products_sku_key_idx;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_products__store_sku;

RESET lock_timeout;
RESET statement_timeout;
