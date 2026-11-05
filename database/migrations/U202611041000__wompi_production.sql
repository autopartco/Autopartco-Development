-- =============================================================================
-- U202611041000__wompi_production.sql  (rollback de V202611041000)
-- DESTRUCTIVO: requiere aprobación. Pierde payment_provider_events (rastro de
-- eventos y conciliación de Wompi) y las columnas provider_transaction_id,
-- provider_status, expires_at y requires_manual_refund de payments y
-- requires_manual_refund de seller_payments. Quita también la función
-- public.purge_payment_provider_events(INT, BOOLEAN).
-- Respaldo previo:
--   pg_dump -Fc --data-only -t public.payment_provider_events -t public.payments \
--     -t public.seller_payments -t public.payment_events "$DATABASE_URL" -f wompi_pre_rollback.dump
-- Semillas: AWAITING_PAYMENT, EXPIRED y WOMPI se borran solo si nada las
--   referencia; si están en uso se conservan con un NOTICE.
-- Aborta si payment_events ya tiene filas con source = REDIRECT_SYNC (tabla de
--   solo inserción: no se pueden reescribir).
-- No revierte database/init.sql: si se revierte en una BD creada con el
--   init.sql actual, al reejecutar init.sql las columnas vuelven a crearse.
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

-- payment_events.source: vuelve al CHECK de V202610141020
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.payment_events WHERE source = 'REDIRECT_SYNC') THEN
        RAISE EXCEPTION 'payment_events tiene filas con source = REDIRECT_SYNC; no se puede restaurar el CHECK anterior';
    END IF;
END $$;
ALTER TABLE public.payment_events DROP CONSTRAINT IF EXISTS chk_payment_events__source;
ALTER TABLE public.payment_events ADD CONSTRAINT chk_payment_events__source
    CHECK (source IN ('CHECKOUT', 'WEBHOOK', 'RECONCILIATION', 'ADMIN'));

-- payment_provider_events (índices y triggers caen con la tabla). La función
-- de purga se quita antes. forbid_update_delete() NO se quita: la usan otras
-- tablas y es de V202610130930.
DROP FUNCTION IF EXISTS public.purge_payment_provider_events(INT, BOOLEAN);
DROP TABLE IF EXISTS public.payment_provider_events;

-- seller_payments
DROP INDEX IF EXISTS public.idx_seller_payments__requires_manual_refund;
ALTER TABLE public.seller_payments
    DROP COLUMN IF EXISTS requires_manual_refund;

-- payments
DROP INDEX IF EXISTS public.idx_payments__requires_manual_refund;
DROP INDEX IF EXISTS public.idx_payments__wompi_status_created;
DROP INDEX IF EXISTS public.uq_payments__provider_transaction;
ALTER TABLE public.payments
    DROP CONSTRAINT IF EXISTS chk_payments__provider_status_format,
    DROP CONSTRAINT IF EXISTS chk_payments__provider,
    DROP COLUMN IF EXISTS requires_manual_refund,
    DROP COLUMN IF EXISTS expires_at,
    DROP COLUMN IF EXISTS provider_status,
    DROP COLUMN IF EXISTS provider_transaction_id;

-- Semillas: solo si no están referenciadas (FK RESTRICT/NO ACTION).
DO $$
BEGIN
    BEGIN
        DELETE FROM public.payment_methods WHERE code = 'WOMPI';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'payment_methods WOMPI en uso: se conserva';
    END;
    BEGIN
        DELETE FROM public.payment_statuses WHERE code = 'EXPIRED';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'payment_statuses EXPIRED en uso: se conserva';
    END;
    BEGIN
        -- state_domains cae por ON DELETE CASCADE.
        DELETE FROM public.states WHERE code = 'AWAITING_PAYMENT';
    EXCEPTION WHEN foreign_key_violation THEN
        RAISE NOTICE 'states AWAITING_PAYMENT en uso: se conserva';
    END;
END $$;
