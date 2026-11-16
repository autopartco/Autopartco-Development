-- Variables del modelo demand_category_28d (unidades vendidas en los próximos 28 días por sucursal y categoría).
-- Fuente única: la usan el entrenamiento (ml/) y la puntuación del backend (core.ml). Cambiarla exige reentrenar.
-- Parámetro :cutoff (fecha America/Bogota): solo se usan ventas de días anteriores a la fecha de corte.
-- Una fila por (sucursal, categoría) con ventas válidas en las 26 semanas anteriores.
WITH lines AS (
    SELECT s.branch_id,
           b.store_id,
           d.category_id_snapshot AS category_id,
           d.order_id,
           d.product_id,
           d.units,
           (CAST(:cutoff AS date) - CAST(s.created_at AT TIME ZONE 'America/Bogota' AS date) - 1) / 7 AS w
    FROM order_details d
    JOIN sales s ON s.id = d.sale_id
    JOIN branches b ON b.id = s.branch_id
    JOIN states st ON st.id = s.state_id
    WHERE s.deleted_at IS NULL
      AND d.deleted_at IS NULL
      AND st.code IN ('CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED')
      AND d.category_id_snapshot IS NOT NULL
      AND s.created_at >= CAST(CAST(:cutoff AS date) - 182 AS timestamp) AT TIME ZONE 'America/Bogota'
      AND s.created_at < CAST(CAST(:cutoff AS date) AS timestamp) AT TIME ZONE 'America/Bogota'
),
series AS (
    SELECT store_id,
           branch_id,
           category_id,
           COALESCE(SUM(units) FILTER (WHERE w = 0), 0) AS lag1,
           COALESCE(SUM(units) FILTER (WHERE w = 1), 0) AS lag2,
           COALESCE(SUM(units) FILTER (WHERE w = 2), 0) AS lag3,
           COALESCE(SUM(units) FILTER (WHERE w = 3), 0) AS lag4,
           COALESCE(SUM(units) FILTER (WHERE w = 4), 0) AS lag5,
           COALESCE(SUM(units) FILTER (WHERE w = 5), 0) AS lag6,
           COALESCE(SUM(units) FILTER (WHERE w < 4), 0) AS sum4,
           COALESCE(SUM(units) FILTER (WHERE w < 8), 0) AS sum8,
           COALESCE(SUM(units) FILTER (WHERE w < 13), 0) AS sum13,
           SUM(units) AS sum26,
           COUNT(DISTINCT w) FILTER (WHERE w < 13) AS active_weeks13,
           COUNT(DISTINCT w) AS active_weeks26,
           MIN(w) AS weeks_since_last,
           MAX(w) AS weeks_since_first,
           COUNT(DISTINCT product_id) FILTER (WHERE w < 13) AS products13,
           COUNT(DISTINCT order_id) FILTER (WHERE w < 13) AS orders13
    FROM lines
    GROUP BY store_id, branch_id, category_id
)
SELECT store_id,
       branch_id,
       category_id,
       lag1,
       lag2,
       lag3,
       lag4,
       lag5,
       lag6,
       sum4,
       sum8,
       sum13,
       sum26,
       active_weeks13,
       active_weeks26,
       weeks_since_last,
       weeks_since_first,
       products13,
       orders13,
       SUM(sum4) OVER (PARTITION BY branch_id) AS branch_sum4,
       CAST(SUM(sum4) OVER (PARTITION BY category_id) AS double precision)
           / COUNT(*) OVER (PARTITION BY category_id) AS category_avg_sum4
FROM series
ORDER BY branch_id, category_id
