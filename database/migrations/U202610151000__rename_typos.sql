-- =============================================================================
-- U202610131000__rename_typos.sql  (rollback de los renombrados)
-- Devuelve los nombres originales. El backend debe volver a los nombres viejos
-- en el mismo despliegue.
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'REQUIERE APROBACIÓN. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.product_compatibilities RENAME TO products_compatibilies;
ALTER TABLE public.store RENAME COLUMN business_name TO bussnies_name;
ALTER TABLE public.store RENAME COLUMN business_email TO bussnies_email;

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tbl, conname,
               replace(conname, 'product_compatibilities', 'products_compatibilies') AS new_name
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace AND conname LIKE '%product_compatibilities%'
    LOOP
        EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', r.tbl, r.conname, r.new_name);
    END LOOP;
    FOR r IN
        SELECT c.relname, replace(c.relname, 'product_compatibilities', 'products_compatibilies') AS new_name
        FROM pg_class c
        WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'i'
          AND c.relname LIKE '%product_compatibilities%'
          AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid = c.oid)
    LOOP
        EXECUTE format('ALTER INDEX public.%I RENAME TO %I', r.relname, r.new_name);
    END LOOP;
END $$;
-- Nota: las restricciones originales de store (store_bussnies_name_key, ...) se
-- renombraron a *business*; si necesita el nombre exacto anterior, renómbrelas a mano.
