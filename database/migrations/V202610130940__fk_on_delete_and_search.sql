-- =============================================================================
-- V202610130940__fk_on_delete_and_search.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito
--   1. ON DELETE explícito en las FK (init.sql usa el NO ACTION implícito):
--      - CASCADE en tablas puente y dependientes puros (roles_collaborators,
--        roles_permissions, branches_collaborators, products_compatibilies,
--        products_specifications, product_images, units_specifications).
--      - SET NULL en referencias opcionales a actores u origen
--        (audit_logs.account_id, seller_applications.reviewed_by,
--        claims.order_id, claims.store_id).
--      - RESTRICT en finanzas, pedidos, inventario y suscripciones.
--      Las FK se recrean con nombre fk_<tabla>__<columna> y NOT VALID. Se
--      validan en V202610130941 sin bloquear escrituras. Se eliminan TODAS las
--      FK equivalentes sobre esa columna, incluidas las que haya creado
--      Hibernate con ddl-auto (FKxxxx), para no dejar duplicados.
--      Cambio semántico acotado: hoy casi todo es borrado lógico. CASCADE solo
--      actúa ante un DELETE físico del padre.
--   2. Búsqueda (contrato futuro GET /marketplace/products?q=):
--      - products.search_vector tsvector GENERATED (nombre A, part_number/sku
--        A con configuración 'simple', descripción C; sin acentos).
--      - products.part_number_normalized GENERATED: mayúsculas sin separadores
--        ("12-345 a" -> "12345A").
--      Los índices GIN/trigram van en V202610130942 (CONCURRENTLY).
--      Product.java no mapea estas columnas. Hibernate las ignora en INSERT,
--      UPDATE y en validate.
--
-- Transacción: SÍ.
-- Bloqueos: ADD COLUMN ... GENERATED STORED REESCRIBE products bajo ACCESS
--   EXCLUSIVE (lecturas y escrituras bloqueadas mientras dura). Hágalo en una
--   ventana de baja carga. Cada FK toma SHARE ROW EXCLUSIVE en ambas tablas
--   (breve, sin recorrido gracias a NOT VALID).
-- Rollback: U202610130940__fk_on_delete_and_search.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '30min';

CREATE OR REPLACE FUNCTION pg_temp.replace_fk(
    p_table regclass, p_column TEXT, p_ref regclass, p_ref_column TEXT, p_action TEXT, p_name TEXT
)
RETURNS void
LANGUAGE plpgsql
AS $$
DECLARE
    attnum_col SMALLINT;
    wanted     "char" := CASE p_action WHEN 'CASCADE' THEN 'c' WHEN 'SET NULL' THEN 'n'
                                       WHEN 'RESTRICT' THEN 'r' ELSE 'a' END;
    fk         RECORD;
BEGIN
    SELECT attnum INTO attnum_col FROM pg_attribute
    WHERE attrelid = p_table AND attname = p_column AND NOT attisdropped;

    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = p_table AND conname = p_name AND contype = 'f'
          AND confrelid = p_ref AND conkey = ARRAY[attnum_col] AND confdeltype = wanted
    ) AND (
        SELECT count(*) FROM pg_constraint
        WHERE conrelid = p_table AND contype = 'f' AND confrelid = p_ref AND conkey = ARRAY[attnum_col]
    ) = 1 THEN
        RETURN;  -- ya está como se quiere
    END IF;

    FOR fk IN
        SELECT conname FROM pg_constraint
        WHERE conrelid = p_table AND contype = 'f' AND confrelid = p_ref AND conkey = ARRAY[attnum_col]
    LOOP
        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', p_table, fk.conname);
    END LOOP;

    EXECUTE format(
        'ALTER TABLE %s ADD CONSTRAINT %I FOREIGN KEY (%I) REFERENCES %s (%I) ON DELETE %s NOT VALID',
        p_table, p_name, p_column, p_ref, p_ref_column, p_action
    );
END;
$$;

-- 1a. CASCADE ---------------------------------------------------------------------
SELECT pg_temp.replace_fk('public.roles_collaborators', 'collaborator_id', 'public.collaborators', 'id', 'CASCADE', 'fk_roles_collaborators__collaborator_id');
SELECT pg_temp.replace_fk('public.roles_collaborators', 'role_id', 'public.roles', 'id', 'CASCADE', 'fk_roles_collaborators__role_id');
SELECT pg_temp.replace_fk('public.roles_permissions', 'role_id', 'public.roles', 'id', 'CASCADE', 'fk_roles_permissions__role_id');
SELECT pg_temp.replace_fk('public.roles_permissions', 'permission_id', 'public.permissions', 'id', 'CASCADE', 'fk_roles_permissions__permission_id');
SELECT pg_temp.replace_fk('public.branches_collaborators', 'collaborator_id', 'public.collaborators', 'id', 'CASCADE', 'fk_branches_collaborators__collaborator_id');
SELECT pg_temp.replace_fk('public.branches_collaborators', 'branch_id', 'public.branches', 'id', 'CASCADE', 'fk_branches_collaborators__branch_id');
SELECT pg_temp.replace_fk('public.products_compatibilies', 'product_id', 'public.products', 'id', 'CASCADE', 'fk_products_compatibilies__product_id');
SELECT pg_temp.replace_fk('public.products_compatibilies', 'compatibility_id', 'public.compatibilities', 'id', 'CASCADE', 'fk_products_compatibilies__compatibility_id');
SELECT pg_temp.replace_fk('public.products_specifications', 'product_id', 'public.products', 'id', 'CASCADE', 'fk_products_specifications__product_id');
SELECT pg_temp.replace_fk('public.product_images', 'product_id', 'public.products', 'id', 'CASCADE', 'fk_product_images__product_id');
SELECT pg_temp.replace_fk('public.units_specifications', 'specification_id', 'public.specifications', 'id', 'CASCADE', 'fk_units_specifications__specification_id');
SELECT pg_temp.replace_fk('public.units_specifications', 'unit_id', 'public.units', 'id', 'CASCADE', 'fk_units_specifications__unit_id');

-- 1b. SET NULL --------------------------------------------------------------------
SELECT pg_temp.replace_fk('public.audit_logs', 'account_id', 'public.accounts', 'id', 'SET NULL', 'fk_audit_logs__account_id');
SELECT pg_temp.replace_fk('public.seller_applications', 'reviewed_by', 'public.accounts', 'id', 'SET NULL', 'fk_seller_applications__reviewed_by');
SELECT pg_temp.replace_fk('public.claims', 'order_id', 'public.orders', 'id', 'SET NULL', 'fk_claims__order_id');
SELECT pg_temp.replace_fk('public.claims', 'store_id', 'public.store', 'id', 'SET NULL', 'fk_claims__store_id');

-- 1c. RESTRICT --------------------------------------------------------------------
SELECT pg_temp.replace_fk('public.order_details', 'order_id', 'public.orders', 'id', 'RESTRICT', 'fk_order_details__order_id');
SELECT pg_temp.replace_fk('public.order_details', 'sale_id', 'public.sales', 'id', 'RESTRICT', 'fk_order_details__sale_id');
SELECT pg_temp.replace_fk('public.order_details', 'inventory_id', 'public.inventory', 'id', 'RESTRICT', 'fk_order_details__inventory_id');
SELECT pg_temp.replace_fk('public.order_addresses', 'order_id', 'public.orders', 'id', 'RESTRICT', 'fk_order_addresses__order_id');
SELECT pg_temp.replace_fk('public.sales', 'order_id', 'public.orders', 'id', 'RESTRICT', 'fk_sales__order_id');
SELECT pg_temp.replace_fk('public.sales', 'branch_id', 'public.branches', 'id', 'RESTRICT', 'fk_sales__branch_id');
SELECT pg_temp.replace_fk('public.payments', 'order_id', 'public.orders', 'id', 'RESTRICT', 'fk_payments__order_id');
SELECT pg_temp.replace_fk('public.payment_events', 'payment_id', 'public.payments', 'id', 'RESTRICT', 'fk_payment_events__payment_id');
SELECT pg_temp.replace_fk('public.receipts', 'order_id', 'public.orders', 'id', 'RESTRICT', 'fk_receipts__order_id');
SELECT pg_temp.replace_fk('public.receipts', 'payment_id', 'public.payments', 'id', 'RESTRICT', 'fk_receipts__payment_id');
SELECT pg_temp.replace_fk('public.shipments', 'sale_id', 'public.sales', 'id', 'RESTRICT', 'fk_shipments__sale_id');
SELECT pg_temp.replace_fk('public.shipment_events', 'shipment_id', 'public.shipments', 'id', 'RESTRICT', 'fk_shipment_events__shipment_id');
SELECT pg_temp.replace_fk('public.inventory', 'branch_id', 'public.branches', 'id', 'RESTRICT', 'fk_inventory__branch_id');
SELECT pg_temp.replace_fk('public.inventory', 'product_id', 'public.products', 'id', 'RESTRICT', 'fk_inventory__product_id');
SELECT pg_temp.replace_fk('public.inventory_movements', 'inventory_id', 'public.inventory', 'id', 'RESTRICT', 'fk_inventory_movements__inventory_id');
SELECT pg_temp.replace_fk('public.inventory_movements', 'order_detail_id', 'public.order_details', 'id', 'RESTRICT', 'fk_inventory_movements__order_detail_id');
SELECT pg_temp.replace_fk('public.seller_payments', 'application_id', 'public.seller_applications', 'id', 'RESTRICT', 'fk_seller_payments__application_id');
SELECT pg_temp.replace_fk('public.seller_payments', 'plan_id', 'public.seller_plans', 'id', 'RESTRICT', 'fk_seller_payments__plan_id');
SELECT pg_temp.replace_fk('public.seller_applications', 'plan_id', 'public.seller_plans', 'id', 'RESTRICT', 'fk_seller_applications__plan_id');
SELECT pg_temp.replace_fk('public.seller_applications', 'selected_plan_id', 'public.seller_plans', 'id', 'RESTRICT', 'fk_seller_applications__selected_plan_id');
SELECT pg_temp.replace_fk('public.seller_applications', 'store_id', 'public.store', 'id', 'RESTRICT', 'fk_seller_applications__store_id');
SELECT pg_temp.replace_fk('public.seller_application_documents', 'application_id', 'public.seller_applications', 'id', 'RESTRICT', 'fk_seller_application_documents__application_id');

DROP FUNCTION pg_temp.replace_fk(regclass, TEXT, regclass, TEXT, TEXT, TEXT);

-- 2. Búsqueda ---------------------------------------------------------------------
-- Un solo ALTER: una sola reescritura de la tabla.
ALTER TABLE public.products
    ADD COLUMN IF NOT EXISTS search_vector tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('spanish'::regconfig, public.immutable_unaccent(coalesce(name, ''))), 'A')
        || setweight(to_tsvector('simple'::regconfig,
               public.immutable_unaccent(coalesce(part_number, '') || ' ' || coalesce(sku, ''))), 'A')
        || setweight(to_tsvector('spanish'::regconfig,
               public.immutable_unaccent(left(coalesce(description, ''), 20000))), 'C')
    ) STORED,
    ADD COLUMN IF NOT EXISTS part_number_normalized VARCHAR(50) GENERATED ALWAYS AS (
        nullif(upper(regexp_replace(part_number, '[^A-Za-z0-9]', '', 'g')), '')
    ) STORED;

COMMENT ON COLUMN public.products.search_vector IS
    'Búsqueda de texto completo: nombre (A), part_number y sku (A, simple), descripción (C). Sin acentos.';
COMMENT ON COLUMN public.products.part_number_normalized IS
    'part_number en mayúsculas y sin separadores, para coincidencia exacta y trigram.';
