-- =============================================================================
-- U202610130925__tenant_isolation_triggers.sql  (rollback de V202610130925)
-- Elimina los triggers y funciones de aislamiento. Sin pérdida de datos.
-- psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- =============================================================================

SET LOCAL lock_timeout = '5s';

DROP TRIGGER IF EXISTS trg_roles_collaborators__same_store ON public.roles_collaborators;
DROP TRIGGER IF EXISTS trg_seller_collaborator_invitations__role_same_store_ins ON public.seller_collaborator_invitations;
DROP TRIGGER IF EXISTS trg_seller_collaborator_invitations__role_same_store_upd ON public.seller_collaborator_invitations;
DROP TRIGGER IF EXISTS trg_branches_collaborators__same_store ON public.branches_collaborators;
DROP TRIGGER IF EXISTS trg_inventory__same_store_ins ON public.inventory;
DROP TRIGGER IF EXISTS trg_inventory__same_store_upd ON public.inventory;
DROP TRIGGER IF EXISTS trg_order_details__consistency_ins ON public.order_details;
DROP TRIGGER IF EXISTS trg_order_details__consistency_upd ON public.order_details;
DROP TRIGGER IF EXISTS trg_inventory_movements__order_detail_match ON public.inventory_movements;
DROP TRIGGER IF EXISTS trg_receipts__payment_order_match ON public.receipts;
DROP TRIGGER IF EXISTS trg_store_reviews__order_consistency ON public.store_reviews;
DROP TRIGGER IF EXISTS trg_claims__order_consistency ON public.claims;
DROP TRIGGER IF EXISTS trg_promotions__product_same_store_ins ON public.promotions;
DROP TRIGGER IF EXISTS trg_promotions__product_same_store_upd ON public.promotions;
DROP TRIGGER IF EXISTS trg_payments__single_approved_ins ON public.payments;
DROP TRIGGER IF EXISTS trg_payments__single_approved_upd ON public.payments;
DROP TRIGGER IF EXISTS trg_roles__store_immutable ON public.roles;
DROP TRIGGER IF EXISTS trg_collaborators__store_immutable ON public.collaborators;
DROP TRIGGER IF EXISTS trg_branches__store_immutable ON public.branches;
DROP TRIGGER IF EXISTS trg_seller_collaborator_invitations__store_immutable ON public.seller_collaborator_invitations;
DROP TRIGGER IF EXISTS trg_seller_applications__store_immutable ON public.seller_applications;
DROP TRIGGER IF EXISTS trg_sales__branch_immutable ON public.sales;
DROP TRIGGER IF EXISTS trg_products__store_immutable ON public.products;

DROP FUNCTION IF EXISTS public.tg_roles_collaborators__same_store();
DROP FUNCTION IF EXISTS public.tg_invitations__role_same_store();
DROP FUNCTION IF EXISTS public.tg_branches_collaborators__same_store();
DROP FUNCTION IF EXISTS public.tg_inventory__same_store();
DROP FUNCTION IF EXISTS public.tg_order_details__consistency();
DROP FUNCTION IF EXISTS public.tg_inventory_movements__order_detail_match();
DROP FUNCTION IF EXISTS public.tg_receipts__payment_order_match();
DROP FUNCTION IF EXISTS public.tg_order_person_store__consistency();
DROP FUNCTION IF EXISTS public.tg_promotions__product_same_store();
DROP FUNCTION IF EXISTS public.tg_payments__single_approved();
DROP FUNCTION IF EXISTS public.tg_tenant_key__immutable();
