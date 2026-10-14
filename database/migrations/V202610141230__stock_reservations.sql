-- =============================================================================
-- V202610141230__stock_reservations.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.4
--
-- Propósito: reservas de stock (carrito o línea de pedido). Invariante que
--   reconcilia InventorySnapshotJob:
--     inventory.reserved_stock = Σ quantity de las reservas ACTIVE.
--   (Se reconcilia en lote y no con un trigger, para no serializar cada
--   reserva sobre la fila de inventario.)
--
-- Transacción: SÍ. Rollback: U202610141230__stock_reservations.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.stock_reservations (
    id              UUID        NOT NULL DEFAULT gen_random_uuid(),
    inventory_id    UUID        NOT NULL,
    order_detail_id UUID,
    cart_id         UUID,
    quantity        INTEGER     NOT NULL,
    status          VARCHAR(15) NOT NULL,
    expires_at      TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_stock_reservations PRIMARY KEY (id),
    CONSTRAINT fk_stock_reservations__inventory_id FOREIGN KEY (inventory_id)
        REFERENCES public.inventory (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_reservations__order_detail_id FOREIGN KEY (order_detail_id)
        REFERENCES public.order_details (id) ON DELETE RESTRICT,
    CONSTRAINT fk_stock_reservations__cart_id FOREIGN KEY (cart_id)
        REFERENCES public.carts (id) ON DELETE SET NULL,
    CONSTRAINT chk_stock_reservations__quantity CHECK (quantity > 0),
    CONSTRAINT chk_stock_reservations__status CHECK (status IN ('ACTIVE', 'CONSUMED', 'RELEASED', 'EXPIRED')),
    CONSTRAINT chk_stock_reservations__origin CHECK (order_detail_id IS NOT NULL OR cart_id IS NOT NULL OR status <> 'ACTIVE'),
    CONSTRAINT chk_stock_reservations__expiry CHECK (expires_at > created_at)
);
CREATE INDEX IF NOT EXISTS idx_stock_reservations__inventory_active
    ON public.stock_reservations (inventory_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_stock_reservations__expires
    ON public.stock_reservations (expires_at) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_stock_reservations__order_detail
    ON public.stock_reservations (order_detail_id) WHERE order_detail_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_stock_reservations__cart
    ON public.stock_reservations (cart_id) WHERE cart_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_stock_reservations_updated_at
    BEFORE UPDATE ON public.stock_reservations FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
