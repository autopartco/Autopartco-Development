-- =============================================================================
-- U202610141020__order_status_history.sql  (rollback de V202610141020)
-- DESTRUCTIVO: requiere aprobación. Pierde los historiales de estado, la
-- instantánea de las líneas y los datos de atribución y SLA.
-- Revierta antes U202610141025 (puente), U202610141021 (relleno) y los U de
-- fase 2 que referencian estas columnas (garaje: FK customer_vehicle_id).
-- Respaldo: pg_dump --data-only -t public.order_status_history -t public.sale_status_history \
--   -t public.orders -t public.sales -t public.order_details -t public.shipments \
--   -t public.shipment_events -t public.payment_events "$DATABASE_URL"
-- Las filas de shipment_statuses añadidas se conservan (podrían estar en uso).
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

CREATE OR REPLACE TRIGGER trg_shipment_events_append_only
    BEFORE UPDATE OR DELETE ON public.shipment_events
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();

DROP INDEX IF EXISTS public.uq_payment_events__payment_provider_event;
DROP INDEX IF EXISTS public.idx_payment_events__status;
DROP INDEX IF EXISTS public.idx_payment_events__from_status;
ALTER TABLE public.payment_events
    DROP COLUMN IF EXISTS occurred_at,
    DROP COLUMN IF EXISTS provider_status,
    DROP COLUMN IF EXISTS provider_event_id,
    DROP COLUMN IF EXISTS source,
    DROP COLUMN IF EXISTS from_status_id;

DROP INDEX IF EXISTS public.idx_shipment_events__status;
ALTER TABLE public.shipment_events
    DROP COLUMN IF EXISTS municipality_code,
    DROP COLUMN IF EXISTS actor_account_id,
    DROP COLUMN IF EXISTS source,
    DROP COLUMN IF EXISTS from_status_id;

ALTER TABLE public.shipments
    DROP COLUMN IF EXISTS package_weight_grams,
    DROP COLUMN IF EXISTS delivery_attempts,
    DROP COLUMN IF EXISTS distance_km,
    DROP COLUMN IF EXISTS destination_municipality_code,
    DROP COLUMN IF EXISTS origin_municipality_code,
    DROP COLUMN IF EXISTS estimated_delivery_at;

ALTER TABLE public.order_details
    DROP COLUMN IF EXISTS customer_vehicle_id,
    DROP COLUMN IF EXISTS compatibility_status_shown,
    DROP COLUMN IF EXISTS vat_rate,
    DROP COLUMN IF EXISTS unit_cost_snapshot,
    DROP COLUMN IF EXISTS promotion_id,
    DROP COLUMN IF EXISTS discount_amount,
    DROP COLUMN IF EXISTS list_unit_price,
    DROP COLUMN IF EXISTS category_id_snapshot,
    DROP COLUMN IF EXISTS sku_snapshot,
    DROP COLUMN IF EXISTS product_name_snapshot,
    DROP COLUMN IF EXISTS product_id;

DROP INDEX IF EXISTS public.idx_sales__updated_at;
ALTER TABLE public.sales
    DROP COLUMN IF EXISTS discount_total,
    DROP COLUMN IF EXISTS promised_delivery_by,
    DROP COLUMN IF EXISTS promised_ship_by,
    DROP COLUMN IF EXISTS cancellation_reason_code,
    DROP COLUMN IF EXISTS cancelled_at,
    DROP COLUMN IF EXISTS ready_at,
    DROP COLUMN IF EXISTS confirmed_at,
    DROP COLUMN IF EXISTS fulfillment_type;

ALTER TABLE public.store
    DROP CONSTRAINT IF EXISTS chk_store__data_origin,
    DROP COLUMN IF EXISTS data_origin;

ALTER TABLE public.orders
    DROP COLUMN IF EXISTS cancellation_reason_code,
    DROP COLUMN IF EXISTS cancelled_at,
    DROP COLUMN IF EXISTS placed_at,
    DROP COLUMN IF EXISTS total,
    DROP COLUMN IF EXISTS shipping_total,
    DROP COLUMN IF EXISTS discount_total,
    DROP COLUMN IF EXISTS subtotal,
    DROP COLUMN IF EXISTS currency,
    DROP COLUMN IF EXISTS data_origin,
    DROP COLUMN IF EXISTS session_id,
    DROP COLUMN IF EXISTS channel;

DROP TABLE IF EXISTS public.sale_status_history;
DROP TABLE IF EXISTS public.order_status_history;
DROP TABLE IF EXISTS public.cancellation_reasons;
