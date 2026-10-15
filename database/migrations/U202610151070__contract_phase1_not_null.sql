-- =============================================================================
-- U202610141500__contract_phase1_not_null.sql  (rollback de A5)
-- Vuelve a permitir NULL. Sin pérdida de datos.
-- =============================================================================

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.inventory_movements ALTER COLUMN reason_code DROP NOT NULL;
ALTER TABLE public.order_details ALTER COLUMN product_id DROP NOT NULL;
