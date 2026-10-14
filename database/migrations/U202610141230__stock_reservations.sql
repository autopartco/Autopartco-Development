-- =============================================================================
-- U202610141230__stock_reservations.sql  (rollback de V202610141230)
-- DESTRUCTIVO: requiere aprobación (pierde las reservas; inventory.reserved_stock
-- quedaría sin respaldo: póngalo a 0 si revierte).
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.stock_reservations;
