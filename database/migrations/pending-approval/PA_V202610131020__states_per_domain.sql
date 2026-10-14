-- REQUIERE APROBACIÓN: restringe los estados permitidos por dominio
-- =============================================================================
-- V202610131020__states_per_domain.sql
-- Autopartco | PostgreSQL 16+ | Auditoría punto 15
--
-- Problema: la tabla states se comparte entre dominios. Hoy nada impide que
--   una cuenta quede SHIPPED o un pedido LOCKED.
-- Solución sin tocar states ni el backend: tabla puente public.state_domains
--   (state_id, domain) y un trigger que valida que el estado asignado
--   pertenece al dominio de la tabla.
-- Mapeo según el código actual (StateCodes, SaleStatusService, ProductCatalog,
--   BranchService, StoreService, AccountStatusService, AcceptStaffInvitation):
--   ACCOUNT       ACTIVE, PENDING, LOCKED, INACTIVE
--   STORE         ACTIVE, PENDING, INACTIVE
--   BRANCH        ACTIVE, INACTIVE
--   COLLABORATOR  ACTIVE, LOCKED, INACTIVE
--   PRODUCT       ACTIVE, INACTIVE
--   ORDER / SALE  PENDING, CONFIRMED, PREPARING, SHIPPED, DELIVERED, CANCELLED, REJECTED
--   CLAIM         PENDING, CONFIRMED, REJECTED, CANCELLED
-- RIESGO: si el backend asigna un estado no listado, la operación falla
--   (23514). Detección previa (debe dar 0 filas):
--   SELECT 'accounts', s.code FROM accounts a JOIN states s ON s.id = a.state_id
--   WHERE s.code NOT IN ('ACTIVE','PENDING','LOCKED','INACTIVE')  -- repetir por tabla
-- Respaldo: no modifica datos (solo añade tabla y triggers).
-- Al aprobar: muévalo a database/migrations/ con un timestamp nuevo.
-- Transacción: SÍ. Rollback: U202610131020__states_per_domain.sql
-- =============================================================================

SET LOCAL lock_timeout = '5s';

CREATE TABLE IF NOT EXISTS public.state_domains (
    state_id   UUID        NOT NULL,
    domain     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_state_domains PRIMARY KEY (state_id, domain),
    CONSTRAINT fk_state_domains__state_id FOREIGN KEY (state_id) REFERENCES public.states (id) ON DELETE CASCADE,
    CONSTRAINT chk_state_domains__domain CHECK (domain IN
        ('ACCOUNT', 'STORE', 'BRANCH', 'COLLABORATOR', 'PRODUCT', 'ORDER', 'SALE', 'CLAIM'))
);

INSERT INTO public.state_domains (state_id, domain)
SELECT s.id, m.domain
FROM (VALUES
    ('ACCOUNT', ARRAY['ACTIVE', 'PENDING', 'LOCKED', 'INACTIVE']),
    ('STORE', ARRAY['ACTIVE', 'PENDING', 'INACTIVE']),
    ('BRANCH', ARRAY['ACTIVE', 'INACTIVE']),
    ('COLLABORATOR', ARRAY['ACTIVE', 'LOCKED', 'INACTIVE']),
    ('PRODUCT', ARRAY['ACTIVE', 'INACTIVE']),
    ('ORDER', ARRAY['PENDING', 'CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REJECTED']),
    ('SALE', ARRAY['PENDING', 'CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REJECTED']),
    ('CLAIM', ARRAY['PENDING', 'CONFIRMED', 'REJECTED', 'CANCELLED'])
) AS m(domain, codes)
JOIN public.states s ON s.code = ANY (m.codes)
ON CONFLICT DO NOTHING;

-- Diagnóstico: aborta si hay filas existentes fuera de su dominio.
DO $$
DECLARE
    report TEXT := '';
    n      BIGINT;
    r      RECORD;
BEGIN
    FOR r IN SELECT * FROM (VALUES
        ('accounts', 'state_id', 'ACCOUNT'), ('store', 'state', 'STORE'), ('branches', 'state', 'BRANCH'),
        ('collaborators', 'state', 'COLLABORATOR'), ('products', 'state_id', 'PRODUCT'),
        ('orders', 'state_id', 'ORDER'), ('sales', 'state_id', 'SALE'), ('claims', 'state_id', 'CLAIM')
    ) AS t(tbl, col, domain) LOOP
        EXECUTE format(
            'SELECT count(*) FROM public.%I x WHERE NOT EXISTS (SELECT 1 FROM public.state_domains d WHERE d.state_id = x.%I AND d.domain = %L)',
            r.tbl, r.col, r.domain) INTO n;
        IF n > 0 THEN
            report := report || format(E'\n  %s.%s: %s filas', r.tbl, r.col, n);
        END IF;
    END LOOP;
    IF report <> '' THEN
        RAISE EXCEPTION 'Estados fuera de dominio:%', report;
    END IF;
END $$;

CREATE OR REPLACE FUNCTION public.tg_state__domain_check()
RETURNS TRIGGER LANGUAGE plpgsql SET search_path = public, pg_temp
AS $$
DECLARE
    state_value UUID := (to_jsonb(NEW) ->> TG_ARGV[0])::uuid;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM state_domains WHERE state_id = state_value AND domain = TG_ARGV[1]) THEN
        RAISE EXCEPTION 'El estado % no es válido para %', (SELECT code FROM states WHERE id = state_value), TG_ARGV[1]
            USING ERRCODE = '23514', CONSTRAINT = format('chk_%s__state_domain', TG_TABLE_NAME);
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE TRIGGER trg_accounts__state_domain BEFORE INSERT OR UPDATE OF state_id ON public.accounts
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state_id', 'ACCOUNT');
CREATE OR REPLACE TRIGGER trg_store__state_domain BEFORE INSERT OR UPDATE OF state ON public.store
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state', 'STORE');
CREATE OR REPLACE TRIGGER trg_branches__state_domain BEFORE INSERT OR UPDATE OF state ON public.branches
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state', 'BRANCH');
CREATE OR REPLACE TRIGGER trg_collaborators__state_domain BEFORE INSERT OR UPDATE OF state ON public.collaborators
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state', 'COLLABORATOR');
CREATE OR REPLACE TRIGGER trg_products__state_domain BEFORE INSERT OR UPDATE OF state_id ON public.products
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state_id', 'PRODUCT');
CREATE OR REPLACE TRIGGER trg_orders__state_domain BEFORE INSERT OR UPDATE OF state_id ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state_id', 'ORDER');
CREATE OR REPLACE TRIGGER trg_sales__state_domain BEFORE INSERT OR UPDATE OF state_id ON public.sales
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state_id', 'SALE');
CREATE OR REPLACE TRIGGER trg_claims__state_domain BEFORE INSERT OR UPDATE OF state_id ON public.claims
    FOR EACH ROW EXECUTE FUNCTION public.tg_state__domain_check('state_id', 'CLAIM');
