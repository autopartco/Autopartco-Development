-- =============================================================================
-- V202610141025__capture_bridge_triggers.sql
-- Autopartco | PostgreSQL 16+ | Puente de captura hasta los cambios de backend (§12)
--
-- Contexto: el diseño (§12) pide que el backend escriba el ledger, el
--   historial de precios y el de estados en la misma transacción que el
--   cambio. Mientras no lo haga, lo que no se registra se pierde (principio 1).
--   Estos triggers capturan lo que el backend actual ya modifica:
--     inventory.stock           -> inventory_movements (IN/OUT, MANUAL_CORRECTION;
--                                  INITIAL_LOAD al crear el listado)
--     inventory.price/unit_cost -> inventory_price_history (cierra la fila
--                                  abierta e inserta la nueva)
--     sales.state_id            -> sale_status_history (+ confirmed_at, ready_at,
--                                  cancelled_at)
--     orders.state_id           -> order_status_history (+ cancelled_at)
--     shipment_events (INSERT)  -> rellena from_status_id y source
--   y fija inventory.last_movement_at.
--
-- Convivencia con el backend futuro (sin duplicados):
--   a) Si la transacción fija SET LOCAL autopartco.capture_managed_by_app = 'on',
--      los triggers no hacen nada. Es lo recomendado cuando existan StockLedger,
--      ListingPrices y el historial en SaleStatusService.
--   b) Además deduplican: no insertan si ya hay en la misma transacción una
--      fila equivalente (precio abierto igual; movimiento con los mismos
--      stocks y occurred_at = now(); historial con to_status igual y
--      changed_at = now()).
-- Actor: autopartco.actor_account_id (UUID) y autopartco.actor_type
--   (CUSTOMER|STORE|PLATFORM|SYSTEM|PROVIDER), opcionales y por transacción.
--   Valores inválidos se ignoran (nunca rompen la transacción). Por defecto,
--   las ventas se atribuyen a STORE (hoy solo las cambia el panel del almacén)
--   y los pedidos a SYSTEM.
--
-- Transacción: SÍ. Rollback: U202610141025__capture_bridge_triggers.sql
--   (se puede retirar sin perder datos cuando el backend escriba directamente).
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

-- -----------------------------------------------------------------------------
-- Contexto de sesión
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.app_capture_managed()
RETURNS BOOLEAN LANGUAGE sql STABLE
AS $$ SELECT coalesce(current_setting('autopartco.capture_managed_by_app', true), '') = 'on' $$;

CREATE OR REPLACE FUNCTION public.app_actor_account_id()
RETURNS UUID LANGUAGE plpgsql STABLE
AS $$
DECLARE
    raw TEXT := current_setting('autopartco.actor_account_id', true);
BEGIN
    IF raw ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$' THEN
        RETURN raw::uuid;
    END IF;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION public.app_actor_type(p_default TEXT)
RETURNS TEXT LANGUAGE sql STABLE
AS $$
    SELECT CASE WHEN current_setting('autopartco.actor_type', true)
                     IN ('CUSTOMER', 'STORE', 'PLATFORM', 'SYSTEM', 'PROVIDER')
                THEN current_setting('autopartco.actor_type', true)
                ELSE p_default END
$$;

-- -----------------------------------------------------------------------------
-- inventory: last_movement_at (BEFORE, sin UPDATE adicional)
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_inventory__touch_last_movement()
RETURNS TRIGGER LANGUAGE plpgsql
AS $$
BEGIN
    NEW.last_movement_at := clock_timestamp();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_inventory__touch_last_movement_ins
    BEFORE INSERT ON public.inventory FOR EACH ROW
    WHEN (NEW.stock > 0)
    EXECUTE FUNCTION public.tg_inventory__touch_last_movement();
CREATE OR REPLACE TRIGGER trg_inventory__touch_last_movement_upd
    BEFORE UPDATE OF stock ON public.inventory FOR EACH ROW
    WHEN (OLD.stock IS DISTINCT FROM NEW.stock)
    EXECUTE FUNCTION public.tg_inventory__touch_last_movement();

-- -----------------------------------------------------------------------------
-- inventory.stock -> inventory_movements
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_inventory__capture_movement()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    prev  INT := CASE WHEN TG_OP = 'INSERT' THEN 0 ELSE OLD.stock END;
    delta INT := NEW.stock - prev;
BEGIN
    IF public.app_capture_managed() OR delta = 0 THEN
        RETURN NULL;
    END IF;
    IF EXISTS (
        SELECT 1 FROM inventory_movements
        WHERE inventory_id = NEW.id AND previous_stock = prev AND new_stock = NEW.stock
          AND occurred_at = now()
    ) THEN
        RETURN NULL;
    END IF;

    INSERT INTO inventory_movements
        (inventory_id, movement_type, quantity, previous_stock, new_stock, reason_code,
         source, actor_account_id, occurred_at, notes)
    VALUES
        (NEW.id, CASE WHEN delta > 0 THEN 'IN' ELSE 'OUT' END, abs(delta), prev, NEW.stock,
         CASE WHEN TG_OP = 'INSERT' THEN 'INITIAL_LOAD' ELSE 'MANUAL_CORRECTION' END,
         'USER', public.app_actor_account_id(), clock_timestamp(), 'Capturado por trigger puente');
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_inventory__capture_movement_ins
    AFTER INSERT ON public.inventory FOR EACH ROW
    WHEN (NEW.stock > 0)
    EXECUTE FUNCTION public.tg_inventory__capture_movement();
CREATE OR REPLACE TRIGGER trg_inventory__capture_movement_upd
    AFTER UPDATE OF stock ON public.inventory FOR EACH ROW
    WHEN (OLD.stock IS DISTINCT FROM NEW.stock)
    EXECUTE FUNCTION public.tg_inventory__capture_movement();

-- -----------------------------------------------------------------------------
-- inventory.price / unit_cost -> inventory_price_history
-- valid_from/valid_to con clock_timestamp(): dos cambios en la misma
-- transacción no violan valid_to > valid_from.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_inventory__capture_price()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    open_row inventory_price_history%ROWTYPE;
    ts       TIMESTAMPTZ := clock_timestamp();
BEGIN
    IF public.app_capture_managed() THEN
        RETURN NULL;
    END IF;

    SELECT * INTO open_row FROM inventory_price_history
    WHERE inventory_id = NEW.id AND valid_to IS NULL
    FOR UPDATE;

    IF FOUND THEN
        IF open_row.list_price = NEW.price AND open_row.unit_cost IS NOT DISTINCT FROM NEW.unit_cost THEN
            RETURN NULL;  -- ya registrado (por el backend o sin cambio real)
        END IF;
        UPDATE inventory_price_history SET valid_to = greatest(ts, open_row.valid_from + interval '1 microsecond')
        WHERE id = open_row.id;
        ts := greatest(ts, open_row.valid_from + interval '1 microsecond');
    END IF;

    INSERT INTO inventory_price_history
        (inventory_id, store_id, branch_id, product_id, list_price, unit_cost, valid_from,
         change_source, changed_by_account_id, change_reason)
    SELECT NEW.id, b.store_id, NEW.branch_id, NEW.product_id, NEW.price, NEW.unit_cost, ts,
           CASE WHEN TG_OP = 'INSERT' THEN 'INITIAL_LOAD' ELSE 'MANUAL' END,
           public.app_actor_account_id(), 'Capturado por trigger puente'
    FROM branches b WHERE b.id = NEW.branch_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_inventory__capture_price_ins
    AFTER INSERT ON public.inventory FOR EACH ROW
    EXECUTE FUNCTION public.tg_inventory__capture_price();
CREATE OR REPLACE TRIGGER trg_inventory__capture_price_upd
    AFTER UPDATE OF price, unit_cost ON public.inventory FOR EACH ROW
    WHEN (OLD.price IS DISTINCT FROM NEW.price OR OLD.unit_cost IS DISTINCT FROM NEW.unit_cost)
    EXECUTE FUNCTION public.tg_inventory__capture_price();

-- -----------------------------------------------------------------------------
-- sales.state_id -> sale_status_history; marcas desnormalizadas
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_sales__stamp_status_times()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    new_code TEXT := (SELECT code FROM states WHERE id = NEW.state_id);
    old_code TEXT := (SELECT code FROM states WHERE id = OLD.state_id);
BEGIN
    IF new_code = 'CONFIRMED' AND NEW.confirmed_at IS NULL THEN
        NEW.confirmed_at := now();
    ELSIF new_code = 'CANCELLED' AND NEW.cancelled_at IS NULL THEN
        NEW.cancelled_at := now();
    END IF;
    IF old_code = 'PREPARING' AND new_code IN ('SHIPPED', 'DELIVERED') AND NEW.ready_at IS NULL THEN
        NEW.ready_at := now();
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_sales__stamp_status_times
    BEFORE UPDATE OF state_id ON public.sales FOR EACH ROW
    WHEN (OLD.state_id IS DISTINCT FROM NEW.state_id)
    EXECUTE FUNCTION public.tg_sales__stamp_status_times();

CREATE OR REPLACE FUNCTION public.tg_sales__capture_status()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    new_code TEXT := (SELECT code FROM states WHERE id = NEW.state_id);
    old_code TEXT := CASE WHEN TG_OP = 'UPDATE' THEN (SELECT code FROM states WHERE id = OLD.state_id) END;
BEGIN
    IF public.app_capture_managed() OR new_code IS NULL OR new_code IS NOT DISTINCT FROM old_code THEN
        RETURN NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM sale_status_history
               WHERE sale_id = NEW.id AND to_status = new_code AND changed_at = now()) THEN
        RETURN NULL;
    END IF;
    INSERT INTO sale_status_history (sale_id, store_id, from_status, to_status, actor_type, actor_account_id)
    SELECT NEW.id, b.store_id, old_code, new_code,
           public.app_actor_type(TG_ARGV[0]), public.app_actor_account_id()
    FROM branches b WHERE b.id = NEW.branch_id;
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_sales__capture_status_ins
    AFTER INSERT ON public.sales FOR EACH ROW
    EXECUTE FUNCTION public.tg_sales__capture_status('SYSTEM');
CREATE OR REPLACE TRIGGER trg_sales__capture_status_upd
    AFTER UPDATE OF state_id ON public.sales FOR EACH ROW
    WHEN (OLD.state_id IS DISTINCT FROM NEW.state_id)
    EXECUTE FUNCTION public.tg_sales__capture_status('STORE');

-- -----------------------------------------------------------------------------
-- orders.state_id -> order_status_history
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_orders__stamp_cancelled()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.cancelled_at IS NULL AND (SELECT code FROM states WHERE id = NEW.state_id) = 'CANCELLED' THEN
        NEW.cancelled_at := now();
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_orders__stamp_cancelled
    BEFORE UPDATE OF state_id ON public.orders FOR EACH ROW
    WHEN (OLD.state_id IS DISTINCT FROM NEW.state_id)
    EXECUTE FUNCTION public.tg_orders__stamp_cancelled();

CREATE OR REPLACE FUNCTION public.tg_orders__capture_status()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
DECLARE
    new_code TEXT := (SELECT code FROM states WHERE id = NEW.state_id);
    old_code TEXT := CASE WHEN TG_OP = 'UPDATE' THEN (SELECT code FROM states WHERE id = OLD.state_id) END;
BEGIN
    IF public.app_capture_managed() OR new_code IS NULL OR new_code IS NOT DISTINCT FROM old_code THEN
        RETURN NULL;
    END IF;
    IF EXISTS (SELECT 1 FROM order_status_history
               WHERE order_id = NEW.id AND to_status = new_code AND changed_at = now()) THEN
        RETURN NULL;
    END IF;
    INSERT INTO order_status_history (order_id, from_status, to_status, actor_type, actor_account_id)
    VALUES (NEW.id, old_code, new_code, public.app_actor_type('SYSTEM'), public.app_actor_account_id());
    RETURN NULL;
END;
$$;

CREATE OR REPLACE TRIGGER trg_orders__capture_status_ins
    AFTER INSERT ON public.orders FOR EACH ROW
    EXECUTE FUNCTION public.tg_orders__capture_status();
CREATE OR REPLACE TRIGGER trg_orders__capture_status_upd
    AFTER UPDATE OF state_id ON public.orders FOR EACH ROW
    WHEN (OLD.state_id IS DISTINCT FROM NEW.state_id)
    EXECUTE FUNCTION public.tg_orders__capture_status();

-- -----------------------------------------------------------------------------
-- shipment_events: estado anterior y origen
-- -----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION public.tg_shipment_events__fill_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.from_status_id IS NULL THEN
        SELECT e.shipment_status_id INTO NEW.from_status_id
        FROM shipment_events e
        WHERE e.shipment_id = NEW.shipment_id
        ORDER BY e.event_at DESC, e.created_at DESC
        LIMIT 1;
    END IF;
    IF NEW.source IS NULL THEN
        NEW.source := 'STORE';
    END IF;
    IF NEW.actor_account_id IS NULL THEN
        NEW.actor_account_id := public.app_actor_account_id();
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_shipment_events__fill_transition
    BEFORE INSERT ON public.shipment_events FOR EACH ROW
    EXECUTE FUNCTION public.tg_shipment_events__fill_transition();
