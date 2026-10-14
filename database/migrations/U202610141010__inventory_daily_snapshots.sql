-- =============================================================================
-- U202610141010__inventory_daily_snapshots.sql  (rollback de V202610141010)
-- DESTRUCTIVO: requiere aprobación (los snapshots pasados no se pueden
-- regenerar: el estado diario del inventario se pierde).
-- Respaldo: pg_dump --data-only -t 'public.inventory_daily_snapshots*' "$DATABASE_URL"
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

-- Borra la tabla padre y todas sus particiones.
DROP TABLE IF EXISTS public.inventory_daily_snapshots;
