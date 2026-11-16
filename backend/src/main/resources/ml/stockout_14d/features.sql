-- Variables del modelo stockout_14d (probabilidad de que el inventario llegue a 0 en los próximos 14 días).
-- Fuente única: la usan el entrenamiento (ml/) y la puntuación del backend (core.ml). Cambiarla exige reentrenar.
-- Parámetro :cutoff (fecha America/Bogota): solo se usan movimientos anteriores a la fecha de corte.
-- Una fila por inventario vigente cuyo stock en la fecha de corte (último movimiento) es mayor que 0.
WITH mv AS (
    SELECT m.id,
           m.inventory_id,
           m.movement_type,
           m.reason_code,
           m.quantity,
           m.new_stock,
           m.occurred_at,
           CAST(:cutoff AS date) - CAST(m.occurred_at AT TIME ZONE 'America/Bogota' AS date) AS days_ago
    FROM inventory_movements m
    WHERE m.deleted_at IS NULL
      AND m.occurred_at < CAST(CAST(:cutoff AS date) AS timestamp) AT TIME ZONE 'America/Bogota'
),
last_stock AS (
    SELECT DISTINCT ON (inventory_id) inventory_id, new_stock AS stock
    FROM mv
    ORDER BY inventory_id, occurred_at DESC, id DESC
),
agg AS (
    SELECT inventory_id,
           COALESCE(SUM(quantity) FILTER (WHERE reason_code = 'SALE' AND days_ago <= 7), 0) AS sold7,
           COALESCE(SUM(quantity) FILTER (WHERE reason_code = 'SALE' AND days_ago <= 14), 0) AS sold14,
           COALESCE(SUM(quantity) FILTER (WHERE reason_code = 'SALE' AND days_ago <= 28), 0) AS sold28,
           COALESCE(SUM(quantity) FILTER (WHERE reason_code = 'SALE' AND days_ago <= 91), 0) AS sold91,
           COUNT(*) FILTER (WHERE reason_code = 'SALE' AND days_ago <= 91) AS sale_events91,
           COALESCE(MIN(days_ago) FILTER (WHERE reason_code = 'SALE'), 365) AS days_since_sale,
           COALESCE(SUM(quantity) FILTER (WHERE movement_type = 'OUT' AND reason_code = 'MANUAL_CORRECTION'
               AND days_ago <= 91), 0) AS correction_out91,
           COALESCE(SUM(quantity) FILTER (WHERE movement_type = 'IN' AND reason_code = 'MANUAL_CORRECTION'
               AND days_ago <= 91), 0) AS correction_in91,
           COUNT(*) FILTER (WHERE reason_code = 'MANUAL_CORRECTION' AND days_ago <= 91) AS correction_events91,
           COALESCE(MIN(days_ago) FILTER (WHERE reason_code = 'MANUAL_CORRECTION'), 365) AS days_since_correction,
           COALESCE(MIN(days_ago) FILTER (WHERE movement_type = 'IN'), 365) AS days_since_in,
           MAX(days_ago) AS age_days
    FROM mv
    GROUP BY inventory_id
),
base AS (
    SELECT i.id AS inventory_id,
           i.branch_id,
           b.store_id,
           i.product_id,
           p.category_id,
           ls.stock,
           a.sold7,
           a.sold14,
           a.sold28,
           a.sold91,
           a.sale_events91,
           a.days_since_sale,
           a.correction_out91,
           a.correction_in91,
           a.correction_events91,
           a.days_since_correction,
           a.days_since_in,
           a.age_days
    FROM last_stock ls
    JOIN agg a ON a.inventory_id = ls.inventory_id
    JOIN inventory i ON i.id = ls.inventory_id
    JOIN branches b ON b.id = i.branch_id
    JOIN products p ON p.id = i.product_id
    WHERE i.deleted_at IS NULL
      AND ls.stock > 0
)
SELECT inventory_id,
       branch_id,
       store_id,
       product_id,
       stock,
       sold7,
       sold14,
       sold28,
       sold91,
       sale_events91,
       days_since_sale,
       correction_out91,
       correction_in91,
       correction_events91,
       days_since_correction,
       days_since_in,
       age_days,
       LEAST(CAST(stock AS double precision) / GREATEST(sold91 / 91.0, 0.01), 9999) AS cover_days,
       CAST(stock AS double precision) / GREATEST(correction_out91, 1) AS stock_per_correction_out,
       AVG(sold91) OVER (PARTITION BY branch_id, category_id) AS category_branch_avg_sold91,
       SUM(sold28) OVER (PARTITION BY branch_id) AS branch_sold28
FROM base
ORDER BY inventory_id
