-- =============================================================================
-- U202610141200__customer_garage.sql  (rollback de V202610141200)
-- DESTRUCTIVO: requiere aprobación (garaje, kilometraje y atributos técnicos
-- de versiones). Revierta antes U202610141220 (cart_items), U202610141250
-- (return_requests) y U202610141320.
-- Respaldo: pg_dump --data-only -t public.customer_vehicles -t public.vehicle_odometer_readings -t public.versions "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

DROP INDEX IF EXISTS public.idx_order_details__customer_vehicle;
ALTER TABLE public.order_details DROP CONSTRAINT IF EXISTS fk_order_details__customer_vehicle_id;
DROP TABLE IF EXISTS public.vehicle_odometer_readings;
DROP TABLE IF EXISTS public.customer_vehicles;
ALTER TABLE public.versions
    DROP CONSTRAINT IF EXISTS chk_versions__year_from,
    DROP CONSTRAINT IF EXISTS chk_versions__year_to,
    DROP CONSTRAINT IF EXISTS chk_versions__years,
    DROP CONSTRAINT IF EXISTS chk_versions__displacement,
    DROP CONSTRAINT IF EXISTS chk_versions__fuel_type,
    DROP CONSTRAINT IF EXISTS chk_versions__transmission,
    DROP COLUMN IF EXISTS body_type,
    DROP COLUMN IF EXISTS transmission,
    DROP COLUMN IF EXISTS fuel_type,
    DROP COLUMN IF EXISTS displacement_cc,
    DROP COLUMN IF EXISTS engine_code,
    DROP COLUMN IF EXISTS year_to,
    DROP COLUMN IF EXISTS year_from;
