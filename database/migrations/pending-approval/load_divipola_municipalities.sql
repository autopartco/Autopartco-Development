-- REQUIERE APROBACIÓN (A10): datos externos; verificar la licencia antes de cargar
-- =============================================================================
-- load_divipola_municipalities.sql   (script manual de psql, NO es una migración de Flyway)
-- Autopartco | Diseño predictivo §5.1
--
-- Carga los municipios de DIVIPOLA desde el CSV oficial del DANE (portal Datos
-- Abiertos Colombia / Geoportal DANE, "Codificación de la División
-- Político-Administrativa de Colombia"). No se incluyen datos en el
-- repositorio. Los códigos salen siempre del archivo oficial.
--
-- Pasos (los ejecuta el usuario):
--   1. Descargue el CSV oficial y confirme su licencia de uso.
--   2. Normalícelo a UTF-8 con estas columnas y encabezado (el nombre de las
--      columnas del archivo oficial varía entre versiones; ajústelo):
--        cod_dpto,cod_mpio,nom_mpio,latitud,longitud
--      cod_mpio = código de 5 dígitos (incluye los 2 del departamento).
--   3. Ejecute:
--        psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -v csv=/ruta/divipola_municipios.csv \
--             -f database/migrations/pending-approval/load_divipola_municipalities.sql
--   4. Capitales: el script marca como capital el municipio con código
--      <dpto>001, que es la convención DIVIPOLA. Bogotá D.C. es 11001.
--      Cundinamarca (25) no tiene municipio 25001 capital porque su capital es
--      Bogotá: el script no marca capital en 25. Verifique el resultado.
--   Población y área metropolitana: carguelas aparte cuando haya una fuente
--   aprobada (DANE, proyecciones de población).
-- Requiere: V202610140920 y R__seed_geo_divipola.sql (departamentos).
-- =============================================================================

\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE stg_divipola (
    cod_dpto TEXT,
    cod_mpio TEXT,
    nom_mpio TEXT,
    latitud  TEXT,
    longitud TEXT
) ON COMMIT DROP;

\copy stg_divipola FROM :'csv' WITH (FORMAT csv, HEADER true, ENCODING 'UTF8')

-- Validación: códigos de 5 dígitos coherentes con un departamento existente.
DO $$
DECLARE
    bad BIGINT;
BEGIN
    SELECT count(*) INTO bad
    FROM stg_divipola s
    WHERE lpad(btrim(s.cod_mpio), 5, '0') !~ '^[0-9]{5}$'
       OR left(lpad(btrim(s.cod_mpio), 5, '0'), 2) <> lpad(btrim(s.cod_dpto), 2, '0')
       OR NOT EXISTS (SELECT 1 FROM public.geo_departments d WHERE d.code = lpad(btrim(s.cod_dpto), 2, '0'));
    IF bad > 0 THEN
        RAISE EXCEPTION '% filas del CSV con código inválido o departamento inexistente', bad;
    END IF;
END $$;

INSERT INTO public.geo_municipalities (code, department_code, name, is_department_capital, latitude, longitude)
SELECT lpad(btrim(cod_mpio), 5, '0'),
       lpad(btrim(cod_dpto), 2, '0'),
       btrim(nom_mpio),
       right(lpad(btrim(cod_mpio), 5, '0'), 3) = '001' AND lpad(btrim(cod_dpto), 2, '0') <> '25',
       nullif(replace(btrim(latitud), ',', '.'), '')::numeric(10,7),
       nullif(replace(btrim(longitud), ',', '.'), '')::numeric(10,7)
FROM stg_divipola
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    latitude = coalesce(EXCLUDED.latitude, public.geo_municipalities.latitude),
    longitude = coalesce(EXCLUDED.longitude, public.geo_municipalities.longitude);

SELECT department_code, count(*) AS municipios FROM public.geo_municipalities GROUP BY 1 ORDER BY 1;

COMMIT;
