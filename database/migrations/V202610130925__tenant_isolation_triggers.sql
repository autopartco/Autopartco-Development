-- =============================================================================
-- V202610130925__tenant_isolation_triggers.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: aislamiento por almacén y coherencia entre tablas relacionadas,
--   garantizados en la BD y no solo en el código.
--
-- Por qué triggers y no FK compuestas: las tablas puente roles_collaborators y
--   roles_permissions las escribe Hibernate (@JoinTable). Añadirles store_id NOT
--   NULL rompería sus INSERT. Los triggers validan sin cambiar columnas.
--
-- Reglas:
--   roles_collaborators            collaborators.collab_store_id = roles.store_id
--   seller_collaborator_invitations roles.store_id = store_id
--   branches_collaborators         branches.store_id = collaborators.collab_store_id
--   inventory                      branches.store_id = products.store_id (NOT NULL)
--   order_details                  sales.order_id = order_id y
--                                  inventory.branch_id = sales.branch_id
--   inventory_movements            order_details.inventory_id = inventory_id
--   receipts                       payments.order_id = order_id
--   store_reviews / claims         el pedido es de la persona y tuvo venta en el almacén
--   promotions (STORE + PRODUCT)   products.store_id = promotions.store_id
--   payments                       como máximo un pago APPROVED por pedido
--   Claves de tenant inmutables    roles.store_id, collaborators.collab_store_id,
--                                  branches.store_id, invitations.store_id,
--                                  seller_applications.store_id, sales.branch_id,
--                                  products.store_id (una vez asignado)
--
-- Los triggers de UPDATE llevan WHEN (columna IS DISTINCT FROM): Hibernate
--   reescribe todas las columnas en cada UPDATE, y así no se revalida (ni
--   falla por datos heredados) cuando la clave no cambia.
-- Error: SQLSTATE 23514 (check_violation). Spring lo traduce a
--   DataIntegrityViolationException.
--
-- Los triggers NO revisan filas existentes. Detección previa (deben dar 0):
--   SELECT count(*) FROM roles_collaborators rc JOIN collaborators c ON c.id = rc.collaborator_id
--     JOIN roles r ON r.id = rc.role_id WHERE c.collab_store_id <> r.store_id;
--   SELECT count(*) FROM seller_collaborator_invitations i JOIN roles r ON r.id = i.role_id
--     WHERE r.store_id <> i.store_id;
--   SELECT count(*) FROM inventory i JOIN branches b ON b.id = i.branch_id
--     JOIN products p ON p.id = i.product_id WHERE p.store_id IS DISTINCT FROM b.store_id;
--
-- Transacción: SÍ. Rollback: U202610130925__tenant_isolation_triggers.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

-- -----------------------------------------------------------------------------
-- roles_collaborators
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_roles_collaborators__same_store()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM collaborators c
        JOIN roles r ON r.store_id = c.collab_store_id
        WHERE c.id = NEW.collaborator_id AND r.id = NEW.role_id
    ) THEN
        RAISE EXCEPTION 'El rol % no pertenece al almacén del colaborador %', NEW.role_id, NEW.collaborator_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_roles_collaborators__same_store';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_roles_collaborators__same_store
    BEFORE INSERT OR UPDATE ON public.roles_collaborators
    FOR EACH ROW EXECUTE FUNCTION public.tg_roles_collaborators__same_store();

-- -----------------------------------------------------------------------------
-- seller_collaborator_invitations
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_invitations__role_same_store()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM roles WHERE id = NEW.role_id AND store_id = NEW.store_id) THEN
        RAISE EXCEPTION 'El rol % no pertenece al almacén %', NEW.role_id, NEW.store_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_seller_collaborator_invitations__role_same_store';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_seller_collaborator_invitations__role_same_store_ins
    BEFORE INSERT ON public.seller_collaborator_invitations
    FOR EACH ROW EXECUTE FUNCTION public.tg_invitations__role_same_store();

CREATE OR REPLACE TRIGGER trg_seller_collaborator_invitations__role_same_store_upd
    BEFORE UPDATE ON public.seller_collaborator_invitations
    FOR EACH ROW
    WHEN (OLD.role_id IS DISTINCT FROM NEW.role_id OR OLD.store_id IS DISTINCT FROM NEW.store_id)
    EXECUTE FUNCTION public.tg_invitations__role_same_store();

-- -----------------------------------------------------------------------------
-- branches_collaborators
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_branches_collaborators__same_store()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM collaborators c
        JOIN branches b ON b.store_id = c.collab_store_id
        WHERE c.id = NEW.collaborator_id AND b.id = NEW.branch_id
    ) THEN
        RAISE EXCEPTION 'La sucursal % no pertenece al almacén del colaborador %', NEW.branch_id, NEW.collaborator_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_branches_collaborators__same_store';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_branches_collaborators__same_store
    BEFORE INSERT OR UPDATE ON public.branches_collaborators
    FOR EACH ROW EXECUTE FUNCTION public.tg_branches_collaborators__same_store();

-- -----------------------------------------------------------------------------
-- inventory: la sucursal y el producto son del mismo almacén
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_inventory__same_store()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM branches b
        JOIN products p ON p.store_id = b.store_id
        WHERE b.id = NEW.branch_id AND p.id = NEW.product_id
    ) THEN
        RAISE EXCEPTION 'El producto % no pertenece al almacén de la sucursal % (o no tiene almacén)',
            NEW.product_id, NEW.branch_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_inventory__same_store';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_inventory__same_store_ins
    BEFORE INSERT ON public.inventory
    FOR EACH ROW EXECUTE FUNCTION public.tg_inventory__same_store();

CREATE OR REPLACE TRIGGER trg_inventory__same_store_upd
    BEFORE UPDATE ON public.inventory
    FOR EACH ROW
    WHEN (OLD.branch_id IS DISTINCT FROM NEW.branch_id OR OLD.product_id IS DISTINCT FROM NEW.product_id)
    EXECUTE FUNCTION public.tg_inventory__same_store();

-- -----------------------------------------------------------------------------
-- order_details: pedido, venta e inventario coherentes
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_order_details__consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM sales s
        JOIN inventory i ON i.branch_id = s.branch_id
        WHERE s.id = NEW.sale_id AND s.order_id = NEW.order_id AND i.id = NEW.inventory_id
    ) THEN
        RAISE EXCEPTION 'Línea incoherente: la venta % no es del pedido % o el inventario % no es de su sucursal',
            NEW.sale_id, NEW.order_id, NEW.inventory_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_order_details__consistency';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_order_details__consistency_ins
    BEFORE INSERT ON public.order_details
    FOR EACH ROW EXECUTE FUNCTION public.tg_order_details__consistency();

CREATE OR REPLACE TRIGGER trg_order_details__consistency_upd
    BEFORE UPDATE ON public.order_details
    FOR EACH ROW
    WHEN (OLD.sale_id IS DISTINCT FROM NEW.sale_id OR OLD.order_id IS DISTINCT FROM NEW.order_id
          OR OLD.inventory_id IS DISTINCT FROM NEW.inventory_id)
    EXECUTE FUNCTION public.tg_order_details__consistency();

-- -----------------------------------------------------------------------------
-- inventory_movements: si referencia una línea, el inventario coincide
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_inventory_movements__order_detail_match()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.order_detail_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM order_details WHERE id = NEW.order_detail_id AND inventory_id = NEW.inventory_id
    ) THEN
        RAISE EXCEPTION 'El movimiento referencia la línea % de otro inventario', NEW.order_detail_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_inventory_movements__order_detail_match';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_inventory_movements__order_detail_match
    BEFORE INSERT ON public.inventory_movements
    FOR EACH ROW EXECUTE FUNCTION public.tg_inventory_movements__order_detail_match();

-- -----------------------------------------------------------------------------
-- receipts: el pago es del mismo pedido
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_receipts__payment_order_match()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM payments WHERE id = NEW.payment_id AND order_id = NEW.order_id) THEN
        RAISE EXCEPTION 'El pago % no pertenece al pedido %', NEW.payment_id, NEW.order_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_receipts__payment_order_match';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_receipts__payment_order_match
    BEFORE INSERT OR UPDATE ON public.receipts
    FOR EACH ROW EXECUTE FUNCTION public.tg_receipts__payment_order_match();

-- -----------------------------------------------------------------------------
-- store_reviews y claims: pedido de la persona y con venta en el almacén
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_order_person_store__consistency()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.order_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM orders WHERE id = NEW.order_id AND person_id = NEW.person_id) THEN
        RAISE EXCEPTION 'El pedido % no pertenece a la persona %', NEW.order_id, NEW.person_id
            USING ERRCODE = '23514', CONSTRAINT = format('chk_%s__order_person', TG_TABLE_NAME);
    END IF;
    IF NEW.store_id IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM sales s JOIN branches b ON b.id = s.branch_id
        WHERE s.order_id = NEW.order_id AND b.store_id = NEW.store_id
    ) THEN
        RAISE EXCEPTION 'El pedido % no tiene ventas del almacén %', NEW.order_id, NEW.store_id
            USING ERRCODE = '23514', CONSTRAINT = format('chk_%s__order_store', TG_TABLE_NAME);
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_store_reviews__order_consistency
    BEFORE INSERT OR UPDATE ON public.store_reviews
    FOR EACH ROW EXECUTE FUNCTION public.tg_order_person_store__consistency();

CREATE OR REPLACE TRIGGER trg_claims__order_consistency
    BEFORE INSERT OR UPDATE ON public.claims
    FOR EACH ROW EXECUTE FUNCTION public.tg_order_person_store__consistency();

-- -----------------------------------------------------------------------------
-- promotions: una promoción de almacén sobre un producto exige que el producto
-- sea de ese almacén (PromotionService ya lo valida; aquí se garantiza).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_promotions__product_same_store()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.scope = 'STORE' AND NEW.target_type = 'PRODUCT' AND NOT EXISTS (
        SELECT 1 FROM products WHERE id = NEW.target_id AND store_id = NEW.store_id
    ) THEN
        RAISE EXCEPTION 'El producto % no pertenece al almacén % de la promoción', NEW.target_id, NEW.store_id
            USING ERRCODE = '23514', CONSTRAINT = 'chk_promotions__product_same_store';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_promotions__product_same_store_ins
    BEFORE INSERT ON public.promotions
    FOR EACH ROW EXECUTE FUNCTION public.tg_promotions__product_same_store();

CREATE OR REPLACE TRIGGER trg_promotions__product_same_store_upd
    BEFORE UPDATE ON public.promotions
    FOR EACH ROW
    WHEN (OLD.target_id IS DISTINCT FROM NEW.target_id OR OLD.target_type IS DISTINCT FROM NEW.target_type
          OR OLD.store_id IS DISTINCT FROM NEW.store_id OR OLD.scope IS DISTINCT FROM NEW.scope)
    EXECUTE FUNCTION public.tg_promotions__product_same_store();

-- -----------------------------------------------------------------------------
-- payments: un solo pago APPROVED por pedido. El estado es una FK a
-- payment_statuses (los UUID cambian entre entornos), así que no sirve un
-- índice único parcial. Se bloquea la fila del pedido para serializar.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_payments__single_approved()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    approved_id UUID;
BEGIN
    SELECT id INTO approved_id FROM payment_statuses WHERE code = 'APPROVED';
    IF approved_id IS NULL OR NEW.payment_status_id IS DISTINCT FROM approved_id THEN
        RETURN NEW;
    END IF;

    PERFORM 1 FROM orders WHERE id = NEW.order_id FOR UPDATE;

    IF EXISTS (
        SELECT 1 FROM payments
        WHERE order_id = NEW.order_id AND payment_status_id = approved_id AND id <> NEW.id
    ) THEN
        RAISE EXCEPTION 'El pedido % ya tiene un pago aprobado', NEW.order_id
            USING ERRCODE = '23505', CONSTRAINT = 'uq_payments__order_approved';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_payments__single_approved_ins
    BEFORE INSERT ON public.payments
    FOR EACH ROW EXECUTE FUNCTION public.tg_payments__single_approved();

CREATE OR REPLACE TRIGGER trg_payments__single_approved_upd
    BEFORE UPDATE ON public.payments
    FOR EACH ROW
    WHEN (OLD.payment_status_id IS DISTINCT FROM NEW.payment_status_id OR OLD.order_id IS DISTINCT FROM NEW.order_id)
    EXECUTE FUNCTION public.tg_payments__single_approved();

-- -----------------------------------------------------------------------------
-- Claves de tenant inmutables. TG_ARGV[0] = columna; TG_ARGV[1] = 'allow_from_null'
-- permite asignarla una vez (products.store_id es nullable).
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_tenant_key__immutable()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    old_value TEXT := to_jsonb(OLD) ->> TG_ARGV[0];
BEGIN
    IF old_value IS NULL AND TG_NARGS > 1 AND TG_ARGV[1] = 'allow_from_null' THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '%.% es una clave de almacén inmutable', TG_TABLE_NAME, TG_ARGV[0]
        USING ERRCODE = '23514', CONSTRAINT = format('chk_%s__%s_immutable', TG_TABLE_NAME, TG_ARGV[0]);
END;
$$;

CREATE OR REPLACE TRIGGER trg_roles__store_immutable
    BEFORE UPDATE ON public.roles FOR EACH ROW
    WHEN (OLD.store_id IS DISTINCT FROM NEW.store_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('store_id');

CREATE OR REPLACE TRIGGER trg_collaborators__store_immutable
    BEFORE UPDATE ON public.collaborators FOR EACH ROW
    WHEN (OLD.collab_store_id IS DISTINCT FROM NEW.collab_store_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('collab_store_id');

CREATE OR REPLACE TRIGGER trg_branches__store_immutable
    BEFORE UPDATE ON public.branches FOR EACH ROW
    WHEN (OLD.store_id IS DISTINCT FROM NEW.store_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('store_id');

CREATE OR REPLACE TRIGGER trg_seller_collaborator_invitations__store_immutable
    BEFORE UPDATE ON public.seller_collaborator_invitations FOR EACH ROW
    WHEN (OLD.store_id IS DISTINCT FROM NEW.store_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('store_id');

CREATE OR REPLACE TRIGGER trg_seller_applications__store_immutable
    BEFORE UPDATE ON public.seller_applications FOR EACH ROW
    WHEN (OLD.store_id IS DISTINCT FROM NEW.store_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('store_id');

CREATE OR REPLACE TRIGGER trg_sales__branch_immutable
    BEFORE UPDATE ON public.sales FOR EACH ROW
    WHEN (OLD.branch_id IS DISTINCT FROM NEW.branch_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('branch_id');

CREATE OR REPLACE TRIGGER trg_products__store_immutable
    BEFORE UPDATE ON public.products FOR EACH ROW
    WHEN (OLD.store_id IS DISTINCT FROM NEW.store_id)
    EXECUTE FUNCTION public.tg_tenant_key__immutable('store_id', 'allow_from_null');
