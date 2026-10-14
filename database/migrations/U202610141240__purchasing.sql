-- =============================================================================
-- U202610141240__purchasing.sql  (rollback de V202610141240)
-- DESTRUCTIVO: requiere aprobación (proveedores, órdenes de compra y lead
-- times reales). Revierta antes U202610141320 (vista de lead times) y
-- U202610141330 (triggers de tenant).
-- Respaldo: pg_dump --data-only -t 'public.supplier*' -t 'public.purchase_*' "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.purchase_receipt_items;
DROP TABLE IF EXISTS public.purchase_receipts;
DROP TABLE IF EXISTS public.purchase_order_items;
DROP TABLE IF EXISTS public.purchase_orders;
DROP TABLE IF EXISTS public.supplier_products;
DROP TABLE IF EXISTS public.suppliers;
