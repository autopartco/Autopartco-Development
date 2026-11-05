# Pagos con Wompi: contrato HTTP para el frontend

- Decisión: `docs/adr/0004-cobros-wompi-produccion.md`. Plan: `docs/architecture/2026-11-04-wompi-produccion-plan.md`.
- Convenciones: `docs/api/conventions.md` (sin envoltorio `data`, `camelCase`) y errores con el formato de `docs/api/error-handling.md` (`{ "error": { code, message, details } }`). El frontend muestra `error.message` y decide solo por `error.code`.
- Fechas: `LocalDateTime` en hora de Colombia, sin desfase (`2026-11-04T10:30:00`), como el resto de la API (ADR 0002).
- Montos: `amountInCents` en centavos enteros de COP, porque así los recibe Wompi. Para mostrar el monto: `amountInCents / 100`.
- Regla de oro: **el frontend nunca decide el estado de un pago.** Los parámetros de la redirección de Wompi (`id`, `env`) son solo pistas que se envían a `sync`.

## 1. Opciones del checkout

`GET /api/v1/marketplace/checkout-options` (requiere sesión)

```json
{
  "provider": "WOMPI",
  "environment": "SANDBOX",
  "methods": [
    { "code": "WOMPI", "name": "Wompi: tarjeta, PSE, Nequi, Bancolombia" }
  ]
}
```

- `provider`: `SIMULATED` | `WOMPI`. Con `SIMULATED`, `methods` trae los `SIMULATED_*` actuales y `environment` es `null`.
- `environment`: `SANDBOX` | `PRODUCTION` | `null`. Con `SANDBOX`, muestra un aviso: "Modo de pruebas: no se hará ningún cobro real."
- Con `WOMPI` no se muestra el selector de resultado simulado.

## 2. Crear el pedido

`POST /api/v1/marketplace/orders` (requiere sesión). El cuerpo no cambia:

```json
{
  "clientRequestId": "6f1c2c1e-7d3b-4c55-9a43-1b2f0c9e5a10",
  "addressId": "uuid",
  "paymentMethod": "WOMPI",
  "simulatedOutcome": null,
  "items": [ { "productId": "uuid", "branchId": "uuid", "quantity": 2 } ]
}
```

- `clientRequestId`: genéralo una sola vez por intento de compra y reutilízalo en los reintentos de red.
- `simulatedOutcome`: solo con `provider = SIMULATED`. Con `WOMPI`, omítelo o envíalo como `null`.

### 2.1 Proveedor SIMULATED (sin cambios)

`201 Created` (o `200` si es una repetición) con el recibo `BuyerOrderResponse` y la cabecera `Location: /api/v1/me/orders/{orderId}`. Un rechazo simulado responde `402 PAYMENT_REJECTED` y no persiste nada.

### 2.2 Proveedor WOMPI

`202 Accepted`, `Location: /api/v1/me/orders/{orderId}`

```json
{
  "orderId": "9b2f6a3e-1c4d-4e8f-a1b2-3c4d5e6f7a8b",
  "orderNumber": 1042,
  "status": "AWAITING_PAYMENT",
  "payment": {
    "reference": "APM-3f9a0c2b7d6e4f1a9b8c7d6e5f4a3b2c",
    "amountInCents": 23000000,
    "currency": "COP",
    "checkoutUrl": "https://checkout.wompi.co/p/?public-key=pub_test_...&currency=COP&amount-in-cents=23000000&reference=APM-...&signature:integrity=...&redirect-url=https%3A%2F%2F<front>%2Fpedido%2F9b2f...%2Fresultado",
    "expiresAt": "2026-11-04T11:00:00"
  }
}
```

- El frontend guarda `orderId` y `reference` (por ejemplo en `sessionStorage`), vacía el carrito y hace `window.location.assign(payment.checkoutUrl)`. No modifica la URL: va firmada.
- El stock queda reservado hasta `expiresAt`.
- **Repetición** con el mismo `clientRequestId` y la misma cuenta: `200 OK` con la misma forma y el estado actual (`status`: `AWAITING_PAYMENT` | `CONFIRMED` | `CANCELLED`). `payment.checkoutUrl` es `null` si el pedido ya no se puede pagar. En ese caso, navega a `/pedido/{orderId}/resultado`.

### 2.3 Errores

| HTTP | `code` | Cuándo | Acción sugerida |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Cuerpo inválido (`details` por campo) | Marcar los campos |
| 400 | `PAYMENT_METHOD_NOT_SUPPORTED` | Método inactivo o de otro proveedor | Recargar `checkout-options` |
| 400 | `SIMULATED_OUTCOME_NOT_ALLOWED` | `simulatedOutcome` enviado con `WOMPI` | Error de integración |
| 400 | `CHECKOUT_DUPLICATE_LINE` | Línea repetida (`details.items`) | Unir las líneas |
| 401 | `UNAUTHORIZED` | Sin sesión | Iniciar sesión y volver |
| 402 | `PAYMENT_REJECTED` | Solo con SIMULATED | Igual que hoy |
| 404 | `ADDRESS_NOT_FOUND` | Dirección ajena o borrada | Elegir otra |
| 404 | `PRODUCT_NOT_FOUND` | Listados no comprables (`details.items`) | Quitarlos del carrito |
| 409 | `INSUFFICIENT_STOCK` | `details.items[{productId, branchId, requested, available}]` | Ajustar las cantidades |
| 409 | `CHECKOUT_LISTING_CHANGED` | Cambió el precio (`details.items`) | Revisar el carrito |
| 409 | `CHECKOUT_DUPLICATE_REQUEST` | `clientRequestId` usado por otra cuenta o en carrera | Recargar y generar uno nuevo |
| 500 | `INTERNAL_SERVER_ERROR` | Error inesperado | Reintentar con el mismo `clientRequestId` |

## 3. Estado del pago del pedido

`GET /api/v1/me/orders/{orderId}/payment` (requiere sesión; solo el dueño del pedido)

```json
{
  "orderId": "9b2f6a3e-1c4d-4e8f-a1b2-3c4d5e6f7a8b",
  "reference": "APM-3f9a0c2b7d6e4f1a9b8c7d6e5f4a3b2c",
  "status": "PENDING",
  "providerStatus": "PENDING",
  "orderStatus": "AWAITING_PAYMENT",
  "amountInCents": 23000000,
  "currency": "COP",
  "checkoutUrl": "https://checkout.wompi.co/p/?...",
  "expiresAt": "2026-11-04T11:00:00",
  "paidAt": null
}
```

- `status`: `PENDING` | `APPROVED` | `REJECTED` | `CANCELLED` | `EXPIRED`. Es el estado de Autopartco y el único que decide la interfaz.
- `providerStatus`: estado crudo de Wompi (`PENDING`, `APPROVED`, `DECLINED`, `ERROR`, `VOIDED`) o `null`. Solo sirve para el soporte.
- `orderStatus`: `AWAITING_PAYMENT` | `CONFIRMED` | `CANCELLED` | (estados posteriores de envío).
- `checkoutUrl`: no es `null` solo mientras se puede pagar (`orderStatus = AWAITING_PAYMENT` y antes de `expiresAt`). Sirve para el botón "Intentar de nuevo".
- Pedidos de SIMULATED: responde con `checkoutUrl` y `expiresAt` en `null`.

Errores: `401 UNAUTHORIZED` y `404 ORDER_NOT_FOUND`. Un pedido ajeno también responde `404`: no se distingue de uno inexistente.

## 4. Sincronizar al volver de Wompi

`POST /api/v1/me/orders/{orderId}/payment/sync` (requiere sesión; solo el dueño)

```json
{ "transactionId": "1234-1730733600-49201" }
```

- `transactionId`: el parámetro `id` de la redirección (`/pedido/{orderId}/resultado?id=…&env=test`). Validación: obligatorio, `^[A-Za-z0-9-]{1,64}$`.
- El backend consulta la transacción en Wompi con su llave privada. Solo la aplica si su referencia es la del pedido. Responde `200` con el mismo cuerpo que el `GET` (§3).
- Si Wompi no responde, devuelve `200` con el estado actual sin cambios. El polling continúa.

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | `transactionId` ausente o con formato inválido |
| 401 | `UNAUTHORIZED` | Sin sesión |
| 404 | `ORDER_NOT_FOUND` | Pedido inexistente o ajeno |
| 404 | `PAYMENT_TRANSACTION_NOT_FOUND` | Wompi no conoce esa transacción |
| 409 | `PAYMENT_REFERENCE_MISMATCH` | La transacción pertenece a otra referencia |
| 429 | `PAYMENT_SYNC_RATE_LIMITED` | Demasiadas sincronizaciones; seguir solo con el `GET` |

## 5. Pago de suscripción (portal)

`GET /api/v1/stores/{storeId}/subscription/payments/{reference}` (requiere sesión; OWNER del almacén)

`POST /api/v1/stores/{storeId}/subscription/payments/{reference}/sync` con `{ "transactionId": "…" }` (mismas reglas que en §4)

Las dos responden `200` con `SubscriptionPaymentView`, la misma forma que cada elemento de `GET …/subscription/payments`:

```json
{
  "id": "uuid",
  "reference": "APC-…",
  "provider": "WOMPI",
  "planCode": "PRO",
  "planName": "Pro",
  "months": 3,
  "amountCop": 270000,
  "currency": "COP",
  "status": "PENDING",
  "createdAt": "2026-11-04T10:30:00",
  "paidAt": null,
  "periodStartsAt": null,
  "periodEndsAt": null
}
```

- En suscripciones, `status` conserva el estado crudo de Wompi: `PENDING` | `APPROVED` | `DECLINED` | `ERROR` | `VOIDED`. Es compatible con `payment-result-page.tsx`. No existe `EXPIRED` porque el pago de suscripción no reserva nada.
- Errores: `401 UNAUTHORIZED`, `403 FORBIDDEN` (no es OWNER), `404 SELLER_PAYMENT_NOT_FOUND` (referencia inexistente o de otro almacén) y los de §4 para `sync`.
- `POST …/subscription/checkout` no cambia.

## 6. Rutas de redirección del frontend

| Producto | Ruta (la arma el backend en `redirect-url`) | Parámetros que agrega Wompi |
|---|---|---|
| Marketplace | `/pedido/{orderId}/resultado` (ruta existente) | `id` (transacción), `env` (`test` en sandbox) |
| Suscripción | `/portal/pago/resultado` (ruta existente) | `id`, `env` |

Comportamiento de la página de resultado:

1. Si hay `id`, llama una vez a `sync`. Si no hay, va directo al paso 2.
2. Consulta el `GET` cada 3 s durante 60 s como máximo. Se detiene cuando el estado es final: `APPROVED`, `EXPIRED`, o `REJECTED`/`CANCELLED` con `checkoutUrl = null`.
3. Si se cumple el tiempo, muestra el estado pendiente con el botón "Volver a verificar", que reinicia el ciclo.
4. Necesita sesión (`RequireSession`). Si no la hay, inicia sesión y vuelve a la misma ruta con sus parámetros.

## 7. Textos sugeridos por estado (marketplace)

| `status` / `orderStatus` | Título | Descripción | Acción |
|---|---|---|---|
| `PENDING` / `AWAITING_PAYMENT` | Estamos confirmando tu pago | Wompi aún no confirma el pago. Con PSE o Nequi puede tardar unos minutos. | "Volver a verificar"; "Ir a pagar" si hay `checkoutUrl` |
| `APPROVED` / `CONFIRMED` | ¡Pago aprobado! | Tu pedido #{orderNumber} está confirmado. Los almacenes ya lo están preparando. | "Ver mi pedido" (`/mis-pedidos/{orderId}`) |
| `REJECTED` / `AWAITING_PAYMENT` | El pago fue rechazado | No se hizo ningún cobro. Reservamos tus repuestos hasta las {hora de expiresAt}. | "Intentar de nuevo" (`checkoutUrl`) |
| `CANCELLED` / `AWAITING_PAYMENT` | El pago fue anulado | No se hizo ningún cobro. Puedes intentarlo de nuevo antes de las {hora de expiresAt}. | "Intentar de nuevo" |
| `EXPIRED` / `CANCELLED` | Se venció el tiempo para pagar | No recibimos el pago a tiempo y liberamos los repuestos. Si ya pagaste, te contactaremos para devolverte el dinero. | "Volver al carrito" |
| `APPROVED` / `CANCELLED` | Recibimos tu pago tarde | El pedido ya se había cancelado. Te contactaremos para devolverte el dinero. | "Contactar soporte" |

En "Mis pedidos" (`GET /me/orders` y `GET /me/orders/{orderId}`), **los pedidos con pago Wompi fallido o vencido se muestran** (decisión del usuario, 2026-11-04):

| `status` del pedido | Etiqueta | Texto del detalle |
|---|---|---|
| `AWAITING_PAYMENT` | Pago pendiente | Completa el pago antes de las {hora de expiresAt} para confirmar tu pedido. |
| `CANCELLED` (pago `EXPIRED`, motivo `PAYMENT_TIMEOUT`) | Cancelado: pago no completado | No recibimos el pago a tiempo y liberamos los repuestos. No se hizo ningún cobro. |
| `CANCELLED` (pago `APPROVED` tardío, `requires_manual_refund`) | Cancelado: pago no completado | Recibimos tu pago después de cancelar el pedido. Te contactaremos para devolverte el dinero. |

Para distinguir los dos casos de `CANCELLED`, el detalle consulta `GET /me/orders/{orderId}/payment` (`status`).

## 8. Webhook (solo backend, no lo llama el frontend)

`POST /api/v1/webhooks/wompi` (público; la autenticidad viene de la firma del evento):

- `401`: firma ausente o inválida.
- `200`: evento procesado, duplicado o inaplicable (se registra para revisión).
- `500`: error interno (Wompi reintenta).

La URL se configura en el panel de Wompi, una por entorno.

## 8a. Portal del almacén: ventas de pedidos sin pagar

- Los listados, el detalle y los resúmenes de ventas del almacén no incluyen ventas de pedidos `AWAITING_PAYMENT` ni de pedidos cancelados por `PAYMENT_TIMEOUT`. El detalle de una de esas ventas responde `404 SELLER_ORDER_NOT_FOUND`, igual que una venta ajena.
- Cambiar el estado de una de esas ventas, o crear o mover su envío, responde `409 SALE_AWAITING_PAYMENT` ("El pedido todavía no está pagado.").

## 9. Seguridad para el frontend

- Solo `VITE_*` públicas. La llave pública de Wompi no hace falta en el frontend porque va dentro de `checkoutUrl`. Nunca se usan la llave privada ni los secretos de integridad y de eventos.
- No construyas ni modifiques URLs de checkout. No muestres un pago como aprobado con base en `env`, `id` u otros parámetros de la redirección.
