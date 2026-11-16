-- Variables del modelo payment_anomaly (Isolation Forest sobre pagos).
-- Fuente única: la usan el entrenamiento (ml/) y la puntuación del backend (core.ml). Cambiarla exige reentrenar.
-- Parámetros :since y :until (timestamptz): pagos creados en [since, until). Solo usa datos conocidos al crear el pago.
WITH pay AS (
    SELECT p.id AS payment_id,
           p.order_id,
           p.amount,
           p.attempt_number,
           p.created_at,
           o.person_id,
           o.total,
           o.discount_total,
           o.shipping_total,
           o.subtotal
    FROM payments p
    JOIN orders o ON o.id = p.order_id
    WHERE p.deleted_at IS NULL
      AND p.created_at >= CAST(:since AS timestamptz)
      AND p.created_at < CAST(:until AS timestamptz)
)
SELECT pay.payment_id,
       pay.order_id,
       LN(1 + pay.amount) AS log_amount,
       CAST(pay.amount AS double precision) / GREATEST(COALESCE(hist.avg_amount, pay.amount), 1) AS amount_vs_history,
       pay.attempt_number,
       SIN(2 * PI() * EXTRACT(HOUR FROM pay.created_at AT TIME ZONE 'America/Bogota') / 24) AS hour_sin,
       COS(2 * PI() * EXTRACT(HOUR FROM pay.created_at AT TIME ZONE 'America/Bogota') / 24) AS hour_cos,
       COALESCE(hist.payments_30d, 0) AS payments_30d,
       COALESCE(hist.declined_30d, 0) AS declined_30d,
       COALESCE(hist.payments_24h, 0) AS payments_24h,
       COALESCE(hist.orders_365d, 0) AS orders_365d,
       COALESCE(items.units, 0) AS units,
       COALESCE(items.lines, 0) AS lines,
       COALESCE(items.branches, 0) AS branches,
       COALESCE(CAST(pay.shipping_total AS double precision) / GREATEST(pay.total, 1), 0) AS shipping_ratio,
       COALESCE(CAST(pay.discount_total AS double precision) / GREATEST(pay.subtotal, 1), 0) AS discount_ratio,
       COALESCE(EXTRACT(EPOCH FROM pay.created_at - pe.created_at) / 86400, 0) AS account_age_days
FROM pay
LEFT JOIN persons pe ON pe.id = pay.person_id
LEFT JOIN LATERAL (
    SELECT AVG(p2.amount) FILTER (WHERE ps.code = 'APPROVED') AS avg_amount,
           COUNT(*) FILTER (WHERE p2.created_at >= pay.created_at - INTERVAL '30 days') AS payments_30d,
           COUNT(*) FILTER (WHERE p2.created_at >= pay.created_at - INTERVAL '30 days'
               AND ps.code = 'DECLINED') AS declined_30d,
           COUNT(*) FILTER (WHERE p2.created_at >= pay.created_at - INTERVAL '1 day') AS payments_24h,
           COUNT(DISTINCT o2.id) AS orders_365d
    FROM orders o2
    JOIN payments p2 ON p2.order_id = o2.id AND p2.deleted_at IS NULL
    JOIN payment_statuses ps ON ps.id = p2.payment_status_id
    WHERE o2.person_id = pay.person_id
      AND p2.created_at < pay.created_at
      AND p2.created_at >= pay.created_at - INTERVAL '365 days'
) hist ON TRUE
LEFT JOIN LATERAL (
    SELECT SUM(d.units) AS units,
           COUNT(*) AS lines,
           COUNT(DISTINCT s.branch_id) AS branches
    FROM order_details d
    LEFT JOIN sales s ON s.id = d.sale_id
    WHERE d.order_id = pay.order_id
      AND d.deleted_at IS NULL
) items ON TRUE
ORDER BY pay.created_at, pay.payment_id
