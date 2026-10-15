-- =============================================================================
-- V202610141220__carts.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.3
--
-- Propósito: carrito persistido y abandono.
--   - public.carts: dueño persona o anonymous_id; como mucho un carrito ACTIVE
--     por persona. CartAbandonmentJob (cada hora) marca ABANDONED los ACTIVE
--     con artículos y last_activity_at < now() - 24 h (idempotente).
--   - public.cart_items: removed_at conserva lo quitado (señal de intención).
--   Recordar un carrito abandonado requiere el consentimiento MARKETING_EMAIL.
--
-- Transacción: SÍ. Rollback: U202610141220__carts.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.carts (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid(),
    person_id          UUID,
    anonymous_id       UUID,
    session_id         UUID,
    status             VARCHAR(20) NOT NULL,
    currency           CHAR(3)     NOT NULL DEFAULT 'COP',
    converted_order_id UUID,
    last_activity_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    abandoned_at       TIMESTAMPTZ,
    converted_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    version            BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT pk_carts PRIMARY KEY (id),
    CONSTRAINT fk_carts__person_id FOREIGN KEY (person_id) REFERENCES public.persons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_carts__converted_order_id FOREIGN KEY (converted_order_id) REFERENCES public.orders (id) ON DELETE RESTRICT,
    CONSTRAINT chk_carts__owner CHECK (person_id IS NOT NULL OR anonymous_id IS NOT NULL),
    CONSTRAINT chk_carts__status CHECK (status IN ('ACTIVE', 'CONVERTED', 'ABANDONED', 'MERGED', 'EXPIRED')),
    CONSTRAINT chk_carts__currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_carts__converted CHECK ((status = 'CONVERTED') = (converted_order_id IS NOT NULL AND converted_at IS NOT NULL)),
    CONSTRAINT chk_carts__abandoned CHECK (status <> 'ABANDONED' OR abandoned_at IS NOT NULL)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_carts__active_person
    ON public.carts (person_id) WHERE status = 'ACTIVE' AND person_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_carts__status_activity ON public.carts (status, last_activity_at);
CREATE INDEX IF NOT EXISTS idx_carts__anonymous ON public.carts (anonymous_id) WHERE anonymous_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_carts__converted_order ON public.carts (converted_order_id) WHERE converted_order_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_carts_updated_at
    BEFORE UPDATE ON public.carts FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.cart_items (
    id                  UUID          NOT NULL DEFAULT gen_random_uuid(),
    cart_id             UUID          NOT NULL,
    inventory_id        UUID          NOT NULL,
    quantity            INTEGER       NOT NULL,
    captured_unit_price NUMERIC(12,2) NOT NULL,
    customer_vehicle_id UUID,
    added_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    removed_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_cart_items PRIMARY KEY (id),
    CONSTRAINT fk_cart_items__cart_id FOREIGN KEY (cart_id) REFERENCES public.carts (id) ON DELETE CASCADE,
    CONSTRAINT fk_cart_items__inventory_id FOREIGN KEY (inventory_id) REFERENCES public.inventory (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cart_items__customer_vehicle_id FOREIGN KEY (customer_vehicle_id)
        REFERENCES public.customer_vehicles (id) ON DELETE SET NULL,
    CONSTRAINT chk_cart_items__quantity CHECK (quantity > 0),
    CONSTRAINT chk_cart_items__price CHECK (captured_unit_price >= 0),
    CONSTRAINT chk_cart_items__removed CHECK (removed_at IS NULL OR removed_at >= added_at)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_items__cart_inventory_active
    ON public.cart_items (cart_id, inventory_id) WHERE removed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_cart_items__inventory ON public.cart_items (inventory_id);
CREATE INDEX IF NOT EXISTS idx_cart_items__customer_vehicle
    ON public.cart_items (customer_vehicle_id) WHERE customer_vehicle_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_cart_items_updated_at
    BEFORE UPDATE ON public.cart_items FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();
