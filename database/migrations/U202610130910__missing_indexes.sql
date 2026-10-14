-- flyway:executeInTransaction=false
-- =============================================================================
-- U202610130910__missing_indexes.sql  (rollback de V202610130910)
-- Elimina los índices creados por la V. No hay pérdida de datos; las consultas
-- vuelven al rendimiento anterior. Aplique antes U202610130915, que recrea
-- idx_inventory_price e idx_payments_order.
-- Transacción: NO (DROP INDEX CONCURRENTLY). Flyway: .sql.conf con
--   executeInTransaction=false. psql: psql -v ON_ERROR_STOP=1 -f <archivo>
-- =============================================================================

SET lock_timeout = '30s';

DROP INDEX CONCURRENTLY IF EXISTS public.idx_sales__state;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products__state;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_payments__plan;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_applications__reviewed_by;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_applications__selected_plan;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_applications__plan;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_compatibilities__version;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_compatibilities__model;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_collaborator_invitations__role;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_collaborator_invitations__invited_by;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_person_addresses__location;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_inventory_movements__order_detail;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_audit_logs__account;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_claims__state;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_claims__store;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_claims__order;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_claims__person;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_store_reviews__order;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_store_reviews__person;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_shipments__shipment_status;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_receipts__order;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_payment_events__payment_created;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_payments__payment_status;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_payments__payment_method;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_order_details__inventory;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products_specifications__specification_unit;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products_specifications__specification;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_units_specifications__unit;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_branches_collaborators__branch;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_roles_permissions__permission;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_roles_collaborators__role;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_collaborators__store_state;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_person_addresses__person_default_created;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_account_sessions__expired;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_account_tokens__expiration;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_account_tokens__account_purpose;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_payments__application_created;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_applications__applicant_created;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_login_attempts__account_attempted;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_inventory__product_price_in_stock;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_payments__order_created;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_sales__branch_created;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_receipts__payment;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_products_specifications__product_specification;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_collaborator_invitations__store_pending_created;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_seller_collaborator_invitations__store_email_upper;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_manufacturers__name_lower;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_categories__code_lower;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_seller_plans__code_upper;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_branches__store_name_lower;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_store__business_email_lower;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_store__business_name_lower;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_account_sessions__account_active;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products__store_name;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_products_compatibilies__compatibility_product;
DROP INDEX CONCURRENTLY IF EXISTS public.idx_accounts__email_upper;
DROP INDEX CONCURRENTLY IF EXISTS public.uq_accounts__email_lower;

RESET lock_timeout;
