-- =============================================================================
-- V202610141250__returns.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.6
--
-- Propósito: devoluciones y garantías con motivo tipificado.
--   - public.return_reasons (catálogo + semilla).
--   - public.return_requests, public.return_request_items,
--     public.return_status_history (solo inserción).
--   WRONG_FITMENT cruzado con order_details.compatibility_status_shown da la
--   tasa real de error de cada nivel de evidencia de compatibilidad.
--   customer_comment puede contener PII: nunca se copia a analytics.
--   Coherencia pedido/venta/almacén/persona: V202610141330.
--
-- Transacción: SÍ. Rollback: U202610141250__returns.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.return_reasons (
    code               VARCHAR(40)  NOT NULL,
    fault_party        VARCHAR(15)  NOT NULL,
    applies_to         VARCHAR(10)  NOT NULL,
    is_fitment_related BOOLEAN      NOT NULL DEFAULT false,
    description        VARCHAR(255) NOT NULL,
    active             BOOLEAN      NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_return_reasons PRIMARY KEY (code),
    CONSTRAINT chk_return_reasons__fault_party CHECK (fault_party IN
        ('CUSTOMER', 'STORE', 'CARRIER', 'MANUFACTURER', 'PLATFORM', 'UNKNOWN')),
    CONSTRAINT chk_return_reasons__applies_to CHECK (applies_to IN ('RETURN', 'WARRANTY', 'BOTH'))
);
CREATE OR REPLACE TRIGGER trg_return_reasons_updated_at
    BEFORE UPDATE ON public.return_reasons FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

INSERT INTO public.return_reasons (code, fault_party, applies_to, is_fitment_related, description) VALUES
    ('WRONG_FITMENT',        'UNKNOWN',      'BOTH',     true,  'El repuesto no corresponde al vehículo'),
    ('DEFECTIVE_ON_ARRIVAL', 'MANUFACTURER', 'BOTH',     false, 'Defectuoso al recibirlo'),
    ('FAILED_IN_USE',        'MANUFACTURER', 'WARRANTY', false, 'Falló durante el uso'),
    ('DAMAGED_IN_TRANSIT',   'CARRIER',      'RETURN',   false, 'Dañado en el transporte'),
    ('NOT_AS_DESCRIBED',     'STORE',        'RETURN',   false, 'No coincide con la descripción'),
    ('WRONG_ITEM_SENT',      'STORE',        'RETURN',   false, 'Se envió otro artículo'),
    ('MISSING_PARTS',        'STORE',        'RETURN',   false, 'Faltan piezas'),
    ('LATE_DELIVERY',        'CARRIER',      'RETURN',   false, 'Entrega tardía'),
    ('NO_LONGER_NEEDED',     'CUSTOMER',     'RETURN',   false, 'El cliente ya no lo necesita'),
    ('ORDERED_BY_MISTAKE',   'CUSTOMER',     'RETURN',   false, 'Pedido por error'),
    ('FOUND_CHEAPER',        'CUSTOMER',     'RETURN',   false, 'Encontró un precio menor'),
    ('INSTALLATION_ISSUE',   'UNKNOWN',      'BOTH',     false, 'Problema de instalación'),
    ('OTHER',                'UNKNOWN',      'BOTH',     false, 'Otro motivo')
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS public.return_requests (
    id                     UUID          NOT NULL DEFAULT gen_random_uuid(),
    request_number         BIGINT        GENERATED ALWAYS AS IDENTITY,
    order_id               UUID          NOT NULL,
    sale_id                UUID          NOT NULL,
    store_id               UUID          NOT NULL,
    person_id              UUID          NOT NULL,
    request_type           VARCHAR(10)   NOT NULL,
    reason_code            VARCHAR(40)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    resolution             VARCHAR(20),
    refund_amount          NUMERIC(12,2),
    customer_vehicle_id    UUID,
    odometer_km_at_failure INTEGER,
    installed_on           DATE,
    failed_on              DATE,
    customer_comment       TEXT,
    claim_id               UUID,
    requested_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    approved_at            TIMESTAMPTZ,
    received_at            TIMESTAMPTZ,
    resolved_at            TIMESTAMPTZ,
    resolved_by_account_id UUID,
    created_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    deleted_at             TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_return_requests PRIMARY KEY (id),
    CONSTRAINT uq_return_requests__request_number UNIQUE (request_number),
    CONSTRAINT fk_return_requests__order_id FOREIGN KEY (order_id) REFERENCES public.orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_requests__sale_id FOREIGN KEY (sale_id) REFERENCES public.sales (id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_requests__store_id FOREIGN KEY (store_id) REFERENCES public.store (id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_requests__person_id FOREIGN KEY (person_id) REFERENCES public.persons (id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_requests__reason_code FOREIGN KEY (reason_code)
        REFERENCES public.return_reasons (code) ON DELETE RESTRICT,
    CONSTRAINT fk_return_requests__customer_vehicle_id FOREIGN KEY (customer_vehicle_id)
        REFERENCES public.customer_vehicles (id) ON DELETE SET NULL,
    CONSTRAINT fk_return_requests__claim_id FOREIGN KEY (claim_id) REFERENCES public.claims (id) ON DELETE SET NULL,
    CONSTRAINT fk_return_requests__resolved_by_account_id FOREIGN KEY (resolved_by_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT chk_return_requests__request_type CHECK (request_type IN ('RETURN', 'WARRANTY', 'EXCHANGE')),
    CONSTRAINT chk_return_requests__status CHECK (status IN
        ('REQUESTED', 'APPROVED', 'REJECTED', 'IN_TRANSIT', 'RECEIVED', 'INSPECTED', 'RESOLVED', 'CANCELLED')),
    CONSTRAINT chk_return_requests__resolution CHECK (resolution IN
        ('REFUND', 'PARTIAL_REFUND', 'REPLACEMENT', 'REPAIR', 'STORE_CREDIT', 'REJECTED')),
    CONSTRAINT chk_return_requests__resolved CHECK (status <> 'RESOLVED' OR (resolution IS NOT NULL AND resolved_at IS NOT NULL)),
    -- Un importe de reembolso exige resolución (no se acepta con resolution NULL).
    CONSTRAINT chk_return_requests__refund CHECK (refund_amount IS NULL OR (
        refund_amount >= 0 AND resolution IS NOT NULL
        AND (resolution IN ('REFUND', 'PARTIAL_REFUND') OR refund_amount = 0))),
    CONSTRAINT chk_return_requests__odometer CHECK (odometer_km_at_failure >= 0),
    CONSTRAINT chk_return_requests__dates CHECK (failed_on IS NULL OR installed_on IS NULL OR failed_on >= installed_on)
);
CREATE INDEX IF NOT EXISTS idx_return_requests__sale ON public.return_requests (sale_id);
CREATE INDEX IF NOT EXISTS idx_return_requests__order ON public.return_requests (order_id);
CREATE INDEX IF NOT EXISTS idx_return_requests__store_status ON public.return_requests (store_id, status);
CREATE INDEX IF NOT EXISTS idx_return_requests__person ON public.return_requests (person_id);
CREATE INDEX IF NOT EXISTS idx_return_requests__reason ON public.return_requests (reason_code);
CREATE INDEX IF NOT EXISTS idx_return_requests__customer_vehicle
    ON public.return_requests (customer_vehicle_id) WHERE customer_vehicle_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_return_requests__claim ON public.return_requests (claim_id) WHERE claim_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_return_requests__resolved_by
    ON public.return_requests (resolved_by_account_id) WHERE resolved_by_account_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_return_requests_updated_at
    BEFORE UPDATE ON public.return_requests FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.return_request_items (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid(),
    return_request_id     UUID        NOT NULL,
    order_detail_id       UUID        NOT NULL,
    units                 INTEGER     NOT NULL,
    item_condition        VARCHAR(20),
    restocked             BOOLEAN     NOT NULL DEFAULT false,
    inventory_movement_id UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_return_request_items PRIMARY KEY (id),
    CONSTRAINT fk_return_request_items__return_request_id FOREIGN KEY (return_request_id)
        REFERENCES public.return_requests (id) ON DELETE CASCADE,
    CONSTRAINT fk_return_request_items__order_detail_id FOREIGN KEY (order_detail_id)
        REFERENCES public.order_details (id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_request_items__inventory_movement_id FOREIGN KEY (inventory_movement_id)
        REFERENCES public.inventory_movements (id) ON DELETE RESTRICT,
    CONSTRAINT uq_return_request_items__request_detail UNIQUE (return_request_id, order_detail_id),
    CONSTRAINT chk_return_request_items__units CHECK (units > 0),
    CONSTRAINT chk_return_request_items__condition CHECK (item_condition IN
        ('SEALED', 'OPENED', 'INSTALLED', 'DAMAGED', 'DEFECTIVE')),
    CONSTRAINT chk_return_request_items__restocked CHECK (NOT restocked OR inventory_movement_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS idx_return_request_items__order_detail ON public.return_request_items (order_detail_id);
CREATE INDEX IF NOT EXISTS idx_return_request_items__movement
    ON public.return_request_items (inventory_movement_id) WHERE inventory_movement_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_return_request_items_updated_at
    BEFORE UPDATE ON public.return_request_items FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS public.return_status_history (
    id                BIGINT GENERATED ALWAYS AS IDENTITY,
    return_request_id UUID        NOT NULL,
    from_status       VARCHAR(20),
    to_status         VARCHAR(20) NOT NULL,
    changed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor_type        VARCHAR(10) NOT NULL,
    actor_account_id  UUID,
    notes             VARCHAR(255),
    CONSTRAINT pk_return_status_history PRIMARY KEY (id),
    CONSTRAINT fk_return_status_history__return_request_id FOREIGN KEY (return_request_id)
        REFERENCES public.return_requests (id) ON DELETE RESTRICT,
    CONSTRAINT fk_return_status_history__actor_account_id FOREIGN KEY (actor_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT chk_return_status_history__actor_type CHECK (actor_type IN ('CUSTOMER', 'STORE', 'PLATFORM', 'SYSTEM', 'PROVIDER')),
    CONSTRAINT chk_return_status_history__transition CHECK (from_status IS DISTINCT FROM to_status)
);
CREATE INDEX IF NOT EXISTS idx_return_status_history__request ON public.return_status_history (return_request_id, changed_at);
CREATE INDEX IF NOT EXISTS idx_return_status_history__actor
    ON public.return_status_history (actor_account_id) WHERE actor_account_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_return_status_history_append_only
    BEFORE UPDATE OR DELETE ON public.return_status_history
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete('actor_account_id:null');
CREATE OR REPLACE TRIGGER trg_return_status_history_append_only_truncate
    BEFORE TRUNCATE ON public.return_status_history
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();
