-- =============================================================================
-- R__seed_planes_comerciales.sql  (repetible, transaccional)
-- Autopartco | SaaS de almacenes. Solo Colombia (COP).
--
-- Siembra los planes comerciales BASIC (Esencial), BUSINESS (Negocio) y PRO (Pro)
-- con sus límites (max_branches/max_products/max_users) y capabilities.
-- - NO siembra ENTERPRISE: con precio 0 se contrataría gratis.
-- - NO toca TRY (plan de prueba de init.sql).
-- - El precio de un plan que ya tiene suscripciones de pago activas
--   (seller_applications ACTIVE, paid_through > now(), no borradas) NO cambia;
--   se avisa con RAISE NOTICE. El resto de campos sí se actualizan.
-- - Deja un único plan recommended (BUSINESS) por uq_seller_plans_single_recommended.
-- Requiere: V202610130905 (columnas max_* y capabilities de seller_plans).
-- Idempotente (ON CONFLICT (code) DO UPDATE). Sin U: es una semilla.
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '1min';

-- Falla pronto si falta V202610130905.
DO $$
DECLARE
    v_missing TEXT;
BEGIN
    SELECT string_agg(c, ', ' ORDER BY c) INTO v_missing
    FROM unnest(ARRAY['max_branches', 'max_products', 'max_users', 'capabilities']) AS c
    WHERE NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = 'seller_plans' AND column_name = c
    );
    IF v_missing IS NOT NULL THEN
        RAISE EXCEPTION 'Faltan columnas en seller_plans: %. Aplique antes V202610130905__entity_drift_fixes.sql', v_missing;
    END IF;
END
$$;

-- Aviso: precios que no se cambiarán por haber suscripciones de pago activas.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN
        SELECT p.code, p.monthly_price_cop AS actual, s.price AS semilla, count(a.id) AS activas
        FROM public.seller_plans p
        JOIN (VALUES ('BASIC', 49900), ('BUSINESS', 149900), ('PRO', 299900)) AS s(code, price)
          ON s.code = p.code
        JOIN public.seller_applications a
          ON a.plan_id = p.id
         AND a.status = 'ACTIVE'
         AND a.paid_through > now()
         AND a.deleted_at IS NULL
        WHERE p.monthly_price_cop <> s.price
        GROUP BY p.code, p.monthly_price_cop, s.price
    LOOP
        RAISE NOTICE 'Plan %: precio actual % difiere de la semilla % y tiene % suscripcion(es) de pago activa(s); el precio no se cambia.',
            r.code, r.actual, r.semilla, r.activas;
    END LOOP;
END
$$;

-- Un solo recommended: libera el índice parcial antes de marcar BUSINESS.
UPDATE public.seller_plans
SET recommended = FALSE, updated_at = now(), version = version + 1
WHERE recommended = TRUE AND deleted_at IS NULL AND code <> 'BUSINESS';

INSERT INTO public.seller_plans AS p
    (code, name, description, monthly_price_cop, currency, features,
     max_branches, max_products, max_users, capabilities, recommended, enabled)
VALUES
    ('BASIC', 'Esencial',
     'Para empezar a vender repuestos en Autopartco con una sede. Pronto: importación CSV.',
     49900, 'COP',
     '["1 sede","Hasta 500 productos","2 usuarios","Vende en el marketplace de Autopartco","Gestión de pedidos","Inventario por sucursal","Analítica básica de ventas"]'::jsonb,
     1, 500, 2,
     '["BASIC_ANALYTICS"]'::jsonb,
     FALSE, TRUE),
    ('BUSINESS', 'Negocio',
     'Para almacenes con varias sedes y equipo. Pronto: importación desde Alegra o Siigo y reportes avanzados.',
     149900, 'COP',
     '["Hasta 3 sedes","Hasta 5.000 productos","10 usuarios","Vende en el marketplace de Autopartco","Gestión de pedidos","Inventario por sucursal","Analítica básica de ventas","Promociones","Roles personalizados"]'::jsonb,
     3, 5000, 10,
     '["BASIC_ANALYTICS","PROMOTIONS","CUSTOM_ROLES"]'::jsonb,
     TRUE, TRUE),
    ('PRO', 'Pro',
     'Para operaciones grandes con muchas sedes. Pronto: sincronización con Alegra y Siigo y facturación electrónica.',
     299900, 'COP',
     '["Hasta 10 sedes","Hasta 25.000 productos","25 usuarios","Vende en el marketplace de Autopartco","Gestión de pedidos","Inventario por sucursal","Analítica básica de ventas","Promociones","Roles personalizados"]'::jsonb,
     10, 25000, 25,
     '["BASIC_ANALYTICS","PROMOTIONS","CUSTOM_ROLES"]'::jsonb,
     FALSE, TRUE)
ON CONFLICT (code) DO UPDATE SET
    name        = EXCLUDED.name,
    description = EXCLUDED.description,
    monthly_price_cop = CASE
        WHEN p.monthly_price_cop <> EXCLUDED.monthly_price_cop
         AND EXISTS (
             SELECT 1 FROM public.seller_applications a
             WHERE a.plan_id = p.id
               AND a.status = 'ACTIVE'
               AND a.paid_through > now()
               AND a.deleted_at IS NULL
         )
        THEN p.monthly_price_cop
        ELSE EXCLUDED.monthly_price_cop
    END,
    currency     = EXCLUDED.currency,
    features     = EXCLUDED.features,
    max_branches = EXCLUDED.max_branches,
    max_products = EXCLUDED.max_products,
    max_users    = EXCLUDED.max_users,
    capabilities = EXCLUDED.capabilities,
    recommended  = EXCLUDED.recommended,
    enabled      = EXCLUDED.enabled,
    deleted_at   = NULL,
    updated_at   = now(),
    version      = p.version + 1;
