-- flyway:executeInTransaction=false
-- =============================================================================
-- U202610130942__search_and_brin_indexes.sql  (rollback de V202610130942)
-- Sin pérdida de datos. Transacción: NO. psql -v ON_ERROR_STOP=1 -f <archivo>
-- =============================================================================

SET lock_timeout = '30s';

DROP INDEX CONCURRENTLY IF EXISTS public.brin_shipment_events__event_at;
DROP INDEX CONCURRENTLY IF EXISTS public.brin_payment_events__created_at;
DROP INDEX CONCURRENTLY IF EXISTS public.brin_login_attempts__attempted_at;
DROP INDEX CONCURRENTLY IF EXISTS public.brin_audit_logs__created_at;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products__attributes;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products__part_number_normalized_trgm;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products__name_trgm;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products__search_vector;

RESET lock_timeout;
