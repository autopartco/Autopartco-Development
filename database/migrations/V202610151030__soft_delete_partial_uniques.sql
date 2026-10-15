-- flyway:executeInTransaction=false
-- =============================================================================
-- V202610131025__soft_delete_partial_uniques.sql
-- Autopartco | PostgreSQL 16+ | Auditoría puntos 6 y 21
--
-- Problema: varios UNIQUE totales chocan con el borrado lógico. El backend
--   comprueba la existencia con @SQLRestriction (deleted_at IS NULL), da el
--   alta por buena y la BD responde 500 por la fila borrada:
--   - collaborators (person_id, collab_store_id): no se puede volver a invitar
--     a un colaborador retirado.
--   - branches (store_id, name): no se puede reutilizar el nombre de una
--     sucursal borrada (ya existe uq_branches__store_name_lower parcial).
--   - roles (store_id, code).
--   Además, shipments.tracking_number es único global, cuando debería serlo
--   por transportadora: (carrier, tracking_number).
-- Qué hace: crea los índices únicos parciales o compuestos (CONCURRENTLY),
--   verifica que son válidos y elimina las restricciones antiguas. Añade
--   idx_shipments__tracking_number para las búsquedas por número de guía.
-- Detección previa (debe dar 0 filas):
--   SELECT person_id, collab_store_id, count(*) FROM collaborators WHERE deleted_at IS NULL GROUP BY 1,2 HAVING count(*) > 1;
--   SELECT store_id, code, count(*) FROM roles WHERE deleted_at IS NULL GROUP BY 1,2 HAVING count(*) > 1;
-- Backend: sin cambios obligatorios. Si alguna entidad declara estas UNIQUE
--   en @Table(uniqueConstraints), ddl-auto: update las recrearía: aplique con
--   ddl-auto: validate.
-- Transacción: NO (CONCURRENTLY). Flyway: .sql.conf con executeInTransaction=false.
-- Respaldo: pg_dump --schema-only "$DATABASE_URL" > schema_pre_partial_uniques.sql
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo.
-- Rollback: U202610131025__soft_delete_partial_uniques.sql
-- =============================================================================

SET lock_timeout = '30s';
SET statement_timeout = 0;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_collaborators__person_store_active
    ON public.collaborators (person_id, collab_store_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_roles__store_code_active
    ON public.roles (store_id, code) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_shipments__carrier_tracking
    ON public.shipments (carrier, tracking_number);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipments__tracking_number
    ON public.shipments (tracking_number);
-- Índices de FK: los UNIQUE totales que se eliminan cubrían las FK (V202610130915
-- quitó idx_branches_store porque uq_branch_store_name lo cubría). Los parciales
-- no cubren las filas borradas.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collaborators__person
    ON public.collaborators (person_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_branches__store
    ON public.branches (store_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_roles__store
    ON public.roles (store_id);

DO $$
DECLARE
    missing TEXT;
    r RECORD;
BEGIN
    SELECT string_agg(n, ', ') INTO missing
    FROM unnest(ARRAY['uq_collaborators__person_store_active', 'uq_roles__store_code_active',
                      'uq_shipments__carrier_tracking', 'uq_branches__store_name_lower']) AS n
    WHERE NOT EXISTS (SELECT 1 FROM pg_class c JOIN pg_index i ON i.indexrelid = c.oid
                      WHERE c.relname = n AND i.indisvalid);
    IF missing IS NOT NULL THEN
        RAISE EXCEPTION 'Índices no válidos o ausentes: %. No se elimina ninguna restricción.', missing;
    END IF;

    ALTER TABLE public.collaborators DROP CONSTRAINT IF EXISTS uq_collaborator_person_store;
    ALTER TABLE public.roles DROP CONSTRAINT IF EXISTS uq_role_store_code;
    ALTER TABLE public.branches DROP CONSTRAINT IF EXISTS uq_branch_store_name;

    FOR r IN
        SELECT con.conname
        FROM pg_constraint con
        JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = con.conkey[1]
        WHERE con.conrelid = 'public.shipments'::regclass AND con.contype = 'u'
          AND array_length(con.conkey, 1) = 1 AND a.attname = 'tracking_number'
    LOOP
        EXECUTE format('ALTER TABLE public.shipments DROP CONSTRAINT %I', r.conname);
    END LOOP;
END $$;

RESET lock_timeout;
RESET statement_timeout;
