-- =============================================================================
-- U202610171000__product_vehicle_rules.sql  (rollback de V202610171000)
-- DESTRUCTIVO: requiere aprobación. Elimina product_vehicle_rules y pierde las
-- reglas con «todos» (modelo/versión NULL) y las creadas por la app, que no
-- existen en product_compatibilities. Las compatibilidades originales siguen en
-- esa tabla, que V202610171000 no tocó. Respaldo previo del usuario: volcado de
-- la tabla public.product_vehicle_rules (formato custom) antes de ejecutar.
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.product_vehicle_rules;
