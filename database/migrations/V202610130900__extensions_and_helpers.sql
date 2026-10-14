-- =============================================================================
-- V202610130900__extensions_and_helpers.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito
--   1. Marcador de línea base: comprueba que database/init.sql ya se aplicó
--      (las migraciones parten de ese esquema; no lo recrean).
--   2. Extensiones necesarias para búsqueda: pg_trgm y unaccent.
--   3. Funciones auxiliares compartidas por las migraciones siguientes:
--      set_updated_at() (idempotente, mismo cuerpo que init.sql) e
--      immutable_unaccent(text) para columnas generadas e índices de expresión.
--
-- Transacción: SÍ (Flyway por defecto).
--   psql: psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- Bloqueos: ninguno sobre tablas de negocio.
-- Privilegios: CREATE EXTENSION requiere ser dueño de la BD o superusuario
--   (en Supabase, el rol postgres puede hacerlo).
-- Rollback: U202610130900__extensions_and_helpers.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

-- -----------------------------------------------------------------------------
-- 1. Marcador de línea base
-- -----------------------------------------------------------------------------
DO $$
DECLARE
    missing TEXT;
BEGIN
    SELECT string_agg(t, ', ')
    INTO missing
    FROM unnest(ARRAY[
        'states', 'accounts', 'persons', 'store', 'branches', 'products',
        'inventory', 'inventory_movements', 'sales', 'orders', 'order_details',
        'payments', 'shipments', 'shipment_events', 'audit_logs', 'seller_plans',
        'seller_applications', 'seller_payments', 'promotions'
    ]) AS t
    WHERE to_regclass(format('public.%I', t)) IS NULL;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION
            'Línea base ausente: faltan tablas (%). Aplique database/init.sql antes de las migraciones.',
            missing;
    END IF;
END $$;

-- -----------------------------------------------------------------------------
-- 2. Extensiones
-- -----------------------------------------------------------------------------
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- -----------------------------------------------------------------------------
-- 3. Funciones auxiliares
-- -----------------------------------------------------------------------------

-- Igual que en init.sql; se redefine para que las migraciones no dependan del
-- orden en que se aplicó la línea base.
CREATE OR REPLACE FUNCTION public.set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- unaccent() es STABLE (depende del diccionario), por lo que no se puede usar
-- en columnas generadas ni en índices. Este envoltorio fija el diccionario y se
-- declara IMMUTABLE (patrón habitual). Si el diccionario 'unaccent' cambiara,
-- habría que reconstruir products.search_vector y los índices que lo usan.
-- El esquema de la extensión se resuelve en tiempo de migración porque en
-- Supabase suele instalarse en "extensions" y no en "public".
DO $$
DECLARE
    ext_schema TEXT;
BEGIN
    SELECT n.nspname
    INTO ext_schema
    FROM pg_extension e
    JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'unaccent';

    EXECUTE format(
        $f$
        CREATE OR REPLACE FUNCTION public.immutable_unaccent(input TEXT)
        RETURNS TEXT
        LANGUAGE sql
        IMMUTABLE
        PARALLEL SAFE
        STRICT
        AS $body$ SELECT %1$I.unaccent(%2$L::regdictionary, input) $body$
        $f$,
        ext_schema,
        ext_schema || '.unaccent'
    );
END $$;

COMMENT ON FUNCTION public.immutable_unaccent(TEXT) IS
    'Envoltorio IMMUTABLE de unaccent con diccionario fijo; usado por products.search_vector e índices trigram.';
