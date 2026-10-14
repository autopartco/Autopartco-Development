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
DROP TRIGGER IF EXISTS trg_payment_risk_assessments__consistency_ins ON public.payment_risk_assessments;
DROP TRIGGER IF EXISTS trg_payment_risk_assessments__consistency_upd ON public.payment_risk_assessments;
DROP TRIGGER IF EXISTS trg_purchase_receipt_items__consistency_ins ON public.purchase_receipt_items;
DROP TRIGGER IF EXISTS trg_purchase_receipt_items__consistency_upd ON public.purchase_receipt_items;
DROP TRIGGER IF EXISTS trg_order_details__vehicle_consistency_ins ON public.order_details;
DROP TRIGGER IF EXISTS trg_order_details__vehicle_consistency_upd ON public.order_details;
DROP TRIGGER IF EXISTS trg_cart_items__consistency_ins ON public.cart_items;
DROP TRIGGER IF EXISTS trg_cart_items__consistency_upd ON public.cart_items;
DROP TRIGGER IF EXISTS trg_carts__consistency_ins ON public.carts;
DROP TRIGGER IF EXISTS trg_carts__consistency_upd ON public.carts;
-- Inmutabilidad de claves padre (tg_tenant_key__immutable es de V202610130925: no se elimina).
DROP TRIGGER IF EXISTS trg_customer_vehicles__person_immutable ON public.customer_vehicles;
DROP TRIGGER IF EXISTS trg_carts__person_immutable ON public.carts;
DROP TRIGGER IF EXISTS trg_payments__order_immutable ON public.payments;
DROP TRIGGER IF EXISTS trg_purchase_receipts__purchase_order_immutable ON public.purchase_receipts;
DROP TRIGGER IF EXISTS trg_purchase_order_items__purchase_order_immutable ON public.purchase_order_items;

DROP FUNCTION IF EXISTS public.tg_payment_risk_assessments__consistency();
DROP FUNCTION IF EXISTS public.tg_purchase_receipt_items__consistency();
DROP FUNCTION IF EXISTS public.tg_order_details__vehicle_consistency();
DROP FUNCTION IF EXISTS public.tg_cart_items__consistency();
DROP FUNCTION IF EXISTS public.tg_carts__consistency();
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
