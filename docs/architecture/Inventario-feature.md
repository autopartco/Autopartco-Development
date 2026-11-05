# Inventario-feature — Inventario por sucursal, transferencias y gestión de productos (portal del almacén)

Estado: plan aprobado por el usuario el 2026-11-05. El frontend (T6–T7) se implementa en esta rama porque el usuario lo pidió. Fuente de verdad del esquema y BD de trabajo: Supabase `autopartco-dev` (`hwojkaguqmegvdxlcysd`); no se usa la BD local. **No se guardan archivos SQL en el repo**: todo cambio de esquema o datos se aplica directamente en Supabase (`apply_migration`) y queda registrado en su historial de migraciones. Tras el PR se publica en producción (autorizado por el usuario el 2026-11-05). Seguimiento: `docs/architecture/HANDOVER-inventario.md`.

Producto terminado: Wompi sandbox es el cobro de producción; ninguna pantalla ni texto menciona demo, prueba técnica o sandbox.

Rama: `feature/inventario-sucursales` desde `origin/develop` (e85ec23), en el worktree `C:\Development\Autopartco-dev-inventario`. PR a `develop`.

## 1. Objetivo y alcance

Entra:
1. Alta de producto en una o varias sucursales (stock y precio por sucursal). En la edición: añadir o quitar sucursales (los endpoints `POST …/listings` y `DELETE …/listings/{branchId}` ya existen; falta la UI).
2. Transferencias atómicas entre sucursales del mismo almacén: todo el inventario de una sucursal, varios productos con cantidades o uno solo.
3. Ledger real en `inventory_movements`: toda variación de stock que hace el portal deja un movimiento con motivo, actor y referencia.
4. Ajustes de stock con motivo (conteo físico, avería, pérdida, obsolescencia, corrección).
5. Historial de movimientos filtrable (sucursal, producto, motivo, fechas).
6. Stock mínimo por listado y alertas de stock bajo (panel y filtro).
7. Edición masiva de productos: publicar, retirar, eliminar y ajustar precio (% o valor) en una sucursal.
8. Filtros adicionales en productos: categoría, fabricante, sin imágenes, stock bajo.
9. CSV: exportar inventario e importar actualizaciones de stock, precio y stock mínimo de productos existentes (con simulación previa). Disponible en todos los planes.
10. Planes comerciales: solo `TRY` (Prueba gratuita) y `ENTERPRISE` (Empresarial). Ver §3.4 y T10.

No entra: paginación en servidor del listado de productos (riesgo §7), alta de productos nuevos por CSV (requiere categoría, fabricante y reglas de vehículo), órdenes de compra, transferencias "en tránsito" con recepción en dos pasos, cambios en el checkout.

## 2. Componentes

| Capa | Componente | Responsabilidad |
|---|---|---|
| BD | Migración `inventory_operations` aplicada en Supabase (sin archivo local) | `stock_transfers`, `stock_transfer_items`, trigger de consistencia, permisos nuevos, RLS y GRANT a `autopartco_app` |
| core.inventory | `StockLedger` (nuevo) | Único punto que cambia `inventory.stock` desde el portal: bloquea filas, actualiza y escribe movimientos. Fija `set_config('autopartco.capture_managed_by_app','on',true)` |
| core.inventory | `StockTransfers` (nuevo) | Transferencia atómica sobre `StockLedger` |
| core.inventory | `StockService` (cambia) | `createListing`/`updateListing`/`addListing` pasan por `StockLedger` (INITIAL_LOAD / MANUAL_CORRECTION). Checkout y `returnStock` no cambian |
| storesuite.inventory | `TransferStock`, `AdjustStock`, `ListMovements`, `ListTransfers`, `LowStock`, `ExportInventoryCsv`, `ImportInventoryCsv`, `InventoryController` | Casos de uso con permiso por almacén |
| storesuite.products | `CreateProduct` (cambia), `BulkProductAction` (nuevo) | Alta multi-sucursal y acciones masivas |
| frontend portal | servicios http + mock, `product-form-page`, `products-page`, `inventory-page` y páginas nuevas `inventory-transfers-page`, `inventory-movements-page` | UI |

## 3. Contratos

### 3.1 BD (aplicada directamente en Supabase)

Supabase (verificado 2026-11-05) ya tiene aplicadas las migraciones versionadas: `inventory_movements` con `reason_code` NOT NULL, `source`, `actor_account_id`, `reference_type/id` (incluye `TRANSFER_ITEM`, `IMPORT_JOB`), `occurred_at`, `idempotency_key`, `notes` y columnas BaseEntity con DEFAULT; catálogo `inventory_movement_reasons`; triggers puente `trg_inventory__capture_movement_*` (respetan `autopartco.capture_managed_by_app`) y `trg_inventory__same_store_*`; `inventory_reorder_policies` (política vigente única por `inventory_id` con `valid_to IS NULL`); RLS en todas las tablas; roles `autopartco_*`. La migración **no** toca nada de eso.

- Umbral de stock bajo: `inventory_reorder_policies.reorder_point` de la política vigente (`source='MANUAL'`, `safety_stock=0`). Sin política = 3. Cambiarlo cierra la vigente (`valid_to = now()`) e inserta otra en la misma transacción.
- `stock_transfers`: `id, store_id FK store, from_branch_id FK branches, to_branch_id FK branches, mode VARCHAR(10) CHECK IN ('ALL','ITEMS'), total_units INT >0, item_count INT >0, notes VARCHAR(255), actor_account_id FK accounts ON DELETE SET NULL, idempotency_key VARCHAR(120), created_at TIMESTAMPTZ DEFAULT now()`; `CHECK (from_branch_id <> to_branch_id)`; `UNIQUE (store_id, idempotency_key) WHERE idempotency_key IS NOT NULL`; índice `(store_id, created_at DESC)`. Solo inserción (`forbid_update_delete()`, salvo `actor_account_id:null`).
- `stock_transfer_items`: `id, transfer_id FK ON DELETE RESTRICT, product_id FK products, from_inventory_id FK inventory, to_inventory_id FK inventory, quantity INT > 0`; `UNIQUE (transfer_id, product_id)`. Solo inserción.
- Trigger de consistencia: ambas sucursales y cada producto pertenecen a `store_id`; los inventarios de cada ítem corresponden a origen/destino y al producto.
- Permisos `INVENTORY_ADJUST` ("Ajustar existencias") e `INVENTORY_TRANSFER` ("Transferir existencias entre sucursales"), asignados a `OWNER` como el resto.
- `ENABLE ROW LEVEL SECURITY` en las tablas nuevas, sin políticas (igual que todas), y GRANT SELECT, INSERT a `autopartco_app` si el rol existe (como en `V202610151080`).
- Planes: actualizados directamente en Supabase en T10 (§3.4).

### 3.4 Planes comerciales (decisión del usuario 2026-11-05)

Solo dos planes en `seller_plans`:

| code | name | precio COP/mes | límites (sucursales/productos/usuarios) | capabilities | enabled | recommended |
|---|---|---|---|---|---|---|
| `TRY` | Prueba gratuita | 0 (1 mes, `Subscriptions.TRIAL_MONTHS`) | 1 / 50 / 1 | `BASIC_ANALYTICS` | true | false |
| `ENTERPRISE` | Empresarial | 299.900 | ilimitados (NULL) | las 11 actuales | true | true |

- `ENTERPRISE` se contrata y renueva desde el portal con Wompi (mismo flujo que hoy los planes pagos) **y** además ofrece «Hablar con ventas» (diálogo de cotización existente, por correo).
- `BASIC`, `BUSINESS` y `PRO` dejan de ofrecerse: se marcan `enabled=false`, `recommended=false` y, si no tienen suscripciones de pago vigentes, `deleted_at=now()`. En Supabase hoy no existen.
- CSV, stock bajo, ajustes e historial: todos los planes. Transferencias: requieren ≥2 sucursales, por tanto ENTERPRISE de hecho.
- `PlanService` (valores por defecto al crear plan): quitar el caso `BUSINESS`.
- Frontend: `PLAN_LADDER = ['TRY','ENTERPRISE']`; sin `SYNTHETIC_ENTERPRISE` ni «A medida»; tarjeta Empresarial con «Contratar» (Wompi) y «Hablar con ventas»; `minPlanCode` del catálogo de capacidades = `ENTERPRISE` salvo `BASIC_ANALYTICS`, `CSV_IMPORT` y `STOCK_ALERTS` (`TRY`, `AVAILABLE`). Mocks con los dos planes.

### 3.2 Movimientos (`StockLedger`)

```java
record StockChange(UUID inventoryId, int newStock, String reasonCode, String referenceType, UUID referenceId, String notes) {}
List<MovementRow> apply(UUID actorAccountId, String source, List<StockChange> changes); // bloquea ORDER BY branch_id, product_id FOR UPDATE
```
Un movimiento por cambio con `delta ≠ 0`: `movement_type` IN/OUT, `quantity=|delta|`, `previous_stock`, `new_stock`. `new_stock < 0` → `409 INSUFFICIENT_STOCK`.

### 3.3 Endpoints (base `ApiPaths.STORES + "/{storeId}"`; cuerpos con records propios, nunca tipos `com.fasterxml`)

| Método y ruta | Cuerpo / query | Respuesta | Permiso |
|---|---|---|---|
| `POST /inventory/transfers` | `{fromBranchId, toBranchId, mode:"ALL"\|"ITEMS", items?:[{productId, quantity}] (1..500), notes?, idempotencyKey?}` | `201 TransferResponse {id, fromBranchId, toBranchId, mode, totalUnits, items:[{productId, productName, quantity, fromStock, toStock}], createdAt}` | `INVENTORY_TRANSFER` |
| `GET /inventory/transfers` | `branchId?, page=0, size=20 (≤100)` | `Page<TransferSummary>` | `PRODUCT_READ` |
| `GET /inventory/transfers/{id}` | — | `TransferResponse` | `PRODUCT_READ` |
| `POST /inventory/adjustments` | `{branchId, productId, mode:"SET"\|"DELTA", quantity, reasonCode, notes?}` | `201 MovementResponse` | `INVENTORY_ADJUST` |
| `GET /inventory/movements` | `branchId?, productId?, reasonCode?, from?, to?, page, size ≤100` | `Page<MovementResponse {id, occurredAt, branchId, branchName, productId, productName, sku, movementType, quantity, previousStock, newStock, reasonCode, reasonLabel, actorName, referenceType, referenceId, notes}>` | `PRODUCT_READ` |
| `GET /inventory/movement-reasons` | — | `[{code, label, direction}]` solo ajustables | `PRODUCT_READ` |
| `GET /inventory/low-stock` | `branchId?` | `[{productId, productName, sku, branchId, branchName, stock, minStock}]` (`stock <= coalesce(reorder_point vigente, 3)`) | `PRODUCT_READ` |
| `PATCH /branches/{branchId}/products/{productId}/min-stock` | `{minStock: int \| null}` (null cierra la política vigente) | `204` | `PRODUCT_UPDATE` |
| `GET /inventory/export.csv` | `branchId?` | `text/csv; charset=UTF-8` con BOM, `;` como separador. Columnas: `sku;referencia;nombre;sucursal;stock;precio;stock_minimo;estado` | `PRODUCT_READ` |
| `POST /inventory/import` (multipart `file`, ≤2 MB, ≤5.000 filas) | `dryRun=true\|false` | `{rows, updated, errors:[{line, code, message}]}`; con errores y `dryRun=false` no aplica nada | `PRODUCT_UPDATE` + `INVENTORY_ADJUST` |
| `POST /products/bulk` | `{productIds (1..500), action:"PUBLISH"\|"UNPUBLISH"\|"DELETE"\|"PRICE", branchId?, priceMode?:"PERCENT"\|"AMOUNT", value?}` | `{affected, skipped:[{productId, code}]}` | según acción: `PRODUCT_PUBLISH` / `PRODUCT_DELETE` / `PRODUCT_UPDATE` |
| `POST /products` (cambia) | añade `listings?:[{branchId, quantity, price?}]` (1..max sucursales, sin repetidos); `branchId`/`quantity` pasan a opcionales; `@AssertTrue`: o `branchId+quantity` o `listings` | igual | igual |

Reglas de transferencia:
- Sucursales distintas, activas y del almacén (`BranchService.requireActiveInStore`) → si no, `400 SAME_BRANCH` / `404 BRANCH_NOT_FOUND`.
- `ALL`: todos los listados vivos con `stock > 0` de la sucursal origen; vacío → `409 TRANSFER_EMPTY`.
- `ITEMS`: productos del almacén con listado en origen (`404 LISTING_NOT_FOUND`), `quantity ≤ stock` (`409 INSUFFICIENT_STOCK`, `details:[{productId, requested, available}]`).
- Reserva: el checkout ya descuenta `inventory.stock` al crear el pedido `AWAITING_PAYMENT`, así que `stock` es lo disponible y las unidades reservadas nunca se transfieren. Si el pago vence, `returnStock` las devuelve al listado de origen (no se toca). El listado origen no se borra aunque quede en 0.
- Destino sin listado: se crea (o se reactiva) con el precio del origen; no consume cupo de `PRODUCTS`.
- Una transacción: bloquea origen y destino en orden `(branch_id, product_id)` (el mismo del checkout), escribe `stock_transfers`, ítems y dos movimientos por ítem (`TRANSFER_OUT`/`TRANSFER_IN`, `reference_type='TRANSFER_ITEM'`).
- `idempotencyKey` repetida → devuelve la transferencia existente (200).

Ajustes: motivos permitidos `COUNT_ADJUSTMENT, MANUAL_CORRECTION, DAMAGE, LOSS_THEFT, OBSOLESCENCE`; `SET` exige `quantity ≥ 0`, `DELTA` ≠ 0; la dirección debe coincidir con la del motivo (`400 REASON_DIRECTION`).

CSV: coincide por `sku` (o `referencia` si no hay SKU) + nombre de sucursal (sin distinguir mayúsculas). Solo actualiza listados existentes; movimiento `CSV_IMPORT` con `reference_type='IMPORT_JOB'` y un UUID por importación. La exportación neutraliza celdas que empiecen por `= + - @` (inyección de fórmulas).

## 4. Decisiones

- **Ledger escrito por la app, no por triggers.** Los triggers puente (`V202610141025`, activos en Supabase) registran todo como `MANUAL_CORRECTION`; la app fija `capture_managed_by_app` para no duplicar. Descartado: depender del trigger (pierde motivo y referencia).
- **Checkout sin cambios.** Ya descuenta `stock` y no escribe movimientos (N14 del plan Wompi); tocarlo ahora arriesga los pagos.
- **`inventory_reorder_policies` como umbral de stock bajo.** Ya existe en Supabase con historial de vigencia y la usa el modelo predictivo; añadir `inventory.min_stock` duplicaría el dato.
- **Transferencia inmediata (un paso).** Descartado "en tránsito con recepción": requiere estados y no lo pide ningún flujo actual.
- **JDBC para ledger y transferencias** (como `StockService`): tablas de solo inserción, sin entidad que `ddl-auto: update` cree sin restricciones.
- **CSV en todos los planes**: el plan Esencial ya lo anuncia. Las transferencias requieren ≥2 sucursales, lo que de hecho las limita a Negocio y Pro sin capability nueva.
- **Edición masiva**: `DELETE` y `PRICE` atómicos; `PUBLISH` publica los que se puedan y devuelve los bloqueados en `skipped`.

## 5. Tareas

Secuencia obligatoria: T1 → T2 → T3 → T4 → T5 → T10 → T6 → T7 → T8 → T9 → T11, todas en el worktree.

Estado (2026-11-05): T1 ✅ (en Supabase; archivos SQL locales eliminados) · T2 ✅ · T10 BD ✅ (Supabase) · resto pendiente. Detalle en el HANDOVER.

**T1 — Migración** · Sonnet · `database`
Hecho en Supabase (migración `inventory_operations`). Sin archivos locales.
Aceptación: tablas, RLS, GRANT y permisos presentes en Supabase (consultas de lectura).

**T2 — Ledger, ajustes, historial y stock bajo** · Sonnet · `backend`
Archivos: `core/inventory/StockLedger.java`, `StockService.java`, `InventoryRepository.java` (`countStockForStore` con el `reorder_point` vigente o 3), `storesuite/inventory/*` (`AdjustStock`, `ListMovements`, `LowStock`, `InventoryController`, DTO), `core/organization/StorePermissionCodes.java` + `StaffDirectory.java` (permisos nuevos); pruebas `StockLedgerTest`, `AdjustStockTest`.
Contexto: §3.2, §3.3 (ajustes, movimientos, motivos, low-stock, min-stock).
Aceptación: `mvn -q test -Dtest=StockLedgerTest,AdjustStockTest,StockServiceListingsTest,ModularityTests` en verde; `updateListing` con stock distinto escribe `MANUAL_CORRECTION`; permisos verificados antes de escribir.

**T3 — Transferencias** · Opus · `backend`
Archivos: `core/inventory/StockTransfers.java`, `storesuite/inventory/TransferStock.java`, `ListTransfers.java`, endpoints en `InventoryController`; pruebas `StockTransfersTest`, `TransferStockTest`.
Contexto: reglas de §3.3; `StockLedger` de T2; `BranchService.requireActiveInStore`; orden de bloqueo de `StockService.LOCK_FOR_RETURN_SQL`.
Aceptación: pruebas de ALL, ITEMS, un producto, misma sucursal, sucursal de otro almacén, stock insuficiente (sin cambios parciales), destino sin listado, idempotencia y permiso denegado; `-Dtest=StockTransfersTest,TransferStockTest,ModularityTests` en verde.

**T4 — Alta multi-sucursal y edición masiva** · Sonnet · `backend`
Archivos: `core/catalog/ProductCreateRequest.java`, `ProductCatalog.java`, `storesuite/products/CreateProduct.java`, `BulkProductAction.java`, `StoreProductController.java`; pruebas `ProductCatalogAtomicTest` (ampliar), `BulkProductActionTest`.
Contexto: §3.3 (`POST /products`, `POST /products/bulk`). Alta compatible con el cuerpo actual.
Aceptación: alta con `listings` crea un listado por sucursal con movimiento `INITIAL_LOAD`; sucursal ajena → 404 sin crear nada; bulk respeta permisos por acción y el límite de 500; `-Dtest=ProductCatalogAtomicTest,BulkProductActionTest,ModularityTests` en verde.

**T5 — CSV** · Sonnet · `backend`
Archivos: `storesuite/inventory/ExportInventoryCsv.java`, `ImportInventoryCsv.java`, endpoints; pruebas `InventoryCsvTest`.
Contexto: §3.3 (CSV). Sin dependencias nuevas (parser propio con `;` y comillas).
Aceptación: exportación con BOM y celdas `=+-@` neutralizadas; importación con errores no aplica nada; `dryRun` no escribe; límites de tamaño y filas → `400`; `-Dtest=InventoryCsvTest,ModularityTests` en verde.

**T10 — Planes TRY y ENTERPRISE** · Sonnet · `backend` → `frontend`
Archivos: (BD: aplicada en Supabase, migración `planes_try_enterprise`), `backend/.../core/billing/PlanService.java` (y su prueba si existe), `frontend/src/features/portal/lib/plan-capabilities.ts`, `pages/portal-plan-page.tsx`, componentes del comparador, `api/mock/mock-state.ts`.
Contexto: §3.4.
Aceptación: en Supabase `select code, enabled, recommended from seller_plans where deleted_at is null` devuelve solo TRY y ENTERPRISE (ambos enabled); el portal permite pagar ENTERPRISE con Wompi y abrir «Hablar con ventas»; `tsc -b` y lint en verde.

**T6 — Productos en el portal** · Sonnet · `frontend`
Archivos: `frontend/src/features/portal/api/{types.ts, product-service.ts, http/http-product-service.ts, mock/mock-product-service.ts}`, `pages/product-form-page.tsx`, `pages/products-page.tsx`.
Contexto: §3.3. Alta: selección de varias sucursales con stock (y precio opcional) por sucursal. Edición: añadir/quitar sucursal con los endpoints de listings. Listado: selección múltiple con barra de acciones (publicar, retirar, eliminar con confirmación, ajustar precio), filtros categoría, fabricante, sin imágenes, stock bajo. Textos de producto terminado.
Aceptación: `npm run build` (`tsc -b`) y `npm run lint` sin errores; el mock reproduce los mismos errores de la API.

**T7 — Inventario en el portal** · Sonnet · `frontend`
Archivos: `api/inventory-service.ts` (+ http y mock), `pages/inventory-page.tsx`, `pages/inventory-transfers-page.tsx`, `pages/inventory-movements-page.tsx`, rutas y navegación del portal, `pages/branches-page.tsx` (acción "Transferir inventario").
Contexto: §3.3. Transferencia: origen, destino, modo (todo / selección con cantidades / un producto), resumen y confirmación; `idempotencyKey` por intento. Ajuste con motivo desde la fila de inventario. Historial filtrable paginado. Alertas de stock bajo y edición de stock mínimo. Exportar e importar CSV con vista previa (`dryRun`). Ocultar acciones sin permiso.
Aceptación: `npm run build` y `npm run lint` sin errores; cada error de §3.3 tiene mensaje en español.

**T8 — Revisión** · Opus · `qa-seguridad`
Alcance: solo el diff de la rama y las funciones implementadas en este plan (indicación del usuario 2026-11-05); nada de revisión general del proyecto. IDOR (sucursal/producto/transferencia de otro almacén), permisos por endpoint, atomicidad y orden de bloqueo frente al checkout, que las unidades reservadas no se transfieran, inyección CSV y SQL, límites de tamaño, `ModularityTests`, ningún tipo `com.fasterxml` en controladores.
Aceptación: informe sin hallazgos altos; pruebas de T2–T5 en verde con `-Dtest=` explícito; `tsc -b` y lint en verde.

**T9 — PR** · Haiku · sesión orquestadora
`graphify update .`, commits por capa, push de `feature/inventario-sucursales` y PR a `develop` con resumen y nota de despliegue (esquema y planes ya están en Supabase).

**T11 — Producción** · sesión orquestadora (autorizado por el usuario el 2026-11-05)
Tras merge del PR a `develop` con CI en verde: `DRY_RUN=1 bash tools/release/promote-to-main.sh`, luego `git tag v1.1.0 origin/develop` y `git push origin v1.1.0`. Verificar el workflow de promoción y el despliegue del backend (Render) y frontend (Vercel).

## 6. Ejecución (lo que hace la sesión orquestadora)

Hecho:
- Exploración del código con graphify y lectura del esquema de Supabase (solo consultas de lectura).
- Worktree `C:\Development\Autopartco-dev-inventario`, rama `feature/inventario-sucursales` desde `origin/develop` (e85ec23).

Pasos:
1. **T1** hecho directamente en Supabase.
2. **T2 → T3 → T4 → T5** con el agente `backend`, uno tras otro (misma área); T3 con Opus. Tras cada uno leo el diff y ejecuto solo sus pruebas con `-Dtest=` explícito más `ModularityTests`. Nunca `BackendApplicationTests`.
3. **T6 → T7** con el agente `frontend`. Tras cada uno, `npm run build` y `npm run lint` en `frontend/`.
4. `graphify update .` en el worktree.
5. **T8** con el agente `qa-seguridad`. Cada hallazgo alto vuelve al agente del área para corregirlo y se repite la revisión hasta que no quede ninguno alto.
6. Commits por capa (`database`, `backend`, `frontend`, `docs`), push de la rama y PR a `develop` con resumen, pruebas ejecutadas y nota de despliegue; enlazo el PR y reviso su CI.
7. Todo SQL nuevo se aplica directamente en Supabase con `apply_migration`; nada se escribe en el repo. Se verifica con consultas de lectura.
8. Merge, tag y despliegue (T11): autorizados por el usuario el 2026-11-05 una vez terminado el plan.

Si durante la ejecución aparece un problema de diseño, se señala y se propone solo la decisión puntual, sin rehacer el plan.

## 7. Riesgos y verificación

- **Esquema desplegado**: migración de inventario y planes ya aplicados en Supabase (verificado 2026-11-05).
- **Suscripciones a planes retirados**: si un almacén tuviera BASIC/BUSINESS/PRO vigente, conserva el plan hasta `paid_through` y la renovación ofrece ENTERPRISE.
- **Triggers puente**: comprobar que `capture_managed_by_app` evita movimientos duplicados.
- **Interbloqueos**: transferencia y checkout deben bloquear en el mismo orden.
- **Tamaño del listado**: con 25.000 productos × 10 sedes el listado completo pesa mucho; la paginación en servidor queda como siguiente paso.
