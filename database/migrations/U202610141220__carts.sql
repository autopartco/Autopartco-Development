-- =============================================================================
-- U202610141220__carts.sql  (rollback de V202610141220)
-- DESTRUCTIVO: requiere aprobación (pierde carritos y señales de abandono).
-- Revierta antes U202610141230 (stock_reservations.cart_id).
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.cart_items;
DROP TABLE IF EXISTS public.carts;
