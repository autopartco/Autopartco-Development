-- =============================================================================
-- V202610141300__ml_registry_and_features.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.8
--
-- Propósito: esquema ml. Ninguna predicción sin línea base ni trazabilidad.
--   - ml.models, ml.datasets (contains_direct_pii siempre false),
--     ml.model_versions (DEPLOYED exige línea base; los datos sintéticos
--     nunca son CHAMPION; un solo CHAMPION desplegado por modelo).
--   - ml.batch_runs (idempotencia de jobs), ml.feature_definitions (linaje).
--   - Features point-in-time: ml.product_branch_features y ml.customer_features
--     (particionadas por mes), ml.branch_features. Regla: as_of_date = D usa
--     solo datos con fecha local < D.
--   - ml.predictions (particionada por mes) y ml.prediction_outcomes.
--   - Vista ml.v_current_demand_forecast (filtrar por store_id en el servidor).
--   - FK pendiente de fase 1: inventory_reorder_policies.model_version_id.
--   Las features de cliente usan subject_key, nunca person_id.
--
-- PostgreSQL 16 no admite IDENTITY en tablas particionadas: ml.predictions.id
--   usa una secuencia propia.
-- Retención (A9): features 24 meses y predicciones 36 meses, quitando
--   particiones.
--
-- Transacción: SÍ. Rollback: U202610141300__ml_registry_and_features.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS ml.models (
    id            UUID         NOT NULL DEFAULT gen_random_uuid(),
    code          VARCHAR(80)  NOT NULL,
    task          VARCHAR(20)  NOT NULL,
    target_entity VARCHAR(20)  NOT NULL,
    description   VARCHAR(500) NOT NULL,
    owner_module  VARCHAR(60)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_models PRIMARY KEY (id),
    CONSTRAINT uq_models__code UNIQUE (code),
    CONSTRAINT chk_models__code CHECK (code ~ '^[a-z][a-z0-9_]*$'),
    CONSTRAINT chk_models__task CHECK (task IN ('FORECAST', 'CLASSIFICATION', 'REGRESSION', 'RANKING', 'ANOMALY')),
    CONSTRAINT chk_models__target_entity CHECK (target_entity IN
        ('PRODUCT_BRANCH', 'CUSTOMER', 'BRANCH', 'PAYMENT', 'SALE', 'VEHICLE'))
);
CREATE OR REPLACE TRIGGER trg_models_updated_at
    BEFORE UPDATE ON ml.models FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

CREATE TABLE IF NOT EXISTS ml.datasets (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    code                VARCHAR(80)  NOT NULL,
    version             VARCHAR(40)  NOT NULL,
    description         VARCHAR(500),
    definition_sql_sha  CHAR(64)     NOT NULL,
    as_of_cutoff        TIMESTAMPTZ  NOT NULL,
    window_from         DATE         NOT NULL,
    window_to           DATE         NOT NULL,
    row_count           BIGINT,
    storage_uri         VARCHAR(500),
    data_origin         VARCHAR(10)  NOT NULL,
    is_pseudonymized    BOOLEAN      NOT NULL DEFAULT true,
    contains_direct_pii BOOLEAN      NOT NULL DEFAULT false,
    created_by          VARCHAR(100) NOT NULL,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_datasets PRIMARY KEY (id),
    CONSTRAINT uq_datasets__code_version UNIQUE (code, version),
    CONSTRAINT chk_datasets__sha CHECK (definition_sql_sha ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_datasets__window CHECK (window_to >= window_from),
    CONSTRAINT chk_datasets__cutoff CHECK (as_of_cutoff >= window_to::timestamp AT TIME ZONE 'America/Bogota'),
    CONSTRAINT chk_datasets__row_count CHECK (row_count >= 0),
    CONSTRAINT chk_datasets__data_origin CHECK (data_origin IN ('REAL', 'SYNTHETIC', 'MIXED')),
    CONSTRAINT chk_datasets__no_direct_pii CHECK (contains_direct_pii = false),
    CONSTRAINT chk_datasets__created_by_not_email CHECK (created_by !~ '@')
);

CREATE TABLE IF NOT EXISTS ml.model_versions (
    id                  UUID         NOT NULL DEFAULT gen_random_uuid(),
    model_id            UUID         NOT NULL,
    version             VARCHAR(40)  NOT NULL,
    algorithm           VARCHAR(60)  NOT NULL,
    is_baseline         BOOLEAN      NOT NULL DEFAULT false,
    training_dataset_id UUID,
    feature_set_version SMALLINT,
    hyperparameters     JSONB        NOT NULL DEFAULT '{}'::jsonb,
    metrics             JSONB        NOT NULL DEFAULT '{}'::jsonb,
    baseline_metrics    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    known_limitations   TEXT,
    train_from          DATE,
    train_to            DATE,
    validation_from     DATE,
    validation_to       DATE,
    artifact_uri        VARCHAR(500),
    code_commit         CHAR(40),
    data_origin         VARCHAR(10)  NOT NULL,
    status              VARCHAR(15)  NOT NULL,
    role                VARCHAR(12),
    trained_at          TIMESTAMPTZ,
    deployed_at         TIMESTAMPTZ,
    retired_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_model_versions PRIMARY KEY (id),
    CONSTRAINT fk_model_versions__model_id FOREIGN KEY (model_id) REFERENCES ml.models (id) ON DELETE RESTRICT,
    CONSTRAINT fk_model_versions__training_dataset_id FOREIGN KEY (training_dataset_id)
        REFERENCES ml.datasets (id) ON DELETE RESTRICT,
    CONSTRAINT uq_model_versions__model_version UNIQUE (model_id, version),
    CONSTRAINT chk_model_versions__data_origin CHECK (data_origin IN ('REAL', 'SYNTHETIC', 'MIXED')),
    CONSTRAINT chk_model_versions__status CHECK (status IN ('TRAINED', 'VALIDATED', 'DEPLOYED', 'RETIRED', 'REJECTED')),
    CONSTRAINT chk_model_versions__role CHECK (role IN ('CHAMPION', 'CHALLENGER', 'SHADOW')),
    CONSTRAINT chk_model_versions__deploy_needs_baseline CHECK (
        status <> 'DEPLOYED' OR is_baseline OR baseline_metrics <> '{}'::jsonb),
    CONSTRAINT chk_model_versions__deployed_at CHECK (status <> 'DEPLOYED' OR deployed_at IS NOT NULL),
    CONSTRAINT chk_model_versions__synthetic_not_champion CHECK (NOT (data_origin <> 'REAL' AND role = 'CHAMPION')),
    CONSTRAINT chk_model_versions__train_window CHECK (train_to IS NULL OR train_from IS NULL OR train_to >= train_from),
    CONSTRAINT chk_model_versions__validation_after_train CHECK (
        validation_from IS NULL OR train_to IS NULL OR validation_from > train_to),
    CONSTRAINT chk_model_versions__code_commit CHECK (code_commit ~ '^[0-9a-f]{40}$')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_model_versions__one_champion
    ON ml.model_versions (model_id) WHERE role = 'CHAMPION' AND status = 'DEPLOYED';
CREATE INDEX IF NOT EXISTS idx_model_versions__training_dataset
    ON ml.model_versions (training_dataset_id) WHERE training_dataset_id IS NOT NULL;
CREATE OR REPLACE TRIGGER trg_model_versions_updated_at
    BEFORE UPDATE ON ml.model_versions FOR EACH ROW EXECUTE FUNCTION public.set_updated_at();

-- FK de fase 1 (inventory_reorder_policies.model_version_id)
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_inventory_reorder_policies__model_version_id'
                   AND conrelid = 'public.inventory_reorder_policies'::regclass) THEN
        ALTER TABLE public.inventory_reorder_policies
            ADD CONSTRAINT fk_inventory_reorder_policies__model_version_id
            FOREIGN KEY (model_version_id) REFERENCES ml.model_versions (id) ON DELETE RESTRICT NOT VALID;
        ALTER TABLE public.inventory_reorder_policies
            VALIDATE CONSTRAINT fk_inventory_reorder_policies__model_version_id;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS ml.batch_runs (
    id               UUID         NOT NULL DEFAULT gen_random_uuid(),
    run_type         VARCHAR(20)  NOT NULL,
    model_version_id UUID,
    idempotency_key  VARCHAR(120) NOT NULL,
    parameters       JSONB        NOT NULL DEFAULT '{}'::jsonb,
    status           VARCHAR(15)  NOT NULL,
    rows_written     BIGINT,
    error_message    VARCHAR(500),
    started_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finished_at      TIMESTAMPTZ,
    CONSTRAINT pk_batch_runs PRIMARY KEY (id),
    CONSTRAINT uq_batch_runs__idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_batch_runs__model_version_id FOREIGN KEY (model_version_id)
        REFERENCES ml.model_versions (id) ON DELETE RESTRICT,
    CONSTRAINT chk_batch_runs__run_type CHECK (run_type IN
        ('FEATURE_BUILD', 'TRAINING', 'BACKTEST', 'SCORING', 'OUTCOME_MATCH')),
    CONSTRAINT chk_batch_runs__status CHECK (status IN ('RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_batch_runs__finished CHECK ((status = 'RUNNING') = (finished_at IS NULL)),
    CONSTRAINT chk_batch_runs__rows CHECK (rows_written >= 0)
);
CREATE INDEX IF NOT EXISTS idx_batch_runs__model_version
    ON ml.batch_runs (model_version_id) WHERE model_version_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_batch_runs__type_started ON ml.batch_runs (run_type, started_at DESC);

CREATE TABLE IF NOT EXISTS ml.feature_definitions (
    feature_set   VARCHAR(40)  NOT NULL,
    version       SMALLINT     NOT NULL,
    feature_name  VARCHAR(80)  NOT NULL,
    data_type     VARCHAR(20)  NOT NULL,
    description   VARCHAR(500) NOT NULL,
    lineage       TEXT         NOT NULL,
    introduced_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_feature_definitions PRIMARY KEY (feature_set, version, feature_name),
    CONSTRAINT chk_feature_definitions__version CHECK (version >= 1)
);

-- Features point-in-time --------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ml.product_branch_features (
    as_of_date                DATE          NOT NULL,
    branch_id                 UUID          NOT NULL,
    product_id                UUID          NOT NULL,
    store_id                  UUID          NOT NULL,
    category_id               UUID,
    feature_set_version       SMALLINT      NOT NULL,
    units_7d                  INTEGER,
    units_28d                 INTEGER,
    units_91d                 INTEGER,
    units_365d                INTEGER,
    units_same_28d_last_year  INTEGER,
    days_with_sales_28d       SMALLINT,
    revenue_28d               NUMERIC(16,2),
    avg_price_28d             NUMERIC(12,2),
    price_change_pct_28d      NUMERIC(9,4),
    promo_days_28d            SMALLINT,
    stockout_days_28d         SMALLINT,
    on_hand                   INTEGER,
    available                 INTEGER,
    in_transit                INTEGER,
    days_of_supply            NUMERIC(10,2),
    views_28d                 INTEGER,
    add_to_cart_28d           INTEGER,
    unmet_searches_28d        INTEGER,
    compatible_versions_count INTEGER,
    lead_time_days_avg        NUMERIC(8,2),
    computed_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_product_branch_features PRIMARY KEY (as_of_date, branch_id, product_id),
    CONSTRAINT chk_product_branch_features__days CHECK (days_with_sales_28d BETWEEN 0 AND 28
        AND promo_days_28d BETWEEN 0 AND 28 AND stockout_days_28d BETWEEN 0 AND 28)
) PARTITION BY RANGE (as_of_date);
CREATE TABLE IF NOT EXISTS ml.product_branch_features_default PARTITION OF ml.product_branch_features DEFAULT;
CREATE INDEX IF NOT EXISTS idx_product_branch_features__store_date ON ml.product_branch_features (store_id, as_of_date);
CREATE INDEX IF NOT EXISTS idx_product_branch_features__product_date ON ml.product_branch_features (product_id, as_of_date);

CREATE TABLE IF NOT EXISTS ml.customer_features (
    as_of_date                DATE          NOT NULL,
    subject_key               UUID          NOT NULL,
    feature_set_version       SMALLINT      NOT NULL,
    tenure_days               INTEGER,
    recency_days              INTEGER,
    frequency_90d             INTEGER,
    frequency_365d            INTEGER,
    monetary_365d             NUMERIC(16,2),
    avg_order_value_365d      NUMERIC(12,2),
    distinct_categories_365d  SMALLINT,
    returns_rate_365d         NUMERIC(6,5),
    cancellations_365d        SMALLINT,
    vehicles_count            SMALLINT,
    primary_vehicle_age_years SMALLINT,
    last_odometer_km          INTEGER,
    est_monthly_km            INTEGER,
    home_department_code      CHAR(2),
    rfm_segment               VARCHAR(30),
    computed_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_customer_features PRIMARY KEY (as_of_date, subject_key),
    CONSTRAINT chk_customer_features__returns_rate CHECK (returns_rate_365d BETWEEN 0 AND 1)
) PARTITION BY RANGE (as_of_date);
CREATE TABLE IF NOT EXISTS ml.customer_features_default PARTITION OF ml.customer_features DEFAULT;
CREATE INDEX IF NOT EXISTS idx_customer_features__subject ON ml.customer_features (subject_key, as_of_date);

CREATE TABLE IF NOT EXISTS ml.branch_features (
    as_of_date             DATE         NOT NULL,
    branch_id              UUID         NOT NULL,
    store_id               UUID         NOT NULL,
    feature_set_version    SMALLINT     NOT NULL,
    active_skus            INTEGER,
    orders_28d             INTEGER,
    fill_rate_28d          NUMERIC(6,5),
    avg_hours_to_ready_28d NUMERIC(10,2),
    on_time_rate_28d       NUMERIC(6,5),
    cancellation_rate_28d  NUMERIC(6,5),
    store_rating_avg_90d   NUMERIC(3,2),
    return_rate_90d        NUMERIC(6,5),
    municipality_code      CHAR(5),
    computed_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_branch_features PRIMARY KEY (as_of_date, branch_id),
    CONSTRAINT chk_branch_features__rates CHECK (fill_rate_28d BETWEEN 0 AND 1 AND on_time_rate_28d BETWEEN 0 AND 1
        AND cancellation_rate_28d BETWEEN 0 AND 1 AND return_rate_90d BETWEEN 0 AND 1),
    CONSTRAINT chk_branch_features__rating CHECK (store_rating_avg_90d BETWEEN 1 AND 5)
);
CREATE INDEX IF NOT EXISTS idx_branch_features__store_date ON ml.branch_features (store_id, as_of_date);

-- Predicciones ---------------------------------------------------------------------------
CREATE SEQUENCE IF NOT EXISTS ml.predictions_id_seq AS BIGINT;
CREATE TABLE IF NOT EXISTS ml.predictions (
    id                  BIGINT        NOT NULL DEFAULT nextval('ml.predictions_id_seq'),
    predicted_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    model_version_id    UUID          NOT NULL,
    batch_run_id        UUID,
    entity_type         VARCHAR(20)   NOT NULL,
    store_id            UUID,
    branch_id           UUID,
    product_id          UUID,
    subject_key         UUID,
    payment_id          UUID,
    sale_id             UUID,
    customer_vehicle_id UUID,
    features_as_of      DATE,
    target_date         DATE,
    horizon_days        SMALLINT,
    value               NUMERIC(18,6),
    lower_bound         NUMERIC(18,6),
    upper_bound         NUMERIC(18,6),
    predicted_class     VARCHAR(50),
    probability         NUMERIC(6,5),
    explanation         JSONB         NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT pk_predictions PRIMARY KEY (predicted_at, id),
    CONSTRAINT fk_predictions__model_version_id FOREIGN KEY (model_version_id)
        REFERENCES ml.model_versions (id) ON DELETE RESTRICT,
    CONSTRAINT fk_predictions__batch_run_id FOREIGN KEY (batch_run_id)
        REFERENCES ml.batch_runs (id) ON DELETE RESTRICT,
    CONSTRAINT chk_predictions__entity_type CHECK (entity_type IN
        ('PRODUCT_BRANCH', 'CUSTOMER', 'BRANCH', 'PAYMENT', 'SALE', 'VEHICLE')),
    CONSTRAINT chk_predictions__horizon CHECK (horizon_days >= 0),
    CONSTRAINT chk_predictions__bounds CHECK (lower_bound IS NULL OR upper_bound IS NULL OR lower_bound <= upper_bound),
    CONSTRAINT chk_predictions__probability CHECK (probability BETWEEN 0 AND 1),
    CONSTRAINT chk_predictions__explanation CHECK (jsonb_typeof(explanation) = 'object' AND pg_column_size(explanation) <= 4096),
    CONSTRAINT chk_predictions__point_in_time CHECK (features_as_of IS NULL OR target_date IS NULL OR target_date >= features_as_of)
) PARTITION BY RANGE (predicted_at);
ALTER SEQUENCE ml.predictions_id_seq OWNED BY ml.predictions.id;
CREATE TABLE IF NOT EXISTS ml.predictions_default PARTITION OF ml.predictions DEFAULT;
-- Sin BRIN: la PK (predicted_at, id) ya cubre los rangos por tiempo.
CREATE INDEX IF NOT EXISTS idx_predictions__model_target ON ml.predictions (model_version_id, target_date);
CREATE INDEX IF NOT EXISTS idx_predictions__branch_product
    ON ml.predictions (branch_id, product_id, target_date) WHERE product_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_predictions__batch_run ON ml.predictions (batch_run_id) WHERE batch_run_id IS NOT NULL;

CREATE OR REPLACE TRIGGER trg_predictions_append_only
    BEFORE UPDATE OR DELETE ON ml.predictions
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();

CREATE TABLE IF NOT EXISTS ml.prediction_outcomes (
    prediction_id BIGINT        NOT NULL,
    predicted_at  TIMESTAMPTZ   NOT NULL,
    actual_value  NUMERIC(18,6),
    actual_class  VARCHAR(50),
    observed_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    abs_error     NUMERIC(18,6),
    censored      BOOLEAN       NOT NULL DEFAULT false,
    CONSTRAINT pk_prediction_outcomes PRIMARY KEY (prediction_id, predicted_at),
    CONSTRAINT fk_prediction_outcomes__predicted_at_prediction_id FOREIGN KEY (predicted_at, prediction_id)
        REFERENCES ml.predictions (predicted_at, id) ON DELETE CASCADE,
    CONSTRAINT chk_prediction_outcomes__abs_error CHECK (abs_error >= 0)
);
CREATE INDEX IF NOT EXISTS idx_prediction_outcomes__predicted_at ON ml.prediction_outcomes (predicted_at, prediction_id);

CREATE OR REPLACE VIEW ml.v_current_demand_forecast AS
SELECT p.*
FROM ml.predictions p
JOIN ml.model_versions mv ON mv.id = p.model_version_id AND mv.role = 'CHAMPION' AND mv.status = 'DEPLOYED'
JOIN ml.models m ON m.id = mv.model_id AND m.code = 'demand_forecast_daily'
WHERE p.predicted_at >= now() - interval '2 days';

SELECT public.ensure_monthly_partitions('ml.product_branch_features', public.bogota_date(now()), 4);
SELECT public.ensure_monthly_partitions('ml.customer_features',       public.bogota_date(now()), 4);
SELECT public.ensure_monthly_partitions('ml.predictions',             public.bogota_date(now()), 4);
