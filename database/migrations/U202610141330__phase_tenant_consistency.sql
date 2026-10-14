-- =============================================================================
-- U202610141330__phase_tenant_consistency.sql  (rollback de V202610141330)
-- Elimina los triggers de coherencia de las tablas nuevas. Sin pérdida de datos.
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DROP TRIGGER IF EXISTS trg_inventory_price_history__consistency ON public.inventory_price_history;
DROP TRIGGER IF EXISTS trg_inventory_reorder_policies__consistency_ins ON public.inventory_reorder_policies;
DROP TRIGGER IF EXISTS trg_inventory_reorder_policies__consistency_upd ON public.inventory_reorder_policies;
DROP TRIGGER IF EXISTS trg_sale_status_history__consistency ON public.sale_status_history;
DROP TRIGGER IF EXISTS trg_supplier_products__consistency_ins ON public.supplier_products;
DROP TRIGGER IF EXISTS trg_supplier_products__consistency_upd ON public.supplier_products;
DROP TRIGGER IF EXISTS trg_purchase_orders__consistency_ins ON public.purchase_orders;
DROP TRIGGER IF EXISTS trg_purchase_orders__consistency_upd ON public.purchase_orders;
DROP TRIGGER IF EXISTS trg_purchase_order_items__consistency_ins ON public.purchase_order_items;
DROP TRIGGER IF EXISTS trg_purchase_order_items__consistency_upd ON public.purchase_order_items;
DROP TRIGGER IF EXISTS trg_purchase_receipts__consistency ON public.purchase_receipts;
DROP TRIGGER IF EXISTS trg_return_requests__consistency_ins ON public.return_requests;
DROP TRIGGER IF EXISTS trg_return_requests__consistency_upd ON public.return_requests;
DROP TRIGGER IF EXISTS trg_return_request_items__consistency ON public.return_request_items;
DROP TRIGGER IF EXISTS trg_stock_reservations__consistency_ins ON public.stock_reservations;
DROP TRIGGER IF EXISTS trg_stock_reservations__consistency_upd ON public.stock_reservations;

DROP FUNCTION IF EXISTS public.tg_inventory_price_history__consistency();
DROP FUNCTION IF EXISTS public.tg_inventory_reorder_policies__consistency();
DROP FUNCTION IF EXISTS public.tg_sale_status_history__consistency();
DROP FUNCTION IF EXISTS public.tg_supplier_products__consistency();
DROP FUNCTION IF EXISTS public.tg_purchase_orders__consistency();
DROP FUNCTION IF EXISTS public.tg_purchase_order_items__consistency();
DROP FUNCTION IF EXISTS public.tg_purchase_receipts__consistency();
DROP FUNCTION IF EXISTS public.tg_return_requests__consistency();
DROP FUNCTION IF EXISTS public.tg_return_request_items__consistency();
DROP FUNCTION IF EXISTS public.tg_stock_reservations__consistency();
DROP FUNCTION IF EXISTS public.raise_tenant_mismatch(TEXT, TEXT);
