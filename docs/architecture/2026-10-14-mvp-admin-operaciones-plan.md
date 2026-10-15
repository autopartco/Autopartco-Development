# Plan: MVP de administración y operaciones internas (consola `/admin`)

> Orden de ejecución, despliegues conjuntos y aprobaciones: [`2026-10-14-mvp-orden-global.md`](2026-10-14-mvp-orden-global.md).

- Fecha: 2026-10-14. Base: `develop`. Agente autor: arquitecto.
- Planes paralelos: `2026-10-14-mvp-saas-vendedores-plan.md` (SaaS) y `2026-10-14-mvp-marketplace-plan.md` (marketplace).
- Secuencia: `database` → `backend` → `frontend` → `qa-seguridad`.
- Backlog cubierto: BL-F15, BL-F19, BL-F20 (UI de planes; catálogo maestro fuera).
- Dueño único en este plan: `WebSecurityConfig` (AD-B3, incluidas las rutas públicas de tiendas del marketplace), `AuditService.record` (AD-B2), `internal/Store.java` (AD-B1), `/seller/setup` y `/vender/solicitud` (AD-F4). Depende de: `PageResponse` (MK-B1/MK-F1), tokens y componentes base (MK-F0), `Subscriptions.startTrial` (SA-B2), backend de `/admin/plans` (SA-B4).

## 1. Objetivo y alcance

**Entra**
1. Bandeja de solicitudes de vendedor: filtros por estado, búsqueda, paginación, asignación y detalle con datos, documentos, notas internas e historial.
2. Decisiones: aprobar, rechazar (motivo obligatorio visible al solicitante) y pedir corrección. Cada decisión queda en historial y auditoría y se notifica al solicitante.
3. Solicitante: formulario "Vende en Autopartco" (se mejora `/vender/registro`), página de estado, corrección y reenvío, y página de configuración (`/seller/setup`).
4. Consola ROOT: UI del catálogo de planes (endpoints del plan SaaS) y listado de tiendas con suspensión y reactivación.
5. Rol `PLATFORM_REVIEWER` (revisor/soporte): solicitudes sí; planes y tiendas no.

**No entra**: moderación de productos o reseñas, disputas, tickets de soporte, métricas, retención o borrado automático de documentos, rate limiting nuevo, alta de revisores por UI (se hace con SQL aprobado), Flyway.

## 2. Estado actual verificado

| Pieza | Estado | Dónde |
|---|---|---|
| `GET /admin/store-applications/pending` (lista completa, sin paginar) | Existe | `operations/storeverification/StoreVerificationController` |
| `PATCH /admin/store-applications/{id}/review` `{approved, notes}` | Existe (solo aprobar/rechazar; el motivo no es obligatorio) | `ReviewStoreApplication`, `StoreApplications.review` |
| `GET /admin/store-applications/{id}/documents/{docId}` | Existe (attachment) | `DownloadApplicationDocument` |
| `POST /store-applications` (público, multipart) | Existe | `storesuite/onboarding/StoreApplicationController` |
| `POST /me/store-applications` y `GET /me/store-applications` | Existe | ídem |
| `POST /store-applications/setup` (token, activa la tienda, `startTrial`, promueve a `STORE_MANAGER`) | Existe | `CompleteStoreSetup` |
| `GET/POST/PUT/DELETE /admin/plans` | Existe (solo ROOT) | `operations/plans/PlanAdminController`, `core/billing/PlanService` |
| Detalle, paginación, búsqueda, asignación, notas e historial | Falta | — |
| Estado "pedir corrección" y reenvío del solicitante | Falta | `StoreApplicationStatus` = `PENDING_REVIEW, CONFIGURATION_PENDING, REJECTED, ACTIVE` |
| Representante, contacto, dirección DIVIPOLA, DV del NIT, consentimiento Ley 1581 | Falta | `StoreRegistrationRequest` = `businessName, nit, businessEmail` |
| Auditoría de decisiones | Falta | `core/audit/AuditService` solo tiene métodos de identidad; `audit_logs` es de solo inserción (V202610130930) |
| Notificación del rechazo | Falta (solo hay correo de aprobación) | `EmailService.sendSellerOnboarding`, `NotificationService.notify` |
| Listado admin de tiendas, suspensión y reactivación | Falta | `StoreRepository.findPublished` filtra `ACTIVE` |
| Ocultar en el marketplace las tiendas no activas | Existe | `ListingQueries` (`ss.code = 'ACTIVE'`), `StoreService.published` |
| Rol revisor | Falta | `types_accounts` = `CUSTOMER, STORE_MANAGER, ROOT`; `/admin/**` exige `ROLE_ROOT` (`WebSecurityConfig`) |
| `accountType` en `GET /me/profile` | Falta | `ProfileResponse` |
| Frontend `/vender`, `/vender/registro` | Existe (público; no envía `planId`) | `features/seller/pages/seller-register-page.tsx`, `features/marketplace/pages/sell-page.tsx` |
| Frontend `/admin/*`, estado de la solicitud, `/seller/setup` | Falta | `app/router.tsx` |
| Componentes UI `table`, `dialog`, `alert-dialog`, `textarea`, `dropdown-menu` | Falta | `components/ui/` |
| Fuente | Geist (`@fontsource-variable/geist`), no Inter | `src/index.css` |

En el código, "organización" es la tabla `store`. Al enviar la solicitud ya se crean la tienda (estado `PENDING`) y el colaborador `OWNER` del solicitante (`StoreService.register`). Al aprobar no se crea ninguna tienda.

## 3. Componentes y responsabilidades

| Componente | Responsabilidad |
|---|---|
| `core/organization` `StoreApplications` | Máquina de estados, datos de la solicitud, eventos, notas y bloqueo pesimista. No conoce billing ni identity más allá de `AccountLookup`. |
| `core/organization` `StoreApplicationEvent`, `StoreApplicationNote`, `StoreStatusHistory` (nuevas, `internal/`) | Historial de transiciones, notas internas e historial de suspensiones. |
| `core/organization` `Nit` (nuevo) | Normaliza el NIT y calcula el DV con el algoritmo DIAN (módulo 11). |
| `core/organization` `StoreService` | Listado admin, suspensión y reactivación. |
| `operations/storeverification` | Casos de uso admin: listar, detalle, asignar, decidir, notas, reenviar enlace y descarga auditada. Orquesta la aprobación con billing e identity. |
| `operations/stores` (nuevo) | Casos de uso admin de tiendas: listar, cambiar estado e historial. |
| `storesuite/onboarding` | Envío con los datos nuevos, estado de la solicitud y corrección. `CompleteStoreSetup` conserva la llamada idempotente a `startTrial` (§5.4). |
| `core/audit` `AuditService` | Método genérico `record(...)` sin datos personales. |
| `core/notification` | Correos nuevos y notificación in-app, enviados después del commit. |
| `core/billing` (lo cambia el plan SaaS: SA-B2, SA-B4) | `startTrial` idempotente y endpoints de planes. Ver §5.5. |
| `frontend/src/features/admin` (nuevo) | Consola: shell, solicitudes, planes y tiendas. |
| `frontend/src/features/seller` | Formulario, estado, corrección y configuración. |

## 4. Máquina de estados (`seller_applications.status`)

| Desde | Hacia | Acción | Quién |
|---|---|---|---|
| — | `PENDING_REVIEW` | `SUBMITTED` | solicitante |
| `PENDING_REVIEW` | `IN_REVIEW` | `CLAIMED` (asigna al revisor) | ROOT, PLATFORM_REVIEWER |
| `IN_REVIEW` | `PENDING_REVIEW` | `RELEASED` | el asignado o ROOT |
| `PENDING_REVIEW`, `IN_REVIEW` | `CONFIGURATION_PENDING` | `APPROVED` | el asignado o ROOT (desde `PENDING_REVIEW` se autoasigna) |
| `PENDING_REVIEW`, `IN_REVIEW` | `REJECTED` (final) | `REJECTED`, mensaje obligatorio | ídem |
| `PENDING_REVIEW`, `IN_REVIEW` | `NEEDS_CHANGES` | `CHANGES_REQUESTED`, mensaje obligatorio | ídem |
| `NEEDS_CHANGES` | `PENDING_REVIEW` | `RESUBMITTED` (`resubmissionCount++`, quita la asignación) | solicitante |
| `CONFIGURATION_PENDING` | `CONFIGURATION_PENDING` | `ONBOARDING_LINK_RESENT` (token nuevo) | ROOT, PLATFORM_REVIEWER |
| `CONFIGURATION_PENDING` | `ACTIVE` | `SETUP_COMPLETED` | solicitante (token) |

- Cualquier otra transición: `409 SELLER_APPLICATION_TRANSITION_INVALID`.
- Si está `IN_REVIEW` y asignada a otro revisor que no es ROOT: `409 SELLER_APPLICATION_ASSIGNED_TO_OTHER`.
- `requireNoOpenApplication` también bloquea `IN_REVIEW` y `NEEDS_CHANGES`.
- Tras `REJECTED` la cuenta puede enviar una solicitud nueva, pero con otro NIT, nombre y correo del negocio (D7).

### Estado de la tienda (`store.state`, dominio STORE = `ACTIVE`, `PENDING`, `INACTIVE`)

| Desde | Hacia | Acción | Quién |
|---|---|---|---|
| `PENDING` | `ACTIVE` | configuración completada | solicitante (token) |
| `ACTIVE` | `INACTIVE` (API: `SUSPENDED`) | suspender, motivo obligatorio | ROOT |
| `INACTIVE` | `ACTIVE` | reactivar | ROOT |

## 5. Contratos

### 5.1 Base de datos: `database/migrations/V202610141400__seller_application_review.sql` + `U202610141400__seller_application_review.sql`

Es autónoma: solo depende de `init.sql`, para poder aplicarla con `psql` antes de Flyway. Columnas de tiempo nuevas en `TIMESTAMPTZ`.

```sql
-- 1. Estados nuevos (el CHECK de init.sql es inline: localizar su nombre en pg_constraint)
ALTER TABLE seller_applications DROP CONSTRAINT <check de status>;
ALTER TABLE seller_applications ADD CONSTRAINT chk_seller_applications__status CHECK (status IN
  ('PENDING_REVIEW','IN_REVIEW','NEEDS_CHANGES','CONFIGURATION_PENDING','REJECTED','ACTIVE'));

-- 2. Datos de la solicitud (nullable por las filas existentes; la obligatoriedad la valida el backend)
ALTER TABLE seller_applications
  ADD COLUMN legal_representative_name      VARCHAR(200),
  ADD COLUMN legal_representative_id_type   VARCHAR(5)  CHECK (legal_representative_id_type IN ('CC','CE','PPT','PA')),
  ADD COLUMN legal_representative_id_number VARCHAR(20),
  ADD COLUMN contact_name                   VARCHAR(200),
  ADD COLUMN contact_phone                  VARCHAR(20),
  ADD COLUMN address_line                   VARCHAR(255),
  ADD COLUMN address_complement             VARCHAR(255),
  ADD COLUMN municipality_code              CHAR(5) CHECK (municipality_code ~ '^[0-9]{5}$'),
  ADD COLUMN city                           VARCHAR(150),
  ADD COLUMN department                     VARCHAR(150),
  ADD COLUMN data_consent_at                TIMESTAMPTZ,
  ADD COLUMN consent_policy_version         VARCHAR(20),
  ADD COLUMN assigned_reviewer_id           UUID REFERENCES accounts(id) ON DELETE SET NULL,
  ADD COLUMN submitted_at                   TIMESTAMPTZ,
  ADD COLUMN decided_at                     TIMESTAMPTZ,
  ADD COLUMN requested_fields               JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN resubmission_count             INTEGER NOT NULL DEFAULT 0 CHECK (resubmission_count >= 0);
UPDATE seller_applications SET submitted_at = created_at WHERE submitted_at IS NULL;  -- relleno no destructivo
CREATE INDEX idx_seller_applications__status_submitted ON seller_applications (status, submitted_at);
CREATE INDEX idx_seller_applications__assigned_reviewer ON seller_applications (assigned_reviewer_id) WHERE assigned_reviewer_id IS NOT NULL;

ALTER TABLE store ADD COLUMN nit_check_digit SMALLINT CHECK (nit_check_digit BETWEEN 0 AND 9);

-- 3. Historial (solo inserción)
CREATE TABLE seller_application_events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID NOT NULL REFERENCES seller_applications(id) ON DELETE CASCADE,
  action VARCHAR(30) NOT NULL CHECK (action IN ('SUBMITTED','CLAIMED','RELEASED','APPROVED','REJECTED',
         'CHANGES_REQUESTED','RESUBMITTED','ONBOARDING_LINK_RESENT','SETUP_COMPLETED')),
  from_status VARCHAR(30), to_status VARCHAR(30) NOT NULL,
  actor_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,   -- NULL = sistema
  applicant_message TEXT CHECK (char_length(applicant_message) <= 2000), -- visible para el solicitante
  created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_seller_application_events__application ON seller_application_events (application_id, created_at);
INSERT INTO seller_application_events (application_id, action, from_status, to_status, created_at)
  SELECT id, 'SUBMITTED', NULL, 'PENDING_REVIEW', created_at FROM seller_applications;  -- relleno

-- 4. Notas internas (solo inserción; nunca se exponen al solicitante)
CREATE TABLE seller_application_notes (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  application_id UUID NOT NULL REFERENCES seller_applications(id) ON DELETE CASCADE,
  author_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
  body TEXT NOT NULL CHECK (char_length(body) BETWEEN 1 AND 4000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_seller_application_notes__application ON seller_application_notes (application_id, created_at);

-- 5. Historial de estado de la tienda
CREATE TABLE store_status_history (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  store_id UUID NOT NULL REFERENCES store(id) ON DELETE CASCADE,
  from_state VARCHAR(30) NOT NULL, to_state VARCHAR(30) NOT NULL,
  reason TEXT CHECK (char_length(reason) <= 1000),
  actor_account_id UUID REFERENCES accounts(id) ON DELETE SET NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now());
CREATE INDEX idx_store_status_history__store ON store_status_history (store_id, created_at);

-- 6. Si existe public.forbid_update_delete() (V202610130930): triggers de solo inserción en las tres tablas.
--    Si no existe: RAISE NOTICE y continúa.

-- 7. Rol revisor (REQUIERE APROBACIÓN: cambio de seguridad)
INSERT INTO types_accounts (code, description) VALUES ('PLATFORM_REVIEWER', 'Revisor de Autopartco')
  ON CONFLICT (code) DO NOTHING;
```

`U`: borra las tablas nuevas y las columnas nuevas, y restaura el CHECK original. Antes exige `SET autopartco.allow_destructive = 'on'` y que no haya filas en `IN_REVIEW` ni `NEEDS_CHANGES`.

Mapeo JPA: `LocalDateTime` en `StoreApplication` y en las entidades nuevas (ADR 0002, opción A). **Aplicar la migración antes de arrancar el backend nuevo**: con `ddl-auto: update`, Hibernate crearía las columnas como `timestamp` y el CHECK viejo rechazaría los estados nuevos.

### 5.2 Convenciones comunes

- Paginación: depende de MK-B1 (`shared/web/PageResponse<T>`, contrato en §4.0 del plan de marketplace): `?page=1&size=20` (`size` ≤ 100) → `{ "items": [...], "page", "size", "total", "totalPages" }`.
- Errores: `{ "error": { "code", "message", "details" } }`. `400 VALIDATION_ERROR` con `details` por campo.
- Fechas: `LocalDateTime` sin zona, hora del servidor (`"2026-10-14T15:30:00"`), según ADR 0002 opción A, igual que checkout, marketplace y SaaS.
- Recurso ajeno: `404` (no `403`) para no revelar que existe.

### 5.3 Endpoints admin de solicitudes (`/api/v1/admin/store-applications`, ROOT o PLATFORM_REVIEWER)

**`GET /admin/store-applications`**
Query: `status` (CSV de estados; por defecto `PENDING_REVIEW,IN_REVIEW`), `search` (nombre, NIT o correo del negocio; mínimo 2 caracteres), `assignedToMe` (bool), `page`, `size`, `sortBy` (`submittedAt` o `businessName`) y `sortOrder` (por defecto `asc`).
```json
{ "items": [ {
    "applicationId": "uuid", "businessName": "Repuestos La 80 SAS", "nit": "900123456", "nitCheckDigit": 7,
    "legalType": "LEGAL_ENTITY", "status": "IN_REVIEW", "city": "Medellín", "department": "Antioquia",
    "submittedAt": "2026-10-14T09:30:00", "resubmissionCount": 0, "documentsCount": 3,
    "assignedReviewer": { "accountId": "uuid", "name": "Ana P." } } ],
  "page": 1, "size": 20, "total": 42, "totalPages": 3 }
```
Errores: `400 VALIDATION_ERROR` (estado desconocido, `size` > 100 o `search` < 2).

**`GET /admin/store-applications/{id}`** → `AdminApplicationDetail`
```json
{ "applicationId": "uuid", "storeId": "uuid", "status": "IN_REVIEW", "legalType": "LEGAL_ENTITY",
  "business": { "businessName": "…", "nit": "900123456", "nitCheckDigit": 7, "businessEmail": "…" },
  "legalRepresentative": { "fullName": "…", "idType": "CC", "idNumber": "…" },
  "contact": { "fullName": "…", "phone": "+573001234567" },
  "address": { "addressLine": "…", "addressComplement": null, "municipalityCode": "05001", "city": "Medellín", "department": "Antioquia" },
  "applicant": { "accountId": "uuid", "email": "…", "fullName": "…", "emailVerified": true },
  "plan": { "id": "uuid", "code": "TRY", "name": "Prueba" },
  "documents": [ { "id": "uuid", "type": "RUT", "filename": "rut.pdf", "contentType": "application/pdf", "sizeBytes": 123456, "uploadedAt": "…" } ],
  "requestedFields": [], "applicantMessage": null,
  "assignedReviewer": { "accountId": "uuid", "name": "…" },
  "submittedAt": "…", "decidedAt": null, "resubmissionCount": 0, "consentAt": "…", "consentPolicyVersion": "2026-10",
  "events": [ { "action": "SUBMITTED", "fromStatus": null, "toStatus": "PENDING_REVIEW", "actor": null, "applicantMessage": null, "createdAt": "…" } ],
  "notes": [ { "id": "uuid", "author": { "accountId": "uuid", "name": "…" }, "body": "…", "createdAt": "…" } ],
  "allowedActions": ["RELEASE", "APPROVE", "REJECT", "REQUEST_CHANGES", "ADD_NOTE"] }
```
`allowedActions` lo calcula el servidor según el estado, el rol y la asignación. La UI solo lo usa para mostrar botones. Errores: `404 SELLER_APPLICATION_NOT_FOUND`.

**`POST /admin/store-applications/{id}/assignment`** (sin cuerpo) → detalle. `PENDING_REVIEW` → `IN_REVIEW`, asignada al llamador. Errores: `409 SELLER_APPLICATION_TRANSITION_INVALID`, `409 SELLER_APPLICATION_ASSIGNED_TO_OTHER`.
**`DELETE /admin/store-applications/{id}/assignment`** → detalle. `IN_REVIEW` → `PENDING_REVIEW`. Mismos errores.

**`POST /admin/store-applications/{id}/decisions`**
```json
{ "decision": "APPROVE | REJECT | REQUEST_CHANGES",
  "applicantMessage": "Texto visible para el solicitante",
  "requestedFields": ["store.nit", "documents.RUT"],
  "internalNote": "opcional; se guarda como nota" }
```
- `applicantMessage`: obligatorio (10 a 2000 caracteres) en `REJECT` y `REQUEST_CHANGES`; opcional en `APPROVE`.
- `requestedFields`: solo en `REQUEST_CHANGES` (al menos 1). Valores permitidos: `store.businessName`, `store.nit`, `store.businessEmail`, `legalRepresentative`, `contact`, `address`, `documents.IDENTITY_DOCUMENT`, `documents.RUT`, `documents.LEGAL_REPRESENTATIVE_ID` y `documents.CERTIFICATE_OF_EXISTENCE`.
- Respuesta `200`: detalle.
- Errores: `400 VALIDATION_ERROR`, `400 SELLER_DECISION_MESSAGE_REQUIRED`, `404`, `409 SELLER_APPLICATION_TRANSITION_INVALID`, `409 SELLER_APPLICATION_ASSIGNED_TO_OTHER`, `409 SELLER_APPLICANT_EMAIL_NOT_VERIFIED` (solo `APPROVE`) y los que propague `startTrial` (`TRIAL_PLAN_NOT_CONFIGURED`).

Aprobación, en **una transacción** (`operations/storeverification/DecideStoreApplication`):
1. `StoreApplications.decide(...)`: bloqueo pesimista, valida la transición, pasa a `CONFIGURATION_PENDING`, genera el token de onboarding (48 h, solo se guarda el hash), fija `decided_at` y escribe el evento `APPROVED`.
2. `AccountProvisioning.promoteToStoreManager(applicantId)` (idempotente).
3. Plan SaaS: `Subscriptions.startTrial(storeId, now)` (§5.5).
4. `AuditService.record(...)`.
5. Publica `StoreApplicationDecidedEvent`. Después del commit (`@TransactionalEventListener(AFTER_COMMIT)`): correo y notificación in-app. Si el correo falla, se registra sin PII y no se revierte la decisión; el revisor puede reenviar el enlace.

Rechazo y corrección: pasos 1, 4 y 5. El mensaje se guarda en `review_notes` (lo que ve el solicitante) y en el evento.

**`GET /admin/store-applications/{id}/notes`** → `[{ id, author, body, createdAt }]`.
**`POST /admin/store-applications/{id}/notes`** `{ "body": "1..4000" }` → `201` + nota. Las notas no se editan ni se borran.

**`POST /admin/store-applications/{id}/onboarding-link`** (sin cuerpo) → `204`. Solo en `CONFIGURATION_PENDING`: token nuevo (invalida el anterior), correo después del commit y evento `ONBOARDING_LINK_RESENT`. Error: `409 SELLER_APPLICATION_TRANSITION_INVALID`.

**`GET /admin/store-applications/{id}/documents/{documentId}`** (existe): añadir `Cache-Control: no-store`, `X-Content-Type-Options: nosniff` y auditoría `SELLER_DOCUMENT_ACCESSED`.

**Compatibilidad:** `GET /pending` y `PATCH /{id}/review` delegan en los casos nuevos (`approved=false` → `REJECT`, con el mismo mínimo de 10 caracteres). Se marcan `@Deprecated` y se eliminan cuando el frontend esté conectado (tarea AD-B7).

### 5.4 Endpoints del solicitante

**`POST /store-applications`** (público) y **`POST /me/store-applications`** (autenticado): mismas partes multipart. La parte `request` añade campos:
```json
{ "account": { "…RegisterRequest (solo en el público)" },
  "store": { "businessName": "…", "nit": "900123456", "nitCheckDigit": 7, "businessEmail": "…" },
  "legalType": "LEGAL_ENTITY",
  "planId": null,
  "details": {
    "legalRepresentative": { "fullName": "…", "idType": "CC", "idNumber": "…" },
    "contact": { "fullName": "…", "phone": "3001234567" },
    "address": { "addressLine": "Cra 80 # 30-12", "addressComplement": null, "municipalityCode": "05001", "city": "Medellín", "department": "Antioquia" },
    "dataProcessingConsent": true,
    "consentPolicyVersion": "2026-10" } }
```
Reglas de validación:
- `store.nit`: 6 a 10 dígitos, sin DV ni guion.
- `nitCheckDigit`: 0 a 9, debe coincidir con el DV de DIAN. Si no: `400 STORE_NIT_CHECK_DIGIT_INVALID`.
- `legalRepresentative`: obligatorio si `LEGAL_ENTITY`. Si `NATURAL_PERSON` se usan los datos del solicitante.
- `contact.phone`: `^(\+57)?(3\d{9}|60\d{8})$`; se guarda normalizado como `+57…`.
- `municipalityCode`: 5 dígitos, y los 2 primeros deben ser un código de departamento válido (lista fija de 33 en `Nit`/validador). La existencia exacta se valida cuando existan las tablas `geo_*`.
- `dataProcessingConsent` debe ser `true`. Si no: `400 SELLER_DATA_CONSENT_REQUIRED`.
- `details` es obligatorio. El frontend actual dejará de funcionar hasta la tarea AD-F3 (AD-B5 y AD-F3 se despliegan juntas).

**`GET /me/store-applications`** (existe; se amplía, sin quitar campos) → por elemento añade: `applicantMessage`, `requestedFields`, `resubmissionCount`, `submittedAt`, `decidedAt`, `details` (lo enviado) y `timeline`: `[{ action, toStatus, applicantMessage, createdAt }]`. Nunca incluye notas internas, la identidad del revisor ni `CLAIMED`/`RELEASED`.

**`PUT /me/store-applications/{id}`** (multipart; `request` = `{ store, legalType, details }` sin `planId`; partes de documento opcionales que reemplazan las del mismo tipo) → `200` + elemento de `GET /me/store-applications`.
- Solo si el llamador es el solicitante y el estado es `NEEDS_CHANGES`. Pasa a `PENDING_REVIEW`, evento `RESUBMITTED`, quita la asignación y `resubmissionCount++`.
- Documento reemplazado: se actualiza la fila (el UNIQUE `application_id, document_type` no es parcial) y el objeto anterior se borra después del commit.
- Errores: `404 SELLER_APPLICATION_NOT_FOUND` (inexistente o ajena), `409 SELLER_APPLICATION_TRANSITION_INVALID`, `400 SELLER_DOCUMENTS_INCOMPLETE` (si `legalType` cambia y falta un documento), `409 STORE_*_ALREADY_EXISTS`.

**`POST /store-applications/setup`** (existe): deja de llamar a `startTrial` (o lo llama y obtiene la fecha ya fijada; es idempotente). Sigue activando la tienda, pasando la solicitud a `ACTIVE`, haciendo `promoteToStoreManager` (idempotente) y abriendo sesión. Evento `SETUP_COMPLETED`.

**`GET /me/profile`** (existe): añade `accountType` (`CUSTOMER`, `STORE_MANAGER`, `ROOT` o `PLATFORM_REVIEWER`). La UI solo lo usa para mostrar u ocultar la consola.

### 5.5 Contrato con el plan SaaS (dependencia explícita)

Esto necesita este plan. Dueño de `Subscriptions` y de `/admin/plans`: plan SaaS (SA-B2 y SA-B4, §4.3–§4.4 de su plan); si difiere, prevalece el suyo y la tarea AD-B4 se adapta.

```java
// core/billing/Subscriptions (existe; el plan SaaS la hace idempotente)
/** Inicia la prueba del plan por defecto (hoy TRY, 1 mes) para la tienda. Idempotente: si la prueba ya empezó,
 *  no cambia nada y devuelve su fin. Se une a la transacción del llamador (REQUIRED). */
LocalDateTime startTrial(UUID storeId, LocalDateTime startsAt);
// Errores: NotFoundException TRIAL_PLAN_NOT_CONFIGURED, SELLER_APPLICATION_NOT_FOUND.

// core/billing/PlanEntitlements (existe) — para el listado de tiendas
Optional<SubscriptionAccess> accessForStore(UUID storeId);
```
- **Transacción:** la aprobación llama a `startTrial` dentro de su transacción. Si falla, se revierte todo (estado, token, promoción y evento). No hay compensación porque nada sale de la BD antes del commit.
- **Cambio de comportamiento:** la prueba empieza al aprobar, no al configurar. Las solicitudes que ya están en `CONFIGURATION_PENDING` siguen funcionando: `startTrial` en la configuración las inicia porque es idempotente.
- **Endpoints de planes que consume la UI (AD-F6):** depende de SA-B4. Contrato único (`PlanRequest` con PUT de reemplazo completo, `PlanResponse`, errores incluido `SELLER_PLAN_CAPABILITY_UNKNOWN`) en "Planes ROOT — `/admin/plans`" del §4.3 del plan SaaS; aquí no se redefine.
- **Auditoría de planes:** la hace SA-B4 con `AuditService.record` de AD-B2 (`PLAN_CREATED`, `PLAN_UPDATED`, `PLAN_DISABLED`). `@PreAuthorize("hasAuthority('ROLE_ROOT')")` en `PlanAdminController` lo pone AD-B3.

### 5.6 Contrato con el plan marketplace

- El CTA "Vende en Autopartco" enlaza a `/vender` (landing existente) o directamente a `/vender/registro`.
- Una tienda suspendida queda en `store.state = INACTIVE`. `ListingQueries` y `StoreService.published` ya filtran `ACTIVE`, así que desaparece del marketplace sin cambios (también de `PublicStoreQueries` de MK-B2, que filtra `ACTIVE`). La tarea AD-Q1 lo verifica.

### 5.7 Endpoints admin de tiendas (`/api/v1/admin/stores`, solo ROOT)

**`GET /admin/stores`** Query: `status` (`ACTIVE`, `SUSPENDED` o `PENDING`), `search`, `page`, `size`, `sortBy` (`businessName` o `createdAt`) y `sortOrder`.
```json
{ "items": [ { "storeId": "uuid", "businessName": "…", "nit": "900123456", "nitCheckDigit": 7, "businessEmail": "…",
  "status": "ACTIVE", "createdAt": "…", "branchesCount": 2,
  "subscription": { "planCode": "TRY", "trialEndsAt": "…", "paidThrough": null, "expired": false } } ],
  "page": 1, "size": 20, "total": 7, "totalPages": 1 }
```
Mapeo de estados: `INACTIVE` ↔ `SUSPENDED` en la API.

**`PATCH /admin/stores/{id}/status`** `{ "status": "SUSPENDED | ACTIVE", "reason": "10..1000 (obligatorio al suspender)" }` → fila.
- Transiciones permitidas: `ACTIVE` ↔ `SUSPENDED`. El resto: `409 STORE_STATUS_TRANSITION_INVALID`.
- Escribe `store_status_history` y la auditoría `STORE_SUSPENDED`/`STORE_REACTIVATED`.
- Después del commit, notifica al propietario (correo + in-app) con el motivo.
- Errores: `404 STORE_NOT_FOUND`, `400 STORE_SUSPENSION_REASON_REQUIRED`.

**`GET /admin/stores/{id}/status-history`** → `[{ fromStatus, toStatus, reason, actor: { accountId, name }, createdAt }]`.

### 5.8 Catálogo de errores nuevos

| Código | HTTP |
|---|---|
| `SELLER_APPLICATION_TRANSITION_INVALID` | 409 |
| `SELLER_APPLICATION_ASSIGNED_TO_OTHER` | 409 |
| `SELLER_DECISION_MESSAGE_REQUIRED` | 400 |
| `SELLER_APPLICANT_EMAIL_NOT_VERIFIED` | 409 |
| `SELLER_DATA_CONSENT_REQUIRED` | 400 |
| `STORE_NIT_CHECK_DIGIT_INVALID` | 400 |
| `STORE_STATUS_TRANSITION_INVALID` | 409 |
| `STORE_SUSPENSION_REASON_REQUIRED` | 400 |

Se mantienen los existentes: `SELLER_APPLICATION_NOT_FOUND`, `SELLER_DOCUMENT_NOT_FOUND`, `SELLER_DOCUMENTS_INCOMPLETE`, `SELLER_APPLICATION_ALREADY_EXISTS`, `STORE_*_ALREADY_EXISTS` y `SELLER_SETUP_TOKEN_INVALID`.

### 5.9 Auditoría (`AuditService.record`)

```java
public void record(UUID actorAccountId, String action, String entityType, UUID entityId, Map<String, Object> metadata);
```
- Acciones: `SELLER_APPLICATION_CLAIMED`, `_RELEASED`, `_APPROVED`, `_REJECTED`, `_CHANGES_REQUESTED`, `_RESUBMITTED`, `_ONBOARDING_LINK_RESENT`, `SELLER_APPLICATION_NOTE_ADDED`, `SELLER_DOCUMENT_ACCESSED`, `STORE_SUSPENDED`, `STORE_REACTIVATED` y `PLAN_*`.
- `metadata` solo admite estados, ids, tipos de documento, `requestedFields` y longitudes. Nunca NIT, correo, teléfono, nombres ni el texto del mensaje o de la nota.

### 5.10 Notificaciones (después del commit)

| Evento | Correo (`EmailService`, método nuevo) | In-app (`NotificationService.notify`, `REQUIRES_NEW`) |
|---|---|---|
| SUBMITTED / RESUBMITTED | `sendSellerApplicationReceived(to, name)` | "Recibimos tu solicitud" |
| APPROVED / LINK_RESENT | `sendSellerOnboarding` (existe) | "Tu almacén fue aprobado" |
| REJECTED | `sendSellerApplicationRejected(to, name, message)` | título + mensaje |
| CHANGES_REQUESTED | `sendSellerApplicationChangesRequested(to, name, message, link /vender/solicitud)` | título + mensaje |
| STORE_SUSPENDED / REACTIVATED | `sendStoreStatusChanged(to, name, suspended, reason)` | título + motivo |

### 5.11 Rutas frontend

| Ruta | Página | Acceso (solo UX; el servidor decide) |
|---|---|---|
| `/vender/registro` | Formulario mejorado (existe). Con sesión de cliente usa `POST /me/store-applications` y omite el bloque de cuenta | público |
| `/vender/solicitud` | Estado de mis solicitudes, timeline público y mensaje del revisor | autenticado |
| `/vender/solicitud/:id/corregir` | Corrección (solo `NEEDS_CHANGES`); resalta `requestedFields` | autenticado |
| `/seller/setup?token=` (alias `/vender/configurar`) | Configuración: logo, banner y descripción → `POST /store-applications/setup` | público con token |
| `/admin` | Redirige a `/admin/solicitudes` | ROOT o PLATFORM_REVIEWER |
| `/admin/solicitudes` | Bandeja; filtros en la query string (`?estado=&q=&pagina=&mias=`) | ROOT o PLATFORM_REVIEWER |
| `/admin/solicitudes/:id` | Detalle (panel derecho en `xl`, página completa en móvil) | ROOT o PLATFORM_REVIEWER |
| `/admin/planes` | Catálogo de planes (tabla + diálogo de edición) | ROOT |
| `/admin/tiendas` | Tiendas: listado, suspender y reactivar, historial | ROOT |

Servicio frontend `features/admin/api/admin-service.ts` con variantes `mock` y `http`, según `DATA_SOURCE`, igual que `seller-service.ts`. Los tipos TypeScript replican §5.3–§5.7 literalmente.

## 6. Sistema visual de la consola (delta sobre MK-F0)

Tokens (sin tokens nuevos ni alias), tipografía (Geist, D9), componentes base (`StatusBadge`, `DataTable`, `ConfirmDialog`, `ListSkeleton`, primitivas `components/ui/*`: `table`, `dialog`, `alert-dialog`, `textarea`, `dropdown-menu`, `tabs`, `sidebar`), estados de carga/vacío/error/éxito y checklist de accesibilidad: los de MK-F0 (§6 del plan de marketplace). Este plan no crea tokens ni componentes base; si falta uno reutilizable, se añade a MK-F0. Solo define:

- **Shell de la consola:** barra lateral de 15rem en `bg-asphalt` (primitiva `sidebar` de MK-F0) con `text-asphalt-muted` e ítem activo en blanco con indicador de 2px `thread-on-asphalt`. Logo `Logo tone="dark"` (`public/logo.svg`). Contenido en `bg-paper` y superficies en `bg-white` con `border-border`. En menos de `lg`, la barra lateral es un `Sheet`. Barra superior con `Breadcrumbs` (existe), `AccountMenu` (existe) y un enlace "Ver marketplace".
- **Tablas:** `DataTable density="compact"` de MK-F0 con `rowHref` (la fila entera es un enlace con foco visible). Paginación con `components/ui/pagination.tsx` sobre `PageResponseDto` (MK-F1).
- **Panel de detalle:** cabecera (nombre, `StatusBadge`, enviada hace X, asignado). Secciones con `Card`: Empresa, Representante, Contacto, Dirección, Documentos (tipo, tamaño y acciones Ver/Descargar mediante blob), Historial (timeline) y Notas internas (rótulo "Solo visible para el equipo Autopartco"). Barra de acciones `sticky bottom-0` generada a partir de `allowedActions`.
- **Estados de solicitud y tienda** (`frontend/src/features/admin/lib/status.ts`, mapa código → props de `StatusBadge` de MK-F0; icono Lucide + texto, el color nunca es la única señal):

| Estado | Texto | `tone` / `variant` | Icono |
|---|---|---|---|
| `PENDING_REVIEW` | Pendiente | `neutral` | `Clock` |
| `IN_REVIEW` | En revisión | `info` | `Eye` |
| `NEEDS_CHANGES` | Requiere cambios | `warning` | `FilePen` |
| `CONFIGURATION_PENDING` | Aprobada · falta configurar | `info` | `Settings2` |
| `ACTIVE` | Activa | `success` | `CircleCheck` |
| `REJECTED` | Rechazada | `danger` | `CircleX` |
| `SUSPENDED` | Suspendida | `danger` / `outline` | `Ban` |

- **Diálogos** (`ConfirmDialog` y `dialog` de MK-F0; contenido propio de la consola):
  - Aprobar: resume los efectos (prueba de 1 mes, correo con el enlace y rol de dueño).
  - Rechazar: `textarea` obligatoria con contador de 10 a 2000 caracteres y vista previa "Esto verá el solicitante". Botón `destructive`.
  - Pedir corrección: checkboxes de `requestedFields` + mensaje.
  - Suspender: motivo obligatorio y aviso "La tienda dejará de verse en el marketplace".
  - Foco, `Esc` y botón pendiente: los de `ConfirmDialog` (MK-F0).
- **Estados propios** (además de los de MK-F0):
  - Vacío: "No hay solicitudes pendientes", con acción "Ver todas".
  - `409`: `FormAlert` en línea con "Otro revisor actualizó esta solicitud" y botón Recargar.
  - `403`: página "No tienes acceso a esta sección".
  - `aria-live="polite"` para los resultados de filtros y acciones.

## 7. Seguridad

- **Rutas admin:** `/api/v1/admin/**` → `hasAnyAuthority('ROLE_ROOT','ROLE_PLATFORM_REVIEWER')` en `WebSecurityConfig`. Cada método además lleva `@PreAuthorize`:
  - solicitudes: `hasAnyAuthority('ROLE_ROOT','ROLE_PLATFORM_REVIEWER')`;
  - `/admin/plans/**`, `/admin/stores/**` y el resto de `/admin/*` existentes (categorías, fabricantes, etc.): `hasAuthority('ROLE_ROOT')`.
  - Prueba obligatoria: que ningún controlador bajo `/admin` quede sin `@PreAuthorize` (test de reflexión).
- **IDOR:** el solicitante solo accede a sus solicitudes y documentos (comparación por `accountId`, no por correo), y para lo ajeno responde `404`. Los ids admin son UUID y nunca se confía en ids del cuerpo.
- **Concurrencia:** `lockById` (PESSIMISTIC_WRITE) en toda transición. Las decisiones son idempotentes por estado: repetirla da `409`.
- **Documentos:** bucket privado de Supabase, descarga solo a través del backend (`StoreFileStorage` valida el tipo por contenido y el tamaño máximo de 10 MB). Respuesta `attachment`, `no-store` y `nosniff`. La vista previa en el frontend usa blob URL; no hay URLs firmadas públicas ni `inline`. Cada acceso queda auditado.
- **Ley 1581:**
  - consentimiento explícito con versión de la política guardada;
  - mínimo de datos: no se pide la fecha de nacimiento del representante;
  - las notas internas nunca llegan al solicitante;
  - sin PII en logs: logs y `audit_logs.metadata` llevan ids, no NIT, correo, teléfono ni texto;
  - la retención y el borrado de documentos de solicitudes rechazadas quedan fuera del MVP y requieren aprobación (D10).
- **Tokens de onboarding:** solo se guarda el hash, duran 48 h y son de un solo uso. Reenviar invalida el anterior.
- **Roles en el JWT:** `AuthTokenService` ya emite `ROLE_<tipo>`. `PLATFORM_REVIEWER` no recibe `ROLE_CUSTOMER`.
- **Sin dependencias nuevas.** Faltan límites de frecuencia en `POST /store-applications` (riesgo R4).

## 8. Decisiones clave

| # | Decisión | Alternativas descartadas |
|---|---|---|
| D1 | Ampliar `StoreApplicationStatus` con `IN_REVIEW` y `NEEDS_CHANGES`; conservar `CONFIGURATION_PENDING` como "aprobada" | Renombrar a `SUBMITTED`/`APPROVED`: rompe datos, el CHECK y el frontend existente |
| D2 | Iniciar la prueba al **aprobar**, en la misma transacción (contrato con el plan SaaS); `startTrial` idempotente | Mantenerla en la configuración: la prueba no empieza si el solicitante no configura, pero contradice el contrato acordado |
| D3 | La tienda pasa a `ACTIVE` (visible) solo al completar la configuración | Activarla al aprobar: aparecería en el marketplace sin marca ni sucursales |
| D4 | Correos y notificaciones después del commit (`@TransactionalEventListener`), con reenvío manual del enlace | Correo dentro de la transacción (hoy): un fallo de SMTP revierte la decisión |
| D5 | Rol `PLATFORM_REVIEWER` como `types_accounts` nuevo (una cuenta tiene un solo tipo) | Permisos granulares de plataforma: sobredimensionado para el MVP |
| D6 | Historial en `seller_application_events` + auditoría genérica en `audit_logs` | Solo `audit_logs`: no sirve de timeline público y mezcla PII |
| D7 | Una solicitud rechazada conserva su tienda `PENDING` con su NIT, nombre y correo; una solicitud nueva con el mismo NIT da `409 STORE_NIT_ALREADY_EXISTS`. Para el MVP se usa "pedir corrección" en vez de rechazar cuando el negocio puede volver a intentarlo | Liberar los únicos al rechazar (cambio destructivo de datos; requiere aprobación) |
| D8 | Suspensión = `store.state INACTIVE` (dominio STORE existente) + `store_status_history` | Estado `SUSPENDED` nuevo: obliga a cambiar `states` y `PA_V202610131020` |
| D9 | Se mantiene Geist, la fuente cargada hoy. Pasar a Inter requiere aprobación (dependencia `@fontsource-variable/inter`) | Inter sin aprobación |
| D10 | DIVIPOLA: código de 5 dígitos con prefijo de departamento validado; el frontend usa un JSON estático extraído de `R__seed_geo_divipola.sql` | Endpoint `/geo`: depende de `V202610140920`, que no está aplicada |
| D11 | Nuevo `PUT /me/store-applications/{id}` para corregir sobre la misma solicitud | Crear otra solicitud: pierde el historial y choca con los únicos de la tienda |
| D12 | Paginación con `PageResponse<T>` de MK-B1 (dueño único; D12 del plan de marketplace) | Devolver `Page` de Spring tal cual; record de página propio: duplica el contrato |

## 9. Tareas

Las tareas de la misma capa van en el orden indicado: comparten archivos.

### AD-D1. Migración de revisión de solicitudes
- **Modelo:** Sonnet. **Agente:** `database`.
- **Archivos:** `database/migrations/V202610141400__seller_application_review.sql`, `database/migrations/U202610141400__seller_application_review.sql` y la fila de la tabla en `database/migrations/README.md` (antes de SA-D1, que edita la misma tabla).
- **Contexto:** lee `docs/architecture/2026-10-14-mvp-admin-operaciones-plan.md` §5.1 y `database/init.sql` (`seller_applications`, `store`, `types_accounts`), y sigue las convenciones de `database/migrations/README.md` §1. Debe ser autónoma respecto de las otras `V` y localizar el CHECK inline de `status` consultando `pg_constraint`. Idempotente. El INSERT de `PLATFORM_REVIEWER` va en un bloque separado y comentado con "REQUIERE APROBACIÓN". No ejecutes nada contra ninguna BD.
- **Aceptación:**
  - revisión del SQL: los nombres siguen la convención;
  - los rellenos de `submitted_at` y `SUBMITTED` no son destructivos;
  - el `U` tiene el guard `autopartco.allow_destructive` y aborta si hay filas en `IN_REVIEW`/`NEEDS_CHANGES`;
  - los triggers de solo inserción son condicionales a `forbid_update_delete()`.

### AD-B1. Dominio: estados, datos de la solicitud, NIT, eventos y notas
- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:**
  - `core/organization/StoreApplicationStatus.java`, `StoreApplications.java`, `StoreApplicationView.java`, `StoreRegistrationRequest.java`;
  - nuevos: `StoreApplicationDetails.java`, `StoreApplicationDecision.java`, `Nit.java`;
  - `internal/StoreApplication.java`, `internal/StoreApplicationRepository.java`, `internal/Store.java` (`nitCheckDigit`);
  - nuevos: `internal/StoreApplicationEvent(+Repository).java`, `internal/StoreApplicationNote(+Repository).java`;
  - tests: `StoreApplicationsStateMachineTest`, `NitTest`.
- **Contexto:** plan §4, §5.1 y §5.4. Requiere AD-D1 aplicada en la BD antes de arrancar el backend (R1). Dueño único de `internal/Store.java` entre los planes del MVP (ninguna otra tarea lo edita). `Store.java` tiene cambios sin commitear en la rama `chore/backend-pending-migrations`: parte de `develop` después de integrarla. API del dominio:
  - `search(filter, page)`, `detail(id)`;
  - `claim(id, actorId, isRoot)`, `release(...)`;
  - `decide(id, actorId, isRoot, StoreApplicationDecision)` → `Reviewed`;
  - `addNote(id, authorId, body)`;
  - `resubmit(id, applicantAccountId, store, legalType, details, documents)`;
  - `regenerateOnboardingToken(id, actorId)`;
  - todas con `lockById`.
  `requireNoOpenApplication` incluye los estados nuevos. Tiempo: `LocalDateTime` en `StoreApplication` y en las entidades nuevas (ADR 0002, opción A).
- **Aceptación:**
  - `mvn -Dtest=StoreApplicationsStateMachineTest,NitTest test` en verde;
  - hay un caso por cada fila de la tabla §4 y uno por cada transición inválida (`409`);
  - DV correcto para `900123456`→`8`, `800197268`→`4` y `860002964`→`4` (pesos DIAN 3, 7, 13, 17, 19, 23, 29, 37, 41… de derecha a izquierda; residuo 0 o 1 → DV = residuo; en otro caso DV = 11 − residuo);
  - `mvn -Dtest=SubmitStoreApplicationTest test` en verde.

### AD-B2. Auditoría genérica y notificaciones después del commit
- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:**
  - `core/audit/AuditService.java`, `core/audit/internal/AuditLogMapper.java`;
  - `core/notification/EmailService.java`;
  - nuevo: `core/notification/StoreApplicationNotifier.java` (listener `AFTER_COMMIT`);
  - tests: `AuditServiceTest`, `StoreApplicationNotifierTest`.
- **Contexto:** plan §5.9 y §5.10. Dueño único de `AuditService.record` (lo usan AD-B4, AD-B6 y SA-B4). La paginación no es de esta tarea: depende de MK-B1. `NotificationService.notify` se llama con `Propagation.REQUIRES_NEW` desde el listener. Los eventos de dominio son records en `core/organization` (`StoreApplicationDecidedEvent`, `StoreStatusChangedEvent`).
- **Aceptación:**
  - con `-Dtest=AuditServiceTest,StoreApplicationNotifierTest`, `record` rechaza las claves de metadata no permitidas;
  - el listener no se ejecuta si la transacción se revierte;
  - un fallo de correo se registra sin el correo del destinatario.

### AD-B3. Seguridad: `WebSecurityConfig` (dueño único), rol revisor y `accountType` en el perfil (**requiere aprobación**)
- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:** `shared/security/WebSecurityConfig.java`, `core/identity/web/ProfileResponse.java`, `core/identity/internal/ProfileService.java`, todos los controladores de `operations/**` con `@PreAuthorize` y `src/test/.../shared/security/WebSecurityConfigTest.java`.
- **Contexto:** plan §7. Única tarea del MVP que edita `WebSecurityConfig`. `/admin/**` pasa a `hasAnyAuthority(ROOT, PLATFORM_REVIEWER)`. Todo lo que no sea solicitudes queda `ROLE_ROOT` explícito. Añade también las rutas públicas del marketplace: `new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/stores/*")` (para MK-B2) y, solo si el usuario aprueba las reseñas P2, `new PublicRoute(HttpMethod.GET, ApiPaths.MARKETPLACE + "/stores/*/reviews")` (para MK-B4). No depende de AD-B1 ni de AD-B2.
- **Aceptación:**
  - `-Dtest=WebSecurityConfigTest` en verde, con casos: el revisor recibe 403 en `/admin/plans` y `/admin/stores` y 200/404 en `/admin/store-applications`; CUSTOMER recibe 403 en todo `/admin`; `GET /marketplace/stores/{id}` sin token no responde 401; `PATCH /marketplace/stores/{id}` sin token sí responde 401;
  - un test de reflexión verifica que todos los métodos de controladores con ruta `/admin` tienen `@PreAuthorize`.

### AD-B4. Casos de uso admin de solicitudes
- **Modelo:** Opus (orquestación transaccional con billing e identity). **Agente:** `backend`.
- **Archivos:**
  - `operations/storeverification/**`: `StoreVerificationController` y, nuevos, `SearchApplications`, `GetApplicationDetail`, `AssignApplication`, `DecideStoreApplication`, `AddApplicationNote`, `ResendOnboardingLink` y los DTO de §5.3;
  - se reescriben `ReviewStoreApplication` y `ListPendingApplications` como envoltorios `@Deprecated`;
  - `DownloadApplicationDocument` (auditoría y cabeceras);
  - tests `DecideStoreApplicationTest`, `StoreVerificationControllerTest`.
- **Contexto:** plan §5.3 y §5.5. Depende de AD-B1, AD-B2, AD-B3, MK-B1 (`PageResponse`) y SA-B2 (cambio idempotente de `Subscriptions.startTrial` del plan SaaS). Si ese cambio no ha llegado, esta tarea **no** lo implementa: se bloquea y lo reporta. El rol se determina con `Authentication.getAuthorities()`; el actor con `AccountLookup.findByEmail(authentication.getName())`.
- **Aceptación:**
  - `-Dtest=DecideStoreApplicationTest,StoreVerificationControllerTest` en verde;
  - aprobar llama a `startTrial` y a `promoteToStoreManager` en la misma transacción, y si `startTrial` lanza, el estado no cambia (rollback verificado);
  - rechazar sin mensaje da `400 SELLER_DECISION_MESSAGE_REQUIRED`;
  - un revisor no asignado da `409`, y ROOT puede decidir igualmente;
  - la respuesta paginada cumple §5.2.

### AD-B5. Solicitante: envío ampliado, estado, corrección y configuración (se despliega junto con AD-F3)
- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:**
  - `storesuite/onboarding/**`: `SellerSignupRequest`, `StoreApplicationRequest`, `StoreApplicationResponse`, `SubmitStoreApplication`, `ListMyApplications`, `CompleteStoreSetup`, `StoreApplicationController`;
  - nuevos: `StoreApplicationCorrectionRequest`, `ResubmitMyApplication`;
  - tests `SubmitStoreApplicationTest`, `ResubmitMyApplicationTest`.
- **Contexto:** plan §5.4. `requireOwnDocument` y la corrección comparan `applicantAccountId`, no el correo. `CompleteStoreSetup` conserva la llamada idempotente a `startTrial`.
- **Aceptación:**
  - con `-Dtest=SubmitStoreApplicationTest,ResubmitMyApplicationTest`: un DV inválido da 400;
  - sin consentimiento da 400;
  - corregir una solicitud ajena da 404, y una que no está en `NEEDS_CHANGES` da 409;
  - el reenvío deja `PENDING_REVIEW` con `resubmissionCount = 1`;
  - `GET /me/store-applications` no expone notas ni el revisor.

### AD-B6. Admin de tiendas
- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:**
  - `core/organization/StoreService.java`, `internal/StoreRepository.java`;
  - nuevo: `internal/StoreStatusHistory(+Repository).java`;
  - nuevo paquete `operations/stores/` (`AdminStoreController`, `ListStores`, `ChangeStoreStatus`, `StoreStatusHistoryQuery`, DTO);
  - test `ChangeStoreStatusTest`.
- **Contexto:** plan §5.7. La suscripción se lee con `PlanEntitlements.accessForStore`. Lista con `PageResponse` (MK-B1). `StoreService.java` llega editado por SA-B1 (perfil): añadir métodos sin tocar los suyos. `AdminStoreController` nace con `@PreAuthorize("hasAuthority('ROLE_ROOT')")` (lo exige el test de reflexión de AD-B3).
- **Aceptación:**
  - con `-Dtest=ChangeStoreStatusTest`: `ACTIVE`→`SUSPENDED` sin motivo da 400; `PENDING`→`SUSPENDED` da 409;
  - suspender deja `store.state=INACTIVE`, una fila de historial, auditoría y el evento publicado;
  - la tienda suspendida no aparece en `StoreService.published()`.

### AD-B7. Retirar los endpoints obsoletos (después de AD-F5)
- **Modelo:** Haiku. **Agente:** `backend`.
- **Archivos:** `operations/storeverification/StoreVerificationController.java`, `ReviewStoreApplication.java`, `ListPendingApplications.java`.
- **Aceptación:**
  - `grep -r "store-applications/pending\|/review" frontend/src backend/src` sin resultados;
  - compila y `-Dtest=StoreVerificationControllerTest` en verde.

### AD-F1. Shell de la consola con todas sus rutas
- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:**
  - `frontend/src/features/admin/components/{console-shell,console-nav,admin-guard}.tsx`;
  - nuevo `frontend/src/features/admin/lib/status.ts` (mapa de estados de §6 sobre `StatusBadge` de MK-F0);
  - `frontend/src/features/admin/pages/*` como stubs;
  - `frontend/src/app/router.tsx`, `frontend/src/app/lazy-pages.tsx`;
  - `frontend/src/features/auth/api/types.ts` (`accountType`).
  - No crea `components/ui/*` ni `StatusBadge`: son de MK-F0.
- **Contexto:** depende de MK-F0. Plan §5.11 y §6. Este es el **único** cambio de `router.tsx` para rutas `/admin` y de vendedor (`/vender/solicitud`, `/vender/solicitud/:id/corregir`, `/seller/setup` y su alias); las añade apuntando a stubs, para que AD-F3 y AD-F4 no toquen el router. La guarda lee `accountType` de `GET /me/profile` solo para la UX.
- **Aceptación:**
  - `npm run build` y `npm run lint` sin errores;
  - `/admin` sin sesión redirige a `/ingresar`;
  - con un CUSTOMER muestra "No tienes acceso";
  - el shell es navegable solo con teclado, y la barra lateral se vuelve `Sheet` en menos de `lg`.

### AD-F2. Bandeja y detalle de solicitudes
- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/admin/api/{types,admin-service}.ts`, `api/http/http-admin-service.ts`, `api/mock/mock-admin-service.ts`, `components/{applications-table,application-detail,decision-dialogs,notes-panel,timeline,document-list}.tsx`, `pages/{admin-applications-page,admin-application-detail-page}.tsx`.
- **Contexto:** plan §5.3, §5.8 y §6. `applications-table` compone `DataTable` de MK-F0; listas con `PageResponseDto` de MK-F1. Los botones salen de `allowedActions`. Los filtros viven en la URL.
- **Aceptación:**
  - build y lint en verde;
  - en modo `mock`: filtrar, buscar, paginar, asignar, aprobar, rechazar (sin motivo el botón está deshabilitado) y pedir corrección;
  - un 409 simulado muestra la alerta con Recargar;
  - los estados de carga, vacío y error son visibles;
  - los diálogos devuelven el foco al disparador.

### AD-F3. Formulario de vendedor mejorado (se despliega junto con AD-B5)
- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:**
  - `frontend/src/features/seller/api/{types.ts,http/http-seller-service.ts,mock/mock-seller-service.ts}`;
  - `frontend/src/features/seller/pages/seller-register-page.tsx`;
  - nuevos: `frontend/src/features/seller/lib/{nit,divipola}.ts`, `frontend/src/features/seller/data/divipola.json`.
- **Contexto:** plan §5.4 y D10. El DV se calcula en vivo como ayuda; el servidor valida. Con sesión de cliente envía a `/me/store-applications`. Casilla de consentimiento con enlace a la política. Selector departamento → municipio a partir del JSON.
- **Aceptación:**
  - build y lint en verde;
  - un DV erróneo muestra un error en el campo;
  - `LEGAL_ENTITY` exige representante;
  - el JSON tiene 33 departamentos y unos 1.120 municipios con códigos de 5 dígitos.

### AD-F4. Estado, corrección y configuración del solicitante
- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/seller/pages/{seller-application-status-page,seller-application-correction-page,seller-setup-page}.tsx`, `frontend/src/features/seller/api/*` (métodos `listMine`, `resubmit` y `completeSetup`).
- **Contexto:** plan §5.4 y §5.11. Dueño único de `/seller/setup` (el plan SaaS no lo implementa) y de BL-F15 (`/vender/solicitud`). Rutas ya creadas por AD-F1. El timeline es público, sin notas.
- **Aceptación:**
  - build y lint en verde;
  - `NEEDS_CHANGES` muestra el mensaje y resalta `requestedFields`;
  - `REJECTED` muestra el motivo;
  - setup con un token inválido muestra `SELLER_SETUP_TOKEN_INVALID` con su mensaje;
  - setup correcto guarda la sesión nueva y redirige a `/portal/:storeId`.

### AD-F5. Conectar el modo `http` y retirar el uso de endpoints obsoletos
- **Modelo:** Haiku. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/admin/api/http/http-admin-service.ts`, `frontend/.env.example` (solo si hace falta una variable nueva; hoy no).
- **Aceptación:** con `VITE_MARKETPLACE_DATA_SOURCE=http` contra el backend local, el flujo enviar → asignar → pedir corrección → corregir → aprobar → configurar funciona. Sin llamadas a `/pending` ni `/review`.

### AD-F6. UI de planes y de tiendas
- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/admin/components/{plans-table,plan-form-dialog,stores-table,store-status-dialog,store-history}.tsx`, `pages/{admin-plans-page,admin-stores-page}.tsx`, `api/types.ts` y `admin-service.ts` (solo añadir métodos).
- **Contexto:** dueño único de la UI de `/admin/plans`; el contrato es el de SA-B4 ("Planes ROOT" en §4.3 del plan SaaS: léelo primero) y §5.7. Precio en COP con `lib/format.ts`, sin decimales. `TRY` no se puede desactivar ni cambiar de código: los controles se deshabilitan y se explica por qué.
- **Aceptación:**
  - build y lint en verde;
  - crear, editar y desactivar plan en modo mock;
  - los errores `TRIAL_PLAN_*` se muestran en el campo;
  - suspender exige motivo y muestra el aviso del marketplace;
  - el historial lista los cambios.

### AD-Q1. Revisión de seguridad y flujo
- **Modelo:** Opus. **Agente:** `qa-seguridad`.
- **Alcance:**
  - ejecutar `mvn -Dtest=WebSecurityConfigTest,StoreApplicationsStateMachineTest,NitTest,DecideStoreApplicationTest,StoreVerificationControllerTest,SubmitStoreApplicationTest,ResubmitMyApplicationTest,ChangeStoreStatusTest,AuditServiceTest,StoreApplicationNotifierTest test`, y `npm run build` + `npm run lint` en `frontend/`;
  - revisar: IDOR en `/me/store-applications/**`, el revisor en planes y tiendas, ausencia de PII en logs y `audit_logs`, cabeceras de descarga, que una tienda suspendida no aparezca en `/marketplace/stores` ni `/marketplace/products` y que una doble decisión concurrente dé 409;
  - recorrido con Playwright (skill `webapp-testing`) por consola y solicitante, con evidencia.
- **Aceptación:** informe con hallazgos clasificados por severidad y evidencia de cada comando.

**Orden** (relativo; el intercalado con los demás planes está en el orden global): AD-D1 → AD-B1 → AD-B2 → AD-B3 → AD-B4 → AD-B5 → AD-B6 → AD-F1 → AD-F2 → AD-F3 → AD-F4 → AD-F6 → AD-F5 → AD-B7 → AD-Q1. AD-F2 a AD-F4 y AD-F6 pueden empezar en modo mock tras AD-F1, sin esperar al backend. AD-B4 espera además a SA-B2 (`startTrial` idempotente); AD-F1 espera a MK-F0.

## 10. Riesgos

| # | Riesgo | Mitigación |
|---|---|---|
| R1 | Sin Flyway, AD-D1 se aplica a mano; si el backend arranca antes, `ddl-auto` crea las columnas como `timestamp` y el CHECK viejo rechaza `IN_REVIEW` | Aplicar AD-D1 antes de desplegar AD-B1 (requiere aprobación); AD-Q1 lo comprueba |
| R2 | `details` obligatorio rompe el formulario actual hasta AD-F3 | Desplegar AD-B5 y AD-F3 juntas; en local, usar el modo mock |
| R3 | El cambio de `startTrial` lo hace el plan SaaS (SA-B2); si se retrasa, AD-B4 se bloquea | Contrato §5.5; AD-B4 lo reporta en vez de implementarlo |
| R4 | `POST /store-applications` es público y no tiene límite de frecuencia (puede usarse para spam o almacenamiento) | Fuera del MVP; requiere aprobación si se añade una dependencia |
| R5 | La JWT de un usuario recién promovido conserva `ROLE_CUSTOMER` hasta refrescarla | La página de configuración abre una sesión nueva (ya existe) |
| R6 | Las tiendas suspendidas siguen pudiendo usar el portal | Fuera de alcance; propuesta: `StoreAccess` bloquea las mutaciones si la tienda no está `ACTIVE`/`PENDING` (decisión puntual posterior) |
| R7 | Rama actual con cambios sin commitear en `Store.java` y `ProductCatalog` | AD-B1 parte de `develop` después de integrar `chore/backend-pending-migrations` |
| R8 | DIVIPOLA en JSON estático y en el SQL de semillas pueden divergir | AD-F3 lo genera desde `R__seed_geo_divipola.sql` y documenta el origen en el propio JSON |

## 11. Requiere aprobación del usuario

1. Rol `PLATFORM_REVIEWER`, cambio de `/admin/**` y rutas públicas de tiendas del marketplace en `WebSecurityConfig` (cambio de seguridad, AD-B3/AD-D1).
2. Aplicar `V202610141400` a cualquier BD (comando contra BD) y crear cuentas revisoras por SQL.
3. D2: la prueba empieza al aprobar, no al configurar (cambio de comportamiento comercial).
4. D9: Inter en vez de Geist (dependencia nueva). Recomendación: mantener Geist.
5. Retención y borrado de documentos de solicitudes rechazadas (Ley 1581; destructivo). Fuera del MVP.
6. Liberar NIT, nombre o correo de solicitudes rechazadas (D7; destructivo).
7. Rate limiting en endpoints públicos si requiere dependencia (R4).
8. `npx shadcn add` si modifica `package.json` (se ejecuta en MK-F0).
