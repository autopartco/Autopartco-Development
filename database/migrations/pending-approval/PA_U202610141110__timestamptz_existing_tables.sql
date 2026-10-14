-- REQUIERE APROBACIÓN
-- =============================================================================
-- U202610141110__timestamptz_existing_tables.sql  (rollback de A3)
-- Devuelve a TIMESTAMP (valor en UTC) solo las columnas registradas en
-- public.migration_a3_converted_columns. Las vistas dependientes se guardan,
-- se eliminan y se recrean igual que en la V. El backend debe volver a
-- LocalDateTime en el mismo despliegue.
-- Respaldo: pg_dump -Fc "$DATABASE_URL" -f autopartco_pre_u_a3.dump
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'REQUIERE APROBACIÓN. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '30s';
SET LOCAL statement_timeout = 0;
SET LOCAL TimeZone = 'UTC';

CREATE TEMP TABLE a3_views (seq INT, kind "char", qualified TEXT, ddl TEXT, indexes TEXT[]) ON COMMIT DROP;

DO $$
DECLARE
    r RECORD;
BEGIN
    INSERT INTO a3_views
    WITH RECURSIVE target_tables AS (
        SELECT DISTINCT format('public.%I', table_name)::regclass::oid AS oid
        FROM public.migration_a3_converted_columns
    ),
    deps AS (
        SELECT DISTINCT rw.ev_class AS view_oid
        FROM pg_depend d JOIN pg_rewrite rw ON rw.oid = d.objid
        WHERE d.refobjid IN (SELECT oid FROM target_tables) AND rw.ev_class <> d.refobjid
        UNION
        SELECT rw.ev_class FROM deps
        JOIN pg_depend d ON d.refobjid = deps.view_oid
        JOIN pg_rewrite rw ON rw.oid = d.objid
        WHERE rw.ev_class <> d.refobjid
    )
    SELECT row_number() OVER (ORDER BY c.oid)::int, c.relkind, format('%I.%I', n.nspname, c.relname),
           CASE c.relkind
               WHEN 'v' THEN format('CREATE VIEW %I.%I AS %s', n.nspname, c.relname, rtrim(pg_get_viewdef(c.oid), ';'))
               ELSE format('CREATE MATERIALIZED VIEW %I.%I AS %s', n.nspname, c.relname, rtrim(pg_get_viewdef(c.oid), ';'))
           END,
           ARRAY(SELECT pg_get_indexdef(i.indexrelid) FROM pg_index i WHERE i.indrelid = c.oid)
    FROM deps JOIN pg_class c ON c.oid = deps.view_oid JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind IN ('v', 'm');

    FOR r IN SELECT * FROM a3_views ORDER BY seq DESC LOOP
        EXECUTE format('DROP %s %s', CASE r.kind WHEN 'v' THEN 'VIEW' ELSE 'MATERIALIZED VIEW' END, r.qualified);
    END LOOP;

    FOR r IN SELECT table_name, column_name FROM public.migration_a3_converted_columns LOOP
        EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I TYPE timestamp USING %I AT TIME ZONE %L',
                       r.table_name, r.column_name, r.column_name, public.legacy_source_tz());
    END LOOP;

    FOR r IN SELECT * FROM a3_views ORDER BY seq LOOP
        EXECUTE r.ddl;
        IF r.kind = 'm' THEN
            FOR i IN 1 .. coalesce(array_length(r.indexes, 1), 0) LOOP
                EXECUTE r.indexes[i];
            END LOOP;
        END IF;
    END LOOP;
END $$;

DROP TABLE public.migration_a3_converted_columns;
