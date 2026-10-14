-- =============================================================================
-- U202610130920__check_constraints_not_valid.sql  (rollback de V202610130920)
-- Elimina los CHECK añadidos. Sin pérdida de datos.
-- psql -v ON_ERROR_STOP=1 --single-transaction -f <archivo>
-- =============================================================================

SET LOCAL lock_timeout = '5s';

ALTER TABLE public.seller_payments DROP CONSTRAINT IF EXISTS chk_seller_payments__status;
ALTER TABLE public.seller_payments DROP CONSTRAINT IF EXISTS chk_seller_payments__currency;
ALTER TABLE public.seller_plans DROP CONSTRAINT IF EXISTS chk_seller_plans__currency;
ALTER TABLE public.seller_plans DROP CONSTRAINT IF EXISTS chk_seller_plans__max_branches;
ALTER TABLE public.seller_plans DROP CONSTRAINT IF EXISTS chk_seller_plans__max_products;
ALTER TABLE public.seller_plans DROP CONSTRAINT IF EXISTS chk_seller_plans__max_users;
ALTER TABLE public.seller_plans DROP CONSTRAINT IF EXISTS chk_seller_plans__capabilities_array;
ALTER TABLE public.seller_plans DROP CONSTRAINT IF EXISTS chk_seller_plans__features_array;
ALTER TABLE public.seller_plans DROP CONSTRAINT IF EXISTS chk_seller_plans__trial_free;
ALTER TABLE public.account_tokens DROP CONSTRAINT IF EXISTS chk_account_tokens__purpose;
ALTER TABLE public.account_tokens DROP CONSTRAINT IF EXISTS chk_account_tokens__expiration;
ALTER TABLE public.accounts DROP CONSTRAINT IF EXISTS chk_accounts__email_normalized;
ALTER TABLE public.accounts DROP CONSTRAINT IF EXISTS chk_accounts__configuration_object;
ALTER TABLE public.store DROP CONSTRAINT IF EXISTS chk_store__business_email_normalized;
ALTER TABLE public.store DROP CONSTRAINT IF EXISTS chk_store__configuration_object;
ALTER TABLE public.branches DROP CONSTRAINT IF EXISTS chk_branches__number_phone_format;
ALTER TABLE public.persons DROP CONSTRAINT IF EXISTS chk_persons__number_phone_format;
ALTER TABLE public.persons DROP CONSTRAINT IF EXISTS chk_persons__date_birth_min;
ALTER TABLE public.locations DROP CONSTRAINT IF EXISTS chk_locations__metadata_object;
ALTER TABLE public.promotions DROP CONSTRAINT IF EXISTS chk_promotions__scope_enum;
ALTER TABLE public.promotions DROP CONSTRAINT IF EXISTS chk_promotions__target_type_enum;
ALTER TABLE public.promotions DROP CONSTRAINT IF EXISTS chk_promotions__discount_type_enum;
ALTER TABLE public.promotions DROP CONSTRAINT IF EXISTS chk_promotions__attribute_operator_enum;
ALTER TABLE public.promotions DROP CONSTRAINT IF EXISTS chk_promotions__target_id;
ALTER TABLE public.sales DROP CONSTRAINT IF EXISTS chk_sales__total_consistent;
ALTER TABLE public.payments DROP CONSTRAINT IF EXISTS chk_payments__amount_positive;
ALTER TABLE public.payments DROP CONSTRAINT IF EXISTS chk_payments__currency;
ALTER TABLE public.shipments DROP CONSTRAINT IF EXISTS chk_shipments__delivery_after_shipping;
ALTER TABLE public.shipments DROP CONSTRAINT IF EXISTS chk_shipments__tracking_not_blank;
ALTER TABLE public.seller_applications DROP CONSTRAINT IF EXISTS chk_seller_applications__trial_period;
ALTER TABLE public.seller_applications DROP CONSTRAINT IF EXISTS chk_seller_applications__onboarding_token_pair;
ALTER TABLE public.seller_collaborator_invitations DROP CONSTRAINT IF EXISTS chk_seller_collaborator_invitations__email_normalized;
ALTER TABLE public.seller_collaborator_invitations DROP CONSTRAINT IF EXISTS chk_seller_collaborator_invitations__expiration;
ALTER TABLE public.seller_collaborator_invitations DROP CONSTRAINT IF EXISTS chk_seller_collaborator_invitations__single_outcome;
ALTER TABLE public.seller_application_documents DROP CONSTRAINT IF EXISTS chk_seller_application_documents__content_type;
ALTER TABLE public.seller_application_documents DROP CONSTRAINT IF EXISTS chk_seller_application_documents__max_size;
ALTER TABLE public.product_images DROP CONSTRAINT IF EXISTS chk_product_images__content_type;
ALTER TABLE public.products DROP CONSTRAINT IF EXISTS chk_products__attributes_object;
ALTER TABLE public.products DROP CONSTRAINT IF EXISTS chk_products__name_not_blank;
ALTER TABLE public.audit_logs DROP CONSTRAINT IF EXISTS chk_audit_logs__metadata_object;
