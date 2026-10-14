-- REQUIERE APROBACIÓN + cambio de backend previo
-- =============================================================================
-- V202610131030__inventory_price_positive.sql
-- Autopartco | PostgreSQL 16+ | Auditoría punto 18
--
-- Qué hace: CHECK (price > 0) en inventory. Hoy existe chk_inventory_price
--   (price >= 0).
-- Por qué no está en el flujo normal: el backend ACEPTA precio 0
--   (ProductCreateRequest/ProductUpdateRequest: @DecimalMin("0.0")). Con este
--   CHECK, esas peticiones fallarían con 500 en lugar de 400.
-- Backend antes de aplicar: @DecimalMin(value = "0.0", inclusive = false) en
--   price (o @Positive), y decidir qué hacer con los listados existentes en 0.
-- Detección: SELECT id, branch_id, product_id FROM inventory WHERE price <= 0 AND deleted_at IS NULL;
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo.
-- Transacción: SÍ. Rollback: U202610131030__inventory_price_positive.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.inventory WHERE price <= 0 AND deleted_at IS NULL) THEN
        RAISE EXCEPTION 'Hay listados vigentes con precio <= 0; corríjalos antes.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_inventory__price_positive'
                   AND conrelid = 'public.inventory'::regclass) THEN
        -- Solo filas vigentes: los listados borrados conservan su precio histórico.
        ALTER TABLE public.inventory ADD CONSTRAINT chk_inventory__price_positive
            CHECK (deleted_at IS NOT NULL OR price > 0) NOT VALID;
    END IF;
END $$;

ALTER TABLE public.inventory VALIDATE CONSTRAINT chk_inventory__price_positive;
