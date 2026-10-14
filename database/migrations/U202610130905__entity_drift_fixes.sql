-- =============================================================================
-- U202610130905__entity_drift_fixes.sql  (rollback de V202610130905)
-- DESTRUCTIVO: requiere aprobación.
--   - Borra max_branches/max_products/max_users/capabilities de seller_plans
--     (pérdida de datos; además Plan.java dejaría de funcionar sin ddl-auto).
--   - Reduce persons.number_phone a VARCHAR(10): aborta si hay valores largos.
-- Respaldo previo recomendado:
--   pg_dump --data-only -t public.seller_plans -t public.persons "$DATABASE_URL" > backup_0905.sql
-- psql -v ON_ERROR_STOP=1 --single-transaction \
--   -c "SET autopartco.allow_destructive = 'on'" -f <archivo>
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on en la sesión.';
    END IF;
    IF EXISTS (SELECT 1 FROM public.persons WHERE length(number_phone) > 10) THEN
        RAISE EXCEPTION 'Hay persons.number_phone con más de 10 caracteres; no se puede reducir el tipo.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.inventory ALTER COLUMN price SET DEFAULT 0;
ALTER TABLE public.persons ALTER COLUMN number_phone TYPE VARCHAR(10);
ALTER TABLE public.seller_plans
    DROP COLUMN IF EXISTS capabilities,
    DROP COLUMN IF EXISTS max_users,
    DROP COLUMN IF EXISTS max_products,
    DROP COLUMN IF EXISTS max_branches;
