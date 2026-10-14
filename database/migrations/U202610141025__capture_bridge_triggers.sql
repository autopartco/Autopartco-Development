-- =============================================================================
-- U202610141025__capture_bridge_triggers.sql  (rollback de V202610141025)
-- Retira los triggers puente. Sin pérdida de datos: lo ya capturado se queda.
-- Retírelos cuando el backend escriba el ledger y los historiales directamente.
-- psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DROP TRIGGER IF EXISTS trg_inventory__touch_last_movement_ins ON public.inventory;
DROP TRIGGER IF EXISTS trg_inventory__touch_last_movement_upd ON public.inventory;
DROP TRIGGER IF EXISTS trg_inventory__capture_movement_ins ON public.inventory;
DROP TRIGGER IF EXISTS trg_inventory__capture_movement_upd ON public.inventory;
DROP TRIGGER IF EXISTS trg_inventory__capture_price_ins ON public.inventory;
DROP TRIGGER IF EXISTS trg_inventory__capture_price_upd ON public.inventory;
DROP TRIGGER IF EXISTS trg_sales__stamp_status_times ON public.sales;
DROP TRIGGER IF EXISTS trg_sales__capture_status_ins ON public.sales;
DROP TRIGGER IF EXISTS trg_sales__capture_status_upd ON public.sales;
DROP TRIGGER IF EXISTS trg_orders__stamp_cancelled ON public.orders;
DROP TRIGGER IF EXISTS trg_orders__capture_status_ins ON public.orders;
DROP TRIGGER IF EXISTS trg_orders__capture_status_upd ON public.orders;
DROP TRIGGER IF EXISTS trg_shipment_events__fill_transition ON public.shipment_events;

DROP FUNCTION IF EXISTS public.tg_inventory__touch_last_movement();
DROP FUNCTION IF EXISTS public.tg_inventory__capture_movement();
DROP FUNCTION IF EXISTS public.tg_inventory__capture_price();
DROP FUNCTION IF EXISTS public.tg_sales__stamp_status_times();
DROP FUNCTION IF EXISTS public.tg_sales__capture_status();
DROP FUNCTION IF EXISTS public.tg_orders__stamp_cancelled();
DROP FUNCTION IF EXISTS public.tg_orders__capture_status();
DROP FUNCTION IF EXISTS public.tg_shipment_events__fill_transition();
DROP FUNCTION IF EXISTS public.app_actor_type(TEXT);
DROP FUNCTION IF EXISTS public.app_actor_account_id();
DROP FUNCTION IF EXISTS public.app_capture_managed();
