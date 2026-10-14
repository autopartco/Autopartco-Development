-- =============================================================================
-- V202610141330__phase_tenant_consistency.sql
-- Autopartco | PostgreSQL 16+ | Integridad de las tablas nuevas (fases 1 y 2)
--
-- Propósito: el diseño (§3, Tenancy) desnormaliza store_id/branch_id para
--   filtrar en el servidor y aplicar RLS en el futuro. Estos triggers
--   garantizan que lo desnormalizado coincide con su origen:
--     inventory_price_history     store/branch/product = los del inventario
--     inventory_reorder_policies  store_id = almacén del inventario
--     sale_status_history         store_id = almacén de la venta
--     supplier_products           producto del almacén del proveedor
--     purchase_orders             sucursal y proveedor del mismo almacén
--     purchase_order_items        producto (e inventario) del almacén/sucursal de la orden
--     purchase_receipts           sucursal = la de la orden
--     return_requests             venta del pedido, almacén de la venta, pedido de la persona
--     return_request_items        la línea pertenece a la venta de la solicitud
--     stock_reservations          la línea reservada es de ese inventario
--   Validación en INSERT y en UPDATE de las columnas clave. SQLSTATE 23514.
--
-- Transacción: SÍ. Rollback: U202610141330__phase_tenant_consistency.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

CREATE OR REPLACE FUNCTION public.raise_tenant_mismatch(p_rule TEXT, p_detail TEXT)
RETURNS void LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'Incoherencia de almacén (%): %', p_rule, p_detail
        USING ERRCODE = '23514', CONSTRAINT = p_rule;
END;
$$;

-- inventory_price_history ------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_inventory_price_history__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM inventory i JOIN branches b ON b.id = i.branch_id
        WHERE i.id = NEW.inventory_id AND i.branch_id = NEW.branch_id
          AND i.product_id = NEW.product_id AND b.store_id = NEW.store_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_inventory_price_history__consistency', NEW.inventory_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_inventory_price_history__consistency
    BEFORE INSERT ON public.inventory_price_history
    FOR EACH ROW EXECUTE FUNCTION public.tg_inventory_price_history__consistency();

-- inventory_reorder_policies ----------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_inventory_reorder_policies__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM inventory i JOIN branches b ON b.id = i.branch_id
        WHERE i.id = NEW.inventory_id AND b.store_id = NEW.store_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_inventory_reorder_policies__consistency', NEW.inventory_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_inventory_reorder_policies__consistency_ins
    BEFORE INSERT ON public.inventory_reorder_policies
    FOR EACH ROW EXECUTE FUNCTION public.tg_inventory_reorder_policies__consistency();
CREATE OR REPLACE TRIGGER trg_inventory_reorder_policies__consistency_upd
    BEFORE UPDATE ON public.inventory_reorder_policies FOR EACH ROW
    WHEN (OLD.inventory_id IS DISTINCT FROM NEW.inventory_id OR OLD.store_id IS DISTINCT FROM NEW.store_id)
    EXECUTE FUNCTION public.tg_inventory_reorder_policies__consistency();

-- sale_status_history -------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_sale_status_history__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sales s JOIN branches b ON b.id = s.branch_id
        WHERE s.id = NEW.sale_id AND b.store_id = NEW.store_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_sale_status_history__consistency', NEW.sale_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_sale_status_history__consistency
    BEFORE INSERT ON public.sale_status_history
    FOR EACH ROW EXECUTE FUNCTION public.tg_sale_status_history__consistency();

-- supplier_products ------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_supplier_products__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM suppliers su JOIN products p ON p.store_id = su.store_id
        WHERE su.id = NEW.supplier_id AND p.id = NEW.product_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_supplier_products__consistency', NEW.product_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_supplier_products__consistency_ins
    BEFORE INSERT ON public.supplier_products
    FOR EACH ROW EXECUTE FUNCTION public.tg_supplier_products__consistency();
CREATE OR REPLACE TRIGGER trg_supplier_products__consistency_upd
    BEFORE UPDATE ON public.supplier_products FOR EACH ROW
    WHEN (OLD.supplier_id IS DISTINCT FROM NEW.supplier_id OR OLD.product_id IS DISTINCT FROM NEW.product_id)
    EXECUTE FUNCTION public.tg_supplier_products__consistency();

-- purchase_orders -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_purchase_orders__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM branches WHERE id = NEW.branch_id AND store_id = NEW.store_id)
       OR NOT EXISTS (SELECT 1 FROM suppliers WHERE id = NEW.supplier_id AND store_id = NEW.store_id) THEN
        PERFORM raise_tenant_mismatch('chk_purchase_orders__consistency', NEW.id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_purchase_orders__consistency_ins
    BEFORE INSERT ON public.purchase_orders
    FOR EACH ROW EXECUTE FUNCTION public.tg_purchase_orders__consistency();
CREATE OR REPLACE TRIGGER trg_purchase_orders__consistency_upd
    BEFORE UPDATE ON public.purchase_orders FOR EACH ROW
    WHEN (OLD.store_id IS DISTINCT FROM NEW.store_id OR OLD.branch_id IS DISTINCT FROM NEW.branch_id
          OR OLD.supplier_id IS DISTINCT FROM NEW.supplier_id)
    EXECUTE FUNCTION public.tg_purchase_orders__consistency();

-- purchase_order_items ------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_purchase_order_items__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM purchase_orders po JOIN products p ON p.store_id = po.store_id
        WHERE po.id = NEW.purchase_order_id AND p.id = NEW.product_id
    ) OR (NEW.inventory_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM purchase_orders po JOIN inventory i ON i.branch_id = po.branch_id
        WHERE po.id = NEW.purchase_order_id AND i.id = NEW.inventory_id AND i.product_id = NEW.product_id
    )) THEN
        PERFORM raise_tenant_mismatch('chk_purchase_order_items__consistency', NEW.product_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_purchase_order_items__consistency_ins
    BEFORE INSERT ON public.purchase_order_items
    FOR EACH ROW EXECUTE FUNCTION public.tg_purchase_order_items__consistency();
CREATE OR REPLACE TRIGGER trg_purchase_order_items__consistency_upd
    BEFORE UPDATE ON public.purchase_order_items FOR EACH ROW
    WHEN (OLD.purchase_order_id IS DISTINCT FROM NEW.purchase_order_id OR OLD.product_id IS DISTINCT FROM NEW.product_id
          OR OLD.inventory_id IS DISTINCT FROM NEW.inventory_id)
    EXECUTE FUNCTION public.tg_purchase_order_items__consistency();

-- purchase_receipts ---------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_purchase_receipts__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM purchase_orders WHERE id = NEW.purchase_order_id AND branch_id = NEW.branch_id) THEN
        PERFORM raise_tenant_mismatch('chk_purchase_receipts__consistency', NEW.purchase_order_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_purchase_receipts__consistency
    BEFORE INSERT OR UPDATE ON public.purchase_receipts
    FOR EACH ROW EXECUTE FUNCTION public.tg_purchase_receipts__consistency();

-- return_requests -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_return_requests__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sales s
        JOIN branches b ON b.id = s.branch_id
        JOIN orders o   ON o.id = s.order_id
        WHERE s.id = NEW.sale_id AND s.order_id = NEW.order_id
          AND b.store_id = NEW.store_id AND o.person_id = NEW.person_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_return_requests__consistency', NEW.sale_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_return_requests__consistency_ins
    BEFORE INSERT ON public.return_requests
    FOR EACH ROW EXECUTE FUNCTION public.tg_return_requests__consistency();
CREATE OR REPLACE TRIGGER trg_return_requests__consistency_upd
    BEFORE UPDATE ON public.return_requests FOR EACH ROW
    WHEN (OLD.sale_id IS DISTINCT FROM NEW.sale_id OR OLD.order_id IS DISTINCT FROM NEW.order_id
          OR OLD.store_id IS DISTINCT FROM NEW.store_id OR OLD.person_id IS DISTINCT FROM NEW.person_id)
    EXECUTE FUNCTION public.tg_return_requests__consistency();

-- return_request_items ----------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_return_request_items__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM return_requests rr JOIN order_details od ON od.sale_id = rr.sale_id
        WHERE rr.id = NEW.return_request_id AND od.id = NEW.order_detail_id AND NEW.units <= od.units
    ) THEN
        PERFORM raise_tenant_mismatch('chk_return_request_items__consistency', NEW.order_detail_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_return_request_items__consistency
    BEFORE INSERT OR UPDATE ON public.return_request_items
    FOR EACH ROW EXECUTE FUNCTION public.tg_return_request_items__consistency();

-- stock_reservations --------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_stock_reservations__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.order_detail_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM order_details WHERE id = NEW.order_detail_id AND inventory_id = NEW.inventory_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_stock_reservations__consistency', NEW.order_detail_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_stock_reservations__consistency_ins
    BEFORE INSERT ON public.stock_reservations
    FOR EACH ROW EXECUTE FUNCTION public.tg_stock_reservations__consistency();
CREATE OR REPLACE TRIGGER trg_stock_reservations__consistency_upd
    BEFORE UPDATE ON public.stock_reservations FOR EACH ROW
    WHEN (OLD.order_detail_id IS DISTINCT FROM NEW.order_detail_id OR OLD.inventory_id IS DISTINCT FROM NEW.inventory_id)
    EXECUTE FUNCTION public.tg_stock_reservations__consistency();
