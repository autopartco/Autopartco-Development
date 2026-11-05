# HANDOVER — Inventario por sucursal y planes TRY/ENTERPRISE

Plan: `docs/architecture/Inventario-feature.md`. Rama `feature/inventario-sucursales` en el worktree `C:\Development\Autopartco-dev-inventario`, desde `origin/develop` (e85ec23 = v1.0.3 en producción).

## Reglas de esta entrega

- BD de trabajo: Supabase `autopartco-dev` (`hwojkaguqmegvdxlcysd`). Nada contra la BD local. Migraciones con `apply_migration`; lecturas con `execute_sql`. **SQL nuevo solo en Supabase, nunca escrito en el repo** (los SQL existentes se conservan).
- Planes: solo `TRY` y `ENTERPRISE` (contratable con Wompi + «Hablar con ventas»).
- Producto terminado: sin textos de demo, prueba técnica ni sandbox.
- Pruebas backend solo con `-Dtest=<Clase>` (nunca `BackendApplicationTests`).
- Al terminar: PR a `develop`, merge y tag para producción (autorizado 2026-11-05).

## Estado

| Tarea | Estado | Notas |
|---|---|---|
| T1 Migración | ✅ | Aplicada en Supabase (`stock_transfers`, `stock_transfer_items`). Sus archivos SQL locales sin commitear se descartaron. |
| T2 Ledger, ajustes, historial, stock bajo | ✅ | `StockLedger`, `InventoryQueries`, `ReorderPolicies`, `storesuite/inventory/*`, `storesuite/stockalerts/*`, permisos `INVENTORY_ADJUST`/`INVENTORY_TRANSFER`. `-Dtest=StockLedgerTest,AdjustStockTest,StockServiceListingsTest,ModularityTests` en verde. |
| T3 Transferencias | ✅ | `StockTransfers`, `TransferStock`, `ListTransfers`, DTO y endpoints. 45 pruebas en verde. Paginación desde `page=1`. Errores extra: `DUPLICATE_PRODUCT` (400), `TRANSFER_TOO_LARGE` (409, >10.000), `TRANSFER_CONFLICT` (409). Listado reactivado suma al stock que conservaba. |
| T4 Alta multi-sucursal y masivo | ✅ | `listings` en alta (exclusivo con `branchId`/`quantity`), `POST /products/bulk` (DELETE/PRICE atómicos; AMOUNT = precio absoluto, PERCENT -90..500). |
| T5 CSV | ✅ | Export con BOM y neutralización; import con `dryRun` (por defecto true), códigos por fila y de archivo. |
| T10 Planes TRY/ENTERPRISE | 🔄 | BD ✅ Supabase (`planes_try_enterprise`). Frontend ✅: Empresarial contratable con Wompi + «Hablar con ventas»; CSV y alertas de stock en todos los planes. `PlanService` ✅. |
| T6 Productos (frontend) | ✅ | Alta multi-sucursal, añadir/quitar sucursal, selección y acciones masivas, filtros. Lint y build en verde. PRICE: AMOUNT = precio absoluto, PERCENT = variación (ajuste en T7). |
| T7 Inventario (frontend) | ✅ | Ajustes con motivo, stock bajo y mínimo, CSV con vista previa, transferencias (idempotentes), historial; pestañas Existencias/Movimientos/Transferencias; «Transferir inventario» en sucursales. Lint y build en verde. |
| T8 QA | ✅ | Acotado al diff. Sin IDOR ni inyección; 15 clases en verde. Hallazgos corregidos: (Alto) import CSV fijaba stock absoluto → columna `stock_base` y delta sobre stock bloqueado; (Medio) bloqueo ordenado antes de precio/mínimo en import; (Medio) DELETE masivo máx. 50. (Bajo, aceptado) editar producto cambia stock con `PRODUCT_UPDATE` sin `INVENTORY_ADJUST`: hoy solo OWNER tiene permisos. |
| T9 PR | 🔄 | |
| T11 Producción | ⏳ | |

## Hallazgos

- `HANDOVER-inventario.md` no existía; se creó con este estado.
- BASIC/BUSINESS/PRO nunca llegaron a Supabase; los planes se gestionan solo allí.
- Cambios sin commitear en el worktree incluyen `.claude/hooks/agent-guard.mjs` (también modificado en el checkout principal): no se incluye en los commits de la feature salvo indicación del usuario.

- `frontend/node_modules` del worktree es una junction al del checkout principal (ignorada por git).
- Textos «Pago de prueba» en `portal-plan-page.tsx` solo aparecen con `paymentMode === 'SIMULATED'`; producción usa Wompi.

- SQL nuevo del backend validado contra Supabase con `PREPARE` + `ROLLBACK` (2026-11-05): todo compila contra el esquema real.

## Registro

- 2026-11-05: revisión de estado, plan actualizado con §3.4 (planes), T10 y T11.
- 2026-11-05: el usuario indica que desde ahora todo SQL se implementa solo en Supabase (los SQL existentes del repo se conservan); los planes se aplicaron directamente allí.
