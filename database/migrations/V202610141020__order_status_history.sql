-- =============================================================================
-- V202610141020__order_status_history.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.7 (corrige H2) y §5.3 (H3)
--
-- Propósito
--   - public.cancellation_reasons (catálogo + semilla).
--   - public.order_status_history y public.sale_status_history (solo
--     inserción). Escribe el backend en la misma transacción que el cambio de
--     estado (§12). Hasta entonces, el trigger puente de V202610141025 captura
--     los cambios de SaleStatusService.
--   - Columnas aditivas (NULL o con DEFAULT; ningún INSERT actual se rompe):
--       orders:          channel, session_id, data_origin, currency, totales,
--                        placed_at, cancelled_at, cancellation_reason_code
--       store:           data_origin
--       sales:           fulfillment_type, confirmed_at, ready_at, cancelled_at,
--                        cancellation_reason_code, promised_*, discount_total
--       order_details:   instantánea de la línea (product_id, nombre, sku,
--                        categoría, precio de lista, descuento, promoción, costo,
--                        IVA, compatibilidad mostrada, customer_vehicle_id)
--       shipments:       promesa, municipios, distancia, intentos, peso
--       shipment_events: from_status_id, source, actor_account_id, municipality_code
--       payment_events:  from_status_id, source, provider_event_id,
--                        provider_status, occurred_at
--   - Semilla aditiva de shipment_statuses: READY_FOR_PICKUP, FAILED_ATTEMPT,
--     RETURNED_TO_SENDER.
--   Store.java no mapea data_origin: el INSERT de Hibernate usa el DEFAULT 'REAL'.
--   El relleno inicial del historial y de order_details.product_id está en
--   V202610141021.
--
-- Transacción: SÍ. Bloqueos: ACCESS EXCLUSIVE breve por tabla. Los ADD COLUMN
--   con DEFAULT constante no reescriben. Los CHECK sobre columnas nuevas se
--   validan al crearse (recorren tablas hoy vacías o pequeñas: orders, sales,
--   order_details, shipments; store es pequeña).
-- Rollback: U202610141020__order_status_history.sql
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

-- -----------------------------------------------------------------------------
-- Catálogo de motivos de cancelación
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.cancellation_reasons (
    code        VARCHAR(40)  NOT NULL,
    actor_scope VARCHAR(10)  NOT NULL,
    description VARCHAR(255) NOT NULL,
    requires_notes BOOLEAN   NOT NULL DEFAULT false,
    active      BOOLEAN      NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_cancellation_reasons PRIMARY KEY (code),
    CONSTRAINT chk_cancellation_reasons__actor_scope CHECK (actor_scope IN ('CUSTOMER', 'STORE', 'PLATFORM', 'SYSTEM'))
);
CREATE OR REPLACE TRIGGER trg_cancellation_reasons_updated_at
    BEFORE UPDATE ON public.cancellation_reasons FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

INSERT INTO public.cancellation_reasons (code, actor_scope, description, requires_notes) VALUES
    ('CUSTOMER_CHANGED_MIND',      'CUSTOMER', 'El cliente cambió de opinión', false),
    ('CUSTOMER_FOUND_CHEAPER',     'CUSTOMER', 'El cliente encontró un precio menor', false),
    ('CUSTOMER_WRONG_PART',        'CUSTOMER', 'El cliente pidió el repuesto equivocado', false),
    ('CUSTOMER_DELIVERY_TOO_SLOW', 'CUSTOMER', 'La entrega tarda demasiado para el cliente', false),
    ('STORE_OUT_OF_STOCK',         'STORE',    'El almacén no tiene existencias', false),
    ('STORE_PRICE_ERROR',          'STORE',    'Error de precio del almacén', false),
    ('STORE_CANNOT_SHIP_TO_ZONE',  'STORE',    'El almacén no despacha a la zona', false),
    ('STORE_NO_RESPONSE_TIMEOUT',  'SYSTEM',   'El almacén no respondió a tiempo', false),
    ('PAYMENT_FAILED',             'SYSTEM',   'El pago falló', false),
    ('PAYMENT_TIMEOUT',            'SYSTEM',   'El pago no se completó a tiempo', false),
    ('FRAUD_SUSPECTED',            'PLATFORM', 'Sospecha de fraude', false),
    ('PLATFORM_POLICY',            'PLATFORM', 'Incumple la política de la plataforma', false),
    ('DUPLICATE_ORDER',            'PLATFORM', 'Pedido duplicado', false),
    ('OTHER',                      'PLATFORM', 'Otro motivo (requiere notas)', true)
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Historiales de estado (solo inserción)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.order_status_history (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    order_id         UUID        NOT NULL,
    from_status      VARCHAR(50),
    to_status        VARCHAR(50) NOT NULL,
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type       VARCHAR(10) NOT NULL,
    actor_account_id UUID,
    reason_code      VARCHAR(40),
    notes            VARCHAR(255),
    CONSTRAINT pk_order_status_history PRIMARY KEY (id),
    CONSTRAINT fk_order_status_history__order_id FOREIGN KEY (order_id)
        REFERENCES public.orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_status_history__actor_account_id FOREIGN KEY (actor_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT fk_order_status_history__reason_code FOREIGN KEY (reason_code)
        REFERENCES public.cancellation_reasons (code) ON DELETE RESTRICT,
    CONSTRAINT chk_order_status_history__actor_type CHECK (actor_type IN ('CUSTOMER', 'STORE', 'PLATFORM', 'SYSTEM', 'PROVIDER')),
    CONSTRAINT chk_order_status_history__transition CHECK (from_status IS DISTINCT FROM to_status),
    CONSTRAINT chk_order_status_history__other_notes CHECK (reason_code IS DISTINCT FROM 'OTHER' OR notes IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_order_status_history__order_changed
    ON public.order_status_history (order_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_order_status_history__actor
    ON public.order_status_history (actor_account_id) WHERE actor_account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_order_status_history__reason
    ON public.order_status_history (reason_code) WHERE reason_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS brin_order_status_history__changed_at
    ON public.order_status_history USING brin (changed_at) WITH (pages_per_range = 32);

CREATE TABLE IF NOT EXISTS public.sale_status_history (
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    sale_id          UUID        NOT NULL,
    store_id         UUID        NOT NULL,
    from_status      VARCHAR(50),
    to_status        VARCHAR(50) NOT NULL,
    changed_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type       VARCHAR(10) NOT NULL,
    actor_account_id UUID,
    reason_code      VARCHAR(40),
    notes            VARCHAR(255),
    CONSTRAINT pk_sale_status_history PRIMARY KEY (id),
    CONSTRAINT fk_sale_status_history__sale_id FOREIGN KEY (sale_id)
        REFERENCES public.sales (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_status_history__store_id FOREIGN KEY (store_id)
        REFERENCES public.store (id) ON DELETE RESTRICT,
    CONSTRAINT fk_sale_status_history__actor_account_id FOREIGN KEY (actor_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT fk_sale_status_history__reason_code FOREIGN KEY (reason_code)
        REFERENCES public.cancellation_reasons (code) ON DELETE RESTRICT,
    CONSTRAINT chk_sale_status_history__actor_type CHECK (actor_type IN ('CUSTOMER', 'STORE', 'PLATFORM', 'SYSTEM', 'PROVIDER')),
    CONSTRAINT chk_sale_status_history__transition CHECK (from_status IS DISTINCT FROM to_status),
    CONSTRAINT chk_sale_status_history__other_notes CHECK (reason_code IS DISTINCT FROM 'OTHER' OR notes IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_sale_status_history__sale_changed
    ON public.sale_status_history (sale_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_sale_status_history__store_changed
    ON public.sale_status_history (store_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_sale_status_history__actor
    ON public.sale_status_history (actor_account_id) WHERE actor_account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_sale_status_history__reason
    ON public.sale_status_history (reason_code) WHERE reason_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS brin_sale_status_history__changed_at
    ON public.sale_status_history USING brin (changed_at) WITH (pages_per_range = 32);

CREATE OR REPLACE TRIGGER trg_order_status_history_append_only
    BEFORE UPDATE OR DELETE ON public.order_status_history
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('actor_account_id:null');
CREATE OR REPLACE TRIGGER trg_order_status_history_append_only_truncate
    BEFORE TRUNCATE ON public.order_status_history
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_sale_status_history_append_only
    BEFORE UPDATE OR DELETE ON public.sale_status_history
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('actor_account_id:null');
CREATE OR REPLACE TRIGGER trg_sale_status_history_append_only_truncate
    BEFORE TRUNCATE ON public.sale_status_history
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

-- -----------------------------------------------------------------------------
-- orders
-- -----------------------------------------------------------------------------
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS channel                  VARCHAR(20)   NOT NULL DEFAULT 'WEB',
    ADD COLUMN IF NOT EXISTS session_id               UUID,
    ADD COLUMN IF NOT EXISTS data_origin              VARCHAR(10)   NOT NULL DEFAULT 'REAL',
    ADD COLUMN IF NOT EXISTS currency                 CHAR(3)       NOT NULL DEFAULT 'COP',
    ADD COLUMN IF NOT EXISTS subtotal                 NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS discount_total           NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS shipping_total           NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS total                    NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS placed_at                TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at             TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancellation_reason_code VARCHAR(40);

SELECT pg_temp.add_constraint('public.orders', 'chk_orders__channel',
    $c$CHECK (channel IN ('WEB', 'MOBILE_WEB', 'APP', 'STORE_ASSISTED', 'API'))$c$);
SELECT pg_temp.add_constraint('public.orders', 'chk_orders__data_origin',
    $c$CHECK (data_origin IN ('REAL', 'DEMO', 'SYNTHETIC'))$c$);
SELECT pg_temp.add_constraint('public.orders', 'chk_orders__currency', $c$CHECK (currency ~ '^[A-Z]{3}$')$c$);
SELECT pg_temp.add_constraint('public.orders', 'chk_orders__amounts',
    'CHECK (subtotal >= 0 AND discount_total >= 0 AND shipping_total >= 0 AND total >= 0)');
SELECT pg_temp.add_constraint('public.orders', 'fk_orders__cancellation_reason_code',
    'FOREIGN KEY (cancellation_reason_code) REFERENCES public.cancellation_reasons (code) ON DELETE RESTRICT');
CREATE INDEX IF NOT EXISTS idx_orders__placed_at ON public.orders (placed_at);
CREATE INDEX IF NOT EXISTS idx_orders__cancellation_reason
    ON public.orders (cancellation_reason_code) WHERE cancellation_reason_code IS NOT NULL;

-- -----------------------------------------------------------------------------
-- store
-- -----------------------------------------------------------------------------
ALTER TABLE public.store
    ADD COLUMN IF NOT EXISTS data_origin VARCHAR(10) NOT NULL DEFAULT 'REAL';
SELECT pg_temp.add_constraint('public.store', 'chk_store__data_origin',
    $c$CHECK (data_origin IN ('REAL', 'DEMO', 'SYNTHETIC'))$c$);

-- -----------------------------------------------------------------------------
-- sales
-- -----------------------------------------------------------------------------
ALTER TABLE public.sales
    ADD COLUMN IF NOT EXISTS fulfillment_type         VARCHAR(20),
    ADD COLUMN IF NOT EXISTS confirmed_at             TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS ready_at                 TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_at             TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancellation_reason_code VARCHAR(40),
    ADD COLUMN IF NOT EXISTS promised_ship_by         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS promised_delivery_by     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS discount_total           NUMERIC(12,2) NOT NULL DEFAULT 0;

SELECT pg_temp.add_constraint('public.sales', 'chk_sales__fulfillment_type',
    $c$CHECK (fulfillment_type IN ('SHIPPING', 'STORE_PICKUP'))$c$);
SELECT pg_temp.add_constraint('public.sales', 'chk_sales__discount_total', 'CHECK (discount_total >= 0)');
SELECT pg_temp.add_constraint('public.sales', 'fk_sales__cancellation_reason_code',
    'FOREIGN KEY (cancellation_reason_code) REFERENCES public.cancellation_reasons (code) ON DELETE RESTRICT');
CREATE INDEX IF NOT EXISTS idx_sales__updated_at ON public.sales (updated_at);
CREATE INDEX IF NOT EXISTS idx_sales__cancellation_reason
    ON public.sales (cancellation_reason_code) WHERE cancellation_reason_code IS NOT NULL;

-- -----------------------------------------------------------------------------
-- order_details (instantánea de la línea)
-- -----------------------------------------------------------------------------
ALTER TABLE public.order_details
    ADD COLUMN IF NOT EXISTS product_id                 UUID,
    ADD COLUMN IF NOT EXISTS product_name_snapshot      VARCHAR(200),
    ADD COLUMN IF NOT EXISTS sku_snapshot               VARCHAR(100),
    ADD COLUMN IF NOT EXISTS category_id_snapshot       UUID,
    ADD COLUMN IF NOT EXISTS list_unit_price            NUMERIC(12,2),
    ADD COLUMN IF NOT EXISTS discount_amount            NUMERIC(12,2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS promotion_id               UUID,
    ADD COLUMN IF NOT EXISTS unit_cost_snapshot         NUMERIC(14,4),
    ADD COLUMN IF NOT EXISTS vat_rate                   NUMERIC(5,4),
    ADD COLUMN IF NOT EXISTS compatibility_status_shown VARCHAR(20),
    ADD COLUMN IF NOT EXISTS customer_vehicle_id        UUID;

SELECT pg_temp.add_constraint('public.order_details', 'fk_order_details__product_id',
    'FOREIGN KEY (product_id) REFERENCES public.products (id) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.order_details', 'fk_order_details__category_id_snapshot',
    'FOREIGN KEY (category_id_snapshot) REFERENCES public.categories (id) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.order_details', 'fk_order_details__promotion_id',
    'FOREIGN KEY (promotion_id) REFERENCES public.promotions (id) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.order_details', 'chk_order_details__list_unit_price', 'CHECK (list_unit_price >= 0)');
SELECT pg_temp.add_constraint('public.order_details', 'chk_order_details__discount_amount', 'CHECK (discount_amount >= 0)');
SELECT pg_temp.add_constraint('public.order_details', 'chk_order_details__unit_cost_snapshot', 'CHECK (unit_cost_snapshot >= 0)');
SELECT pg_temp.add_constraint('public.order_details', 'chk_order_details__vat_rate', 'CHECK (vat_rate BETWEEN 0 AND 1)');
SELECT pg_temp.add_constraint('public.order_details', 'chk_order_details__compatibility_status_shown',
    $c$CHECK (compatibility_status_shown IN ('CONFIRMED', 'SELLER_DECLARED', 'SUGGESTED', 'UNKNOWN'))$c$);
CREATE INDEX IF NOT EXISTS idx_order_details__product ON public.order_details (product_id);
CREATE INDEX IF NOT EXISTS idx_order_details__category_snapshot
    ON public.order_details (category_id_snapshot) WHERE category_id_snapshot IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_order_details__promotion
    ON public.order_details (promotion_id) WHERE promotion_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- shipments y shipment_events
-- -----------------------------------------------------------------------------
ALTER TABLE public.shipments
    ADD COLUMN IF NOT EXISTS estimated_delivery_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS origin_municipality_code      CHAR(5),
    ADD COLUMN IF NOT EXISTS destination_municipality_code CHAR(5),
    ADD COLUMN IF NOT EXISTS distance_km                   NUMERIC(8,2),
    ADD COLUMN IF NOT EXISTS delivery_attempts             SMALLINT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS package_weight_grams          INTEGER;

SELECT pg_temp.add_constraint('public.shipments', 'fk_shipments__origin_municipality_code',
    'FOREIGN KEY (origin_municipality_code) REFERENCES public.geo_municipalities (code) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.shipments', 'fk_shipments__destination_municipality_code',
    'FOREIGN KEY (destination_municipality_code) REFERENCES public.geo_municipalities (code) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.shipments', 'chk_shipments__distance_km', 'CHECK (distance_km >= 0)');
SELECT pg_temp.add_constraint('public.shipments', 'chk_shipments__delivery_attempts', 'CHECK (delivery_attempts >= 0)');
SELECT pg_temp.add_constraint('public.shipments', 'chk_shipments__package_weight', 'CHECK (package_weight_grams > 0)');
CREATE INDEX IF NOT EXISTS idx_shipments__origin_municipality
    ON public.shipments (origin_municipality_code) WHERE origin_municipality_code IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_shipments__destination_municipality
    ON public.shipments (destination_municipality_code) WHERE destination_municipality_code IS NOT NULL;

ALTER TABLE public.shipment_events
    ADD COLUMN IF NOT EXISTS from_status_id    UUID,
    ADD COLUMN IF NOT EXISTS source            VARCHAR(20),
    ADD COLUMN IF NOT EXISTS actor_account_id  UUID,
    ADD COLUMN IF NOT EXISTS municipality_code CHAR(5);

SELECT pg_temp.add_constraint('public.shipment_events', 'fk_shipment_events__from_status_id',
    'FOREIGN KEY (from_status_id) REFERENCES public.shipment_statuses (id) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.shipment_events', 'fk_shipment_events__actor_account_id',
    'FOREIGN KEY (actor_account_id) REFERENCES public.accounts (id) ON DELETE SET NULL');
SELECT pg_temp.add_constraint('public.shipment_events', 'fk_shipment_events__municipality_code',
    'FOREIGN KEY (municipality_code) REFERENCES public.geo_municipalities (code) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.shipment_events', 'chk_shipment_events__source',
    $c$CHECK (source IN ('STORE', 'CARRIER_WEBHOOK', 'SYSTEM'))$c$);
CREATE INDEX IF NOT EXISTS idx_shipment_events__from_status
    ON public.shipment_events (from_status_id) WHERE from_status_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_shipment_events__status
    ON public.shipment_events (shipment_status_id);
CREATE INDEX IF NOT EXISTS idx_shipment_events__actor
    ON public.shipment_events (actor_account_id) WHERE actor_account_id IS NOT NULL;

CREATE OR REPLACE TRIGGER trg_shipment_events_append_only
    BEFORE UPDATE OR DELETE ON public.shipment_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('actor_account_id:null');

INSERT INTO public.shipment_statuses (code, description) VALUES
    ('READY_FOR_PICKUP',   'Listo para recoger en tienda'),
    ('FAILED_ATTEMPT',     'Intento de entrega fallido'),
    ('RETURNED_TO_SENDER', 'Devuelto al remitente')
ON CONFLICT (code) DO NOTHING;

-- -----------------------------------------------------------------------------
-- payment_events
-- -----------------------------------------------------------------------------
ALTER TABLE public.payment_events
    ADD COLUMN IF NOT EXISTS from_status_id    UUID,
    ADD COLUMN IF NOT EXISTS source            VARCHAR(20),
    ADD COLUMN IF NOT EXISTS provider_event_id VARCHAR(150),
    ADD COLUMN IF NOT EXISTS provider_status   VARCHAR(50);

-- occurred_at: en las filas existentes se rellena desde created_at (no con la
-- fecha de la migración). Solo cuando la columna se crea aquí. payment_events
-- es de solo inserción: se suspenden sus triggers de usuario mientras dura el
-- relleno, dentro de esta misma transacción.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_attribute WHERE attrelid = 'public.payment_events'::regclass
                   AND attname = 'occurred_at' AND NOT attisdropped) THEN
        ALTER TABLE public.payment_events ADD COLUMN occurred_at TIMESTAMPTZ NOT NULL DEFAULT now();
        ALTER TABLE public.payment_events DISABLE TRIGGER USER;
        UPDATE public.payment_events SET occurred_at = least(public.legacy_ts(created_at), now());
        ALTER TABLE public.payment_events ENABLE TRIGGER USER;
    END IF;
END $$;

SELECT pg_temp.add_constraint('public.payment_events', 'fk_payment_events__from_status_id',
    'FOREIGN KEY (from_status_id) REFERENCES public.payment_statuses (id) ON DELETE RESTRICT');
SELECT pg_temp.add_constraint('public.payment_events', 'chk_payment_events__source',
    $c$CHECK (source IN ('CHECKOUT', 'WEBHOOK', 'RECONCILIATION', 'ADMIN'))$c$);
CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_events__payment_provider_event
    ON public.payment_events (payment_id, provider_event_id) WHERE provider_event_id IS NOT NULL;
-- (payment_id, created_at) ya existe: idx_payment_events__payment_created (0910).
-- No se añade (payment_id, occurred_at): occurred_at se rellena igual a created_at.
CREATE INDEX IF NOT EXISTS idx_payment_events__status
    ON public.payment_events (payment_status_id);
CREATE INDEX IF NOT EXISTS idx_payment_events__from_status
    ON public.payment_events (from_status_id) WHERE from_status_id IS NOT NULL;

DROP FUNCTION pg_temp.add_constraint(regclass, TEXT, TEXT);
