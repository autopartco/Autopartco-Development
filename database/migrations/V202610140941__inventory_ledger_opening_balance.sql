-- =============================================================================
-- V202610140941__inventory_ledger_opening_balance.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.3 (relleno de datos)
--
-- Propósito: migración de datos separada de la estructura (§14). Crea un
--   movimiento INITIAL_LOAD (IN, 0 -> stock) por cada inventario vigente con
--   stock > 0 que todavía no tenga movimientos. Así el ledger cuadra con
--   inventory.stock desde el primer día:
--     inventory.stock = new_stock del último movimiento.
--   occurred_at = inventory.created_at interpretado en autopartco.legacy_source_tz
--   (fíjela a la zona real de la JVM antes de migrar; por defecto UTC), acotado
--   a now() para no generar fechas futuras si la zona supuesta es errónea.
-- Idempotente: no duplica si ya hay movimientos.
--
-- Transacción: SÍ.
-- Rollback: U202610140941 (borra solo estos movimientos; requiere purga).
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

INSERT INTO public.inventory_movements
    (inventory_id, movement_type, quantity, previous_stock, new_stock,
     reason_code, source, occurred_at, idempotency_key, notes)
SELECT i.id, 'IN', i.stock, 0, i.stock,
       'INITIAL_LOAD', 'SYSTEM', least(public.legacy_ts(i.created_at), now()),
       'OPENING_BALANCE:' || i.id, 'Saldo inicial generado por migración'
FROM public.inventory i
WHERE i.deleted_at IS NULL
  AND i.stock > 0
  AND NOT EXISTS (SELECT 1 FROM public.inventory_movements m WHERE m.inventory_id = i.id)
ON CONFLICT DO NOTHING;
