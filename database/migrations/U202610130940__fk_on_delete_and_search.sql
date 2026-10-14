-- =============================================================================
-- U202610130940__fk_on_delete_and_search.sql  (rollback de V202610130940)
-- 1. Quita search_vector y part_number_normalized (columnas derivadas, sin
--    pérdida de datos). Antes aplique U202610130942, que borra sus índices.
-- 2. Devuelve cada FK fk_<tabla>__<columna> creada por la V a ON DELETE NO
--    ACTION con el nombre por defecto de PostgreSQL (<tabla>_<columna>_fkey).
-- psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- =============================================================================

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.products
    DROP COLUMN IF EXISTS search_vector,
    DROP COLUMN IF EXISTS part_number_normalized;

DO $$
DECLARE
    fk RECORD;
BEGIN
    FOR fk IN
        SELECT c.conrelid::regclass AS tbl, c.conname, c.confrelid::regclass AS ref,
               a.attname AS col, ra.attname AS ref_col, t.relname AS table_name
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_namespace n ON n.oid = t.relnamespace
        JOIN pg_attribute a ON a.attrelid = c.conrelid AND a.attnum = c.conkey[1]
        JOIN pg_attribute ra ON ra.attrelid = c.confrelid AND ra.attnum = c.confkey[1]
        WHERE n.nspname = 'public' AND c.contype = 'f'
          AND c.conname = 'fk_' || t.relname || '__' || a.attname
          AND t.relname IN (
              'roles_collaborators', 'roles_permissions', 'branches_collaborators', 'products_compatibilies',
              'products_specifications', 'product_images', 'units_specifications', 'audit_logs',
              'seller_applications', 'claims', 'order_details', 'order_addresses', 'sales', 'payments',
              'payment_events', 'receipts', 'shipments', 'shipment_events', 'inventory',
              'inventory_movements', 'seller_payments', 'seller_application_documents')
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', fk.tbl, fk.conname);
        EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %s (%I)',
                       fk.tbl, fk.table_name || '_' || fk.col || '_fkey', fk.col, fk.ref, fk.ref_col);
    END LOOP;
END $$;
