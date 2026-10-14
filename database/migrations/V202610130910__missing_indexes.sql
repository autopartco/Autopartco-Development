-- flyway:executeInTransaction=false
-- =============================================================================
-- V202610130910__missing_indexes.sql
-- Autopartco | PostgreSQL 16+
--
-- Propósito: índices que faltan, críticos, altos y medios, según la auditoría
--   de esquema y el inventario de consultas del backend. Cada índice indica
--   la consulta que lo justifica.
--
-- Transacción: NO. CREATE INDEX CONCURRENTLY no puede ejecutarse dentro de una
--   transacción.
--   Flyway: V202610130910__missing_indexes.sql.conf -> executeInTransaction=false
--   psql:   psql -v ON_ERROR_STOP=1 -f <archivo>      (SIN --single-transaction)
-- Bloqueos: SHARE UPDATE EXCLUSIVE, que no bloquea lecturas ni escrituras.
--
-- Si un CREATE INDEX CONCURRENTLY falla, deja un índice INVALID que
--   IF NOT EXISTS no vuelve a crear. El bloque final aborta si queda alguno.
--   Corrección: DROP INDEX CONCURRENTLY <nombre>; resolver la causa; reintentar.
--
-- ÚNICOS FUNCIONALES: antes de aplicar, ejecute las consultas de detección de
-- duplicados. Si devuelven filas, el índice único falla:
--   SELECT lower(email), count(*) FROM accounts GROUP BY 1 HAVING count(*) > 1;
--   SELECT lower(bussnies_name), count(*) FROM store GROUP BY 1 HAVING count(*) > 1;
--   SELECT lower(bussnies_email), count(*) FROM store GROUP BY 1 HAVING count(*) > 1;
--   SELECT store_id, lower(name), count(*) FROM branches WHERE deleted_at IS NULL
--     GROUP BY 1, 2 HAVING count(*) > 1;
--   SELECT upper(code), count(*) FROM seller_plans GROUP BY 1 HAVING count(*) > 1;
--   SELECT lower(code), count(*) FROM categories GROUP BY 1 HAVING count(*) > 1;
--   SELECT lower(name), count(*) FROM manufacturers GROUP BY 1 HAVING count(*) > 1;
--   SELECT product_id, specification_id, count(*) FROM products_specifications
--     WHERE deleted_at IS NULL GROUP BY 1, 2 HAVING count(*) > 1;
--   SELECT payment_id, count(*) FROM receipts GROUP BY 1 HAVING count(*) > 1;
-- Rollback: U202610130910__missing_indexes.sql
-- =============================================================================

SET lock_timeout = '30s';
SET statement_timeout = 0;

-- ---------------------------------------------------------------------------
-- CRÍTICOS
-- ---------------------------------------------------------------------------

-- Integridad: email único sin distinguir mayúsculas. Lo usa
-- AccountRepository.lockByEmail: lower(account.email) = lower(:email).
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_accounts__email_lower
    ON public.accounts (lower(email));

-- findByEmailIgnoreCase / existsByEmailIgnoreCase (login, registro, recuperación):
-- Hibernate genera upper(email) = upper(?). Sin este índice el login recorre
-- toda la tabla. Si backend unifica las consultas con lower(), se puede eliminar.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_accounts__email_upper
    ON public.accounts (upper(email));

-- Filtro por compatibilidad (contrato futuro ?compatibilityId=) y FK
-- compatibility_id. La PK (product_id, compatibility_id) no sirve si la
-- búsqueda empieza por compatibility_id.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_compatibilies__compatibility_product
    ON public.products_compatibilies (compatibility_id, product_id);

-- ListingQueries.STORE_LISTINGS_SQL: WHERE p.store_id = ? ORDER BY p.name;
-- ProductRepository.findOwned y productCatalog.countInStore.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products__store_name
    ON public.products (store_id, name)
    WHERE deleted_at IS NULL;

-- AccountSessionRepository: WHERE account_id = ? AND revoked_at IS NULL
-- ORDER BY created_at DESC. El bloque DO de init.sql (319-337) eliminó el
-- UNIQUE(account_id) y la FK quedó sin índice.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_sessions__account_active
    ON public.account_sessions (account_id, created_at DESC)
    WHERE revoked_at IS NULL AND deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- ALTOS: unicidad sin distinguir mayúsculas
-- ---------------------------------------------------------------------------

-- StoreRepository: lower(s.businessName) = lower(:name).
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_store__business_name_lower
    ON public.store (lower(bussnies_name));

-- StoreRepository: lower(s.businessEmail) = lower(:email).
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_store__business_email_lower
    ON public.store (lower(bussnies_email));

-- BranchRepository.nameExists / nameUsedByOther: store_id = ? AND
-- lower(name) = lower(?). @SQLRestriction añade deleted_at IS NULL.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_branches__store_name_lower
    ON public.branches (store_id, lower(name))
    WHERE deleted_at IS NULL;

-- PlanRepository.*CodeIgnoreCase -> upper(code).
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_seller_plans__code_upper
    ON public.seller_plans (upper(code));

-- CategoryRepository (nativa): lower(code) = lower(:code).
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_categories__code_lower
    ON public.categories (lower(code));

-- ManufacturerRepository (nativa): lower(name) = lower(:name).
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_manufacturers__name_lower
    ON public.manufacturers (lower(name));

-- StaffInvitationRepository.findPendingByEmail usa upper(email). El índice
-- existente es lower(email) y no lo aprovecha. No se cambia el único
-- existente; se añade el que sirve a la consulta.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_collaborator_invitations__store_email_upper
    ON public.seller_collaborator_invitations (store_id, upper(email))
    WHERE accepted_at IS NULL AND revoked_at IS NULL AND deleted_at IS NULL;

-- StaffInvitationRepository.findPendingInStore: ORDER BY created_at DESC.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_collaborator_invitations__store_pending_created
    ON public.seller_collaborator_invitations (store_id, created_at DESC)
    WHERE accepted_at IS NULL AND revoked_at IS NULL AND deleted_at IS NULL;

-- Tablas sin uso actual (sin datos): unicidad de negocio.
CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_products_specifications__product_specification
    ON public.products_specifications (product_id, specification_id)
    WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX CONCURRENTLY IF NOT EXISTS uq_receipts__payment
    ON public.receipts (payment_id);

-- ---------------------------------------------------------------------------
-- MEDIOS: índices compuestos de consultas reales
-- ---------------------------------------------------------------------------

-- SaleQueries.list y monthlySummary: sales JOIN branches WHERE
-- b.store_id = ? ORDER BY sale.created_at DESC, id LIMIT/OFFSET.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales__branch_created
    ON public.sales (branch_id, created_at DESC, id)
    WHERE deleted_at IS NULL;

-- SaleQueries.list LATERAL: payments WHERE order_id = ? ORDER BY created_at
-- DESC LIMIT 1. Sustituye a idx_payments_order (se elimina en 0915).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments__order_created
    ON public.payments (order_id, created_at DESC);

-- ListingQueries.PUBLISHED_LISTING_SQL: p.id = ? AND i.stock > 0 ORDER BY
-- i.price. Sustituye a idx_inventory_price (se elimina en 0915).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventory__product_price_in_stock
    ON public.inventory (product_id, price)
    WHERE deleted_at IS NULL AND stock > 0;

-- LoginAttemptService: intentos recientes por cuenta.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_login_attempts__account_attempted
    ON public.login_attempts (account_id, attempted_at DESC);

-- Solicitudes del solicitante (StoreApplications / StoreSubscription).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_applications__applicant_created
    ON public.seller_applications (applicant_account_id, created_at DESC);

-- Pagos de suscripción por solicitud.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_payments__application_created
    ON public.seller_payments (application_id, created_at DESC);

-- AccountTokenRepository / EmailVerificationTokenService (tokens por cuenta y
-- propósito; también sirve para la FK account_id).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_tokens__account_purpose
    ON public.account_tokens (account_id, purpose);

-- Purga de expirados (job futuro).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_tokens__expiration
    ON public.account_tokens (expiration_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_account_sessions__expired
    ON public.account_sessions (expired_at);

-- PersonAddressRepository.findAllOfPerson: ORDER BY is_default DESC, created_at DESC.
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_person_addresses__person_default_created
    ON public.person_addresses (person_id, is_default DESC, created_at DESC)
    WHERE deleted_at IS NULL;

-- CollaboratorRepository.countActiveInStore / StaffDirectory (collab_store_id = ?).
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_collaborators__store_state
    ON public.collaborators (collab_store_id, state)
    WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------------
-- MEDIOS: FK sin índice (borrados y joins por la FK)
-- ---------------------------------------------------------------------------
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_roles_collaborators__role
    ON public.roles_collaborators (role_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_roles_permissions__permission
    ON public.roles_permissions (permission_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_branches_collaborators__branch
    ON public.branches_collaborators (branch_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_units_specifications__unit
    ON public.units_specifications (unit_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_specifications__specification
    ON public.products_specifications (specification_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products_specifications__specification_unit
    ON public.products_specifications (specification_unit_id)
    WHERE specification_unit_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_order_details__inventory
    ON public.order_details (inventory_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments__payment_method
    ON public.payments (payment_method_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payments__payment_status
    ON public.payments (payment_status_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_payment_events__payment_created
    ON public.payment_events (payment_id, created_at);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_receipts__order
    ON public.receipts (order_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_shipments__shipment_status
    ON public.shipments (shipment_status_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_store_reviews__person
    ON public.store_reviews (person_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_store_reviews__order
    ON public.store_reviews (order_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims__person
    ON public.claims (person_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims__order
    ON public.claims (order_id) WHERE order_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims__store
    ON public.claims (store_id) WHERE store_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_claims__state
    ON public.claims (state_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_audit_logs__account
    ON public.audit_logs (account_id) WHERE account_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_inventory_movements__order_detail
    ON public.inventory_movements (order_detail_id) WHERE order_detail_id IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_person_addresses__location
    ON public.person_addresses (location_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_collaborator_invitations__invited_by
    ON public.seller_collaborator_invitations (invited_by_account_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_collaborator_invitations__role
    ON public.seller_collaborator_invitations (role_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compatibilities__model
    ON public.compatibilities (model_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_compatibilities__version
    ON public.compatibilities (version_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_applications__plan
    ON public.seller_applications (plan_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_applications__selected_plan
    ON public.seller_applications (selected_plan_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_applications__reviewed_by
    ON public.seller_applications (reviewed_by) WHERE reviewed_by IS NOT NULL;
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_seller_payments__plan
    ON public.seller_payments (plan_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_products__state
    ON public.products (state_id);
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_sales__state
    ON public.sales (state_id);

-- ---------------------------------------------------------------------------
-- Verificación: ningún índice inválido en public
-- ---------------------------------------------------------------------------
DO $$
DECLARE
    invalid TEXT;
BEGIN
    SELECT string_agg(c.relname, ', ')
    INTO invalid
    FROM pg_index i
    JOIN pg_class c ON c.oid = i.indexrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public' AND NOT i.indisvalid;

    IF invalid IS NOT NULL THEN
        RAISE EXCEPTION 'Índices INVALID tras CREATE INDEX CONCURRENTLY: %. Elimínelos con DROP INDEX CONCURRENTLY y reintente.', invalid;
    END IF;
END $$;

RESET lock_timeout;
RESET statement_timeout;
