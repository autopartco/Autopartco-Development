-- =============================================================================
-- U202610140950__inventory_price_history.sql  (rollback de V202610140950)
-- DESTRUCTIVO: requiere aprobación (pierde el historial de precios, que no se
-- puede reconstruir). Revierta antes U202610141025 (trigger puente) y
-- U202610141330.
-- Respaldo: pg_dump --data-only -t public.inventory_price_history "$DATABASE_URL" > iph.sql
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.inventory_price_history;
