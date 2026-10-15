-- =============================================================================
-- V202610141310__payment_risk.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.7 (+ auditoría punto 12)
--
-- Propósito
--   - payments: idempotency_key, attempt_number, environment (SANDBOX |
--     PRODUCTION; DEFAULT SANDBOX según el diseño), failure_*, refunded_amount.
--     payments no se escribe hoy desde el backend.
--   - seller_payments.environment: NULLABLE, porque NULL significa "no
--     medido". Un DEFAULT etiquetaría mal los pagos reales de Wompi. El
--     backend debe rellenarlo según la configuración de Wompi.
--     SubscriptionPayment.java no la mapea y el INSERT no se rompe.
--   - public.payment_risk_assessments: solo inserción, con excepción para las
--     columnas de resultado (outcome, outcome_at, outcome_source,
--     reviewed_by_account_id) y para anular ip_address (retención de 180
--     días, A9).
--   Claves permitidas en signals (lista blanca validada en el backend):
--     account_age_days, is_first_order, orders_last_24h, failed_payments_last_24h,
--     login_failures_last_24h, distinct_shipping_addresses_30d,
--     ip_department_matches_shipping, email_domain_disposable,
--     order_total_vs_account_avg, high_value_category_share.
--   Un pago SANDBOX nunca entra en un dataset de entrenamiento real.
--   device_hash es un HMAC-SHA256 con un secreto del backend (A8).
--
-- Transacción: SÍ. Rollback: U202610141310__payment_risk.sql
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

ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS attempt_number  SMALLINT      NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS environment     VARCHAR(10)   NOT NULL DEFAULT 'SANDBOX',
    ADD COLUMN IF NOT EXISTS failure_code    VARCHAR(50),
    ADD COLUMN IF NOT EXISTS failure_message VARCHAR(255),
    ADD COLUMN IF NOT EXISTS failed_at       TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS refunded_amount NUMERIC(12,2) NOT NULL DEFAULT 0;

SELECT pg_temp.add_constraint('public.payments', 'chk_payments__attempt_number', 'CHECK (attempt_number >= 1)');
SELECT pg_temp.add_constraint('public.payments', 'chk_payments__environment',
    $c$CHECK (environment IN ('SANDBOX', 'PRODUCTION'))$c$);
SELECT pg_temp.add_constraint('public.payments', 'chk_payments__refunded_amount',
    'CHECK (refunded_amount >= 0 AND refunded_amount <= amount)');
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments__idempotency_key
    ON public.payments (idempotency_key) WHERE idempotency_key IS NOT NULL;

ALTER TABLE public.seller_payments
    ADD COLUMN IF NOT EXISTS environment VARCHAR(10);
SELECT pg_temp.add_constraint('public.seller_payments', 'chk_seller_payments__environment',
    $c$CHECK (environment IN ('SANDBOX', 'PRODUCTION'))$c$);

CREATE TABLE IF NOT EXISTS public.payment_risk_assessments (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY,
    payment_id             UUID         NOT NULL,
    order_id               UUID         NOT NULL,
    assessed_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    stage                  VARCHAR(20)  NOT NULL,
    risk_score             NUMERIC(6,5),
    provider_risk_score    NUMERIC(6,5),
    decision               VARCHAR(10)  NOT NULL,
    rules_triggered        TEXT[]       NOT NULL DEFAULT '{}',
    signals                JSONB        NOT NULL DEFAULT '{}'::jsonb,
    model_version_id       UUID,
    ip_address             INET,
    device_hash            CHAR(64),
    outcome                VARCHAR(20),
    outcome_at             TIMESTAMPTZ,
    outcome_source         VARCHAR(20),
    reviewed_by_account_id UUID,
    CONSTRAINT pk_payment_risk_assessments PRIMARY KEY (id),
    CONSTRAINT fk_payment_risk_assessments__payment_id FOREIGN KEY (payment_id)
        REFERENCES public.payments (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_risk_assessments__order_id FOREIGN KEY (order_id)
        REFERENCES public.orders (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_risk_assessments__model_version_id FOREIGN KEY (model_version_id)
        REFERENCES ml.model_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_risk_assessments__reviewed_by_account_id FOREIGN KEY (reviewed_by_account_id)
        REFERENCES public.accounts (id) ON DELETE SET NULL,
    CONSTRAINT chk_payment_risk_assessments__stage CHECK (stage IN ('PRE_AUTHORIZATION', 'POST_AUTHORIZATION', 'MANUAL_REVIEW')),
    CONSTRAINT chk_payment_risk_assessments__scores CHECK (risk_score BETWEEN 0 AND 1 AND provider_risk_score BETWEEN 0 AND 1),
    CONSTRAINT chk_payment_risk_assessments__decision CHECK (decision IN ('ALLOW', 'REVIEW', 'DENY')),
    CONSTRAINT chk_payment_risk_assessments__signals CHECK (jsonb_typeof(signals) = 'object' AND pg_column_size(signals) <= 4096),
    CONSTRAINT chk_payment_risk_assessments__device_hash CHECK (device_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_payment_risk_assessments__outcome CHECK (outcome IN
        ('LEGIT', 'FRAUD_CONFIRMED', 'CHARGEBACK', 'FRIENDLY_FRAUD', 'UNKNOWN')),
    CONSTRAINT chk_payment_risk_assessments__outcome_source CHECK (outcome_source IN
        ('PROVIDER', 'MANUAL_REVIEW', 'CHARGEBACK_NOTICE', 'TIMEOUT')),
    CONSTRAINT chk_payment_risk_assessments__outcome_complete CHECK (
        (outcome IS NULL) = (outcome_at IS NULL) AND (outcome IS NULL) = (outcome_source IS NULL))
);
CREATE INDEX IF NOT EXISTS idx_payment_risk_assessments__payment ON public.payment_risk_assessments (payment_id);
CREATE INDEX IF NOT EXISTS idx_payment_risk_assessments__order ON public.payment_risk_assessments (order_id);
CREATE INDEX IF NOT EXISTS idx_payment_risk_assessments__model_version
    ON public.payment_risk_assessments (model_version_id) WHERE model_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_risk_assessments__reviewed_by
    ON public.payment_risk_assessments (reviewed_by_account_id) WHERE reviewed_by_account_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_risk_assessments__pending_outcome
    ON public.payment_risk_assessments (assessed_at) WHERE outcome IS NULL;
CREATE INDEX IF NOT EXISTS brin_payment_risk_assessments__assessed_at
    ON public.payment_risk_assessments USING brin (assessed_at) WITH (pages_per_range = 32);

CREATE OR REPLACE TRIGGER trg_payment_risk_assessments_append_only
    BEFORE UPDATE OR DELETE ON public.payment_risk_assessments
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete(
        'outcome', 'outcome_at', 'outcome_source', 'reviewed_by_account_id', 'ip_address:null');
CREATE OR REPLACE TRIGGER trg_payment_risk_assessments_append_only_truncate
    BEFORE TRUNCATE ON public.payment_risk_assessments
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

DROP FUNCTION pg_temp.add_constraint(regclass, TEXT, TEXT);
