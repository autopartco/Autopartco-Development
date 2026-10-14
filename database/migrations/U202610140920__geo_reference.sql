-- =============================================================================
-- U202610140920__geo_reference.sql  (rollback de V202610140920)
-- DESTRUCTIVO: requiere aprobación (borra la referencia geográfica y los
-- municipality_code asignados en locations/order_addresses).
-- Respaldo: pg_dump --data-only -t public.geo_departments -t public.geo_municipalities "$DATABASE_URL"
-- Requiere haber revertido antes los U que referencian geo_municipalities
-- (order_status_history: shipments/shipment_events; purchasing: suppliers).
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

DROP VIEW IF EXISTS analytics.dim_geography;
ALTER TABLE public.order_addresses DROP COLUMN IF EXISTS municipality_code;
ALTER TABLE public.locations DROP COLUMN IF EXISTS municipality_code;
DROP TABLE IF EXISTS public.geo_municipalities;
DROP TABLE IF EXISTS public.geo_departments;
