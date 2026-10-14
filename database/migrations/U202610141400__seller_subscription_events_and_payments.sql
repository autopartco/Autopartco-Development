-- =============================================================================
-- U202610141400__seller_subscription_events_and_payments.sql
-- Rollback manual de V202610141400. Flyway Community no lo ejecuta.
--
-- ⚠ Destructivo: borra el historial de suscripción y las columnas de pago
-- añadidas. Requiere SET autopartco.allow_destructive = 'on'.
-- =============================================================================

DO $$
BEGIN
    IF current_setting('autopartco.allow_destructive', true) IS DISTINCT FROM 'on' THEN
        RAISE EXCEPTION 'U202610141400 borra datos; ejecutar con SET autopartco.allow_destructive = ''on''';
    END IF;
END;
$$;

DROP TABLE IF EXISTS public.seller_subscription_events;

DROP INDEX IF EXISTS public.idx_seller_payments__requested_by;

ALTER TABLE public.seller_payments
    DROP CONSTRAINT IF EXISTS chk_seller_payments__period,
    DROP CONSTRAINT IF EXISTS chk_seller_payments__provider,
    DROP COLUMN IF EXISTS requested_by_account_id,
    DROP COLUMN IF EXISTS period_ends_at,
    DROP COLUMN IF EXISTS period_starts_at,
    DROP COLUMN IF EXISTS paid_at,
    DROP COLUMN IF EXISTS plan_snapshot,
    DROP COLUMN IF EXISTS provider;
