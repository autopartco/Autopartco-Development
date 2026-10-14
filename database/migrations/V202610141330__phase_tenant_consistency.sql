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
--     purchase_receipt_items      línea de la orden de compra del recibo; movimiento de ese
--                                 inventario/sucursal/producto (e inventario de la línea)
--     return_requests             venta del pedido, almacén de la venta, pedido de la persona;
--                                 vehículo y reclamo (si hay) de la misma persona (reclamo del
--                                 mismo almacén si tiene store_id y del mismo pedido si tiene
--                                 order_id; reclamo sin pedido: basta la persona)
--     return_request_items        la línea pertenece a la venta de la solicitud; el movimiento
--                                 (si hay) es del inventario de la línea del pedido
--     stock_reservations          la línea reservada es de ese inventario
--     payment_risk_assessments    el pago pertenece al pedido evaluado
--     order_details               customer_vehicle_id es de la persona del pedido
--     cart_items                  customer_vehicle_id es de la persona del carrito (anónimo: no)
--     carts                       converted_order_id exige person_id y es pedido de esa persona
--   Claves padre inmutables (reutiliza public.tg_tenant_key__immutable de V202610130925, que
--   se crea antes; BEFORE UPDATE ... WHEN (OLD.x IS DISTINCT FROM NEW.x)):
--     customer_vehicles.person_id, payments.order_id, purchase_receipts.purchase_order_id,
--     purchase_order_items.purchase_order_id   (no se pueden reasignar)
--     carts.person_id  con 'allow_from_null': NULL -> persona (fusión anónimo -> cuenta) sí;
--                      persona A -> B y A -> NULL no.
--   Validación en INSERT y en UPDATE de las columnas clave. SQLSTATE 23514.
--   Vehículos con deleted_at: se rechazan solo al asociarlos (INSERT o cambio de la columna
--   del vehículo); no se revalidan históricos en otros UPDATE.
--   Columnas NULLables: NULL = no se valida (salvo carts.converted_order_id, que exige persona).
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
    -- Vehículo: de la persona; si es eliminado, solo se rechaza al asociarlo (INSERT o cambio).
    IF NEW.customer_vehicle_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM customer_vehicles cv
        WHERE cv.id = NEW.customer_vehicle_id AND cv.person_id = NEW.person_id
          AND (cv.deleted_at IS NULL
               OR (TG_OP = 'UPDATE' AND OLD.customer_vehicle_id IS NOT DISTINCT FROM NEW.customer_vehicle_id))
    ) THEN
        PERFORM raise_tenant_mismatch('chk_return_requests__consistency', NEW.customer_vehicle_id::text);
    END IF;
    -- Reclamo: de la persona; del mismo almacén de la devolución (o sin almacén) y, si tiene
    -- pedido, del mismo pedido. Reclamo sin order_id: basta coincidir la persona (decisión C).
    IF NEW.claim_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM claims c
        WHERE c.id = NEW.claim_id AND c.person_id = NEW.person_id
          AND (c.store_id IS NULL OR c.store_id = NEW.store_id)
          AND (c.order_id IS NULL OR c.order_id = NEW.order_id)
    ) THEN
        PERFORM raise_tenant_mismatch('chk_return_requests__consistency', NEW.claim_id::text);
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
          OR OLD.store_id IS DISTINCT FROM NEW.store_id OR OLD.person_id IS DISTINCT FROM NEW.person_id
          OR OLD.customer_vehicle_id IS DISTINCT FROM NEW.customer_vehicle_id
          OR OLD.claim_id IS DISTINCT FROM NEW.claim_id)
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
    -- Movimiento de reingreso: del inventario de la línea del pedido devuelta.
    IF NEW.inventory_movement_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM inventory_movements im JOIN order_details od ON od.id = NEW.order_detail_id
        WHERE im.id = NEW.inventory_movement_id AND im.inventory_id = od.inventory_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_return_request_items__consistency', NEW.inventory_movement_id::text);
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

-- payment_risk_assessments ---------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_payment_risk_assessments__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payments p WHERE p.id = NEW.payment_id AND p.order_id = NEW.order_id) THEN
        PERFORM raise_tenant_mismatch('chk_payment_risk_assessments__consistency', NEW.payment_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_payment_risk_assessments__consistency_ins
    BEFORE INSERT ON public.payment_risk_assessments
    FOR EACH ROW EXECUTE FUNCTION public.tg_payment_risk_assessments__consistency();
CREATE OR REPLACE TRIGGER trg_payment_risk_assessments__consistency_upd
    BEFORE UPDATE ON public.payment_risk_assessments FOR EACH ROW
    WHEN (OLD.payment_id IS DISTINCT FROM NEW.payment_id OR OLD.order_id IS DISTINCT FROM NEW.order_id)
    EXECUTE FUNCTION public.tg_payment_risk_assessments__consistency();

-- purchase_receipt_items ------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_purchase_receipt_items__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    -- La línea de la orden pertenece a la orden de compra del recibo.
    IF NOT EXISTS (
        SELECT 1
        FROM purchase_receipts pr JOIN purchase_order_items poi ON poi.purchase_order_id = pr.purchase_order_id
        WHERE pr.id = NEW.purchase_receipt_id AND poi.id = NEW.purchase_order_item_id
    ) THEN
        PERFORM raise_tenant_mismatch('chk_purchase_receipt_items__consistency', NEW.purchase_order_item_id::text);
    END IF;
    -- El movimiento es de un inventario de la sucursal del recibo, del producto de la línea
    -- y, si la línea de la orden fija inventario, exactamente de ese.
    IF NEW.inventory_movement_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM inventory_movements im
        JOIN inventory i                ON i.id = im.inventory_id
        JOIN purchase_receipts pr       ON pr.id = NEW.purchase_receipt_id
        JOIN purchase_order_items poi   ON poi.id = NEW.purchase_order_item_id
        WHERE im.id = NEW.inventory_movement_id
          AND i.branch_id = pr.branch_id AND i.product_id = poi.product_id
          AND (poi.inventory_id IS NULL OR poi.inventory_id = im.inventory_id)
    ) THEN
        PERFORM raise_tenant_mismatch('chk_purchase_receipt_items__consistency', NEW.inventory_movement_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_purchase_receipt_items__consistency_ins
    BEFORE INSERT ON public.purchase_receipt_items
    FOR EACH ROW EXECUTE FUNCTION public.tg_purchase_receipt_items__consistency();
CREATE OR REPLACE TRIGGER trg_purchase_receipt_items__consistency_upd
    BEFORE UPDATE ON public.purchase_receipt_items FOR EACH ROW
    WHEN (OLD.purchase_receipt_id IS DISTINCT FROM NEW.purchase_receipt_id
          OR OLD.purchase_order_item_id IS DISTINCT FROM NEW.purchase_order_item_id
          OR OLD.inventory_movement_id IS DISTINCT FROM NEW.inventory_movement_id)
    EXECUTE FUNCTION public.tg_purchase_receipt_items__consistency();

-- order_details.customer_vehicle_id (trigger propio; no toca tg_order_details__consistency) ---
CREATE OR REPLACE FUNCTION public.tg_order_details__vehicle_consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.customer_vehicle_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM orders o JOIN customer_vehicles cv ON cv.person_id = o.person_id
        WHERE o.id = NEW.order_id AND cv.id = NEW.customer_vehicle_id
          AND (cv.deleted_at IS NULL
               OR (TG_OP = 'UPDATE' AND OLD.customer_vehicle_id IS NOT DISTINCT FROM NEW.customer_vehicle_id))
    ) THEN
        PERFORM raise_tenant_mismatch('chk_order_details__vehicle_consistency', NEW.customer_vehicle_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_order_details__vehicle_consistency_ins
    BEFORE INSERT ON public.order_details FOR EACH ROW
    WHEN (NEW.customer_vehicle_id IS NOT NULL)
    EXECUTE FUNCTION public.tg_order_details__vehicle_consistency();
CREATE OR REPLACE TRIGGER trg_order_details__vehicle_consistency_upd
    BEFORE UPDATE ON public.order_details FOR EACH ROW
    WHEN (OLD.customer_vehicle_id IS DISTINCT FROM NEW.customer_vehicle_id OR OLD.order_id IS DISTINCT FROM NEW.order_id)
    EXECUTE FUNCTION public.tg_order_details__vehicle_consistency();

-- cart_items.customer_vehicle_id ----------------------------------------------------------------
-- Carrito anónimo (person_id NULL): el JOIN por persona nunca coincide, así que con vehículo falla.
CREATE OR REPLACE FUNCTION public.tg_cart_items__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.customer_vehicle_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM carts c JOIN customer_vehicles cv ON cv.person_id = c.person_id
        WHERE c.id = NEW.cart_id AND cv.id = NEW.customer_vehicle_id
          AND (cv.deleted_at IS NULL
               OR (TG_OP = 'UPDATE' AND OLD.customer_vehicle_id IS NOT DISTINCT FROM NEW.customer_vehicle_id))
    ) THEN
        PERFORM raise_tenant_mismatch('chk_cart_items__consistency', NEW.customer_vehicle_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_cart_items__consistency_ins
    BEFORE INSERT ON public.cart_items FOR EACH ROW
    WHEN (NEW.customer_vehicle_id IS NOT NULL)
    EXECUTE FUNCTION public.tg_cart_items__consistency();
CREATE OR REPLACE TRIGGER trg_cart_items__consistency_upd
    BEFORE UPDATE ON public.cart_items FOR EACH ROW
    WHEN (OLD.customer_vehicle_id IS DISTINCT FROM NEW.customer_vehicle_id OR OLD.cart_id IS DISTINCT FROM NEW.cart_id)
    EXECUTE FUNCTION public.tg_cart_items__consistency();

-- carts.converted_order_id ------------------------------------------------------------------------
-- Un carrito convertido exige persona (la fusión anónimo -> cuenta ocurre antes del checkout).
CREATE OR REPLACE FUNCTION public.tg_carts__consistency()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.converted_order_id IS NOT NULL AND (
        NEW.person_id IS NULL OR NOT EXISTS (
            SELECT 1 FROM orders o WHERE o.id = NEW.converted_order_id AND o.person_id = NEW.person_id)
    ) THEN
        PERFORM raise_tenant_mismatch('chk_carts__consistency', NEW.converted_order_id::text);
    END IF;
    RETURN NEW;
END;
$$;
CREATE OR REPLACE TRIGGER trg_carts__consistency_ins
    BEFORE INSERT ON public.carts FOR EACH ROW
    WHEN (NEW.converted_order_id IS NOT NULL)
    EXECUTE FUNCTION public.tg_carts__consistency();
CREATE OR REPLACE TRIGGER trg_carts__consistency_upd
    BEFORE UPDATE ON public.carts FOR EACH ROW
    WHEN (OLD.converted_order_id IS DISTINCT FROM NEW.converted_order_id OR OLD.person_id IS DISTINCT FROM NEW.person_id)
    EXECUTE FUNCTION public.tg_carts__consistency();

-- Claves padre inmutables --------------------------------------------------------------------------
-- Reutiliza public.tg_tenant_key__immutable (V202610130925). Evita reasignar el padre de una fila
-- y así saltarse las validaciones de coherencia anteriores. Hibernate reescribe todas las columnas
-- en cada UPDATE; el WHEN impide disparar el trigger si la clave no cambia.
CREATE OR REPLACE TRIGGER trg_customer_vehicles__person_immutable
    BEFORE UPDATE ON public.customer_vehicles FOR EACH ROW
    WHEN (OLD.person_id IS DISTINCT FROM NEW.person_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('person_id');

-- Carrito anónimo (person_id NULL) -> persona permitido por la fusión; A -> B y A -> NULL no.
CREATE OR REPLACE TRIGGER trg_carts__person_immutable
    BEFORE UPDATE ON public.carts FOR EACH ROW
    WHEN (OLD.person_id IS DISTINCT FROM NEW.person_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('person_id', 'allow_from_null');

CREATE OR REPLACE TRIGGER trg_payments__order_immutable
    BEFORE UPDATE ON public.payments FOR EACH ROW
    WHEN (OLD.order_id IS DISTINCT FROM NEW.order_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('order_id');

CREATE OR REPLACE TRIGGER trg_purchase_receipts__purchase_order_immutable
    BEFORE UPDATE ON public.purchase_receipts FOR EACH ROW
    WHEN (OLD.purchase_order_id IS DISTINCT FROM NEW.purchase_order_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('purchase_order_id');

CREATE OR REPLACE TRIGGER trg_purchase_order_items__purchase_order_immutable
    BEFORE UPDATE ON public.purchase_order_items FOR EACH ROW
    WHEN (OLD.purchase_order_id IS DISTINCT FROM NEW.purchase_order_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('purchase_order_id');
