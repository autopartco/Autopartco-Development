-- =============================================================================
-- V202610130920__check_constraints_not_valid.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: CHECK de enumeraciones y reglas de negocio que hoy solo vive en el
--   backend. Primero se diagnostican los datos existentes y se aborta si
--   alguna fila incumple (antes de crear nada). Luego se añaden con NOT
--   VALID: se aplican a toda fila nueva o modificada desde ya, sin recorrer
--   la tabla bajo bloqueo exclusivo. VALIDATE va en V202610130921.
--
-- Cada regla se contrastó con el backend actual para que ningún INSERT/UPDATE
-- válido para la API falle:
--   - seller_payments.status: SubscriptionCheckout ("PENDING") y
--     WompiWebhookProcessor (APPROVED, DECLINED, ERROR, VOIDED, FAILED).
--   - account_tokens.purpose: EmailVerificationTokenService (3 propósitos).
--   - accounts.email / store.bussnies_email / invitaciones.email: el backend
--     guarda trim().toLowerCase(Locale.ROOT) (AccountProvisioning, StoreService,
--     InviteStaffMember).
--   - promotions: enums PromotionScope/TargetType/DiscountType/AttributeOperator
--     y reglas de PromotionService.validateTarget.
--   - documentos: StoreFileStorage (PDF/PNG/JPEG, 10 MB); imágenes:
--     ProductImageValidator (JPEG/PNG/WebP).
--   - branches.number_phone: BranchCreateRequest @Pattern [0-9]{7,10}.
--   - persons.number_phone: RegisterRequest solo exige @NotBlank @Size(max=10).
--     Se usa un patrón laxo (dígitos, espacios, + ( ) . -; 7 a 15 dígitos).
--     RIESGO: un teléfono con letras pasaría a dar 500 en vez de 400. El
--     backend debe añadir @Pattern equivalente (ver README).
--   - seller_applications: StoreApplications fija y anula juntos el hash y la
--     expiración del token de onboarding.
-- NO se incluyen (requieren aprobación o cambio de backend):
--   - inventory_movements movement_type/balance (A4, pending-approval).
--   - inventory.price > 0 (el backend acepta 0: @DecimalMin("0.0")).
--   - account_sessions.expired_at > created_at: created_at lo pone now() en
--     la BD y expired_at la JVM; zonas mezcladas (ADR 0001 §1).
--
-- Transacción: SÍ. Bloqueo: ACCESS EXCLUSIVE breve por tabla (sin recorrido).
-- Rollback: U202610130920__check_constraints_not_valid.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

-- Paso 1: se registran las definiciones. Paso 2 (al final): diagnóstico de los
-- datos existentes. Si alguna fila incumple, se aborta ANTES de crear ningún
-- CHECK. Paso 3: creación NOT VALID.
CREATE TEMP TABLE chk_defs (
    seq   SERIAL,
    tbl   regclass NOT NULL,
    name  TEXT     NOT NULL,
    expr  TEXT     NOT NULL
) ON COMMIT DROP;

CREATE OR REPLACE FUNCTION pg_temp.add_check(p_table regclass, p_name TEXT, p_expr TEXT)
RETURNS void
LANGUAGE sql
AS $$ INSERT INTO chk_defs (tbl, name, expr) VALUES (p_table, p_name, p_expr) $$;

-- Pagos de suscripción --------------------------------------------------------
SELECT pg_temp.add_check('public.seller_payments', 'chk_seller_payments__status',
    $c$status IN ('PENDING', 'APPROVED', 'DECLINED', 'ERROR', 'VOIDED', 'FAILED')$c$);
SELECT pg_temp.add_check('public.seller_payments', 'chk_seller_payments__currency',
    $c$currency ~ '^[A-Z]{3}$'$c$);

-- Planes ------------------------------------------------------------------------
SELECT pg_temp.add_check('public.seller_plans', 'chk_seller_plans__currency',
    $c$currency ~ '^[A-Z]{3}$'$c$);
SELECT pg_temp.add_check('public.seller_plans', 'chk_seller_plans__max_branches',
    $c$max_branches IS NULL OR max_branches >= 1$c$);
SELECT pg_temp.add_check('public.seller_plans', 'chk_seller_plans__max_products',
    $c$max_products IS NULL OR max_products >= 0$c$);
SELECT pg_temp.add_check('public.seller_plans', 'chk_seller_plans__max_users',
    $c$max_users IS NULL OR max_users >= 1$c$);
SELECT pg_temp.add_check('public.seller_plans', 'chk_seller_plans__capabilities_array',
    $c$jsonb_typeof(capabilities) = 'array'$c$);
SELECT pg_temp.add_check('public.seller_plans', 'chk_seller_plans__features_array',
    $c$jsonb_typeof(features) = 'array'$c$);
SELECT pg_temp.add_check('public.seller_plans', 'chk_seller_plans__trial_free',
    $c$code <> 'TRY' OR monthly_price_cop = 0$c$);

-- Tokens y cuentas ----------------------------------------------------------------
SELECT pg_temp.add_check('public.account_tokens', 'chk_account_tokens__purpose',
    $c$purpose IN ('EMAIL_VERIFICATION', 'ACCOUNT_UNLOCK', 'PASSWORD_RECOVERY')$c$);
SELECT pg_temp.add_check('public.account_tokens', 'chk_account_tokens__expiration',
    $c$expiration_at > created_at$c$);
SELECT pg_temp.add_check('public.accounts', 'chk_accounts__email_normalized',
    $c$email = lower(btrim(email))$c$);
SELECT pg_temp.add_check('public.accounts', 'chk_accounts__configuration_object',
    $c$jsonb_typeof(configuration) = 'object'$c$);

-- Almacén, sucursal, persona ------------------------------------------------------
SELECT pg_temp.add_check('public.store', 'chk_store__business_email_normalized',
    $c$bussnies_email = lower(btrim(bussnies_email))$c$);
SELECT pg_temp.add_check('public.store', 'chk_store__configuration_object',
    $c$jsonb_typeof(configuration) = 'object'$c$);
SELECT pg_temp.add_check('public.branches', 'chk_branches__number_phone_format',
    $c$number_phone ~ '^[0-9]{7,10}$'$c$);
SELECT pg_temp.add_check('public.persons', 'chk_persons__number_phone_format',
    $c$number_phone ~ '^\+?[0-9 ().-]+$'
       AND length(regexp_replace(number_phone, '[^0-9]', '', 'g')) BETWEEN 7 AND 15$c$);
SELECT pg_temp.add_check('public.persons', 'chk_persons__date_birth_min',
    $c$date_birth >= DATE '1900-01-01'$c$);
SELECT pg_temp.add_check('public.locations', 'chk_locations__metadata_object',
    $c$jsonb_typeof(metadata) = 'object'$c$);

-- Promociones ---------------------------------------------------------------------
SELECT pg_temp.add_check('public.promotions', 'chk_promotions__scope_enum',
    $c$scope IN ('STORE', 'PLATFORM')$c$);
SELECT pg_temp.add_check('public.promotions', 'chk_promotions__target_type_enum',
    $c$target_type IN ('ALL', 'STORE', 'PRODUCT', 'CATEGORY', 'MANUFACTURER', 'ATTRIBUTE')$c$);
SELECT pg_temp.add_check('public.promotions', 'chk_promotions__discount_type_enum',
    $c$discount_type IN ('PERCENTAGE', 'FIXED_AMOUNT')$c$);
SELECT pg_temp.add_check('public.promotions', 'chk_promotions__attribute_operator_enum',
    $c$attribute_operator IS NULL OR attribute_operator IN ('EQUALS', 'CONTAINS')$c$);
SELECT pg_temp.add_check('public.promotions', 'chk_promotions__target_id',
    $c$(target_type = 'ALL' AND scope = 'PLATFORM' AND target_id IS NULL)
       OR (target_type = 'ATTRIBUTE' AND target_id IS NULL)
       OR (target_type IN ('PRODUCT', 'CATEGORY', 'MANUFACTURER') AND target_id IS NOT NULL)
       OR (target_type = 'STORE' AND (
              (scope = 'PLATFORM' AND target_id IS NOT NULL)
           OR (scope = 'STORE' AND (target_id IS NULL OR target_id = store_id))))$c$);

-- Ventas, pagos y envíos (hoy sin escritura desde el backend salvo envíos) ---------
SELECT pg_temp.add_check('public.sales', 'chk_sales__total_consistent',
    $c$total = subtotal + shipping_cost$c$);
SELECT pg_temp.add_check('public.payments', 'chk_payments__amount_positive',
    $c$amount > 0$c$);
SELECT pg_temp.add_check('public.payments', 'chk_payments__currency',
    $c$currency ~ '^[A-Z]{3}$'$c$);
SELECT pg_temp.add_check('public.shipments', 'chk_shipments__delivery_after_shipping',
    $c$delivered_at IS NULL OR (shipped_at IS NOT NULL AND delivered_at >= shipped_at)$c$);
SELECT pg_temp.add_check('public.shipments', 'chk_shipments__tracking_not_blank',
    $c$btrim(tracking_number) <> ''$c$);

-- Solicitudes de vendedor e invitaciones --------------------------------------------
SELECT pg_temp.add_check('public.seller_applications', 'chk_seller_applications__trial_period',
    $c$trial_starts_at IS NULL OR trial_ends_at IS NULL OR trial_ends_at > trial_starts_at$c$);
SELECT pg_temp.add_check('public.seller_applications', 'chk_seller_applications__onboarding_token_pair',
    $c$(onboarding_token_hash IS NULL) = (onboarding_token_expires_at IS NULL)$c$);
SELECT pg_temp.add_check('public.seller_collaborator_invitations', 'chk_seller_collaborator_invitations__email_normalized',
    $c$email = lower(btrim(email))$c$);
SELECT pg_temp.add_check('public.seller_collaborator_invitations', 'chk_seller_collaborator_invitations__expiration',
    $c$expires_at > created_at$c$);
SELECT pg_temp.add_check('public.seller_collaborator_invitations', 'chk_seller_collaborator_invitations__single_outcome',
    $c$accepted_at IS NULL OR revoked_at IS NULL$c$);

-- Archivos ---------------------------------------------------------------------------
SELECT pg_temp.add_check('public.seller_application_documents', 'chk_seller_application_documents__content_type',
    $c$content_type IN ('application/pdf', 'image/png', 'image/jpeg')$c$);
SELECT pg_temp.add_check('public.seller_application_documents', 'chk_seller_application_documents__max_size',
    $c$size_bytes <= 10485760$c$);
SELECT pg_temp.add_check('public.product_images', 'chk_product_images__content_type',
    $c$content_type IN ('image/jpeg', 'image/png', 'image/webp')$c$);

-- Productos y auditoría ----------------------------------------------------------------
SELECT pg_temp.add_check('public.products', 'chk_products__attributes_object',
    $c$jsonb_typeof(attributes) = 'object'$c$);
SELECT pg_temp.add_check('public.products', 'chk_products__name_not_blank',
    $c$btrim(name) <> ''$c$);
SELECT pg_temp.add_check('public.audit_logs', 'chk_audit_logs__metadata_object',
    $c$jsonb_typeof(metadata) = 'object'$c$);

-- Paso 2: diagnóstico. Lista todas las reglas incumplidas y aborta sin crear nada.
-- Corrija los datos, por ejemplo:
--   UPDATE accounts SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));
DO $$
DECLARE
    d          RECORD;
    violations BIGINT;
    report     TEXT := '';
BEGIN
    FOR d IN SELECT * FROM chk_defs ORDER BY seq LOOP
        EXECUTE format('SELECT count(*) FROM %s WHERE NOT (%s)', d.tbl, d.expr) INTO violations;
        IF violations > 0 THEN
            report := report || format(E'\n  %s.%s: %s filas', d.tbl, d.name, violations);
        END IF;
    END LOOP;
    IF report <> '' THEN
        RAISE EXCEPTION 'Datos que incumplen los CHECK (no se creó ninguno):%', report;
    END IF;
END $$;

-- Paso 3: creación NOT VALID (idempotente).
DO $$
DECLARE
    d RECORD;
BEGIN
    FOR d IN SELECT * FROM chk_defs ORDER BY seq LOOP
        IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid = d.tbl AND conname = d.name) THEN
            EXECUTE format('ALTER TABLE %s ADD CONSTRAINT %I CHECK (%s) NOT VALID', d.tbl, d.name, d.expr);
        END IF;
    END LOOP;
END $$;

DROP FUNCTION pg_temp.add_check(regclass, TEXT, TEXT);
