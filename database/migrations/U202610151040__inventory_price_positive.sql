-- =============================================================================
-- U202610131030__inventory_price_positive.sql  (rollback)
-- Sin pérdida de datos.
-- =============================================================================

ALTER TABLE public.inventory DROP CONSTRAINT IF EXISTS chk_inventory__price_positive;
