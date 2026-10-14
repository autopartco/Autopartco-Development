-- =============================================================================
-- V202610141240__purchasing.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.5
--
-- Propósito: proveedores, órdenes de compra, recepciones y lead time real.
--   - suppliers, supplier_products, purchase_orders, purchase_order_items,
--     purchase_receipts, purchase_receipt_items.
--   - Lead time real = purchase_receipts.received_at - purchase_orders.ordered_at
--     (vista analytics.v_supplier_lead_times en V202610141320).
--   - En tránsito = Σ (qty_ordered - qty_received) de las órdenes SENT,
--     CONFIRMED o PARTIALLY_RECEIVED. Con esto el snapshot rellena in_transit.
--   La recepción llama a StockLedger.record(PURCHASE_RECEIPT) y enlaza
--   inventory_movement_id.
--   Coherencia de tenant (proveedor, sucursal y productos del mismo
--   almacén): V202610141330.
--
-- Transacción: SÍ. Rollback: U202610141240__purchasing.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.suppliers (
    id                     UUID         NOT NULL DEFAULT gen_random_uuid(),
    store_id               UUID         NOT NULL,
    name                   VARCHAR(200) NOT NULL,
    nit                    VARCHAR(20),
    contact_email          VARCHAR(200),
    contact_phone          VARCHAR(20),
    municipality_code      CHAR(5),
    default_lead_time_days SMALLINT,
    payment_terms_days     SMALLINT,
    active                 BOOLEAN      NOT NULL DEFAULT true,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_suppliers PRIMARY KEY (id),
    CONSTRAINT fk_suppliers__store_id FOREIGN KEY (store_id) REFERENCES public.store (id) ON DELETE RESTRICT,
    CONSTRAINT fk_suppliers__municipality_code FOREIGN KEY (municipality_code)
        REFERENCES public.geo_municipalities (code) ON DELETE RESTRICT,
    CONSTRAINT chk_suppliers__name CHECK (btrim(name) <> ''),
    CONSTRAINT chk_suppliers__email CHECK (contact_email = lower(btrim(contact_email))),
    CONSTRAINT chk_suppliers__lead_time CHECK (default_lead_time_days >= 0),
    CONSTRAINT chk_suppliers__payment_terms CHECK (payment_terms_days >= 0)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_suppliers__store_nit
    ON public.suppliers (store_id, nit) WHERE nit IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_suppliers__store ON public.suppliers (store_id) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_suppliers__municipality
    ON public.suppliers (municipality_code) WHERE municipality_code IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_suppliers_updated_at
    BEFORE UPDATE ON public.suppliers FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.supplier_products (
    id                      UUID          NOT NULL DEFAULT gen_random_uuid(),
    supplier_id             UUID          NOT NULL,
    product_id              UUID          NOT NULL,
    supplier_sku            VARCHAR(100),
    last_unit_cost          NUMERIC(14,4),
    min_order_qty           INTEGER,
    pack_size               INTEGER,
    declared_lead_time_days SMALLINT,
    created_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_supplier_products PRIMARY KEY (id),
    CONSTRAINT fk_supplier_products__supplier_id FOREIGN KEY (supplier_id)
        REFERENCES public.suppliers (id) ON DELETE CASCADE,
    CONSTRAINT fk_supplier_products__product_id FOREIGN KEY (product_id)
        REFERENCES public.products (id) ON DELETE CASCADE,
    CONSTRAINT uq_supplier_products__supplier_product UNIQUE (supplier_id, product_id),
    CONSTRAINT chk_supplier_products__cost CHECK (last_unit_cost >= 0),
    CONSTRAINT chk_supplier_products__min_order CHECK (min_order_qty > 0),
    CONSTRAINT chk_supplier_products__pack CHECK (pack_size > 0),
    CONSTRAINT chk_supplier_products__lead_time CHECK (declared_lead_time_days >= 0)
);
CREATE INDEX IF NOT EXISTS idx_supplier_products__product ON public.supplier_products (product_id);
CREATE OR REPLACE TRIGGER trg_supplier_products_updated_at
    BEFORE UPDATE ON public.supplier_products FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.purchase_orders (
    id                    UUID          NOT NULL DEFAULT gen_random_uuid(),
    store_id              UUID          NOT NULL,
    branch_id             UUID          NOT NULL,
    supplier_id           UUID          NOT NULL,
    po_number             VARCHAR(40)   NOT NULL,
    status                VARCHAR(20)   NOT NULL,
    source                VARCHAR(20)   NOT NULL,
    prediction_id         BIGINT,
    currency              CHAR(3)       NOT NULL DEFAULT 'COP',
    subtotal              NUMERIC(14,2),
    ordered_at            TIMESTAMPTZ,
    expected_at           TIMESTAMPTZ,
    first_received_at     TIMESTAMPTZ,
    fully_received_at     TIMESTAMPTZ,
    cancelled_at          TIMESTAMPTZ,
    created_by_account_id UUID,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version               BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_purchase_orders PRIMARY KEY (id),
    CONSTRAINT fk_purchase_orders__store_id FOREIGN KEY (store_id) REFERENCES public.store (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders__branch_id FOREIGN KEY (branch_id) REFERENCES public.branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders__supplier_id FOREIGN KEY (supplier_id) REFERENCES public.suppliers (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_orders__created_by_account_id FOREIGN KEY (created_by_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT uq_purchase_orders__store_number UNIQUE (store_id, po_number),
    CONSTRAINT chk_purchase_orders__status CHECK (status IN
        ('DRAFT', 'SENT', 'CONFIRMED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED', 'CLOSED')),
    CONSTRAINT chk_purchase_orders__source CHECK (source IN ('MANUAL', 'REORDER_SUGGESTION', 'ERP_SYNC')),
    CONSTRAINT chk_purchase_orders__currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_purchase_orders__subtotal CHECK (subtotal >= 0),
    CONSTRAINT chk_purchase_orders__ordered CHECK (status IN ('DRAFT', 'CANCELLED') OR ordered_at IS NOT NULL),
    CONSTRAINT chk_purchase_orders__dates CHECK (
        (expected_at IS NULL OR ordered_at IS NULL OR expected_at >= ordered_at)
        AND (fully_received_at IS NULL OR first_received_at IS NULL OR fully_received_at >= first_received_at))
);
CREATE INDEX IF NOT EXISTS idx_purchase_orders__branch_status ON public.purchase_orders (branch_id, status);
CREATE INDEX IF NOT EXISTS idx_purchase_orders__supplier ON public.purchase_orders (supplier_id);
CREATE INDEX IF NOT EXISTS idx_purchase_orders__created_by
    ON public.purchase_orders (created_by_account_id) WHERE created_by_account_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_purchase_orders_updated_at
    BEFORE UPDATE ON public.purchase_orders FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.purchase_order_items (
    id                UUID          NOT NULL DEFAULT gen_random_uuid(),
    purchase_order_id UUID          NOT NULL,
    product_id        UUID          NOT NULL,
    inventory_id      UUID,
    qty_ordered       INTEGER       NOT NULL,
    qty_received      INTEGER       NOT NULL DEFAULT 0,
    unit_cost         NUMERIC(14,4) NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_purchase_order_items PRIMARY KEY (id),
    CONSTRAINT fk_purchase_order_items__purchase_order_id FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (id) ON DELETE CASCADE,
    CONSTRAINT fk_purchase_order_items__product_id FOREIGN KEY (product_id)
        REFERENCES public.products (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_order_items__inventory_id FOREIGN KEY (inventory_id)
        REFERENCES public.inventory (id) ON DELETE RESTRICT,
    CONSTRAINT uq_purchase_order_items__po_product UNIQUE (purchase_order_id, product_id),
    CONSTRAINT chk_purchase_order_items__qty_ordered CHECK (qty_ordered > 0),
    CONSTRAINT chk_purchase_order_items__qty_received CHECK (qty_received >= 0),
    CONSTRAINT chk_purchase_order_items__unit_cost CHECK (unit_cost >= 0)
);
CREATE INDEX IF NOT EXISTS idx_purchase_order_items__product ON public.purchase_order_items (product_id);
CREATE INDEX IF NOT EXISTS idx_purchase_order_items__inventory
    ON public.purchase_order_items (inventory_id) WHERE inventory_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_purchase_order_items_updated_at
    BEFORE UPDATE ON public.purchase_order_items FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.purchase_receipts (
    id                     UUID        NOT NULL DEFAULT gen_random_uuid(),
    purchase_order_id      UUID        NOT NULL,
    branch_id              UUID        NOT NULL,
    received_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    received_by_account_id UUID,
    supplier_document_ref  VARCHAR(60),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_purchase_receipts PRIMARY KEY (id),
    CONSTRAINT fk_purchase_receipts__purchase_order_id FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_receipts__branch_id FOREIGN KEY (branch_id)
        REFERENCES public.branches (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_receipts__received_by_account_id FOREIGN KEY (received_by_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL
);
CREATE INDEX IF NOT EXISTS idx_purchase_receipts__purchase_order ON public.purchase_receipts (purchase_order_id, received_at);
CREATE INDEX IF NOT EXISTS idx_purchase_receipts__branch ON public.purchase_receipts (branch_id);
CREATE INDEX IF NOT EXISTS idx_purchase_receipts__received_by
    ON public.purchase_receipts (received_by_account_id) WHERE received_by_account_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.purchase_receipt_items (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    purchase_receipt_id    UUID          NOT NULL,
    purchase_order_item_id UUID          NOT NULL,
    qty_received           INTEGER       NOT NULL,
    qty_rejected           INTEGER       NOT NULL DEFAULT 0,
    unit_cost              NUMERIC(14,4) NOT NULL,
    inventory_movement_id  UUID,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_purchase_receipt_items PRIMARY KEY (id),
    CONSTRAINT fk_purchase_receipt_items__purchase_receipt_id FOREIGN KEY (purchase_receipt_id)
        REFERENCES public.purchase_receipts (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_receipt_items__purchase_order_item_id FOREIGN KEY (purchase_order_item_id)
        REFERENCES public.purchase_order_items (id) ON DELETE RESTRICT,
    CONSTRAINT fk_purchase_receipt_items__inventory_movement_id FOREIGN KEY (inventory_movement_id)
        REFERENCES public.inventory_movements (id) ON DELETE RESTRICT,
    CONSTRAINT chk_purchase_receipt_items__qty CHECK (qty_received >= 0 AND qty_rejected >= 0
                                                      AND qty_received + qty_rejected > 0),
    CONSTRAINT chk_purchase_receipt_items__unit_cost CHECK (unit_cost >= 0)
);
CREATE INDEX IF NOT EXISTS idx_purchase_receipt_items__receipt ON public.purchase_receipt_items (purchase_receipt_id);
CREATE INDEX IF NOT EXISTS idx_purchase_receipt_items__po_item ON public.purchase_receipt_items (purchase_order_item_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_purchase_receipt_items__movement
    ON public.purchase_receipt_items (inventory_movement_id) WHERE inventory_movement_id IS NOT NULL;
