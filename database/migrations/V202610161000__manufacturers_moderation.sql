-- =============================================================================
-- V202610161000__manufacturers_moderation.sql
-- Autopartco | PostgreSQL 16+
--
-- Qué hace: moderación de fabricantes sugeridos por vendedores.
--   1. Alinea manufacturers con BaseEntity (init.sql solo trae id/name/logo_id):
--      created_at, updated_at, deleted_at, version (idempotente; Hibernate
--      `update` no puede añadir NOT NULL sobre filas existentes).
--   2. approved BOOLEAN NOT NULL DEFAULT true (los existentes quedan aprobados).
--   3. suggested_by_store_id UUID NULL -> store(id) ON DELETE SET NULL.
--   4. CHECK: approved = false exige suggested_by_store_id (NOT VALID + VALIDATE;
--      las filas existentes son approved = true, así que valida sin problema).
--   5. Índice único parcial sobre immutable_unaccent(lower(name)) (sin duplicados
--      que difieran solo en mayúsculas/acentos) y GIN trigram para sugerir
--      "¿quisiste decir...?". Ambos WHERE deleted_at IS NULL.
--
-- Nota: con SET NULL en la FK, borrar físicamente un almacén con sugerencias
--   pendientes violaría el CHECK; los almacenes se borran en lógico.
-- Datos: el seed R__seed_catalog_master_co.sql no tiene colisiones por
--   acento/mayúscula (Bosch, Brembo, Gates, Mahle, NGK, Denso, Valeo, SKF,
--   Monroe, Sachs, TRW, Mobil, Castrol, Genuine/OEM, Philips).
-- Backend: Manufacturer.java necesita approved y suggestedByStoreId.
-- Transacción: SÍ. Rollback: U202610161000__manufacturers_moderation.sql
-- =============================================================================

SET LOCAL lock_timeout = '30s';

ALTER TABLE public.manufacturers
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS approved BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS suggested_by_store_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'public.manufacturers'::regclass
                     AND conname = 'fk_manufacturers__suggested_by_store_id') THEN
        ALTER TABLE public.manufacturers
            ADD CONSTRAINT fk_manufacturers__suggested_by_store_id
            FOREIGN KEY (suggested_by_store_id) REFERENCES public.store (id)
            ON DELETE SET NULL;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_constraint
                   WHERE conrelid = 'public.manufacturers'::regclass
                     AND conname = 'chk_manufacturers__pending_has_store') THEN
        ALTER TABLE public.manufacturers
            ADD CONSTRAINT chk_manufacturers__pending_has_store
            CHECK (approved OR suggested_by_store_id IS NOT NULL) NOT VALID;
    END IF;
END $$;

ALTER TABLE public.manufacturers VALIDATE CONSTRAINT chk_manufacturers__pending_has_store;

-- Aborta con un mensaje claro si hay duplicados por acento/mayúscula.
DO $$
DECLARE
    dup TEXT;
BEGIN
    SELECT string_agg(k, ', ') INTO dup FROM (
        SELECT public.immutable_unaccent(lower(name)) AS k
        FROM public.manufacturers WHERE deleted_at IS NULL
        GROUP BY 1 HAVING count(*) > 1) d;
    IF dup IS NOT NULL THEN
        RAISE EXCEPTION 'manufacturers duplicados por acento/mayúscula: %. Resolver (fusionar o borrar en lógico) y reintentar.', dup;
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_manufacturers__name_normalized
    ON public.manufacturers (public.immutable_unaccent(lower(name)))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_manufacturers__name_trgm
    ON public.manufacturers USING gin (public.immutable_unaccent(lower(name)) gin_trgm_ops)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_manufacturers__suggested_by_store_id
    ON public.manufacturers (suggested_by_store_id)
    WHERE suggested_by_store_id IS NOT NULL;

-- Cola de moderación: pendientes por antigüedad.
CREATE INDEX IF NOT EXISTS idx_manufacturers__pending
    ON public.manufacturers (created_at)
    WHERE approved = false AND deleted_at IS NULL;

-- EXPLAIN que debe ejecutar el usuario:
--   EXPLAIN SELECT id, name FROM manufacturers
--   WHERE deleted_at IS NULL
--     AND public.immutable_unaccent(lower(name)) % public.immutable_unaccent(lower('bosh'));
