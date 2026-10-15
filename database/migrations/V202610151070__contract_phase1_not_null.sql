-- =============================================================================
-- V202610141500__contract_phase1_not_null.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §10.15 / §11 A5
--
-- Qué hace: inventory_movements.reason_code y order_details.product_id pasan
--   a NOT NULL. Patrón sin recorrido bajo bloqueo exclusivo:
--   CHECK (col IS NOT NULL) NOT VALID -> VALIDATE (sin bloquear escrituras) ->
--   SET NOT NULL (PG 12+ usa el CHECK validado y no vuelve a recorrer la tabla)
--   -> DROP CHECK.
-- Precondición (aborta si no se cumple): no quedan nulos.
--   SELECT count(*) FROM inventory_movements WHERE reason_code IS NULL;
--   SELECT count(*) FROM order_details WHERE product_id IS NULL;
-- Backend: todo INSERT en inventory_movements (StockLedger) debe enviar
--   reason_code, y el checkout debe enviar order_details.product_id.
-- Respaldo: pg_dump --schema-only "$DATABASE_URL" > schema_pre_a5.sql
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo.
-- Transacción: SÍ. Rollback: U202610141500__contract_phase1_not_null.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.inventory_movements WHERE reason_code IS NULL) THEN
        RAISE EXCEPTION 'Hay inventory_movements sin reason_code; asigne motivo antes de la contracción.';
    END IF;
    IF EXISTS (SELECT 1 FROM public.order_details WHERE product_id IS NULL) THEN
        RAISE EXCEPTION 'Hay order_details sin product_id; ejecute el relleno (V202610141021) antes.';
    END IF;
END $$;

ALTER TABLE public.inventory_movements
    ADD CONSTRAINT chk_inventory_movements__reason_code_nn CHECK (reason_code IS NOT NULL) NOT VALID;
ALTER TABLE public.inventory_movements VALIDATE CONSTRAINT chk_inventory_movements__reason_code_nn;
ALTER TABLE public.inventory_movements ALTER COLUMN reason_code SET NOT NULL;
ALTER TABLE public.inventory_movements DROP CONSTRAINT chk_inventory_movements__reason_code_nn;

ALTER TABLE public.order_details
    ADD CONSTRAINT chk_order_details__product_id_nn CHECK (product_id IS NOT NULL) NOT VALID;
ALTER TABLE public.order_details VALIDATE CONSTRAINT chk_order_details__product_id_nn;
ALTER TABLE public.order_details ALTER COLUMN product_id SET NOT NULL;
ALTER TABLE public.order_details DROP CONSTRAINT chk_order_details__product_id_nn;
