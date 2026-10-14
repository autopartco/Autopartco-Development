# Plan: preparar el backend para las migraciones pendientes (`pending-approval/`)

Fecha: 2026-10-14. Estado: **propuesto, sin implementar**.

## 1. Objetivo y alcance

Dejar el backend compatible con las 8 migraciones de `database/migrations/pending-approval/` que aún no están aplicadas en la BD local, para poder aplicarlas sin romper nada. Ya aplicadas: `states_per_domain`, `soft_delete_partial_uniques`, `retention_functions`.

Entra: cambios en `backend/src/main/java/` y `backend/src/test/`, configuración JPA, y mover/renumerar las migraciones aprobadas.
No entra: jobs de retención (`RetentionJob`), `StockLedger` completo (diseño §12), cambios en `frontend/`.

## 2. Estado actual verificado

| Migración | Prerrequisito en backend | Estado |
|---|---|---|
| `PA_V…131000 rename_typos` | `Product.java:85` (`products_compatibilies`), `Store.java:40,46` (`bussnies_*`), `ListingQueries.java:101,122` | Pendiente |
| `PA_V…131010 sku_unique_per_store` | `Product.java:56` tiene `@Column(unique = true)` en `sku` | Pendiente |
| `PA_V…131030 price_positive` | `ProductCreateRequest:21` y `ProductUpdateRequest:22` usan `@DecimalMin("0.0")` (permite 0) | Pendiente |
| `PA_V…140945 movements_constraints` | Ninguna entidad ni SQL escribe `inventory_movements`. La escritura la hace el trigger puente `V…141025` (IN/OUT con balance correcto) | **Aplicable ya**, tras el diagnóstico propio de la migración |
| `PA_V…141500 not_null` | `reason_code`: lo escribe el trigger puente. `order_details.product_id`: ningún Java inserta en `order_details`; `SaleQueries` solo lee | **Aplicable ya** si la BD no tiene nulos; verificar el checkout (T6) |
| `PA_V…141110 timestamptz` | 135 usos de `LocalDateTime` en ~45 archivos; sin `hibernate.jdbc.time_zone` | Pendiente (grande) |
| `PA_V…141510 database_roles` | Requiere `ddl-auto: validate` antes de usar `autopartco_app`; necesita `allow_destructive=on` que debe fijar el usuario | Lo ejecuta el usuario |
| `ddl-auto` | `application.yaml:30` y `application-dev.yaml:4` están en `update`. `rename_typos` exige `validate`, o Hibernate recrea las tablas y columnas viejas | Pendiente |

## 3. Decisiones clave

1. **`ddl-auto: validate`** en todos los perfiles (D1). Descartado: mantener `update`, que recrea los nombres viejos tras `rename_typos`.
2. **Zona horaria (D2) — DECIDIDO, Opción A** (ADR `docs/adr/0002-zona-horaria-y-timestamptz.md`, aceptado 2026-10-14). Mantener `LocalDateTime` y fijar la zona de la JVM en `America/Bogota` con `TimeZone.setDefault` en `BackendApplication.main`. **No** se fija `hibernate.jdbc.time_zone` (no tiene efecto sobre `timestamptz`). `validate` acepta `LocalDateTime` sobre `timestamptz` (medido en el spike T2). A3 se ejecuta con `autopartco.legacy_source_tz = 'America/Bogota'`. Descartadas: B (`Instant`/`OffsetDateTime`, cambia el JSON) y A' (JVM en UTC, el frontend mostraría +5 h).
3. **SKU (D3).** Se quita `unique = true` y se añade validación por almacén en el servicio, porque el índice por almacén lo crea la migración.
4. **Precio (D4).** `@DecimalMin(value = "0.01")` en crear y actualizar productos.
5. **Movimientos de inventario.** No se escribe desde Java; el puente por triggers sigue siendo el mecanismo hasta que exista `StockLedger`.

## 4. Contratos

- `Product`: `@CollectionTable(name = "product_compatibilities", joinColumns = @JoinColumn(name = "product_id"))`.
- `Store`: `@Column(name = "business_name", ...)` y `@Column(name = "business_email", ...)`. Los getters y setters Java no cambian de nombre.
- SQL nativo: `s.business_name` en `ListingQueries` (líneas 101 y 122).
- `application*.yaml`: `spring.jpa.hibernate.ddl-auto: validate`, sin `hibernate.jdbc.time_zone` (ADR 0002).
- Formato de fecha en la API: sin cambios (Opción A).

## 5. Tareas (en este orden: database → backend → qa-seguridad)

### T1. Diagnóstico de la BD local antes de aplicar
- Modelo: Haiku. Agente: `database` (solo lectura, el usuario autoriza).
- Archivos: ninguno.
- Instrucción: contra la BD local, ejecutar las precondiciones de `PA_V…140945` (tipo y balance de `inventory_movements`) y `PA_V…141500` (nulos en `inventory_movements.reason_code` y `order_details.product_id`). Informar los conteos.
- Aceptación: conteos 0 en las cuatro consultas, o lista de filas culpables.

### T2. Spike: `LocalDateTime` sobre `timestamptz` con `validate`
- Modelo: Opus. Agente: `backend`.
- Archivos: `backend/src/test/` (prueba aislada), sin tocar la BD de `backend/.env`.
- Contexto mínimo: Hibernate 7 con Spring Boot 4.1.1; `BaseEntity` en `shared/persistence`; `PA_V…141110` convierte todo `TIMESTAMP` de `public` a `TIMESTAMPTZ`.
- Instrucción: en una BD desechable (Testcontainers o una BD temporal que cree el usuario), comprobar si una entidad con `LocalDateTime` y `hibernate.jdbc.time_zone=UTC` valida y guarda correctamente contra una columna `timestamptz`. Decidir entre Opción A y B (D2) y documentarlo en `docs/adr/`.
- Aceptación: ADR con la decisión y una prueba que demuestra lectura y escritura sin desfase de 5 horas (America/Bogota).

### T3. Renombres, SKU y precio
- Modelo: Sonnet. Agente: `backend`.
- Archivos: `core/catalog/internal/Product.java`, `core/organization/internal/Store.java`, `core/catalog/ListingQueries.java`, `ProductCreateRequest.java`, `ProductUpdateRequest.java`, servicio de productos (validación de SKU por almacén), pruebas.
- Instrucción: aplicar los contratos de la sección 4 y las decisiones D3 y D4. Buscar con `rg -n "bussnies_|products_compatibilies" backend/src` hasta que no queden coincidencias.
- Aceptación: `rg` sin resultados; pruebas unitarias nuevas: precio 0 rechazado, SKU repetido en el mismo almacén rechazado y en otro almacén aceptado. Ejecutar solo con `-Dtest=<Clase>` explícito.

### T4. Configuración JPA y zona horaria
- Modelo: Sonnet. Agente: `backend`. Depende de T2.
- Archivos: `BackendApplication.java` (agente `backend`); `application.yaml` y `application-dev.yaml` los edita el usuario.
- Instrucción: añadir `TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"))` en `BackendApplication.main` antes de `SpringApplication.run`, con una prueba que lo verifique. El usuario pone `ddl-auto: validate` en los yaml, **sin** `hibernate.jdbc.time_zone`. No se migran tipos (D2 = A, ADR 0002).
- Aceptación: el backend arranca contra una BD con todas las migraciones aplicadas y `validate` no reporta diferencias; el JSON de una respuesta con fecha no cambia.

### T5. Aplicar las migraciones pendientes
- Modelo: Haiku. Agente: `database`. Requiere autorización del usuario y respaldo previo.
- Archivos: `database/migrations/` (quitar `PA_`, nuevo timestamp posterior a `202610141330`, `.sql.conf` donde haga falta).
- Instrucción: mover y renumerar las 8 migraciones y sus `U`, en este orden: `movements_constraints`, `not_null`, `price_positive`, `sku_unique_per_store`, `rename_typos`, `timestamptz` (con la app detenida). `database_roles` la ejecuta el usuario con `allow_destructive=on`.
- Aceptación: consultas de §7.1 del README en 0 filas; el backend arranca con `validate`.

### T6. Verificar el checkout contra `order_details.product_id`
- Modelo: Sonnet. Agente: `backend`.
- Archivos: `marketplace/checkout/` (rama `feature/checkout-backend`).
- Instrucción: confirmar que cualquier inserción de líneas de pedido envía `product_id`.
- Aceptación: prueba que falla si falta `product_id`.

### T7. Revisión
- Modelo: Opus. Agente: `qa-seguridad`.
- Instrucción: revisar el diff, ejecutar las pruebas con `-Dtest=<Clase>` explícito y comprobar que ningún endpoint cambió su contrato.
- Aceptación: informe sin hallazgos críticos.

## 6. Riesgos

- `validate` puede fallar por diferencias previas entre entidades y BD que hoy `update` oculta. T4 las revela; se corrigen caso a caso.
- `timestamptz` reescribe las tablas: ejecutarla con la app detenida y con respaldo.
- `BackendApplicationTests` modifica la BD de `backend/.env`; no ejecutarla.
- El árbol de trabajo tiene cambios sin commitear del checkout en `feature/checkout-backend`. Crear esta rama desde `develop` (`chore/backend-pending-migrations`) o commitear primero.
