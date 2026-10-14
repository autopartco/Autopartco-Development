-- =============================================================================
-- U202610141310__payment_risk.sql  (rollback de V202610141310)
-- DESTRUCTIVO: requiere aprobación (evaluaciones de riesgo con sus resultados,
-- que son las etiquetas del modelo de fraude, y columnas de pago).
-- Respaldo: pg_dump --data-only -t public.payment_risk_assessments -t public.payments -t public.seller_payments "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

DROP TABLE IF EXISTS public.payment_risk_assessments;
ALTER TABLE public.seller_payments
    DROP CONSTRAINT IF EXISTS chk_seller_payments__environment,
    DROP COLUMN IF EXISTS environment;
DROP INDEX IF EXISTS public.uq_payments__idempotency_key;
ALTER TABLE public.payments
    DROP COLUMN IF EXISTS refunded_amount,
    DROP COLUMN IF EXISTS failed_at,
    DROP COLUMN IF EXISTS failure_message,
    DROP COLUMN IF EXISTS failure_code,
    DROP COLUMN IF EXISTS environment,
    DROP COLUMN IF EXISTS attempt_number,
    DROP COLUMN IF EXISTS idempotency_key;
