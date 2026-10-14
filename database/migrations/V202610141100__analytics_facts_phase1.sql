-- =============================================================================
-- V202610141100__analytics_facts_phase1.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.11
--
-- Propósito: capa analítica mínima. Son tablas pobladas por funciones
--   idempotentes que recalculan una ventana (DELETE del rango + INSERT, o
--   upsert) dentro de una transacción. No contienen PII.
--   - analytics.fact_sales_daily        (fecha local, sucursal, producto)
--   - analytics.fact_order_fulfillment  (grano: venta)
--   - analytics.fact_unmet_demand_daily (búsquedas sin resultado, quiebres vistos,
--                                        vehículos sin repuestos compatibles)
--   Funciones (RETURNS integer = filas escritas). Filtran store.data_origin
--   y orders.data_origin = 'REAL':
--     analytics.refresh_fact_sales_daily(p_from date, p_to date)
--     analytics.refresh_fact_order_fulfillment(p_since timestamptz)
--     analytics.refresh_fact_unmet_demand_daily(p_from date, p_to date)
--   Cadencia (AnalyticsRefreshJob): cada noche, sales_daily sobre los últimos
--   35 días, fulfillment desde hace 48 h y unmet_demand sobre los últimos 3
--   días. El día 1 de cada mes, sales_daily sobre los últimos 13 meses.
--   Las columnas TIMESTAMP heredadas se leen con public.legacy_ts() y
--   public.bogota_date(), según autopartco.legacy_source_tz (fíjela con
--   ALTER DATABASE para que la hereden los jobs). Los filtros por fecha
--   comparan la columna desnuda con un límite de su mismo tipo
--   (public.legacy_bound), así que son sargables. Si se aplica A3
--   (timestamptz), las sobrecargas mantienen el resultado.
--   Definiciones de fulfillment (horas):
--     hours_to_confirm = confirmed - order_created
--     hours_to_ready   = ready - confirmed
--     hours_to_ship    = shipped - coalesce(ready, confirmed)
--     hours_in_transit = delivered - shipped
--     hours_total      = delivered - order_created
--
-- Transacción: SÍ. Rollback: U202610141100__analytics_facts_phase1.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE TABLE IF NOT EXISTS analytics.fact_sales_daily (
    sale_date        DATE          NOT NULL,
    store_id         UUID          NOT NULL,
    branch_id        UUID          NOT NULL,
    product_id       UUID          NOT NULL,
    category_id      UUID,
    orders_count     INTEGER       NOT NULL,
    units_ordered    INTEGER       NOT NULL,
    units_cancelled  INTEGER       NOT NULL,
    units_delivered  INTEGER       NOT NULL,
    units_returned   INTEGER       NOT NULL DEFAULT 0,
    gross_revenue    NUMERIC(16,2) NOT NULL,
    discount_amount  NUMERIC(16,2) NOT NULL,
    net_revenue      NUMERIC(16,2) NOT NULL,
    cogs             NUMERIC(16,4),
    avg_unit_price   NUMERIC(12,2),
    min_unit_price   NUMERIC(12,2),
    max_unit_price   NUMERIC(12,2),
    list_price_eod   NUMERIC(12,2),
    promo_active     BOOLEAN,
    available_eod    INTEGER,
    stockout_minutes INTEGER,
    refreshed_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_fact_sales_daily PRIMARY KEY (sale_date, branch_id, product_id)
);
CREATE INDEX IF NOT EXISTS idx_fact_sales_daily__store_date ON analytics.fact_sales_daily (store_id, sale_date);
CREATE INDEX IF NOT EXISTS idx_fact_sales_daily__product_date ON analytics.fact_sales_daily (product_id, sale_date);

CREATE TABLE IF NOT EXISTS analytics.fact_order_fulfillment (
    sale_id                       UUID          NOT NULL,
    order_id                      UUID          NOT NULL,
    store_id                      UUID          NOT NULL,
    branch_id                     UUID          NOT NULL,
    order_date                    DATE          NOT NULL,
    order_created_at              TIMESTAMPTZ   NOT NULL,
    confirmed_at                  TIMESTAMPTZ,
    ready_at                      TIMESTAMPTZ,
    shipped_at                    TIMESTAMPTZ,
    delivered_at                  TIMESTAMPTZ,
    cancelled_at                  TIMESTAMPTZ,
    final_status                  VARCHAR(50)   NOT NULL,
    cancellation_reason_code      VARCHAR(40),
    fulfillment_type              VARCHAR(20),
    carrier                       VARCHAR(100),
    origin_municipality_code      CHAR(5),
    destination_municipality_code CHAR(5),
    distance_km                   NUMERIC(8,2),
    hours_to_confirm              NUMERIC(10,2),
    hours_to_ready                NUMERIC(10,2),
    hours_to_ship                 NUMERIC(10,2),
    hours_in_transit              NUMERIC(10,2),
    hours_total                   NUMERIC(10,2),
    business_days_total           SMALLINT,
    promised_delivery_by          TIMESTAMPTZ,
    estimated_delivery_at         TIMESTAMPTZ,
    delivered_on_time             BOOLEAN,
    delivery_delay_hours          NUMERIC(10,2),
    units                         INTEGER       NOT NULL,
    net_total                     NUMERIC(12,2) NOT NULL,
    refreshed_at                  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_fact_order_fulfillment PRIMARY KEY (sale_id)
);
CREATE INDEX IF NOT EXISTS idx_fact_order_fulfillment__store_date ON analytics.fact_order_fulfillment (store_id, order_date);

CREATE TABLE IF NOT EXISTS analytics.fact_unmet_demand_daily (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY,
    demand_date         DATE         NOT NULL,
    signal_type         VARCHAR(25)  NOT NULL,
    search_type         VARCHAR(20),
    query_hash          CHAR(64),
    query_text_sample   VARCHAR(200),
    make_id             UUID,
    model_id            UUID,
    version_id          UUID,
    vehicle_year        SMALLINT,
    category_id         UUID,
    product_id          UUID,
    geo_department_code CHAR(2),
    events_count        INTEGER      NOT NULL,
    distinct_sessions   INTEGER      NOT NULL,
    refreshed_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_fact_unmet_demand_daily PRIMARY KEY (id),
    CONSTRAINT chk_fact_unmet_demand_daily__signal_type CHECK (signal_type IN
        ('ZERO_RESULTS', 'OUT_OF_STOCK_VIEW', 'NO_COMPATIBLE_RESULTS')),
    CONSTRAINT chk_fact_unmet_demand_daily__sample_k_anonymity CHECK (query_text_sample IS NULL OR distinct_sessions >= 3),
    CONSTRAINT uq_fact_unmet_demand_daily__grain UNIQUE NULLS NOT DISTINCT (demand_date, signal_type, search_type,
        query_hash, make_id, model_id, version_id, vehicle_year, category_id, product_id, geo_department_code)
);
CREATE INDEX IF NOT EXISTS idx_fact_unmet_demand_daily__date ON analytics.fact_unmet_demand_daily (demand_date);

-- -----------------------------------------------------------------------------
-- refresh_fact_sales_daily
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION analytics.refresh_fact_sales_daily(p_from DATE, p_to DATE)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = analytics, public, pg_temp
AS $$
DECLARE
    written  INTEGER;
    s_orders public.orders.created_at%TYPE;   -- solo fija el tipo del límite
    v_from   public.orders.created_at%TYPE;
    v_to     public.orders.created_at%TYPE;
BEGIN
    -- Límites del mismo tipo que orders.created_at: filtro sargable sobre la columna desnuda.
    v_from := public.legacy_bound(p_from::timestamp AT TIME ZONE 'America/Bogota', s_orders);
    v_to   := public.legacy_bound((p_to + 1)::timestamp AT TIME ZONE 'America/Bogota', s_orders);

    DELETE FROM analytics.fact_sales_daily WHERE sale_date BETWEEN p_from AND p_to;

    INSERT INTO analytics.fact_sales_daily (
        sale_date, store_id, branch_id, product_id, category_id, orders_count, units_ordered,
        units_cancelled, units_delivered, units_returned, gross_revenue, discount_amount, net_revenue,
        cogs, avg_unit_price, min_unit_price, max_unit_price, list_price_eod, promo_active,
        available_eod, stockout_minutes
    )
    WITH lines AS (
        SELECT public.bogota_date(o.created_at)            AS sale_date,
               b.store_id,
               s.branch_id,
               coalesce(od.product_id, i.product_id)       AS product_id,
               coalesce(od.category_id_snapshot, p.category_id) AS category_id,
               o.id                                        AS order_id,
               od.units,
               od.unit_price,
               od.list_unit_price,
               od.discount_amount,
               od.unit_cost_snapshot,
               st.code                                     AS sale_status
        FROM public.order_details od
        JOIN public.sales s      ON s.id = od.sale_id AND s.deleted_at IS NULL
        JOIN public.orders o     ON o.id = od.order_id AND o.deleted_at IS NULL
        JOIN public.branches b   ON b.id = s.branch_id
        JOIN public.store so     ON so.id = b.store_id
        JOIN public.inventory i  ON i.id = od.inventory_id
        JOIN public.products p   ON p.id = coalesce(od.product_id, i.product_id)
        JOIN public.states st    ON st.id = s.state_id
        WHERE o.data_origin = 'REAL'
          AND so.data_origin = 'REAL'
          AND o.created_at >= v_from
          AND o.created_at <  v_to
    ),
    agg AS (
        SELECT sale_date, store_id, branch_id, product_id,
               (array_agg(category_id))[1]                                               AS category_id,
               count(DISTINCT order_id)                                                  AS orders_count,
               sum(units)                                                                AS units_ordered,
               coalesce(sum(units) FILTER (WHERE sale_status = 'CANCELLED'), 0)          AS units_cancelled,
               coalesce(sum(units) FILTER (WHERE sale_status = 'DELIVERED'), 0)          AS units_delivered,
               coalesce(sum(coalesce(list_unit_price, unit_price) * units)
                        FILTER (WHERE sale_status <> 'CANCELLED'), 0)                    AS gross_revenue,
               coalesce(sum(discount_amount) FILTER (WHERE sale_status <> 'CANCELLED'), 0) AS discount_amount,
               coalesce(sum(unit_price * units) FILTER (WHERE sale_status <> 'CANCELLED'), 0) AS net_revenue,
               CASE WHEN bool_and(unit_cost_snapshot IS NOT NULL) FILTER (WHERE sale_status <> 'CANCELLED')
                    THEN sum(unit_cost_snapshot * units) FILTER (WHERE sale_status <> 'CANCELLED') END AS cogs,
               round(avg(unit_price), 2)                                                 AS avg_unit_price,
               min(unit_price)                                                           AS min_unit_price,
               max(unit_price)                                                           AS max_unit_price
        FROM lines
        GROUP BY sale_date, store_id, branch_id, product_id
    )
    SELECT a.sale_date, a.store_id, a.branch_id, a.product_id, a.category_id, a.orders_count,
           a.units_ordered, a.units_cancelled, a.units_delivered, 0, a.gross_revenue, a.discount_amount,
           a.net_revenue, a.cogs, a.avg_unit_price, a.min_unit_price, a.max_unit_price,
           snap.list_price, snap.effective_price < snap.list_price, snap.available, snap.stockout_minutes
    FROM agg a
    LEFT JOIN LATERAL (
        SELECT ids.list_price, ids.effective_price, ids.available, ids.stockout_minutes
        FROM public.inventory_daily_snapshots ids
        WHERE ids.snapshot_date = a.sale_date AND ids.branch_id = a.branch_id AND ids.product_id = a.product_id
        LIMIT 1
    ) snap ON true;

    GET DIAGNOSTICS written = ROW_COUNT;
    RETURN written;
END;
$$;

-- -----------------------------------------------------------------------------
-- refresh_fact_order_fulfillment
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION analytics.refresh_fact_order_fulfillment(p_since TIMESTAMPTZ)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = analytics, public, pg_temp
AS $$
DECLARE
    written       INTEGER;
    s_sales       public.sales.updated_at%TYPE;      -- solo fijan el tipo del límite
    s_shipments   public.shipments.updated_at%TYPE;
    v_sales_since public.sales.updated_at%TYPE;
    v_ship_since  public.shipments.updated_at%TYPE;
BEGIN
    v_sales_since := public.legacy_bound(p_since, s_sales);
    v_ship_since  := public.legacy_bound(p_since, s_shipments);

    WITH target AS (
        SELECT s.id
        FROM public.sales s
        WHERE s.updated_at >= v_sales_since
        UNION
        SELECT h.sale_id FROM public.sale_status_history h WHERE h.changed_at >= p_since
        UNION
        SELECT sh.sale_id FROM public.shipments sh WHERE sh.updated_at >= v_ship_since
    ),
    base AS (
        SELECT s.id AS sale_id, s.order_id, b.store_id, s.branch_id,
               public.bogota_date(o.created_at)                      AS order_date,
               public.legacy_ts(o.created_at)                        AS order_created_at,
               coalesce(s.confirmed_at, hist.confirmed_at)           AS confirmed_at,
               s.ready_at,
               public.legacy_ts(sh.shipped_at)                       AS shipped_at,
               public.legacy_ts(sh.delivered_at)                     AS delivered_at,
               coalesce(s.cancelled_at, hist.cancelled_at)           AS cancelled_at,
               st.code                                               AS final_status,
               s.cancellation_reason_code,
               s.fulfillment_type,
               sh.carrier,
               sh.origin_municipality_code,
               sh.destination_municipality_code,
               sh.distance_km,
               s.promised_delivery_by,
               sh.estimated_delivery_at,
               coalesce((SELECT sum(od.units) FROM public.order_details od WHERE od.sale_id = s.id), 0) AS units,
               s.total AS net_total
        FROM target t
        JOIN public.sales s     ON s.id = t.id AND s.deleted_at IS NULL
        JOIN public.orders o    ON o.id = s.order_id
        JOIN public.branches b  ON b.id = s.branch_id
        JOIN public.store so    ON so.id = b.store_id
        JOIN public.states st   ON st.id = s.state_id
        LEFT JOIN public.shipments sh ON sh.sale_id = s.id
        LEFT JOIN LATERAL (
            SELECT min(h.changed_at) FILTER (WHERE h.to_status = 'CONFIRMED') AS confirmed_at,
                   min(h.changed_at) FILTER (WHERE h.to_status = 'CANCELLED') AS cancelled_at
            FROM public.sale_status_history h WHERE h.sale_id = s.id
        ) hist ON true
        WHERE o.data_origin = 'REAL' AND so.data_origin = 'REAL'
    )
    INSERT INTO analytics.fact_order_fulfillment AS f (
        sale_id, order_id, store_id, branch_id, order_date, order_created_at, confirmed_at, ready_at,
        shipped_at, delivered_at, cancelled_at, final_status, cancellation_reason_code, fulfillment_type,
        carrier, origin_municipality_code, destination_municipality_code, distance_km, hours_to_confirm,
        hours_to_ready, hours_to_ship, hours_in_transit, hours_total, business_days_total,
        promised_delivery_by, estimated_delivery_at, delivered_on_time, delivery_delay_hours, units,
        net_total, refreshed_at
    )
    SELECT b.sale_id, b.order_id, b.store_id, b.branch_id, b.order_date, b.order_created_at,
           b.confirmed_at, b.ready_at, b.shipped_at, b.delivered_at, b.cancelled_at, b.final_status,
           b.cancellation_reason_code, b.fulfillment_type, b.carrier, b.origin_municipality_code,
           b.destination_municipality_code, b.distance_km,
           round((extract(epoch FROM b.confirmed_at - b.order_created_at) / 3600)::numeric, 2),
           round((extract(epoch FROM b.ready_at - b.confirmed_at) / 3600)::numeric, 2),
           round((extract(epoch FROM b.shipped_at - coalesce(b.ready_at, b.confirmed_at)) / 3600)::numeric, 2),
           round((extract(epoch FROM b.delivered_at - b.shipped_at) / 3600)::numeric, 2),
           round((extract(epoch FROM b.delivered_at - b.order_created_at) / 3600)::numeric, 2),
           CASE WHEN b.delivered_at IS NOT NULL THEN (
               SELECT count(*) FROM analytics.dim_date d
               WHERE d."date" > b.order_date AND d."date" <= public.bogota_date(b.delivered_at)
                 AND d.is_business_day_co)::smallint END,
           b.promised_delivery_by, b.estimated_delivery_at,
           CASE WHEN b.delivered_at IS NOT NULL AND coalesce(b.promised_delivery_by, b.estimated_delivery_at) IS NOT NULL
                THEN b.delivered_at <= coalesce(b.promised_delivery_by, b.estimated_delivery_at) END,
           CASE WHEN b.delivered_at IS NOT NULL AND coalesce(b.promised_delivery_by, b.estimated_delivery_at) IS NOT NULL
                THEN round((greatest(extract(epoch FROM b.delivered_at
                                     - coalesce(b.promised_delivery_by, b.estimated_delivery_at)), 0) / 3600)::numeric, 2) END,
           b.units, b.net_total, now()
    FROM base b
    ON CONFLICT (sale_id) DO UPDATE SET
        confirmed_at = EXCLUDED.confirmed_at, ready_at = EXCLUDED.ready_at,
        shipped_at = EXCLUDED.shipped_at, delivered_at = EXCLUDED.delivered_at,
        cancelled_at = EXCLUDED.cancelled_at, final_status = EXCLUDED.final_status,
        cancellation_reason_code = EXCLUDED.cancellation_reason_code,
        fulfillment_type = EXCLUDED.fulfillment_type, carrier = EXCLUDED.carrier,
        origin_municipality_code = EXCLUDED.origin_municipality_code,
        destination_municipality_code = EXCLUDED.destination_municipality_code,
        distance_km = EXCLUDED.distance_km, hours_to_confirm = EXCLUDED.hours_to_confirm,
        hours_to_ready = EXCLUDED.hours_to_ready, hours_to_ship = EXCLUDED.hours_to_ship,
        hours_in_transit = EXCLUDED.hours_in_transit, hours_total = EXCLUDED.hours_total,
        business_days_total = EXCLUDED.business_days_total,
        promised_delivery_by = EXCLUDED.promised_delivery_by,
        estimated_delivery_at = EXCLUDED.estimated_delivery_at,
        delivered_on_time = EXCLUDED.delivered_on_time,
        delivery_delay_hours = EXCLUDED.delivery_delay_hours,
        units = EXCLUDED.units, net_total = EXCLUDED.net_total, refreshed_at = now();

    GET DIAGNOSTICS written = ROW_COUNT;
    RETURN written;
END;
$$;

-- -----------------------------------------------------------------------------
-- refresh_fact_unmet_demand_daily
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION analytics.refresh_fact_unmet_demand_daily(p_from DATE, p_to DATE)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = analytics, public, pg_temp
AS $$
DECLARE
    written  INTEGER;
    ts_from  TIMESTAMPTZ := p_from::timestamp AT TIME ZONE 'America/Bogota';
    ts_to    TIMESTAMPTZ := (p_to + 1)::timestamp AT TIME ZONE 'America/Bogota';
BEGIN
    DELETE FROM analytics.fact_unmet_demand_daily WHERE demand_date BETWEEN p_from AND p_to;

    INSERT INTO analytics.fact_unmet_demand_daily (
        demand_date, signal_type, search_type, query_hash, query_text_sample, make_id, model_id,
        version_id, vehicle_year, category_id, product_id, geo_department_code, events_count, distinct_sessions
    )
    WITH searches AS (
        SELECT public.bogota_date(e.occurred_at) AS demand_date,
               CASE WHEN e.results_count = 0 THEN 'ZERO_RESULTS' ELSE 'NO_COMPATIBLE_RESULTS' END AS signal_type,
               e.search_type, e.query_hash, e.query_text, e.make_id, e.model_id, e.version_id,
               e.vehicle_year, e.category_id, left(e.geo_municipality_code, 2) AS geo_department_code,
               e.session_id
        FROM tracking.search_events e
        WHERE e.occurred_at >= ts_from AND e.occurred_at < ts_to
          AND (e.results_count = 0 OR (e.model_id IS NOT NULL AND e.compatible_results_count = 0))
          AND NOT EXISTS (SELECT 1 FROM tracking.sessions s WHERE s.session_id = e.session_id AND s.is_bot)
    ),
    views AS (
        SELECT public.bogota_date(e.occurred_at) AS demand_date, e.product_id, p.category_id, e.session_id
        FROM tracking.product_events e
        LEFT JOIN public.products p ON p.id = e.product_id
        WHERE e.occurred_at >= ts_from AND e.occurred_at < ts_to
          AND e.event_type = 'PRODUCT_VIEW' AND e.availability_shown = 'OUT_OF_STOCK'
          AND NOT EXISTS (SELECT 1 FROM tracking.sessions s WHERE s.session_id = e.session_id AND s.is_bot)
    )
    SELECT demand_date, signal_type, search_type, query_hash,
           CASE WHEN count(DISTINCT session_id) >= 3 THEN min(query_text) END,
           make_id, model_id, version_id, vehicle_year, category_id, NULL::uuid, geo_department_code,
           count(*), count(DISTINCT session_id)
    FROM searches
    GROUP BY demand_date, signal_type, search_type, query_hash, make_id, model_id, version_id,
             vehicle_year, category_id, geo_department_code
    UNION ALL
    SELECT demand_date, 'OUT_OF_STOCK_VIEW', NULL, NULL, NULL, NULL, NULL, NULL, NULL,
           category_id, product_id, NULL, count(*), count(DISTINCT session_id)
    FROM views
    GROUP BY demand_date, product_id, category_id;

    GET DIAGNOSTICS written = ROW_COUNT;
    RETURN written;
END;
$$;
