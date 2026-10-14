-- REQUIERE APROBACIÓN (A3): cambio de tipo y reescritura de tablas
-- =============================================================================
-- V202610141110__timestamptz_existing_tables.sql
-- Autopartco | PostgreSQL 16+ | ADR 0001 §2.5 / Diseño predictivo H4
--
-- Qué hace: convierte TODAS las columnas TIMESTAMP (sin zona) de las tablas
--   de public a TIMESTAMPTZ con USING <col> AT TIME ZONE 'UTC'. Excluye
--   flyway_schema_history. Las columnas convertidas quedan registradas en
--   public.migration_a3_converted_columns para que el rollback sea exacto
--   (promotions.starts_at/ends_at y las columnas nuevas ya eran timestamptz
--   y no se tocan).
--   Las vistas y vistas materializadas que dependen de esas columnas (p. ej.
--   analytics.mv_customer_cohorts y mv_customer_rfm) se guardan, se eliminan
--   y se recrean con su definición e índices. Las materializadas se vuelven
--   a poblar.
--   Las restricciones CHECK y los índices se reescriben automáticamente.
--
-- Supuesto: los valores actuales están en autopartco.legacy_source_tz.
--   Ejecútelo con autopartco.legacy_source_tz = 'America/Bogota' (zona de la
--   JVM, ADR 0002). La BD de desarrollo es desechable (decisión U1 del ADR):
--   las filas con zonas mezcladas no se corrigen una a una.
--
-- Backend (en el MISMO despliegue, ADR 0002):
--   - Se conservan las entidades con LocalDateTime (no se cambian a
--     OffsetDateTime/Instant).
--   - La JVM con zona America/Bogota se fija en BackendApplication.main.
--   - Requiere en backend: la JVM con zona America/Bogota fijada en
--     BackendApplication.main (ADR 0002) y ddl-auto: validate; no fijar
--     hibernate.jdbc.time_zone.
--
-- Impacto operativo: ACCESS EXCLUSIVE y reescritura de cada tabla. Las
--   lecturas y escrituras quedan bloqueadas mientras dura. Ejecútelo con la
--   aplicación detenida.
-- Respaldo previo OBLIGATORIO:
--   pg_dump -Fc "$DATABASE_URL" -f autopartco_pre_a3.dump
-- Guard: requiere SET autopartco.allow_destructive = 'on' en la sesión.
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo.
-- Transacción: SÍ (todo o nada). Rollback: U202610141110__timestamptz_existing_tables.sql
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'REQUIERE APROBACIÓN (A3). Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '30s';
SET LOCAL statement_timeout = 0;
SET LOCAL TimeZone = 'UTC';

CREATE TABLE IF NOT EXISTS public.migration_a3_converted_columns (
    table_name  TEXT        NOT NULL,
    column_name TEXT        NOT NULL,
    converted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_migration_a3_converted_columns PRIMARY KEY (table_name, column_name)
);

CREATE TEMP TABLE a3_views (
    seq       INT,
    view_oid  OID,
    kind      "char",
    qualified TEXT,
    ddl       TEXT,
    indexes   TEXT[]
) ON COMMIT DROP;

DO $$
DECLARE
    r RECORD;
BEGIN
    -- 1. Vistas (y vistas sobre vistas) que dependen de tablas con columnas a convertir.
    INSERT INTO a3_views (seq, view_oid, kind, qualified, ddl, indexes)
    WITH RECURSIVE target_tables AS (
        SELECT DISTINCT c.oid
        FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND c.relname <> 'flyway_schema_history'
          AND a.atttypid = 'timestamp without time zone'::regtype
          AND a.attnum > 0 AND NOT a.attisdropped
    ),
    deps AS (
        SELECT DISTINCT rw.ev_class AS view_oid
        FROM pg_depend d
        JOIN pg_rewrite rw ON rw.oid = d.objid
        WHERE d.refobjid IN (SELECT oid FROM target_tables) AND rw.ev_class <> d.refobjid
        UNION
        SELECT rw.ev_class
        FROM deps
        JOIN pg_depend d ON d.refobjid = deps.view_oid
        JOIN pg_rewrite rw ON rw.oid = d.objid
        WHERE rw.ev_class <> d.refobjid
    )
    SELECT row_number() OVER (ORDER BY c.oid)::int, c.oid, c.relkind,
           format('%I.%I', n.nspname, c.relname),
           CASE c.relkind
               WHEN 'v' THEN format('CREATE VIEW %I.%I AS %s', n.nspname, c.relname, rtrim(pg_get_viewdef(c.oid), ';'))
               ELSE format('CREATE MATERIALIZED VIEW %I.%I AS %s', n.nspname, c.relname,
                           rtrim(pg_get_viewdef(c.oid), ';'))
           END,
           ARRAY(SELECT pg_get_indexdef(i.indexrelid) FROM pg_index i WHERE i.indrelid = c.oid)
    FROM deps
    JOIN pg_class c ON c.oid = deps.view_oid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE c.relkind IN ('v', 'm');

    FOR r IN SELECT * FROM a3_views ORDER BY seq DESC LOOP
        EXECUTE format('DROP %s %s', CASE r.kind WHEN 'v' THEN 'VIEW' ELSE 'MATERIALIZED VIEW' END, r.qualified);
    END LOOP;

    -- 2. Conversión.
    FOR r IN
        SELECT c.oid::regclass AS tbl, c.relname, a.attname
        FROM pg_attribute a
        JOIN pg_class c ON c.oid = a.attrelid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
          AND c.relname <> 'flyway_schema_history'
          AND a.atttypid = 'timestamp without time zone'::regtype
          AND a.attnum > 0 AND NOT a.attisdropped
        ORDER BY c.relname, a.attnum
    LOOP
        EXECUTE format('ALTER TABLE %s ALTER COLUMN %I TYPE timestamptz USING %I AT TIME ZONE %L',
                       r.tbl, r.attname, r.attname, public.legacy_source_tz());
        INSERT INTO public.migration_a3_converted_columns (table_name, column_name)
        VALUES (r.relname, r.attname) ON CONFLICT DO NOTHING;
    END LOOP;

    -- 3. Recreación de vistas en orden de creación original.
    FOR r IN SELECT * FROM a3_views ORDER BY seq LOOP
        EXECUTE r.ddl;
        IF r.kind = 'm' THEN
            FOR i IN 1 .. coalesce(array_length(r.indexes, 1), 0) LOOP
                EXECUTE r.indexes[i];
            END LOOP;
        END IF;
    END LOOP;
END $$;

COMMENT ON TABLE public.migration_a3_converted_columns IS
    'Columnas convertidas a timestamptz por A3; lo usa el rollback U202610141110.';
