# Plan: refactorización modular del backend

> Ejecuta el diseño aprobado [2026-10-13-backend-modular-architecture-design.md](2026-10-13-backend-modular-architecture-design.md). El diseño es la fuente de verdad: este plan solo ordena el trabajo, asigna quién lo hace y fija las comprobaciones. Las secciones se citan como §N.

## Reglas para todas las fases

- Rama `refactor/modular-backend`, creada desde `main`. Un commit por fase (3a/3b y 4a/4b van por separado). El push y el PR final necesitan autorización del usuario.
- Fuera de alcance: cambios de esquema SQL, de JSON de respuesta y de códigos de error (§10), y funcionalidades nuevas.
- Cada fase se cierra con estos pasos, desde `backend/` en Git Bash:
  1. `./mvnw -q -DskipTests compile test-compile`
  2. `./mvnw -q -Dtest=<SUITE>,ModularityTests test`. `SUITE` es la lista explícita de §7, que se actualiza cuando se renombra una clase de prueba. Nunca se usan comodines ni se incluye `BackendApplicationTests`.
  3. `graphify update .` desde la raíz.
  4. Revisar `git diff --stat`: no puede haber archivos fuera de `backend/` salvo `docs/` en la fase 8.
- Ejecutor:
  - **OpenCode `backend`**: movimientos y divisiones mecánicas. Se lanza en segundo plano y luego reviso el diff y ejecuto las pruebas.
  - **Claude `backend`**: lo que toca seguridad, JWT, autenticación, pagos o Wompi.
- Confirmación del usuario (hook): cualquier edición en `pom.xml`, `src/main/resources/`, `config/`, `shared/config/` y `shared/security/`. Lo ya autorizado está en §14.

## Resumen

| Fase | Objetivo | Ejecutor | Riesgo | Confirmaciones |
|---|---|---|---|---|
| 0 | Base: Modulith, `shared`, paquetes vacíos | Claude `backend` | Medio (mueve seguridad/JWT) | `pom.xml`, `shared/security`, `shared/config` |
| 1 | `core.audit` + `core.notification` | OpenCode | Bajo | — |
| 2 | `core.identity` (`/auth`, `/me`) | Claude `backend` | Alto (autenticación) | — |
| 3a | `core.organization` + `storesuite.store/branches` + `marketplace.stores` | OpenCode | Medio | — |
| 3b | `core.billing` + Wompi + `storesuite.subscription` + `operations.plans` | Claude `backend` | Alto (pagos/webhook) | Arranque de humo |
| 4a | `core.vehicle/inventory/pricing/catalog` | OpenCode | Medio (JPQL → SQL nativo) | — |
| 4b | Endpoints de catálogo en los productos | OpenCode | Bajo | Arranque de humo |
| 5 | `core.order` + `storesuite.orders/dashboard` | OpenCode | Medio (SQL directo) | — |
| 6 | `storesuite.onboarding/staff` + `operations.storeverification`; borrar paquetes legados | OpenCode | Medio | — |
| 7 | Legibilidad (§9) | OpenCode | Bajo | — |
| 8 | `WebSecurityConfig`, marcadores, `verify()` completo, docs | Claude `backend` + `qa-seguridad` | Alto | `shared/security`, arranque de humo |

## Fases

### Fase 0: base
1. `git switch -c refactor/modular-backend`.
2. En `pom.xml`, importar el BOM de Spring Modulith 2.1.1 y añadir `spring-modulith-api` (compile) y `spring-modulith-starter-test` (test). Según §4.3, sin el runtime ni el registro de eventos.
3. Crear `src/test/.../ModularityTests` con `printsModules()` y `springBeanAndEntityNamesAreUnique()`, y la estrategia de detección con su `spring.factories` (§4.3). Antes, cerrar las dudas de §13 sobre la API de 2.1.1; si no funciona, aplicar el plan B y anotarlo en §13.
4. Mover `common`, `exception`, `storage` y `config` a `shared.*` según la tabla de §5.1. `WompiProperties` sale de `config`.
5. Eliminar `test/SecurityTestController`, `test/PrivateEndpointResponse` y la entrada `/test/public` de `PUBLIC_ENDPOINTS`. Mover `GeneratedKeyPair` a `src/test`.
6. Añadir `shared/package-info.java` con `@ApplicationModule(type = OPEN)`. Crear `core/*`, `marketplace`, `storesuite`, `intelligence` y `operations`, cada uno con su `package-info.java`.
- **Hecho cuando**: la SUITE pasa y `printsModules` coincide con §4.
- **Commit**: `refactor(backend): base modular con Spring Modulith y paquete shared`.

### Fase 1: audit y notification
Mapeo según §5.2 (audit) y §5.4 (notificaciones de `SellerDashboardService`). Crear `NotificationController` en `/api/v1/me/notifications` (§6).
- **Hecho cuando**: la SUITE pasa y `audit` y `notification` no tienen violaciones.
- **Commit**: `refactor(backend): extraer core.audit y core.notification`.

### Fase 2: identity
Mapeo según §5.2. Fachadas de §4.6. `AuthController` + `MeController` con las rutas de §6. `SellerRegistrationService` pasa temporalmente a `seller.service`. **No se cambian los matchers de seguridad** (§7).
- **Pruebas**: las de `auth` renombradas a su paquete nuevo, más la SUITE actualizada.
- **Commit**: `refactor(backend): mover auth a core.identity con rutas /auth y /me`.

### Fase 3a: organization
Mapeo según §5.5 y la parte de solicitudes de almacén de §5.4. Relaciones por ID (§4.4, R1). `StoreApplication` mapea todavía todas las columnas.
- **Pruebas**: `StoreAccessTest`, `StoreFileStorageTest` y la SUITE.
- **Commit**: `refactor(backend): extraer core.organization y endpoints de tienda`.

### Fase 3b: billing
Separar `StoreSubscription` y `StoreApplication` sobre la misma tabla (§4.4). Mover planes, pagos y Wompi a `core.billing`. El webhook pasa a `POST /api/v1/webhooks/wompi`, sin alias. Crear `storesuite.subscription` y `operations.plans`.
- **Pruebas**: las de pagos y planes, más la SUITE. Después, un arranque de humo: `./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.jpa.hibernate.ddl-auto=validate`.
- **Commit**: `refactor(backend): extraer core.billing y webhook de Wompi`.

### Fase 4a: vehicle, inventory, pricing y catalog
Dividir `product` según §5.3 con relaciones por ID. Antes de reescribir en SQL nativo dentro de `ListingQueries`, leer el JPQL de `findAllInStore`, `findPublished` y `findPublishedByProductId` (§13). Crear `PriceCalculator` y la SPI `PromotionTargets`.
- **Pruebas**: `PriceCalculatorTest`, las de imágenes y la SUITE.
- **Commit**: `refactor(backend): dividir product en catalog, inventory, pricing y vehicle`.

### Fase 4b: endpoints de catálogo
Crear `storesuite.products`, `storesuite.promotions`, `marketplace.catalog`, `operations.catalogadmin` y `operations.promotions` con las rutas de §6.
- **Pruebas**: la SUITE y un arranque de humo con `validate`. Revisar en el log que no aparezcan FK nuevas en `products_compatibilies` (§13).
- **Commit**: `refactor(backend): exponer catálogo por producto (marketplace, storesuite, operations)`.

### Fase 5: order
Extraer `SaleQueries`, `SaleStatusService` y `ShipmentService` de `SellerDashboardService` (§5.4). Crear `storesuite.orders` y `storesuite.dashboard`.
- **Pruebas**: las de transiciones de `SellerDashboardServiceTest`, movidas, y la SUITE.
- **Commit**: `refactor(backend): extraer core.order y panel de storesuite`.

### Fase 6: resto de storesuite y operations
Crear `storesuite.onboarding`, `storesuite.staff` (7 casos de uso, §5.4) y `operations.storeverification`. Borrar los paquetes `auth`, `seller`, `store` y `product`.
- **Pruebas**: las pruebas divididas y la SUITE. `grep -r "backend\.(auth|seller|store|product)\." src` no debe devolver nada.
- **Commit**: `refactor(backend): casos de uso de storesuite y operations; eliminar paquetes legados`.

### Fase 7: legibilidad
Renombrar las consultas de §9.2, aplicar las reglas de §9.1, quitar los FQCN en línea y crear `StateCodes` y `TokenHashing`.
- **Pruebas**: la SUITE.
- **Commit**: `refactor(backend): nombres de consultas y legibilidad`.

### Fase 8: cierre
1. Reescribir `WebSecurityConfig` según §6: lista explícita de rutas públicas, `/api/v1/admin/**` solo para root y el resto autenticado.
2. Crear los `package-info.java` de los marcadores (§8) y definir `allowedDependencies` en todos los módulos.
3. Ejecutar `ModularityTests.verify()` completo, sin filtros.
4. Actualizar `docs/backend/*.md` con las rutas nuevas (lo redacta OpenCode).
- **Pruebas**: la SUITE, `verify()` en verde, un arranque de humo con `validate` y una revisión de `qa-seguridad` sobre el diff completo.
- **Commit**: `refactor(backend): seguridad consolidada y verificación modular completa`.
- Luego, con autorización del usuario: push y un PR único hacia `main`.
