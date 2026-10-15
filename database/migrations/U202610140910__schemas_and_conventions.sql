-- =============================================================================
-- U202610140910__schemas_and_conventions.sql  (rollback de V202610140910)
-- Borra funciones y esquemas. DROP SCHEMA sin CASCADE falla si quedan
-- objetos: aplique antes los U posteriores en orden inverso.
-- psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- =============================================================================

DROP FUNCTION IF EXISTS public.ensure_monthly_partitions(regclass, DATE, INT);
DROP FUNCTION IF EXISTS public.bogota_date(TIMESTAMP);
DROP FUNCTION IF EXISTS public.bogota_date(TIMESTAMPTZ);
DROP FUNCTION IF EXISTS public.legacy_bound(TIMESTAMPTZ, TIMESTAMPTZ);
DROP FUNCTION IF EXISTS public.legacy_bound(TIMESTAMPTZ, TIMESTAMP);
DROP FUNCTION IF EXISTS public.legacy_ts(TIMESTAMPTZ);
DROP FUNCTION IF EXISTS public.legacy_ts(TIMESTAMP);
DROP FUNCTION IF EXISTS public.legacy_source_tz();
DROP SCHEMA IF EXISTS privacy;
DROP SCHEMA IF EXISTS ml;
DROP SCHEMA IF EXISTS analytics;
DROP SCHEMA IF EXISTS tracking;
