-- flyway:executeInTransaction=false
-- =============================================================================
-- U202610130915__drop_redundant_indexes.sql  (rollback de V202610130915)
-- Recrea los índices eliminados, con la misma definición que en init.sql.
-- Transacción: NO. psql: psql -v ON_ERROR_STOP=1 -f <archivo>
-- =============================================================================

SET lock_timeout = '30s';
SET statement_timeout = 0;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_branches_store ON public.branches (store_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_model_make ON public.model (make_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_versions_model ON public.versions (model_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compatibility_vehicle ON public.compatibilities (make_id, model_id, version_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventory_branch ON public.inventory (branch_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales_order ON public.sales (order_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipments_tracking ON public.shipments (tracking_number);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_reviews_store ON public.store_reviews (store_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventory_price ON public.inventory (price);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments_order ON public.payments (order_id);

RESET lock_timeout;
RESET statement_timeout;
