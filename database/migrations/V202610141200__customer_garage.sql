-- =============================================================================
-- V202610141200__customer_garage.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.1
--
-- Propósito
--   - versions: atributos técnicos (años, motor, cilindrada, combustible,
--     transmisión, carrocería). Aditivo y nullable; versions no tiene entidad JPA.
--   - public.customer_vehicles: garaje del cliente. El VIN es opcional y es
--     dato personal. La placa NO se guarda.
--   - public.vehicle_odometer_readings: lecturas de kilometraje (solo
--     inserción, salvo is_suspect, que puede recalcularse).
--   - FK order_details.customer_vehicle_id -> customer_vehicles. Es la etiqueta
--     más valiosa para los modelos de compatibilidad, mantenimiento y demanda
--     por vehículo. Se crea NOT VALID; la valida V202610141201.
--
-- Transacción: SÍ. Rollback: U202610141200__customer_garage.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE OR REPLACE FUNCTION pg_temp.add_constraint(p_table regclass, p_name TEXT, p_ddl TEXT)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = p_table AND conname = p_name) THEN
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', p_table, p_name, p_ddl);
    END IF;
END;
$$;

-- versions (existente; aditivo) ------------------------------------------------------
ALTER TABLE public.versions
    ADD COLUMN IF NOT EXISTS year_from       SMALLINT,
    ADD COLUMN IF NOT EXISTS year_to         SMALLINT,
    ADD COLUMN IF NOT EXISTS engine_code     VARCHAR(30),
    ADD COLUMN IF NOT EXISTS displacement_cc INTEGER,
    ADD COLUMN IF NOT EXISTS fuel_type       VARCHAR(20),
    ADD COLUMN IF NOT EXISTS transmission    VARCHAR(20),
    ADD COLUMN IF NOT EXISTS body_type       VARCHAR(30);

SELECT pg_temp.add_constraint('public.versions', 'chk_versions__year_from', 'CHECK (year_from BETWEEN 1950 AND 2100)');
SELECT pg_temp.add_constraint('public.versions', 'chk_versions__year_to', 'CHECK (year_to BETWEEN 1950 AND 2100)');
SELECT pg_temp.add_constraint('public.versions', 'chk_versions__years',
    'CHECK (year_to IS NULL OR year_from IS NULL OR year_to >= year_from)');
SELECT pg_temp.add_constraint('public.versions', 'chk_versions__displacement', 'CHECK (displacement_cc > 0)');
SELECT pg_temp.add_constraint('public.versions', 'chk_versions__fuel_type',
    $c$CHECK (fuel_type IN ('GASOLINE', 'DIESEL', 'HYBRID', 'PLUG_IN_HYBRID', 'ELECTRIC', 'CNG', 'FLEX'))$c$);
SELECT pg_temp.add_constraint('public.versions', 'chk_versions__transmission',
    $c$CHECK (transmission IN ('MANUAL', 'AUTOMATIC', 'CVT', 'DCT', 'OTHER'))$c$);

-- customer_vehicles --------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.customer_vehicles (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    person_id           UUID        NOT NULL,
    make_id             UUID        NOT NULL,
    model_id            UUID        NOT NULL,
    version_id          UUID,
    model_year          SMALLINT    NOT NULL,
    vin                 VARCHAR(17),
    nickname            VARCHAR(60),
    usage_type          VARCHAR(20),
    fuel_type           VARCHAR(20),
    declared_monthly_km INTEGER,
    acquired_on         DATE,
    is_primary          BOOLEAN     NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at          TIMESTAMPTZ,
    version             BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_vehicles PRIMARY KEY (id),
    CONSTRAINT fk_customer_vehicles__person_id FOREIGN KEY (person_id)
        REFERENCES public.persons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_customer_vehicles__make_id FOREIGN KEY (make_id)
        REFERENCES public.make (id) ON DELETE RESTRICT,
    CONSTRAINT fk_customer_vehicles__model_id_make_id FOREIGN KEY (model_id, make_id)
        REFERENCES public.model (id, make_id) ON DELETE RESTRICT,
    CONSTRAINT fk_customer_vehicles__version_id_model_id FOREIGN KEY (version_id, model_id)
        REFERENCES public.versions (id, model_id) ON DELETE RESTRICT,
    CONSTRAINT chk_customer_vehicles__model_year CHECK (model_year BETWEEN 1950 AND 2100),
    CONSTRAINT chk_customer_vehicles__vin CHECK (vin ~ '^[A-HJ-NPR-Z0-9]{17}$'),
    CONSTRAINT chk_customer_vehicles__usage_type CHECK (usage_type IN
        ('PERSONAL', 'RIDE_HAILING', 'TAXI', 'FLEET', 'COMMERCIAL', 'OTHER')),
    CONSTRAINT chk_customer_vehicles__fuel_type CHECK (fuel_type IN
        ('GASOLINE', 'DIESEL', 'HYBRID', 'PLUG_IN_HYBRID', 'ELECTRIC', 'CNG', 'FLEX')),
    CONSTRAINT chk_customer_vehicles__monthly_km CHECK (declared_monthly_km BETWEEN 0 AND 100000)
);
CREATE INDEX IF NOT EXISTS idx_customer_vehicles__person
    ON public.customer_vehicles (person_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_customer_vehicles__model_year
    ON public.customer_vehicles (model_id, model_year);
CREATE INDEX IF NOT EXISTS idx_customer_vehicles__make ON public.customer_vehicles (make_id);
CREATE INDEX IF NOT EXISTS idx_customer_vehicles__version
    ON public.customer_vehicles (version_id) WHERE version_id IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_vehicles__primary
    ON public.customer_vehicles (person_id) WHERE is_primary AND deleted_at IS NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_vehicles__person_vin
    ON public.customer_vehicles (person_id, vin) WHERE vin IS NOT NULL AND deleted_at IS NULL;

CREATE OR REPLACE TRIGGER trg_customer_vehicles_updated_at
    BEFORE UPDATE ON public.customer_vehicles FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- vehicle_odometer_readings ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.vehicle_odometer_readings (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    customer_vehicle_id UUID        NOT NULL,
    odometer_km         INTEGER     NOT NULL,
    read_at             TIMESTAMPTZ NOT NULL,
    source              VARCHAR(20) NOT NULL,
    is_suspect          BOOLEAN     NOT NULL DEFAULT false,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_vehicle_odometer_readings PRIMARY KEY (id),
    CONSTRAINT fk_vehicle_odometer_readings__customer_vehicle_id FOREIGN KEY (customer_vehicle_id)
        REFERENCES public.customer_vehicles (id) ON DELETE CASCADE,
    CONSTRAINT chk_vehicle_odometer_readings__km CHECK (odometer_km BETWEEN 0 AND 3000000),
    CONSTRAINT chk_vehicle_odometer_readings__source CHECK (source IN
        ('USER', 'CHECKOUT', 'SERVICE_RECORD', 'WARRANTY_CLAIM', 'OBD', 'IMPORT'))
);
CREATE INDEX IF NOT EXISTS idx_vehicle_odometer_readings__vehicle_read
    ON public.vehicle_odometer_readings (customer_vehicle_id, read_at DESC);

-- Solo inserción; is_suspect recalculable. DELETE permitido solo por purga
-- controlada. El ON DELETE CASCADE desde customer_vehicles (borrado físico por
-- supresión) requiere autopartco.allow_purge = on en esa transacción.
CREATE OR REPLACE TRIGGER trg_vehicle_odometer_readings_append_only
    BEFORE UPDATE OR DELETE ON public.vehicle_odometer_readings
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('is_suspect');

-- order_details.customer_vehicle_id (columna creada en V202610141020) ----------------------
SELECT pg_temp.add_constraint('public.order_details', 'fk_order_details__customer_vehicle_id',
    'FOREIGN KEY (customer_vehicle_id) REFERENCES public.customer_vehicles (id) ON DELETE RESTRICT NOT VALID');
-- El VALIDATE va en V202610141201: dentro de esta transacción mantendría el
-- bloqueo exclusivo de order_details durante todo el recorrido.
CREATE INDEX IF NOT EXISTS idx_order_details__customer_vehicle
    ON public.order_details (customer_vehicle_id) WHERE customer_vehicle_id IS NOT NULL;

DROP FUNCTION pg_temp.add_constraint(regclass, TEXT, TEXT);
