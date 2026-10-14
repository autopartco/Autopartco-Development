-- =============================================================================
-- R__seed_holidays_co.sql  (repetible; Flyway lo reaplica si cambia su contenido)
-- Autopartco | Diseño predictivo §5.2. Solo Colombia.
--
-- 1. Festivos nacionales 2020-2035 calculados con analytics.co_national_holidays
--    (Ley 51 de 1983 + días basados en Pascua).
-- 2. Fechas comerciales recurrentes (COMMERCIAL, laborables):
--      Día de la Madre (2.º domingo de mayo), Día del Padre (3.er domingo de
--      junio), Amor y Amistad (3.er sábado de septiembre), Black Friday
--      (viernes siguiente al 4.º jueves de noviembre) y Cyber Monday.
--    Los "días sin IVA" se decretan cada año: añádalos aquí cuando se publiquen
--    (este archivo es repetible).
-- 3. Recalcula analytics.dim_date.
--
-- Requiere: V202610140930. Transacción: SÍ.
-- =============================================================================

INSERT INTO analytics.holidays (country_code, region_code, holiday_date, name, holiday_type, is_moved, is_non_working)
SELECT 'CO', NULL, h.holiday_date, h.name, 'NATIONAL', h.is_moved, true
FROM generate_series(2020, 2035) AS y
CROSS JOIN LATERAL analytics.co_national_holidays(y) AS h
ON CONFLICT ON CONSTRAINT uq_holidays__country_region_date_name
DO UPDATE SET is_moved = EXCLUDED.is_moved, is_non_working = EXCLUDED.is_non_working;

WITH years AS (SELECT y FROM generate_series(2020, 2035) AS y),
nth_weekday AS (
    -- n-ésimo día de la semana (isodow) del mes
    SELECT y,
           make_date(y, 5, 1) + ((7 - extract(isodow FROM make_date(y, 5, 1))::int) % 7) + 7  AS mothers_day,
           make_date(y, 6, 1) + ((7 - extract(isodow FROM make_date(y, 6, 1))::int) % 7) + 14 AS fathers_day,
           make_date(y, 9, 1) + ((6 - extract(isodow FROM make_date(y, 9, 1))::int + 7) % 7) + 14 AS amor_amistad,
           make_date(y, 11, 1) + ((4 - extract(isodow FROM make_date(y, 11, 1))::int + 7) % 7) + 21 AS thanksgiving
    FROM years
)
INSERT INTO analytics.holidays (country_code, region_code, holiday_date, name, holiday_type, is_moved, is_non_working)
SELECT 'CO', NULL, d, n, 'COMMERCIAL', false, false
FROM nth_weekday
CROSS JOIN LATERAL (VALUES
    (mothers_day,      'Día de la Madre'),
    (fathers_day,      'Día del Padre'),
    (amor_amistad,     'Amor y Amistad'),
    (thanksgiving + 1, 'Black Friday'),
    (thanksgiving + 4, 'Cyber Monday')
) AS v(d, n)
ON CONFLICT ON CONSTRAINT uq_holidays__country_region_date_name DO NOTHING;

SELECT analytics.populate_dim_date(DATE '2020-01-01', DATE '2035-12-31');
