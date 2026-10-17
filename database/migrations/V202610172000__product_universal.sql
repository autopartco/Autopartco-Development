-- =============================================================================
-- V202610172000__product_universal.sql
-- Autopartco | PostgreSQL 16+ | Registro de productos v2
--
-- Qué hace (fase EXPAND):
--   Añade public.products.is_universal BOOLEAN NOT NULL DEFAULT false.
--   Los productos existentes quedan en false (con compatibilidad por reglas).
--
-- Semántica: is_universal = true => el producto no depende del vehículo
--   (aceites, herramientas, accesorios) y NO debe tener filas activas en
--   product_vehicle_rules. Esa invariante entre tablas la valida el backend;
--   no se modela con CHECK (un CHECK no puede consultar otra tabla).
--
-- Nota: con PostgreSQL 11+ ADD COLUMN con DEFAULT constante no reescribe la tabla.
-- Transacción: SÍ. Rollback: U202610172000__product_universal.sql
-- Backend: Product.isUniversal (boolean, columna is_universal, NOT NULL).
-- =============================================================================

SET LOCAL lock_timeout = '30s';

ALTER TABLE public.products
    ADD COLUMN IF NOT EXISTS is_universal BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN public.products.is_universal IS
    'true = el producto no depende del vehículo y no debe tener reglas en product_vehicle_rules (lo valida el backend).';

-- Verificación (la ejecuta el usuario); debe dar 0 filas tras el backend v2:
--   SELECT p.id FROM public.products p
--   WHERE p.is_universal AND EXISTS (
--     SELECT 1 FROM public.product_vehicle_rules r
--     WHERE r.product_id = p.id AND r.deleted_at IS NULL);
