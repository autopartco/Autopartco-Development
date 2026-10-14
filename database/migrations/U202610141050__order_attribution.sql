-- =============================================================================
-- U202610141050__order_attribution.sql  (rollback de V202610141050)
-- DESTRUCTIVO: requiere aprobación (la atribución no se puede reconstruir).
-- Respaldo: pg_dump --data-only -t public.order_attributions "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.order_attributions;
