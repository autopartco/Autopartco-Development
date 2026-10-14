-- =============================================================================
-- U202610140940__inventory_ledger_reasons.sql  (rollback de V202610140940)
-- DESTRUCTIVO: requiere aprobación (borra columnas del ledger y del inventario
-- con datos: motivo, costo, reservas).
-- Respaldo: pg_dump --data-only -t public.inventory_movements -t public.inventory "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

SET LOCAL lock_timeout = '5s';

CREATE OR REPLACE TRIGGER trg_inventory_movements_append_only
    BEFORE UPDATE OR DELETE ON public.inventory_movements
    FOR EACH ROW EXECUTE FUNCTION public.forbid_update_delete();

ALTER TABLE public.inventory
    DROP CONSTRAINT IF EXISTS chk_inventory__reserved,
    DROP CONSTRAINT IF EXISTS chk_inventory__unit_cost,
    DROP COLUMN IF EXISTS last_counted_at,
    DROP COLUMN IF EXISTS last_movement_at,
    DROP COLUMN IF EXISTS unit_cost,
    DROP COLUMN IF EXISTS reserved_stock;

DROP INDEX IF EXISTS public.brin_inventory_movements__occurred_at;
DROP INDEX IF EXISTS public.idx_inventory_movements__actor;
DROP INDEX IF EXISTS public.idx_inventory_movements__reason;
DROP INDEX IF EXISTS public.idx_inventory_movements__reference;
DROP INDEX IF EXISTS public.idx_inventory_movements__inventory_occurred;
DROP INDEX IF EXISTS public.uq_inventory_movements__idempotency_key;

ALTER TABLE public.inventory_movements
    DROP COLUMN IF EXISTS notes,
    DROP COLUMN IF EXISTS idempotency_key,
    DROP COLUMN IF EXISTS occurred_at,
    DROP COLUMN IF EXISTS unit_cost,
    DROP COLUMN IF EXISTS reference_id,
    DROP COLUMN IF EXISTS reference_type,
    DROP COLUMN IF EXISTS actor_account_id,
    DROP COLUMN IF EXISTS source,
    DROP COLUMN IF EXISTS reason_code;

DROP TABLE IF EXISTS public.inventory_movement_reasons;
