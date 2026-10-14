-- =============================================================================
-- V202610140950__inventory_price_history.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.5 (corrige H3)
--
-- Propósito: historial de precio de lista y costo por inventario (sucursal +
--   producto), con validez [valid_from, valid_to). Como mucho una fila abierta
--   por inventario (uq_inventory_price_history__open).
--   gross_margin_rate se calcula sobre el precio neto de IVA.
--   Quién escribe: el backend (ListingPrices.change, §12), en la misma
--   transacción que actualiza inventory.price/unit_cost. Mientras tanto, el
--   trigger puente de V202610141025 captura los cambios que hace hoy
--   StockService.updateListing.
--   El relleno INITIAL_LOAD está en V202610140951 (datos separados de la
--   estructura).
--   Coherencia de tenant (store/branch/product = los del inventario):
--   V202610141330.
--
-- Transacción: SÍ. Rollback: U202610140950__inventory_price_history.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.inventory_price_history (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY,
    inventory_id          UUID          NOT NULL,
    store_id              UUID          NOT NULL,
    branch_id             UUID          NOT NULL,
    product_id            UUID          NOT NULL,
    list_price            NUMERIC(12,2) NOT NULL,
    unit_cost             NUMERIC(14,4),
    currency              CHAR(3)       NOT NULL DEFAULT 'COP',
    vat_rate              NUMERIC(5,4),
    price_includes_vat    BOOLEAN       NOT NULL DEFAULT true,
    -- Acotado a [-9999.9999, 1]: con precio de lista ínfimo y costo alto, el
    -- margen negativo desbordaría NUMERIC(9,4). El valor exacto se recalcula
    -- desde list_price/unit_cost si hiciera falta.
    gross_margin_rate     NUMERIC(9,4)  GENERATED ALWAYS AS (
        CASE WHEN list_price > 0 AND unit_cost IS NOT NULL THEN greatest(-9999.9999, round(
            (list_price / (1 + CASE WHEN price_includes_vat THEN coalesce(vat_rate, 0) ELSE 0 END) - unit_cost)
            / (list_price / (1 + CASE WHEN price_includes_vat THEN coalesce(vat_rate, 0) ELSE 0 END)), 4))
        END) STORED,
    valid_from            TIMESTAMPTZ   NOT NULL,
    valid_to              TIMESTAMPTZ,
    change_source         VARCHAR(20)   NOT NULL,
    changed_by_account_id UUID,
    change_reason         VARCHAR(255),
    prediction_id         BIGINT,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_inventory_price_history PRIMARY KEY (id),
    CONSTRAINT fk_inventory_price_history__inventory_id FOREIGN KEY (inventory_id)
        REFERENCES public.inventory (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_price_history__store_id FOREIGN KEY (store_id)
        REFERENCES public.store (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_price_history__branch_id FOREIGN KEY (branch_id)
        REFERENCES public.branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_price_history__product_id FOREIGN KEY (product_id)
        REFERENCES public.products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_inventory_price_history__changed_by_account_id FOREIGN KEY (changed_by_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT chk_inventory_price_history__list_price CHECK (list_price >= 0),
    CONSTRAINT chk_inventory_price_history__unit_cost CHECK (unit_cost >= 0),
    CONSTRAINT chk_inventory_price_history__currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_inventory_price_history__vat_rate CHECK (vat_rate BETWEEN 0 AND 1),
    CONSTRAINT chk_inventory_price_history__validity CHECK (valid_to IS NULL OR valid_to > valid_from),
    CONSTRAINT chk_inventory_price_history__change_source CHECK (change_source IN
        ('INITIAL_LOAD', 'MANUAL', 'CSV_IMPORT', 'ERP_SYNC', 'PRICE_RULE', 'MODEL_SUGGESTION', 'PURCHASE_RECEIPT'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_inventory_price_history__open
    ON public.inventory_price_history (inventory_id) WHERE valid_to IS NULL;
CREATE INDEX IF NOT EXISTS idx_inventory_price_history__inventory_from
    ON public.inventory_price_history (inventory_id, valid_from DESC);
CREATE INDEX IF NOT EXISTS idx_inventory_price_history__product_from
    ON public.inventory_price_history (product_id, valid_from);
CREATE INDEX IF NOT EXISTS idx_inventory_price_history__store_from
    ON public.inventory_price_history (store_id, valid_from);
CREATE INDEX IF NOT EXISTS idx_inventory_price_history__branch
    ON public.inventory_price_history (branch_id);
CREATE INDEX IF NOT EXISTS idx_inventory_price_history__changed_by
    ON public.inventory_price_history (changed_by_account_id) WHERE changed_by_account_id IS NOT NULL;

-- Historial: solo se permite cerrar la fila abierta (valid_to) y la anulación
-- del actor por ON DELETE SET NULL.
CREATE OR REPLACE TRIGGER trg_inventory_price_history_append_only
    BEFORE UPDATE OR DELETE ON public.inventory_price_history
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('valid_to', 'changed_by_account_id:null');
CREATE OR REPLACE TRIGGER trg_inventory_price_history_append_only_truncate
    BEFORE TRUNCATE ON public.inventory_price_history
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

COMMENT ON TABLE public.inventory_price_history IS
    'Precio de lista y costo por inventario con validez temporal. Retención indefinida.';
