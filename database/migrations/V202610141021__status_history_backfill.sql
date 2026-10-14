-- =============================================================================
-- V202610141021__status_history_backfill.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.7 (relleno de datos)
--
--   1. Una fila from_status = NULL -> to_status = <estado actual>,
--      changed_at = created_at interpretado en autopartco.legacy_source_tz
--      (por defecto UTC; fíjela a la zona real de la JVM antes de migrar),
--      acotado a now(), actor_type = 'SYSTEM', por cada pedido y venta sin
--      historial.
--   2. order_details.product_id desde inventory.product_id. No se rellenan
--      el nombre, la categoría ni el precio de lista: el valor actual no es
--      el del momento de la venta y sesgaría los modelos. Los ids rellenados
--      se guardan en public.migration_backfill_order_details para que el
--      rollback anule solo esos.
-- Idempotente. Transacción: SÍ. Rollback: U202610141021.
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

INSERT INTO public.order_status_history (order_id, from_status, to_status, changed_at, actor_type, notes)
SELECT o.id, NULL, s.code, least(public.legacy_ts(o.created_at), now()), 'SYSTEM', 'Estado inicial por migración'
FROM public.orders o
JOIN public.states s ON s.id = o.state_id
WHERE NOT EXISTS (SELECT 1 FROM public.order_status_history h WHERE h.order_id = o.id);

INSERT INTO public.sale_status_history (sale_id, store_id, from_status, to_status, changed_at, actor_type, notes)
SELECT sa.id, b.store_id, NULL, s.code, least(public.legacy_ts(sa.created_at), now()), 'SYSTEM', 'Estado inicial por migración'
FROM public.sales sa
JOIN public.branches b ON b.id = sa.branch_id
JOIN public.states s ON s.id = sa.state_id
WHERE NOT EXISTS (SELECT 1 FROM public.sale_status_history h WHERE h.sale_id = sa.id);

CREATE TABLE IF NOT EXISTS public.migration_backfill_order_details (
    order_detail_id UUID        NOT NULL,
    backfilled_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_migration_backfill_order_details PRIMARY KEY (order_detail_id)
);
COMMENT ON TABLE public.migration_backfill_order_details IS
    'order_details.product_id rellenados por V202610141021; los usa su rollback.';

WITH filled AS (
    UPDATE public.order_details od
    SET product_id = i.product_id
    FROM public.inventory i
    WHERE i.id = od.inventory_id
      AND od.product_id IS NULL
    RETURNING od.id
)
INSERT INTO public.migration_backfill_order_details (order_detail_id)
SELECT id FROM filled
ON CONFLICT DO NOTHING;
