-- flyway:executeInTransaction=false
-- REQUIERE APROBACIÓN
-- =============================================================================
-- U202610131025__soft_delete_partial_uniques.sql  (rollback)
-- Restaura los UNIQUE totales. Falla si ya existen duplicados entre filas
-- borradas y vigentes (consecuencia esperada de haber permitido la
-- reutilización). En ese caso, decida qué filas conservar antes de reintentar.
-- Transacción: NO.
-- =============================================================================

SET lock_timeout = '30s';
SET statement_timeout = 0;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_collaborator_person_store ON public.collaborators (person_id, collab_store_id);
ALTER TABLE public.collaborators ADD CONSTRAINT uq_collaborator_person_store UNIQUE USING INDEX uq_collaborator_person_store;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_role_store_code ON public.roles (store_id, code);
ALTER TABLE public.roles ADD CONSTRAINT uq_role_store_code UNIQUE USING INDEX uq_role_store_code;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_branch_store_name ON public.branches (store_id, name);
ALTER TABLE public.branches ADD CONSTRAINT uq_branch_store_name UNIQUE USING INDEX uq_branch_store_name;
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS shipments_tracking_number_key ON public.shipments (tracking_number);
ALTER TABLE public.shipments ADD CONSTRAINT shipments_tracking_number_key UNIQUE USING INDEX shipments_tracking_number_key;

DROP INDEX CONCURRENTLY IF EXISTS public.idx_collaborators__person;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_branches__store;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_roles__store;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_shipments__tracking_number;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_shipments__carrier_tracking;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_roles__store_code_active;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_collaborators__person_store_active;

RESET lock_timeout;
RESET statement_timeout;
