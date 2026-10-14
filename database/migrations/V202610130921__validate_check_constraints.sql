-- =============================================================================
-- V202610130921__validate_check_constraints.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: VALIDATE CONSTRAINT de los CHECK NOT VALID creados en
--   V202610130920. El diagnóstico de datos que incumplen ya se hizo en 0920,
--   antes de crear los CHECK. Desde entonces los CHECK protegen toda fila
--   nueva o modificada, así que aquí no debería fallar.
--   VALIDATE toma SHARE UPDATE EXCLUSIVE: no bloquea lecturas ni escrituras.
--
-- Transacción: SÍ.
-- Rollback: no aplica. Validar no cambia datos ni estructura; el U de 0920
--   elimina las restricciones.
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT c.conrelid::regclass AS tbl, c.conname
        FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE n.nspname = 'public' AND c.contype = 'c' AND NOT c.convalidated
          AND c.conname LIKE 'chk\_%\_\_%'
    LOOP
        EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', r.tbl, r.conname);
    END LOOP;
END $$;
