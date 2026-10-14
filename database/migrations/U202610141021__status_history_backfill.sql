-- =============================================================================
-- U202610141021__status_history_backfill.sql  (rollback de V202610141021)
-- DESTRUCTIVO: requiere aprobación. Borra las filas de estado inicial creadas
-- por la migración y anula solo los order_details.product_id que ella rellenó.
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL autopartco.allow_purge = 'on';

DELETE FROM public.order_status_history WHERE notes = 'Estado inicial por migración' AND from_status IS NULL;
DELETE FROM public.sale_status_history  WHERE notes = 'Estado inicial por migración' AND from_status IS NULL;
-- Solo los product_id que rellenó la migración (no los escritos por la aplicación).
UPDATE public.order_details od
SET product_id = NULL
FROM public.migration_backfill_order_details b
WHERE b.order_detail_id = od.id;
DROP TABLE IF EXISTS public.migration_backfill_order_details;
