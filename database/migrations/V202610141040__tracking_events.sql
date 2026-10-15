-- =============================================================================
-- V202610141040__tracking_events.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.9
--
-- Propósito: esquema tracking (intención y exposición; los hechos de negocio
--   viven en el OLTP).
--   - tracking.event_definitions: catálogo y gobierno de eventos (+ semilla).
--     pii_reviewed se siembra en false: el backend rechaza los eventos no
--     revisados. Marcar cada evento como revisado es una decisión de
--     gobierno de datos, no de esta migración.
--   - tracking.sessions, search_events, product_events y events: particionadas
--     por mes, con partición default. Se crean el mes actual y los 3
--     siguientes. Idempotencia con UNIQUE (event_id, occurred_at) y
--     ON CONFLICT DO NOTHING.
--   - tracking.identity_links: anonymous_id <-> subject_key.
--   NUNCA se guardan IP, email, teléfono ni VIN completo (solo WMI y el código
--   de año). query_text llega depurado del servidor.
--
-- PostgreSQL 16 NO admite columnas IDENTITY en tablas particionadas (llega
--   en PG 17). Por eso id usa una secuencia propia (OWNED BY) con DEFAULT
--   nextval. El efecto es el mismo que el diseño.
-- Sin BRIN: la PK btree (occurred_at|started_at, id) ya empieza por la
--   columna de tiempo y cubre los rangos (revisión de qa-seguridad).
-- Las tablas de eventos son de solo inserción. La retención (25 meses) se
--   aplica quitando particiones (A9), sin DELETE.
--
-- Transacción: SÍ. Rollback: U202610141040__tracking_events.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

-- -----------------------------------------------------------------------------
-- Catálogo de eventos
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tracking.event_definitions (
    event_name         VARCHAR(60)  NOT NULL,
    target_table       VARCHAR(30)  NOT NULL,
    description        VARCHAR(255) NOT NULL,
    allowed_properties JSONB        NOT NULL DEFAULT '[]'::jsonb,
    pii_reviewed       BOOLEAN      NOT NULL DEFAULT false,
    owner_module       VARCHAR(60)  NOT NULL,
    introduced_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    retired_at         TIMESTAMPTZ,
    CONSTRAINT pk_event_definitions PRIMARY KEY (event_name),
    CONSTRAINT chk_event_definitions__event_name CHECK (event_name ~ '^[a-z]+(_[a-z]+)*$'),
    CONSTRAINT chk_event_definitions__target_table CHECK (target_table IN ('search_events', 'product_events', 'events')),
    CONSTRAINT chk_event_definitions__allowed_properties CHECK (jsonb_typeof(allowed_properties) = 'array'),
    CONSTRAINT chk_event_definitions__retired CHECK (retired_at IS NULL OR retired_at > introduced_at)
);

INSERT INTO tracking.event_definitions (event_name, target_table, description, allowed_properties, owner_module) VALUES
    ('search_performed',          'search_events',  'Búsqueda ejecutada (texto, referencia, vehículo, VIN, categoría)', '[]', 'core.tracking'),
    ('product_view',              'product_events', 'Vista de ficha de producto', '[]', 'core.tracking'),
    ('search_result_click',       'product_events', 'Clic en un resultado de búsqueda', '[]', 'core.tracking'),
    ('add_to_cart',               'product_events', 'Producto añadido al carrito', '[]', 'core.tracking'),
    ('remove_from_cart',          'product_events', 'Producto retirado del carrito', '[]', 'core.tracking'),
    ('cart_quantity_changed',     'product_events', 'Cambio de cantidad en el carrito', '[]', 'core.tracking'),
    ('contact_store',             'product_events', 'Contacto con el almacén desde la ficha', '[]', 'core.tracking'),
    ('compatibility_check',       'product_events', 'Verificación de compatibilidad con un vehículo', '[]', 'core.tracking'),
    ('favorite_added',            'product_events', 'Producto marcado como favorito', '[]', 'core.tracking'),
    ('favorite_removed',          'product_events', 'Producto retirado de favoritos', '[]', 'core.tracking'),
    ('share',                     'product_events', 'Producto compartido', '[]', 'core.tracking'),
    ('page_view',                 'events',         'Vista de página', '["path_template","referrer_host"]', 'core.tracking'),
    ('filter_applied',            'events',         'Filtro aplicado en listados', '["filter_key","filter_value_kind"]', 'core.tracking'),
    ('checkout_started',          'events',         'Inicio del checkout', '["items_count","cart_value_band"]', 'core.tracking'),
    ('checkout_completed',        'events',         'Checkout completado', '["items_count"]', 'core.tracking'),
    ('signup_completed',          'events',         'Registro completado', '[]', 'core.tracking'),
    ('login_completed',           'events',         'Inicio de sesión', '[]', 'core.tracking'),
    ('analytics_consent_granted', 'events',         'Consentimiento de analítica otorgado (visitante)', '["policy_version"]', 'core.tracking'),
    ('analytics_consent_revoked', 'events',         'Consentimiento de analítica revocado (visitante)', '["policy_version"]', 'core.tracking')
ON CONFLICT (event_name) DO NOTHING;

-- -----------------------------------------------------------------------------
-- Sesiones (mutables: last_seen_at se actualiza)
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tracking.sessions (
    session_id            UUID         NOT NULL,
    started_at            TIMESTAMPTZ  NOT NULL,
    last_seen_at          TIMESTAMPTZ  NOT NULL,
    anonymous_id          UUID         NOT NULL,
    subject_key           UUID,
    surface               VARCHAR(20)  NOT NULL,
    device_type           VARCHAR(10)  NOT NULL,
    os_family             VARCHAR(30),
    browser_family        VARCHAR(30),
    app_version           VARCHAR(20),
    utm_source            VARCHAR(100),
    utm_medium            VARCHAR(100),
    utm_campaign          VARCHAR(150),
    utm_term              VARCHAR(150),
    utm_content           VARCHAR(150),
    referrer_host         VARCHAR(255),
    landing_path          VARCHAR(500),
    geo_department_code   CHAR(2),
    geo_municipality_code CHAR(5),
    is_bot                BOOLEAN      NOT NULL DEFAULT false,
    CONSTRAINT pk_sessions PRIMARY KEY (started_at, session_id),
    CONSTRAINT chk_sessions__surface CHECK (surface IN ('MARKETPLACE', 'STORESUITE', 'OPERATIONS')),
    CONSTRAINT chk_sessions__device_type CHECK (device_type IN ('DESKTOP', 'MOBILE', 'TABLET', 'OTHER', 'UNKNOWN')),
    CONSTRAINT chk_sessions__last_seen CHECK (last_seen_at >= started_at),
    -- Sin query string salvo parámetros utm_*, y sin PII evidente
    -- (emails ni celulares colombianos 3XXXXXXXXX / +57).
    CONSTRAINT chk_sessions__landing_path CHECK (
        landing_path !~ '\?(?!(utm_[a-z]+=[^&]*&?)+$)'
        AND landing_path !~ '@'
        AND landing_path !~ '(^|[^0-9])(\+?57)?3[0-9]{9}([^0-9]|$)'),
    CONSTRAINT chk_sessions__referrer_host CHECK (referrer_host ~ '^[A-Za-z0-9.-]+(:[0-9]+)?$')
) PARTITION BY RANGE (started_at);
CREATE TABLE IF NOT EXISTS tracking.sessions_default PARTITION OF tracking.sessions DEFAULT;
CREATE INDEX IF NOT EXISTS idx_sessions__session_id ON tracking.sessions (session_id);
CREATE INDEX IF NOT EXISTS idx_sessions__subject_key ON tracking.sessions (subject_key) WHERE subject_key IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Búsquedas
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS tracking.search_events_id_seq AS BIGINT;
CREATE TABLE IF NOT EXISTS tracking.search_events (
    id                       BIGINT        NOT NULL DEFAULT nextval('tracking.search_events_id_seq'),
    event_id                 UUID          NOT NULL,
    occurred_at              TIMESTAMPTZ   NOT NULL,
    received_at              TIMESTAMPTZ   NOT NULL DEFAULT now(),
    session_id               UUID          NOT NULL,
    anonymous_id             UUID          NOT NULL,
    subject_key              UUID,
    surface                  VARCHAR(20)   NOT NULL,
    search_type              VARCHAR(20)   NOT NULL,
    query_text               VARCHAR(200),
    query_hash               CHAR(64),
    make_id                  UUID,
    model_id                 UUID,
    version_id               UUID,
    vehicle_year             SMALLINT,
    vin_wmi                  CHAR(3),
    vin_model_year_code      CHAR(1),
    vin_decoded              BOOLEAN,
    customer_vehicle_used    BOOLEAN       NOT NULL DEFAULT false,
    category_id              UUID,
    manufacturer_id          UUID,
    store_id                 UUID,
    price_min                NUMERIC(12,2),
    price_max                NUMERIC(12,2),
    geo_municipality_code    CHAR(5),
    filters                  JSONB         NOT NULL DEFAULT '{}'::jsonb,
    sort                     VARCHAR(30),
    page                     SMALLINT      NOT NULL DEFAULT 1,
    results_count            INTEGER       NOT NULL,
    zero_results             BOOLEAN       GENERATED ALWAYS AS (results_count = 0) STORED,
    compatible_results_count INTEGER,
    latency_ms               INTEGER,
    CONSTRAINT pk_search_events PRIMARY KEY (occurred_at, id),
    CONSTRAINT uq_search_events__event UNIQUE (event_id, occurred_at),
    CONSTRAINT chk_search_events__surface CHECK (surface IN ('MARKETPLACE', 'STORESUITE', 'OPERATIONS')),
    CONSTRAINT chk_search_events__search_type CHECK (search_type IN
        ('TEXT', 'PART_NUMBER', 'VEHICLE', 'VIN', 'SEMANTIC', 'CATEGORY_BROWSE')),
    CONSTRAINT chk_search_events__query_hash CHECK (query_hash ~ '^[0-9a-f]{64}$'),
    -- Texto depurado en el servidor: no vacío, longitud acotada y sin PII
    -- evidente (emails, celulares colombianos ni VIN completo de 17 caracteres).
    CONSTRAINT chk_search_events__query_text CHECK (
        char_length(query_text) BETWEEN 1 AND 200
        AND query_text !~ '@'
        AND query_text !~ '(^|[^0-9])(\+?57)?3[0-9]{9}([^0-9]|$)'
        AND query_text !~* '(^|[^A-Z0-9])[A-HJ-NPR-Z0-9]{17}([^A-Z0-9]|$)'),
    CONSTRAINT chk_search_events__vehicle_year CHECK (vehicle_year BETWEEN 1950 AND 2100),
    CONSTRAINT chk_search_events__vin_wmi CHECK (vin_wmi ~ '^[A-HJ-NPR-Z0-9]{3}$'),
    CONSTRAINT chk_search_events__prices CHECK (price_min >= 0 AND price_max >= 0 AND (price_max >= price_min)),
    CONSTRAINT chk_search_events__filters CHECK (jsonb_typeof(filters) = 'object' AND pg_column_size(filters) <= 2048),
    CONSTRAINT chk_search_events__page CHECK (page >= 1),
    CONSTRAINT chk_search_events__results CHECK (results_count >= 0 AND compatible_results_count >= 0
                                                 AND compatible_results_count <= results_count),
    CONSTRAINT chk_search_events__latency CHECK (latency_ms >= 0)
) PARTITION BY RANGE (occurred_at);
ALTER SEQUENCE tracking.search_events_id_seq OWNED BY tracking.search_events.id;
CREATE TABLE IF NOT EXISTS tracking.search_events_default PARTITION OF tracking.search_events DEFAULT;
CREATE INDEX IF NOT EXISTS idx_search_events__session ON tracking.search_events (session_id);
CREATE INDEX IF NOT EXISTS idx_search_events__zero_results ON tracking.search_events (occurred_at, query_hash) WHERE results_count = 0;
CREATE INDEX IF NOT EXISTS idx_search_events__vehicle ON tracking.search_events (model_id, version_id, vehicle_year) WHERE model_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Eventos de producto
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS tracking.product_events_id_seq AS BIGINT;
CREATE TABLE IF NOT EXISTS tracking.product_events (
    id                         BIGINT        NOT NULL DEFAULT nextval('tracking.product_events_id_seq'),
    event_id                   UUID          NOT NULL,
    occurred_at                TIMESTAMPTZ   NOT NULL,
    received_at                TIMESTAMPTZ   NOT NULL DEFAULT now(),
    session_id                 UUID          NOT NULL,
    anonymous_id               UUID          NOT NULL,
    subject_key                UUID,
    event_type                 VARCHAR(30)   NOT NULL,
    product_id                 UUID          NOT NULL,
    inventory_id               UUID,
    store_id                   UUID,
    branch_id                  UUID,
    quantity                   INTEGER,
    displayed_price            NUMERIC(12,2),
    displayed_list_price       NUMERIC(12,2),
    promotion_id               UUID,
    availability_shown         VARCHAR(20),
    compatibility_status_shown VARCHAR(20),
    search_event_id            UUID,
    result_position            SMALLINT,
    cart_id                    UUID,
    CONSTRAINT pk_product_events PRIMARY KEY (occurred_at, id),
    CONSTRAINT uq_product_events__event UNIQUE (event_id, occurred_at),
    CONSTRAINT chk_product_events__event_type CHECK (event_type IN ('PRODUCT_VIEW', 'SEARCH_RESULT_CLICK',
        'ADD_TO_CART', 'REMOVE_FROM_CART', 'CART_QUANTITY_CHANGED', 'CONTACT_STORE',
        'COMPATIBILITY_CHECK', 'FAVORITE_ADDED', 'FAVORITE_REMOVED', 'SHARE')),
    CONSTRAINT chk_product_events__prices CHECK (displayed_price >= 0 AND displayed_list_price >= 0),
    CONSTRAINT chk_product_events__availability CHECK (availability_shown IN ('IN_STOCK', 'LOW_STOCK', 'OUT_OF_STOCK', 'UNKNOWN')),
    CONSTRAINT chk_product_events__compatibility CHECK (compatibility_status_shown IN
        ('CONFIRMED', 'SELLER_DECLARED', 'SUGGESTED', 'UNKNOWN')),
    CONSTRAINT chk_product_events__result_position CHECK (result_position >= 1)
) PARTITION BY RANGE (occurred_at);
ALTER SEQUENCE tracking.product_events_id_seq OWNED BY tracking.product_events.id;
CREATE TABLE IF NOT EXISTS tracking.product_events_default PARTITION OF tracking.product_events DEFAULT;
CREATE INDEX IF NOT EXISTS idx_product_events__product_type ON tracking.product_events (product_id, event_type, occurred_at);
CREATE INDEX IF NOT EXISTS idx_product_events__session ON tracking.product_events (session_id);

-- -----------------------------------------------------------------------------
-- Eventos genéricos (cola larga)
-- -----------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS tracking.events_id_seq AS BIGINT;
CREATE TABLE IF NOT EXISTS tracking.events (
    id             BIGINT       NOT NULL DEFAULT nextval('tracking.events_id_seq'),
    event_id       UUID         NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    received_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    session_id     UUID         NOT NULL,
    anonymous_id   UUID         NOT NULL,
    subject_key    UUID,
    event_name     VARCHAR(60)  NOT NULL,
    surface        VARCHAR(20)  NOT NULL,
    store_id       UUID,
    branch_id      UUID,
    product_id     UUID,
    order_id       UUID,
    schema_version SMALLINT     NOT NULL DEFAULT 1,
    properties     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_events PRIMARY KEY (occurred_at, id),
    CONSTRAINT uq_events__event UNIQUE (event_id, occurred_at),
    CONSTRAINT fk_events__event_name FOREIGN KEY (event_name)
        REFERENCES tracking.event_definitions (event_name) ON DELETE RESTRICT,
    CONSTRAINT chk_events__surface CHECK (surface IN ('MARKETPLACE', 'STORESUITE', 'OPERATIONS')),
    CONSTRAINT chk_events__schema_version CHECK (schema_version >= 1),
    CONSTRAINT chk_events__properties CHECK (jsonb_typeof(properties) = 'object' AND pg_column_size(properties) <= 4096)
) PARTITION BY RANGE (occurred_at);
ALTER SEQUENCE tracking.events_id_seq OWNED BY tracking.events.id;
CREATE TABLE IF NOT EXISTS tracking.events_default PARTITION OF tracking.events DEFAULT;
CREATE INDEX IF NOT EXISTS idx_events__name_occurred ON tracking.events (event_name, occurred_at);

-- Solo inserción (las particiones se eliminan por retención, sin DELETE).
CREATE OR REPLACE TRIGGER trg_search_events_append_only
    BEFORE UPDATE OR DELETE ON tracking.search_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_product_events_append_only
    BEFORE UPDATE OR DELETE ON tracking.product_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();
CREATE OR REPLACE TRIGGER trg_events_append_only
    BEFORE UPDATE OR DELETE ON tracking.events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();

-- -----------------------------------------------------------------------------
-- Vínculos de identidad
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tracking.identity_links (
    anonymous_id    UUID        NOT NULL,
    subject_key     UUID        NOT NULL,
    first_linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_linked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_identity_links PRIMARY KEY (anonymous_id, subject_key),
    CONSTRAINT chk_identity_links__dates CHECK (last_linked_at >= first_linked_at)
);
CREATE INDEX IF NOT EXISTS idx_identity_links__subject_key ON tracking.identity_links (subject_key);
CREATE INDEX IF NOT EXISTS idx_identity_links__last_linked ON tracking.identity_links (last_linked_at);

-- Particiones iniciales: mes actual y 3 siguientes --------------------------------
SELECT public.ensure_monthly_partitions('tracking.sessions',       public.bogota_date(now()), 4);
SELECT public.ensure_monthly_partitions('tracking.search_events',  public.bogota_date(now()), 4);
SELECT public.ensure_monthly_partitions('tracking.product_events', public.bogota_date(now()), 4);
SELECT public.ensure_monthly_partitions('tracking.events',         public.bogota_date(now()), 4);
