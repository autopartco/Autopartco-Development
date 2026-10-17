-- =============================================================================
-- U202610172000__product_universal.sql  (rollback de V202610172000)
-- DESTRUCTIVO: requiere aprobación. Elimina products.is_universal y pierde qué
-- productos eran universales (tras el rollback quedarían sin reglas de vehículo).
-- Respaldo previo del usuario:
--   COPY (SELECT id FROM public.products WHERE is_universal) TO '<ruta>' CSV;
-- o volcado de la tabla public.products (formato custom) antes de ejecutar.
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

ALTER TABLE public.products DROP COLUMN IF EXISTS is_universal;
