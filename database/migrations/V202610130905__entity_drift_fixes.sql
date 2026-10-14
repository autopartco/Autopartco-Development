-- =============================================================================
-- V202610130905__entity_drift_fixes.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: cerrar la deriva entre init.sql y las entidades JPA.
--   1. seller_plans: max_branches, max_products, max_users (INTEGER NULL;
--      NULL = ilimitado, ver PlanEntitlements.requireCapacity) y capabilities
--      JSONB NOT NULL DEFAULT '[]' (Plan.java). Relleno igual a
--      PlanService.applyDefaultEntitlements para filas sin límites.
--   2. persons.number_phone: VARCHAR(10) -> VARCHAR(20) (Person.java length=20).
--      Ampliar un VARCHAR no reescribe la tabla.
--   3. inventory.price: se quita DEFAULT 0. El backend siempre envía el precio
--      (StockService.createListing); el default solo servía para crear filas
--      con precio 0 por accidente desde SQL.
--
-- Compatibilidad: si ddl-auto: update ya creó las columnas de seller_plans,
--   ADD COLUMN IF NOT EXISTS las respeta y el relleno solo toca filas vacías.
-- Divergencias documentadas que NO se cambian (BD más estricta que la entidad):
--   accounts.state_id / accounts.configuration / audit_logs.metadata NOT NULL;
--   account_sessions.refresh_token_hash VARCHAR(64) (SHA-256 hex = 64).
--
-- Transacción: SÍ. Bloqueos: ACCESS EXCLUSIVE breve sobre seller_plans,
--   persons e inventory (sin reescritura).
-- Rollback: U202610130905__entity_drift_fixes.sql (destructivo para seller_plans).
-- =============================================================================

SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '15min';

-- 1. seller_plans ---------------------------------------------------------------
ALTER TABLE public.seller_plans
    ADD COLUMN IF NOT EXISTS max_branches INTEGER,
    ADD COLUMN IF NOT EXISTS max_products INTEGER,
    ADD COLUMN IF NOT EXISTS max_users    INTEGER,
    ADD COLUMN IF NOT EXISTS capabilities JSONB;

UPDATE public.seller_plans
SET capabilities = '[]'::jsonb
WHERE capabilities IS NULL;

ALTER TABLE public.seller_plans
    ALTER COLUMN capabilities SET DEFAULT '[]'::jsonb,
    ALTER COLUMN capabilities SET NOT NULL;

-- Mismos valores que PlanService.applyDefaultEntitlements. Solo filas que nunca
-- recibieron límites (las tres columnas NULL y capabilities vacío).
UPDATE public.seller_plans p
SET max_branches = d.max_branches,
    max_products = d.max_products,
    max_users    = d.max_users,
    capabilities = d.capabilities
FROM (
    VALUES
        ('TRY',        1,    50,   1,    '["BASIC_ANALYTICS"]'::jsonb),
        ('FREE',       1,    50,   1,    '["BASIC_ANALYTICS"]'::jsonb),
        ('BUSINESS',   5,    5000, 10,   '["BASIC_ANALYTICS","ADVANCED_REPORTS","PROMOTIONS","CSV_IMPORT","WHATSAPP_NOTIFICATIONS","INTEGRATIONS","CUSTOM_ROLES"]'::jsonb),
        ('ENTERPRISE', NULL, NULL, NULL, '["BASIC_ANALYTICS","ADVANCED_REPORTS","PROMOTIONS","CSV_IMPORT","WHATSAPP_NOTIFICATIONS","INTEGRATIONS","CUSTOM_ROLES","API_ACCESS","WEBHOOKS","ERP_INTEGRATIONS","CUSTOM_DOMAIN"]'::jsonb)
) AS d(code, max_branches, max_products, max_users, capabilities)
WHERE upper(p.code) = d.code
  AND p.max_branches IS NULL
  AND p.max_products IS NULL
  AND p.max_users IS NULL
  AND p.capabilities = '[]'::jsonb;

-- 2. persons.number_phone ------------------------------------------------------
ALTER TABLE public.persons
    ALTER COLUMN number_phone TYPE VARCHAR(20);

-- 3. inventory.price -----------------------------------------------------------
ALTER TABLE public.inventory
    ALTER COLUMN price DROP DEFAULT;
