-- =============================================================================
-- U202610141520__retention_functions.sql  (rollback de A9)
-- Elimina las funciones de retención. Sin pérdida de datos (lo ya purgado no
-- se recupera; restáurelo desde respaldo).
-- =============================================================================

DROP FUNCTION IF EXISTS public.purge_stale_identity_links(INT, BOOLEAN);
DROP FUNCTION IF EXISTS public.purge_payment_risk_ip(INT, BOOLEAN);
DROP FUNCTION IF EXISTS public.detach_and_drop_partitions_older_than(regclass, INT, BOOLEAN);
