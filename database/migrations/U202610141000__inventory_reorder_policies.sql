-- =============================================================================
-- U202610141000__inventory_reorder_policies.sql  (rollback de V202610141000)
-- DESTRUCTIVO: requiere aprobación (pierde el historial de políticas).
-- Revierta antes U202610141300 (FK a ml.model_versions) y U202610141330.
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS public.inventory_reorder_policies;
