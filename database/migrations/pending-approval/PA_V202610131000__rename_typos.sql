-- REQUIERE APROBACIÓN: renombrados (rompen el backend si no se despliega a la vez)
-- =============================================================================
-- V202610131000__rename_typos.sql
-- Autopartco | PostgreSQL 16+ | Auditoría punto 22
--
-- Qué hace: corrige erratas de nombres.
--   products_compatibilies   -> product_compatibilities
--   store.bussnies_name      -> store.business_name
--   store.bussnies_email     -> store.business_email
--   Índices y restricciones que llevan el nombre viejo se renombran también.
--   Los índices funcionales (uq_store__business_*_lower) siguen funcionando:
--   PostgreSQL referencia las columnas por número, no por nombre.
-- NO incluye (más invasivos; evaluar aparte): store -> stores, make -> makes,
--   model -> models, types_accounts -> account_types, collab_store_id ->
--   store_id, accounts.id_person -> person_id.
--
-- Backend, en el MISMO despliegue:
--   Product.java  @CollectionTable(name = "product_compatibilities")
--                 (o la entidad ProductFitment, H5)
--   Store.java    @Column(name = "business_name"), @Column(name = "business_email")
--   SQL nativo:   ListingQueries (s.bussnies_name), SaleQueries y cualquier
--                 consulta JDBC con products_compatibilies o bussnies_*.
--   Buscar con: rg -n "bussnies_|products_compatibilies" backend/src
-- Riesgo: con ddl-auto: update y entidades sin actualizar, Hibernate VOLVERÍA a
--   crear las columnas/tabla viejas vacías. Aplique solo con ddl-auto: validate.
-- Respaldo: pg_dump --schema-only "$DATABASE_URL" > schema_pre_rename.sql
-- Al aprobar: muévalo a database/migrations/ con un timestamp NUEVO (posterior
--   a la última aplicada).
-- Transacción: SÍ. Rollback: U202610131000__rename_typos.sql
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'REQUIERE APROBACIÓN. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.products_compatibilies RENAME TO product_compatibilities;
ALTER TABLE public.store RENAME COLUMN bussnies_name TO business_name;
ALTER TABLE public.store RENAME COLUMN bussnies_email TO business_email;

-- Restricciones e índices con el nombre antiguo.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT conrelid::regclass AS tbl, conname,
               replace(replace(conname, 'products_compatibilies', 'product_compatibilities'), 'bussnies', 'business') AS new_name
        FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace
          AND (conname LIKE '%products_compatibilies%' OR conname LIKE '%bussnies%')
    LOOP
        EXECUTE format('ALTER TABLE %s RENAME CONSTRAINT %I TO %I', r.tbl, r.conname, r.new_name);
    END LOOP;

    FOR r IN
        SELECT c.relname,
               replace(replace(c.relname, 'products_compatibilies', 'product_compatibilities'), 'bussnies', 'business') AS new_name
        FROM pg_class c
        WHERE c.relnamespace = 'public'::regnamespace AND c.relkind = 'i'
          AND (c.relname LIKE '%products_compatibilies%' OR c.relname LIKE '%bussnies%')
          AND NOT EXISTS (SELECT 1 FROM pg_constraint k WHERE k.conindid = c.oid)
    LOOP
        EXECUTE format('ALTER INDEX public.%I RENAME TO %I', r.relname, r.new_name);
    END LOOP;
END $$;
