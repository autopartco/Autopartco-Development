-- =============================================================================
-- U202610141250__returns.sql  (rollback de V202610141250)
-- DESTRUCTIVO: requiere aprobación (devoluciones, garantías y su historial).
-- Revierta antes U202610141320 y U202610141330.
-- Respaldo: pg_dump --data-only -t 'public.return_*' "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.return_status_history;
DROP TABLE IF EXISTS public.return_request_items;
DROP TABLE IF EXISTS public.return_requests;
DROP TABLE IF EXISTS public.return_reasons;
