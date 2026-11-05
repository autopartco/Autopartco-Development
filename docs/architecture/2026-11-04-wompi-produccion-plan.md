# Plan: cobros reales con Wompi en producción (marketplace + suscripciones)

- Fecha: 2026-11-04
- Relacionado: `docs/adr/0004-cobros-wompi-produccion.md`, `docs/api/payments-wompi.md`, `docs/architecture/2026-10-14-mvp-checkout-plan.md` (D1 queda modificada para el proveedor WOMPI)
- Las correcciones hechas contra el código están marcadas con **[N#]** y explicadas en "Notas del arquitecto".

## Objetivo y alcance
Entra: cobrar en producción con Wompi (Web Checkout) los pedidos del marketplace y las suscripciones de almacenes; todo el dinero llega a la cuenta Wompi de Autopartco; confirmación servidor-servidor (webhook firmado + consulta a la API), idempotencia, reserva y liberación de stock, conciliación de pendientes, separación SANDBOX/PRODUCTION.
No entra: reembolsos y devoluciones (solo se marcan para gestión manual), dispersión/liquidación a vendedores, comisión del marketplace, cobro recurrente automático (tokenización), facturación electrónica, consola `operations.payments`.

## Punto de partida (código actual)
- Suscripciones: `core.billing.SubscriptionCheckout` ya genera el enlace firmado de Web Checkout (`APC-…`, firma de integridad) y `WompiWebhookController` → `WompiWebhookProcessor` aplica el pago sobre `seller_payments`. Falta: entorno, política de respuesta del webhook, registro de eventos, conciliación. `seller_payments.status` guarda el estado crudo de Wompi (`DECLINED`, `ERROR`, `VOIDED`) y el portal depende de ello **[N8]**.
- Marketplace: `marketplace.checkout.PlaceMarketplaceOrder` cobra **síncrono** con `SimulatedCheckoutPaymentGateway` dentro de la transacción y crea el pedido `CONFIRMED`. Hoy `provider_reference = clientRequestId` con `provider='SIMULATED'`. Wompi es asíncrono: hay que partir el flujo.
- BD activa (`database/init.sql` + `ddl-auto`) **[N2]**: `payments` (`provider`, `provider_reference`, `amount DECIMAL(12,2)` en pesos), `payment_events` (`payment_id`, `payment_status_id`, `message`), `payment_statuses` (PENDING, APPROVED, REJECTED, CANCELLED, REFUNDED), `seller_payments`, estados ORDER (PENDING, CONFIRMED, …, CANCELLED). Solo en migraciones sin aplicar: `payments.environment/idempotency_key/attempt_number` (V202610141310), `payment_events.source/provider_event_id/provider_status`, `cancellation_reasons`, `order_status_history` (V202610141020), `state_domains` (V202610151020), `stock_reservations` (V202610141230).

## Componentes y responsabilidades
| Componente | Módulo | Responsabilidad |
|---|---|---|
| `WompiClient` | `core.payments.internal.wompi` (nuevo) | Firma de integridad, URL de checkout, `GET /v1/transactions/{id}` y `GET /v1/transactions?reference=` (llave privada), verificación de firma de eventos. Base URL según entorno. |
| `WompiProperties` | `core.payments` (se mueve desde billing) | `environment`, `publicKey`, `privateKey`, `integritySecret`, `eventsSecret`, `checkoutUrl`, `apiUrl`, `redirectUrl`. Validación al arrancar. |
| `WompiWebhookController` + `WompiEventRouter` | `core.payments.web` | Recibe el evento, verifica firma, registra el evento (dedupe), y despacha por prefijo de referencia a un `WompiTransactionHandler`. |
| `WompiTransactionHandler` (SPI) | `core.payments` | `boolean supports(String reference)`; `void apply(WompiTransaction tx, Source source)`. Implementaciones: billing (`APC-`) y checkout (`APM-`). |
| `SubscriptionWompiHandler` | `core.billing.internal` | Lógica actual de `WompiWebhookProcessor` adaptada al SPI. |
| `MarketplaceWompiHandler` | `marketplace.checkout` | Aprueba/rechaza el pago del pedido, confirma o mantiene el pedido, marca pagos tardíos. |
| `StartMarketplaceCheckout` | `marketplace.checkout` | Valida carrito, reserva stock, crea pedido `AWAITING_PAYMENT` + pago `PENDING` (`APM-…`), devuelve URL firmada. |
| `ExpireAwaitingPayments` + `ReconcilePendingPayments` | `marketplace.checkout` / `core.payments` | `@Scheduled`: consulta Wompi por pendientes; vence reservas y libera stock. Requiere `@EnableScheduling` (hoy no existe) **[N11]**. |
| Página de resultado | `frontend/` | Lee `id` de la redirección, llama al backend y muestra estado (polling corto). Nunca decide el estado. |

## Contratos
### Configuración (`.env` raíz / Render; nunca en frontend salvo la llave pública)
```
WOMPI_ENVIRONMENT=SANDBOX|PRODUCTION
WOMPI_PUBLIC_KEY=pub_test_…|pub_prod_…
WOMPI_PRIVATE_KEY=prv_test_…|prv_prod_…        # solo backend, para consultar transacciones
WOMPI_INTEGRITY_SECRET=…
WOMPI_EVENTS_SECRET=…
WOMPI_CHECKOUT_URL=https://checkout.wompi.co/p/
WOMPI_API_URL=(derivado: https://sandbox.wompi.co/v1 | https://production.wompi.co/v1)
WOMPI_REDIRECT_URL=https://<front>/portal/pago/resultado
APP_FRONTEND_BASE_URL=https://<front>           # existente; el redirect del marketplace es <base>/pedido/{orderId}/resultado [N6]
APP_CHECKOUT_PAYMENT_PROVIDER=SIMULATED|WOMPI   # sustituye a APP_CHECKOUT_SIMULATED_PAYMENTS_ENABLED [N1]
APP_CHECKOUT_RESERVATION_MINUTES=30
SIMULATED_PAYMENTS_ENABLED=false                # nombre real del simulador de suscripciones [N1]
```
Arranque falla si: `PRODUCTION` y alguna llave no empieza por `pub_prod_`/`prv_prod_`; `SANDBOX` con llaves `prod`; perfil `prod` con algún simulador activo; `WOMPI` elegido y faltan secretos; `PRODUCTION` con `WOMPI_REDIRECT_URL` o `APP_FRONTEND_BASE_URL` que no sean `https://` **[N16]**.

### Referencias
- Suscripción: `APC-<uuid32>` (existente). Marketplace: `APM-<uuid32>`, una por pedido. `clientRequestId` pasa a `payments.idempotency_key`.

### API
Contrato completo para el frontend: `docs/api/payments-wompi.md`.
- `GET /api/v1/marketplace/checkout-options` (nuevo) → proveedor activo y métodos, para que el frontend no codifique los métodos simulados **[N12]**.
- `POST /api/v1/marketplace/orders` (mismo cuerpo; `simulatedOutcome` solo con proveedor SIMULATED).
  - SIMULATED: comportamiento actual (201 pedido confirmado).
  - WOMPI: `202 Accepted` → `{ orderId, orderNumber, status:"AWAITING_PAYMENT", payment:{ reference, amountInCents, currency, checkoutUrl, expiresAt } }`. Repetición con mismo `clientRequestId` → `200` con **la misma forma** y el estado actual (`checkoutUrl` `null` si ya no se puede pagar) **[N13]**.
- `GET /api/v1/me/orders/{orderId}/payment` (dueño del pedido) → `{ orderId, reference, status: PENDING|APPROVED|REJECTED|CANCELLED|EXPIRED, providerStatus, orderStatus, amountInCents, currency, checkoutUrl|null, expiresAt, paidAt }`.
- `POST /api/v1/me/orders/{orderId}/payment/sync` body `{ transactionId }` → consulta `GET /v1/transactions/{id}` en Wompi y aplica con `Source.REDIRECT_SYNC` si la referencia coincide con la del pedido; responde como el GET. Rate limit por usuario.
- `GET /api/v1/stores/{storeId}/subscription/payments/{reference}` (OWNER) → estado del pago de suscripción (para la página de resultado del portal) y `POST …/{reference}/sync` con el mismo cuerpo.
- `POST /api/v1/webhooks/wompi` (público): firma inválida o incompleta → 401 (hoy 400); evento ya registrado → 200; referencia desconocida, monto/moneda distintos o entorno distinto → se registra con `outcome=UNKNOWN_REFERENCE|REJECTED_MISMATCH`, log `ERROR`, **200** (evita reintentos infinitos); éxito → 200 sin cuerpo (hoy 204) **[N7]**; error interno → 500 (Wompi reintenta).

### Mapeo de estados Wompi → `payment_statuses` (solo `payments` del marketplace)
APPROVED→APPROVED · DECLINED→REJECTED · ERROR→REJECTED · VOIDED→CANCELLED · PENDING→sin cambio · vencido por Autopartco→EXPIRED (nuevo). El estado crudo de Wompi se guarda en `payments.provider_status` y en el evento. `seller_payments.status` conserva el estado crudo **[N8]**.

### Reglas de negocio del pedido
1. `StartMarketplaceCheckout` (una transacción): validaciones actuales + descuento de stock (reserva) + `orders` `AWAITING_PAYMENT` + `sales` `PENDING` + `payments` `PENDING` (`provider=WOMPI`, `environment`, `expires_at = now + reserva`). Sin llamadas de red.
2. APPROVED dentro del plazo → pago APPROVED (`paid_at`, `provider_transaction_id`), pedido `CONFIRMED`, `order_status_history` cuando exista en el esquema activo **[N2]**. Idempotente por `(provider, provider_transaction_id)`.
3. DECLINED/ERROR/VOIDED → evento registrado y pago `REJECTED`/`CANCELLED`; el pedido sigue `AWAITING_PAYMENT` hasta vencer (el comprador puede reintentar en el checkout con la misma referencia). `REJECTED → APPROVED` es transición válida dentro del plazo.
4. Vence sin aprobación → pago EXPIRED, pedido `CANCELLED` (motivo `PAYMENT_TIMEOUT`, existente **[N3]**), stock devuelto (sin movimiento de inventario, igual que el descuento actual **[N14]**).
5. APPROVED después de vencer/cancelar → pago APPROVED con `requires_manual_refund = true`, pedido sigue `CANCELLED`, log `ERROR`. Se gestiona a mano (reembolsos fuera de alcance).
6. Validar siempre: referencia, monto en centavos = `amount.movePointRight(2).longValueExact()` **[N10]**, moneda COP, entorno.
7. Conciliación cada 5 min: pagos `PENDING`/`REJECTED` no vencidos con > 2 min → `GET /v1/transactions?reference=` (llave privada) y aplicar con `Source.RECONCILIATION`; luego vencer los expirados. Filas tomadas con `FOR UPDATE SKIP LOCKED` (seguro con varias instancias) **[N11]**.

### Esquema de datos (`database/init.sql` obligatorio **[N2]** + migración `V2026110410xx__wompi_production.sql` / `U…`)
- `states` (dominio ORDER): `AWAITING_PAYMENT` (y en `state_domains` si esa migración se aplica). `cancellation_reasons`: reutilizar `PAYMENT_TIMEOUT` **[N3]**.
- `payment_statuses`: `EXPIRED`. `payment_methods`: `WOMPI` ("Wompi: tarjeta, PSE, Nequi, Bancolombia").
- `payments`: en `init.sql` además `idempotency_key` (único parcial) y `environment` (hoy solo en V202610141310); `provider_transaction_id VARCHAR(100)` + índice único parcial `(provider, provider_transaction_id)`; `expires_at TIMESTAMPTZ`; `requires_manual_refund BOOLEAN NOT NULL DEFAULT false`; `provider_status VARCHAR(30)`; índice parcial para conciliación `(payment_status_id, created_at) WHERE provider='WOMPI'`.
- `orders`: `cancellation_reason_code VARCHAR(40)` y `cancelled_at` en `init.sql` si no existen (hoy solo en V202610141020) **[N2]**.
- `payment_events.source`: ampliar el CHECK de V202610141020 con `REDIRECT_SYNC` **[N4]**.
- `seller_payments`: `environment` obligatorio para filas nuevas (rellenar con la configuración); `requires_manual_refund`.
- `payment_provider_events` (nuevo, solo inserción): `id, provider, environment, event_type, checksum (NULL en REDIRECT_SYNC/RECONCILIATION; único parcial (provider, checksum) WHERE checksum IS NOT NULL) [N5], reference, provider_transaction_id, provider_status, amount_in_cents, currency, source (WEBHOOK|REDIRECT_SYNC|RECONCILIATION), outcome (APPLIED|DUPLICATE|IGNORED|REJECTED_MISMATCH|UNKNOWN_REFERENCE), payload JSONB, received_at`. Los eventos con firma inválida no se guardan, solo log `WARN` [N5]. Sin datos de tarjeta (Wompi no los envía; no ampliar el payload).

## Decisiones clave
- **Web Checkout redirigido** (ya usado en suscripciones) en vez de Widget o API directa: no toca datos de tarjeta (sin alcance PCI), soporta PSE/Nequi/Bancolombia. Descartada la API de transacciones directa (exige tokenizar y aceptar términos por API).
- **Módulo `core.payments` con SPI por prefijo** en vez de un webhook por producto: Wompi admite una sola URL de eventos por cuenta/entorno.
- **Reserva de stock al iniciar el pago** con vencimiento (30 min) en vez de descontar al aprobar: evita vender sin stock; el costo es stock bloqueado unos minutos. Debe coincidir con `expiration-time` del checkout si Wompi lo admite (verificar).
- **DECLINED no cancela de inmediato**: el comprador puede reintentar dentro del checkout con la misma referencia.
- **Pagos tardíos se marcan, no se reembolsan**: coherente con "devoluciones después".
- **Webhook responde 200 a eventos válidos pero inaplicables**: se registran para revisión y no generan tormenta de reintentos.
- **Consulta a la API como segunda fuente** (sync de redirección y conciliación): el redirect nunca es prueba; el webhook puede perderse.

## Tareas (database → backend → frontend → qa-seguridad)

### T1 — Opus — `arquitecto`
Archivos: `docs/adr/0004-cobros-wompi-produccion.md`, `docs/architecture/2026-11-04-wompi-produccion-plan.md` (este plan) y `docs/api/payments-wompi.md`. Aceptación: ADR con las decisiones clave, el mapeo de estados y la lista de bloqueos del usuario; contrato HTTP para el frontend (otra cuenta) en ese archivo.

### T2 — Sonnet — `database`
Archivos: `database/init.sql` (obligatorio, esquema activo), `database/migrations/V…__wompi_production.sql`, `U…__wompi_production.sql`, `database/migrations/README.md`. Contexto: sección "Esquema de datos"; seguir ADR 0001 (versionado) y 0002 (`timestamptz`); en `init.sql` solo `ADD COLUMN IF NOT EXISTS`/`CREATE … IF NOT EXISTS` idempotentes; `payment_provider_events` con `forbid_update_delete()` como `payment_events` en la migración; semillas idempotentes (`ON CONFLICT DO NOTHING`). Aceptación: SQL revisable, reversible; no ejecutar contra ninguna BD sin autorización.

### T3 — Sonnet — `backend` (infraestructura de pagos)
Archivos: `core/payments/**` (nuevo), mover `WompiProperties`/`WompiSignatureVerifier`, `WompiWebhookController` → router + SPI, `core/billing/internal/SubscriptionWompiHandler.java`, `BillingSettings`, `SubscriptionCheckout`, `storesuite/subscription/SubscriptionController` (GET/sync por referencia), `application.yaml`, `.env.example`, pruebas. Aceptación (`-Dtest=` explícito): validación de arranque por entorno/prefijo de llaves; firma inválida 401; evento duplicado (mismo checksum) 200 sin efectos; referencia desconocida 200 + evento `UNKNOWN_REFERENCE`; flujo de suscripción actual sin regresión (`SubscriptionCheckoutTest`, prueba del procesador adaptada); `ModularityTests` pasa.

### T4 — Sonnet (Opus si falla 2 veces) — `backend` (checkout marketplace)
Archivos: `marketplace/checkout/**` (`StartMarketplaceCheckout`, `MarketplaceWompiHandler`, `ExpireAwaitingPayments`, controlador, DTO, `checkout-options`), `core/order/OrderPlacement.java` (insertar en espera / confirmar / cancelar), `core/order/BuyerOrderQueries.java` (lectura por `provider`+`idempotency_key`), `core/inventory/StockService` (liberar reserva), `ReconcilePendingPayments`, configuración `@EnableScheduling`, pruebas. Aceptación: reglas 1–7 cubiertas con pruebas unitarias; carrera APPROVED vs vencimiento resuelta con bloqueo de fila del pago (un solo ganador); importe calculado solo en servidor; proveedor SIMULATED conserva el comportamiento y pruebas actuales (`PlaceMarketplaceOrderTest`).

### T5 — Sonnet — `frontend` (desde la cuenta del frontend)
Archivos: checkout del marketplace, página `/pedido/:id/resultado` (existente) **[N6]**, página `/portal/pago/resultado`, cliente API y tipos. Contexto: `docs/api/payments-wompi.md`. Aceptación: con `202` redirige a `checkoutUrl`; la página de resultado llama a `sync` con el `id` de Wompi y hace polling a `GET …/payment` (máx. ~60 s) mostrando aprobado/pendiente/rechazado/vencido con acción de reintentar; sin llaves privadas en el bundle; `tsc -b` y lint limpios.

### T6 — Opus — `qa-seguridad`
Revisión del diff T2–T5: firma y comparación en tiempo constante, idempotencia, IDOR en `GET/sync` de pagos, monto/moneda/entorno, secretos fuera de logs y respuestas, rate limit de `sync`, carreras de stock. Ejecutar las pruebas de T3/T4 con `-Dtest=`. Aceptación: informe sin hallazgos altos abiertos.

### T7 — Haiku — `redactor`/OpenCode
`docs/deploy/wompi-produccion.md`: runbook de salida a producción (checklist de abajo). Aceptación: pasos verificables y sin secretos.

## Puesta en producción (la hace el usuario; requiere autorización)
1. Sandbox completo: llaves `test`, URL de eventos del entorno sandbox apuntando al backend de staging, pruebas con tarjetas/PSE de prueba de Wompi (aprobado, rechazado, pendiente).
2. En el panel Wompi (producción): URL de eventos `https://<backend>/api/v1/webhooks/wompi`, copiar llaves y secretos a Render; `APP_CHECKOUT_PAYMENT_PROVIDER=WOMPI`, simuladores en `false`.
3. Aplicar el cambio de esquema en producción (autorización).
4. `git tag vX.Y.Z origin/develop` + push (autorización).
5. Prueba de humo con un pago real pequeño hecho por el usuario, verificando evento registrado, pedido confirmado y monto en el panel Wompi.

## Riesgos y bloqueos a verificar
- **Recaudo para terceros (riesgo asumido por el usuario, no bloqueante):** cobrar ventas de los almacenes en la cuenta de Autopartco implica actuar como agregador/recaudador. Decisión del usuario del 2026-11-04 ("Wompi desde ya"): el marketplace cobra con WOMPI en producción desde el despliegue y el usuario se hace responsable de esta validación. Se sigue recomendando confirmar con Wompi que la cuenta lo permite y con el contador el tratamiento (ingresos para terceros, retenciones, facturación electrónica de quién vende). Para suspender el cobro, se marca inactivo el método `WOMPI` en `payment_methods` (requiere autorización). No sirve volver a `SIMULATED`, porque el perfil `prod` no arranca con el simulador activo.
- **Cambio de la decisión D1 del checkout [N9], cerrado:** el usuario confirmó el 2026-11-04 ("Sí, mostrarlos") que, con WOMPI, un pedido con pago fallido o vencido queda `CANCELLED`. El comprador lo ve en "Mis pedidos" ("Cancelado: pago no completado") y también aparece en el portal del almacén.
- Webhook perdido o caído → mitigado por conciliación; vigilar logs `ERROR` de pagos tardíos y `REJECTED_MISMATCH`.
- Stock bloqueado por compras abandonadas: ajustar `APP_CHECKOUT_RESERVATION_MINUTES`.
- Verificar en la documentación vigente de Wompi: parámetro `expiration-time` del Web Checkout (y si entra en la firma de integridad), reutilización de referencia tras DECLINED (si no se admite: nuevo pago con `attempt_number + 1` y referencia nueva), forma exacta de `GET /transactions?reference=`.
- Rate limit en memoria por instancia (no hay limitador compartido; añadir uno requiere aprobación).
- `BackendApplicationTests` toca la BD del `.env`: no ejecutarla.
- Frontend lo trabaja otra cuenta: el contrato de T1 debe estar en el repo antes de T5.

## Notas del arquitecto
- **N1.** Variables reales: el simulador de suscripciones lee `SIMULATED_PAYMENTS_ENABLED` (`app.billing.simulated-payments-enabled`), no `APP_BILLING_SIMULATED_PAYMENTS_ENABLED`. El simulador del checkout lee `APP_CHECKOUT_SIMULATED_PAYMENTS_ENABLED` con valor por defecto **`true`**; `APP_CHECKOUT_PAYMENT_PROVIDER` lo sustituye y su ausencia en el perfil `prod` debe hacer fallar el arranque.
- **N2.** El "punto de partida" de BD citaba columnas que solo existen en migraciones sin aplicar. El esquema activo es `init.sql`, así que T2 debe tocarlo (no es opcional) y T4 no puede depender de `order_status_history` ni de `cancellation_reasons` (FK) hasta que se apliquen.
- **N3.** `cancellation_reasons` ya tiene `PAYMENT_TIMEOUT` ("El pago no se completó a tiempo"); no se crea `PAYMENT_EXPIRED`.
- **N4.** `payment_events.source` tiene CHECK `CHECKOUT|WEBHOOK|RECONCILIATION|ADMIN`; sin ampliarlo, escribir `REDIRECT_SYNC` falla.
- **N5.** El sync y la conciliación no traen checksum: la columna es nula con único parcial. No se guardan eventos con firma inválida (evita llenar la tabla con peticiones anónimas); por eso desaparece `REJECTED_SIGNATURE`.
- **N6.** El frontend ya tiene `/pedido/:id/resultado`. Como el `redirect-url` va en cada enlace, el backend lo arma con el `orderId` y no hace falta `/checkout/resultado` ni `WOMPI_MARKETPLACE_REDIRECT_URL`.
- **N7.** Hoy el webhook responde 204 y los errores de firma son 400 (`WOMPI_SIGNATURE_MISSING/INVALID`); pasan a 200 y 401. Hoy un pago desconocido es 404 y un monto distinto es 400, lo que provoca reintentos de Wompi; pasan a 200 con el evento registrado.
- **N8.** El mapeo a `payment_statuses` no aplica a suscripciones: `seller_payments.status` es texto con el estado crudo (y `FAILED`) y `payment-result-page.tsx` lo usa.
- **N9.** El plan de checkout (D1) decidió que un rechazo no persiste nada (402). Eso se mantiene con SIMULATED; con WOMPI no es posible porque el pago es asíncrono. El usuario cerró la decisión el 2026-11-04: los pedidos cancelados por pago se muestran al comprador.
- **N10.** `payments.amount` está en pesos (`DECIMAL(12,2)`), a diferencia de `seller_payments.amount_in_cents`. Wompi exige centavos enteros; con `longValueExact` un valor con más decimales lanza un error y no se redondea en silencio.
- **N11.** No hay `@EnableScheduling` ni `@Scheduled` en el backend. Render puede tener más de una instancia; por eso los trabajos usan `SKIP LOCKED` y no ShedLock (que añadiría una dependencia).
- **N12.** El frontend tiene los métodos `SIMULATED_*` escritos en el código (`features/checkout/api/types.ts`); sin un endpoint de opciones no sabe cuándo enviar `WOMPI`.
- **N13.** Dos formas de cuerpo para el mismo `200` (respuesta de inicio o recibo) complican al cliente. Se usa una sola forma y el recibo sale de `GET /me/orders/{id}`.
- **N14.** El backend no escribe `inventory_movements` al descontar stock; si la liberación escribiera uno, el libro quedaría descuadrado. Se aplaza al plan del libro de inventario.
- **N15.** Las fechas del contrato son `LocalDateTime` en hora de Colombia sin desfase (ADR 0002), como el resto de la API. El `expiration-time` que se envía a Wompi se convierte a UTC.
- **N16.** El valor por defecto de `WOMPI_REDIRECT_URL` es `http://localhost:5173/...`; en producción se exige `https`.
