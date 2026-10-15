-- =============================================================================
-- U202610151010__products_sku_unique_per_store.sql  (rollback de V202610151010)
-- Restaura la UNIQUE global de products.sku. FALLA si hay SKU repetidos entre
-- productos; en ese caso hay que corregir los datos antes. No borra nada.
--   SELECT sku, count(*) FROM products WHERE sku IS NOT NULL GROUP BY sku HAVING count(*) > 1;
-- Transacción: SÍ.
-- =============================================================================

SET LOCAL lock_timeout = '30s';

ALTER TABLE public.products ADD CONSTRAINT products_sku_key UNIQUE (sku);
