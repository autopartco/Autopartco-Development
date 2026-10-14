-- =============================================================================
-- V202610141000__inventory_reorder_policies.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.4
--
-- Propósito: puntos de reorden con historial (valid_from/valid_to). Como mucho
--   una política vigente por inventario. Sirve para evaluar si una política
--   (manual o sugerida por un modelo) redujo los quiebres de stock.
--   prediction_id sin FK: ml.predictions está particionada; lo valida el
--   servicio. La FK de model_version_id se añade en V202610141300
--   (ml.model_versions).
--   El backend cierra la vigente e inserta la nueva (ReorderPolicies.set).
--
-- Transacción: SÍ. Rollback: U202610141000__inventory_reorder_policies.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.inventory_reorder_policies (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    inventory_id          UUID        NOT NULL,
    store_id              UUID        NOT NULL,
    reorder_point         INTEGER     NOT NULL,
    safety_stock          INTEGER     NOT NULL DEFAULT 0,
    reorder_quantity      INTEGER,
    max_stock             INTEGER,
    review_period_days    SMALLINT,
    source                VARCHAR(20) NOT NULL,
    prediction_id         BIGINT,
    model_version_id      UUID,
    valid_from            TIMESTAMPTZ NOT NULL DEFAULT now(),
    valid_to              TIMESTAMPTZ,
    created_by_account_id UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_inventory_reorder_policies PRIMARY KEY (id),
    CONSTRAINT fk_inventory_reorder_policies__inventory_id FOREIGN KEY (inventory_id)
        REFERENCES public.inventory (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reorder_policies__store_id FOREIGN KEY (store_id)
        REFERENCES public.store (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_reorder_policies__created_by_account_id FOREIGN KEY (created_by_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT chk_inventory_reorder_policies__reorder_point CHECK (reorder_point >= 0),
    CONSTRAINT chk_inventory_reorder_policies__safety_stock CHECK (safety_stock >= 0),
    CONSTRAINT chk_inventory_reorder_policies__reorder_quantity CHECK (reorder_quantity > 0),
    CONSTRAINT chk_inventory_reorder_policies__max_stock CHECK (max_stock IS NULL OR max_stock >= reorder_point),
    CONSTRAINT chk_inventory_reorder_policies__review_period CHECK (review_period_days > 0),
    CONSTRAINT chk_inventory_reorder_policies__source CHECK (source IN ('MANUAL', 'RULE', 'MODEL_SUGGESTION', 'ERP')),
    CONSTRAINT chk_inventory_reorder_policies__model_trace CHECK (
        source <> 'MODEL_SUGGESTION' OR model_version_id IS NOT NULL),
    CONSTRAINT chk_inventory_reorder_policies__validity CHECK (valid_to IS NULL OR valid_to > valid_from)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_reorder_policies__current
    ON public.inventory_reorder_policies (inventory_id) WHERE valid_to IS NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_reorder_policies__inventory_from
    ON public.inventory_reorder_policies (inventory_id, valid_from DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_reorder_policies__store
    ON public.inventory_reorder_policies (store_id);
CREATE INDEX IF NOT EXISTS idx_inventory_reorder_policies__model_version
    ON public.inventory_reorder_policies (model_version_id) WHERE model_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_reorder_policies__created_by
    ON public.inventory_reorder_policies (created_by_account_id) WHERE created_by_account_id IS NOT NULL;

CREATE OR REPLACE TRIGGER trg_inventory_reorder_policies_updated_at
    BEFORE UPDATE ON public.inventory_reorder_policies FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
