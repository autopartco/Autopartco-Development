# ADR 0004 — Cobros reales con Wompi (marketplace y suscripciones)

- Estado: **Propuesto** (pendiente de aprobación del usuario; el recaudo para terceros es un riesgo asumido por el usuario, §6.1)
- Fecha: 2026-11-04
- Autor: agente `arquitecto`
- Relacionado: `docs/architecture/2026-11-04-wompi-produccion-plan.md`, `docs/api/payments-wompi.md`, `docs/architecture/2026-10-14-mvp-checkout-plan.md` (D1), ADR 0001, ADR 0002

## 1. Contexto

- Las suscripciones ya cobran con Wompi Web Checkout: `core.billing.SubscriptionCheckout` firma el enlace (`APC-…`) y `core.billing.web.WompiWebhookController` → `WompiWebhookProcessor` aplica el evento sobre `seller_payments`. No distingue el entorno, no registra los eventos y no concilia. Un evento inaplicable responde 4xx, así que Wompi lo reintenta.
- El checkout del marketplace (`marketplace.checkout.PlaceMarketplaceOrder`) cobra de forma síncrona con `SimulatedCheckoutPaymentGateway` dentro de la transacción del pedido. Un pago real con Wompi es asíncrono (PSE, Nequi y los pagos pendientes), así que ese flujo no sirve.
- Wompi admite una sola URL de eventos por cuenta y entorno.
- El esquema activo es `database/init.sql`; las columnas de pago de las migraciones V202610141020/V202610141310 no están aplicadas (ADR 0001).

## 2. Decisión

1. **Web Checkout redirigido** para los dos productos. El backend arma el enlace firmado (referencia, monto en centavos, moneda, `redirect-url`, `expiration-time` si Wompi lo admite). El navegador va a Wompi y vuelve al frontend. El frontend nunca ve datos de tarjeta ni llaves privadas.
2. **Módulo `core.payments`** con `WompiProperties`, `WompiClient`, el verificador de firma, el webhook único y un SPI `WompiTransactionHandler { supports(reference); apply(tx, source) }`. El enrutamiento va por prefijo: `APC-` → `core.billing` (suscripción) y `APM-` → `marketplace.checkout` (pedido). `core.payments` no depende de billing ni de checkout.
3. **Separación de entornos**: `WOMPI_ENVIRONMENT=SANDBOX|PRODUCTION`. Al arrancar se valida que los prefijos de las llaves (`pub_test_`/`prv_test_` frente a `pub_prod_`/`prv_prod_`) coincidan con el entorno, que el perfil `prod` no tenga simuladores activos y que, con WOMPI, no falte ningún secreto. Cada pago y cada evento guardan `environment`; un evento de otro entorno se rechaza.
4. **Flujo del pedido en dos fases** (solo con `APP_CHECKOUT_PAYMENT_PROVIDER=WOMPI`; SIMULATED se mantiene igual).
   - Inicio: en una transacción, sin red, se descuenta el stock como reserva y se crean el pedido `AWAITING_PAYMENT`, las ventas `PENDING` y el pago `PENDING`, con `APM-…`, `idempotency_key = clientRequestId` y `expires_at = now + APP_CHECKOUT_RESERVATION_MINUTES` (30). Responde `202`.
   - Aprobación dentro del plazo: el pago pasa a `APPROVED` y el pedido a `CONFIRMED`.
   - Vencimiento: el pago pasa a `EXPIRED` y el pedido a `CANCELLED` con el motivo `PAYMENT_TIMEOUT`, y se devuelve el stock.
5. **DECLINED no cancela.** El pago queda `REJECTED`/`CANCELLED` y el pedido sigue `AWAITING_PAYMENT` hasta vencer: el comprador puede reintentar en el checkout con la misma referencia. `REJECTED → APPROVED` es válido dentro del plazo.
6. **Los pagos tardíos se marcan, no se revierten.** Un APPROVED que llega con el pedido ya vencido o cancelado deja el pago `APPROVED` con `requires_manual_refund = true`, el pedido sigue `CANCELLED` y se registra un log `ERROR`. El reembolso es manual porque los reembolsos quedan fuera de alcance.
7. **Política de respuesta del webhook**:

   | Caso | HTTP | Registro |
   |---|---|---|
   | Firma ausente o inválida | 401 | No se guarda; log `WARN` |
   | Evento ya registrado (mismo checksum) | 200 | `DUPLICATE` sin efectos |
   | Referencia desconocida | 200 | `UNKNOWN_REFERENCE`, log `ERROR` |
   | Monto, moneda o entorno distintos | 200 | `REJECTED_MISMATCH`, log `ERROR` |
   | Estado intermedio (`PENDING`) o que no cambia nada | 200 | `IGNORED` |
   | Aplicado | 200 | `APPLIED` |
   | Error interno (BD, etc.) | 500 | Transacción revertida; Wompi reintenta |

   La verificación es idempotente por `(provider, checksum)` en `payment_provider_events` y por `(provider, provider_transaction_id)` en `payments`, siempre con bloqueo de fila del pago (`FOR UPDATE`). Si una aprobación y el vencimiento llegan a la vez, gana solo uno.
8. **Conciliación por API como segunda fuente.** La redirección nunca es prueba de pago.
   - `POST …/payment/sync` con el `id` de la redirección consulta `GET /v1/transactions/{id}` (llave privada) y aplica el resultado con `source = REDIRECT_SYNC` solo si la referencia coincide.
   - Un trabajo `@Scheduled` cada 5 min toma con `FOR UPDATE SKIP LOCKED` los pagos `PENDING`/`REJECTED` no vencidos de más de 2 min, consulta `GET /v1/transactions?reference=` y aplica con `RECONCILIATION`. Después vence los expirados.
   - Las tres fuentes usan el mismo handler.

### Mapeo de estados Wompi → `payment_statuses` (tabla `payments`)

| Wompi | `payment_statuses` | Efecto en el pedido |
|---|---|---|
| `APPROVED` | `APPROVED` | `CONFIRMED` si está dentro del plazo; si no, `requires_manual_refund` |
| `DECLINED` | `REJECTED` | Sigue `AWAITING_PAYMENT` |
| `ERROR` | `REJECTED` | Sigue `AWAITING_PAYMENT` |
| `VOIDED` | `CANCELLED` | Sigue `AWAITING_PAYMENT` |
| `PENDING` | sin cambio | Sin cambio |
| (vencido por Autopartco) | `EXPIRED` (nuevo) | `CANCELLED`, motivo `PAYMENT_TIMEOUT` |

El estado crudo se guarda en `payments.provider_status` y en `payment_provider_events.provider_status`. Las suscripciones (`seller_payments.status`) siguen guardando el estado crudo de Wompi porque el portal lo usa.

## 3. Alternativas descartadas

- **Widget o API de transacciones directa**: exigen tokenizar y aceptar términos por API, y acercan el alcance PCI. El Web Checkout ya funciona en suscripciones.
- **Un webhook por producto**: Wompi solo permite una URL por entorno.
- **Descontar el stock al aprobar**: permite vender stock que ya no existe mientras el comprador paga.
- **Reservar con `stock_reservations`/`reserved_stock`**: es el modelo de destino (V202610141230), pero no está en el esquema activo. Se usa el descuento atómico actual y se libera al vencer.
- **Cancelar con DECLINED**: obliga a rehacer el carrito ante un rechazo que se podía reintentar.
- **Reembolso automático de pagos tardíos**: los reembolsos están fuera de alcance.
- **Responder 4xx a eventos inaplicables**: Wompi los reintentaría sin fin y nadie los revisaría.
- **Confiar solo en el webhook o en la redirección**: el webhook puede perderse y la redirección se puede falsificar.
- **ShedLock o una cola** para los trabajos: añaden dependencias; con `SKIP LOCKED` basta.

## 4. Consecuencias

- Cambia la decisión D1 del plan de checkout, solo para WOMPI. **El usuario lo confirmó el 2026-11-04 ("Sí, mostrarlos").** Un pedido cuyo pago Wompi falla o vence termina `CANCELLED` (motivo `PAYMENT_TIMEOUT`). El comprador lo ve en "Mis pedidos" como "Cancelado: pago no completado", y también aparece en el portal. Con SIMULATED, el `402` sin persistir se mantiene.
- Hay contratos nuevos: `202` en `POST /marketplace/orders`, `GET/POST sync` de pago del pedido y de la suscripción, y `GET /marketplace/checkout-options` (`docs/api/payments-wompi.md`).
- Esquema (T2): `AWAITING_PAYMENT`, `EXPIRED`, `WOMPI`; columnas nuevas en `payments` y `seller_payments`; la tabla `payment_provider_events` es de solo inserción. Va en `init.sql` y en la migración versionada.
- El stock queda bloqueado hasta 30 min por compra abandonada.
- Los pagos marcados con `requires_manual_refund` necesitan seguimiento manual hasta que exista `operations.payments`.
- El rate limit del `sync` es en memoria y por instancia.
- Todo el dinero entra en la cuenta Wompi de Autopartco. La dispersión a los almacenes queda fuera de alcance y pendiente.

## 5. Requiere aprobación

- Cambio de esquema en `init.sql` y en la migración, y su aplicación en cualquier BD.
- Llaves y secretos de producción en Render, y la URL de eventos en el panel de Wompi.
- Despliegue (`git tag` y push) y la prueba de humo con un pago real.
- El cambio de D1 descrito en §4: **aprobado por el usuario el 2026-11-04**; ya no está pendiente.

## 6. Pendientes y riesgos del usuario (no técnicos)

1. **Recaudo para terceros: riesgo asumido por el usuario.** Decisión del usuario del 2026-11-04 ("Wompi desde ya"): el checkout del marketplace usa `APP_CHECKOUT_PAYMENT_PROVIDER=WOMPI` en producción desde el despliegue, y el usuario se hace responsable de validar el recaudo para terceros. Ya no es un bloqueo técnico ni una condición para activar WOMPI.

   Cobrar las ventas de los almacenes en la cuenta de Autopartco equivale a actuar como agregador o recaudador. Se sigue recomendando validar, lo antes posible:
   - con **Wompi**, que la cuenta y el contrato lo permiten;
   - con el **contador**, el tratamiento: ingresos para terceros, retenciones (ReteFuente, ReteICA, ReteIVA), GMF y quién emite la factura electrónica de cada venta.

   Si alguna validación sale negativa, el cobro del marketplace se suspende marcando inactivo el método `WOMPI` en `payment_methods` (`POST /marketplace/orders` responde `PAYMENT_METHOD_NOT_SUPPORTED`). Es un comando contra la BD de producción y requiere autorización. Volver a `SIMULATED` no sirve: el perfil `prod` no arranca con el simulador activo. Las suscripciones no tienen este riesgo porque son ingreso propio.
2. Definir **quién gestiona y con qué plazo** los reembolsos de los pagos marcados con `requires_manual_refund`.
3. Verificar en la documentación vigente de Wompi:
   - el parámetro `expiration-time` y si forma parte de la firma de integridad;
   - si una referencia se puede reutilizar después de un DECLINED (si no, se crea un pago nuevo con `attempt_number + 1`);
   - la forma exacta de `GET /v1/transactions?reference=`.
