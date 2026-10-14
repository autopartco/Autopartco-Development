-- =============================================================================
-- U202610141210__compatibility_evidence.sql  (rollback de V202610141210)
-- DESTRUCTIVO: requiere aprobación (pierde la evidencia de compatibilidad).
-- Respaldo: pg_dump --data-only -t public.products_compatibilies "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

DROP TRIGGER IF EXISTS trg_products_compatibilies_updated_at ON public.products_compatibilies;
DROP INDEX IF EXISTS public.idx_products_compatibilies__confirmed_by;
ALTER TABLE public.products_compatibilies
    DROP COLUMN IF EXISTS updated_at,
    DROP COLUMN IF EXISTS created_at,
    DROP COLUMN IF EXISTS confirmed_at,
    DROP COLUMN IF EXISTS confirmed_by_account_id,
    DROP COLUMN IF EXISTS year_to,
    DROP COLUMN IF EXISTS year_from,
    DROP COLUMN IF EXISTS source,
    DROP COLUMN IF EXISTS evidence_status;
