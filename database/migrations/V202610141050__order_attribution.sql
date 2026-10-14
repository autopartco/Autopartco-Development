-- =============================================================================
-- V202610141050__order_attribution.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.8
--
-- Propósito: atribución de canal por pedido (primer y último toque). Una fila
--   por pedido; la escribe el checkout (marketplace.checkout).
--   UTM y sesión solo se rellenan con consentimiento ANALYTICS_TRACKING (§8).
--   Sin él, solo device_type. landing_path se guarda sin query string, salvo
--   los parámetros utm_*.
--
-- Transacción: SÍ. Rollback: U202610141050__order_attribution.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS public.order_attributions (
    order_id                UUID         NOT NULL,
    session_id              UUID,
    anonymous_id            UUID,
    first_touch_at          TIMESTAMPTZ,
    first_utm_source        VARCHAR(100),
    first_utm_medium        VARCHAR(100),
    first_utm_campaign      VARCHAR(150),
    first_utm_term          VARCHAR(150),
    first_utm_content       VARCHAR(150),
    first_referrer_host     VARCHAR(255),
    first_landing_path      VARCHAR(500),
    last_touch_at           TIMESTAMPTZ,
    last_utm_source         VARCHAR(100),
    last_utm_medium         VARCHAR(100),
    last_utm_campaign       VARCHAR(150),
    last_utm_term           VARCHAR(150),
    last_utm_content        VARCHAR(150),
    last_referrer_host      VARCHAR(255),
    device_type             VARCHAR(10),
    os_family               VARCHAR(30),
    browser_family          VARCHAR(30),
    touches_count           SMALLINT,
    attribution_window_days SMALLINT     NOT NULL DEFAULT 30,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_order_attributions PRIMARY KEY (order_id),
    CONSTRAINT fk_order_attributions__order_id FOREIGN KEY (order_id)
        REFERENCES public.orders (id) ON DELETE RESTRICT,
    CONSTRAINT chk_order_attributions__device_type CHECK (device_type IN ('DESKTOP', 'MOBILE', 'TABLET', 'OTHER', 'UNKNOWN')),
    CONSTRAINT chk_order_attributions__touches CHECK (touches_count >= 0),
    CONSTRAINT chk_order_attributions__window CHECK (attribution_window_days BETWEEN 1 AND 365),
    CONSTRAINT chk_order_attributions__touch_order CHECK (
        first_touch_at IS NULL OR last_touch_at IS NULL OR last_touch_at >= first_touch_at),
    CONSTRAINT chk_order_attributions__landing_no_query CHECK (
        first_landing_path IS NULL OR first_landing_path !~ '\?(?!(utm_[a-z]+=[^&]*&?)+$)')
);

CREATE INDEX IF NOT EXISTS idx_order_attributions__session
    ON public.order_attributions (session_id) WHERE session_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_order_attributions__last_source
    ON public.order_attributions (last_utm_source, last_utm_medium) WHERE last_utm_source IS NOT NULL;
