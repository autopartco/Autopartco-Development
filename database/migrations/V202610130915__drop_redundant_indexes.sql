-- flyway:executeInTransaction=false
-- =============================================================================
-- V202610130915__drop_redundant_indexes.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: eliminar índices redundantes de init.sql. Cada uno está cubierto
-- por otro índice cuya columna inicial es la misma, o fue sustituido en 0910.
-- Verificación uno a uno:
--   idx_branches_store (store_id)           <- uq_branch_store_name (store_id, name)
--   idx_model_make (make_id)                <- uq_model_make_name (make_id, name)
--   idx_versions_model (model_id)           <- uq_version_model_name (model_id, version)
--   idx_compatibility_vehicle (make,model,version) == uq_compatibility (idéntico)
--   idx_inventory_branch (branch_id)        <- uq_inventory_branch_product (branch_id, product_id)
--   idx_sales_order (order_id)              <- uq_sale_order_branch (order_id, branch_id)
--   idx_shipments_tracking (tracking_number) == UNIQUE(tracking_number) (shipments_tracking_number_key)
--   idx_reviews_store (store_id)            <- uq_review_person_store_order (store_id, person_id, order_id)
--   idx_inventory_price (price)             sin consulta que filtre solo por precio;
--                                           sustituido por idx_inventory__product_price_in_stock
--   idx_payments_order (order_id)           <- idx_payments__order_created (order_id, created_at DESC)
-- Se CONSERVA idx_sales_branch: el nuevo idx_sales__branch_created es parcial
-- (deleted_at IS NULL) y no cubre la comprobación de FK sobre filas borradas.
--
-- El bloque inicial verifica que cada índice que cubre exista y sea válido.
-- Si falta alguno, aborta sin borrar nada.
--
-- Transacción: NO (DROP INDEX CONCURRENTLY). Flyway: .sql.conf.
--   psql: psql -v ON_ERROR_STOP=1 -f <archivo>
-- Rollback: U202610130915__drop_redundant_indexes.sql (recrea los índices)
-- =============================================================================

SET lock_timeout = '30s';

DO $$
DECLARE
    missing TEXT;
BEGIN
    -- Índices de cobertura (por nombre o por la restricción que los respalda).
    WITH required(covering) AS (
        VALUES ('uq_branch_store_name'), ('uq_model_make_name'), ('uq_version_model_name'),
               ('uq_compatibility'), ('uq_inventory_branch_product'), ('uq_sale_order_branch'),
               ('uq_review_person_store_order'), ('idx_inventory__product_price_in_stock'),
               ('idx_payments__order_created')
    )
    SELECT string_agg(r.covering, ', ')
    INTO missing
    FROM required r
    WHERE NOT EXISTS (
        SELECT 1
        FROM pg_class c
        JOIN pg_index i ON i.indexrelid = c.oid
        JOIN pg_namespace n ON n.oid = c.relnamespace
        WHERE n.nspname = 'public' AND c.relname = r.covering AND i.indisvalid
    );

    -- UNIQUE de shipments.tracking_number (nombre generado por PostgreSQL o Hibernate).
    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
        WHERE i.indrelid = 'public.shipments'::regclass
          AND i.indisunique AND i.indisvalid AND i.indnatts = 1
          AND a.attname = 'tracking_number'
    ) THEN
        missing := concat_ws(', ', missing, 'UNIQUE(shipments.tracking_number)');
    END IF;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan índices de cobertura: %. No se elimina nada.', missing;
    END IF;
END $$;

DROP INDEX CONCURRENTLY IF EXISTS public.idx_branches_store;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_model_make;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_versions_model;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_compatibility_vehicle;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_inventory_branch;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_sales_order;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_shipments_tracking;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_reviews_store;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_inventory_price;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_payments_order;

RESET lock_timeout;
