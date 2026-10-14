-- =============================================================================
-- V202610140910__schemas_and_conventions.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §3, §4, §10.2
--
-- Propósito
--   - Esquemas tracking, analytics, ml y privacy. Un esquema nuevo no da
--     USAGE a PUBLIC, así que solo el dueño (rol de migración) accede hasta
--     que se apliquen los GRANT de roles (A6, pending-approval).
--   - public.ensure_monthly_partitions(parent, from, months): crea las
--     particiones mensuales que falten (idempotente).
--   - Funciones de tiempo para la transición TIMESTAMP -> TIMESTAMPTZ (H4):
--       public.legacy_source_tz()                 zona de origen (autopartco.legacy_source_tz; por defecto UTC)
--       public.legacy_ts(timestamp|timestamptz)   -> timestamptz
--       public.legacy_bound(timestamptz, <tipo>)  -> límite sargable del tipo de la columna
--       public.bogota_date(timestamp|timestamptz) -> DATE local America/Bogota
--     Las funciones analíticas y los rellenos usan estas sobrecargas. Si se
--     aplica A3 (timestamptz), siguen siendo correctas sin reescribirlas.
--     ANTES de migrar, fije la zona real de la JVM, por ejemplo:
--       ALTER DATABASE <bd> SET autopartco.legacy_source_tz = 'America/Bogota';
--   - public.forbid_update_delete() ya existe (V202610130930).
--   - detach_and_drop_partitions_older_than es destructiva (A9) y está en
--     pending-approval/.
--
-- Transacción: SÍ. Rollback: U202610140910__schemas_and_conventions.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE SCHEMA IF NOT EXISTS tracking;
CREATE SCHEMA IF NOT EXISTS analytics;
CREATE SCHEMA IF NOT EXISTS ml;
CREATE SCHEMA IF NOT EXISTS privacy;

COMMENT ON SCHEMA tracking  IS 'Eventos de comportamiento crudos (sin PII directa; solo subject_key). Retención 25 meses.';
COMMENT ON SCHEMA analytics IS 'Dimensiones y hechos agregados sin PII. Poblado por funciones idempotentes.';
COMMENT ON SCHEMA ml        IS 'Registro de modelos, datasets, features, predicciones y resultados.';
COMMENT ON SCHEMA privacy   IS 'Seudónimos person_id -> subject_key. Solo backend (core.identity).';

-- -----------------------------------------------------------------------------
-- Tiempo
-- -----------------------------------------------------------------------------
-- Zona de origen de las columnas TIMESTAMP heredadas. BaseEntity las escribe
-- con LocalDateTime.now() en la zona de la JVM (probablemente America/Bogota)
-- y DEFAULT now() en la zona de la sesión. Se configura con
--   ALTER DATABASE <bd> SET autopartco.legacy_source_tz = 'America/Bogota';
-- (o SET por sesión). Si no se fija, se asume UTC.
CREATE OR REPLACE FUNCTION public.legacy_source_tz()
RETURNS TEXT LANGUAGE sql STABLE PARALLEL SAFE
AS $$ SELECT coalesce(nullif(current_setting('autopartco.legacy_source_tz', true), ''), 'UTC') $$;

-- TIMESTAMP heredado -> instante (timestamptz), según legacy_source_tz.
CREATE OR REPLACE FUNCTION public.legacy_ts(ts TIMESTAMP)
RETURNS TIMESTAMPTZ LANGUAGE sql STABLE PARALLEL SAFE
AS $$ SELECT ts AT TIME ZONE public.legacy_source_tz() $$;

-- Tras A3 (columnas ya timestamptz) la sobrecarga es la identidad.
CREATE OR REPLACE FUNCTION public.legacy_ts(ts TIMESTAMPTZ)
RETURNS TIMESTAMPTZ LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT ts $$;

-- Límite de filtro con el MISMO tipo que la columna, para que la comparación
-- sea sargable (columna desnuda >= límite). El segundo argumento solo fija el
-- tipo: en plpgsql pase una variable declarada <tabla>.<columna>%TYPE.
CREATE OR REPLACE FUNCTION public.legacy_bound(p TIMESTAMPTZ, type_sample TIMESTAMP)
RETURNS TIMESTAMP LANGUAGE sql STABLE PARALLEL SAFE
AS $$ SELECT p AT TIME ZONE public.legacy_source_tz() $$;

CREATE OR REPLACE FUNCTION public.legacy_bound(p TIMESTAMPTZ, type_sample TIMESTAMPTZ)
RETURNS TIMESTAMPTZ LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT p $$;

CREATE OR REPLACE FUNCTION public.bogota_date(ts TIMESTAMPTZ)
RETURNS DATE LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT (ts AT TIME ZONE 'America/Bogota')::date $$;

CREATE OR REPLACE FUNCTION public.bogota_date(ts TIMESTAMP)
RETURNS DATE LANGUAGE sql STABLE PARALLEL SAFE
AS $$ SELECT (public.legacy_ts(ts) AT TIME ZONE 'America/Bogota')::date $$;

COMMENT ON FUNCTION public.legacy_ts(TIMESTAMP) IS
    'TIMESTAMP heredado -> timestamptz interpretado en autopartco.legacy_source_tz (por defecto UTC).';
COMMENT ON FUNCTION public.bogota_date(TIMESTAMPTZ) IS
    'Fecha de negocio local America/Bogota (diseño §3).';

-- -----------------------------------------------------------------------------
-- Particiones mensuales
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.ensure_monthly_partitions(p_parent regclass, p_from DATE, p_months INT)
RETURNS INT
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    key_type   regtype;
    schema_nm  TEXT;
    table_nm   TEXT;
    month_from DATE;
    month_to   DATE;
    part_nm    TEXT;
    lower_lit  TEXT;
    upper_lit  TEXT;
    created    INT := 0;
BEGIN
    SELECT a.atttypid::regtype, n.nspname, c.relname
    INTO key_type, schema_nm, table_nm
    FROM pg_partitioned_table pt
    JOIN pg_class c ON c.oid = pt.partrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_attribute a ON a.attrelid = pt.partrelid AND a.attnum = pt.partattrs[0]
    WHERE pt.partrelid = p_parent AND pt.partstrat = 'r' AND pt.partnatts = 1;

    IF key_type IS NULL THEN
        RAISE EXCEPTION '% no es una tabla particionada por rango de una columna', p_parent;
    END IF;
    IF key_type NOT IN ('date'::regtype, 'timestamp with time zone'::regtype) THEN
        RAISE EXCEPTION 'Tipo de clave de partición no soportado: %', key_type;
    END IF;

    FOR i IN 0 .. greatest(p_months, 1) - 1 LOOP
        month_from := (date_trunc('month', p_from) + make_interval(months => i))::date;
        month_to   := (month_from + interval '1 month')::date;
        part_nm    := format('%s_p%s', table_nm, to_char(month_from, 'YYYYMM'));

        IF to_regclass(format('%I.%I', schema_nm, part_nm)) IS NULL THEN
            IF key_type = 'date'::regtype THEN
                lower_lit := to_char(month_from, 'YYYY-MM-DD');
                upper_lit := to_char(month_to, 'YYYY-MM-DD');
            ELSE
                lower_lit := to_char(month_from, 'YYYY-MM-DD') || ' 00:00:00+00';
                upper_lit := to_char(month_to, 'YYYY-MM-DD') || ' 00:00:00+00';
            END IF;
            EXECUTE format('CREATE TABLE %I.%I PARTITION OF %s FOR VALUES FROM (%L) TO (%L)',
                           schema_nm, part_nm, p_parent, lower_lit, upper_lit);
            created := created + 1;
        END IF;
    END LOOP;
    RETURN created;
END;
$$;

COMMENT ON FUNCTION public.ensure_monthly_partitions(regclass, DATE, INT) IS
    'Crea <tabla>_pYYYYMM que falten (clave date o timestamptz, límites en UTC). Idempotente. Si la partición default tiene filas del rango, falla: muévalas antes.';
