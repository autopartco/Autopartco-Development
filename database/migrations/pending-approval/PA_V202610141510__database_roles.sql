-- REQUIERE APROBACIÓN (A6): cambio de seguridad
-- =============================================================================
-- V202610141510__database_roles.sql
-- Autopartco | PostgreSQL 16+ | Diseño predictivo §4 / ADR 0001 §3 / auditoría punto 10
--
-- Qué hace: roles de mínimo privilegio. Hoy la aplicación se conecta como
--   postgres (superusuario).
--   autopartco_migrator  dueño de los objetos; DDL (Flyway).
--   autopartco_app       DML en public; INSERT/SELECT en tracking; SELECT/INSERT/DELETE
--                        en privacy; SELECT + EXECUTE de funciones de refresco en
--                        analytics; SELECT/INSERT/UPDATE en ml. SIN UPDATE/DELETE/TRUNCATE
--                        en tablas de solo inserción (la barrera real, no la variable
--                        autopartco.allow_purge).
--   autopartco_analyst   SELECT en analytics, ml y tracking. Sin acceso a public ni privacy.
--   autopartco_ml        lo del analista + INSERT/UPDATE en ml.
--   Los roles se crean NOLOGIN y SIN contraseña: este archivo no contiene
--   credenciales. Para habilitar el inicio de sesión, el DBA ejecuta a mano
--   (fuera del control de versiones):
--     ALTER ROLE autopartco_app LOGIN PASSWORD '<secreto desde el gestor de secretos>';
--
-- Requisitos y orden:
--   1. La app solo puede usar autopartco_app después de pasar ddl-auto de
--      update a validate (ADR 0001 §2.4). Con update, Hibernate necesita DDL.
--   2. Los roles son objetos del clúster, no de la base: ejecútelo con un
--      superusuario (en Supabase, el rol postgres). No conviene que lo aplique
--      Flyway con el usuario de la aplicación.
--   3. Supabase: no añada tracking/analytics/ml/privacy a los esquemas
--      expuestos por la Data API (H6).
--   4. Traspaso de propiedad: descomente el bloque final solo cuando Flyway ya
--      se conecte como autopartco_migrator.
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo o
--   ejecútelo como script manual del DBA (recomendado).
-- Transacción: SÍ. Rollback: U202610141510__database_roles.sql
-- =============================================================================

DO $$
BEGIN
    IF coalesce(current_setting('autopartco.allow_destructive', true), '') <> 'on' THEN
        RAISE EXCEPTION 'REQUIERE APROBACIÓN (A6). Fije autopartco.allow_destructive = on.';
    END IF;
END $$;

DO $$
DECLARE
    r TEXT;
BEGIN
    FOREACH r IN ARRAY ARRAY['autopartco_migrator', 'autopartco_app', 'autopartco_analyst', 'autopartco_ml'] LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format('CREATE ROLE %I NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT', r);
        END IF;
    END LOOP;
END $$;

-- autopartco_ml hereda los permisos del analista.
ALTER ROLE autopartco_ml INHERIT;
GRANT autopartco_analyst TO autopartco_ml;

DO $$
BEGIN
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO autopartco_app, autopartco_analyst, autopartco_ml, autopartco_migrator',
                   current_database());
END $$;

-- public: la app opera; analista y ML no.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT USAGE ON SCHEMA public TO autopartco_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO autopartco_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO autopartco_app;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO autopartco_app;

-- Solo inserción: sin UPDATE/DELETE/TRUNCATE para la app. Las excepciones de
-- columna (outcome, valid_to, ip_address:null...) se conceden por columna.
REVOKE UPDATE, DELETE, TRUNCATE ON
    public.audit_logs, public.login_attempts, public.payment_events, public.shipment_events,
    public.inventory_movements, public.order_status_history, public.sale_status_history,
    public.return_status_history, public.person_consents, public.inventory_price_history,
    public.payment_risk_assessments, public.vehicle_odometer_readings
FROM autopartco_app;
GRANT UPDATE (valid_to) ON public.inventory_price_history TO autopartco_app;
GRANT UPDATE (outcome, outcome_at, outcome_source, reviewed_by_account_id, ip_address)
    ON public.payment_risk_assessments TO autopartco_app;
GRANT UPDATE (is_suspect) ON public.vehicle_odometer_readings TO autopartco_app;
GRANT UPDATE (ip_address, user_agent) ON public.person_consents TO autopartco_app;

-- tracking
GRANT USAGE ON SCHEMA tracking TO autopartco_app, autopartco_analyst;
GRANT SELECT, INSERT ON ALL TABLES IN SCHEMA tracking TO autopartco_app;
GRANT UPDATE ON tracking.sessions, tracking.identity_links TO autopartco_app;
GRANT DELETE ON tracking.identity_links TO autopartco_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA tracking TO autopartco_app;
GRANT SELECT ON ALL TABLES IN SCHEMA tracking TO autopartco_analyst;

-- privacy: solo la app
REVOKE ALL ON SCHEMA privacy FROM PUBLIC;
GRANT USAGE ON SCHEMA privacy TO autopartco_app;
GRANT SELECT, INSERT, DELETE ON ALL TABLES IN SCHEMA privacy TO autopartco_app;

-- analytics
GRANT USAGE ON SCHEMA analytics TO autopartco_app, autopartco_analyst;
GRANT SELECT ON ALL TABLES IN SCHEMA analytics TO autopartco_app, autopartco_analyst;
GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA analytics TO autopartco_app;

-- ml
GRANT USAGE ON SCHEMA ml TO autopartco_app, autopartco_analyst;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA ml TO autopartco_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA ml TO autopartco_app, autopartco_ml;
GRANT SELECT ON ALL TABLES IN SCHEMA ml TO autopartco_analyst;
GRANT INSERT, UPDATE ON ALL TABLES IN SCHEMA ml TO autopartco_ml;

-- Privilegios por defecto para objetos que cree el migrador en el futuro.
ALTER DEFAULT PRIVILEGES FOR ROLE autopartco_migrator IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO autopartco_app;
ALTER DEFAULT PRIVILEGES FOR ROLE autopartco_migrator IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO autopartco_app;
ALTER DEFAULT PRIVILEGES FOR ROLE autopartco_migrator IN SCHEMA tracking
    GRANT SELECT, INSERT ON TABLES TO autopartco_app;
ALTER DEFAULT PRIVILEGES FOR ROLE autopartco_migrator IN SCHEMA tracking
    GRANT SELECT ON TABLES TO autopartco_analyst;
ALTER DEFAULT PRIVILEGES FOR ROLE autopartco_migrator IN SCHEMA analytics
    GRANT SELECT ON TABLES TO autopartco_app, autopartco_analyst;
ALTER DEFAULT PRIVILEGES FOR ROLE autopartco_migrator IN SCHEMA ml
    GRANT SELECT ON TABLES TO autopartco_analyst;
ALTER DEFAULT PRIVILEGES FOR ROLE autopartco_migrator IN SCHEMA ml
    GRANT SELECT, INSERT, UPDATE ON TABLES TO autopartco_app;

-- Traspaso de propiedad (descomentar al aprobar el cambio de usuario de Flyway):
-- REASSIGN OWNED BY postgres TO autopartco_migrator;  -- NO en Supabase: postgres posee objetos del sistema.
-- En Supabase, use ALTER TABLE/SCHEMA ... OWNER TO autopartco_migrator objeto a objeto.
