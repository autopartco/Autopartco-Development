-- Semilla de DESARROLLO para probar el portal del almacén. SOLO bases locales de prueba (p. ej. autopartco_saas_test).
-- Requiere init.sql + migraciones V aplicadas y la extensión pgcrypto. Cuenta: owner@test.co (contraseña en esta misma línea del INSERT de accounts).
-- Uso: psql -d autopartco_saas_test -v ON_ERROR_STOP=1 -f seed_portal_dev.sql
-- Datos de prueba: SOLO autopartco_saas_test. Idempotente: borra y vuelve a crear las mismas filas.
BEGIN;
-- Historial de suscripción de las pruebas (solo BD de pruebas; la tabla es de solo inserción, TRUNCATE no dispara el trigger de fila).
TRUNCATE seller_subscription_events;
DELETE FROM seller_payments WHERE application_id IN (SELECT id FROM seller_applications WHERE store_id IN ('c0000000-0000-4000-8000-000000000001','c0000000-0000-4000-8000-000000000002'));
DELETE FROM roles_collaborators WHERE collaborator_id IN (SELECT id FROM collaborators WHERE person_id IN ('a0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002'));
DELETE FROM collaborators WHERE person_id IN ('a0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002');
DELETE FROM roles_permissions WHERE role_id = 'b0000000-0000-4000-8000-000000000001';
DELETE FROM roles WHERE id = 'b0000000-0000-4000-8000-000000000001';
DELETE FROM seller_applications WHERE store_id IN ('c0000000-0000-4000-8000-000000000001','c0000000-0000-4000-8000-000000000002');
DELETE FROM store WHERE id IN ('c0000000-0000-4000-8000-000000000001','c0000000-0000-4000-8000-000000000002');
DELETE FROM accounts WHERE email = 'owner@test.co';
DELETE FROM persons WHERE id IN ('a0000000-0000-4000-8000-000000000001','a0000000-0000-4000-8000-000000000002');

INSERT INTO persons (id, name, surname, identification, date_birth, number_phone)
VALUES ('a0000000-0000-4000-8000-000000000001', 'Ana', 'Pérez', 'TEST-0001', '1990-01-01', '3001234567'),
       ('a0000000-0000-4000-8000-000000000002', 'Beto', 'Ruiz', 'TEST-0002', '1990-01-01', '3007654321');

INSERT INTO accounts (id, id_person, email, password, type_id, state_id)
SELECT 'd0000000-0000-4000-8000-000000000001'::uuid, 'a0000000-0000-4000-8000-000000000001', 'owner@test.co', crypt('Portal-Dev-2026!', gen_salt('bf', 10)),
       (SELECT id FROM types_accounts WHERE code = 'STORE_MANAGER'), (SELECT id FROM states WHERE code = 'ACTIVE');

INSERT INTO store (id, business_name, nit, business_email, state, configuration, data_origin)
SELECT 'c0000000-0000-4000-8000-000000000001'::uuid, 'Repuestos Test', '900000001', 'ventas@test.co',
       (SELECT id FROM states WHERE code = 'ACTIVE'), '{"description":"Tienda de prueba"}'::jsonb, 'REAL'
UNION ALL
SELECT 'c0000000-0000-4000-8000-000000000002'::uuid, 'Tienda Ajena', '900000002', 'ajena@test.co',
       (SELECT id FROM states WHERE code = 'ACTIVE'), '{}'::jsonb, 'REAL';

INSERT INTO seller_applications (id, applicant_account_id, store_id, plan_id, selected_plan_id, legal_type, status,
                                 trial_starts_at, trial_ends_at)
SELECT 'e0000000-0000-4000-8000-000000000001'::uuid, 'd0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
       (SELECT id FROM seller_plans WHERE code = 'TRY'), (SELECT id FROM seller_plans WHERE code = 'TRY'),
       'NATURAL_PERSON', 'ACTIVE', now() - interval '1 day', now() + interval '30 days';

INSERT INTO roles (id, store_id, code, name)
VALUES ('b0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001', 'OWNER', 'Propietario');

INSERT INTO roles_permissions (role_id, permission_id)
SELECT 'b0000000-0000-4000-8000-000000000001', id FROM permissions;

INSERT INTO collaborators (id, person_id, collab_store_id, state)
VALUES ('f0000000-0000-4000-8000-000000000001', 'a0000000-0000-4000-8000-000000000001', 'c0000000-0000-4000-8000-000000000001',
        (SELECT id FROM states WHERE code = 'ACTIVE'));

INSERT INTO roles_collaborators (collaborator_id, role_id)
VALUES ('f0000000-0000-4000-8000-000000000001', 'b0000000-0000-4000-8000-000000000001');
COMMIT;

-- Plan de pago de DESARROLLO para probar el cobro simulado (precios NO aprobados; la semilla oficial espera la aprobación A1).
INSERT INTO seller_plans (code, name, description, monthly_price_cop, currency, features, recommended, enabled,
                          max_branches, max_products, max_users, capabilities)
VALUES ('BUSINESS', 'Negocio', 'Plan de pago de desarrollo.', 149900, 'COP',
        '["Hasta 5 sucursales","Hasta 5.000 productos","Hasta 10 usuarios"]'::jsonb, TRUE, TRUE,
        5, 5000, 10, '["BASIC_ANALYTICS","CSV_IMPORT","ADVANCED_REPORTS","CUSTOM_ROLES"]'::jsonb)
ON CONFLICT (code) DO NOTHING;
