
-- ============================================================
-- AUTOPARTCO - DATABASE SCHEMA
-- PostgreSQL
-- ============================================================

BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ============================================================
-- CATALOGOS
-- ============================================================

CREATE TABLE IF NOT EXISTS states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS types_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS categories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS manufacturers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    logo_id UUID
);

CREATE TABLE IF NOT EXISTS specifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS units (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    code VARCHAR(30) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- ============================================================
-- UBICACIONES
-- Preparada para Google Maps / OpenStreetMap
-- ============================================================

CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    address_line VARCHAR(255) NOT NULL,
    address_complement VARCHAR(255),
    city VARCHAR(150) NOT NULL,
    department VARCHAR(150) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'Colombia',
    postal_code VARCHAR(20),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    place_id VARCHAR(255),
    provider VARCHAR(50) NOT NULL DEFAULT 'INTERNAL',
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_location_latitude
        CHECK (latitude IS NULL OR latitude BETWEEN -90 AND 90),

    CONSTRAINT chk_location_longitude
        CHECK (longitude IS NULL OR longitude BETWEEN -180 AND 180)
);

-- ============================================================
-- PERSONAS
-- ============================================================

CREATE TABLE IF NOT EXISTS persons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    surname VARCHAR(100) NOT NULL,
    identification VARCHAR(20) NOT NULL UNIQUE,
    date_birth DATE NOT NULL,
    number_phone VARCHAR(10) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ============================================================
-- ALMACENES
-- ============================================================

CREATE TABLE IF NOT EXISTS store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bussnies_name VARCHAR(255) NOT NULL UNIQUE,
    nit VARCHAR(20) NOT NULL UNIQUE,
    bussnies_email VARCHAR(200) NOT NULL UNIQUE,
    state UUID NOT NULL REFERENCES states(id),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    logo_path VARCHAR(500),
    banner_path VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- ============================================================
-- SUCURSALES
-- ============================================================

CREATE TABLE IF NOT EXISTS branches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES store(id),
    name VARCHAR(255) NOT NULL,
    location_id UUID NOT NULL REFERENCES locations(id),
    number_phone VARCHAR(10) NOT NULL,
    state UUID NOT NULL REFERENCES states(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_branch_store_name
        UNIQUE (store_id, name)
);

ALTER TABLE branches ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE branches ALTER COLUMN state SET NOT NULL;

-- ============================================================
-- CUENTAS
-- ============================================================

CREATE TABLE IF NOT EXISTS accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_person UUID NOT NULL UNIQUE REFERENCES persons(id),
    email VARCHAR(100) NOT NULL UNIQUE,
    password TEXT NOT NULL,
    type_id UUID NOT NULL REFERENCES types_accounts(id),
    state_id UUID NOT NULL REFERENCES states(id),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS seller_plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(120) NOT NULL,
    description TEXT,
    monthly_price_cop BIGINT NOT NULL CHECK (monthly_price_cop >= 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'COP',
    features JSONB NOT NULL DEFAULT '[]'::jsonb,
    recommended BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_seller_plans_single_recommended
    ON seller_plans (recommended)
    WHERE recommended = TRUE AND deleted_at IS NULL;

INSERT INTO seller_plans(code, name, description, monthly_price_cop, currency, features, recommended, enabled)
VALUES (
    'TRY',
    'Prueba gratuita',
    'Un mes para empezar a vender en Autopartco.',
    0,
    'COP',
    '["Publica tu almacén","Administra productos y sucursales","Prueba las herramientas de venta"]'::jsonb,
    FALSE,
    TRUE
)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS seller_applications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    applicant_account_id UUID NOT NULL REFERENCES accounts(id),
    store_id UUID NOT NULL UNIQUE REFERENCES store(id),
    plan_id UUID NOT NULL REFERENCES seller_plans(id),
    selected_plan_id UUID NOT NULL REFERENCES seller_plans(id),
    legal_type VARCHAR(30) NOT NULL CHECK (legal_type IN ('NATURAL_PERSON', 'LEGAL_ENTITY')),
    status VARCHAR(30) NOT NULL CHECK (status IN (
        'PENDING_REVIEW', 'CONFIGURATION_PENDING', 'REJECTED', 'ACTIVE'
    )),
    reviewed_by UUID REFERENCES accounts(id),
    review_notes TEXT,
    onboarding_token_hash VARCHAR(64) UNIQUE,
    onboarding_token_expires_at TIMESTAMP,
    configuration_completed_at TIMESTAMP,
    trial_starts_at TIMESTAMP,
    trial_ends_at TIMESTAMP,
    paid_through TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_seller_applications_review
    ON seller_applications (status, created_at);

CREATE TABLE IF NOT EXISTS seller_application_documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES seller_applications(id),
    document_type VARCHAR(40) NOT NULL CHECK (document_type IN (
        'IDENTITY_DOCUMENT', 'RUT', 'LEGAL_REPRESENTATIVE_ID', 'CERTIFICATE_OF_EXISTENCE'
    )),
    object_path VARCHAR(500) NOT NULL UNIQUE,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes > 0),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_seller_application_document_type UNIQUE (application_id, document_type)
);

CREATE TABLE IF NOT EXISTS seller_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL REFERENCES seller_applications(id),
    plan_id UUID NOT NULL REFERENCES seller_plans(id),
    reference VARCHAR(100) NOT NULL UNIQUE,
    amount_in_cents BIGINT NOT NULL CHECK (amount_in_cents > 0),
    prepaid_months INTEGER NOT NULL CHECK (prepaid_months BETWEEN 1 AND 12),
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_transaction_id VARCHAR(120) UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ============================================================
-- AUTENTICACION
-- ============================================================

CREATE TABLE IF NOT EXISTS login_attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    attempted_at TIMESTAMP NOT NULL DEFAULT now(),
    successful BOOLEAN NOT NULL,
    ip_address INET NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS account_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    purpose TEXT NOT NULL,
    token TEXT NOT NULL UNIQUE,
    expiration_at TIMESTAMP NOT NULL,
    used BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS account_sessions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    refresh_token_hash VARCHAR(64) NOT NULL,
    ip_address INET NOT NULL,
    user_agent TEXT NOT NULL,
    expired_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_account_sessions_refresh_token_hash
    ON account_sessions (refresh_token_hash);

DO $$
DECLARE
    constraint_name TEXT;
BEGIN
    SELECT tc.constraint_name
    INTO constraint_name
    FROM information_schema.table_constraints tc
    JOIN information_schema.constraint_column_usage ccu
        ON ccu.constraint_name = tc.constraint_name
        AND ccu.table_schema = tc.table_schema
    WHERE tc.table_schema = current_schema()
      AND tc.table_name = 'account_sessions'
      AND tc.constraint_type = 'UNIQUE'
      AND ccu.column_name = 'account_id';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE account_sessions DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

-- ============================================================
-- ROLES Y PERMISOS
-- ============================================================

CREATE TABLE IF NOT EXISTS roles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES store(id),
    code VARCHAR(100) NOT NULL,
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_role_store_code
        UNIQUE (store_id, code)
);

CREATE TABLE IF NOT EXISTS collaborators (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES persons(id),
    collab_store_id UUID NOT NULL REFERENCES store(id),
    state UUID NOT NULL REFERENCES states(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_collaborator_person_store
        UNIQUE (person_id, collab_store_id)
);

CREATE TABLE IF NOT EXISTS roles_collaborators (
    collaborator_id UUID NOT NULL REFERENCES collaborators(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    PRIMARY KEY (collaborator_id, role_id)
);

CREATE TABLE IF NOT EXISTS roles_permissions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_role_permission
        UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS seller_collaborator_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES store(id),
    invited_by_account_id UUID NOT NULL REFERENCES accounts(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    email VARCHAR(100) NOT NULL,
    token_hash VARCHAR(64) UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    accepted_at TIMESTAMP,
    revoked_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_seller_collaborator_invitation_pending_email
    ON seller_collaborator_invitations (store_id, lower(email))
    WHERE accepted_at IS NULL
      AND revoked_at IS NULL
      AND deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_seller_collaborator_invitation_pending
    ON seller_collaborator_invitations (store_id, expires_at)
    WHERE accepted_at IS NULL
      AND revoked_at IS NULL
      AND deleted_at IS NULL;

INSERT INTO permissions(code, description)
VALUES
    ('STORE_READ', 'Consultar datos del almacén'),
    ('STORE_UPDATE', 'Actualizar datos del almacén'),
    ('BRANCH_READ', 'Consultar sucursales'),
    ('BRANCH_CREATE', 'Crear sucursales'),
    ('BRANCH_UPDATE', 'Actualizar sucursales'),
    ('BRANCH_DELETE', 'Desactivar sucursales'),
    ('PRODUCT_READ', 'Consultar productos del almacén'),
    ('PRODUCT_CREATE', 'Crear productos'),
    ('PRODUCT_UPDATE', 'Actualizar productos'),
    ('PRODUCT_PUBLISH', 'Publicar o retirar productos'),
    ('PRODUCT_DELETE', 'Eliminar productos'),
    ('PRODUCT_IMAGE_MANAGE', 'Administrar imágenes de productos'),
    ('PROMOTION_READ', 'Consultar promociones'),
    ('PROMOTION_CREATE', 'Crear promociones'),
    ('PROMOTION_UPDATE', 'Actualizar promociones'),
    ('PROMOTION_DELETE', 'Eliminar promociones'),
    ('ORDER_READ', 'Consultar pedidos y ventas del almacén'),
    ('ORDER_UPDATE', 'Actualizar el estado de pedidos y ventas del almacén'),
    ('SHIPMENT_UPDATE', 'Crear y actualizar envíos del almacén'),
    ('COLLABORATOR_READ', 'Consultar colaboradores e invitaciones del almacén'),
    ('COLLABORATOR_MANAGE', 'Invitar, retirar y administrar colaboradores del almacén')
ON CONFLICT (code) DO NOTHING;

INSERT INTO roles_permissions(role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
CROSS JOIN permissions permission
WHERE role.code = 'OWNER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS branches_collaborators (
    collaborator_id UUID NOT NULL REFERENCES collaborators(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    PRIMARY KEY (collaborator_id, branch_id)
);

-- ============================================================
-- VEHICULOS
-- ============================================================

CREATE TABLE IF NOT EXISTS make (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(200) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS model (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    make_id UUID NOT NULL REFERENCES make(id),
    name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_model_make_name
        UNIQUE (make_id, name),

    CONSTRAINT uq_model_id_make
        UNIQUE (id, make_id)
);

CREATE TABLE IF NOT EXISTS versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    model_id UUID NOT NULL REFERENCES model(id),
    version VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_version_model_name
        UNIQUE (model_id, version),

    CONSTRAINT uq_version_id_model
        UNIQUE (id, model_id)
);

-- ============================================================
-- PRODUCTOS
-- ============================================================

CREATE TABLE IF NOT EXISTS products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID REFERENCES store(id),
    name VARCHAR(200) NOT NULL,
    description TEXT,
    sku VARCHAR(100) UNIQUE,
    part_number VARCHAR(50),
    manufacturer_id UUID NOT NULL REFERENCES manufacturers(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    state_id UUID NOT NULL REFERENCES states(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

ALTER TABLE products ADD COLUMN IF NOT EXISTS store_id UUID REFERENCES store(id);
ALTER TABLE products ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE products ADD COLUMN IF NOT EXISTS attributes JSONB NOT NULL DEFAULT '{}'::jsonb;

CREATE TABLE IF NOT EXISTS product_images (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    storage_path VARCHAR(500) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    is_main BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_product_images_product_order
    ON product_images (product_id, display_order);

CREATE UNIQUE INDEX IF NOT EXISTS uq_product_images_single_main
    ON product_images (product_id)
    WHERE is_main = TRUE AND deleted_at IS NULL;

-- ============================================================
-- COMPATIBILIDADES
-- ============================================================

CREATE TABLE IF NOT EXISTS compatibilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    make_id UUID NOT NULL REFERENCES make(id),
    model_id UUID NOT NULL REFERENCES model(id),
    version_id UUID NOT NULL REFERENCES versions(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_compatibility
        UNIQUE (make_id, model_id, version_id),

    -- El modelo debe pertenecer a la marca.
    CONSTRAINT fk_compatibility_model_make
        FOREIGN KEY (model_id, make_id)
        REFERENCES model(id, make_id),

    -- La versión debe pertenecer al modelo.
    CONSTRAINT fk_compatibility_version_model
        FOREIGN KEY (version_id, model_id)
        REFERENCES versions(id, model_id)
);

CREATE TABLE IF NOT EXISTS products_compatibilies (
    product_id UUID NOT NULL REFERENCES products(id),
    compatibility_id UUID NOT NULL REFERENCES compatibilities(id),

    PRIMARY KEY (product_id, compatibility_id)
);

-- ============================================================
-- ESPECIFICACIONES
-- ============================================================

CREATE TABLE IF NOT EXISTS units_specifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    specification_id UUID NOT NULL REFERENCES specifications(id),
    unit_id UUID NOT NULL REFERENCES units(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_specification_unit
        UNIQUE (specification_id, unit_id)
);

CREATE TABLE IF NOT EXISTS products_specifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(id),
    specification_id UUID NOT NULL REFERENCES specifications(id),
    specification_unit_id UUID REFERENCES units_specifications(id),
    value VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- ============================================================
-- INVENTARIO
-- ============================================================

CREATE TABLE IF NOT EXISTS inventory (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id UUID NOT NULL REFERENCES branches(id),
    product_id UUID NOT NULL REFERENCES products(id),
    stock INTEGER NOT NULL DEFAULT 0,
    price DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_inventory_branch_product
        UNIQUE (branch_id, product_id),

    CONSTRAINT chk_inventory_stock
        CHECK (stock >= 0),

    CONSTRAINT chk_inventory_price
        CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS promotions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope VARCHAR(20) NOT NULL,
    store_id UUID REFERENCES store(id),
    name VARCHAR(120) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id UUID,
    attribute_key VARCHAR(100),
    attribute_value VARCHAR(255),
    attribute_operator VARCHAR(20),
    discount_type VARCHAR(20) NOT NULL,
    discount_value DECIMAL(12,2) NOT NULL,
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_promotions_scope_store
        CHECK ((scope = 'STORE' AND store_id IS NOT NULL)
            OR (scope = 'PLATFORM' AND store_id IS NULL)),
    CONSTRAINT chk_promotions_dates
        CHECK (ends_at > starts_at),
    CONSTRAINT chk_promotions_discount
        CHECK (discount_value > 0
            AND (discount_type <> 'PERCENTAGE' OR discount_value <= 100)),
    CONSTRAINT chk_promotions_attribute
        CHECK ((target_type = 'ATTRIBUTE' AND attribute_key IS NOT NULL AND attribute_value IS NOT NULL)
            OR (target_type <> 'ATTRIBUTE' AND attribute_key IS NULL
                AND attribute_value IS NULL AND attribute_operator IS NULL))
);

CREATE INDEX IF NOT EXISTS idx_promotions_active_dates
    ON promotions (enabled, starts_at, ends_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_promotions_store_scope
    ON promotions (store_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- ============================================================
-- DIRECCIONES DEL CLIENTE
-- ============================================================

CREATE TABLE IF NOT EXISTS person_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES persons(id),
    location_id UUID NOT NULL REFERENCES locations(id),
    label VARCHAR(100) NOT NULL DEFAULT 'Principal',
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_person_addresses_default
    ON person_addresses (person_id)
    WHERE is_default = true AND deleted_at IS NULL;

-- ============================================================
-- PEDIDOS
-- ============================================================

CREATE TABLE IF NOT EXISTS orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_number BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
    person_id UUID NOT NULL REFERENCES persons(id),
    state_id UUID NOT NULL REFERENCES states(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP
);

-- Snapshot de la dirección utilizada en la compra.
CREATE TABLE IF NOT EXISTS order_addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id),
    address_line VARCHAR(255) NOT NULL,
    address_complement VARCHAR(255),
    city VARCHAR(150) NOT NULL,
    department VARCHAR(150) NOT NULL,
    country VARCHAR(100) NOT NULL DEFAULT 'Colombia',
    postal_code VARCHAR(20),
    latitude DECIMAL(10,7),
    longitude DECIMAL(10,7),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- VENTAS POR VENDEDOR
--
-- Una orden puede contener productos de varios almacenes.
--
-- ORDER
--   ├── SALE -> STORE A
--   └── SALE -> STORE B
-- ============================================================

CREATE TABLE IF NOT EXISTS sales (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    branch_id UUID NOT NULL REFERENCES branches(id),
    state_id UUID NOT NULL REFERENCES states(id),
    subtotal DECIMAL(12,2) NOT NULL DEFAULT 0,
    shipping_cost DECIMAL(12,2) NOT NULL DEFAULT 0,
    total DECIMAL(12,2) NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT uq_sale_order_branch
        UNIQUE (order_id, branch_id),

    CONSTRAINT chk_sale_subtotal
        CHECK (subtotal >= 0),

    CONSTRAINT chk_sale_shipping
        CHECK (shipping_cost >= 0),

    CONSTRAINT chk_sale_total
        CHECK (total >= 0)
);

CREATE TABLE IF NOT EXISTS order_details (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    sale_id UUID NOT NULL REFERENCES sales(id),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    units INTEGER NOT NULL,
    unit_price DECIMAL(12,2) NOT NULL,

    CONSTRAINT chk_order_detail_units
        CHECK (units > 0),

    CONSTRAINT chk_order_detail_price
        CHECK (unit_price >= 0)
);

-- ============================================================
-- PAGOS SIMULADOS
-- ============================================================

CREATE TABLE IF NOT EXISTS payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL UNIQUE,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS payment_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    payment_method_id UUID NOT NULL REFERENCES payment_methods(id),
    payment_status_id UUID NOT NULL REFERENCES payment_statuses(id),
    provider VARCHAR(50) NOT NULL DEFAULT 'SIMULATED',
    provider_reference VARCHAR(150),
    amount DECIMAL(12,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'COP',
    paid_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_payment_provider_reference
        UNIQUE (provider, provider_reference),

    CONSTRAINT chk_payment_amount
        CHECK (amount >= 0)
);

CREATE TABLE IF NOT EXISTS payment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id UUID NOT NULL REFERENCES payments(id),
    payment_status_id UUID NOT NULL REFERENCES payment_statuses(id),
    message VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- RECIBOS
-- ============================================================

CREATE TABLE IF NOT EXISTS receipts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id UUID NOT NULL REFERENCES orders(id),
    payment_id UUID NOT NULL REFERENCES payments(id),
    receipt_number VARCHAR(100) NOT NULL UNIQUE,
    issued_at TIMESTAMP NOT NULL DEFAULT now(),
    total DECIMAL(12,2) NOT NULL,

    CONSTRAINT chk_receipt_total
        CHECK (total >= 0)
);

-- ============================================================
-- ENVIOS
-- ============================================================

CREATE TABLE IF NOT EXISTS shipment_statuses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS shipments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id UUID NOT NULL UNIQUE REFERENCES sales(id),
    shipment_status_id UUID NOT NULL REFERENCES shipment_statuses(id),
    carrier VARCHAR(100) NOT NULL DEFAULT 'SIMULATED',
    tracking_number VARCHAR(100) NOT NULL UNIQUE,
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS shipment_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shipment_id UUID NOT NULL REFERENCES shipments(id),
    shipment_status_id UUID NOT NULL REFERENCES shipment_statuses(id),
    notes VARCHAR(255),
    event_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- RESEÑAS
-- ============================================================

CREATE TABLE IF NOT EXISTS store_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    store_id UUID NOT NULL REFERENCES store(id),
    person_id UUID NOT NULL REFERENCES persons(id),
    order_id UUID NOT NULL REFERENCES orders(id),
    rating SMALLINT NOT NULL,
    comment TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,

    CONSTRAINT chk_review_rating
        CHECK (rating BETWEEN 1 AND 5),

    CONSTRAINT uq_review_person_store_order
        UNIQUE (store_id, person_id, order_id)
);

-- ============================================================
-- NOTIFICACIONES
-- ============================================================

CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID NOT NULL REFERENCES accounts(id),
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- RECLAMOS
-- ============================================================

CREATE TABLE IF NOT EXISTS claims (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id UUID NOT NULL REFERENCES persons(id),
    order_id UUID REFERENCES orders(id),
    store_id UUID REFERENCES store(id),
    reason VARCHAR(150) NOT NULL,
    description TEXT NOT NULL,
    state_id UUID NOT NULL REFERENCES states(id),
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now()
);

-- ============================================================
-- AUDITORIA
-- ============================================================

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id UUID REFERENCES accounts(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    updated_at TIMESTAMP NOT NULL DEFAULT now(),
    deleted_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0
);

-- ============================================================
-- MOVIMIENTOS DE INVENTARIO
-- ============================================================

CREATE TABLE IF NOT EXISTS inventory_movements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventory(id),
    order_detail_id UUID REFERENCES order_details(id),
    movement_type VARCHAR(30) NOT NULL,
    quantity INTEGER NOT NULL,
    previous_stock INTEGER NOT NULL,
    new_stock INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT chk_inventory_movement_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_inventory_movement_previous
        CHECK (previous_stock >= 0),

    CONSTRAINT chk_inventory_movement_new
        CHECK (new_stock >= 0)
);

-- ============================================================
-- CAMPOS COMUNES DE ENTIDADES
-- Todas las tablas con entidad propia siguen la estructura BaseEntity.
-- Las tablas puente conservan sus claves compuestas.
-- ============================================================

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'states',
        'types_accounts',
        'permissions',
        'categories',
        'manufacturers',
        'specifications',
        'units',
        'locations',
        'persons',
        'store',
        'branches',
        'accounts',
        'seller_plans',
        'seller_applications',
        'seller_application_documents',
        'seller_payments',
        'login_attempts',
        'account_tokens',
        'account_sessions',
        'roles',
        'collaborators',
        'roles_permissions',
        'make',
        'model',
        'versions',
        'products',
        'compatibilities',
        'units_specifications',
        'products_specifications',
        'inventory',
        'promotions',
        'person_addresses',
        'orders',
        'order_addresses',
        'sales',
        'order_details',
        'payment_methods',
        'payment_statuses',
        'payments',
        'payment_events',
        'receipts',
        'shipment_statuses',
        'shipments',
        'shipment_events',
        'store_reviews',
        'notifications',
        'claims',
        'audit_logs',
        'inventory_movements'
    ]
    LOOP
        EXECUTE format(
            'ALTER TABLE %I
             ADD COLUMN IF NOT EXISTS created_at TIMESTAMP NOT NULL DEFAULT now(),
             ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP NOT NULL DEFAULT now(),
             ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP,
             ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0',
            t
        );
    END LOOP;
END $$;

-- ============================================================
-- INDICES
-- ============================================================

CREATE INDEX IF NOT EXISTS idx_products_name
ON products USING gin (to_tsvector('spanish', name));

CREATE INDEX IF NOT EXISTS idx_products_part_number
ON products(part_number);

CREATE INDEX IF NOT EXISTS idx_products_manufacturer
ON products(manufacturer_id);

CREATE INDEX IF NOT EXISTS idx_products_category
ON products(category_id);

CREATE INDEX IF NOT EXISTS idx_inventory_product
ON inventory(product_id);

CREATE INDEX IF NOT EXISTS idx_inventory_branch
ON inventory(branch_id);

CREATE INDEX IF NOT EXISTS idx_inventory_price
ON inventory(price);

CREATE INDEX IF NOT EXISTS idx_branches_location
ON branches(location_id);

CREATE INDEX IF NOT EXISTS idx_branches_store
ON branches(store_id);

CREATE INDEX IF NOT EXISTS idx_model_make
ON model(make_id);

CREATE INDEX IF NOT EXISTS idx_versions_model
ON versions(model_id);

CREATE INDEX IF NOT EXISTS idx_compatibility_vehicle
ON compatibilities(make_id, model_id, version_id);

CREATE INDEX IF NOT EXISTS idx_orders_person_created
ON orders(person_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_orders_state
ON orders(state_id);

CREATE INDEX IF NOT EXISTS idx_order_details_order
ON order_details(order_id);

CREATE INDEX IF NOT EXISTS idx_order_details_sale
ON order_details(sale_id);

CREATE INDEX IF NOT EXISTS idx_sales_branch
ON sales(branch_id);

CREATE INDEX IF NOT EXISTS idx_sales_order
ON sales(order_id);

CREATE INDEX IF NOT EXISTS idx_payments_order
ON payments(order_id);

CREATE INDEX IF NOT EXISTS idx_shipments_tracking
ON shipments(tracking_number);

CREATE INDEX IF NOT EXISTS idx_shipment_events_shipment
ON shipment_events(shipment_id, event_at DESC);

CREATE INDEX IF NOT EXISTS idx_reviews_store
ON store_reviews(store_id);

CREATE INDEX IF NOT EXISTS idx_notifications_account
ON notifications(account_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity
ON audit_logs(entity_type, entity_id);

-- ============================================================
-- UPDATED_AT AUTOMATICO
-- ============================================================

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

DO $$
DECLARE
    t TEXT;
BEGIN
    FOREACH t IN ARRAY ARRAY[
        'states',
        'types_accounts',
        'permissions',
        'categories',
        'manufacturers',
        'specifications',
        'units',
        'locations',
        'persons',
        'store',
        'branches',
        'accounts',
        'seller_plans',
        'seller_applications',
        'seller_application_documents',
        'seller_payments',
        'login_attempts',
        'account_tokens',
        'account_sessions',
        'roles',
        'collaborators',
        'roles_permissions',
        'make',
        'model',
        'versions',
        'products',
        'compatibilities',
        'units_specifications',
        'products_specifications',
        'inventory',
        'promotions',
        'person_addresses',
        'orders',
        'order_addresses',
        'order_details',
        'sales',
        'payment_methods',
        'payment_statuses',
        'payments',
        'payment_events',
        'receipts',
        'shipment_statuses',
        'shipments',
        'shipment_events',
        'store_reviews',
        'notifications',
        'claims',
        'audit_logs',
        'inventory_movements'
    ]
    LOOP
        EXECUTE format(
            'DROP TRIGGER IF EXISTS trg_%I_updated_at ON %I',
            t,
            t
        );

        EXECUTE format(
            'CREATE TRIGGER trg_%I_updated_at
             BEFORE UPDATE ON %I
             FOR EACH ROW
             EXECUTE FUNCTION set_updated_at()',
            t,
            t
        );
    END LOOP;
END $$;

-- ============================================================
-- SEEDS MINIMOS
-- ============================================================

INSERT INTO states(code, description)
VALUES
    ('ACTIVE', 'Registro activo'),
    ('LOCKED', 'Cuenta bloqueada'),
    ('INACTIVE', 'Registro inactivo'),
    ('PENDING', 'Pendiente'),
    ('CONFIRMED', 'Confirmado'),
    ('PREPARING', 'Preparando'),
    ('SHIPPED', 'Enviado'),
    ('DELIVERED', 'Entregado'),
    ('CANCELLED', 'Cancelado'),
    ('REJECTED', 'Rechazado')
ON CONFLICT(code) DO NOTHING;

INSERT INTO types_accounts(code, description)
VALUES
    ('CUSTOMER', 'Cliente'),
    ('STORE_MANAGER', 'Gestor de Almacén'),
    ('ROOT', 'Administrador Root')
ON CONFLICT(code) DO NOTHING;

INSERT INTO payment_methods(code, name)
VALUES
    ('SIMULATED_CARD', 'Tarjeta simulada'),
    ('SIMULATED_TRANSFER', 'Transferencia simulada'),
    ('SIMULATED_CASH', 'Efectivo simulado')
ON CONFLICT(code) DO NOTHING;

INSERT INTO payment_statuses(code, description)
VALUES
    ('PENDING', 'Pago pendiente'),
    ('APPROVED', 'Pago aprobado'),
    ('REJECTED', 'Pago rechazado'),
    ('CANCELLED', 'Pago cancelado'),
    ('REFUNDED', 'Pago reembolsado')
ON CONFLICT(code) DO NOTHING;

INSERT INTO shipment_statuses(code, description)
VALUES
    ('PREPARING', 'Preparando envío'),
    ('DISPATCHED', 'Despachado'),
    ('IN_TRANSIT', 'En tránsito'),
    ('OUT_FOR_DELIVERY', 'En entrega'),
    ('DELIVERED', 'Entregado')
ON CONFLICT(code) DO NOTHING;

COMMIT;

-- ============================================================
-- FIN init.sql
-- ============================================================
