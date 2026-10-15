-- =============================================================================
-- V202610140945__inventory_movements_constraints.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §5.3 / §11 A4
--
-- Qué hace: restricciones nuevas sobre una tabla existente.
--   chk_inventory_movements__type    movement_type IN ('IN','OUT')
--   chk_inventory_movements__balance new_stock = previous_stock ± quantity
--   chk_inventory_movements__reason_direction (trigger): la dirección del
--     movimiento es compatible con inventory_movement_reasons.direction.
-- Datos existentes: el saldo inicial (V202610140941) y el trigger puente
--   (V202610141025) ya escriben IN/OUT con balance correcto. El diagnóstico
--   aborta si hay filas que incumplan.
-- Backend: StockLedger.record debe escribir movement_type IN/OUT y un motivo
--   coherente con la dirección.
-- Respaldo: pg_dump --data-only -t public.inventory_movements "$DATABASE_URL" > im_backup.sql
-- Al aprobar: muévalo a database/migrations/ con un timestamp NUEVO, posterior
--   a la última migración aplicada (Flyway rechaza versiones antiguas con
--   outOfOrder=false).
-- Transacción: SÍ. Rollback: U202610140945__inventory_movements_constraints.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

DO $$
DECLARE
    bad BIGINT;
BEGIN
    SELECT count(*) INTO bad FROM public.inventory_movements
    WHERE movement_type NOT IN ('IN', 'OUT')
       OR new_stock <> previous_stock + CASE movement_type WHEN 'IN' THEN quantity ELSE -quantity END;
    IF bad > 0 THEN
        RAISE EXCEPTION '% movimientos incumplen tipo/balance. Revíselos antes de aprobar.', bad;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_movements__type'
                   AND conrelid = 'public.inventory_movements'::regclass) THEN
        ALTER TABLE public.inventory_movements ADD CONSTRAINT chk_inventory_movements__type
            CHECK (movement_type IN ('IN', 'OUT')) NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory_movements__balance'
                   AND conrelid = 'public.inventory_movements'::regclass) THEN
        ALTER TABLE public.inventory_movements ADD CONSTRAINT chk_inventory_movements__balance
            CHECK (new_stock = previous_stock + CASE movement_type WHEN 'IN' THEN quantity ELSE -quantity END) NOT VALID;
    END IF;
END $$;

ALTER TABLE public.inventory_movements VALIDATE CONSTRAINT chk_inventory_movements__type;
ALTER TABLE public.inventory_movements VALIDATE CONSTRAINT chk_inventory_movements__balance;

CREATE OR REPLACE FUNCTION public.tg_inventory_movements__reason_direction()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
BEGIN
    IF NEW.reason_code IS NOT NULL AND NOT EXISTS (
        SELECT 1 FROM inventory_movement_reasons r
        WHERE r.code = NEW.reason_code AND r.active AND r.direction IN ('ANY', NEW.movement_type)
    ) THEN
        RAISE EXCEPTION 'El motivo % no admite movimientos %', NEW.reason_code, NEW.movement_type
            USING ERRCODE = '23514', CONSTRAINT = 'chk_inventory_movements__reason_direction';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_inventory_movements__reason_direction
    BEFORE INSERT ON public.inventory_movements
    FOR EACH ROW EXECUTE FUNCTION public.tg_inventory_movements__reason_direction();
