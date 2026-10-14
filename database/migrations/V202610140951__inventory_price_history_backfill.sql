-- =============================================================================
-- V202610140951__inventory_price_history_backfill.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.5 (relleno de datos)
--
-- Una fila INITIAL_LOAD abierta por cada inventario vigente:
--   list_price = inventory.price, unit_cost = inventory.unit_cost,
--   valid_from = inventory.created_at interpretado en autopartco.legacy_source_tz
--   (por defecto UTC), acotado a now().
-- Idempotente: omite inventarios que ya tienen fila abierta.
-- Transacción: SÍ. Rollback: U202610140951.
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

INSERT INTO public.inventory_price_history
    (inventory_id, store_id, branch_id, product_id, list_price, unit_cost, valid_from, change_source, change_reason)
SELECT i.id, b.store_id, i.branch_id, i.product_id, i.price, i.unit_cost,
       least(public.legacy_ts(i.created_at), now()), 'INITIAL_LOAD', 'Relleno inicial por migración'
FROM public.inventory i
JOIN public.branches b ON b.id = i.branch_id
WHERE i.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM public.inventory_price_history h
      WHERE h.inventory_id = i.id AND h.valid_to IS NULL
  );
