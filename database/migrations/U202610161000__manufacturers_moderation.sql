-- Rollback de V202610161000__manufacturers_moderation.sql
-- Quita índices, CHECK, FK y columnas de moderación. Pierde qué fabricantes eran
-- sugerencias pendientes y quién las propuso: DESTRUCTIVO, requiere aprobación
-- y respaldo previo de la tabla manufacturers.
-- No se eliminan created_at/updated_at/deleted_at/version (la entidad las usa).
DO $$
BEGIN
    IF current_setting('autopartco.allow_destructive', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'Rollback destructivo: SET autopartco.allow_destructive = ''on'' tras aprobación y respaldo.';
    END IF;
END $$;

DROP INDEX IF EXISTS public.idx_manufacturers__pending;
DROP INDEX IF EXISTS public.idx_manufacturers__suggested_by_store_id;
DROP INDEX IF EXISTS public.idx_manufacturers__name_trgm;
DROP INDEX IF EXISTS public.uq_manufacturers__name_normalized;
ALTER TABLE public.manufacturers DROP CONSTRAINT IF EXISTS chk_manufacturers__pending_has_store;
ALTER TABLE public.manufacturers DROP CONSTRAINT IF EXISTS fk_manufacturers__suggested_by_store_id;
ALTER TABLE public.manufacturers DROP COLUMN IF EXISTS suggested_by_store_id;
ALTER TABLE public.manufacturers DROP COLUMN IF EXISTS approved;
