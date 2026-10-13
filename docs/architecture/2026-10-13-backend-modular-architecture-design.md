# Diseño: backend de Autopartco como monolito modular

- Fecha: 2026-10-13
- Estado: propuesto (listo para planificar)
- Autor: agente `arquitecto`
- Alcance: `backend/src/main/java/com/autopartco/backend/` y `backend/src/test/java/com/autopartco/backend/`
- Base inspeccionada: `main` @ `25070ee` (202 clases Java de producción, 19 de prueba, `database/init.sql` con 54 tablas)

---

## 1. Intención y motivo

Autopartco son cuatro productos conectados que comparten datos:

| Producto | Paquete | Prefijo REST | Qué cubre (visión) |
|---|---|---|---|
| A. Marketplace | `marketplace` | `/api/v1/marketplace/...` | catálogo, búsqueda por vehículo, comparación entre almacenes, compatibilidad, cotizaciones, carrito, pedidos, seguimiento, reseñas, atención |
| B. SaaS para almacenes | `storesuite` | `/api/v1/stores/{storeId}/...` | sucursales, inventario, ventas, colaboradores, compras a proveedores, alertas de stock, precios, analítica, suscripciones, automatización comercial |
| C. Inteligencia automotriz | `intelligence` | (sin rutas aún) | diagnóstico, identificación de repuestos, recomendación de compatibilidad, historial del vehículo, mantenimiento preventivo, predicción de demanda |
| D. Operaciones internas | `operations` | `/api/v1/admin/...` | administración root, moderación, verificación comercial, soporte, disputas, planes, pagos, auditoría, métricas, anomalías |

El código actual no refleja esa estructura. Problemas medidos:

1. **Acoplamiento cruzado.** `auth` importa `seller` 16 veces; `seller` importa `auth` 19 y `store` 20; `product` importa `store` 14; `common.AuditLogs` importa `auth.entity.Account`. Hay ciclos: `store.StoreAccessService → seller.SellerApplicationRepository`, `seller.SellerPlanEntitlementService → product.ProductRepository` y `product.ProductService → seller.SellerPlanEntitlementService`. `SellerCollaboratorService` inyecta 9 repositorios de 3 módulos y `auth.service.SellerRegistrationService` escribe datos de `seller` y `store`.
2. **Código difícil de leer.** Hay consultas derivadas muy largas (por ejemplo `findByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc`) y servicios que lo hacen todo: `SellerDashboardService` (592 líneas, SQL de ventas, envíos y notificaciones) y `SellerCollaboratorService` (497).
3. **Base de datos incompleta** para el alcance de los cuatro productos (sección 11).

Este diseño reorganiza el backend en módulos con límites verificables (Spring Modulith). **No cambia el esquema ni añade funcionalidad.**

## 2. Objetivos, no-objetivos y anti-objetivos

**Objetivos**
- Estructura `shared / core / marketplace / storesuite / intelligence / operations` con dependencias dirigidas y sin ciclos, verificada por `ApplicationModules.verify()`.
- Cada dominio de `core` expone una API pública (servicios y DTO en la raíz del paquete). Entidades y repositorios son internos.
- Dividir los servicios que lo hacen todo por caso de uso y sustituir las consultas derivadas largas por `@Query` con nombres que expliquen la intención.
- Reorganizar las rutas REST por producto (el frontend se reconstruye desde cero).
- Dejar los productos y capacidades que aún no existen solo con su paquete declarado (`package-info.java`), sin implementación.
- Producir la lista de brechas de esquema por módulo para una pasada posterior del agente `database`.

**No-objetivos**
- No hay funcionalidades nuevas: ni carrito, ni cotizaciones, ni IA, ni soporte, ni ERP.
- No cambia el esquema: ni tablas, ni columnas, ni índices, ni `init.sql`, ni migraciones. Las entidades siguen mapeando las tablas y columnas actuales.
- No hay cambios en el frontend.
- No cambian la forma de los JSON ni los códigos de error (`error.code`). Solo cambian las rutas.
- No se actualizan versiones de dependencias existentes. Las fijaciones de `jackson-databind` y `configuration-processor` se tratan en otra tarea (sección 14).

**Anti-objetivos**
- Una reescritura total que deje el build roto varios días. Cada fase termina con compilación y pruebas en verde.
- Renombrar paquetes sin reducir el acoplamiento. Mover `seller` a `storesuite` manteniendo las mismas dependencias no sirve. El criterio de éxito es que `verify()` pase, no que cambien los nombres.

## 3. Restricciones

- **AGENTS.md**: cada agente edita solo su área. El orden es `database` → `backend` → `frontend` → `qa-seguridad`; este refactor solo afecta a `backend`.
- **Pruebas**: solo con `-Dtest=<Clase>[,<Clase>...]` explícito. **Nunca** `BackendApplicationTests` (`@SpringBootTest` con `ddl-auto: update` modifica la BD de `backend/.env`). Nunca `mvn test`, `verify` o `package` sin `-Dtest` o sin `-DskipTests`.
- **Hook `agent-guard`** (agente `backend`): permite editar `backend/src/main/java/` y `backend/src/test/`. Pide confirmación para `backend/src/main/java/com/autopartco/backend/config/`, `.../shared/config/`, `.../shared/security/`, `backend/src/main/resources/` y `backend/pom.xml`. Las rutas de `shared` ya están añadidas en `.claude/agents/backend.md` y `.opencode/agents/backend.md`. El usuario ya autorizó el cambio de `pom.xml` para añadir Modulith. Mover `config/` y tocar `WebSecurityConfig` seguirá pidiendo confirmación (es correcto).
- **`docs/AUTOPARTCO_AGENT_CONTEXT.md`**: monolito modular, integraciones detrás de adaptadores, autorización en servidor por organización/sucursal/recurso, no usar `float` para dinero, webhooks idempotentes, no rehacer sin inspeccionar.
- **`ddl-auto: update` está activo** (`application.yaml` y `application-dev.yaml`). Cualquier entidad o campo renombrado sin `@Table`/`@Column(name=...)` explícito **crea columnas o tablas nuevas** al arrancar. Reglas obligatorias:
  - No renombrar un campo persistente sin fijar antes `@Column(name = "<columna_actual>")`.
  - No eliminar `@Table(name=...)`.
  - Los nombres simples de entidad deben ser únicos en toda la aplicación (Hibernate usa el nombre simple como nombre de entidad JPQL). Al renombrar una entidad, hay que actualizar todo JPQL que la cite.

## 4. Estructura objetivo y reglas de dependencia

```
com.autopartco.backend
├── BackendApplication
├── shared/                         módulo OPEN (accesible por todos)
│   ├── web/            ApiPaths
│   ├── error/          ApiError, ErrorResponse, GlobalExceptionHandler, *Exception
│   ├── security/       WebSecurityConfig, RsaKey*, CorsConfig, ForbiddenHandler, UnauthorizedEntryPoint
│   ├── config/         AppProperties, JacksonConfig
│   ├── storage/        SupabaseStorageService (+Config, Properties), StorageException
│   ├── persistence/    BaseEntity, State, StateRepository, StateCodes
│   └── location/       Location, LocationRepository
├── core/
│   ├── identity/       cuentas, personas, sesiones, tokens, perfil, direcciones
│   ├── organization/   almacén, sucursal, roles/permisos, colaboradores, acceso, solicitud de almacén, invitaciones
│   ├── catalog/        producto, categoría, fabricante, imágenes, consultas de listado
│   ├── vehicle/        compatibilidades (make/model/version)
│   ├── inventory/      stock y precio base por sucursal
│   ├── pricing/        promociones y cálculo de precio efectivo
│   ├── order/          ventas, envíos, transiciones de estado
│   ├── billing/        planes, suscripción, cobros de suscripción, adaptador Wompi, derechos de plan
│   ├── notification/   correo y notificaciones in-app
│   └── audit/          registro de auditoría
├── marketplace/        A
├── storesuite/         B
├── intelligence/       C (solo package-info)
└── operations/         D
```

### 4.1 Disposición interna de cada dominio de `core`

```
core/<dominio>/
  package-info.java   @ApplicationModule(displayName = "...", allowedDependencies = {...})
  <Servicio>.java     API pública: servicios @Service que usan otros módulos
  <Dto>.java          records de entrada/salida de la API pública
  <Evento>.java       eventos publicados (records en pasado: StoreApplicationApproved)
  web/                controladores propios del dominio (solo identity, notification, billing-webhook)
  internal/           entidades, repositorios, servicios auxiliares, mappers, adaptadores
```

En los productos (`marketplace`, `storesuite`, `operations`) cada capacidad es un subpaquete (`storesuite/staff/`) con su controlador, sus casos de uso y sus DTO web. Nadie importa productos, así que todo lo que contienen es interno.

### 4.2 Reglas

1. Los productos dependen solo de `core.*` y `shared`. **Ningún producto importa otro producto.** La comunicación entre productos se hace con eventos de Spring (`ApplicationEventPublisher`), cuyos records se publican desde `core`.
2. Dentro de `core`, las dependencias siguen este grafo acíclico. Solo se permiten estas aristas:

```
audit         → shared
notification  → shared
identity      → shared, audit, notification
organization  → shared, identity
vehicle       → shared
inventory     → shared
pricing       → shared
catalog       → shared, organization, vehicle, inventory, pricing
billing       → shared, identity, organization, catalog
order         → shared, notification
```

3. **La autorización por usuario y los límites de plan van en los productos.** Los casos de uso de `storesuite`, `marketplace` y `operations` llaman a `organization.StoreAccess.requirePermission(...)` y a `billing.PlanEntitlements.require*(...)` antes de invocar `core`. Los servicios de `core` no resuelven permisos de usuario. Sí siguen **acotados al recurso**: todo método que modifica algo de un almacén recibe `storeId` y filtra por él (`findInStore(storeId, id)`), como defensa en profundidad. Esto rompe los ciclos `organization ↔ billing` y `catalog ↔ billing`.
4. Solo el módulo dueño de una tabla escribe en ella (tabla de dueños en la sección 11.1).
5. **Lecturas entre dominios ("read models").** Una clase `*Queries` puede hacer `SELECT` (SQL nativo o `JdbcClient`/`JdbcTemplate`) sobre tablas de otros dominios para componer vistas. Nunca escribe. Hoy son estas, y cualquier nueva debe añadirse a esta lista:
   - `core.order.SaleQueries`: `sales`, `orders`, `order_details`, `order_addresses`, `shipments`, `shipment_events`, `branches`, `persons`, `accounts`, `inventory`, `products`, `payments`, `payment_*`, `states`.
   - `core.catalog.ListingQueries`: `inventory`, `products`, `branches`, `store`, `states`, `product_images`.
   - `core.organization.StaffDirectory#listMembers`: `collaborators`, `persons`, `accounts`, `roles*`, `permissions`, `states`.
   - `core.vehicle.CompatibilityOptions`: `make`, `model`, `versions` (ya existe como proyección nativa).

### 4.3 Spring Modulith

- **Versión: Spring Modulith 2.1.1.** Se comprobó en la caché local de Maven (`~/.m2/repository/org/springframework/modulith/spring-modulith-bom/2.1.1/`): la BOM 2.1.1 declara `spring-boot-autoconfigure` 4.1.1 y ArchUnit 1.4.2, es decir, está alineada con Boot 4.1.1. El BOM de Boot 4.1.1 **no** gestiona Modulith, así que hay que importar `spring-modulith-bom` en `<dependencyManagement>`.
- **Dependencias** (aprobadas por el usuario; se editan en `pom.xml`, que pide confirmación):
  - `org.springframework.modulith:spring-modulith-bom:2.1.1`, `type=pom`, `scope=import`.
  - `org.springframework.modulith:spring-modulith-api`, `scope=compile`. Solo aporta anotaciones (`@ApplicationModule`, `@NamedInterface`).
  - `org.springframework.modulith:spring-modulith-starter-test`, `scope=test`. Trae `ApplicationModules` y ArchUnit.
  - **No** se añaden `spring-modulith-starter-core` ni `-starter-jpa`: el registro de publicación de eventos necesita la tabla `event_publication`, que sería un cambio de esquema. Se anota como brecha.
- **Detección de módulos.** La estrategia por defecto toma como módulo cada subpaquete directo de `com.autopartco.backend`. Así, `core` sería **un solo módulo** y no se podrían verificar los límites entre `identity`, `organization`, etc. Decisión: una estrategia propia `AutopartcoModuleDetection` (implementa `org.springframework.modulith.core.ApplicationModuleDetectionStrategy`). Trata cada `core.<x>` como módulo de primer nivel y el resto de subpaquetes directos (`shared`, `marketplace`, `storesuite`, `intelligence`, `operations`) también. Va en `src/test/java/com/autopartco/backend/modularity/` y se registra en `src/test/resources/META-INF/spring.factories`. Como solo la usan las pruebas, no hay footprint en tiempo de ejecución.
  - Alternativa descartada como opción principal: módulos anidados (`@ApplicationModule` en `core/<x>/package-info.java`). En Modulith, el código de un módulo anidado solo es visible para su módulo padre y sus hermanos, salvo que se exponga con interfaces nombradas. Eso obligaría a declarar `@NamedInterface` en cada dominio para que los productos lo usen. Si la estrategia propia no funcionara en 2.1.1, se usa esta como plan B (ver sección 13).
- **Interfaces nombradas.** Por defecto la API de un módulo es su paquete raíz y los subpaquetes (`internal`, `web`) quedan ocultos. Regla: no crear `@NamedInterface` salvo excepción registrada en este documento. Hoy no hay ninguna.
- **`shared`** se declara `@ApplicationModule(type = ApplicationModule.Type.OPEN)`.
- **`allowedDependencies`**: cada `package-info.java` de `core.<x>` y de cada producto declara exactamente las aristas de la sección 4.2. El identificador exacto de cada módulo (`identity` o `core.identity`) depende de la estrategia; el constructor lo fija al imprimir `ApplicationModules`.

### 4.4 Asociaciones JPA entre dominios

El esquema obliga a FK entre tablas de dominios distintos (`products.store_id → store`). Regla pragmática:

- **R1. Por defecto, ID en lugar de asociación.** Entre dominios distintos se mapea la FK como `@Column(name = "store_id") private UUID storeId;`, no como `@ManyToOne Store`. La validación de existencia o propiedad se hace llamando a la API del dominio dueño. Es barato en todos los casos actuales:

| Entidad (nuevo nombre) | Asociación actual | Pasa a |
|---|---|---|
| `catalog.internal.Product` | `Store store` | `UUID storeId` |
| `catalog.internal.Product` | `Set<Compatibility> compatibilities` (`@ManyToMany`, tabla `products_compatibilies`) | `@ElementCollection @CollectionTable(name = "products_compatibilies", joinColumns = @JoinColumn(name = "product_id")) @Column(name = "compatibility_id") Set<UUID> compatibilityIds` |
| `inventory.internal.Inventory` | `Branch branch`, `Product product` | `UUID branchId`, `UUID productId` |
| `pricing.internal.Promotion` | `Store store` | `UUID storeId` |
| `organization.internal.Collaborator` | `Person person` | `UUID personId` |
| `organization.internal.StoreApplication` | `Account applicant`, `Account reviewedBy`, `SellerPlan plan/selectedPlan` | `UUID applicantAccountId`, `UUID reviewedByAccountId`, `UUID planId`/`selectedPlanId` (`updatable = false`) |
| `organization.internal.StaffInvitation` | `Account invitedBy` | `UUID invitedByAccountId` (`Store`/`StoreRole` siguen asociados: mismo dominio) |
| `audit.internal.AuditLog` | `Account account` | `UUID accountId` |

- **R2.** Las asociaciones dentro del mismo dominio y hacia `shared` (`State`, `Location`, `BaseEntity`) se mantienen.
- **R3. Excepción controlada.** Si alguna conversión resulta cara, el constructor puede exponer temporalmente un paquete de entidades con `@NamedInterface("jpa")`. Debe anotarla en la sección 13 con un plan de salida. Hoy no hace falta ninguna.
- **Tabla `seller_applications` compartida (organization + billing).** Mezcla dos conceptos: el ciclo de verificación del almacén y el estado de la suscripción. Sin cambiar el esquema, se mapea con **dos entidades sobre la misma tabla**, con columnas disjuntas:
  - `organization.internal.StoreApplication` (dueña de la fila): `id`, `applicant_account_id`, `store_id` (asociación a `Store`), `legal_type`, `status`, `reviewed_by`, `review_notes`, `onboarding_token_*`, `configuration_completed_at` y `BaseEntity`/`@Version`. Además, `plan_id` y `selected_plan_id` como `UUID` con `insertable = true, updatable = false` (el `NOT NULL` obliga a fijarlos al insertar).
  - `billing.internal.StoreSubscription`: `id`, `plan_id`, `selected_plan_id` (asociación a `Plan`, `insertable = false`), `trial_starts_at`, `trial_ends_at`, `paid_through`. Además, en solo lectura (`insertable = false, updatable = false`): `store_id`, `status`, `applicant_account_id`. **No** extiende `BaseEntity` ni tiene `@Version`. Usa `@DynamicUpdate` y bloqueo pesimista (`lockById`), como ya hace el webhook.
  - Así ambas pueden escribir en la misma transacción (por ejemplo, `CompleteStoreSetup`) sin conflicto de versión: solo `StoreApplication` incrementa `version` y los conjuntos de columnas no se pisan. La separación real en dos tablas es la brecha DB-ORG-1 y DB-BIL-1.

### 4.5 Eventos

- Records en la raíz del módulo que publica, en pasado: `StoreApplicationApproved`, `SaleStatusChanged`.
- Efectos laterales (correo, notificación) con `@TransactionalEventListener(phase = AFTER_COMMIT)`. Si el efecto debe ser atómico con la operación, se usa una llamada directa dentro de `core`, no un evento.
- No hay registro de publicación persistente (no hay tabla `event_publication`). Un fallo del listener no reintenta. Se acepta en esta fase porque hoy tampoco hay reintentos.
- Hoy no existe ningún flujo real entre productos. Los eventos se introducen solo cuando sustituyen una llamada directa entre productos.

### 4.6 API pública de cada dominio (contrato para el constructor)

Son nombres orientativos de fachadas; los métodos se derivan de los usos actuales. Todas reciben y devuelven DTO de la raíz, nunca entidades.

- **identity**: `AccountLookup` (`requireByEmail(email) → AccountRef(accountId, personId, email, fullName, stateCode, accountTypeCode)`, `findById`). `AccountProvisioning` (`registerSellerAccount`, `sendSellerVerification`, `createInvitedAccount(email, details)`, `promoteToStoreManager(accountId)`, `activate(accountId)`). `SessionIssuer` (`issue(accountId, ip, userAgent) → LoginResponse`). DTO públicos: `LoginResponse`, `RegisterRequest`, `RegisterResponse`, `InvitedAccountDetails`.
- **organization**: `StoreService`, `BranchService`, `StoreAccess` (`requirePermission`, `permissionsFor`), `StorePermissionCodes`, `StoreApplications` (`submit`, `review`, `completeConfiguration`, `pending`, `ofApplicant`, `requireDocument`, `requireActive`), `StoreFiles` (documentos y marca), `StaffDirectory` (`listMembers`, `assignablePermissions`, `addCollaborator`, `countActive`), `StaffInvitations` (`create`, `resend`, `revoke`, `findPendingByToken`, `lockPendingByToken`, `markAccepted`, `countPending`).
- **catalog**: `ProductCatalog` (`create`, `update`, `setPublished`, `requireOwnedForUpdate`, `delete`, `countInStore`), `ProductImages`, `MasterDataService`, `ListingQueries` (`storeListings`, `publishedListings`, `publishedListing`, `options`). Implementa la SPI `pricing.PromotionTargets`.
- **vehicle**: `CompatibilityService` (`requireAllExist(ids)`, `options()`).
- **inventory**: `StockService` (`createListing(branchId, productId, stock, price)`, `updateListing`, `removeAllForProduct`).
- **pricing**: `PromotionService` (CRUD acotado por `storeId` o plataforma), `PriceCalculator` (antes `PromotionPricingService`; entrada `PricingSubject`, sin entidades de otros dominios), SPI `PromotionTargets`.
- **order**: `SaleQueries`, `SaleStatusService`, `ShipmentService`.
- **billing**: `PlanService`, `PlanEntitlements` (`requireCapacity`, `requireCapability`, `requireSubscriptionActive`, `access`, `usage`), `Subscriptions` (`startTrial(storeId)`, `view(storeId)`), `SubscriptionCheckout` (`create(storeId, accountId, request)`).
- **notification**: `EmailService`, `NotificationService` (`notify(accountId, title, message)`, `listFor(accountId)`, `markRead(accountId, id)`).
- **audit**: `AuditService` (métodos actuales con `accountId` en lugar de `Account`).

## 5. Mapeo completo de clases

Prefijo omitido: `com.autopartco.backend.`. "→ X/Y" significa que la clase se divide entre ambos destinos.

### 5.1 Raíz, `common`, `config`, `exception`, `storage`, `test`

| Actual | Destino |
|---|---|
| `BackendApplication` | se queda |
| `GeneratedKeyPair` | mover a `src/test/java/.../tools/GeneratedKeyPair` (utilidad de desarrollo, no de producción) |
| `common.AuditLogs` | `core.audit.internal.AuditLog` (`@Table("audit_logs")`, `UUID accountId`) |
| `common.BaseEntity` | `shared.persistence.BaseEntity` |
| `common.State` | `shared.persistence.State` |
| `config.ApiConfig` | `shared.web.ApiPaths` (constantes `BASE`, `ADMIN`, `MARKETPLACE`, `STORES`, `ME`) |
| `config.AppProperties` | `shared.config.AppProperties` |
| `config.CorsConfig` | `shared.security.CorsConfig` |
| `config.JacksonConfig` | `shared.config.JacksonConfig` |
| `config.RsaKeyConfig`, `config.RsaKeyProperties` | `shared.security` |
| `config.SupabaseStorageConfig`, `config.SupabaseStorageProperties` | `shared.storage` |
| `config.WebSecurityConfig` | `shared.security.WebSecurityConfig` (quitar `WompiProperties` de `@EnableConfigurationProperties`) |
| `config.WompiProperties` | `core.billing.internal.wompi.WompiProperties` (registrada en `billing.internal.BillingConfig`) |
| `exception.ApiError`, `ErrorResponse`, `GlobalExceptionHandler`, `BadRequestException`, `ConflictException`, `NotFoundException`, `PayloadTooLargeException`, `UnauthorizedException` | `shared.error` |
| `exception.ForbiddenHandler`, `exception.UnauthorizedEntryPoint` | `shared.security` |
| `exception.StorageException` | `shared.storage` |
| `storage.SupabaseStorageService` | `shared.storage.SupabaseStorageService` |
| `test.SecurityTestController`, `test.PrivateEndpointResponse` | **eliminar** (y quitar `/api/v1/test/public` de `PUBLIC_ENDPOINTS`) |

### 5.2 `auth`

| Actual | Destino |
|---|---|
| `auth.controller.AuthController` | → `core.identity.web.AuthController` (`/auth/*`) / `core.identity.web.MeController` (`/me/profile`, `/me/addresses`, `/me/sessions`) / `storesuite.onboarding.StoreApplicationController` (`registerSeller`) |
| `auth.dto.LoginResponse`, `RegisterRequest`, `RegisterResponse` | `core.identity` (API) |
| `auth.dto.AccountSessionResponse`, `AddressLocationRequest`, `LoginRequest`, `LogoutRequest`, `PasswordRecoveryRequest`, `PasswordRecoveryResponse`, `PasswordResetRequest`, `PersonAddressRequest`, `PersonAddressResponse`, `ProfileResponse`, `ProfileUpdateRequest`, `RefreshTokenRequest`, `UnlockAccountRequest`, `UnlockAccountResponse`, `VerifyEmailResponse` | `core.identity.web` |
| `auth.dto.SellerRegistrationRequest`, `SellerRegistrationResponse` | `storesuite.onboarding.SellerSignupRequest` / `SellerSignupResponse` |
| `auth.entity.Account`, `AccountSession`, `AccountToken`, `AccountType`, `LoginAttempt`, `Person`, `PersonAddress` | `core.identity.internal` (`PersonAddress` conserva `Location` de `shared.location`) |
| `auth.mapper.AccountMapper`, `AuthResponseMapper`, `LoginAttemptMapper`, `SessionMapper`, `TokenMapper` | `core.identity.internal` |
| `auth.mapper.AuditLogMapper` | `core.audit.internal.AuditLogMapper` |
| `auth.repository.AccountRepository`, `AccountSessionRepository`, `AccountTokenRepository`, `LoginAttemptRepository`, `PersonAddressRepository`, `PersonRepository` | `core.identity.internal` |
| `auth.repository.TypeAccountRepository` | `core.identity.internal.AccountTypeRepository` |
| `auth.repository.AuditLogsRepository` | `core.audit.internal.AuditLogRepository` |
| `auth.repository.StateRepository` | `shared.persistence.StateRepository` |
| `auth.service.AccountSessionService`, `AccountStatusService`, `AccountUnlockService`, `AuthTokenService`, `EmailVerificationService`, `EmailVerificationTokenService`, `LoginAttemptService`, `LoginService`, `PasswordRecoveryService`, `PersonAddressService`, `ProfileService`, `RefreshTokenHash`, `SessionRefreshService` | `core.identity.internal` (detrás de las fachadas `AccountLookup`, `AccountProvisioning`, `SessionIssuer`) |
| `auth.service.RegistrationService` | `core.identity.internal`; `registerSellerAccount`/`sendSellerVerification` se exponen vía `AccountProvisioning` (`PreparedRegistration` pasa a la raíz) |
| `auth.service.AuditService` | `core.audit.AuditService` (API; firmas con `UUID accountId`) |
| `auth.service.EmailService` | `core.notification.EmailService` (API) |
| `auth.service.SellerRegistrationService` | → `storesuite.onboarding.SubmitStoreApplication` (orquesta identity + billing + organization) / `core.organization.StoreApplications#submit` (persistencia de almacén, solicitud y documentos) |

### 5.3 `product`

| Actual | Destino |
|---|---|
| `product.controller.MasterDataController` | `operations.catalogadmin.CatalogAdminController` |
| `product.controller.ProductController` | → `storesuite.products.StoreProductController` + `storesuite.products.StoreProductImageController` / `marketplace.catalog.ProductBrowseController` (publicados, detalle, opciones) |
| `product.controller.PromotionController` | → `storesuite.promotions.StorePromotionController` / `operations.promotions.PlatformPromotionController` |
| `product.dto.CategoryRequest`, `CategoryResponse`, `ManufacturerRequest`, `ManufacturerResponse` | `core.catalog` (API) |
| `product.dto.ProductCreateRequest`, `ProductUpdateRequest`, `ProductPublicationRequest`, `ProductImageOrderRequest`, `ProductResponse`, `ProductImageResponse`, `ProductOptionsResponse` | `core.catalog` (API; `ProductResponse` es la vista de listado) |
| `product.dto.PromotionResponse`, `PromotionUpsertRequest` | `core.pricing` (API) |
| `product.entity.Category`, `Manufacturer`, `Product`, `ProductImage` | `core.catalog.internal` |
| `product.entity.Compatibility` | `core.vehicle.internal` |
| `product.entity.Inventory` | `core.inventory.internal` |
| `product.entity.Promotion` | `core.pricing.internal` |
| `product.entity.PromotionAttributeOperator`, `PromotionDiscountType`, `PromotionScope`, `PromotionTargetType` | `core.pricing` (API: aparecen en DTO) |
| `product.mapper.ProductImageMapper` | `core.catalog.internal` |
| `product.repository.CategoryRepository`, `ManufacturerRepository`, `ProductRepository`, `ProductImageRepository` | `core.catalog.internal` |
| `product.repository.CompatibilityRepository`, `CompatibilityOptionView` | `core.vehicle.internal` |
| `product.repository.InventoryRepository` | `core.inventory.internal` (sus consultas de listado pasan a `catalog.ListingQueries`) |
| `product.repository.PromotionRepository` | `core.pricing.internal` |
| `product.service.MasterDataService` | `core.catalog.MasterDataService` |
| `product.service.ProductImageService` | `core.catalog.ProductImages` (sin chequeo de permisos; el borrado de producto lo orquesta `storesuite.products.DeleteProduct`) |
| `product.service.ProductImageValidator` | `core.catalog.internal` |
| `product.service.ProductService` | → `core.catalog.ProductCatalog` / `core.catalog.ListingQueries` / `core.inventory.StockService` / `storesuite.products.{CreateProduct, UpdateProduct, PublishProduct}` (permiso + derechos de plan + orquestación) |
| `product.service.PromotionPricingService` | `core.pricing.PriceCalculator` (entrada `PricingSubject`) |
| `product.service.PromotionService` | → `core.pricing.PromotionService` (CRUD acotado; valida objetivos vía la SPI `PromotionTargets` que implementa `catalog`) / `storesuite.promotions` (permiso + capacidad `PROMOTIONS`) / `operations.promotions` |

### 5.4 `seller`

| Actual | Destino |
|---|---|
| `seller.controller.SellerApplicationController` | → `storesuite.onboarding.StoreApplicationController` (solicitud con cuenta existente, mis solicitudes, documento propio, setup) / `operations.storeverification.StoreVerificationController` (pendientes, revisión, documento como root) / `marketplace.stores.StoreBrowseController` (logo/banner público) |
| `seller.controller.SellerDashboardController` | → `storesuite.dashboard.DashboardController` / `storesuite.orders.StoreOrderController` / `storesuite.staff.StaffController` + `storesuite.staff.StaffInvitationPublicController` / `core.notification.web.NotificationController` |
| `seller.controller.SellerPaymentController` | → `storesuite.subscription.SubscriptionController` (checkout) / `core.billing.web.WompiWebhookController` |
| `seller.controller.SellerPlanController` | → `storesuite.subscription.PlanCatalogController` (GET público) / `operations.plans.PlanAdminController` |
| `seller.dto.SellerApplicationCreatedResponse` | `storesuite.onboarding.StoreApplicationCreatedResponse` |
| `seller.dto.SellerApplicationRequest` | `storesuite.onboarding.StoreApplicationRequest` |
| `seller.dto.SellerApplicationResponse` | `core.organization.StoreApplicationResponse` |
| `seller.dto.SellerApplicationReviewRequest` | `core.organization.StoreApplicationReviewRequest` (lo usa `operations`) |
| `seller.dto.SellerCheckoutRequest`, `SellerCheckoutResponse` | `core.billing.CheckoutRequest` / `CheckoutResponse` |
| `seller.dto.SellerCollaboratorAcceptRequest` | `storesuite.staff.InvitationAcceptRequest` (su `AccountDetails` se mapea a `identity.InvitedAccountDetails`) |
| `seller.dto.SellerCollaboratorInvitationResponse` | `storesuite.staff.StaffInvitationResponse` |
| `seller.dto.SellerCollaboratorInviteRequest` | `storesuite.staff.StaffInviteRequest` |
| `seller.dto.SellerCollaboratorResponse` | `storesuite.staff.StaffResponse` (`Member`, `InvitationPreview`, `Accepted` anidados) |
| `seller.dto.SellerPermissionOption` | `core.organization.PermissionOption` |
| `seller.dto.SellerDashboardResponse` | `storesuite.dashboard.DashboardResponse` |
| `seller.dto.SellerNotificationResponse` | `core.notification.NotificationResponse` |
| `seller.dto.SellerOrderResponse`, `SellerOrderSummaryResponse` | `core.order.SaleDetailResponse` / `SaleSummaryResponse` |
| `seller.dto.SellerOrderStatusRequest`, `SellerShipmentCreateRequest`, `SellerShipmentStatusRequest` | `core.order.SaleStatusRequest` / `ShipmentCreateRequest` / `ShipmentStatusRequest` |
| `seller.dto.SellerPlanRequest`, `SellerPlanResponse` | `core.billing.PlanRequest` / `PlanResponse` |
| `seller.dto.SellerSetupResponse` | `storesuite.onboarding.StoreSetupResponse` |
| `seller.entity.SellerApplication` | → `core.organization.internal.StoreApplication` / `core.billing.internal.StoreSubscription` (misma tabla, sección 4.4) |
| `seller.entity.SellerApplicationDocument` | `core.organization.internal.StoreApplicationDocument` |
| `seller.entity.SellerApplicationStatus` | `core.organization.StoreApplicationStatus` |
| `seller.entity.SellerDocumentType` | `core.organization.ApplicationDocumentType` |
| `seller.entity.SellerLegalType` | `core.organization.LegalType` |
| `seller.entity.SellerCollaboratorInvitation` | `core.organization.internal.StaffInvitation` |
| `seller.entity.SellerPayment` | `core.billing.internal.SubscriptionPayment` |
| `seller.entity.SellerPlan` | `core.billing.internal.Plan` |
| `seller.repository.SellerApplicationRepository` | → `core.organization.internal.StoreApplicationRepository` / `core.billing.internal.StoreSubscriptionRepository` (nuevo) |
| `seller.repository.SellerApplicationDocumentRepository` | `core.organization.internal.StoreApplicationDocumentRepository` |
| `seller.repository.SellerCollaboratorInvitationRepository` | `core.organization.internal.StaffInvitationRepository` |
| `seller.repository.SellerPaymentRepository` | `core.billing.internal.SubscriptionPaymentRepository` |
| `seller.repository.SellerPlanRepository` | `core.billing.internal.PlanRepository` |
| `seller.service.SellerApplicationService` | → `core.organization.StoreApplications` (estado, token de onboarding, documentos, almacén público) / `operations.storeverification.ReviewStoreApplication` (revisión + correo de onboarding) / `storesuite.onboarding.CompleteStoreSetup` (orquesta organization + `billing.Subscriptions.startTrial` + `identity.AccountProvisioning.promoteToStoreManager` + `identity.SessionIssuer`) / `storesuite.onboarding.ListMyApplications` |
| `seller.service.SellerCollaboratorService` | → `storesuite.staff.{ListStaff, ListAssignablePermissions, InviteStaffMember, ResendStaffInvitation, RevokeStaffInvitation, PreviewStaffInvitation, AcceptStaffInvitation}` / `core.organization.{StaffDirectory, StaffInvitations}` / `core.identity.AccountProvisioning#createInvitedAccount`. El hash del token y su generación van a `shared.security.TokenHashing` (hoy está duplicado en `SellerApplicationService`, `SellerCollaboratorService` y `RefreshTokenHash`). |
| `seller.service.SellerDashboardService` | → `storesuite.dashboard.StoreDashboardQuery` / `core.order.SaleQueries` (`ORDER_SELECT`, `loadOrder`, resumen mensual) / `core.order.SaleStatusService` (`updateOrderStatus`, `isOrderTransitionAllowed`, `lockSaleStatus`, `updateSaleStatus`) / `core.order.ShipmentService` (`createShipment`, `updateShipmentStatus`, `isShipmentTransitionAllowed`) / `core.notification.NotificationService` (`notifications`, `markNotificationRead`; `notifyCustomer` pasa a `order` resolviendo el `accountId` + `notification.notify`) |
| `seller.service.SellerFileService` | `core.organization.internal.StoreFileStorage` (expuesto vía `StoreFiles`) |
| `seller.service.SellerPaymentService` | → `core.billing.SubscriptionCheckout` (`createCheckout`) / `core.billing.internal.wompi.WompiWebhookProcessor` (`processWebhook`) / `core.billing.internal.wompi.WompiSignatureVerifier` |
| `seller.service.SellerPlanEntitlementService` | `core.billing.PlanEntitlements` (`Access` deja de exponer `SellerApplication`: pasa a `SubscriptionAccess` record; el conteo de uso lo hacen `organization.BranchService#countActive`, `StaffDirectory#countActive` y `catalog.ProductCatalog#countInStore`) |
| `seller.service.SellerPlanService` | `core.billing.PlanService` |

### 5.5 `store`

| Actual | Destino |
|---|---|
| `store.controller.StoreController` | → `storesuite.store.StoreProfileController` (update, status, my-permissions) / `storesuite.branches.BranchController` / `marketplace.stores.StoreBrowseController` (publicados) |
| `store.dto.BranchCreateRequest`, `BranchLocationRequest`, `BranchLocationResponse`, `BranchResponse`, `BranchUpdateRequest`, `StoreRegistrationRequest`, `StoreResponse`, `StoreStatusResponse`, `StoreUpdateRequest` | `core.organization` (API) |
| `store.entity.Branch`, `Collaborator`, `Store`, `StorePermission`, `StoreRole` | `core.organization.internal` (`Collaborator.person` → `UUID personId`) |
| `store.entity.Location` | `shared.location.Location` |
| `store.repository.BranchRepository`, `CollaboratorRepository`, `StorePermissionRepository`, `StoreRepository`, `StoreRoleRepository` | `core.organization.internal` |
| `store.repository.LocationRepository` | `shared.location.LocationRepository` |
| `store.service.BranchService` | `core.organization.BranchService` (sin `StoreAccessService` ni `SellerPlanEntitlementService`; esos chequeos pasan a `storesuite.branches.*`) |
| `store.service.StoreAccessService` | `core.organization.StoreAccess` (busca la persona vía `identity.AccountLookup`) |
| `store.service.StorePermissionCodes` | `core.organization.StorePermissionCodes` |
| `store.service.StoreService` | `core.organization.StoreService` |

### 5.6 Pruebas (`src/test`)

| Actual | Destino |
|---|---|
| `BackendApplicationTests` | sin tocar; **no se ejecuta** |
| `exception.GlobalExceptionHandlerTest` | `shared/error/` |
| `auth.service.AccountSessionServiceTest`, `LoginAttemptServiceTest`, `LoginServiceTest`, `PersonAddressServiceTest`, `ProfileServiceTest`, `RefreshTokenHashTest`, `SessionRefreshServiceTest` | `core/identity/internal/` |
| `auth.service.SellerRegistrationServiceTest` | `storesuite/onboarding/SubmitStoreApplicationTest` |
| `product.service.ProductImageServiceTest`, `ProductImageValidatorTest` | `core/catalog/` |
| `product.service.PromotionPricingServiceTest` | `core/pricing/PriceCalculatorTest` |
| `seller.service.SellerCollaboratorServiceTest` | divididas por caso de uso en `storesuite/staff/` |
| `seller.service.SellerDashboardServiceTest` | → `core/order/` (transiciones) / `storesuite/dashboard/` |
| `seller.service.SellerFileServiceTest` | `core/organization/internal/StoreFileStorageTest` |
| `seller.service.SellerPaymentServiceTest` | `core/billing/` (checkout + `WompiWebhookProcessorTest`) |
| `store.service.StoreAccessServiceTest` | `core/organization/StoreAccessTest` |
| nuevo | `ModularityTests`, `modularity/AutopartcoModuleDetection`, `src/test/resources/META-INF/spring.factories` |

## 6. Mapeo de rutas

Base `ApiPaths.BASE = /api/v1`. Se mantienen el método HTTP, el cuerpo y la respuesta; solo cambia la ruta. "Igual" significa que la ruta no cambia.

| Actual | Nueva | Dueño | Acceso |
|---|---|---|---|
| `POST /auth/register` | igual | identity | público |
| `POST /auth/register/seller` (multipart) | `POST /store-applications` | storesuite.onboarding | público |
| `POST /auth/login`, `/auth/refresh`, `/auth/logout`, `/auth/logout/others` | igual | identity | login/refresh públicos; logout autenticado |
| `GET /auth/verify-email`, `POST /auth/unlock/request`, `GET /auth/unlock`, `POST /auth/password-recovery/request`, `POST /auth/password-recovery/reset` | igual | identity | público |
| `GET /auth/sessions` | `GET /me/sessions` | identity | autenticado |
| `DELETE /auth/sessions/{sessionId}` | `DELETE /me/sessions/{sessionId}` | identity | autenticado |
| `GET`/`PATCH /auth/profile` | `GET`/`PATCH /me/profile` | identity | autenticado |
| `GET`/`POST /auth/addresses`, `PUT`/`DELETE /auth/addresses/{addressId}` | `/me/addresses[/{addressId}]` | identity | autenticado |
| `GET /categories`, `POST /categories`, `PATCH`/`DELETE /categories/{id}` | `/admin/categories[/{id}]` | operations.catalogadmin | ROOT |
| `GET /manufacturers`, `POST`, `PATCH`/`DELETE /{id}` | `/admin/manufacturers[/{id}]` | operations.catalogadmin | ROOT |
| `GET`/`POST /stores/{storeId}/products` | igual | storesuite.products | permiso |
| `PATCH /stores/{storeId}/branches/{branchId}/products/{productId}` | igual | storesuite.products | permiso |
| `PATCH /stores/{storeId}/products/{productId}/publication` | igual | storesuite.products | permiso |
| `DELETE /stores/{storeId}/products/{productId}` | igual | storesuite.products | permiso |
| `POST /stores/{s}/products/{p}/images`; `PATCH .../images/{i}/main`; `PATCH .../images/order`; `PUT`/`DELETE .../images/{i}` | igual | storesuite.products | permiso |
| `GET /products/published` | `GET /marketplace/products` | marketplace.catalog | público |
| `GET /products/{productId}` | `GET /marketplace/products/{productId}` | marketplace.catalog | público |
| `GET /products/options[/manufacturers\|/categories\|/compatibilities]` | `GET /marketplace/product-options[/manufacturers\|/categories\|/compatibilities]` | marketplace.catalog | público |
| `GET`/`POST /stores/{storeId}/promotions`, `PUT`/`DELETE /stores/{storeId}/promotions/{promotionId}` | igual | storesuite.promotions | permiso |
| `GET`/`POST /platform/promotions`, `PUT`/`DELETE /platform/promotions/{id}` | `/admin/promotions[/{id}]` | operations.promotions | ROOT |
| `POST /seller-applications` (multipart, cuenta existente) | `POST /me/store-applications` | storesuite.onboarding | autenticado |
| `GET /seller-applications/me` | `GET /me/store-applications` | storesuite.onboarding | autenticado |
| `GET /seller-applications/root/pending` | `GET /admin/store-applications/pending` | operations.storeverification | ROOT |
| `PATCH /seller-applications/root/{applicationId}/review` | `PATCH /admin/store-applications/{applicationId}/review` | operations.storeverification | ROOT |
| `GET /seller-applications/{a}/documents/{d}` (solicitante o root) | `GET /me/store-applications/{a}/documents/{d}` (solicitante) y `GET /admin/store-applications/{a}/documents/{d}` (ROOT) | storesuite.onboarding / operations | se divide por actor |
| `POST /seller-applications/setup` (token) | `POST /store-applications/setup` | storesuite.onboarding | público con token |
| `GET /seller-applications/stores/{storeId}/brand/{assetType}` | `GET /marketplace/stores/{storeId}/brand/{assetType}` | marketplace.stores | público |
| `GET /seller-dashboard/{storeId}` | `GET /stores/{storeId}/dashboard` | storesuite.dashboard | permiso |
| `GET /seller-dashboard/{storeId}/orders` | `GET /stores/{storeId}/orders` | storesuite.orders | permiso |
| `GET /seller-dashboard/{storeId}/orders/{saleId}` | `GET /stores/{storeId}/orders/{saleId}` | storesuite.orders | permiso |
| `PATCH /seller-dashboard/{storeId}/orders/{saleId}/status` | `PATCH /stores/{storeId}/orders/{saleId}/status` | storesuite.orders | permiso |
| `POST`/`PATCH /seller-dashboard/{storeId}/orders/{saleId}/shipment` | `POST`/`PATCH /stores/{storeId}/orders/{saleId}/shipment` | storesuite.orders | permiso |
| `GET /seller-dashboard/notifications/me` | `GET /me/notifications` | notification | autenticado |
| `PATCH /seller-dashboard/notifications/{id}/read` | `PATCH /me/notifications/{id}/read` | notification | autenticado |
| `GET /seller-dashboard/{storeId}/collaborators` | `GET /stores/{storeId}/staff` | storesuite.staff | permiso |
| `GET /seller-dashboard/{storeId}/collaborators/permissions` | `GET /stores/{storeId}/staff/permission-options` | storesuite.staff | permiso |
| `POST /seller-dashboard/{storeId}/collaborators/invitations` | `POST /stores/{storeId}/staff/invitations` | storesuite.staff | permiso |
| `POST .../invitations/{invitationId}/resend` | `POST /stores/{storeId}/staff/invitations/{invitationId}/resend` | storesuite.staff | permiso |
| `PATCH .../invitations/{invitationId}/revoke` | `PATCH /stores/{storeId}/staff/invitations/{invitationId}/revoke` | storesuite.staff | permiso |
| `GET /seller-dashboard/invitations/preview?token=` | `GET /staff-invitations/preview?token=` | storesuite.staff | público con token |
| `POST /seller-dashboard/invitations/accept` | `POST /staff-invitations/accept` | storesuite.staff | público con token |
| `POST /seller-payments/applications/{applicationId}/checkout` | `POST /stores/{storeId}/subscription/checkout` | storesuite.subscription | autenticado; se mantiene la regla actual: solo el solicitante de la solicitud de ese almacén |
| `POST /seller-payments/wompi/webhook` | `POST /webhooks/wompi` (sin alias) | billing.web | público con firma |
| `GET /seller-plans` | `GET /plans` | storesuite.subscription | público |
| `GET /seller-plans/admin`, `POST /seller-plans/admin`, `PUT`/`DELETE /seller-plans/admin/{id}` | `/admin/plans[/{id}]` | operations.plans | ROOT |
| `PATCH /stores/{id}` | `PATCH /stores/{storeId}` | storesuite.store | permiso |
| `GET /stores/{id}/status` | igual | storesuite.store | autenticado + permiso |
| `GET /stores/{storeId}/permissions` | `GET /stores/{storeId}/my-permissions` | storesuite.store | autenticado |
| `GET /stores/published` | `GET /marketplace/stores` | marketplace.stores | público |
| `GET`/`POST /stores/{storeId}/branches`, `PATCH`/`DELETE /stores/{storeId}/branches/{branchId}` | igual | storesuite.branches | permiso |
| `GET /test/public`, `GET /test/private` | **eliminar** | — | — |

**Seguridad (aprobado por el usuario).** `WebSecurityConfig` se reescribe una sola vez:
1. Lista explícita de rutas públicas, agrupadas por producto: `/auth/*` públicas, `/store-applications`, `/store-applications/setup`, `/staff-invitations/*`, `/plans`, `GET /marketplace/**`, `POST /webhooks/wompi`, `/actuator/health`, `OPTIONS /**`.
2. `/api/v1/admin/**` con `hasAuthority('ROLE_ROOT')`, además de los `@PreAuthorize` que ya existen (defensa en profundidad).
3. Todo lo demás, `authenticated()`.

Esto sustituye los ~35 matchers por ruta. Hay **una única revisión de `qa-seguridad`**, sobre la configuración final: accesos entre almacenes, root frente a cliente y endpoints públicos.

**Cuándo: en la fase 8** (decisión del arquitecto). Motivos:
- La lista de rutas públicas solo es definitiva cuando existen todas las rutas nuevas. Hacerla antes obligaría a reescribirla y revisarla más de una vez.
- Mientras tanto, la configuración actual **falla cerrada**. Las rutas movidas que antes eran `permitAll` caen en `anyRequest().authenticated()` y devuelven 401; las rutas root nuevas siguen protegidas por `@PreAuthorize`. Ninguna fase intermedia abre acceso.
- Nada está desplegado y el frontend no consume la API todavía, así que la indisponibilidad temporal de rutas públicas no afecta a nadie.

Entre las fases 0 y 7 solo se toca `WebSecurityConfig` en la fase 0: se mueve a `shared.security`, se quitan `WompiProperties` y la entrada `/api/v1/test/public`. No se añaden matchers intermedios. Los arranques de humo de las fases 3b y 4b solo verifican el arranque, no las rutas públicas.

## 7. Fases de migración

**En todas las fases:**
- Mover con refactor de IDE (mover paquete/clase) para que se actualicen los imports.
- Borrar del paquete legado lo ya migrado.
- `./mvnw -q -DskipTests compile test-compile`.
- Suite unitaria **por nombre** (`SUITE` = todas las clases de prueba existentes en ese momento salvo `BackendApplicationTests`).
- `ModularityTests`.
- `graphify update .`.

Durante la migración se permiten dependencias de módulos ya migrados hacia paquetes legados (`auth`, `seller`, `store`, `product`). Nunca al revés hacia un paquete ya eliminado.

Comando tipo (Git Bash, desde `backend/`):
```
./mvnw -q -Dtest=GlobalExceptionHandlerTest,AccountSessionServiceTest,LoginAttemptServiceTest,LoginServiceTest,PersonAddressServiceTest,ProfileServiceTest,RefreshTokenHashTest,SessionRefreshServiceTest,SellerRegistrationServiceTest,ProductImageServiceTest,ProductImageValidatorTest,PromotionPricingServiceTest,SellerCollaboratorServiceTest,SellerDashboardServiceTest,SellerFileServiceTest,SellerPaymentServiceTest,StoreAccessServiceTest,ModularityTests test
```
(Al renombrar clases de prueba, se actualiza la lista.)

`ModularityTests` crece fase a fase:
- `printsModules()`: imprime `ApplicationModules.of(BackendApplication.class)`.
- `migratedModulesHaveNoViolations()`: `modules.detectViolations()` filtrado a los módulos ya migrados y `throwIfPresent()`.
- `springBeanAndEntityNamesAreUnique()`: regla ArchUnit, porque nombres simples duplicados en `@Component`/`@Service`/`@RestController`/`@Entity` romperían el arranque y ninguna prueba levanta el contexto.
- `verify()` completo en la fase 8.

**Arranque de humo (autorizado por el usuario).** Al cerrar las fases 3b, 4b y 8: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=validate`, contra la BD de `backend/.env`. Es desechable y no productiva: nada está desplegado. `validate` no muta el esquema y detecta errores de mapeo, nombres de beans duplicados y JPQL roto. No hay otros arranques autorizados (en particular, ninguno con `update`), y `BackendApplicationTests` sigue sin ejecutarse.

**Integración (decisión del orquestador).** Rama `refactor/modular-backend`, **un commit por fase** (3a/3b y 4a/4b son commits distintos), con build y SUITE en verde después de cada uno. Un **único PR** al final. El push y el PR requieren autorización del usuario en ese momento.

| Fase | Contenido | Pruebas/criterio de salida |
|---|---|---|
| **0. Base** | `pom.xml`: BOM Modulith 2.1.1, `spring-modulith-api` y `spring-modulith-starter-test` (confirmación del hook). `ModularityTests` + estrategia de detección + `spring.factories`. Mover todo `shared` (5.1), incluido `config/` → `shared.config`/`shared.security` (el hook ya pide confirmación en las rutas nuevas). Eliminar `test/` y su entrada en `PUBLIC_ENDPOINTS` (aprobado). Mover `GeneratedKeyPair` a `src/test`. `package-info.java` con `@ApplicationModule(type = OPEN)` en `shared`. Crear los paquetes vacíos `core/*`, `marketplace`, `storesuite`, `intelligence`, `operations` con `package-info.java`. | SUITE + `ModularityTests.printsModules`: la lista impresa coincide con la sección 4. |
| **1. audit + notification** | `AuditLogs`/`AuditService`/`AuditLogMapper`/`AuditLogsRepository` → `core.audit` (`accountId`). `EmailService` → `core.notification`. Notificaciones in-app (consultas JDBC de `SellerDashboardService`) → `NotificationService` + `core.notification.web.NotificationController` en `/me/notifications`. | SUITE; sin violaciones en `audit`/`notification`. |
| **2. identity** | Todo `auth` salvo `SellerRegistrationService` (pasa temporalmente a `seller.service`) → `core.identity` con las fachadas de la sección 4.6. `AuthController` + `MeController`. Rutas `/auth`, `/me` (sin cambios de seguridad: `/auth/*` conserva sus matchers actuales y `/me/*` queda autenticado por defecto). | Pruebas de identity movidas + SUITE; sin violaciones en `identity`. |
| **3a. organization** | `store` + solicitud de almacén, documentos, invitaciones y archivos → `core.organization` con IDs (R1). `SellerApplication` pasa entera a `organization.internal.StoreApplication`, mapeando todavía todas las columnas, y el código legado de `seller` la usa temporalmente. Se crean `storesuite.store`, `storesuite.branches` y `marketplace.stores` (StoreBrowse publicado + marca), con permiso y derechos de plan en el caso de uso. | `StoreAccessTest`, `StoreFileStorageTest` + SUITE. Arranque de humo opcional. |
| **3b. billing** | `StoreSubscription` (misma tabla, sección 4.4). `StoreApplication` queda sin columnas de suscripción salvo los `planId` insert-only. `SellerPlan`, `SellerPayment`, Wompi, `PlanEntitlements`, `PlanService`, `SubscriptionCheckout` → `core.billing`. Webhook en `/webhooks/wompi`, sin alias. `storesuite.subscription` (checkout y planes públicos), `operations.plans`. | Pruebas de billing + SUITE. **Arranque de humo con `validate`** (autorizado). |
| **4a. vehicle, inventory, pricing, catalog** | Separar `product` en los cuatro dominios con R1 (`Product.storeId`, `compatibilityIds`, `Inventory.branchId/productId`, `Promotion.storeId`). Crear `ListingQueries` con SQL nativo para listados, `PriceCalculator` con `PricingSubject` y la SPI `PromotionTargets`. | `PriceCalculatorTest`, pruebas de imágenes + SUITE. |
| **4b. Endpoints de catálogo** | `storesuite.products`, `storesuite.promotions`, `marketplace.catalog`, `operations.catalogadmin`, `operations.promotions`. | SUITE. **Arranque de humo con `validate`.** |
| **5. order** | `SaleQueries`, `SaleStatusService`, `ShipmentService` desde `SellerDashboardService`. `storesuite.orders` y `storesuite.dashboard`. | Pruebas de transiciones movidas + SUITE. |
| **6. storesuite restante + operations** | `storesuite.onboarding` (`SubmitStoreApplication`, `CompleteStoreSetup`, `ListMyApplications`, documentos), `storesuite.staff` (7 casos de uso), `operations.storeverification`. Se eliminan los paquetes `auth`, `seller`, `store` y `product`. | Pruebas divididas + SUITE. |
| **7. Legibilidad** | Renombrar consultas (sección 9.2), quitar FQCN en línea, `StateCodes`, `TokenHashing`, revisar tamaños. | SUITE. |
| **8. Cierre** | **Reescritura consolidada de `WebSecurityConfig`** (sección 6). `package-info.java` de los marcadores (sección 8) y `allowedDependencies` en todos los módulos. `ModularityTests.verify()` completo, sin filtros. Opcional: `Documenter` para generar diagramas en `target/`. Actualizar `docs/backend/*.md` con las rutas nuevas (por `backend` o vía orquestador). | SUITE + `verify()` en verde. **Arranque de humo con `validate`.** Revisión de `qa-seguridad`. |

Cada fase (y cada subfase 3a/3b, 4a/4b) es un commit en `refactor/modular-backend`. Hay un único PR al final.

## 8. Marcadores por producto (solo `package-info.java`, sin código)

Cada `package-info.java` lleva un Javadoc de 2 a 4 líneas con el alcance previsto y la etiqueta "No implementado".

- **marketplace**: `catalog` (existe), `stores` (existe), `vehiclesearch` (búsqueda por vehículo), `comparison` (comparación entre almacenes), `quotes` (cotizaciones), `cart`, `checkout`, `tracking` (seguimiento de pedido del comprador), `reviews`, `customercare` (atención).
- **storesuite**: `store`, `branches`, `products`, `promotions`, `orders`, `staff`, `dashboard`, `onboarding`, `subscription` (existen); `inventory` (movimientos, ajustes, transferencias), `purchasing` (compras a proveedores), `stockalerts`, `pricelists`, `analytics`, `automation` (automatización comercial), `integrations` (Alegra/Siigo, importación CSV).
- **intelligence**: `diagnosis`, `partidentification`, `fitmentadvisor` (recomendación de compatibilidad), `vehiclehistory`, `maintenance`, `demandforecast`. No se añade `allowedDependencies` hasta que haya código.
- **operations**: `storeverification`, `catalogadmin`, `promotions`, `plans` (existen); `moderation`, `support`, `disputes`, `payments` (consola de cobros), `auditlog` (visor), `metrics`, `anomalies`.
- **core**: no se crean dominios vacíos nuevos. `integration` (adaptadores ERP) se decidirá cuando exista el primer conector; por ahora vive como marcador en `storesuite.integrations`.

## 9. Convenciones de nombres y legibilidad

### 9.1 Reglas

- Paquetes en minúscula y de una palabra (`storeverification`).
- **Sin prefijo redundante.** Dentro de `storesuite` no se usa `Seller*`; dentro de `order` no se usa `Order*` cuando basta `Sale*`.
- Entidades con el nombre del concepto (`StoreApplication`, `StaffInvitation`, `Plan`, `AuditLog`). `@Table` siempre explícito.
- Casos de uso en productos: clase `VerboSustantivo` (`InviteStaffMember`) con **un** método público `handle(...)`. Servicios de `core`: sustantivo (`BranchService`, `PriceCalculator`).
- DTO web: `*Request`/`*Response`. Records de API de `core` por concepto (`AccountRef`, `SubscriptionAccess`, `PricingSubject`).
- Repositorios: métodos de 1 a 4 palabras. Prefijos `find`/`exists`/`count`, `lock*` para `PESSIMISTIC_WRITE` e `*InStore(storeId, ...)` para acotar por almacén. Cualquier consulta derivada con más de 2 condiciones o con orden pasa a `@Query` con nombre de intención.
- Sin nombres totalmente calificados en línea (`java.util.List`, `org.springframework...DeleteMapping` en `AuthController`, `java.math.BigDecimal` en `SellerDashboardService`). Se usan imports.
- Constantes de estado en `shared.persistence.StateCodes` (`ACTIVE`, `INACTIVE`, `PENDING`, `LOCKED`) en lugar de literales repetidos.
- Tamaño orientativo: ninguna clase de servicio por encima de unas 250 líneas. Ninguna clase con más de 6 dependencias inyectadas; si las supera, se divide.
- Los códigos de error (`SELLER_*`, etc.) **no se renombran** en este refactor: son contrato.

### 9.2 Consultas a renombrar

| Actual | Nuevo |
|---|---|
| `countByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullAndExpiresAtAfter` | `countPendingInStore(storeId, now)` |
| `findByStore_IdAndAcceptedAtIsNullAndRevokedAtIsNullOrderByCreatedAtDesc` | `findPendingInStore(storeId)` |
| `findByStore_IdAndEmailIgnoreCaseAndAcceptedAtIsNullAndRevokedAtIsNull` | `findPendingByEmail(storeId, email)` |
| `findByIdAndStore_Id` (invitaciones) | `findInStore(storeId, id)` |
| `findByTokenHash` / `findByTokenHashForUpdate` | `findByTokenHash` / `lockByTokenHash` |
| `findByApplication_IdOrderByDocumentType` | `findAllOfApplication(applicationId)` |
| `findByEnabledTrueOrderByMonthlyPriceCopAsc` | `findEnabledByPrice()` |
| `findByScopeAndStore_IdOrderByCreatedAtDesc` | `findStorePromotions(storeId)` |
| `findByScopeAndStoreIsNullOrderByCreatedAtDesc` | `findPlatformPromotions()` |
| `findByIdAndScopeAndStore_Id` | `findStorePromotion(storeId, id)` |
| `findByIdAndScopeAndStoreIsNull` | `findPlatformPromotion(id)` |
| `findByPerson_IdOrderByIsDefaultDescCreatedAtDesc` | `findAllOfPerson(personId)` |
| `findFirstByPerson_IdAndIsDefaultTrueOrderByCreatedAtDesc` | `findDefaultOf(personId)` |
| `findFirstByPerson_IdOrderByCreatedAtDesc` | `findLatestOf(personId)` |
| `findFirstByPerson_IdAndIdNotOrderByCreatedAtDesc` | `findLatestOtherThan(personId, addressId)` |
| `findByProduct_IdOrderByDisplayOrderAscCreatedAtAsc` | `findAllOfProduct(productId)` |
| `findByProduct_IdInOrderByProduct_IdAscDisplayOrderAscCreatedAtAsc` | `findAllOfProducts(productIds)` |
| `findByEmailIgnoreCaseForUpdate` | `lockByEmail(email)` |
| `findByOnboardingTokenHashForUpdate` | `lockByOnboardingToken(hash)` |
| `findByRefreshTokenHashForUpdate` | `lockByRefreshTokenHash(hash)` |
| `findByTokenAndPurposeForUpdate` | `lockByTokenAndPurpose(token, purpose)` |
| `countByStore_IdAndState_Code` (Branch, Collaborator) | `countActiveInStore(storeId)` |
| `existsByPerson_IdAndStore_Id` | `existsInStore(personId, storeId)` |
| `existsByStore_Id` / `existsByStore_IdAndStatus` | `existsForStore(storeId)` / `isActive(storeId)` |

## 10. Contratos que no cambian

- JSON de entrada y salida y `error.code` idénticos (sección 2). `ProductResponse`, `SellerOrderResponse` (ahora `SaleDetailResponse`), etc. conservan sus componentes en el mismo orden.
- Reglas de autorización idénticas en semántica:
  - Permiso de almacén por código.
  - `ROLE_ROOT` para administración.
  - El checkout solo lo hace el solicitante de la solicitud.
  - El documento lo descarga el solicitante o root (ahora en dos rutas).
  - `StoreAccess` deniega si la solicitud del almacén existe y no está `ACTIVE`.
- Transacciones: los casos de uso de producto son `@Transactional` y engloban las llamadas a `core`. Se conserva la atomicidad actual (por ejemplo, registro de vendedor y aceptación de invitación). Siguen igual la limpieza de archivos subidos ante fallo y la sincronización `afterCompletion` de `ProductImageService`.
- Enlaces de correo al frontend (`/seller/setup`, `/seller/invitation`): sin cambios. Son responsabilidad del frontend y quedan fuera de alcance (sección 14).

## 11. Brechas de base de datos por módulo (para el agente `database`)

### 11.1 Tablas y su dueño

| Dueño | Tablas |
|---|---|
| shared | `states`, `locations` |
| identity | `persons`, `accounts`, `types_accounts`, `account_sessions`, `account_tokens`, `login_attempts`, `person_addresses` |
| organization | `store`, `branches`, `roles`, `permissions`, `roles_permissions`, `collaborators`, `roles_collaborators`, `branches_collaborators`*, `seller_applications` (columnas de ciclo), `seller_application_documents`, `seller_collaborator_invitations` |
| billing | `seller_plans`, `seller_applications` (columnas de suscripción), `seller_payments`, `payments`, `payment_methods`, `payment_statuses`, `payment_events`*, `receipts`* |
| catalog | `products`, `product_images`, `categories`, `manufacturers`, `products_compatibilies`, `specifications`*, `units`*, `units_specifications`*, `products_specifications`* |
| vehicle | `make`, `model`, `versions`, `compatibilities` |
| inventory | `inventory`, `inventory_movements`* |
| pricing | `promotions` |
| order | `orders`, `order_addresses`, `sales`, `order_details`, `shipments`, `shipment_statuses`, `shipment_events` |
| notification | `notifications` |
| audit | `audit_logs` |
| marketplace | `store_reviews`* |
| operations | `claims`* |

\* La tabla existe, pero ningún código la usa.

### 11.2 Brechas

**Transversales**
- DB-X-1: no hay herramienta de migraciones versionadas. `ddl-auto: update` está activo en `application.yaml` (que aplica también a producción).
- DB-X-2: **deriva entre `init.sql` y la BD real.** `seller_plans.max_branches`, `max_products`, `max_users` y `capabilities` (mapeadas en `SellerPlan`) no existen en `init.sql`; solo las crea `ddl-auto`. Una BD creada desde `init.sql` falla al usar planes.
- DB-X-3: no existe la tabla `event_publication` (registro de eventos de Modulith) ni un outbox, así que los eventos no son fiables.
- DB-X-4: el dinero tiene tipos heterogéneos: `DECIMAL(12,2)` en inventario, ventas y promociones; `BIGINT` en COP en planes; centavos en `seller_payments`. No hay reglas de impuestos ni redondeo.
- DB-X-5: `states` es un catálogo genérico compartido por cuentas, almacenes, productos y ventas, sin tipo ni ámbito. Las transiciones de cada dominio no están restringidas.

**identity**
- DB-ID-1: no hay tipo de identificación (CC/NIT/CE/PAS); `identification` es única global en `VARCHAR(20)`.
- DB-ID-2: no hay roles de plataforma separados (root frente a soporte). Solo existe `types_accounts`.
- DB-ID-3: no hay consentimientos ni preferencias de privacidad, ni MFA.
- DB-ID-4: no hay vehículos del cliente (garaje), que son base de búsqueda por vehículo e historial.

**organization**
- DB-ORG-1: `seller_applications` mezcla verificación y suscripción. Habría que separar `store_verifications` (con historial de revisiones) de la suscripción.
- DB-ORG-2: no hay estado de moderación ni suspensión del almacén, con motivo y actor.
- DB-ORG-3: la tabla se llama `store` en singular y las columnas `bussnies_name` y `bussnies_email` tienen errata. No hay `slug` ni perfil público (la descripción vive en `configuration` JSONB).
- DB-ORG-4: `branches_collaborators` existe pero no se usa. No hay alcance por sucursal en los permisos.
- DB-ORG-5: no hay horarios ni datos operativos de sucursal.

**catalog / vehicle**
- DB-CAT-1: `products.sku` es `UNIQUE` **global**, lo que provoca colisiones entre almacenes; debería ser único por `store_id`.
- DB-CAT-2: no hay separación entre producto base compartido y oferta del vendedor. `products` pertenece al almacén.
- DB-CAT-3: no hay identificadores (OEM, referencias cruzadas, códigos externos). `categories` solo tiene `code` (sin nombre, jerarquía ni slug). No se distingue marca de fabricante.
- DB-CAT-4: no hay estado de moderación del listado.
- DB-VEH-1: `versions` no tiene años, motor ni carrocería. `compatibilities` no tiene fuente, evidencia ni estado (declarada/confirmada/sugerida).
- DB-VEH-2: no hay decodificación ni almacenamiento de VIN.

**inventory / pricing**
- DB-INV-1: `inventory_movements` existe, pero el código actualiza `inventory.stock` sin escribir movimientos, así que no hay trazabilidad (es una prioridad del producto). No hay reservas, snapshots ni transferencias.
- DB-INV-2: no hay umbral mínimo para alertas de stock.
- DB-INV-3: no hay proveedores, órdenes de compra ni recepciones.
- DB-PRC-1: el precio vive en `inventory`. No hay listas de precios, historial de precios ni impuestos (IVA). Las promociones no tienen límite de uso ni cupones.

**order**
- DB-ORD-1: no hay carrito ni cotizaciones.
- DB-ORD-2: `order_details` no guarda instantánea de la descripción del producto. `orders` no guarda totales ni moneda.
- DB-ORD-3: no hay historial de estados del pedido o la venta (solo de envíos). Las ventas usan `states` genérico.
- DB-ORD-4: no hay devoluciones, reembolsos ni garantías.

**billing**
- DB-BIL-1: no hay tablas de suscripciones, periodos ni versiones de plan (todo está embebido en `seller_applications`).
- DB-BIL-2: no hay tabla de eventos de webhook para idempotencia y auditoría. La idempotencia actual depende de `reference` y `provider_transaction_id`.
- DB-BIL-3: no hay facturas de suscripción. `payments` (pedidos) no tiene clave de idempotencia ni relación con el intento externo.

**notification / audit**
- DB-NOT-1: solo hay notificaciones in-app. No hay canal, plantilla, preferencia ni registro de correos enviados.
- DB-AUD-1: `audit_logs` no tiene columna de alcance (almacén/organización), IP ni user agent (van en `metadata`), así que no se puede filtrar por tenant.

**Productos**
- marketplace: no hay reseñas de producto, preguntas y respuestas, conversaciones de atención ni favoritos. `store_reviews` existe pero no se usa.
- storesuite: no hay `integration_connections`, `external_entity_mappings`, `sync_runs`, `sync_conflicts` (Alegra/Siigo), trabajos de importación CSV, reglas de automatización ni agregados analíticos.
- intelligence: no existe nada: sesiones de diagnóstico, síntomas, hipótesis, fuentes, versiones de modelo/prompt, feedback, historial y planes de mantenimiento, pronósticos de demanda, embeddings (`pgvector` sería una dependencia de infraestructura y requiere aprobación).
- operations: `claims` es mínima (sin responsable, mensajes ni resolución). No hay tickets de soporte, acciones de moderación, reportes, métricas ni anomalías.

## 12. Decisiones del arquitecto (no tomadas por el usuario)

1. **Rutas de identity:** `/api/v1/auth/*` para el flujo sin sesión y la gestión del token (incluidos `logout` y `logout/others`); `/api/v1/me/*` para profile, addresses, sessions, notifications y store-applications del usuario.
2. **Estrategia propia de detección de módulos** (solo en pruebas) para que cada `core.<x>` sea un módulo de primer nivel. Los módulos anidados quedan como plan B.
3. **Solo `spring-modulith-api` en compilación y `starter-test` en pruebas.** No se añade el runtime ni el registro de eventos (necesitaría tabla).
4. **La autorización de usuario y los límites de plan van en los productos.** `core` queda acotado por recurso. Así se rompen los ciclos.
5. **Regla R1** (IDs entre dominios) y uso de `@ElementCollection` para `products_compatibilies`.
6. **Dos entidades sobre `seller_applications`** con columnas disjuntas y una sola `@Version`.
7. **`State` y `Location` en `shared`** (usados por identity y organization).
8. **Las invitaciones a colaboradores se persisten en `organization`**, no en `storesuite`, porque aceptarlas crea colaborador y rol, y billing cuenta las pendientes.
9. **Los pagos de pedidos (`payments*`) pertenecen a `billing`.** `order` solo los lee.
10. **Algunos dominios de `core` tienen controladores propios** (identity, notification en `/me/notifications`, webhook de billing) porque no pertenecen a ningún producto.
11. **El listado compuesto** (producto + stock + precio efectivo + imágenes) vive en `catalog.ListingQueries`, porque lo usan marketplace y storesuite.
12. **Las lecturas SQL entre dominios se permiten solo en clases `*Queries` listadas.** Las escrituras solo las hace el dueño.
13. **Contratos JSON y `error.code` sin cambios.** Solo cambian las rutas.
14. **Se mantiene la regla de autorización del checkout** (solo el solicitante). Pasarla a un permiso de almacén sería un cambio de seguridad.
15. **`GeneratedKeyPair` se mueve a `src/test`.**
16. **La reescritura consolidada de `WebSecurityConfig` va en la fase 8**, con una sola revisión de `qa-seguridad`. Justificación en la sección 6: lista pública definitiva solo al final y comportamiento fail-closed mientras tanto.

## 13. Abierto para el constructor

- Confirmar en 2.1.1 la firma de `ApplicationModuleDetectionStrategy` y la clave en `spring.factories`. Si no funciona, aplicar el plan B (módulos anidados con `@NamedInterface`) y dejarlo anotado aquí.
- Confirmar la API de `Violations` (`filter`/`throwIfPresent`) para el filtrado por fases. Si no existe, usar `@Disabled` en `verify()` hasta la fase 8 y comprobar a mano la salida de `detectViolations()`.
- Fijar los identificadores exactos de módulo para `allowedDependencies` (imprimir `ApplicationModules`).
- En `ListingQueries`, conservar el filtro y el orden exactos de `InventoryRepository.findAllInStore`, `findPublished` y `findPublishedByProductId` (leer su JPQL antes de reescribirlo en SQL nativo).
- Comprobar que el `@ElementCollection` sobre `products_compatibilies` no provoca que `ddl-auto` añada otra FK. Revisar el log del arranque de humo; si la añade, mapear con `@ManyToMany` hacia una entidad mínima de vehicle y registrar la excepción R3.
- Para reglas que hoy no están claras (por ejemplo, si `StoreAccess` debe exigir `ACTIVE` también en lecturas), conservar el comportamiento actual y anotarlo; no "corregirlo" de paso.

## 14. Decisiones del usuario y del orquestador (2026-10-13)

**Del usuario**
- U1. Nada está desplegado. La BD de `backend/.env` es desechable (no productiva, credenciales de un solo uso).
- U2. **Autorizados** los arranques de humo con `ddl-auto=validate` en las fases 3b, 4b y 8 contra esa BD.
- U3. **Aprobada** la reescritura consolidada de `WebSecurityConfig` (rutas públicas explícitas, `/api/v1/admin/**` solo para root, el resto autenticado), con una sola revisión de `qa-seguridad`. Ubicación elegida por el arquitecto: fase 8 (decisión 16).
- U4. **Aprobado** eliminar los endpoints `/test/*` (fase 0).
- U5. **Aprobado y hecho:** `.claude/agents/backend.md` y `.opencode/agents/backend.md` piden confirmación también para `backend/src/main/java/com/autopartco/backend/shared/config/` y `.../shared/security/` (se mantiene la regla de `config/`). La fase 0 puede mover `config/` a `shared/`.
- U6. **Aprobada** la dependencia Spring Modulith en `pom.xml` (el hook pedirá confirmación al editarlo).
- U7. El push de la rama y la apertura del PR final **siguen requiriendo autorización** del usuario en ese momento.

**Del orquestador**
- O1. Integración: rama `refactor/modular-backend`, un commit por fase con build en verde y un único PR al final.
- O2. Wompi: como nada está desplegado, el webhook pasa directamente a `POST /api/v1/webhooks/wompi`, **sin alias**. El usuario configura esa URL en el panel sandbox de Wompi cuando lo prepare.
- O3. Los enlaces de correo `/seller/setup` y `/seller/invitation` no cambian. Son responsabilidad del frontend y quedan fuera de alcance.
- O4. Las fijaciones de `jackson-databind` 2.14.1 y `spring-boot-configuration-processor` 3.1.5 en `pom.xml` quedan fuera de alcance; ya hay otra tarea en curso para ellas.
- O5. Modulith 2.1.1 con solo `spring-modulith-api` (compile) y `spring-modulith-starter-test` (test), sin registro de publicación de eventos (confirma la decisión 3 del arquitecto).

**Pendiente de autorización:** solo el push y el PR finales (U7). No queda ninguna otra pregunta abierta para el usuario.
