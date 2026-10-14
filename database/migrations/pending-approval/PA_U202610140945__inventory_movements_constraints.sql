-- =============================================================================
-- U202610140945__inventory_movements_constraints.sql  (rollback de A4)
-- Quita las restricciones. Sin pérdida de datos.
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DROP TRIGGER IF EXISTS trg_inventory_movements__reason_direction ON public.inventory_movements;
DROP FUNCTION IF EXISTS public.tg_inventory_movements__reason_direction();
ALTER TABLE public.inventory_movements
    DROP CONSTRAINT IF EXISTS chk_inventory_movements__balance,
    DROP CONSTRAINT IF EXISTS chk_inventory_movements__type;
