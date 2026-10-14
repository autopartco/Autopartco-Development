-- =============================================================================
-- V202610141320__analytics_phase2.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.9
--
-- Propósito
--   Tablas + funciones de refresco (idempotentes; DELETE del rango + INSERT):
--     analytics.fact_vehicle_demand_daily   refresh_fact_vehicle_demand_daily(from, to)
--     analytics.fact_search_funnel_daily    refresh_fact_search_funnel_daily(from, to)
--     analytics.fact_returns_daily          refresh_fact_returns_daily(from, to)
--   Vistas materializadas (cada una con índice único, para REFRESH ... CONCURRENTLY):
--     analytics.mv_inventory_rotation_monthly (nocturno)
--     analytics.mv_customer_cohorts           (semanal)
--     analytics.mv_customer_rfm               (semanal; estado actual por subject_key)
--   Vista: analytics.v_supplier_lead_times.
--   Ninguna expone PII: los clientes aparecen solo por subject_key
--   (privacy.subject_pseudonyms). Las personas sin seudónimo se excluyen.
--   units_sold_allocated reparte por igual las unidades sin vehículo
--   declarado entre las versiones compatibles del producto. Es una
--   ESTIMACIÓN y así se documenta.
--   Las vistas materializadas se crean con datos (hoy las fuentes están
--   vacías o son pequeñas).
--
-- Transacción: SÍ. Rollback: U202610141320__analytics_phase2.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

-- -----------------------------------------------------------------------------
-- Demanda por vehículo
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_vehicle_demand_daily (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY,
    demand_date          DATE          NOT NULL,
    make_id              UUID,
    model_id             UUID,
    version_id           UUID,
    vehicle_year         SMALLINT,
    category_id          UUID,
    department_code      CHAR(2),
    searches             INTEGER       NOT NULL DEFAULT 0,
    zero_result_searches INTEGER       NOT NULL DEFAULT 0,
    product_views        INTEGER       NOT NULL DEFAULT 0,
    add_to_carts         INTEGER       NOT NULL DEFAULT 0,
    units_sold_explicit  INTEGER       NOT NULL DEFAULT 0,
    units_sold_allocated NUMERIC(14,4) NOT NULL DEFAULT 0,
    refreshed_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_fact_vehicle_demand_daily PRIMARY KEY (id),
    CONSTRAINT uq_fact_vehicle_demand_daily__grain UNIQUE NULLS NOT DISTINCT
        (demand_date, make_id, model_id, version_id, vehicle_year, category_id, department_code)
);
CREATE INDEX IF NOT EXISTS idx_fact_vehicle_demand_daily__date ON analytics.fact_vehicle_demand_daily (demand_date);
CREATE INDEX IF NOT EXISTS idx_fact_vehicle_demand_daily__model ON analytics.fact_vehicle_demand_daily (model_id, version_id, demand_date);

CREATE OR REPLACE FUNCTION analytics.refresh_fact_vehicle_demand_daily(p_from DATE, p_to DATE)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = analytics, public, pg_temp
AS $$
DECLARE
    written  INTEGER;
    ts_from  TIMESTAMPTZ := p_from::timestamp AT TIME ZONE 'America/Bogota';
    ts_to    TIMESTAMPTZ := (p_to + 1)::timestamp AT TIME ZONE 'America/Bogota';
    s_orders public.orders.created_at%TYPE;   -- solo fija el tipo del límite
    o_from   public.orders.created_at%TYPE;
    o_to     public.orders.created_at%TYPE;
BEGIN
    o_from := public.legacy_bound(ts_from, s_orders);
    o_to   := public.legacy_bound(ts_to, s_orders);

    DELETE FROM analytics.fact_vehicle_demand_daily WHERE demand_date BETWEEN p_from AND p_to;

    INSERT INTO analytics.fact_vehicle_demand_daily (
        demand_date, make_id, model_id, version_id, vehicle_year, category_id, department_code,
        searches, zero_result_searches, product_views, add_to_carts, units_sold_explicit, units_sold_allocated
    )
    WITH search_rows AS (
        SELECT public.bogota_date(e.occurred_at) AS d, e.make_id, e.model_id, e.version_id, e.vehicle_year,
               e.category_id, left(e.geo_municipality_code, 2) AS dep,
               1 AS searches, (e.results_count = 0)::int AS zero, 0 AS views, 0 AS carts,
               0 AS sold_explicit, 0::numeric AS sold_allocated
        FROM tracking.search_events e
        WHERE e.occurred_at >= ts_from AND e.occurred_at < ts_to
          AND (e.make_id IS NOT NULL OR e.model_id IS NOT NULL)
    ),
    product_rows AS (
        SELECT public.bogota_date(pe.occurred_at), se.make_id, se.model_id, se.version_id, se.vehicle_year,
               p.category_id, left(se.geo_municipality_code, 2),
               0, 0, (pe.event_type = 'PRODUCT_VIEW')::int, (pe.event_type = 'ADD_TO_CART')::int, 0, 0::numeric
        FROM tracking.product_events pe
        JOIN tracking.search_events se ON se.event_id = pe.search_event_id
        LEFT JOIN public.products p ON p.id = pe.product_id
        WHERE pe.occurred_at >= ts_from AND pe.occurred_at < ts_to
          AND pe.event_type IN ('PRODUCT_VIEW', 'ADD_TO_CART')
          AND (se.make_id IS NOT NULL OR se.model_id IS NOT NULL)
    ),
    sold_lines AS (
        SELECT public.bogota_date(o.created_at) AS d, od.id AS order_detail_id, od.units,
               coalesce(od.product_id, i.product_id) AS product_id,
               coalesce(od.category_id_snapshot, p.category_id) AS category_id,
               od.customer_vehicle_id, left(oa.municipality_code, 2) AS dep
        FROM public.order_details od
        JOIN public.orders o    ON o.id = od.order_id AND o.data_origin = 'REAL'
        JOIN public.sales s     ON s.id = od.sale_id
        JOIN public.states st   ON st.id = s.state_id AND st.code <> 'CANCELLED'
        JOIN public.inventory i ON i.id = od.inventory_id
        JOIN public.products p  ON p.id = coalesce(od.product_id, i.product_id)
        LEFT JOIN public.order_addresses oa ON oa.order_id = o.id
        WHERE o.created_at >= o_from AND o.created_at < o_to
    ),
    explicit_rows AS (
        SELECT sl.d, cv.make_id, cv.model_id, cv.version_id, cv.model_year, sl.category_id, sl.dep,
               0, 0, 0, 0, sl.units, 0::numeric
        FROM sold_lines sl
        JOIN public.customer_vehicles cv ON cv.id = sl.customer_vehicle_id
    ),
    allocated_rows AS (
        SELECT sl.d, c.make_id, c.model_id, c.version_id, NULL::smallint, sl.category_id, sl.dep,
               0, 0, 0, 0, 0,
               round(sl.units::numeric / count(*) OVER (PARTITION BY sl.order_detail_id), 4)
        FROM sold_lines sl
        JOIN public.products_compatibilies pc ON pc.product_id = sl.product_id
        JOIN public.compatibilities c ON c.id = pc.compatibility_id AND c.deleted_at IS NULL
        WHERE sl.customer_vehicle_id IS NULL
    ),
    all_rows AS (
        SELECT * FROM search_rows
        UNION ALL SELECT * FROM product_rows
        UNION ALL SELECT * FROM explicit_rows
        UNION ALL SELECT * FROM allocated_rows
    )
    SELECT d, make_id, model_id, version_id, vehicle_year, category_id, dep,
           sum(searches), sum(zero), sum(views), sum(carts), sum(sold_explicit), sum(sold_allocated)
    FROM all_rows
    GROUP BY d, make_id, model_id, version_id, vehicle_year, category_id, dep;

    GET DIAGNOSTICS written = ROW_COUNT;
    RETURN written;
END;
$$;

-- -----------------------------------------------------------------------------
-- Embudo de búsqueda
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_search_funnel_daily (
    funnel_date  DATE        NOT NULL,
    surface      VARCHAR(20) NOT NULL,
    search_type  VARCHAR(20) NOT NULL,
    device_type  VARCHAR(10) NOT NULL,
    searches     INTEGER     NOT NULL,
    zero_results INTEGER     NOT NULL,
    clicks       INTEGER     NOT NULL,
    add_to_carts INTEGER     NOT NULL,
    checkouts    INTEGER     NOT NULL,
    orders       INTEGER     NOT NULL,
    refreshed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_fact_search_funnel_daily PRIMARY KEY (funnel_date, surface, search_type, device_type)
);

CREATE OR REPLACE FUNCTION analytics.refresh_fact_search_funnel_daily(p_from DATE, p_to DATE)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = analytics, public, pg_temp
AS $$
DECLARE
    written INTEGER;
    ts_from TIMESTAMPTZ := p_from::timestamp AT TIME ZONE 'America/Bogota';
    ts_to   TIMESTAMPTZ := (p_to + 1)::timestamp AT TIME ZONE 'America/Bogota';
BEGIN
    DELETE FROM analytics.fact_search_funnel_daily WHERE funnel_date BETWEEN p_from AND p_to;

    INSERT INTO analytics.fact_search_funnel_daily (
        funnel_date, surface, search_type, device_type, searches, zero_results, clicks,
        add_to_carts, checkouts, orders
    )
    WITH s AS (
        SELECT public.bogota_date(e.occurred_at) AS d, e.surface, e.search_type,
               coalesce(ss.device_type, 'UNKNOWN') AS device_type,
               e.event_id, e.session_id, e.results_count
        FROM tracking.search_events e
        LEFT JOIN LATERAL (
            SELECT x.device_type, x.is_bot FROM tracking.sessions x WHERE x.session_id = e.session_id LIMIT 1
        ) ss ON true
        WHERE e.occurred_at >= ts_from AND e.occurred_at < ts_to
          AND coalesce(ss.is_bot, false) = false
    ),
    follow AS (
        SELECT pe.search_event_id,
               count(*) FILTER (WHERE pe.event_type = 'SEARCH_RESULT_CLICK') AS clicks,
               count(*) FILTER (WHERE pe.event_type = 'ADD_TO_CART')         AS carts
        FROM tracking.product_events pe
        WHERE pe.occurred_at >= ts_from AND pe.occurred_at < ts_to + interval '1 day'
          AND pe.search_event_id IS NOT NULL
        GROUP BY pe.search_event_id
    ),
    session_outcomes AS (
        SELECT ev.session_id,
               bool_or(ev.event_name = 'checkout_started')   AS checkout,
               bool_or(ev.event_name = 'checkout_completed') AS completed
        FROM tracking.events ev
        WHERE ev.occurred_at >= ts_from AND ev.occurred_at < ts_to + interval '1 day'
          AND ev.event_name IN ('checkout_started', 'checkout_completed')
        GROUP BY ev.session_id
    )
    SELECT s.d, s.surface, s.search_type, s.device_type,
           count(*),
           count(*) FILTER (WHERE s.results_count = 0),
           coalesce(sum(f.clicks), 0),
           coalesce(sum(f.carts), 0),
           count(DISTINCT s.session_id) FILTER (WHERE so.checkout),
           count(DISTINCT s.session_id) FILTER (WHERE so.completed OR ord.has_order)
    FROM s
    LEFT JOIN follow f ON f.search_event_id = s.event_id
    LEFT JOIN session_outcomes so ON so.session_id = s.session_id
    LEFT JOIN LATERAL (
        SELECT true AS has_order FROM public.orders o WHERE o.session_id = s.session_id LIMIT 1
    ) ord ON true
    GROUP BY s.d, s.surface, s.search_type, s.device_type;

    GET DIAGNOSTICS written = ROW_COUNT;
    RETURN written;
END;
$$;

-- -----------------------------------------------------------------------------
-- Devoluciones
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS analytics.fact_returns_daily (
    id                         BIGINT GENERATED ALWAYS AS IDENTITY,
    return_date                DATE          NOT NULL,
    store_id                   UUID          NOT NULL,
    product_id                 UUID,
    reason_code                VARCHAR(40)   NOT NULL,
    fault_party                VARCHAR(15)   NOT NULL,
    compatibility_status_shown VARCHAR(20),
    request_type               VARCHAR(10)   NOT NULL,
    units                      INTEGER       NOT NULL,
    refund_amount              NUMERIC(16,2) NOT NULL,
    refreshed_at               TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pk_fact_returns_daily PRIMARY KEY (id),
    CONSTRAINT uq_fact_returns_daily__grain UNIQUE NULLS NOT DISTINCT
        (return_date, store_id, product_id, reason_code, fault_party, compatibility_status_shown, request_type)
);
CREATE INDEX IF NOT EXISTS idx_fact_returns_daily__store_date ON analytics.fact_returns_daily (store_id, return_date);

CREATE OR REPLACE FUNCTION analytics.refresh_fact_returns_daily(p_from DATE, p_to DATE)
RETURNS INTEGER
LANGUAGE plpgsql
SET search_path = analytics, public, pg_temp
AS $$
DECLARE
    written INTEGER;
    ts_from TIMESTAMPTZ := p_from::timestamp AT TIME ZONE 'America/Bogota';
    ts_to   TIMESTAMPTZ := (p_to + 1)::timestamp AT TIME ZONE 'America/Bogota';
BEGIN
    DELETE FROM analytics.fact_returns_daily WHERE return_date BETWEEN p_from AND p_to;

    INSERT INTO analytics.fact_returns_daily (
        return_date, store_id, product_id, reason_code, fault_party, compatibility_status_shown,
        request_type, units, refund_amount
    )
    WITH items AS (
        -- El reembolso es por solicitud; se reparte entre sus líneas en proporción a las unidades.
        SELECT public.bogota_date(rr.requested_at) AS return_date, rr.store_id,
               coalesce(od.product_id, i.product_id) AS product_id,
               rr.reason_code, rs.fault_party, od.compatibility_status_shown, rr.request_type,
               ri.units,
               coalesce(rr.refund_amount, 0) * ri.units
                   / nullif(sum(ri.units) OVER (PARTITION BY rr.id), 0) AS refund_share
        FROM public.return_requests rr
        JOIN public.return_reasons rs       ON rs.code = rr.reason_code
        JOIN public.return_request_items ri ON ri.return_request_id = rr.id
        JOIN public.order_details od        ON od.id = ri.order_detail_id
        JOIN public.inventory i             ON i.id = od.inventory_id
        JOIN public.store so                ON so.id = rr.store_id AND so.data_origin = 'REAL'
        WHERE rr.deleted_at IS NULL
          AND rr.status <> 'CANCELLED'
          AND rr.requested_at >= ts_from AND rr.requested_at < ts_to
    )
    SELECT return_date, store_id, product_id, reason_code, fault_party, compatibility_status_shown,
           request_type, sum(units), round(coalesce(sum(refund_share), 0), 2)
    FROM items
    GROUP BY 1, 2, 3, 4, 5, 6, 7;

    GET DIAGNOSTICS written = ROW_COUNT;
    RETURN written;
END;
$$;

-- -----------------------------------------------------------------------------
-- Rotación de inventario mensual
-- -----------------------------------------------------------------------------
-- Una sola pasada: la última venta acumulada se calcula con una función de
-- ventana (sin LATERAL por fila).
CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.mv_inventory_rotation_monthly AS
WITH daily AS (
    SELECT s.*,
           max(CASE WHEN s.units_sold > 0 THEN s.snapshot_date END)
               OVER (PARTITION BY s.inventory_id ORDER BY s.snapshot_date) AS last_sale_to_date
    FROM public.inventory_daily_snapshots s
),
monthly AS (
    SELECT date_trunc('month', d.snapshot_date)::date AS month,
           d.inventory_id, d.store_id, d.branch_id, d.product_id,
           sum(d.units_sold)                                                   AS units_sold,
           round(avg(d.on_hand), 2)                                            AS avg_on_hand,
           count(*) FILTER (WHERE coalesce(d.stockout_minutes, 0) > 0 OR d.available <= 0) AS stockout_days,
           count(*)                                                            AS days_observed,
           (array_agg(d.on_hand ORDER BY d.snapshot_date DESC))[1]             AS on_hand_eom,
           max(d.snapshot_date)                                                AS last_snapshot_date,
           max(d.last_sale_to_date)                                            AS last_sale_date
    FROM daily d
    GROUP BY 1, 2, 3, 4, 5
)
SELECT m.month, m.store_id, m.branch_id, m.product_id, m.inventory_id,
       m.units_sold, m.avg_on_hand,
       round(m.units_sold / nullif(m.avg_on_hand, 0), 4)                              AS turnover,
       round(m.avg_on_hand / nullif(m.units_sold::numeric / nullif(m.days_observed, 0), 0), 2) AS days_of_supply,
       m.stockout_days,
       m.last_snapshot_date - m.last_sale_date                                         AS days_since_last_sale,
       (m.on_hand_eom > 0 AND (m.last_sale_date IS NULL OR m.last_snapshot_date - m.last_sale_date > 180)) AS is_dead_stock
FROM monthly m;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_inventory_rotation_monthly__grain
    ON analytics.mv_inventory_rotation_monthly (month, branch_id, product_id);
CREATE INDEX IF NOT EXISTS idx_mv_inventory_rotation_monthly__store
    ON analytics.mv_inventory_rotation_monthly (store_id, month);

-- -----------------------------------------------------------------------------
-- Cohortes y RFM (por subject_key; sin PII)
-- -----------------------------------------------------------------------------
CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.mv_customer_cohorts AS
WITH order_values AS (
    SELECT sp.subject_key,
           date_trunc('month', public.bogota_date(o.created_at))::date AS order_month,
           o.id AS order_id,
           coalesce(sum(s.total) FILTER (WHERE st.code <> 'CANCELLED'), 0) AS net_revenue
    FROM public.orders o
    JOIN privacy.subject_pseudonyms sp ON sp.person_id = o.person_id
    LEFT JOIN public.sales s  ON s.order_id = o.id AND s.deleted_at IS NULL
    LEFT JOIN public.states st ON st.id = s.state_id
    WHERE o.data_origin = 'REAL' AND o.deleted_at IS NULL
    GROUP BY 1, 2, 3
),
firsts AS (
    SELECT subject_key, min(order_month) AS cohort_month FROM order_values GROUP BY 1
)
SELECT f.cohort_month,
       ((extract(year FROM ov.order_month) - extract(year FROM f.cohort_month)) * 12
        + extract(month FROM ov.order_month) - extract(month FROM f.cohort_month))::int AS months_since_first,
       count(DISTINCT ov.subject_key) AS active_subjects,
       count(DISTINCT ov.order_id)    AS orders,
       sum(ov.net_revenue)            AS net_revenue
FROM order_values ov
JOIN firsts f ON f.subject_key = ov.subject_key
GROUP BY 1, 2;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_customer_cohorts__grain
    ON analytics.mv_customer_cohorts (cohort_month, months_since_first);

CREATE MATERIALIZED VIEW IF NOT EXISTS analytics.mv_customer_rfm AS
WITH base AS (
    SELECT sp.subject_key,
           (current_date - max(public.bogota_date(o.created_at)))                     AS recency_days,
           count(DISTINCT o.id) FILTER (WHERE public.legacy_ts(o.created_at) >= now() - interval '365 days') AS frequency_365d,
           coalesce(sum(s.total) FILTER (WHERE st.code <> 'CANCELLED'
                    AND public.legacy_ts(o.created_at) >= now() - interval '365 days'), 0) AS monetary_365d
    FROM public.orders o
    JOIN privacy.subject_pseudonyms sp ON sp.person_id = o.person_id
    LEFT JOIN public.sales s  ON s.order_id = o.id AND s.deleted_at IS NULL
    LEFT JOIN public.states st ON st.id = s.state_id
    WHERE o.data_origin = 'REAL' AND o.deleted_at IS NULL
    GROUP BY sp.subject_key
),
scored AS (
    SELECT b.*,
           (6 - ntile(5) OVER (ORDER BY b.recency_days))::smallint AS r_score,
           ntile(5) OVER (ORDER BY b.frequency_365d)::smallint     AS f_score,
           ntile(5) OVER (ORDER BY b.monetary_365d)::smallint      AS m_score
    FROM base b
)
SELECT subject_key, recency_days, frequency_365d, monetary_365d, r_score, f_score, m_score,
       CASE WHEN r_score >= 4 AND f_score >= 4 THEN 'CHAMPIONS'
            WHEN f_score >= 4 THEN 'LOYAL'
            WHEN r_score <= 2 AND f_score >= 3 THEN 'AT_RISK'
            WHEN r_score >= 4 AND frequency_365d <= 1 THEN 'NEW'
            WHEN r_score <= 2 AND f_score <= 2 THEN 'HIBERNATING'
            ELSE 'OTHERS' END AS segment,
       now() AS computed_at
FROM scored;
CREATE UNIQUE INDEX IF NOT EXISTS uq_mv_customer_rfm__subject ON analytics.mv_customer_rfm (subject_key);

-- -----------------------------------------------------------------------------
-- Lead time real de proveedores
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW analytics.v_supplier_lead_times AS
SELECT po.store_id,
       po.branch_id,
       po.supplier_id,
       po.id                                                           AS purchase_order_id,
       poi.id                                                          AS purchase_order_item_id,
       poi.product_id,
       po.ordered_at,
       po.expected_at,
       pr.received_at,
       round((extract(epoch FROM pr.received_at - po.ordered_at) / 86400)::numeric, 2) AS lead_time_days,
       round((extract(epoch FROM po.expected_at - po.ordered_at) / 86400)::numeric, 2) AS promised_days,
       pri.qty_received,
       pri.qty_rejected,
       poi.qty_ordered,
       round(poi.qty_received::numeric / nullif(poi.qty_ordered, 0), 4) AS fill_rate
FROM public.purchase_receipt_items pri
JOIN public.purchase_receipts pr      ON pr.id = pri.purchase_receipt_id
JOIN public.purchase_order_items poi  ON poi.id = pri.purchase_order_item_id
JOIN public.purchase_orders po        ON po.id = poi.purchase_order_id
WHERE po.ordered_at IS NOT NULL;
