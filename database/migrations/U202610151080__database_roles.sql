-- =============================================================================
-- U202610141510__database_roles.sql  (rollback de A6)
-- Revoca los permisos y elimina los roles. Antes, la aplicación debe volver a
-- conectarse con su usuario anterior; si no, deja de funcionar.
-- DROP ROLE falla si el rol aún posee objetos (es lo deseado).
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'REQUIERE APROBACIÓN. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DO $$
DECLARE
    r TEXT;
BEGIN
    FOREACH r IN ARRAY ARRAY['autopartco_ml', 'autopartco_analyst', 'autopartco_app', 'autopartco_migrator'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            -- Primero se devuelve la propiedad al usuario actual: DROP OWNED BORRARÍA los objetos.
            EXECUTE format('REASSIGN OWNED BY %I TO %I', r, current_user);
            EXECUTE format('DROP OWNED BY %I', r);   -- ya solo quita permisos y privilegios por defecto
            EXECUTE format('DROP ROLE %I', r);
        END IF;
    END LOOP;
END $$;

GRANT CREATE ON SCHEMA public TO PUBLIC;
