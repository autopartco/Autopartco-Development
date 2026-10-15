-- =============================================================================
-- U202610131020__states_per_domain.sql  (rollback)
-- Quita la validación por dominio. Sin pérdida de datos de negocio.
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DROP TRIGGER IF EXISTS trg_accounts__state_domain ON public.accounts;
DROP TRIGGER IF EXISTS trg_store__state_domain ON public.store;
DROP TRIGGER IF EXISTS trg_branches__state_domain ON public.branches;
DROP TRIGGER IF EXISTS trg_collaborators__state_domain ON public.collaborators;
DROP TRIGGER IF EXISTS trg_products__state_domain ON public.products;
DROP TRIGGER IF EXISTS trg_orders__state_domain ON public.orders;
DROP TRIGGER IF EXISTS trg_sales__state_domain ON public.sales;
DROP TRIGGER IF EXISTS trg_claims__state_domain ON public.claims;
DROP FUNCTION IF EXISTS public.tg_state__domain_check();
DROP TABLE IF EXISTS public.state_domains;
