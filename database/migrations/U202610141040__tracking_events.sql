-- =============================================================================
-- U202610141040__tracking_events.sql  (rollback de V202610141040)
-- DESTRUCTIVO: requiere aprobación (los eventos de comportamiento no se
-- pueden reconstruir). Revierta antes U202610141100 y U202610141320, que leen
-- de tracking.
-- Respaldo: pg_dump --data-only -n tracking "$DATABASE_URL" > tracking.sql
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'DESTRUCTIVO: requiere aprobación. Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DROP TABLE IF EXISTS tracking.identity_links;
DROP TABLE IF EXISTS tracking.events;
DROP TABLE IF EXISTS tracking.product_events;
DROP TABLE IF EXISTS tracking.search_events;
DROP TABLE IF EXISTS tracking.sessions;
DROP TABLE IF EXISTS tracking.event_definitions;
