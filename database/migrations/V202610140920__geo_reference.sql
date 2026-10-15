-- =============================================================================
-- V202610140920__geo_reference.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.1 (Colombia, DIVIPOLA)
--
-- Propósito
--   - public.geo_departments (CHAR(2)) y public.geo_municipalities (CHAR(5)),
--     con country_code DEFAULT 'CO' para una extensión futura. Solo se
--     siembra Colombia.
--   - locations.municipality_code y order_addresses.municipality_code:
--     nullable y aditivas. Location.java no las mapea; los INSERT actuales
--     dejan NULL.
--   - Vista analytics.dim_geography.
-- Semilla: pending-approval/R__seed_geo_divipola.sql (A10: verificar la
--   licencia de los datos abiertos del DANE antes de cargar los ~1.100
--   municipios).
--
-- Transacción: SÍ. Bloqueos: ACCESS EXCLUSIVE breve en locations y
--   order_addresses (ADD COLUMN nullable, sin reescritura). El índice de
--   locations se crea sin CONCURRENTLY porque la columna es nueva y está vacía.
-- Rollback: U202610140920__geo_reference.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.geo_departments (
    code           CHAR(2)      NOT NULL,
    name           VARCHAR(100) NOT NULL,
    natural_region VARCHAR(20)  NOT NULL,
    country_code   CHAR(2)      NOT NULL DEFAULT 'CO',
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_geo_departments PRIMARY KEY (code),
    CONSTRAINT chk_geo_departments__code CHECK (code ~ '^[0-9]{2}$'),
    CONSTRAINT chk_geo_departments__natural_region CHECK (natural_region IN
        ('ANDINA', 'CARIBE', 'PACIFICA', 'ORINOQUIA', 'AMAZONIA', 'INSULAR')),
    CONSTRAINT chk_geo_departments__country_code CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE IF NOT EXISTS public.geo_municipalities (
    code                  CHAR(5)       NOT NULL,
    department_code       CHAR(2)       NOT NULL,
    name                  VARCHAR(150)  NOT NULL,
    is_department_capital BOOLEAN       NOT NULL DEFAULT false,
    metro_area_code       VARCHAR(10),
    latitude              NUMERIC(10,7),
    longitude             NUMERIC(10,7),
    population            INTEGER,
    population_year       SMALLINT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_geo_municipalities PRIMARY KEY (code),
    CONSTRAINT fk_geo_municipalities__department_code FOREIGN KEY (department_code)
        REFERENCES public.geo_departments (code) ON DELETE RESTRICT,
    CONSTRAINT chk_geo_municipalities__code CHECK (code ~ '^[0-9]{5}$' AND left(code, 2) = department_code),
    CONSTRAINT chk_geo_municipalities__latitude CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT chk_geo_municipalities__longitude CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT chk_geo_municipalities__population CHECK (population >= 0),
    CONSTRAINT chk_geo_municipalities__population_year CHECK (population_year BETWEEN 1900 AND 2100)
);

CREATE INDEX IF NOT EXISTS idx_geo_municipalities__department_code
    ON public.geo_municipalities (department_code);
CREATE UNIQUE INDEX IF NOT EXISTS uq_geo_municipalities__department_capital
    ON public.geo_municipalities (department_code) WHERE is_department_capital;

CREATE OR REPLACE TRIGGER trg_geo_departments_updated_at
    BEFORE UPDATE ON public.geo_departments FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
CREATE OR REPLACE TRIGGER trg_geo_municipalities_updated_at
    BEFORE UPDATE ON public.geo_municipalities FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Tablas existentes (aditivo) -------------------------------------------------------
ALTER TABLE public.locations
    ADD COLUMN IF NOT EXISTS municipality_code CHAR(5);
ALTER TABLE public.order_addresses
    ADD COLUMN IF NOT EXISTS municipality_code CHAR(5);

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_locations__municipality_code'
                   AND conrelid = 'public.locations'::regclass) THEN
        ALTER TABLE public.locations ADD CONSTRAINT fk_locations__municipality_code
            FOREIGN KEY (municipality_code) REFERENCES public.geo_municipalities (code) ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_addresses__municipality_code'
                   AND conrelid = 'public.order_addresses'::regclass) THEN
        ALTER TABLE public.order_addresses ADD CONSTRAINT fk_order_addresses__municipality_code
            FOREIGN KEY (municipality_code) REFERENCES public.geo_municipalities (code) ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_locations__municipality_code
    ON public.locations (municipality_code) WHERE municipality_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_order_addresses__municipality_code
    ON public.order_addresses (municipality_code) WHERE municipality_code IS NOT NULL;

-- Vista analítica --------------------------------------------------------------------
CREATE OR REPLACE VIEW analytics.dim_geography AS
SELECT m.code       AS municipality_code,
       m.name       AS municipality_name,
       d.code       AS department_code,
       d.name       AS department_name,
       d.natural_region,
       d.country_code,
       m.is_department_capital,
       m.metro_area_code,
       m.latitude,
       m.longitude,
       m.population
FROM public.geo_municipalities m
JOIN public.geo_departments d ON d.code = m.department_code;
