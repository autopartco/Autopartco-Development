-- =============================================================================
-- V202611041000__wompi_production.sql
-- Autopartco | PostgreSQL 16+ | Cobros Wompi en producción (ADR 0004, T2)
--
-- Propósito (aditivo; ningún INSERT actual del backend se rompe)
--   - Semillas:
--       states             AWAITING_PAYMENT, solo en el dominio ORDER
--                          (state_domains, V202610151020). Las ventas (sales)
--                          siguen en PENDING mientras se espera el pago.
--       payment_statuses   EXPIRED (vencido por Autopartco, no por Wompi).
--       payment_methods    WOMPI ("Wompi"): tarjeta, PSE, Nequi y Bancolombia;
--                          el medio concreto lo elige el comprador en Wompi.
--     cancellation_reasons: no se crea motivo nuevo. El vencimiento usa
--     PAYMENT_TIMEOUT, ya sembrado en V202610141020 (ADR 0004).
--   - payments: provider_transaction_id (id de la transacción en Wompi, único
--     por proveedor), provider_status (estado crudo: PENDING, APPROVED,
--     DECLINED, VOIDED, ERROR), expires_at (vencimiento de la reserva) y
--     requires_manual_refund (pago aprobado sobre un pedido ya vencido o
--     cancelado). environment e idempotency_key ya existen (V202610141310).
--     CHECK de provider (SIMULATED | WOMPI) NOT VALID: se exige a filas nuevas
--     y modificadas; validar después con el diagnóstico de abajo.
--   - seller_payments: requires_manual_refund. provider_transaction_id
--     (init.sql, VARCHAR(120) UNIQUE) y environment (V202610141310, nullable)
--     ya existen y no se tocan. La obligatoriedad de environment en filas
--     nuevas queda para una migración de contracción posterior al despliegue
--     del backend (T3): hoy SubscriptionPayment.java no la mapea y un NOT NULL
--     o un trigger romperían el INSERT actual.
--   - payment_events.source: el CHECK de V202610141020 se amplía con
--     REDIRECT_SYNC (CHECKOUT | WEBHOOK | RECONCILIATION | ADMIN | REDIRECT_SYNC).
--   - public.payment_provider_events (nueva, solo inserción): eventos con firma
--     válida y consultas a Wompi, aplicados o no. Los eventos con firma
--     inválida NO se guardan (solo log WARN). Deduplicación por
--     (provider, checksum) cuando hay checksum. Datos personales: el evento de
--     Wompi trae datos del comprador (correo, nombre, teléfono) y datos
--     parciales de la tarjeta (marca, últimos dígitos, titular). El backend
--     guarda en payload SOLO una lista blanca: event, environment, timestamp,
--     signature y, de data.transaction: id, reference, status, status_message,
--     amount_in_cents, currency, payment_method_type, created_at,
--     finalized_at. Ampliar la lista exige revisión (Ley 1581).
--   - public.purge_payment_provider_events(months, dry_run): purga de
--     retención (24 meses por defecto, dry_run por defecto). No se programa.
--   - database/init.sql (esquema activo) refleja estos cambios, incluidos
--     forbid_update_delete() (copia literal de V202610130930), el trigger de
--     solo inserción, la función de purga y los GRANT/REVOKE condicionados.
--     No refleja lo que depende de objetos que solo crean las migraciones:
--     state_domains, payment_events.source y la semilla AWAITING_PAYMENT.
--
-- Precondiciones (camino de migraciones versionadas; el guard de abajo aborta
-- si falta alguna):
--   - V202610130930  public.forbid_update_delete()
--   - V202610141020  payment_events.source y chk_payment_events__source
--   - V202610151020  public.state_domains
--   Una BD creada solo con init.sql NO cumple estas precondiciones: no aplique
--   esta V sobre ella sin haber aplicado antes las V anteriores (README §2).
--
-- Diagnóstico previo al VALIDATE de chk_payments__provider (debe dar 0 filas):
--   SELECT provider, count(*) FROM public.payments
--   WHERE provider NOT IN ('SIMULATED', 'WOMPI') GROUP BY 1;
--   ALTER TABLE public.payments VALIDATE CONSTRAINT chk_payments__provider;
--
-- Transacción: SÍ. Bloqueos: ACCESS EXCLUSIVE breve en payments,
--   seller_payments y payment_events (ADD COLUMN con DEFAULT constante no
--   reescribe; el CHECK de payment_events recorre la tabla, hoy pequeña).
-- Rollback: U202611041000__wompi_production.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

DO $$
DECLARE
    missing TEXT[] := ARRAY[]::TEXT[];
BEGIN
    IF to_regprocedure('public.forbid_update_delete()') IS NULL THEN
        missing := missing || 'public.forbid_update_delete() (V202610130930)';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_schema = 'public' AND table_name = 'payment_events'
                     AND column_name = 'source') THEN
        missing := missing || 'payment_events.source (V202610141020)';
    END IF;
    IF to_regclass('public.state_domains') IS NULL THEN
        missing := missing || 'public.state_domains (V202610151020)';
    END IF;
    IF cardinality(missing) > 0 THEN
        RAISE EXCEPTION 'V202611041000: faltan precondiciones: %. Aplique antes las V anteriores (README §2).',
            array_to_string(missing, ', ');
    END IF;
END $$;

CREATE OR REPLACE FUNCTION pg_temp.add_constraint(p_table regclass, p_name TEXT, p_ddl TEXT)
RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = p_table AND conname = p_name) THEN
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I %s', p_table, p_name, p_ddl);
    END IF;
END;
$$;

-- -----------------------------------------------------------------------------
-- Semillas (idempotentes)
-- -----------------------------------------------------------------------------
INSERT INTO public.states (code, description)
VALUES ('AWAITING_PAYMENT', 'Pedido en espera de pago')
ON CONFLICT (code) DO NOTHING;

INSERT INTO public.state_domains (state_id, domain)
SELECT s.id, 'ORDER' FROM public.states s WHERE s.code = 'AWAITING_PAYMENT'
ON CONFLICT DO NOTHING;

INSERT INTO public.payment_statuses (code, description)
VALUES ('EXPIRED', 'Pago vencido sin aprobación')
ON CONFLICT DO NOTHING;

INSERT INTO public.payment_methods (code, name)
VALUES ('WOMPI', 'Wompi')
ON CONFLICT DO NOTHING;

-- -----------------------------------------------------------------------------
-- payments
-- -----------------------------------------------------------------------------
ALTER TABLE public.payments
    ADD COLUMN IF NOT EXISTS provider_transaction_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS provider_status         VARCHAR(30),
    ADD COLUMN IF NOT EXISTS expires_at              TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS requires_manual_refund  BOOLEAN NOT NULL DEFAULT false;

SELECT pg_temp.add_constraint('public.payments', 'chk_payments__provider',
    $c$CHECK (provider IN ('SIMULATED', 'WOMPI')) NOT VALID$c$);
SELECT pg_temp.add_constraint('public.payments', 'chk_payments__provider_status_format',
    $c$CHECK (provider_status ~ '^[A-Z_]{1,30}$')$c$);

-- Idempotencia de la aprobación (regla 2): una transacción del proveedor se
-- aplica a un solo pago.
CREATE UNIQUE INDEX IF NOT EXISTS uq_payments__provider_transaction
    ON public.payments (provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;

-- Conciliación y vencimiento (regla 7):
--   SELECT ... FROM payments
--   WHERE provider = 'WOMPI' AND payment_status_id = :pending
--     AND created_at < now() - interval '2 minutes'
--   ORDER BY created_at LIMIT :n;
-- EXPLAIN (ANALYZE, BUFFERS) esperado: Index Scan idx_payments__wompi_status_created.
CREATE INDEX IF NOT EXISTS idx_payments__wompi_status_created
    ON public.payments (payment_status_id, created_at)
    WHERE provider = 'WOMPI';

-- Revisión manual de pagos tardíos (regla 5).
CREATE INDEX IF NOT EXISTS idx_payments__requires_manual_refund
    ON public.payments (created_at)
    WHERE requires_manual_refund;

-- -----------------------------------------------------------------------------
-- seller_payments
-- -----------------------------------------------------------------------------
ALTER TABLE public.seller_payments
    ADD COLUMN IF NOT EXISTS requires_manual_refund BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_seller_payments__requires_manual_refund
    ON public.seller_payments (created_at)
    WHERE requires_manual_refund;

-- -----------------------------------------------------------------------------
-- payment_events.source: admite REDIRECT_SYNC
-- -----------------------------------------------------------------------------
ALTER TABLE public.payment_events DROP CONSTRAINT IF EXISTS chk_payment_events__source;
ALTER TABLE public.payment_events ADD CONSTRAINT chk_payment_events__source
    CHECK (source IN ('CHECKOUT', 'WEBHOOK', 'RECONCILIATION', 'ADMIN', 'REDIRECT_SYNC'));

-- -----------------------------------------------------------------------------
-- payment_provider_events (solo inserción)
--   source   WEBHOOK (POST /api/v1/webhooks/wompi), REDIRECT_SYNC (sync tras la
--            redirección) o RECONCILIATION (job de conciliación).
--   checksum signature.checksum del evento de Wompi; NULL en REDIRECT_SYNC y
--            RECONCILIATION. Único parcial (provider, checksum) WHERE checksum
--            IS NOT NULL. El backend deduplica con
--            INSERT ... ON CONFLICT (provider, checksum) WHERE checksum IS NOT NULL DO NOTHING
--            o capturando 23505 de uq_payment_provider_events__provider_checksum.
--   outcome  APPLIED | DUPLICATE | IGNORED | REJECTED_MISMATCH | UNKNOWN_REFERENCE.
--   environment  el del backend que recibió el evento (no el que declara el payload).
--   payload  solo la lista blanca del encabezado (sin correo, nombre,
--            teléfono ni datos parciales de tarjeta). Aun así contiene
--            referencias y montos ligados a una persona (Ley 1581).
--   Retención: 24 meses con public.purge_payment_provider_events (abajo).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS public.payment_provider_events (
    id                      UUID         NOT NULL DEFAULT gen_random_uuid(),
    provider                VARCHAR(20)  NOT NULL,
    environment             VARCHAR(10)  NOT NULL,
    event_type              VARCHAR(60),
    checksum                VARCHAR(128),
    reference               VARCHAR(100),
    provider_transaction_id VARCHAR(100),
    provider_status         VARCHAR(30),
    amount_in_cents         BIGINT,
    currency                VARCHAR(3),
    source                  VARCHAR(20)  NOT NULL,
    outcome                 VARCHAR(30)  NOT NULL,
    payload                 JSONB        NOT NULL DEFAULT '{}'::jsonb,
    received_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_payment_provider_events PRIMARY KEY (id),
    CONSTRAINT chk_payment_provider_events__provider CHECK (provider IN ('WOMPI', 'SIMULATED')),
    CONSTRAINT chk_payment_provider_events__environment CHECK (environment IN ('SANDBOX', 'PRODUCTION')),
    CONSTRAINT chk_payment_provider_events__source CHECK (source IN ('WEBHOOK', 'REDIRECT_SYNC', 'RECONCILIATION')),
    CONSTRAINT chk_payment_provider_events__outcome CHECK (outcome IN
        ('APPLIED', 'DUPLICATE', 'IGNORED', 'REJECTED_MISMATCH', 'UNKNOWN_REFERENCE')),
    CONSTRAINT chk_payment_provider_events__amount CHECK (amount_in_cents >= 0),
    CONSTRAINT chk_payment_provider_events__currency CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT chk_payment_provider_events__payload CHECK (
        jsonb_typeof(payload) = 'object' AND pg_column_size(payload) <= 32768)
);

-- CREATE TABLE IF NOT EXISTS no añade restricciones si la tabla ya existía
-- (p. ej. creada por init.sql o por ddl-auto de una entidad). Se añaden aparte.
SELECT pg_temp.add_constraint('public.payment_provider_events', 'chk_payment_provider_events__provider',
    $c$CHECK (provider IN ('WOMPI', 'SIMULATED'))$c$);
SELECT pg_temp.add_constraint('public.payment_provider_events', 'chk_payment_provider_events__environment',
    $c$CHECK (environment IN ('SANDBOX', 'PRODUCTION'))$c$);
SELECT pg_temp.add_constraint('public.payment_provider_events', 'chk_payment_provider_events__source',
    $c$CHECK (source IN ('WEBHOOK', 'REDIRECT_SYNC', 'RECONCILIATION'))$c$);
SELECT pg_temp.add_constraint('public.payment_provider_events', 'chk_payment_provider_events__outcome',
    $c$CHECK (outcome IN ('APPLIED', 'DUPLICATE', 'IGNORED', 'REJECTED_MISMATCH', 'UNKNOWN_REFERENCE'))$c$);
SELECT pg_temp.add_constraint('public.payment_provider_events', 'chk_payment_provider_events__amount',
    'CHECK (amount_in_cents >= 0)');
SELECT pg_temp.add_constraint('public.payment_provider_events', 'chk_payment_provider_events__currency',
    $c$CHECK (currency ~ '^[A-Z]{3}$')$c$);
SELECT pg_temp.add_constraint('public.payment_provider_events', 'chk_payment_provider_events__payload',
    $c$CHECK (jsonb_typeof(payload) = 'object' AND pg_column_size(payload) <= 32768)$c$);

CREATE UNIQUE INDEX IF NOT EXISTS uq_payment_provider_events__provider_checksum
    ON public.payment_provider_events (provider, checksum)
    WHERE checksum IS NOT NULL;
-- Historial de una referencia (APM-/APC-), del más reciente al más antiguo.
CREATE INDEX IF NOT EXISTS idx_payment_provider_events__reference_received
    ON public.payment_provider_events (reference, received_at DESC)
    WHERE reference IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_provider_events__transaction
    ON public.payment_provider_events (provider, provider_transaction_id)
    WHERE provider_transaction_id IS NOT NULL;
-- Monitoreo de eventos inaplicables (log ERROR del plan).
CREATE INDEX IF NOT EXISTS idx_payment_provider_events__needs_review
    ON public.payment_provider_events (received_at)
    WHERE outcome IN ('REJECTED_MISMATCH', 'UNKNOWN_REFERENCE');
CREATE INDEX IF NOT EXISTS brin_payment_provider_events__received_at
    ON public.payment_provider_events USING brin (received_at) WITH (pages_per_range = 32);

CREATE OR REPLACE TRIGGER trg_payment_provider_events_append_only
    BEFORE UPDATE OR DELETE ON public.payment_provider_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_payment_provider_events_append_only_truncate
    BEFORE TRUNCATE ON public.payment_provider_events
    FOR EACH STATEMENT EXECUTE FUNCTION public.forbid_update_delete();

-- Supabase: todas las tablas de public tienen RLS (sin políticas) para que
-- PostgREST (anon/authenticated) no las exponga; el backend usa el dueño.
ALTER TABLE public.payment_provider_events ENABLE ROW LEVEL SECURITY;

COMMENT ON TABLE public.payment_provider_events IS
    'Eventos con firma válida y consultas a proveedores de pago (Wompi), aplicados o no. Solo inserción. payload: solo la lista blanca de campos del backend (sin datos del comprador ni de tarjeta). Retención: purge_payment_provider_events.';

-- -----------------------------------------------------------------------------
-- Retención (patrón de V202610151090): crear la función no borra nada. Borra
-- solo con p_dry_run = false; el plazo debe validarse con asesoría legal. No se
-- programa: activarla en RetentionJob requiere aprobación (A9).
-- La tabla es de solo inserción: la función fija autopartco.allow_purge = 'on'
-- solo durante el DELETE (set_config local; se apaga al terminar). Es SECURITY INVOKER: quien la ejecute necesita DELETE en la tabla,
-- que autopartco_app no tiene (A6).
-- Uso: SELECT public.purge_payment_provider_events();          -- cuenta
--      SELECT public.purge_payment_provider_events(24, false); -- borra
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.purge_payment_provider_events(
    p_months INT DEFAULT 24, p_dry_run BOOLEAN DEFAULT true
)
RETURNS BIGINT
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    n BIGINT;
BEGIN
    IF p_months IS NULL OR p_months < 1 THEN
        RAISE EXCEPTION 'p_months debe ser >= 1';
    END IF;
    IF p_dry_run THEN
        SELECT count(*) INTO n FROM payment_provider_events
        WHERE received_at < now() - make_interval(months => p_months);
        RETURN n;
    END IF;
    -- set_config local (no la cláusula SET de la función): en Supabase el rol
    -- postgres no puede fijar parámetros personalizados en CREATE FUNCTION.
    PERFORM set_config('autopartco.allow_purge', 'on', true);
    DELETE FROM payment_provider_events
    WHERE received_at < now() - make_interval(months => p_months);
    GET DIAGNOSTICS n = ROW_COUNT;
    PERFORM set_config('autopartco.allow_purge', 'off', true);
    RETURN n;
END;
$$;

COMMENT ON FUNCTION public.purge_payment_provider_events(INT, BOOLEAN) IS
    'DESTRUCTIVA (A9). dry_run por defecto. Borra payment_provider_events con received_at anterior a p_months (24) meses.';
REVOKE ALL ON FUNCTION public.purge_payment_provider_events(INT, BOOLEAN) FROM PUBLIC;

-- Roles (V202610151080): si ya existen, la app solo inserta y lee.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'autopartco_app') THEN
        GRANT SELECT, INSERT ON public.payment_provider_events TO autopartco_app;
        REVOKE UPDATE, DELETE, TRUNCATE ON public.payment_provider_events FROM autopartco_app;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'autopartco_analyst') THEN
        REVOKE ALL ON public.payment_provider_events FROM autopartco_analyst;
    END IF;
END $$;

DROP FUNCTION pg_temp.add_constraint(regclass, TEXT, TEXT);
