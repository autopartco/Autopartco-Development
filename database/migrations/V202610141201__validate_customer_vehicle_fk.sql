-- =============================================================================
-- V202610141201__validate_customer_vehicle_fk.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §6.1
--
-- Propósito: VALIDATE CONSTRAINT de fk_order_details__customer_vehicle_id,
--   creada NOT VALID en V202610141200. Va en una migración aparte para que el
--   recorrido de order_details no ocurra bajo el bloqueo exclusivo del
--   ADD CONSTRAINT (mismo patrón que 0920/0921 y 0940/0941).
--   Antes de validar se cuentan los huérfanos y se aborta con un mensaje claro.
--   VALIDATE toma SHARE UPDATE EXCLUSIVE: no bloquea lecturas ni escrituras.
--
-- Transacción: SÍ.
-- Rollback: no aplica. U202610141200 elimina la restricción.
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

DO $$
DECLARE
    orphans BIGINT;
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'public.order_details'::regclass
          AND conname = 'fk_order_details__customer_vehicle_id'
          AND NOT convalidated
    ) THEN
        RETURN;
    END IF;

    SELECT count(*) INTO orphans
    FROM public.order_details od
    WHERE od.customer_vehicle_id IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM public.customer_vehicles cv WHERE cv.id = od.customer_vehicle_id);
    IF orphans > 0 THEN
        RAISE EXCEPTION 'order_details.customer_vehicle_id: % filas huérfanas (no se validó la FK)', orphans;
    END IF;

    ALTER TABLE public.order_details VALIDATE CONSTRAINT fk_order_details__customer_vehicle_id;
END $$;
