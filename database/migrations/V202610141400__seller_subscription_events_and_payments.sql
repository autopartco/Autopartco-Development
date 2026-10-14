-- =============================================================================
-- V202610141400__seller_subscription_events_and_payments.sql
-- Autopartco | PostgreSQL 16+ | Plan SaaS vendedores §4.5 (SA-D1)
--
-- Propósito
--   - seller_payments: proveedor (WOMPI | SIMULATED), instantánea del plan y
--     periodo pagado. Aditivo; las filas existentes quedan como WOMPI.
--   - seller_subscription_events: historial de solo inserción de la suscripción
--     (TRIAL_STARTED, PAYMENT_APPROVED, PLAN_CHANGED).
--
-- Transacción: SÍ. Rollback: U202610141400__seller_subscription_events_and_payments.sql
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

-- seller_payments (existente; aditivo) ----------------------------------------------
ALTER TABLE public.seller_payments
    ADD COLUMN IF NOT EXISTS provider                 VARCHAR(20) NOT NULL DEFAULT 'WOMPI',
    ADD COLUMN IF NOT EXISTS plan_snapshot            JSONB,
    ADD COLUMN IF NOT EXISTS paid_at                  TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS period_starts_at         TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS period_ends_at           TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS requested_by_account_id  UUID REFERENCES public.accounts(id) ON DELETE SET NULL;

SELECT pg_temp.add_constraint('public.seller_payments', 'chk_seller_payments__provider',
    $c$CHECK (provider IN ('WOMPI', 'SIMULATED'))$c$);
SELECT pg_temp.add_constraint('public.seller_payments', 'chk_seller_payments__period',
    'CHECK (period_ends_at IS NULL OR period_starts_at IS NULL OR period_ends_at > period_starts_at)');

CREATE INDEX IF NOT EXISTS idx_seller_payments__requested_by
    ON public.seller_payments (requested_by_account_id)
    WHERE requested_by_account_id IS NOT NULL;

-- seller_subscription_events (nueva; solo inserción) ----------------------------------
CREATE TABLE IF NOT EXISTS public.seller_subscription_events (
    id                  UUID        NOT NULL DEFAULT gen_random_uuid(),
    application_id      UUID        NOT NULL,
    store_id            UUID        NOT NULL,
    event_type          VARCHAR(30) NOT NULL,
    plan_id             UUID,
    previous_plan_id    UUID,
    payment_id          UUID,
    actor_account_id    UUID,
    details             JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_seller_subscription_events PRIMARY KEY (id),
    CONSTRAINT fk_seller_subscription_events__application
        FOREIGN KEY (application_id) REFERENCES public.seller_applications(id),
    CONSTRAINT fk_seller_subscription_events__store
        FOREIGN KEY (store_id) REFERENCES public.store(id),
    CONSTRAINT fk_seller_subscription_events__plan
        FOREIGN KEY (plan_id) REFERENCES public.seller_plans(id),
    CONSTRAINT fk_seller_subscription_events__previous_plan
        FOREIGN KEY (previous_plan_id) REFERENCES public.seller_plans(id),
    CONSTRAINT fk_seller_subscription_events__payment
        FOREIGN KEY (payment_id) REFERENCES public.seller_payments(id),
    CONSTRAINT fk_seller_subscription_events__actor
        FOREIGN KEY (actor_account_id) REFERENCES public.accounts(id) ON DELETE SET NULL,
    CONSTRAINT chk_seller_subscription_events__type
        CHECK (event_type IN ('TRIAL_STARTED', 'PAYMENT_APPROVED', 'PLAN_CHANGED'))
);

CREATE INDEX IF NOT EXISTS idx_seller_subscription_events__store_occurred
    ON public.seller_subscription_events (store_id, occurred_at DESC);

DROP TRIGGER IF EXISTS trg_seller_subscription_events__append_only ON public.seller_subscription_events;
CREATE TRIGGER trg_seller_subscription_events__append_only
    BEFORE UPDATE OR DELETE ON public.seller_subscription_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
