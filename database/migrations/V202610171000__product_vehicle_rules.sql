-- =============================================================================
-- V202610171000__product_vehicle_rules.sql
-- Autopartco | PostgreSQL 16+ | PLAN.md T1 (registro de productos v2)
--
-- Qué hace (fase EXPAND; product_compatibilities NO se elimina):
--   1. Crea public.product_vehicle_rules: regla de compatibilidad Marca ->
--      Modelo -> Versión con «todos» (model_id / version_id NULL = todos).
--   2. Invariantes en BD: version_id exige model_id; el modelo pertenece a la
--      marca y la versión al modelo (FK compuestas, igual que
--      fk_compatibility_model_make / fk_compatibility_version_model de init.sql).
--      Con MATCH SIMPLE, una columna NULL desactiva la FK compuesta, que es lo
--      que se quiere para «todos».
--   3. Único parcial por (producto, marca, modelo, versión) tratando NULL como
--      uuid cero, solo filas no borradas.
--   4. Índice de matching por vehículo (make_id, model_id, version_id) e índice
--      por product_id.
--   5. Migra product_compatibilities -> reglas exactas (marca/modelo/versión de
--      cada compatibilidad no borrada). Idempotente (ON CONFLICT DO NOTHING).
--
-- Nota: la tabla se llama product_compatibilities porque V202610151000
--   (rename_typos) ya la renombró desde products_compatibilies.
-- Nota: la normalización «regla contenida en otra» la hace el servicio; la BD
--   solo impide duplicados exactos.
-- Backend (T3): entidad ProductVehicleRule extends BaseEntity (incluye version).
-- Transacción: SÍ. Rollback: U202610171000__product_vehicle_rules.sql
-- =============================================================================

SET LOCAL lock_timeout = '30s';

CREATE TABLE IF NOT EXISTS public.product_vehicle_rules (
    id          UUID        NOT NULL DEFAULT gen_random_uuid(),
    product_id  UUID        NOT NULL,
    make_id     UUID        NOT NULL,
    model_id    UUID,
    version_id  UUID,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at  TIMESTAMPTZ,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_product_vehicle_rules PRIMARY KEY (id),
    CONSTRAINT fk_product_vehicle_rules__product_id
        FOREIGN KEY (product_id) REFERENCES public.products (id) ON DELETE CASCADE,
    CONSTRAINT fk_product_vehicle_rules__make_id
        FOREIGN KEY (make_id) REFERENCES public.make (id),
    -- El modelo debe pertenecer a la marca (se omite si model_id es NULL).
    CONSTRAINT fk_product_vehicle_rules__model_make
        FOREIGN KEY (model_id, make_id) REFERENCES public.model (id, make_id),
    -- La versión debe pertenecer al modelo (se omite si version_id es NULL).
    CONSTRAINT fk_product_vehicle_rules__version_model
        FOREIGN KEY (version_id, model_id) REFERENCES public.versions (id, model_id),
    CONSTRAINT chk_product_vehicle_rules__version_needs_model
        CHECK (version_id IS NULL OR model_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_vehicle_rules__rule
    ON public.product_vehicle_rules (
        product_id, make_id,
        coalesce(model_id,   '00000000-0000-0000-0000-000000000000'::uuid),
        coalesce(version_id, '00000000-0000-0000-0000-000000000000'::uuid))
    WHERE deleted_at IS NULL;

-- Match por vehículo: filtra por marca y, dentro, por modelo/versión.
CREATE INDEX IF NOT EXISTS idx_product_vehicle_rules__vehicle
    ON public.product_vehicle_rules (make_id, model_id, version_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_product_vehicle_rules__product_id
    ON public.product_vehicle_rules (product_id)
    WHERE deleted_at IS NULL;

CREATE OR REPLACE TRIGGER trg_product_vehicle_rules_updated_at
    BEFORE UPDATE ON public.product_vehicle_rules
    FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- Migración de datos: cada compatibilidad no borrada -> regla exacta.
INSERT INTO public.product_vehicle_rules (product_id, make_id, model_id, version_id)
SELECT pc.product_id, c.make_id, c.model_id, c.version_id
FROM public.product_compatibilities pc
JOIN public.compatibilities c ON c.id = pc.compatibility_id
WHERE c.deleted_at IS NULL
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- Verificación (la ejecuta el usuario). Antes de que el backend escriba reglas
-- nuevas, ambos conteos deben coincidir; la segunda consulta no debe dar filas:
--   SELECT
--     (SELECT count(*) FROM (SELECT DISTINCT pc.product_id, c.make_id, c.model_id, c.version_id
--        FROM product_compatibilities pc JOIN compatibilities c ON c.id = pc.compatibility_id
--        WHERE c.deleted_at IS NULL) x)                                    AS compat_origen,
--     (SELECT count(*) FROM product_vehicle_rules WHERE deleted_at IS NULL) AS reglas;
--
--   SELECT pc.product_id, c.make_id, c.model_id, c.version_id
--   FROM product_compatibilities pc JOIN compatibilities c ON c.id = pc.compatibility_id
--   WHERE c.deleted_at IS NULL
--     AND NOT EXISTS (SELECT 1 FROM product_vehicle_rules r
--       WHERE r.deleted_at IS NULL AND r.product_id = pc.product_id AND r.make_id = c.make_id
--         AND r.model_id IS NOT DISTINCT FROM c.model_id
--         AND r.version_id IS NOT DISTINCT FROM c.version_id);
--
-- EXPLAIN del match por vehículo (una regla cubre el vehículo si la marca coincide
-- y (model_id IS NULL OR modelo coincide) y (version_id IS NULL OR versión coincide)).
-- Tras ANALYZE y con ids reales; esperado: Bitmap/Index Scan sobre
-- idx_product_vehicle_rules__vehicle con condición por make_id:
--   EXPLAIN (ANALYZE, BUFFERS)
--   SELECT DISTINCT r.product_id
--   FROM public.product_vehicle_rules r
--   WHERE r.deleted_at IS NULL
--     AND r.make_id = '<make uuid>'
--     AND (r.model_id   IS NULL OR r.model_id   = '<model uuid>')
--     AND (r.version_id IS NULL OR r.version_id = '<version uuid>');
