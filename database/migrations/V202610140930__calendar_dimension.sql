-- =============================================================================
-- V202610140930__calendar_dimension.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.2 (Colombia)
--
-- Propósito
--   - analytics.holidays: festivos (NATIONAL, REGIONAL, COMMERCIAL). Solo
--     Colombia; country_code DEFAULT 'CO' queda para una extensión futura.
--   - analytics.dim_date: dimensión de fecha con variables de calendario
--     colombiano (festivos, puentes, quincenas, prima, días hábiles lun-sáb).
--   - analytics.easter_sunday(year): algoritmo gregoriano anónimo
--     (Meeus/Jones/Butcher).
--   - analytics.co_national_holidays(year): festivos nacionales según la Ley 51
--     de 1983 (Ley Emiliani) y los días basados en Pascua.
--   - analytics.populate_dim_date(from, to): upsert idempotente. Recalcula los
--     meses completos que tocan el rango para que business_day_of_month sea
--     correcto.
--   Esta V puebla dim_date de 2020-01-01 a 2035-12-31 sin festivos.
--   R__seed_holidays_co.sql inserta los festivos y vuelve a poblar el rango.
--   Si aplica las migraciones con psql en lugar de Flyway, ejecute también
--   el R__ al final.
--
-- Transacción: SÍ. Rollback: U202610140930__calendar_dimension.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS analytics.holidays (
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    country_code   CHAR(2)      NOT NULL DEFAULT 'CO',
    region_code    VARCHAR(10),
    holiday_date   DATE         NOT NULL,
    name           VARCHAR(120) NOT NULL,
    holiday_type   VARCHAR(15)  NOT NULL,
    is_moved       BOOLEAN      NOT NULL DEFAULT false,
    is_non_working BOOLEAN      NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_holidays PRIMARY KEY (id),
    CONSTRAINT chk_holidays__holiday_type CHECK (holiday_type IN ('NATIONAL', 'REGIONAL', 'COMMERCIAL')),
    CONSTRAINT chk_holidays__region CHECK ((holiday_type = 'REGIONAL') = (region_code IS NOT NULL)),
    CONSTRAINT uq_holidays__country_region_date_name
        UNIQUE NULLS NOT DISTINCT (country_code, region_code, holiday_date, name)
);
CREATE INDEX IF NOT EXISTS idx_holidays__date ON analytics.holidays (holiday_date);

CREATE OR REPLACE TRIGGER trg_holidays_updated_at
    BEFORE UPDATE ON analytics.holidays FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS analytics.dim_date (
    "date"                 DATE         NOT NULL,
    date_key               INTEGER      NOT NULL,
    year                   SMALLINT     NOT NULL,
    quarter                SMALLINT     NOT NULL,
    month                  SMALLINT     NOT NULL,
    month_name_es          VARCHAR(12)  NOT NULL,
    iso_year               SMALLINT     NOT NULL,
    iso_week               SMALLINT     NOT NULL,
    day_of_month           SMALLINT     NOT NULL,
    day_of_week            SMALLINT     NOT NULL,
    day_name_es            VARCHAR(12)  NOT NULL,
    day_of_year            SMALLINT     NOT NULL,
    week_of_month          SMALLINT     NOT NULL,
    is_weekend             BOOLEAN      NOT NULL,
    is_holiday_co          BOOLEAN      NOT NULL DEFAULT false,
    holiday_name           VARCHAR(120),
    is_business_day_co     BOOLEAN      NOT NULL,
    business_day_of_month  SMALLINT,
    is_long_weekend        BOOLEAN      NOT NULL DEFAULT false,
    days_to_next_holiday   SMALLINT,
    days_from_prev_holiday SMALLINT,
    is_payday_quincena     BOOLEAN      NOT NULL,
    days_from_last_payday  SMALLINT     NOT NULL,
    is_prima_period        BOOLEAN      NOT NULL,
    commercial_event       VARCHAR(120),
    CONSTRAINT pk_dim_date PRIMARY KEY ("date"),
    CONSTRAINT uq_dim_date__date_key UNIQUE (date_key),
    CONSTRAINT chk_dim_date__day_of_week CHECK (day_of_week BETWEEN 1 AND 7)
);

-- -----------------------------------------------------------------------------
-- Funciones
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION analytics.easter_sunday(p_year INT)
RETURNS DATE
LANGUAGE plpgsql IMMUTABLE PARALLEL SAFE
AS $$
DECLARE
    a INT := p_year % 19;
    b INT := p_year / 100;
    c INT := p_year % 100;
    d INT := b / 4;
    e INT := b % 4;
    f INT := (b + 8) / 25;
    g INT := (b - f + 1) / 3;
    h INT := (19 * a + b - d - g + 15) % 30;
    i INT := c / 4;
    k INT := c % 4;
    l INT := (32 + 2 * e + 2 * i - h - k) % 7;
    m INT := (a + 11 * h + 22 * l) / 451;
BEGIN
    RETURN make_date(p_year, (h + l - 7 * m + 114) / 31, ((h + l - 7 * m + 114) % 31) + 1);
END;
$$;

-- Traslado al lunes siguiente (Ley 51 de 1983). Si ya es lunes, no cambia.
CREATE OR REPLACE FUNCTION analytics.next_monday(p_date DATE)
RETURNS DATE
LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$ SELECT p_date + ((8 - extract(isodow FROM p_date)::int) % 7) $$;

CREATE OR REPLACE FUNCTION analytics.co_national_holidays(p_year INT)
RETURNS TABLE (holiday_date DATE, name TEXT, is_moved BOOLEAN)
LANGUAGE sql IMMUTABLE PARALLEL SAFE
AS $$
    WITH easter AS (SELECT analytics.easter_sunday(p_year) AS p),
    fixed(original, name) AS (
        VALUES (make_date(p_year, 1, 1),   'Año Nuevo'),
               (make_date(p_year, 5, 1),   'Día del Trabajo'),
               (make_date(p_year, 7, 20),  'Día de la Independencia'),
               (make_date(p_year, 8, 7),   'Batalla de Boyacá'),
               (make_date(p_year, 12, 8),  'Inmaculada Concepción'),
               (make_date(p_year, 12, 25), 'Navidad')
    ),
    movable(original, name) AS (
        VALUES (make_date(p_year, 1, 6),   'Día de los Reyes Magos'),
               (make_date(p_year, 3, 19),  'Día de San José'),
               (make_date(p_year, 6, 29),  'San Pedro y San Pablo'),
               (make_date(p_year, 8, 15),  'Asunción de la Virgen'),
               (make_date(p_year, 10, 12), 'Día de la Raza'),
               (make_date(p_year, 11, 1),  'Todos los Santos'),
               (make_date(p_year, 11, 11), 'Independencia de Cartagena')
    ),
    easter_fixed(original, name) AS (
        SELECT p - 3, 'Jueves Santo' FROM easter
        UNION ALL SELECT p - 2, 'Viernes Santo' FROM easter
    ),
    easter_movable(original, name) AS (
        SELECT p + 39, 'Ascensión del Señor' FROM easter
        UNION ALL SELECT p + 60, 'Corpus Christi' FROM easter
        UNION ALL SELECT p + 68, 'Sagrado Corazón de Jesús' FROM easter
    )
    SELECT original, name, false FROM fixed
    UNION ALL SELECT original, name, false FROM easter_fixed
    UNION ALL SELECT analytics.next_monday(original), name, analytics.next_monday(original) <> original FROM movable
    UNION ALL SELECT analytics.next_monday(original), name, true FROM easter_movable
$$;

CREATE OR REPLACE FUNCTION analytics.populate_dim_date(p_from DATE, p_to DATE)
RETURNS INT
LANGUAGE plpgsql
SET search_path = analytics, public, pg_temp
AS $$
DECLARE
    written INT;
BEGIN
    IF p_to < p_from THEN
        RAISE EXCEPTION 'Rango inválido: % > %', p_from, p_to;
    END IF;

    WITH days AS (
        SELECT d::date AS dt
        FROM generate_series(date_trunc('month', p_from), date_trunc('month', p_to) + interval '1 month - 1 day',
                             interval '1 day') AS d
    ),
    hol AS (
        SELECT h.holiday_date, string_agg(h.name, ' / ' ORDER BY h.name) AS name
        FROM analytics.holidays h
        WHERE h.country_code = 'CO' AND h.region_code IS NULL
          AND h.holiday_type = 'NATIONAL' AND h.is_non_working
        GROUP BY h.holiday_date
    ),
    com AS (
        SELECT h.holiday_date, string_agg(h.name, ' / ' ORDER BY h.name) AS name
        FROM analytics.holidays h
        WHERE h.country_code = 'CO' AND h.holiday_type = 'COMMERCIAL'
        GROUP BY h.holiday_date
    ),
    base AS (
        SELECT d.dt,
               extract(isodow FROM d.dt)::smallint AS dow,
               extract(day FROM d.dt)::smallint AS dom,
               (date_trunc('month', d.dt) + interval '1 month - 1 day')::date AS last_dom,
               hol.name AS holiday_name,
               hol.holiday_date IS NOT NULL AS is_hol,
               com.name AS commercial_event
        FROM days d
        LEFT JOIN hol ON hol.holiday_date = d.dt
        LEFT JOIN com ON com.holiday_date = d.dt
    ),
    calc AS (
        SELECT b.*,
               (b.dow BETWEEN 1 AND 6 AND NOT b.is_hol) AS is_bd
        FROM base b
    ),
    final AS (
        SELECT c.*,
               CASE WHEN c.is_bd THEN
                   count(*) FILTER (WHERE c.is_bd) OVER (PARTITION BY date_trunc('month', c.dt) ORDER BY c.dt)
               END AS bd_of_month
        FROM calc c
    )
    INSERT INTO analytics.dim_date AS t (
        "date", date_key, year, quarter, month, month_name_es, iso_year, iso_week, day_of_month,
        day_of_week, day_name_es, day_of_year, week_of_month, is_weekend, is_holiday_co, holiday_name,
        is_business_day_co, business_day_of_month, is_long_weekend, days_to_next_holiday,
        days_from_prev_holiday, is_payday_quincena, days_from_last_payday, is_prima_period, commercial_event
    )
    SELECT f.dt,
           to_char(f.dt, 'YYYYMMDD')::int,
           extract(year FROM f.dt)::smallint,
           extract(quarter FROM f.dt)::smallint,
           extract(month FROM f.dt)::smallint,
           (ARRAY['enero','febrero','marzo','abril','mayo','junio','julio','agosto',
                  'septiembre','octubre','noviembre','diciembre'])[extract(month FROM f.dt)::int],
           extract(isoyear FROM f.dt)::smallint,
           extract(week FROM f.dt)::smallint,
           f.dom,
           f.dow,
           (ARRAY['lunes','martes','miércoles','jueves','viernes','sábado','domingo'])[f.dow],
           extract(doy FROM f.dt)::smallint,
           ((f.dom - 1) / 7 + 1)::smallint,
           f.dow IN (6, 7),
           f.is_hol,
           f.holiday_name,
           f.is_bd,
           f.bd_of_month::smallint,
           (f.dow IN (1, 5) AND f.is_hol)
             OR (f.dow = 6 AND EXISTS (SELECT 1 FROM hol WHERE hol.holiday_date IN (f.dt + 2, f.dt - 1)))
             OR (f.dow = 7 AND EXISTS (SELECT 1 FROM hol WHERE hol.holiday_date IN (f.dt + 1, f.dt - 2))),
           (SELECT min(hol.holiday_date) FROM hol WHERE hol.holiday_date > f.dt) - f.dt,
           f.dt - (SELECT max(hol.holiday_date) FROM hol WHERE hol.holiday_date < f.dt),
           f.dom = 15 OR f.dt = f.last_dom,
           CASE WHEN f.dom = 15 OR f.dt = f.last_dom THEN 0
                WHEN f.dom > 15 THEN f.dom - 15
                ELSE f.dom END,
           (extract(month FROM f.dt) = 6 AND f.dom >= 15) OR (extract(month FROM f.dt) = 12 AND f.dom <= 20),
           f.commercial_event
    FROM final f
    WHERE f.dt BETWEEN date_trunc('month', p_from) AND (date_trunc('month', p_to) + interval '1 month - 1 day')
    ON CONFLICT ("date") DO UPDATE SET
        is_holiday_co          = EXCLUDED.is_holiday_co,
        holiday_name           = EXCLUDED.holiday_name,
        is_business_day_co     = EXCLUDED.is_business_day_co,
        business_day_of_month  = EXCLUDED.business_day_of_month,
        is_long_weekend        = EXCLUDED.is_long_weekend,
        days_to_next_holiday   = EXCLUDED.days_to_next_holiday,
        days_from_prev_holiday = EXCLUDED.days_from_prev_holiday,
        commercial_event       = EXCLUDED.commercial_event;

    GET DIAGNOSTICS written = ROW_COUNT;
    RETURN written;
END;
$$;

COMMENT ON FUNCTION analytics.populate_dim_date(DATE, DATE) IS
    'Upsert de dim_date para los meses completos del rango. Idempotente. Ejecútelo de nuevo tras cambiar analytics.holidays.';

SELECT analytics.populate_dim_date(DATE '2020-01-01', DATE '2035-12-31');
