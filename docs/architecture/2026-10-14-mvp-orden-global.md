# Orden global del MVP (CK, MK, SA, AD)

- Fecha: 2026-10-14
- Planes: [checkout](2026-10-14-mvp-checkout-plan.md) (`CK-T*`), [marketplace](2026-10-14-mvp-marketplace-plan.md) (`MK-*`), [SaaS](2026-10-14-mvp-saas-vendedores-plan.md) (`SA-*`), [admin](2026-10-14-mvp-admin-operaciones-plan.md) (`AD-*`). Backlog: [mvp-backlog](mvp-backlog.md) (`BL-*`).
- Prerrequisito (ola 0): integrar `chore/backend-pending-migrations` en `develop`.

## Reglas

- Una ola empieza cuando la anterior está integrada en `develop`. Dentro de una ola, cada agente ejecuta sus filas en el orden de la tabla; agentes distintos trabajan en paralelo (áreas disjuntas: `database/migrations/`, `backend/src/`, `frontend/`, `docs/`).
- `qa-seguridad` revisa el commit de cierre de la ola en que terminaron sus dependencias, no el árbol en edición.
- Un solo agente por área a la vez: todas las tareas `backend` van en serie entre planes; igual las `frontend` y las `database`.

## Piezas compartidas y dueño único

| Pieza | Dueño | Los demás |
|---|---|---|
| `shared/web/PageResponse<T>` y `frontend/src/lib/page.ts` | MK-B1 / MK-F1 (contrato en marketplace §4.0) | MK-B3, SA-B5, AD-B2, AD-B4, AD-B6, SA-F1, AD-F2: "depende de MK-B1/MK-F1" |
| Tokens, componentes base, primitivas `components/ui/*`, `lib/api.ts` (métodos), `lib/sale-status.ts` | MK-F0 (marketplace §6) | SA §5 y AD §6 son deltas de shell y contenido |
| `WebSecurityConfig` (rol revisor, `/admin/**`, rutas públicas de tiendas y reseñas) | AD-B3 | MK-B2 y MK-B4 dependen de AD-B3 |
| `/admin/plans` | Backend y contrato: SA-B4 · UI: AD-F6 · `@PreAuthorize`: AD-B3 | Auditoría de planes en SA-B4 con `AuditService.record` de AD-B2 |
| `StockService` / movimiento de venta (BL-B6) | `UPDATE` atómico y firma: CK-T2 · registro del movimiento: SA-B5 | CK-T6 no se toca |
| `GET /me/stores` (BL-B3) | SA-B1 | — |
| `internal/Store.java` | AD-B1 (tras la ola 0) | SA-B1, MK-B1, MK-B2 solo leen el nombre de columna vigente |
| `Subscriptions.startTrial` | SA-B2 | AD-B4 lo invoca |
| `/seller/setup` y `/vender/solicitud` (BL-F15) | AD-F4 (rutas en AD-F1) | SA-F8 solo `/seller/invitation` |

Archivos tocados por varias tareas, siempre en este orden: `ListingQueries.java` CK-T3 → MK-B1 → MK-B2 → SA-B6 · `StockService.java` CK-T2 → SA-B5 · `StoreService.java` SA-B1 → AD-B6 · `database/migrations/README.md` AD-D1 → SA-D1 · `router.tsx`/`lazy-pages.tsx` CK-T10 → MK-F3 → MK-F4 → MK-F6 → MK-F7 → SA-F1 → AD-F1 · `cart-source.ts` CK-T9 → MK-F2 · `features/auth/api/types.ts` CK-T8 → AD-F1.

## Secuencia

Orden = `ola.posición`.

| Orden | ID | Plan | Agente | Modelo | Depende de | Se despliega junto con |
|---|---|---|---|---|---|---|
| 1.1 | AD-D1 | AD | database | Sonnet | — | Aplicar en la BD antes de AD-B1 (**obligatorio**, requiere aprobación) |
| 1.2 | SA-D1 | SA | database | Haiku | AD-D1 (README) | No se aplica (Flyway pendiente) |
| 1.3 | CK-T1 | CK | backend | Haiku | ola 0 | — |
| 1.4 | CK-T2 | CK | backend | Sonnet | CK-T1 | — |
| 1.5 | CK-T3 | CK | backend | Sonnet | CK-T2 | — |
| 1.6 | CK-T4 | CK | backend | Haiku | CK-T3 | — |
| 1.7 | CK-T5 | CK | backend | Sonnet | CK-T4 | — |
| 1.8 | CK-T6 | CK | backend | Opus | CK-T1–CK-T5 | — |
| 1.9 | CK-T8 | CK | frontend | Haiku | — | — |
| 1.10 | CK-T9 | CK | frontend | Sonnet | CK-T8 | — |
| 1.11 | MK-F0 | MK | frontend | Sonnet | CK-T8, CK-T9 | — |
| 1.12 | MK-F1 | MK | frontend | Sonnet | MK-F0 | **MK-B1 (obligatorio)** |
| 1.13 | MK-F2 | MK | frontend | Sonnet | CK-T9, MK-F0 | — |
| 1.14 | MK-F3 | MK | frontend | Sonnet | MK-F2 | — |
| 1.15 | CK-T10 | CK | frontend | Sonnet | CK-T9 | — |
| 1.16 | CK-T11 | CK | frontend | Sonnet | CK-T10 | — |
| 1.17 | CK-T13 | CK | redactor | Haiku | — | — |
| 2.1 | SA-B1 | SA | backend | Sonnet | CK-T1 | — |
| 2.2 | MK-B1 | MK | backend | Sonnet | CK-T3 | **MK-F1 (obligatorio)** |
| 2.3 | MK-B3 | MK | backend | Sonnet | CK-T5, CK-T6, MK-B1 | — |
| 2.4 | MK-F4 | MK | frontend | Sonnet | MK-F0 | MK-B2 (recomendado: retira `nit`/`businessEmail`) |
| 2.5 | MK-F5 | MK | frontend | Sonnet | MK-F0 | — |
| 2.6 | MK-F6 | MK | frontend | Sonnet | MK-F0, MK-F1, CK-T11 | — |
| 2.7 | MK-F7 | MK | frontend | Sonnet | MK-F0, CK-T10 | — |
| 2.8 | MK-F8 | MK | frontend | Haiku | MK-F6, MK-F7 | — |
| 2.9 | CK-T7 | CK | qa-seguridad | Opus | CK-T6 | — |
| 2.10 | CK-T12 | CK | qa-seguridad | Sonnet | CK-T11 (parte `http`: CK-T6) | — |
| 3.1 | SA-B2 | SA | backend | Opus | CK-T1 | — |
| 3.2 | SA-B3 | SA | backend | Sonnet | SA-B2 | — |
| 3.3 | AD-B1 | AD | backend | Sonnet | AD-D1 | Requiere AD-D1 aplicada (**obligatorio**) |
| 3.4 | AD-B2 | AD | backend | Sonnet | AD-B1 | — |
| 3.5 | AD-B3 | AD | backend | Sonnet | — (aprobación de seguridad) | Antes o con MK-B2 |
| 3.6 | MK-B2 | MK | backend | Sonnet | AD-B3, MK-B1 | MK-F4 (recomendado) |
| 3.7 | SA-F1 | SA | frontend | Sonnet | CK-T8, MK-F0, MK-F1 | — |
| 3.8 | SA-F2 | SA | frontend | Sonnet | SA-F1 | — |
| 3.9 | SA-F3 | SA | frontend | Sonnet | SA-F1 | — |
| 3.10 | SA-F5 | SA | frontend | Sonnet | SA-F1 | — |
| 3.11 | SA-F6 | SA | frontend | Sonnet | SA-F1 | — |
| 3.12 | SA-F7 | SA | frontend | Sonnet | SA-F1 | — |
| 3.13 | SA-F8 | SA | frontend | Sonnet | SA-F1 | — |
| 3.14 | SA-F10 | SA | frontend | Sonnet | SA-F1 | — |
| 3.15 | MK-Q2 | MK | qa-seguridad | Sonnet | MK-F8 | — |
| 4.1 | SA-B4 | SA | backend | Sonnet | AD-B2 | — |
| 4.2 | AD-B4 | AD | backend | Opus | AD-B1, AD-B2, AD-B3, MK-B1, SA-B2 | — |
| 4.3 | AD-B5 | AD | backend | Sonnet | AD-B1 | **AD-F3 (obligatorio)** |
| 4.4 | AD-B6 | AD | backend | Sonnet | AD-B2, MK-B1, SA-B1 | — |
| 4.5 | SA-B5 | SA | backend | Sonnet | CK-T2, MK-B1 | — |
| 4.6 | SA-B6 | SA | backend | Sonnet | SA-B5, MK-B2 | — |
| 4.7 | SA-B7 | SA | backend | Sonnet | SA-B5, CK-T6 | — |
| 4.8 | SA-B8 | SA | backend | Haiku | SA-B2, SA-B6 | — |
| 4.9 | SA-B9 | SA | backend | Sonnet (revisión Opus) | SA-B5, SA-B6 | — |
| 4.10 | AD-F1 | AD | frontend | Sonnet | MK-F0 | — |
| 4.11 | AD-F2 | AD | frontend | Sonnet | AD-F1, MK-F1 | — |
| 4.12 | AD-F3 | AD | frontend | Sonnet | AD-F1 | **AD-B5 (obligatorio)** |
| 4.13 | AD-F4 | AD | frontend | Sonnet | AD-F1 | — |
| 4.14 | AD-F6 | AD | frontend | Sonnet | AD-F1 | — |
| 4.15 | MK-Q1 | MK | qa-seguridad | Opus | MK-B1, MK-B2, MK-B3 | — |
| 5.1 | MK-B4 | MK | backend | Sonnet | AD-B3 (P2, opcional, con aprobación) | MK-F9 |
| 5.2 | AD-F5 | AD | frontend | Haiku | AD-F2–AD-F4, AD-F6, AD-B4–AD-B6 | Antes de AD-B7 |
| 5.3 | SA-F4 | SA | frontend | Sonnet | SA-F3 | — |
| 5.4 | SA-F9 | SA | frontend | Sonnet | SA-F1 | — |
| 5.5 | SA-F11 | SA | frontend | Sonnet | SA-F3 | — |
| 5.6 | MK-F9 | MK | frontend | Sonnet | MK-F4, MK-F6 (P2, opcional) | MK-B4 |
| 5.7 | SA-Q1 | SA | qa-seguridad | Opus | SA-B9 | — |
| 6.1 | AD-B7 | AD | backend | Haiku | AD-F5 desplegada | **Después de AD-F5 (obligatorio)** |
| 6.2 | SA-Q2 | SA | qa-seguridad | Sonnet | SA-F11, SA-B9 | — |
| 7.1 | AD-Q1 | AD | qa-seguridad | Opus | AD-B7 | — |

Siete olas (más la ola 0 de integración). Rutas críticas P0: CK-T1 → CK-T6 → MK-B3 → MK-F6 y SA-B1 → SA-F1 → SA-F6.

## Requiere aprobación del usuario (lista única)

1. **Seguridad en `WebSecurityConfig` (AD-B3):** rol `PLATFORM_REVIEWER` (también su `INSERT` en AD-D1), `/admin/**` para ROOT y revisor, `PublicRoute GET /marketplace/stores/*` y, si se hacen reseñas, `/marketplace/stores/*/reviews`.
2. **Autorización del checkout Wompi de suscripción** de "correo del solicitante" a "rol OWNER" (SA-B2; A5 del SaaS).
3. **Comandos contra BD:** aplicar AD-D1 (`V202610141400`) y crear cuentas revisoras por SQL; ejecutar SA-D1 o la semilla de planes; verificación `http` de MK-Q2, SA-Q2, AD-F5 y AD-Q1 contra la BD de `backend/.env` (el guion `curl` de CK-T7 ya está autorizado).
4. **Fuente:** mantener Geist (recomendado) o añadir `@fontsource-variable/inter` (D8 de MK, D12/A6 de SA, D9 de AD).
5. **Dependencias de UI:** cualquier `npx shadcn add` de MK-F0 que modifique `package.json`.
6. **Precios y límites de la semilla de planes** (`TRY` 0, `BASIC` 59.900, `BUSINESS` 149.900, `ENTERPRISE` 499.900 COP/mes; A1 del SaaS).
7. **Cobro simulado de suscripción activo por defecto** en builds publicados (`app.billing.simulated-payments-enabled=true`; A2). El de pedidos ya está decidido: activo para la demo.
8. **Límites durante la prueba:** los de `TRY` (propuesto) o los del plan elegido (A3).
9. **Ocultar del marketplace productos de tiendas con suscripción vencida** (propuesto P1, fuera del MVP; A4).
10. **La prueba empieza al aprobar**, no al configurar (D2 de AD).
11. **Cambios de contrato público del marketplace:** forma paginada de `GET /marketplace/products` (D1 de MK) y retirar `nit`/`businessEmail` de `/marketplace/stores` (D5 de MK).
12. **Destructivos, fuera del MVP:** retención y borrado de documentos de solicitudes rechazadas (Ley 1581) y liberar NIT, nombre o correo de solicitudes rechazadas (D7 de AD).
13. **Rate limiting** en endpoints públicos (`POST /store-applications`, `POST /marketplace/orders`) si requiere dependencia.
14. **Despliegue:** cada etiqueta `vX.Y.Z` hacia `main`, respetando los despliegues conjuntos de la tabla.

Ya decididos (no reabrir): Testcontainers no; `getAccessToken()` sí; Playwright ya instalado; pagos simulados de pedidos activos para la demo; fechas del plan admin con `LocalDateTime` sin zona (ADR 0002 opción A), como el resto de planes; paginación `{items, page, size, total, totalPages}` aprobada y reflejada en `docs/api/conventions.md` §8.
