# Graph Report - Autopartco-dev  (2026-10-13)

## Corpus Check
- 244 files · ~74,230 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 172 file(s) not represented in the graph (top: .class 161, (none) 3, .example 2)

## Summary
- 1755 nodes · 6172 edges · 82 communities (71 shown, 11 thin omitted)
- Extraction: 92% EXTRACTED · 8% INFERRED · 0% AMBIGUOUS · INFERRED: 477 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `fc2f2031`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- lombok.AllArgsConstructor
- uuid
- ProductService
- PromotionService
- size
- SellerDashboardService.java
- SellerCollaboratorService.java
- org.springframework.security.core.Authentication
- Convenciones de API Backend - Autopartco
- SellerPlanEntitlementService
- .findByEmailIgnoreCase
- ProductImageValidator
- ConflictException
- AccountRepository
- BranchService.java
- MasterDataService
- LoginAttemptService.java
- org.springframework.transaction.annotation.Transactional
- ApiError
- org.junit.jupiter.api.Test
- PersonAddressService
- org.springframework.stereotype.Service
- AuthController.java
- AccountToken
- WebSecurityConfig.java
- org.springframework.http.ResponseEntity
- RegistrationService
- AppProperties
- RsaKeyConfig.java
- SellerRegistrationService.java
- org.springframework.context.annotation.Bean
- Convenciones de API Backend - Autopartco
- org.springframework.stereotype.Component
- ProfileService
- SellerPaymentService
- SellerApplicationController
- ProductOptionsResponse
- PasswordRecoveryService.java
- AUTOPARTCO_PREDEVELOPMENT_RUNBOOK.md
- SellerApplicationService.java
- AUTOPARTCO — Definición integral del producto y plan de desarrollo
- GeneratedKeyPair.java
- Seguridad del Backend
- Manejo centralizado de errores de API
- Manejo centralizado de errores de API
- Seguridad del Backend
- org.springframework.web.bind.annotation.GetMapping
- BackendApplication.java
- com.autopartco:backend
- com.autopartco:backend
- Guía de trabajo con Git y GitHub
- PromotionTargetType
- PromotionPricingService
- Autopartco — Contexto base para agentes de IA
- 15. Skills y herramientas para agentes de IA
- Registro y planes para vendedores
- EmailService
- 12. Modelo de datos objetivo
- 17. Hoja de ruta y entregables
- Registro y verificacion de correo
- 5. Marketplace para compradores
- Registro y verificacion de correo
- PromotionScope
- Autopartco — Backlog de preparación (antes del desarrollo funcional)
- 6. Portal comercial para almacenes
- AccountStatusService
- 10. Funciones de inteligencia artificial
- 14. Experiencia de usuario y diseño visual
- 7. Integraciones con Alegra y Siigo
- 13. Arquitectura técnica
- 16. Calidad, seguridad y operación
- 2. Problema, mercado y propuesta de valor
- 9. Pagos de demostración y Wompi sandbox
- Imágenes de productos
- PromotionAttributeOperator
- 11. Analítica y predicción: qué medir antes de entrenar
- 20. Métricas de éxito del producto
- 4. Tipos de cuenta, organizaciones y permisos
- 1. Definición ejecutiva

## God Nodes (most connected - your core abstractions)
1. `Account` - 92 edges
2. `BaseEntity` - 61 edges
3. `State` - 51 edges
4. `AccountRepository` - 48 edges
5. `ConflictException` - 47 edges
6. `Store` - 47 edges
7. `SellerCollaboratorService` - 45 edges
8. `ProductService` - 42 edges
9. `Promotion` - 36 edges
10. `SellerApplication` - 35 edges

## Surprising Connections (you probably didn't know these)
- `CORS` --references--> `WebSecurityConfig`  [INFERRED]
  docs/backend/security.md → backend/src/main/java/com/autopartco/backend/config/WebSecurityConfig.java
- `CORS` --references--> `WebSecurityConfig`  [INFERRED]
  backend/bin/docs/backend/security.md → backend/src/main/java/com/autopartco/backend/config/WebSecurityConfig.java
- `AuthController` --references--> `AccountSessionService`  [EXTRACTED]
  backend/src/main/java/com/autopartco/backend/auth/controller/AuthController.java → backend/src/main/java/com/autopartco/backend/auth/service/AccountSessionService.java
- `AuthController` --references--> `AccountUnlockService`  [EXTRACTED]
  backend/src/main/java/com/autopartco/backend/auth/controller/AuthController.java → backend/src/main/java/com/autopartco/backend/auth/service/AccountUnlockService.java
- `AuthController` --references--> `EmailVerificationService`  [EXTRACTED]
  backend/src/main/java/com/autopartco/backend/auth/controller/AuthController.java → backend/src/main/java/com/autopartco/backend/auth/service/EmailVerificationService.java

## Import Cycles
- None detected.

## Communities (82 total, 11 thin omitted)

### Community 0 - "lombok.AllArgsConstructor"
Cohesion: 0.12
Nodes (29): Account, AccountSession, AccountType, LoginAttempt, Person, PersonAddress, AuditLogMapper, AuditLogsRepository (+21 more)

### Community 1 - "uuid"
Cohesion: 0.05
Nodes (9): CategoryRepository, CompatibilityOptionView, CompatibilityRepository, InventoryRepository, ManufacturerRepository, ProductRepository, SellerApplicationDocumentRepository, SellerApplicationRepository (+1 more)

### Community 2 - "ProductService"
Cohesion: 0.06
Nodes (11): StorageException, ProductImageResponse, ProductResponse, ProductImageMapper, ProductImageRepository, ImageBackup, ProductImageService, ProductService (+3 more)

### Community 3 - "PromotionService"
Cohesion: 0.24
Nodes (3): PromotionResponse, PromotionUpsertRequest, PromotionService

### Community 4 - "size"
Cohesion: 0.08
Nodes (3): ProfileUpdateRequest, ProductUpdateRequest, StoreUpdateRequest

### Community 5 - "SellerDashboardService.java"
Cohesion: 0.15
Nodes (9): DeliveryAddress, Event, Item, SellerOrderResponse, Shipment, SellerOrderSummaryResponse, SellerDashboardService, SellerOrderBase (+1 more)

### Community 6 - "SellerCollaboratorService.java"
Cohesion: 0.08
Nodes (15): PersonRepository, TypeAccountRepository, AccountDetails, SellerCollaboratorAcceptRequest, Accepted, InvitationPreview, SellerCollaboratorInvitationRepository, SellerCollaboratorService (+7 more)

### Community 7 - "org.springframework.security.core.Authentication"
Cohesion: 0.11
Nodes (8): ProductController, ProductCreateRequest, ProductImageOrderRequest, ProductPublicationRequest, SellerOrderStatusRequest, SellerShipmentStatusRequest, StoreController, StoreStatusResponse

### Community 8 - "Convenciones de API Backend - Autopartco"
Cohesion: 0.04
Nodes (47): 10. Ordenamiento, 11.1 Fecha y hora, 11.2 Fecha sin hora, 11. Formato de fechas, 12. Identificadores, 13. Validación de DTO, 14. Eliminación lógica, 15. Autenticación (+39 more)

### Community 9 - "SellerPlanEntitlementService"
Cohesion: 0.13
Nodes (10): SalesSummary, SellerDashboardResponse, Usage, Access, Capacity, BRANCHES, PRODUCTS, USERS (+2 more)

### Community 10 - ".findByEmailIgnoreCase"
Cohesion: 0.12
Nodes (11): LoginRequest, LoginResponse, AccountSessionRepository, AccountSessionService, AuthTokenService, TokenPair, LoginService, SessionRefreshService (+3 more)

### Community 11 - "ProductImageValidator"
Cohesion: 0.13
Nodes (4): PayloadTooLargeException, ProductImageValidator, ValidatedImage, ProductImageValidatorTest

### Community 12 - "ConflictException"
Cohesion: 0.17
Nodes (7): ConflictException, SellerPlanController, SellerPlanRequest, SellerPlanResponse, SellerPlan, SellerPlanRepository, SellerPlanService

### Community 13 - "AccountRepository"
Cohesion: 0.14
Nodes (6): AccountRepository, BadRequestException, AccountSessionServiceTest, PersonAddressServiceTest, SellerRegistrationServiceTest, StoreAccessServiceTest

### Community 14 - "BranchService.java"
Cohesion: 0.18
Nodes (7): BranchCreateRequest, BranchLocationRequest, BranchLocationResponse, BranchResponse, BranchUpdateRequest, BranchRepository, BranchService

### Community 15 - "MasterDataService"
Cohesion: 0.18
Nodes (6): MasterDataController, CategoryRequest, CategoryResponse, ManufacturerRequest, ManufacturerResponse, MasterDataService

### Community 16 - "LoginAttemptService.java"
Cohesion: 0.25
Nodes (4): LoginAttemptMapper, LoginAttemptRepository, LoginAttemptService, LoginAttemptServiceTest

### Community 17 - "org.springframework.transaction.annotation.Transactional"
Cohesion: 0.14
Nodes (3): Document, SellerSetupResponse, SellerApplicationService

### Community 18 - "ApiError"
Cohesion: 0.19
Nodes (4): ApiError, ErrorResponse, GlobalExceptionHandler, GlobalExceptionHandlerTest

### Community 19 - "org.junit.jupiter.api.Test"
Cohesion: 0.09
Nodes (7): SupabaseStorageProperties, WompiProperties, RefreshTokenHashTest, BackendApplicationTests, SellerDashboardServiceTest, SellerFileServiceTest, SellerPaymentServiceTest

### Community 20 - "PersonAddressService"
Cohesion: 0.18
Nodes (6): AddressLocationRequest, PersonAddressRequest, PersonAddressResponse, PersonAddressRepository, PersonAddressService, LocationRepository

### Community 21 - "org.springframework.stereotype.Service"
Cohesion: 0.25
Nodes (5): VerifyEmailResponse, AuthResponseMapper, StateRepository, EmailVerificationService, NotFoundException

### Community 22 - "AuthController.java"
Cohesion: 0.22
Nodes (3): ApiConfig, SellerPaymentController, SecurityTestController

### Community 23 - "AccountToken"
Cohesion: 0.23
Nodes (5): AccountToken, TokenMapper, AccountTokenRepository, CreatedEmailVerificationToken, EmailVerificationTokenService

### Community 24 - "WebSecurityConfig.java"
Cohesion: 0.10
Nodes (3): CORS, WebSecurityConfig, CORS

### Community 25 - "org.springframework.http.ResponseEntity"
Cohesion: 0.11
Nodes (5): AuthController, LogoutRequest, RefreshTokenRequest, PromotionController, SellerShipmentCreateRequest

### Community 26 - "RegistrationService"
Cohesion: 0.31
Nodes (5): RegisterRequest, RegisterResponse, AccountMapper, PreparedRegistration, RegistrationService

### Community 27 - "AppProperties"
Cohesion: 0.15
Nodes (7): UnlockAccountRequest, UnlockAccountResponse, AccountUnlockService, AppProperties, EmailVerification, PasswordRecovery, Tokens

### Community 29 - "SellerRegistrationService.java"
Cohesion: 0.07
Nodes (25): SellerRegistrationRequest, SellerRegistrationResponse, CreatedApplication, SellerRegistrationService, SellerApplicationCreatedResponse, SellerApplicationRequest, SellerApplicationStatus, ACTIVE (+17 more)

### Community 30 - "org.springframework.context.annotation.Bean"
Cohesion: 0.15
Nodes (3): CorsConfig, JacksonConfig, SupabaseStorageConfig

### Community 31 - "Convenciones de API Backend - Autopartco"
Cohesion: 0.04
Nodes (47): 10. Ordenamiento, 11.1 Fecha y hora, 11.2 Fecha sin hora, 11. Formato de fechas, 12. Identificadores, 13. Validación de DTO, 14. Eliminación lógica, 15. Autenticación (+39 more)

### Community 32 - "org.springframework.stereotype.Component"
Cohesion: 0.25
Nodes (3): SessionMapper, ForbiddenHandler, UnauthorizedEntryPoint

### Community 33 - "ProfileService"
Cohesion: 0.29
Nodes (3): ProfileResponse, ProfileService, ProfileServiceTest

### Community 34 - "SellerPaymentService"
Cohesion: 0.25
Nodes (3): SellerCheckoutRequest, SellerCheckoutResponse, SellerPaymentService

### Community 36 - "SellerApplicationController"
Cohesion: 0.27
Nodes (3): SellerApplicationController, SellerApplicationResponse, SellerApplicationReviewRequest

### Community 37 - "ProductOptionsResponse"
Cohesion: 0.24
Nodes (4): CategoryOption, CompatibilityOption, ManufacturerOption, ProductOptionsResponse

### Community 38 - "PasswordRecoveryService.java"
Cohesion: 0.39
Nodes (3): PasswordRecoveryRequest, PasswordRecoveryResponse, PasswordResetRequest

### Community 39 - "AUTOPARTCO_PREDEVELOPMENT_RUNBOOK.md"
Cohesion: 0.04
Nodes (45): Autopartco — Runbook de preparación antes del desarrollo, Benchmark de modelos, Capacidades para evaluar más adelante, Fase 0 — Inventario, respaldo y punto de restauración, Fase 10 — Automatización mínima, Fase 11 — Lista de autorización para empezar desarrollo funcional, Fase 1 — Escoger y estabilizar el entorno, Fase 2 — Preparar el uso de todas las IAs (+37 more)

### Community 41 - "AUTOPARTCO — Definición integral del producto y plan de desarrollo"
Cohesion: 0.10
Nodes (19): 18. Definición de MVP comercial, 19. Criterios globales de aceptación, 21. Gobernanza del desarrollo con agentes, 22. Estado conocido del repositorio y límites de la auditoría, 23.1 Decisiones recomendadas por defecto para cerrar el MVP, 23. Decisiones por cerrar antes de comprometer el alcance comercial, 24. Recursos de referencia, 3. Alcance general del producto (+11 more)

### Community 43 - "Seguridad del Backend"
Cohesion: 0.11
Nodes (17): Bloqueo por intentos fallidos, Configuracion de correo, Direcciones propias, Endpoints publicos actuales, Operaciones de almacenes, Passwords, Perfil propio, Permisos por almacen (+9 more)

### Community 46 - "Manejo centralizado de errores de API"
Cohesion: 0.12
Nodes (16): 10. Información sensible, 11. Manejo centralizado, 12. Reglas generales, 13. Criterios de aceptación, 1. Objetivo, 2. Formato estándar de errores, 3. Códigos HTTP, 4. Errores de validación — 400 (+8 more)

### Community 47 - "Manejo centralizado de errores de API"
Cohesion: 0.12
Nodes (16): 10. Información sensible, 11. Manejo centralizado, 12. Reglas generales, 13. Criterios de aceptación, 1. Objetivo, 2. Formato estándar de errores, 3. Códigos HTTP, 4. Errores de validación — 400 (+8 more)

### Community 48 - "Seguridad del Backend"
Cohesion: 0.13
Nodes (14): Bloqueo por intentos fallidos, Configuracion de correo, Endpoints publicos actuales, Operaciones de almacenes, Passwords, Perfil propio, Politica general, Proposito (+6 more)

### Community 49 - "org.springframework.web.bind.annotation.GetMapping"
Cohesion: 0.10
Nodes (9): AccountSessionResponse, SellerDashboardController, SellerCollaboratorInvitationResponse, SellerCollaboratorInviteRequest, Member, SellerCollaboratorResponse, SellerNotificationResponse, SellerPermissionOption (+1 more)

### Community 53 - "Guía de trabajo con Git y GitHub"
Cohesion: 0.13
Nodes (14): 10. Después del merge, 11. Checklist de entrega, 1. Requisitos, 2. Configurar Git una sola vez, 3. Clonar el proyecto, 4. Preparar la configuración local, 5. Trabajar desde un Issue, 6. Crear y actualizar una rama (+6 more)

### Community 54 - "PromotionTargetType"
Cohesion: 0.15
Nodes (10): PromotionDiscountType, FIXED_AMOUNT, PERCENTAGE, PromotionTargetType, ALL, ATTRIBUTE, CATEGORY, MANUFACTURER (+2 more)

### Community 55 - "PromotionPricingService"
Cohesion: 0.30
Nodes (3): PriceQuote, PromotionPricingService, PromotionPricingServiceTest

### Community 56 - "Autopartco — Contexto base para agentes de IA"
Cohesion: 0.15
Nodes (12): Autopartco — Contexto base para agentes de IA, Definición de terminado, Diseño/UX, Flujo recomendado para cada tarea, Plantilla de tarea, Principios de arquitectura, Prioridades del producto, Qué es el producto (+4 more)

### Community 57 - "15. Skills y herramientas para agentes de IA"
Cohesion: 0.15
Nodes (13): 15.1 Diseño: conjunto recomendado, 15.2 Código, planificación y depuración, 15.3 Backend, datos y calidad, 15.4 IA, evaluaciones e integraciones, 15.5 Política de selección de skills, 15.6 Orquestación de agentes, 15. Skills y herramientas para agentes de IA, A. UI/UX Pro Max — skill principal de inteligencia de diseño (+5 more)

### Community 58 - "Registro y planes para vendedores"
Cohesion: 0.15
Nodes (12): Activación y pago, Alcance solicitado, Almacenamiento de archivos, Configuración de integración requerida, Contratos disponibles en backend, Decisiones acordadas, Fases, Implementación de frontend (+4 more)

### Community 60 - "12. Modelo de datos objetivo"
Cohesion: 0.20
Nodes (9): 12.1 Identidad y estructura comercial, 12.2 Catálogo y universo automotriz, 12.3 Inventario y compras, 12.4 Comercio y pagos, 12.5 Integraciones, 12.6 Suscripciones y soporte, 12.7 IA y analítica, 12.8 Reglas de modelado (+1 more)

### Community 61 - "17. Hoja de ruta y entregables"
Cohesion: 0.20
Nodes (10): 17. Hoja de ruta y entregables, Fase 0 — Descubrimiento y línea base, Fase 1 — Fundación y UX del producto, Fase 2 — Portal comercial, catálogo e inventario, Fase 3 — Integraciones Alegra y Siigo, Fase 4 — Marketplace y ciclo de pedido, Fase 5 — Pagos de demostración y monetización, Fase 6 — Operaciones y soporte (+2 more)

### Community 62 - "Registro y verificacion de correo"
Cohesion: 0.22
Nodes (8): Auditoria, Endpoint de registro, Endpoint de verificacion, Flujo, Registro y verificacion de correo, Tokens de cuenta, Variables de entorno, Índice

### Community 63 - "5. Marketplace para compradores"
Cohesion: 0.22
Nodes (9): 5.1 Inicio y descubrimiento, 5.2 Búsqueda, 5.3 Resultados vacíos y errores, 5.4 Página de producto, 5.5 Tienda y sucursal, 5.6 Carrito, pedidos y seguimiento, 5.7 Cuentas, vehículos y fidelización, 5.8 Confianza, reseñas y reportes (+1 more)

### Community 64 - "Registro y verificacion de correo"
Cohesion: 0.22
Nodes (8): Auditoria, Endpoint de registro, Endpoint de verificacion, Flujo, Registro y verificacion de correo, Tokens de cuenta, Variables de entorno, Índice

### Community 65 - "PromotionScope"
Cohesion: 0.32
Nodes (4): PromotionScope, PLATFORM, STORE, PromotionRepository

### Community 66 - "Autopartco — Backlog de preparación (antes del desarrollo funcional)"
Cohesion: 0.25
Nodes (7): Autopartco — Backlog de preparación (antes del desarrollo funcional), Criterio de aceptación compartido, Definición del estado, Gates para iniciar desarrollo funcional, Orden de ejecución, Qué no hacer durante esta fase, Reglas de asignación

### Community 67 - "6. Portal comercial para almacenes"
Cohesion: 0.25
Nodes (8): 6.1 Registro y verificación, 6.2 Panel inicial, 6.3 Catálogo y publicación, 6.4 Inventario por sucursal, 6.5 Pedidos, ventas y promociones, 6.6 Colaboradores, 6.7 Analítica comercial, 6. Portal comercial para almacenes

### Community 69 - "10. Funciones de inteligencia artificial"
Cohesion: 0.29
Nodes (7): 10.1 Búsqueda semántica de repuestos, 10.2 Copiloto de diagnóstico orientativo, 10.3 Compatibilidad y equivalencias, 10.4 Inteligencia de inventario, 10.5 Herramientas internas para vendedores, 10.6 Arquitectura de IA, 10. Funciones de inteligencia artificial

### Community 70 - "14. Experiencia de usuario y diseño visual"
Cohesion: 0.29
Nodes (7): 14.1 Principios de diseño, 14.2 Identidad visual, 14.3 Biblioteca y componentes, 14.4 Accesibilidad y responsive, 14.5 Formas y datos, 14.6 Investigación y pruebas de UX, 14. Experiencia de usuario y diseño visual

### Community 71 - "7. Integraciones con Alegra y Siigo"
Cohesion: 0.29
Nodes (7): 7.1 Objetivo de integración, 7.2 Funciones mínimas, 7.3 Datos a integrar por prioridad, 7.4 Reglas de sincronización, 7.5 Diseño técnico, 7.6 Criterios de aceptación, 7. Integraciones con Alegra y Siigo

### Community 72 - "13. Arquitectura técnica"
Cohesion: 0.33
Nodes (6): 13.1 Frontend existente como base, 13.2 Backend existente como base, 13.3 Módulos lógicos propuestos, 13.4 APIs y contratos, 13.5 Configuración y despliegue, 13. Arquitectura técnica

### Community 73 - "16. Calidad, seguridad y operación"
Cohesion: 0.33
Nodes (6): 16.1 Pruebas, 16.2 Seguridad, 16.3 Observabilidad, 16.4 Respaldo y recuperación, 16.5 Rendimiento, 16. Calidad, seguridad y operación

### Community 74 - "2. Problema, mercado y propuesta de valor"
Cohesion: 0.40
Nodes (5): 2.1 Problema que resuelve, 2.2 Clientes y usuarios principales, 2.3 Propuesta de valor, 2.4 Principios de producto, 2. Problema, mercado y propuesta de valor

### Community 75 - "9. Pagos de demostración y Wompi sandbox"
Cohesion: 0.40
Nodes (5): 9.1 Capas, 9.2 Flujo esperado, 9.3 Controles obligatorios, 9.4 Fuera de alcance inicial, 9. Pagos de demostración y Wompi sandbox

### Community 76 - "Imágenes de productos"
Cohesion: 0.40
Nodes (4): Configuración, Endpoints, Imágenes de productos, Inicialización y prueba

### Community 77 - "PromotionAttributeOperator"
Cohesion: 0.50
Nodes (3): PromotionAttributeOperator, CONTAINS, EQUALS

### Community 78 - "11. Analítica y predicción: qué medir antes de entrenar"
Cohesion: 0.50
Nodes (4): 11.1 Eventos y métricas de producto, 11.2 Indicadores, 11.3 Evaluación de modelos, 11. Analítica y predicción: qué medir antes de entrenar

### Community 79 - "20. Métricas de éxito del producto"
Cohesion: 0.50
Nodes (4): 20. Métricas de éxito del producto, Para almacenes, Para Autopartco, Para compradores

### Community 80 - "4. Tipos de cuenta, organizaciones y permisos"
Cohesion: 0.50
Nodes (4): 4.1 Roles propuestos, 4.2 Organización y sucursales, 4.3 Seguridad de cuentas, 4. Tipos de cuenta, organizaciones y permisos

### Community 81 - "1. Definición ejecutiva"
Cohesion: 0.67
Nodes (3): 1. Definición ejecutiva, Principio rector, Qué no es Autopartco

## Knowledge Gaps
- **339 isolated node(s):** `com.autopartco:backend`, `com.autopartco:backend`, `EQUALS`, `CONTAINS`, `PERCENTAGE` (+334 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 466 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **11 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `AUTOPARTCO — Definición integral del producto y plan de desarrollo` connect `AUTOPARTCO — Definición integral del producto y plan de desarrollo` to `6. Portal comercial para almacenes`, `10. Funciones de inteligencia artificial`, `14. Experiencia de usuario y diseño visual`, `7. Integraciones con Alegra y Siigo`, `13. Arquitectura técnica`, `16. Calidad, seguridad y operación`, `2. Problema, mercado y propuesta de valor`, `9. Pagos de demostración y Wompi sandbox`, `11. Analítica y predicción: qué medir antes de entrenar`, `20. Métricas de éxito del producto`, `4. Tipos de cuenta, organizaciones y permisos`, `1. Definición ejecutiva`, `15. Skills y herramientas para agentes de IA`, `12. Modelo de datos objetivo`, `17. Hoja de ruta y entregables`, `5. Marketplace para compradores`?**
  _High betweenness centrality (0.125) - this node is a cross-community bridge._
- **Are the 2 inferred relationships involving `Account` (e.g. with `.persistsSessionWithPreassignedJwtSessionIdAsNewEntity()` and `.makesFirstAddressDefaultAndAssociatesItWithAuthenticatedPerson()`) actually correct?**
  _`Account` has 2 INFERRED edges - model-reasoned connections that need verification._
- **What connects `com.autopartco:backend`, `com.autopartco:backend`, `EQUALS` to the rest of the system?**
  _339 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `lombok.AllArgsConstructor` be split into smaller, more focused modules?**
  _Cohesion score 0.11936839055483123 - nodes in this community are weakly interconnected._
- **Why does `12. Modelo de datos objetivo` connect `12. Modelo de datos objetivo` to `AUTOPARTCO — Definición integral del producto y plan de desarrollo`?**
  _High betweenness centrality (0.083) - this node is a cross-community bridge._
- **Should `uuid` be split into smaller, more focused modules?**
  _Cohesion score 0.052564102564102565 - nodes in this community are weakly interconnected._
- **Should `ProductService` be split into smaller, more focused modules?**
  _Cohesion score 0.057967313585291114 - nodes in this community are weakly interconnected._