-- =============================================================================
-- U202610140951__inventory_price_history_backfill.sql  (rollback de V202610140951)
-- DESTRUCTIVO: requiere aprobación. Borra solo las filas del relleno
-- (change_source INITIAL_LOAD con el motivo de la migración).
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL autopartco.allow_purge = 'on';

DELETE FROM public.inventory_price_history
WHERE change_source = 'INITIAL_LOAD' AND change_reason = 'Relleno inicial por migración';
