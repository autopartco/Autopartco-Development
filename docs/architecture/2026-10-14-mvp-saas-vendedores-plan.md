# Plan: MVP del SaaS para almacenes (portal `/portal`) y planes comerciales

> Orden de ejecución, despliegues conjuntos y aprobaciones: [`2026-10-14-mvp-orden-global.md`](2026-10-14-mvp-orden-global.md).

- Fecha: 2026-10-14 · Autor: agente `arquitecto` · Base: `develop` + rama `chore/backend-pending-migrations`
- Backlog cubierto: BL-B3, BL-B5, BL-B6, BL-F8–BL-F14, BL-F16–BL-F18 (`docs/architecture/mvp-backlog.md`). BL-F15 (estado de mi solicitud) y `/seller/setup` los cubre AD-F4 del plan de admin.
- Dueño único en este plan: `GET /me/stores` (SA-B1), `Subscriptions.startTrial` (SA-B2), backend de `/admin/plans` (SA-B4), ledger de inventario y movimiento de venta (SA-B5). Depende de: `PageResponse` (MK-B1/MK-F1), tokens y componentes base (MK-F0), `WebSecurityConfig` (AD-B3), `AuditService.record` (AD-B2).
- Planes vecinos (no se planifican aquí): `2026-10-14-mvp-marketplace-plan.md` (comprador, carrito, checkout) y `2026-10-14-mvp-admin-operaciones-plan.md` (solicitud, revisión, UI ROOT de planes)
- Rutas Java relativas a `backend/src/main/java/com/autopartco/backend/`.

## 1. Objetivo y alcance

| Entra (P0) | Entra (P1) | Fuera |
|---|---|---|
| `GET /me/stores` (BL-B3), layout del portal, selector de tienda | Importación CSV con previsualización y errores por fila | Alegra/Siigo (fase posterior, detrás de adaptador) |
| Dashboard | Imágenes de producto (UI; el API ya existe) | Wompi real en el portal (el endpoint existe; la UI usa el simulado) |
| CRUD de productos, publicar/despublicar, borrado lógico | Reingreso de stock al cancelar venta | Autorización por sucursal (`branches_collaborators`): P2 |
| Precio y stock por sucursal con movimientos trazables (BL-B6 incluido) | Paginación del listado de productos en servidor | Renovación automática, prorrateo, facturas electrónicas |
| Pedidos recibidos: lista, detalle, estado, envío (BL-B5) | Colaboradores e invitaciones (UI) | Avisos de fin de prueba por correo (P2, job idempotente) |
| Sucursales, perfil de tienda (`/seller/setup` es de AD-F4) | | Promociones (UI): P2 |
| Planes, límites, prueba de 1 mes, enforcement en servidor, "Plan y suscripción", cobro **simulado** | | Versionado formal de planes (P2; MVP guarda snapshot por pago) |

## 2. Estado actual verificado

| Endpoint / vista | Estado | Nota |
|---|---|---|
| `GET /me/stores` | **Falta** | BL-B3 |
| `GET /stores/{id}/my-permissions`, `GET /stores/{id}/status`, `PATCH /stores/{id}` | Existe | PATCH: `businessName`, `nit`, `businessEmail` |
| `GET /stores/{id}/profile` (lectura del perfil) | **Falta** | descripción está en `store.configuration.description` |
| `GET /stores/{id}/dashboard` | Existe | sin estado de suscripción ni métricas de inventario |
| `GET/POST /stores/{id}/products`, `PATCH /stores/{id}/branches/{b}/products/{p}`, `PATCH …/products/{p}/publication`, `DELETE …/products/{p}` | Existe | lista = una fila por (producto, sucursal), sin paginación |
| `GET /stores/{id}/products/{p}` (detalle con listados por sucursal) | **Falta** | `ListingQueries.storeListing` existe, sin controlador |
| Alta de listado en otra sucursal | **Falta** | `create` solo crea un listado |
| `…/products/{p}/images` (POST, PATCH main/order, PUT, DELETE) | Existe | |
| Movimientos de inventario | **Falta** | tabla `inventory_movements` existe en `init.sql` sin entidad; `StockService.updateListing` sobrescribe stock sin rastro |
| Importación CSV | **Falta** | capability `CSV_IMPORT` ya existe en planes |
| `/stores/{id}/branches` (GET, POST, PATCH, DELETE) | Existe | `CreateBranch` aplica cupo `BRANCHES` |
| `/stores/{id}/orders` (lista, detalle, `PATCH status`, `POST/PATCH shipment`) | Existe | lista devuelve array; cancelar no repone stock |
| `/stores/{id}/staff/*`, `/staff-invitations/*` | Existe | cupo `USERS` + capability `CUSTOM_ROLES` |
| `GET /plans`, `/admin/plans` (GET, POST, PUT, DELETE) | Existe | PUT: `max*` nulo = "no cambiar" (no permite volver a ilimitado) |
| `POST /stores/{id}/subscription/checkout` (Wompi), `POST /webhooks/wompi` | Existe | autoriza por correo del solicitante, no por rol OWNER |
| `GET /stores/{id}/subscription`, historial de pagos, cobro simulado | **Falta** | |
| `PlanEntitlements` (`requireCapacity`, `requireCapability`, `requireSubscriptionActive`) | Existe | errores sin `details` |
| `Subscriptions.startTrial(storeId, startsAt)` | Existe | no idempotente; lo llama `CompleteStoreSetup` |
| Frontend `/portal/*`, `/seller/setup`, `/seller/invitation` | **Falta** | solo marketplace, cuentas y `vender/registro` |

Modelo de suscripción real: columnas `plan_id`, `selected_plan_id`, `trial_starts_at`, `trial_ends_at`, `paid_through` de `seller_applications` (entidad `core.billing.internal.StoreSubscription`); pagos en `seller_payments`; planes en `seller_plans` (`max_branches`, `max_products`, `max_users`, `capabilities` solo vía `ddl-auto`, ver ADR 0001).

## 3. Componentes y responsabilidades

| Módulo | Nuevo / cambia | Responsabilidad |
|---|---|---|
| `core.billing` | `Subscriptions` (cambia), `SubscriptionView`, `SubscriptionStatus`, `SubscriptionPeriods` (nuevo, interno), `SimulatedSubscriptionPayments` (nuevo), `SubscriptionEvent` (entidad nueva), `PlanService`/`PlanRequest` (cambia), `PlanEntitlements` (cambia: `details`) | Estado, vigencia, cobro simulado, historial, límites |
| `core.inventory` | `InventoryMovement` (entidad), `InventoryLedger` (nuevo), `StockService` (cambia) | Toda variación de stock deja movimiento |
| `core.catalog` | `ProductCatalog` (cambia: `addListing`, `removeListing`), `ListingQueries` (cambia: `storeProduct`, contadores) | Producto + listados por sucursal |
| `core.organization` | `StoreAccess` (cambia: `requireOwner`, `storesOf`), `StoreService` (cambia: `profile`, descripción) | Membresía y perfil |
| `storesuite.store` | `ListMyStores`, `GetStoreProfile` (nuevos), `MeStoreController` | BL-B3, perfil |
| `storesuite.subscription` | `SubscriptionController` (cambia), `GetSubscription`, `ListSubscriptionPayments`, `PaySubscriptionSimulated` | Pantalla "Plan y suscripción" |
| `storesuite.inventory` | `InventoryController`, `AdjustStock`, `ListInventoryMovements` | Ajustes y kardex |
| `storesuite.products` | `GetStoreProduct`, `AddProductListing`, `RemoveProductListing`, `ImportProducts` (P1) | Detalle, sucursales, CSV |
| `storesuite.orders` | `UpdateStoreOrderStatus` (cambia) | Reingreso al cancelar |
| `storesuite.dashboard` | `StoreDashboardQuery`, `DashboardResponse` (cambian, aditivo) | Estado de suscripción e inventario |
| `frontend/src/features/portal` | nuevo | Portal completo |

Reglas de Modulith vigentes: `core.inventory` → solo `shared`; `core.order` → `shared`, `core.notification`. La orquestación entre inventario y pedidos va en `storesuite`.

## 4. Contratos

### 4.1 Convenciones

- Prefijo `/api/v1`. JSON sin envoltorio `data` (decisión D9 del plan de checkout). Fechas `LocalDateTime` ISO sin zona, hora de Bogotá (ADR 0002). Dinero: `BigDecimal` con 2 decimales para precios; planes en pesos enteros (`long`).
- Error: `{"error":{"code","message","details"}}`. `details` requiere los constructores con `details` de `ConflictException`/`NotFoundException`/`BadRequestException` y `PaymentRequiredException` (tarea CK-T1 del plan de checkout). **Dependencia dura.**
- Autorización: tienda ajena → 403 `FORBIDDEN` (comportamiento actual de `StoreAccess`); recurso de otra tienda bajo un `storeId` propio → 404 con el código del recurso.

### 4.2 Errores de planes (existentes, ahora con `details`)

| HTTP | code | details |
|---|---|---|
| 409 | `SELLER_PLAN_LIMIT_REACHED` | `{"capacity":"PRODUCTS|BRANCHES|USERS","limit":50,"used":50,"requested":1,"planCode":"TRY"}` |
| 409 | `SELLER_PLAN_FEATURE_UNAVAILABLE` | `{"capability":"CSV_IMPORT","planCode":"TRY"}` |
| 409 | `SELLER_SUBSCRIPTION_EXPIRED` | `{"expiredAt":"2026-11-14T10:00:00"}` |
| 409 | `SELLER_APPLICATION_NOT_ACTIVE` | — |

Matriz de enforcement (servidor):

| Operación | Suscripción vencida | Cupo / capability |
|---|---|---|
| Crear producto, importar CSV (filas `CREATE`) | bloquea | `PRODUCTS` (+N) · `CSV_IMPORT` |
| Publicar producto | bloquea | — |
| Crear sucursal | bloquea | `BRANCHES` |
| Invitar / reenviar invitación | bloquea | `USERS`, `CUSTOM_ROLES` |
| Agregar listado en otra sucursal | bloquea | — |
| Editar producto, ajustar stock, despublicar, borrar | permite | — |
| Leer todo, gestionar pedidos y envíos | permite | — |
| Pagar plan (simulado) | permite | — |

Al bajar de plan no se borra nada: los cupos excedidos solo impiden altas nuevas.

### 4.3 Endpoints nuevos o modificados

**BL-B3 — `GET /me/stores`** (autenticado; cualquier tipo de cuenta; dueño único SA-B1; no lo define ningún otro plan) → `200`

```json
[{
  "storeId": "uuid", "businessName": "Repuestos La 30", "logoUrl": "/api/v1/marketplace/stores/{id}/brand/logo",
  "applicationStatus": "ACTIVE|CONFIGURATION_PENDING|PENDING_REVIEW|REJECTED",
  "isOwner": true, "roleNames": ["Propietario"],
  "subscription": {"status": "TRIALING", "planCode": "TRY", "planName": "Prueba gratuita", "validThrough": "2026-11-14T10:00:00"}
}]
```
Solo tiendas donde la persona es colaborador `ACTIVE`. `logoUrl` nulo si no hay logo. `subscription` nulo si no hay solicitud. Lista vacía si no tiene tiendas.

**`GET /stores/{storeId}/profile`** (`STORE_READ`) → `{"id","businessName","nit","businessEmail","state","description","logoUrl","bannerUrl"}`.
**`PATCH /stores/{storeId}`** (`STORE_UPDATE`): se añade `description` (`@Size(max=1000)`, se guarda en `configuration.description`). Respuesta: el perfil anterior. `nit` sigue aceptándose en el API; la UI lo muestra de solo lectura (D11).

**`GET /stores/{storeId}/products/{productId}`** (`PRODUCT_READ`) → `404 PRODUCT_NOT_FOUND` si no es de la tienda.

```json
{
  "id": "uuid", "storeId": "uuid", "name": "Pastillas de freno", "sku": "PF-001", "partNumber": "D1234",
  "description": "…", "manufacturerId": "uuid", "categoryId": "uuid", "state": "ACTIVE|INACTIVE",
  "attributes": {}, "compatibilityIds": ["uuid"], "images": [{"id","url","isMain","displayOrder","createdAt"}],
  "listings": [{"inventoryId": "uuid", "branchId": "uuid", "branchName": "Principal", "branchState": "ACTIVE",
                "price": 85000.00, "effectivePrice": 76500.00, "appliedPromotionName": "Octubre", "stock": 12}]
}
```

**`POST /stores/{storeId}/products/{productId}/listings`** (`PRODUCT_UPDATE`) body `{"branchId","price","stock"}` (`price ≥ 0.01`, `stock ≥ 0`) → `201` detalle. Errores: `409 LISTING_ALREADY_EXISTS`, `404 BRANCH_NOT_FOUND` (sucursal ajena o inactiva), `409 SELLER_SUBSCRIPTION_EXPIRED`. Registra movimiento `INITIAL_LOAD` si `stock > 0`.
**`DELETE /stores/{storeId}/products/{productId}/listings/{branchId}`** (`PRODUCT_UPDATE`) → `204`; borrado lógico del listado; `409 LAST_LISTING` si es el único (para eso está borrar el producto).

**`PATCH /stores/{storeId}/branches/{b}/products/{p}`**: contrato sin cambios. Si trae `quantity`, el cambio se registra como movimiento `MANUAL_CORRECTION` (dirección según signo, sin movimiento si no cambia).

**Ajuste de stock — `POST /stores/{storeId}/branches/{branchId}/products/{productId}/stock-adjustments`** (`PRODUCT_UPDATE`)

```json
{"mode": "DELTA|COUNT", "quantity": -3, "reasonCode": "DAMAGE", "notes": "Caja golpeada", "clientRequestId": "uuid"}
```
- `DELTA`: `quantity` ≠ 0 (entero, |q| ≤ 100000). `COUNT`: `quantity` ≥ 0 es el conteo físico; motivo forzado `COUNT_ADJUSTMENT`.
- `reasonCode` permitido manualmente: `PURCHASE_RECEIPT`, `CUSTOMER_RETURN`, `COUNT_ADJUSTMENT`, `DAMAGE`, `LOSS_THEFT`, `OBSOLESCENCE`, `MANUAL_CORRECTION`. Validar dirección: `PURCHASE_RECEIPT`/`CUSTOMER_RETURN` solo positivos; `DAMAGE`/`LOSS_THEFT`/`OBSOLESCENCE` solo negativos.
- `200` → `{"movement": InventoryMovementResponse, "stock": 9}`. Repetición con el mismo `clientRequestId` → `200` con el movimiento original.
- Errores: `400 INVENTORY_REASON_INVALID`, `409 INSUFFICIENT_STOCK` (`details: {"available": 2}`), `404 PRODUCT_NOT_FOUND`, `400 VALIDATION_ERROR`.

**Kardex — `GET /stores/{storeId}/inventory/movements?branchId&productId&reasonCode&page=1&size=50`** (`PRODUCT_READ`; `size ≤ 100`) → `200 PageResponse<InventoryMovementView>` (contrato de paginación de MK-B1, marketplace §4.0)

```json
{"items": [{"id":"uuid","occurredAt":"2026-10-14T09:12:00","productId":"uuid","productName":"…","sku":"PF-001",
            "branchId":"uuid","branchName":"Principal","direction":"IN|OUT","quantity":3,"previousStock":12,"newStock":9,
            "reasonCode":"DAMAGE","source":"USER|ORDER|CSV|SYSTEM","actorName":"Ana Pérez","referenceType":"SALE|null",
            "referenceId":"uuid|null","notes":"…"}],
 "page": 1, "size": 50, "total": 134, "totalPages": 3}
```

**Suscripción — `GET /stores/{storeId}/subscription`** (`STORE_READ`) → `200 SubscriptionResponse`

```json
{
  "storeId": "uuid",
  "status": "PENDING_SETUP|TRIALING|ACTIVE|EXPIRED",
  "plan": PlanResponse, "selectedPlan": PlanResponse,
  "trialStartsAt": "…", "trialEndsAt": "…", "paidThrough": null, "validThrough": "…", "daysRemaining": 21,
  "usage": {"branches": {"used": 1, "limit": 1}, "products": {"used": 40, "limit": 100}, "users": {"used": 1, "limit": 2}},
  "capabilities": ["BASIC_ANALYTICS", "CSV_IMPORT"],
  "paymentMode": "SIMULATED|WOMPI|NONE",
  "canManage": true,
  "planChangeAllowed": true
}
```
- `status`: solicitud ≠ `ACTIVE` → `PENDING_SETUP`; `paidThrough > now` → `ACTIVE`; `trialEndsAt > now` → `TRIALING`; si no, `EXPIRED`. `limit: null` = ilimitado.
- `plan` = efectivo (pagado vigente; si no, el de prueba `TRY`). `paymentMode`: `SIMULATED` si `app.billing.simulated-payments-enabled=true`; `WOMPI` si está desactivado y Wompi configurado; si no, `NONE`. `canManage` = es OWNER. `planChangeAllowed` = `status != ACTIVE`.

**`GET /stores/{storeId}/subscription/payments`** (`STORE_READ`) → `200`
```json
[{"id":"uuid","reference":"SIM-…","provider":"SIMULATED|WOMPI","planCode":"BUSINESS","planName":"Negocio","months":3,
  "amountCop":449700,"currency":"COP","status":"APPROVED|PENDING|DECLINED|ERROR|VOIDED|FAILED",
  "createdAt":"…","paidAt":"…","periodStartsAt":"…","periodEndsAt":"…"}]
```

**Cobro simulado — `POST /stores/{storeId}/subscription/simulated-payments`** (solo OWNER)
```json
{"planId": "uuid", "months": 3, "simulatedOutcome": "APPROVED|REJECTED", "clientRequestId": "uuid"}
```
- `201` (`200` en repetición idempotente) → `{"payment": SubscriptionPaymentResponse, "subscription": SubscriptionResponse}`.
- `402 PAYMENT_REJECTED` (`details: {"amountCop","currency"}`): no persiste nada.
- `400 PAYMENT_METHOD_NOT_SUPPORTED` (simulador apagado), `400 SELLER_PLAN_NOT_PAYABLE`, `400 INVALID_PREPAID_MONTHS`, `404 SELLER_PLAN_NOT_FOUND`, `409 SUBSCRIPTION_PLAN_CHANGE_LOCKED` (`details: {"currentPlanCode","paidThrough"}`; plan distinto con periodo pagado vigente), `409 SUBSCRIPTION_DUPLICATE_REQUEST` (misma clave de otra tienda), `403 FORBIDDEN` (no OWNER).
- Efecto: igual que el webhook aprobado de Wompi (`SubscriptionPeriods.applyApproved`): `plan = planId`, `paidThrough = max(now, trialEndsAt, paidThrough) + months` (meses calendario). Inserta `seller_payments` (`provider='SIMULATED'`, `environment='SANDBOX'`, `reference='SIM-'+clientRequestId`, `status='APPROVED'`, `amount_in_cents = price×months×100`, `plan_snapshot`) y evento `PAYMENT_APPROVED` (+ `PLAN_CHANGED` si cambia).
- `POST /subscription/checkout` (Wompi) pasa de "correo del solicitante" a `StoreAccess.requireOwner` y aplica la misma regla de cambio de plan.

**Dashboard — `GET /stores/{storeId}/dashboard`**: campos aditivos `subscriptionStatus` (mismo enum), `daysRemaining`, `inventory: {"listings": 120, "outOfStock": 4, "lowStock": 9}` (`lowStock` = `0 < stock ≤ 3`).

**Importación CSV (P1)** — sin estado en servidor (D7)

- `GET /stores/{storeId}/product-imports/template` (`PRODUCT_READ`) → `text/csv` UTF-8 con BOM. Columnas: `sku,nombre,descripcion,referencia,fabricante,categoria,sucursal,precio,stock,compatibilidades,publicar`.
  - `sku` obligatorio (clave). `fabricante`, `categoria`, `sucursal`: nombre exacto sin distinguir mayúsculas. `compatibilidades`: `Marca>Modelo>Versión` separadas por `|`; obligatorio solo al crear. `publicar`: `si|no`. Separador `,` o `;` (autodetección por encabezado). Precio con punto decimal.
- `POST /stores/{storeId}/product-imports/preview` multipart `file` (≤ 1 MiB, ≤ 1000 filas). Requiere `PRODUCT_CREATE` y `PRODUCT_UPDATE` y capability `CSV_IMPORT`. → `200`
```json
{"checksum": "sha256-hex", "totalRows": 120, "validRows": 117, "errorRows": 3,
 "summary": {"create": 20, "update": 97, "stockMovements": 64, "productsAfterImport": 160, "productLimit": 500},
 "rows": [{"rowNumber": 2, "sku": "PF-001", "action": "CREATE|UPDATE|SKIP", "branchName": "Principal",
           "errors": [{"column": "precio", "code": "INVALID_DECIMAL", "message": "El precio debe ser un número mayor que 0."}],
           "warnings": []}]}
```
- `POST /stores/{storeId}/product-imports/commit` multipart `file` + `checksum` + `applyValidOnly` (bool). Revalida; si el checksum no coincide → `409 IMPORT_PREVIEW_STALE`; si hay errores y `applyValidOnly=false` → `400 IMPORT_HAS_ERRORS` (`details.rows`). Una transacción; cupo `PRODUCTS` con `+create`. Stock vía movimientos `CSV_IMPORT`. → `200 {"created":20,"updated":97,"skipped":3,"movements":64}`.
- Otros: `400 IMPORT_FILE_INVALID` (encabezado/encoding), `413 PAYLOAD_TOO_LARGE`, `409 SELLER_PLAN_FEATURE_UNAVAILABLE`, `409 SELLER_PLAN_LIMIT_REACHED`.

**Pedidos (BL-B5, sin cambio de contrato)**: `PATCH …/orders/{saleId}/status` con `CANCELLED` repone stock de cada ítem (`SALE_CANCELLED_RESTOCK`, idempotencia `cancel:{saleId}:{inventoryId}`) en la misma transacción.

**Planes ROOT — `/admin/plans`** (dueño único del backend y del contrato: SA-B4; la UI es AD-F6 y la protección `ROLE_ROOT` es AD-B3)

- `GET /admin/plans` → `PlanResponse[]` (incluye deshabilitados). `POST` → `201`. `PUT /{id}` → `200`. `DELETE /{id}` → `204` (deshabilita; `TRY` → `409 TRIAL_PLAN_REQUIRED`).
- `PlanRequest` (**PUT = reemplazo completo**; cambio respecto al código actual):
```json
{"code":"BUSINESS","name":"Negocio","description":"…","monthlyPriceCop":149900,"currency":"COP",
 "features":["Hasta 5 sucursales","…"],"recommended":true,"enabled":true,
 "maxBranches":5,"maxProducts":5000,"maxUsers":10,"capabilities":["BASIC_ANALYTICS","CSV_IMPORT","PROMOTIONS","CUSTOM_ROLES"]}
```
  `max*: null` = ilimitado. `capabilities` obligatorio (puede ser `[]`), valores de `PlanCapabilities.ALL`: `BASIC_ANALYTICS, ADVANCED_REPORTS, PROMOTIONS, CSV_IMPORT, CUSTOM_ROLES, INTEGRATIONS, WHATSAPP_NOTIFICATIONS, API_ACCESS, WEBHOOKS, ERP_INTEGRATIONS, CUSTOM_DOMAIN`; otro valor → `400 SELLER_PLAN_CAPABILITY_UNKNOWN`. Se elimina `applyDefaultEntitlements`.
- `PlanResponse`: `{"id","code","name","description","monthlyPriceCop","currency","features","recommended","enabled","maxBranches","maxProducts","maxUsers","capabilities"}` (sin cambios).
- Los cambios de precio no afectan periodos ya pagados (prepago; snapshot en `seller_payments.plan_snapshot`).
- Errores existentes que se conservan: `SELLER_PLAN_CODE_EXISTS`, `TRIAL_PLAN_CODE_IMMUTABLE`, `TRIAL_PLAN_MUST_BE_FREE`, `TRIAL_PLAN_REQUIRED`, `SELLER_PLAN_CURRENCY_UNSUPPORTED`, `DISABLED_PLAN_CANNOT_BE_RECOMMENDED`; nuevo `SELLER_PLAN_CAPABILITY_UNKNOWN`.
- Auditoría: `PlanService` llama a `AuditService.record` (contrato de AD-B2) con `PLAN_CREATED`, `PLAN_UPDATED` y `PLAN_DISABLED`; `metadata` solo con `planId`, `code` y nombres de campos cambiados.

### 4.4 Contrato con el plan de admin (aprobación → prueba)

```java
// core.billing.Subscriptions (API pública)
/**
 * Idempotente. Si la tienda no tiene prueba iniciada: plan efectivo = plan con code "TRY",
 * trial = [startsAt, startsAt + app.billing.trial-months (por defecto 1) meses calendario),
 * y registra el evento TRIAL_STARTED. Si ya tenía prueba, no cambia nada.
 * Debe correr dentro de la transacción del llamador que bloquea la solicitud.
 * Errores: 404 SELLER_APPLICATION_NOT_FOUND (no hay fila en seller_applications para storeId),
 *          404 TRIAL_PLAN_NOT_CONFIGURED.
 * @return fin de la prueba (nuevo o existente)
 */
@Transactional
public LocalDateTime startTrial(UUID storeId, LocalDateTime startsAt);

@Transactional(readOnly = true)
public SubscriptionView view(UUID storeId);   // misma forma que SubscriptionResponse (§4.3) sin canManage

public enum SubscriptionStatus { PENDING_SETUP, TRIALING, ACTIVE, EXPIRED }
```

- El plan de admin, al **aprobar**, invoca `subscriptions.startTrial(storeId, LocalDateTime.now())` desde `DecideStoreApplication` (AD-B4; archivo del plan de admin). Precondición: la fila de `seller_applications` existe (hoy la crea `StoreApplications.submit` junto con la tienda y el rol `OWNER` del solicitante).
- `CompleteStoreSetup` sigue llamando `startTrial`; al ser idempotente no reinicia la prueba. El estado visible será `PENDING_SETUP` hasta completar `/seller/setup` y `TRIALING` después.
- La UI ROOT del catálogo de planes usa solo `/admin/plans` (§4.3).

### 4.5 Esquema de BD

Con `ddl-auto: update` las entidades crean las columnas en local. El agente `database` escribe las migraciones versionadas equivalentes (V + U) para cuando se adopte Flyway (ADR 0001). Todo tiempo nuevo es `TIMESTAMPTZ`.

| Tabla | Cambio | Columnas |
|---|---|---|
| `seller_plans` | sin cambios (deriva ya cubierta por `V202610130905`) | `max_branches`, `max_products`, `max_users` INTEGER NULL (NULL = ilimitado), `capabilities` JSONB |
| `seller_payments` | añadir | `provider VARCHAR(20) NOT NULL DEFAULT 'WOMPI'` CHECK (`WOMPI`,`SIMULATED`); `plan_snapshot JSONB`; `paid_at TIMESTAMPTZ`; `period_starts_at TIMESTAMPTZ`; `period_ends_at TIMESTAMPTZ`; `requested_by_account_id UUID REFERENCES accounts(id)`. `environment` ya está en `V202610141310` |
| `seller_subscription_events` | nueva, solo inserción | `id UUID PK`, `application_id UUID NOT NULL REFERENCES seller_applications(id)`, `store_id UUID NOT NULL REFERENCES store(id)`, `event_type VARCHAR(30) NOT NULL` CHECK (`TRIAL_STARTED`,`PAYMENT_APPROVED`,`PLAN_CHANGED`), `plan_id UUID REFERENCES seller_plans(id)`, `previous_plan_id UUID REFERENCES seller_plans(id)`, `payment_id UUID REFERENCES seller_payments(id)`, `actor_account_id UUID REFERENCES accounts(id)`, `details JSONB NOT NULL DEFAULT '{}'`, `occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()`; índice `(store_id, occurred_at DESC)` |
| `inventory_movements` | columnas de `V202610140940` (no se redefinen) | se usan `movement_type` (`IN`/`OUT`), `quantity > 0`, `previous_stock`, `new_stock`, `reason_code`, `source`, `actor_account_id`, `reference_type`, `reference_id`, `idempotency_key`, `notes`, `occurred_at`, `order_detail_id`. La entidad JPA mapea exactamente esos nombres y tipos |
| `inventory_movements` | índice nuevo | `uq_inventory_movements__idempotency_key` UNIQUE parcial `WHERE idempotency_key IS NOT NULL` (verificar si `V202610140940` ya lo crea; si sí, no duplicar) |

`reference_type` usado por el MVP: `SALE` (venta del portal) además de los de `V202610140940`; si su CHECK no lo admite, la migración nueva lo amplía. Uso actual (productos, sucursales, usuarios) se calcula en vivo; no hay tabla de uso.

Semilla de planes propuesta (**requiere aprobación de precios y límites**), `INSERT … ON CONFLICT (code) DO NOTHING`:

| code | Nombre | COP/mes | Sucursales | Productos | Usuarios | Capabilities |
|---|---|---|---|---|---|---|
| `TRY` | Prueba gratuita (1 mes) | 0 | 1 | 100 | 2 | `BASIC_ANALYTICS`, `CSV_IMPORT` |
| `BASIC` | Básico | 59.900 | 1 | 500 | 3 | + `PROMOTIONS` |
| `BUSINESS` | Negocio (recomendado) | 149.900 | 5 | 5.000 | 10 | + `ADVANCED_REPORTS`, `CUSTOM_ROLES` |
| `ENTERPRISE` | Empresa | 499.900 | ilimitado | ilimitado | ilimitado | + `INTEGRATIONS`, `API_ACCESS`, `WEBHOOKS` |

### 4.6 Tipos TypeScript (copiar en `frontend/src/features/portal/api/types.ts`)

```ts
export type SubscriptionStatus = 'PENDING_SETUP' | 'TRIALING' | 'ACTIVE' | 'EXPIRED'
export type Capacity = 'PRODUCTS' | 'BRANCHES' | 'USERS'
export interface MyStore { storeId: string; businessName: string; logoUrl: string | null
  applicationStatus: 'ACTIVE' | 'CONFIGURATION_PENDING' | 'PENDING_REVIEW' | 'REJECTED'
  isOwner: boolean; roleNames: string[]
  subscription: { status: SubscriptionStatus; planCode: string; planName: string; validThrough: string | null } | null }
export interface Plan { id: string; code: string; name: string; description: string | null; monthlyPriceCop: number
  currency: 'COP'; features: string[]; recommended: boolean; enabled: boolean
  maxBranches: number | null; maxProducts: number | null; maxUsers: number | null; capabilities: string[] }
export interface UsageItem { used: number; limit: number | null }
export interface Subscription { storeId: string; status: SubscriptionStatus; plan: Plan; selectedPlan: Plan
  trialStartsAt: string | null; trialEndsAt: string | null; paidThrough: string | null; validThrough: string | null
  daysRemaining: number | null; usage: Record<'branches' | 'products' | 'users', UsageItem>
  capabilities: string[]; paymentMode: 'SIMULATED' | 'WOMPI' | 'NONE'; canManage: boolean; planChangeAllowed: boolean }
export interface SubscriptionPayment { id: string; reference: string; provider: 'SIMULATED' | 'WOMPI'; planCode: string
  planName: string; months: number; amountCop: number; currency: 'COP'; status: string; createdAt: string
  paidAt: string | null; periodStartsAt: string | null; periodEndsAt: string | null }
export interface SimulatedPaymentInput { planId: string; months: number; simulatedOutcome: 'APPROVED' | 'REJECTED'; clientRequestId: string }
export interface Listing { inventoryId: string; branchId: string; branchName: string; branchState: string
  price: number; effectivePrice: number; appliedPromotionName: string | null; stock: number }
export interface StoreProduct { id: string; storeId: string; name: string; sku: string | null; partNumber: string | null
  description: string; manufacturerId: string; categoryId: string; state: 'ACTIVE' | 'INACTIVE'
  attributes: Record<string, unknown>; compatibilityIds: string[]
  images: { id: string; url: string; isMain: boolean; displayOrder: number; createdAt: string }[]; listings: Listing[] }
export interface StockAdjustmentInput { mode: 'DELTA' | 'COUNT'; quantity: number; reasonCode: string; notes?: string; clientRequestId: string }
export interface InventoryMovement { id: string; occurredAt: string; productId: string; productName: string; sku: string | null
  branchId: string; branchName: string; direction: 'IN' | 'OUT'; quantity: number; previousStock: number; newStock: number
  reasonCode: string; source: 'USER' | 'ORDER' | 'CSV' | 'SYSTEM'; actorName: string | null
  referenceType: string | null; referenceId: string | null; notes: string | null }
// Listas paginadas: PageResponseDto<T> de frontend/src/lib/page.ts (MK-F1); no se define un Page<T> propio.
export interface ImportPreview { checksum: string; totalRows: number; validRows: number; errorRows: number
  summary: { create: number; update: number; stockMovements: number; productsAfterImport: number; productLimit: number | null }
  rows: { rowNumber: number; sku: string; action: 'CREATE' | 'UPDATE' | 'SKIP'; branchName: string | null
    errors: { column: string; code: string; message: string }[]; warnings: string[] }[] }
```
Para `ProductResponse` (lista), `SaleSummaryResponse`, `SaleDetailResponse`, `BranchResponse`, `StaffResponse`, `DashboardResponse`: transcribir los records Java de `core/catalog`, `core/order`, `core/organization`, `storesuite/staff`, `storesuite/dashboard` (+ campos aditivos de §4.3).

### 4.7 Rutas frontend

| Ruta | Vista | Guarda |
|---|---|---|
| `/portal` | redirige a `/portal/:storeId` si hay una tienda `ACTIVE`; si hay varias, selector; si ninguna, estado vacío con enlace a `/vender` y a "Estado de mi solicitud" (`/vender/solicitud`, AD-F4) | sesión |
| `/portal/:storeId` → `inicio` | Dashboard | sesión + `my-permissions` no vacío |
| `…/productos`, `…/productos/nuevo`, `…/productos/:productId`, `…/productos/importar` | Productos, alta, edición (datos, sucursales y stock, imágenes), CSV | `PRODUCT_READ` |
| `…/inventario` | Kardex y ajustes | `PRODUCT_READ` |
| `…/pedidos`, `…/pedidos/:saleId` | Pedidos | `ORDER_READ` |
| `…/sucursales` | Sucursales (alta/edición en `Sheet`) | `BRANCH_READ` |
| `…/equipo` | Colaboradores e invitaciones | `COLLABORATOR_READ` |
| `…/tienda` | Perfil | `STORE_READ` |
| `…/plan` | Plan y suscripción | `STORE_READ`; acciones solo `canManage` |
| `/seller/setup?token=` | Completar configuración: **dueño AD-F4** (plan de admin); aquí solo se consume su redirección a `/portal/:storeId` | pública (ruta que envía el backend) |
| `/seller/invitation?token=` | Aceptar invitación (SA-F8; la ruta la registra SA-F1) | pública (ruta que envía el backend) |

Las guardas del cliente solo ocultan navegación; el servidor decide.

## 5. Sistema visual del portal (delta sobre MK-F0)

Tokens, tipografía (Geist, D12), componentes base (`StatusBadge`, `DataTable`, `ConfirmDialog`, `ListSkeleton`, primitivas `components/ui/*` incluidas `sidebar`, `dropdown-menu`, `progress`, `switch`, `avatar`), estados de carga/vacío/error/éxito, formularios y checklist de accesibilidad: los de MK-F0 (§6 del plan de marketplace). Este plan no crea tokens ni componentes base; si falta uno reutilizable, se añade a MK-F0. Solo define:

- **Shell del portal:** sidebar 15rem en carbón (`asphalt`, primitiva `sidebar` de MK-F0) con logo real (`components/common/logo.tsx`), ítem activo con barra de 2px `thread-on-asphalt` y `aria-current="page"`; iconos (Lucide) en `lg`, `Sheet` en `< lg`. Topbar blanca 3.5rem: selector de tienda (`Select`), insignia del plan con días restantes, menú de cuenta. Contenido sobre `paper`, ancho máx. 80rem, `PageHeader` con título, descripción y acción primaria. Enlace "Saltar al contenido" y landmarks `nav`/`main`.
- **Densidad:** la de gestión de MK-F0 (`DataTable density="compact"`); orden y filtro en cliente (P0); acciones por fila en `DropdownMenu`.
- **Insignias:** venta y envío con `saleStatusView`/`shipmentStatusView` de `src/lib/sale-status.ts` (MK-F0); stock: 0 "Agotado" (`danger`), 1–3 "Bajo" (`warning`).
- **Planes (propio del portal):** `UsageMeter` (barra + "40 de 100 productos"; aviso ≥ 80 %); en límite, CTA "Mejorar plan" a `…/plan`. Un 409 `SELLER_PLAN_LIMIT_REACHED`/`FEATURE_UNAVAILABLE` abre `UpgradeDialog` con `details`. Banner persistente si `EXPIRED` (solo lectura de altas) o si `TRIALING` con ≤ 7 días. Tarjetas de plan con "Recomendado" (`recommended`/`recommended-soft`). El cobro simulado lleva la etiqueta visible "Pago de prueba: no se realiza ningún cobro".

## 6. Autorización y pruebas IDOR

- Todo endpoint `/stores/{storeId}/…` llama primero `StoreAccess.requirePermission(email, storeId, código)`; cobro y checkout de suscripción usan `StoreAccess.requireOwner`.
- Cada consulta filtra por `store_id` en SQL (producto `p.store_id`, sucursal `b.store_id`, venta vía `branches.store_id`, movimiento vía `inventory → branches.store_id`).
- `GET /me/stores` solo deriva de la persona autenticada; no acepta parámetros.

| # | Caso | Esperado |
|---|---|---|
| I1 | Usuario de tienda A llama cualquier endpoint con `storeId` B | 403 `FORBIDDEN` |
| I2 | `storeId` A + `productId` de B (detalle, listings, ajuste, imágenes) | 404 `PRODUCT_NOT_FOUND` |
| I3 | `storeId` A + `branchId` de B (listing, ajuste, kardex `?branchId`) | 404 `BRANCH_NOT_FOUND` / lista vacía |
| I4 | `storeId` A + `saleId` de B (detalle, estado, envío) | 404 |
| I5 | Colaborador sin `PRODUCT_UPDATE` ajusta stock | 403 |
| I6 | Colaborador no OWNER paga suscripción | 403 |
| I7 | `clientRequestId` de un pago de B reutilizado desde A | 409 `SUBSCRIPTION_DUPLICATE_REQUEST` |
| I8 | Tienda `CONFIGURATION_PENDING` o `REJECTED` | 403 en operaciones (comportamiento actual de `StoreAccess`) |
| I9 | Suscripción vencida: crear producto, publicar, importar, crear sucursal, invitar | 409 `SELLER_SUBSCRIPTION_EXPIRED` |
| I10 | Altas concurrentes en el límite de productos | nunca supera el cupo (bloqueo de `store`) |
| I11 | Ajuste que dejaría stock negativo | 409 `INSUFFICIENT_STOCK`, sin movimiento |

## 7. Decisiones

| # | Decisión | Descartado |
|---|---|---|
| D1 | Mantener la suscripción en columnas de `seller_applications` y derivar `status` en lectura | Tabla `store_subscriptions` nueva: reescribe onboarding, Wompi y entitlements sin beneficio en MVP |
| D2 | Cobro simulado reutiliza `seller_payments` y la misma lógica de extensión que Wompi (`SubscriptionPeriods`) | Simulador separado: dos reglas de vigencia |
| D3 | Prepago 1–12 meses, sin renovación automática; cambio a otro plan solo sin periodo pagado vigente (409 si no) | Prorrateo/créditos: complejidad sin pasarela real |
| D4 | Durante la prueba aplican los límites del plan `TRY` (configurables por ROOT) | Prueba con límites del plan elegido: ver aprobación A3 |
| D5 | Gestión de suscripción solo OWNER; lectura con `STORE_READ`. Inventario reutiliza `PRODUCT_READ/UPDATE` | Permisos nuevos: exigen sembrar `permissions` y re-otorgar a roles OWNER existentes |
| D6 | Movimientos en `inventory_movements` con columnas de `V202610140940`; `StockService` es el único que cambia stock | Ledger nuevo: duplica el diseño predictivo |
| D7 | CSV sin estado: preview y commit reciben el archivo, el commit verifica `checksum` | Tablas `product_imports`: historial no necesario en MVP; el rastro queda en movimientos `CSV_IMPORT` |
| D8 | Parser CSV propio (RFC 4180 básico) en `storesuite.products.csv` | Apache Commons CSV: dependencia nueva |
| D9 | Venta del checkout registra movimiento `MARKETPLACE_SALE` dentro de `StockService.decrementIfAvailable` (sin `order_detail_id`). Dueño único del movimiento: SA-B5; CK-T2 es dueño del `UPDATE` atómico y su firma, que SA-B5 no cambia | Editar `PlaceMarketplaceOrder`: archivo del plan de checkout |
| D10 | `PlanRequest` en PUT es reemplazo completo; `null` = ilimitado | Mantener "null = no cambia": impide volver a ilimitado |
| D11 | NIT de solo lectura en la UI | Edición libre del NIT tras verificación |
| D12 | El portal usa la fuente actual (Geist) | Inter: requiere aprobación (dependencia y cambio global) |
| D13 | Interruptor `app.billing.simulated-payments-enabled` con `@Value(...:true)` sin editar `application.yaml` | Configuración en YAML: fuera del área del agente `backend` |
| D14 | Listado de productos sin paginación en P0 (los cupos lo acotan); paginación servidor P1, con `PageResponse` de MK-B1 | Cambiar ya el tipo de retorno |

## 8. Tareas

Dependencias externas: **CK-T1** (`details` en excepciones, `PaymentRequiredException`), **CK-T2** (`StockService.decrementIfAvailable`) y **CK-T8** (`getAccessToken`, `details` en `ApiError` del frontend) del plan de checkout deben estar en `develop` antes de las tareas que las citan; también **MK-B1** (`PageResponse`), **MK-F0** (componentes base), **MK-F1** (`PageResponseDto`) y **AD-B2** (`AuditService.record`). La rama `chore/backend-pending-migrations` (cambios en `ListingQueries`, `Product`, `Store`) debe fusionarse antes de SA-B1. IDs: `SA-D*` database, `SA-B*` backend, `SA-F*` frontend, `SA-Q*` qa.

Contexto mínimo común para cualquier tarea: leer `AGENTS.md`, este plan §4 y `docs/api/error-handling.md`. Pruebas backend solo con `./mvnw -q test -Dtest=<Clases>` desde `backend/`.

### Database

**SA-D1 — Migraciones de suscripción** · Haiku · `database`
- Archivos: `database/migrations/V<ts>__seller_subscription_events_and_payments.sql` + `U<ts>__…`, `V<ts>__seed_seller_plans.sql` + `U<ts>__…`, `database/migrations/README.md` (tabla de orden; después de AD-D1, que edita la misma tabla).
- Contexto: §4.5. Convenciones de `database/migrations/README.md` (idempotentes, `SET lock_timeout`, `TIMESTAMPTZ`, `forbid_update_delete()` para solo inserción, guard destructivo en `U`). Semilla con `ON CONFLICT (code) DO NOTHING` y valores de §4.5 (pendientes de aprobación A1).
- Aceptación: revisión: columnas, CHECK, FK e índice exactamente como §4.5; `seller_subscription_events` con trigger de solo inserción; `U` revierte; no se ejecuta contra ninguna BD.

### Backend

**SA-B1 — Mis tiendas y perfil** · Sonnet · `backend`
- Archivos: `core/organization/StoreAccess.java` (`storesOf(email)`, `requireOwner(email, storeId)`), `core/organization/internal/CollaboratorRepository.java` (consulta de tiendas por persona), `core/organization/StoreService.java` (`profile`, descripción en `update`), `core/organization/StoreUpdateRequest.java`, nuevos `core/organization/MyStoreMembership.java`, `core/organization/StoreProfileResponse.java`, `storesuite/store/MeStoreController.java`, `storesuite/store/ListMyStores.java`, `storesuite/store/GetStoreProfile.java`, `storesuite/store/StoreProfileController.java`; pruebas `storesuite/store/ListMyStoresTest.java`, `GetStoreProfileTest.java`.
- Contexto: §4.3 BL-B3 y perfil. Dueño único de `GET /me/stores`. `subscription` de `MyStore` sale de `Subscriptions.view` (SA-B2); hasta que exista SA-B2, devolver `null` detrás de un método único para enchufarlo. No toca `internal/Store.java` (dueño AD-B1); `StoreService.java` lo vuelve a editar AD-B6 después, con métodos distintos.
- Aceptación: `-Dtest=ListMyStoresTest,GetStoreProfileTest,ModularityTests` verde; casos: sin tiendas → `[]`; colaborador inactivo no aparece; perfil de otra tienda → 403.

**SA-B2 — Dominio de suscripción** · Opus · `backend`
- Archivos: `core/billing/Subscriptions.java`, nuevos `core/billing/SubscriptionView.java`, `SubscriptionStatus.java`, `SubscriptionPaymentView.java`, `core/billing/internal/SubscriptionPeriods.java`, `core/billing/internal/SubscriptionEvent.java` + repositorio, `core/billing/internal/SubscriptionPayment.java` (columnas §4.5), `core/billing/internal/wompi/WompiWebhookProcessor.java` (usar `SubscriptionPeriods`, evento), `core/billing/SubscriptionCheckout.java` (OWNER vía `StoreAccess.requireOwner`, regla D3), `core/billing/PlanEntitlements.java` (`details` §4.2); `storesuite/subscription/SubscriptionController.java`, nuevos `GetSubscription.java`, `ListSubscriptionPayments.java`; pruebas `core/billing/SubscriptionsTest.java`, `SubscriptionPeriodsTest.java`, actualizar `SubscriptionCheckoutTest`, `WompiWebhookProcessorTest`, `PlanEntitlementsTest`.
- Contexto: §4.3 suscripción, §4.4 (contrato con admin, firma exacta), D1–D5. Trial con `@Value("${app.billing.trial-months:1}")`. Requiere CK-T1. Desbloquea AD-B4.
- Aceptación: pruebas verdes con `-Dtest=SubscriptionsTest,SubscriptionPeriodsTest,SubscriptionCheckoutTest,WompiWebhookProcessorTest,PlanEntitlementsTest,ModularityTests`; cubren: `startTrial` idempotente (segunda llamada no cambia fechas ni crea evento), derivación de los 4 estados con reloj fijo, extensión desde `max(now, trial, paid)`, `SUBSCRIPTION_PLAN_CHANGE_LOCKED`, `details` de límite.

**SA-B3 — Cobro simulado de suscripción** · Sonnet · `backend`
- Archivos: nuevos `core/billing/SimulatedSubscriptionPayments.java`, `core/billing/SimulatedPaymentRequest.java`, `storesuite/subscription/PaySubscriptionSimulated.java`; `storesuite/subscription/SubscriptionController.java`; prueba `core/billing/SimulatedSubscriptionPaymentsTest.java`.
- Contexto: §4.3 cobro simulado, D2, D13. Usa `SubscriptionPeriods` de SA-B2. Requiere SA-B2 y CK-T1.
- Aceptación: `-Dtest=SimulatedSubscriptionPaymentsTest` verde: aprobado extiende y guarda `provider=SIMULATED`, `plan_snapshot`, evento; rechazado → `PaymentRequiredException` sin `save`; repetición devuelve el mismo pago; clave de otra tienda → 409; interruptor apagado → 400; no OWNER → `AccessDeniedException`.

**SA-B4 — Catálogo de planes para ROOT** · Sonnet · `backend`
- Archivos: `core/billing/PlanService.java`, `core/billing/PlanRequest.java`, nuevo `core/billing/PlanCapabilities.java`; prueba `core/billing/PlanServiceTest.java`. No toca `operations/plans/PlanAdminController.java` (`@PreAuthorize` de AD-B3).
- Contexto: §4.3 "Planes ROOT", D10. Dueño único del backend de `/admin/plans`. Quitar `applyDefaultEntitlements`; validar capabilities; auditar con `AuditService.record`. Requiere AD-B2.
- Aceptación: `-Dtest=PlanServiceTest` verde: PUT con `maxProducts=null` deja ilimitado; capability desconocida → 400 `SELLER_PLAN_CAPABILITY_UNKNOWN`; reglas `TRY` existentes intactas; crear, editar y deshabilitar llaman a `AuditService.record` con `PLAN_CREATED`/`PLAN_UPDATED`/`PLAN_DISABLED` sin PII.

**SA-B5 — Ledger de inventario, ajustes y kardex** · Sonnet · `backend`
- Archivos: nuevos `core/inventory/internal/InventoryMovement.java` + `InventoryMovementRepository.java`, `core/inventory/InventoryLedger.java`, `core/inventory/MovementReason.java`, `core/inventory/InventoryMovementView.java`; `core/inventory/StockService.java` (`createListing` → `INITIAL_LOAD`; `updateListing` → `MANUAL_CORRECTION`; `decrementIfAvailable` → `MARKETPLACE_SALE`, `source=ORDER`; nuevos `adjust(...)` y `restock(...)`); nuevos `storesuite/inventory/InventoryController.java`, `AdjustStock.java`, `ListInventoryMovements.java`, `StockAdjustmentRequest.java`; pruebas `core/inventory/InventoryLedgerTest.java`, `StockServiceTest` (ampliar), `storesuite/inventory/AdjustStockTest.java`.
- Contexto: §4.3 ajustes y kardex, §4.5 `inventory_movements`, D6, D9. Requiere CK-T2 y MK-B1 (kardex con `PageResponse`). Dueño único del registro de movimientos (incluido el de venta del checkout, BL-B6); no cambia la firma ni el SQL de `decrementIfAvailable` de CK-T2 ni toca `marketplace/checkout/**`. `actorAccountId` lo pasa `storesuite` (inventory solo depende de `shared`). Ajustes con `UPDATE … RETURNING` como CK-T2 para no perder concurrencia.
- Aceptación: `-Dtest=InventoryLedgerTest,StockServiceTest,AdjustStockTest,ModularityTests` verde: cada camino de cambio de stock inserta exactamente un movimiento con `previous/new` correctos; idempotencia por `clientRequestId`; negativo → 409 sin movimiento; motivo con dirección inválida → 400.

**SA-B6 — Detalle de producto y listados por sucursal** · Sonnet · `backend`
- Archivos: `core/catalog/ListingQueries.java` (`storeProduct`, contadores de inventario para dashboard), `core/catalog/ProductCatalog.java` (`addListing`, `removeListing`), nuevos `core/catalog/StoreProductResponse.java`, `core/catalog/ListingCreateRequest.java`; nuevos `storesuite/products/GetStoreProduct.java`, `AddProductListing.java`, `RemoveProductListing.java`; `storesuite/products/StoreProductController.java`; pruebas `storesuite/products/StoreProductUseCasesTest.java` (ampliar), `core/catalog/ListingQueriesTest.java` (ampliar).
- Contexto: §4.3 detalle y listings; enforcement §4.2 (`requireSubscriptionActive` al agregar listado). Requiere SA-B5 (`INITIAL_LOAD`); `ListingQueries.java` llega ya editado por CK-T3, MK-B1 y MK-B2.
- Aceptación: pruebas verdes; producto de otra tienda → 404; sucursal ajena → `BRANCH_NOT_FOUND`; listado duplicado → 409; último listado → 409 `LAST_LISTING`.

**SA-B7 — Pedidos del checkout en el portal y reingreso al cancelar** · Sonnet · `backend`
- Archivos: `storesuite/orders/UpdateStoreOrderStatus.java`; prueba `storesuite/orders/UpdateStoreOrderStatusTest.java`; si hace falta, `core/order/SaleQueries.java` (solo lectura de ítems).
- Contexto: BL-B5/BL-B6; ventas creadas por el checkout (`sales` `PENDING`, `order_details.inventory_id`). Al pasar a `CANCELLED`, `StockService.restock(inventoryId, units, SALE, saleId, actor)` por ítem con idempotencia `cancel:{saleId}:{inventoryId}`. Requiere SA-B5 y CK-T6.
- Aceptación: `-Dtest=UpdateStoreOrderStatusTest` verde (cancelar repone y no duplica al reintentar). Guion manual (autorizado contra BD dev en el plan de checkout): pedido del checkout aparece en `GET /stores/{id}/orders` y recorre `PENDING → CONFIRMED → PREPARING → envío → DELIVERED`.

**SA-B8 — Dashboard ampliado** · Haiku · `backend`
- Archivos: `storesuite/dashboard/DashboardResponse.java`, `StoreDashboardQuery.java`, `StoreDashboardQueryTest.java`.
- Contexto: §4.3 dashboard (campos aditivos). Requiere SA-B2 y SA-B6.
- Aceptación: `-Dtest=StoreDashboardQueryTest` verde con `subscriptionStatus`, `daysRemaining`, `inventory`.

**SA-B9 — Importación CSV (P1)** · Sonnet (revisión Opus) · `backend`
- Archivos: nuevos `storesuite/products/csv/CsvReader.java`, `ProductImportRow.java`, `ProductImportValidator.java`, `storesuite/products/ImportProducts.java`, `ProductImportController.java`, `ImportPreviewResponse.java`; pruebas `CsvReaderTest.java`, `ImportProductsTest.java`.
- Contexto: §4.3 CSV, D7, D8. Alta con `ProductCatalog.create`, actualización con `update`, stock por `StockService.adjust(…, CSV_IMPORT, source=CSV)`. Resolución de nombres con `ListingQueries.manufacturers/categories/compatibilities` y `BranchService.list`. Límite 1 MiB / 1000 filas.
- Aceptación: pruebas verdes: comillas, `;`, BOM, filas vacías; errores por columna con número de fila; checksum distinto → 409; cupo excedido → 409 con `details`; sin `CSV_IMPORT` → 409; commit atómico (falla una fila con `applyValidOnly=false` → nada aplicado).

### Frontend (dependen de CK-T8, MK-F0 y MK-F1; usar `mock` mientras el backend no exista)

**SA-F1 — Base del portal** · Sonnet · `frontend`
- Archivos: `frontend/src/features/portal/api/{types.ts,portal-service.ts,mock/*,http/*}`, `frontend/src/features/portal/layout/{portal-layout.tsx,portal-sidebar.tsx,store-switcher.tsx,plan-badge.tsx,subscription-banner.tsx}`, `frontend/src/features/portal/components/{usage-meter.tsx,upgrade-dialog.tsx}`, `frontend/src/features/portal/hooks.ts`, `frontend/src/app/router.tsx`, `frontend/src/app/lazy-pages.tsx` (rutas `/portal/*` y `/seller/invitation`), `frontend/src/features/portal/pages/portal-entry-page.tsx`. No crea `components/ui/*`, `StatusBadge`, tablas ni estados vacíos (son de MK-F0).
- Contexto: §4.6, §4.7, §5. Patrón de servicios mock/http de `features/marketplace/api` y `lib/api.ts` (`DATA_SOURCE`). Errores con `ApiError.details`. Componentes de MK-F0 y `PageResponseDto` de MK-F1.
- Aceptación: `npm run build` y `npm run lint` sin errores; con `mock`, `/portal` con 0/1/2 tiendas muestra vacío/redirección/selector; navegación por teclado y `aria-current`; responsive 360/768/1280 px.

**SA-F2 — Dashboard** · Sonnet · `frontend` · Archivos: `features/portal/pages/dashboard-page.tsx`. Aceptación: tarjetas de ventas, uso con `UsageMeter`, inventario bajo/agotado, estados carga/vacío/error; build verde.

**SA-F3 — Productos (lista, alta, edición, publicar, borrar, sucursales y stock)** · Sonnet · `frontend` · Archivos: `features/portal/pages/products-page.tsx`, `product-form-page.tsx`, `features/portal/components/{product-form.tsx,listings-editor.tsx,compatibility-picker.tsx}`. Contexto: §4.3 productos, opciones de `/marketplace/products/options/*`. Aceptación: alta con validación en línea; 409 de límite abre `upgrade-dialog`; despublicar/borrar con confirmación; agregar/quitar sucursal; build verde.

**SA-F4 — Imágenes (P1)** · Sonnet · `frontend` · Archivos: `features/portal/components/product-images.tsx`. Contexto: `docs/backend/product-images.md`. Aceptación: subir, principal, reordenar (teclado incluido), borrar.

**SA-F5 — Inventario** · Sonnet · `frontend` · Archivos: `features/portal/pages/inventory-page.tsx`, `features/portal/components/stock-adjustment-dialog.tsx`. Aceptación: filtros por sucursal/producto/motivo, paginación, ajuste DELTA/COUNT con `clientRequestId` generado una vez por apertura del diálogo; 409 `INSUFFICIENT_STOCK` muestra disponible.

**SA-F6 — Pedidos** · Sonnet · `frontend` · Archivos: `features/portal/pages/orders-page.tsx`, `order-detail-page.tsx`, `features/portal/components/{order-status-actions.tsx,shipment-form.tsx}`. Aceptación: solo se ofrecen transiciones válidas (§2); envío solo en `PREPARING`; historial de eventos; build verde.

**SA-F7 — Sucursales y perfil** · Sonnet · `frontend` · Archivos: `features/portal/pages/branches-page.tsx`, `store-profile-page.tsx`, `features/portal/components/branch-sheet.tsx`. Contexto: `BranchCreateRequest` (teléfono 7–10 dígitos), `features/location` para ciudad/departamento DIVIPOLA. Aceptación: crear/editar/desactivar sucursal; límite de sucursales con CTA; NIT solo lectura.

**SA-F8 — Aceptación de invitación** · Sonnet · `frontend` · Archivos: `features/portal/pages/seller-invitation-page.tsx` (`/seller/invitation`). `/seller/setup` es de AD-F4. Contexto: `docs/backend/seller-onboarding-and-plans.md` (preview/accept de invitación). Aceptación: token inválido/vencido con mensaje accionable; aceptar abre sesión y redirige a `/portal/:storeId`.

**SA-F9 — Equipo (P1)** · Sonnet · `frontend` · Archivos: `features/portal/pages/team-page.tsx`, `features/portal/components/invite-dialog.tsx`. Aceptación: invitar, reenviar, revocar; sin `CUSTOM_ROLES` o sin cupo muestra mejora de plan.

**SA-F10 — Plan y suscripción** · Sonnet · `frontend` · Archivos: `features/portal/pages/plan-page.tsx`, `features/portal/components/{plan-card.tsx,simulated-checkout-dialog.tsx,payments-table.tsx}`. Contexto: §4.3 suscripción, §5 planes. Aceptación: estado, días restantes, uso vs límites, comparativa de `GET /plans`, cobro simulado 1–12 meses con total en COP y etiqueta de prueba; 402 muestra rechazo sin cambiar estado; 409 `SUBSCRIPTION_PLAN_CHANGE_LOCKED` explicado; no OWNER ve solo lectura.

**SA-F11 — Importación CSV (P1)** · Sonnet · `frontend` · Archivos: `features/portal/pages/product-import-page.tsx`. Aceptación: descarga de plantilla, previsualización con tabla de errores por fila/columna, "Aplicar solo válidas", reintento con archivo corregido.

### QA

**SA-Q1 — Seguridad y pruebas backend** · Opus · `qa-seguridad`
- Contexto: §6 (I1–I11), §4.2.
- Aceptación: informe con salida de `./mvnw -q test -Dtest=ListMyStoresTest,GetStoreProfileTest,SubscriptionsTest,SubscriptionPeriodsTest,SubscriptionCheckoutTest,WompiWebhookProcessorTest,PlanEntitlementsTest,SimulatedSubscriptionPaymentsTest,PlanServiceTest,InventoryLedgerTest,StockServiceTest,AdjustStockTest,StoreProductUseCasesTest,ListingQueriesTest,UpdateStoreOrderStatusTest,StoreDashboardQueryTest,ImportProductsTest,CsvReaderTest,ModularityTests`; revisión de cada caso I1–I11 (prueba o lectura de código citada); sin datos personales en logs del cobro simulado.

**SA-Q2 — Verificación del portal** · Sonnet · `qa-seguridad`
- Aceptación: Playwright (ya instalado) con `mock` y con `http`: recorrido alta de producto → ajuste de stock → pedido del checkout gestionado hasta `DELIVERED` → pago simulado; capturas en 360/768/1280 px; revisión de foco, etiquetas y contraste según §5 y el checklist de MK-F0.

### Orden y paralelismo

Orden relativo de este plan; el intercalado con los demás está en el orden global.

- `database`: SA-D1 (tras AD-D1, por la tabla de `database/migrations/README.md`).
- `backend` (secuencial, una sola sesión de edición): SA-B1 → SA-B2 → SA-B3 → SA-B4 (tras AD-B2) → SA-B5 → SA-B6 → SA-B7 → SA-B8 → SA-B9.
- `frontend`: SA-F1 → (SA-F2, SA-F3, SA-F5, SA-F6, SA-F7, SA-F8, SA-F10 en secuencia) → SA-F4, SA-F9, SA-F11. Puede arrancar con `mock` en paralelo al backend.
- `qa-seguridad`: SA-Q1 tras SA-B9; SA-Q2 tras SA-F11 y backend desplegado en local.

## 9. Riesgos

1. Sin Testcontainers (decisión del usuario): concurrencia de cupos y ajustes solo con mocks y guion manual.
2. Los triggers puente de `V202610141025` (cuando se apliquen) también insertan movimientos al cambiar `inventory.stock`: verificar que detectan el movimiento ya escrito y no duplican.
3. `ddl-auto: update` no crea CHECK, índices parciales ni triggers de solo inserción: hasta Flyway la integridad la garantiza el código.
4. La rama actual cambia nombres de columnas (`business_name` frente a `bussnies_name` de `init.sql`); SA-B1/SA-B6 deben basarse en el estado fusionado.
5. Productos publicados de tiendas con suscripción vencida siguen visibles en el marketplace (A4).
6. `MARKETPLACE_SALE` sin `order_detail_id` (D9): trazabilidad por venta parcial hasta que el checkout pase la referencia.
7. Estados de pedido: una venta `CONFIRMED` cancelada repone stock; una ya enviada no puede cancelarse (transiciones actuales).
8. Configuración nueva (`app.billing.simulated-payments-enabled`, `app.billing.trial-months`) va con valor por defecto en `@Value`; añadirla a cada `*.example` de entorno (`backend/.env.example` y los de local/demo/producción) la hace la sesión principal.

## 10. Requiere aprobación del usuario

- **A1** Precios y límites de la semilla (§4.5): `TRY` 0 / `BASIC` 59.900 / `BUSINESS` 149.900 / `ENTERPRISE` 499.900 COP/mes.
- **A2** Cobro simulado activo por defecto en builds publicados (`app.billing.simulated-payments-enabled=true`).
- **A3** Límites durante la prueba: los de `TRY` (propuesto) o los del plan elegido.
- **A4** Ocultar del marketplace los productos de tiendas con suscripción vencida (propuesto: P1, no en MVP).
- **A5** Cambio de autorización del checkout Wompi de suscripción: de "correo del solicitante" a "rol OWNER" (cambio de seguridad).
- **A6** Fuente Inter en lugar de Geist (dependencia nueva y cambio global).
- **A7** Cualquier componente shadcn que exija un paquete npm nuevo (se añaden en MK-F0).
- **A8** Ejecutar la semilla de planes o migraciones contra una BD (comando contra base de datos).
