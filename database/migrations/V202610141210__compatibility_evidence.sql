-- =============================================================================
-- V202610141210__compatibility_evidence.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.2
--
-- Propósito: nivel de evidencia de cada compatibilidad producto-vehículo.
--   Todas las columnas son nullables o tienen DEFAULT: el INSERT actual de
--   Hibernate (product_id, compatibility_id) sigue funcionando.
--
-- ADVERTENCIA (H5): Product.java mapea products_compatibilies con
--   @ElementCollection. Hibernate borra y reinserta las filas al editar la
--   colección, así que la evidencia se perdería (volvería a SELLER_DECLARED).
--   El backend debe mapearla como entidad (internal/ProductFitment) ANTES de
--   escribir evidencia distinta del valor por defecto. Esta migración es segura
--   de aplicar ya: hoy nadie escribe estas columnas.
--
-- Transacción: SÍ. Rollback: U202610141210__compatibility_evidence.sql
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

ALTER TABLE public.products_compatibilies
    ADD COLUMN IF NOT EXISTS evidence_status         VARCHAR(20) NOT NULL DEFAULT 'SELLER_DECLARED',
    ADD COLUMN IF NOT EXISTS source                  VARCHAR(30),
    ADD COLUMN IF NOT EXISTS year_from               SMALLINT,
    ADD COLUMN IF NOT EXISTS year_to                 SMALLINT,
    ADD COLUMN IF NOT EXISTS confirmed_by_account_id UUID,
    ADD COLUMN IF NOT EXISTS confirmed_at            TIMESTAMPTZ;

-- created_at/updated_at: la tabla puente no tenía marcas de tiempo. En las
-- filas existentes se usa la creación más reciente entre el producto y la
-- compatibilidad (el vínculo no puede ser anterior a ninguno de los dos), y no
-- la fecha de la migración. Solo cuando las columnas se crean aquí; el trigger
-- updated_at se crea después del relleno.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'public.products_compatibilies'::regclass
                   AND attname = 'created_at' AND NOT attisdropped) THEN
        ALTER TABLE public.products_compatibilies
            ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
            ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
        UPDATE public.products_compatibilies pc
        SET created_at = least(greatest(public.legacy_ts(p.created_at), public.legacy_ts(c.created_at)), now()),
            updated_at = least(greatest(public.legacy_ts(p.created_at), public.legacy_ts(c.created_at)), now())
        FROM public.products p, public.compatibilities c
        WHERE p.id = pc.product_id AND c.id = pc.compatibility_id;
    END IF;
END $$;

SELECT pg_temp.add_constraint('public.products_compatibilies', 'chk_products_compatibilies__evidence_status',
    $c$CHECK (evidence_status IN ('CONFIRMED', 'SELLER_DECLARED', 'SUGGESTED', 'REJECTED'))$c$);
SELECT pg_temp.add_constraint('public.products_compatibilies', 'chk_products_compatibilies__source',
    $c$CHECK (source IN ('SELLER', 'OEM_CATALOG', 'SUPPLIER', 'AI_SUGGESTION', 'ADMIN', 'ERP'))$c$);
SELECT pg_temp.add_constraint('public.products_compatibilies', 'chk_products_compatibilies__years',
    'CHECK (year_from BETWEEN 1950 AND 2100 AND year_to BETWEEN 1950 AND 2100 AND year_to >= year_from)');
SELECT pg_temp.add_constraint('public.products_compatibilies', 'chk_products_compatibilies__confirmation',
    $c$CHECK (evidence_status <> 'CONFIRMED' OR confirmed_at IS NOT NULL)$c$);
SELECT pg_temp.add_constraint('public.products_compatibilies', 'fk_products_compatibilies__confirmed_by_account_id',
    'FOREIGN KEY (confirmed_by_account_id) REFERENCES public.accounts (id) ON DELETE SET NULL');

CREATE INDEX IF NOT EXISTS idx_products_compatibilies__confirmed_by
    ON public.products_compatibilies (confirmed_by_account_id) WHERE confirmed_by_account_id IS NOT NULL;

CREATE OR REPLACE TRIGGER trg_products_compatibilies_updated_at
    BEFORE UPDATE ON public.products_compatibilies FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

DROP FUNCTION pg_temp.add_constraint(regclass, TEXT, TEXT);
