-- =============================================================================
-- V202610130941__validate_foreign_keys.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: validar las FK recreadas NOT VALID en V202610130940.
--   VALIDATE CONSTRAINT toma SHARE UPDATE EXCLUSIVE en la tabla hija y ROW
--   SHARE en la padre: no bloquea escrituras. Antes de validar, informa de las
--   filas huérfanas y aborta si las hay.
-- Huérfanos (ejemplo): SELECT od.* FROM order_details od
--   LEFT JOIN sales s ON s.id = od.sale_id WHERE s.id IS NULL;
--
-- Transacción: SÍ.
-- Rollback: sin acción (validar no cambia nada; ver U202610130940).
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

DO $$
DECLARE
    fk       RECORD;
    orphans  BIGINT;
    report   TEXT := '';
BEGIN
    FOR fk IN
        SELECT c.conrelid::regclass AS tbl, c.conname, c.confrelid::regclass AS ref,
               a.attname AS col, ra.attname AS ref_col
        FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        JOIN pg_attribute ra ON ra.attrelid = c.confrelid AND ra.attnum = c.confkey[1]
        WHERE n.nspname = 'public' AND c.contype = 'f' AND NOT c.convalidated
          AND c.conname LIKE 'fk\_%\_\_%'
    LOOP
        EXECUTE format(
            'SELECT count(*) FROM %s ch WHERE ch.%I IS NOT NULL AND NOT EXISTS (SELECT 1 FROM %s p WHERE p.%I = ch.%I)',
            fk.tbl, fk.col, fk.ref, fk.ref_col, fk.col
        ) INTO orphans;
        IF orphans > 0 THEN
            report := report || format(E'\n  %s (%s.%s): %s huérfanos', fk.conname, fk.tbl, fk.col, orphans);
        END IF;
    END LOOP;

    IF report <> '' THEN
        RAISE EXCEPTION 'FK con filas huérfanas (no se validó nada):%', report;
    END IF;

    FOR fk IN
        SELECT c.conrelid::regclass AS tbl, c.conname
        FROM pg_constraint c
        JOIN pg_namespace n ON n.oid = c.connamespace
        WHERE n.nspname = 'public' AND c.contype = 'f' AND NOT c.convalidated
          AND c.conname LIKE 'fk\_%\_\_%'
    LOOP
        EXECUTE format('ALTER TABLE %s VALIDATE CONSTRAINT %I', fk.tbl, fk.conname);
    END LOOP;
END $$;
