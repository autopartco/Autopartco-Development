# Plan: Marketplace (producto A) completo en el frontend + ambientes demo y producción

- Fecha: 2026-10-14
- Base: rama `feature/checkout-frontend` (PR #71: carrito, `/checkout`, `/pedido/:id/resultado` ya hechos)
- Estructura: (1) objetivo y alcance, (2) estrategia de ejecución, (3) mapa de pantallas, (4) **contratos con el backend**, (5) decisiones, (6) tareas, (7) ambientes, (8) riesgos.
- Este documento es el `PLAN.md` de la iniciativa. Cada tarea de §6 se puede copiar a un chat nuevo sin depender de esta conversación.

---

## 1. Objetivo y alcance

**Objetivo.** Que el comprador tenga en el frontend todo lo que un marketplace de autopartes debe ofrecer (cuenta y perfil, pedidos, garaje de vehículos, favoritos y alertas, reseñas, tiendas, cotizaciones, ayuda), y que el backend lo implemente por tandas contra los contratos de §4. Entretanto, todo funciona con datos simulados y, en el ambiente **demo**, mezclado con el backend real.

**Dentro del alcance (producto A: Marketplace)**

- Cuenta: perfil, direcciones, seguridad y sesiones, notificaciones, privacidad y datos personales (Ley 1581).
- Compra: mis pedidos y su seguimiento, ofertas por tienda en el producto, mejoras de carrito y de checkout (entrega a domicilio o recogida).
- Personalización: garaje, "¿sirve para mi carro?", favoritos, alertas de precio y de stock, búsquedas guardadas, vistos recientemente, comparador.
- Confianza: página de tienda, reseñas, reportes.
- Servicios: **consultas y cotizaciones a una tienda** (botón "Consultar / Cotizar"), cotizaciones de varios repuestos para talleres, ayuda y soporte básico.
- Transversal: analítica de eventos con consentimiento, banner de consentimiento, pruebas E2E, revisión de accesibilidad y seguridad.
- Ambientes: **demo** (backend + BD, con las funciones que falten en modo simulado) y **producción** (configuración, guardas de build y lista de verificación). Ya entregados en esta rama: `*.env*.example`, `application-prod.yaml`, `docs/deploy/ENVIRONMENTS.md`.

**Fuera del alcance**

- Portal del almacén (producto B), inteligencia automotriz (C) y operaciones internas (D). Solo se dejan los ganchos (por ejemplo, las cotizaciones que los almacenes responderán desde su portal). Las pantallas del MVP del portal (F8–F13) y la administración de solicitudes (F19) siguen en `docs/architecture/mvp-backlog.md` y se planifican aparte.
- Wompi para pedidos, ERP (Alegra/Siigo), IA, pagos reales, cupones y programas de fidelización con dinero.
- Implementar el backend de §4: lo hace el usuario; aquí solo se define el contrato.

---

## 2. Estrategia de ejecución: frontend primero, backend después

1. **Un servicio por función**, con la interfaz en `api/types.ts` y dos implementaciones (`mock` y `http`), igual que `features/checkout/api`. Los tipos de `api/types.ts` **son** el contrato de §4.
2. **Fuente híbrida por función** (decisión D1). Hoy `VITE_MARKETPLACE_DATA_SOURCE` es global. Se añade `VITE_MOCK_FEATURES` (lista separada por comas, p. ej. `garage,favorites,reviews`): con la fuente global `http`, esas funciones siguen en simulado hasta que el backend las implemente. El usuario quita nombres de la lista a medida que termina cada endpoint. Una función simulada dentro de un ambiente `http` muestra una etiqueta honesta ("Datos de demostración").
3. **Nada se inventa**: sin stock, precios ni compatibilidades que el servidor no haya devuelto (blueprint §5.4). Los intervalos de mantenimiento los define el usuario.
4. **El frontend nunca decide** autorización, precios, stock ni estado de pago.
5. **Orden por capas** (regla del repositorio): `database` → `backend` → `frontend` → `qa-seguridad`. Como el frontend va adelantado, cada tarea de §6 declara qué endpoint espera y su sustituto simulado.

Criterios comunes **C0** (aplican a todas las tareas de frontend y no se repiten en cada una):

- `cd frontend && npm run build && npm run lint` sin errores; sin dependencias nuevas (los componentes que falten se escriben sobre `@base-ui/react`, que ya está instalado).
- Estados de carga (skeleton), vacío, error con "Reintentar" y éxito; `role="alert"` en errores; foco movido al error o al resultado.
- Responsive desde 360 px sin scroll horizontal; objetivos táctiles de 44 px; teclado completo; contraste AA con los tokens de `index.css` (carbón, papel, hilo rojo).
- Microcopy en español de Colombia, accionable. Moneda COP con `formatCOP`. Teléfonos `+57`. Ciudades y departamentos DIVIPOLA.
- Servicio con implementación `mock` y `http`, selección con `getXService()` por función (T1) y prueba manual en `mock` y, cuando el endpoint exista, en `http`.
- Tras modificar código: `graphify update .`.

---

## 3. Mapa de pantallas

Prioridad **P0** imprescindible para la demo, **P1** recomendable, **P2** opcional. "Existe" = el backend ya lo expone hoy.

### 3.1 Cuenta (`/cuenta/*`, requiere sesión)

| Ruta | Qué ofrece | Backend hoy | Backend nuevo (§4) | Prior. |
|---|---|---|---|---|
| `/cuenta` | Resumen: saludo, pedidos recientes, notificaciones sin leer, accesos rápidos, avance del perfil | `GET /me/profile`, `/me/notifications` | `GET /me/orders` (C6) | P0 |
| `/cuenta/perfil` | Ver y editar nombre, apellido, identificación, fecha de nacimiento y teléfono; ver el correo y si está verificado; reenviar verificación; foto | **Existe** `GET/PATCH /me/profile` | `emailVerified`, `avatarUrl`, `createdAt` en el perfil; `POST /me/email/verification`; `PUT /me/avatar` (C1) | P0 |
| `/cuenta/direcciones` | Libreta de direcciones: crear, editar, borrar, marcar predeterminada, etiqueta, selector de departamento y ciudad | **Existe** `GET/POST/PUT/DELETE /me/addresses` | `PATCH /me/addresses/{id}/default` (C2) | P0 |
| `/cuenta/seguridad` | Cambiar contraseña; sesiones activas con dispositivo, IP y fecha; cerrar una o todas las demás | **Existe** `GET /me/sessions`, `DELETE /me/sessions/{id}`, `POST /auth/logout/others` | `POST /me/password` (C3); `GET /me/security-events` (P2) | P0 |
| `/cuenta/notificaciones` | Bandeja con filtro (todas, sin leer), marcar leída, marcar todas, enlace a lo notificado; **preferencias** por tipo y canal | **Existe** `GET /me/notifications`, `PATCH …/{id}/read` | `unread-count`, `read-all`, `type`/`link`, `GET/PUT /me/notification-preferences` (C4) | P0 / P1 |
| `/cuenta/privacidad` | Descargar mis datos, consentimientos (marketing, analítica), eliminar la cuenta con confirmación | — | `GET /me/data-export`, `GET/PUT /me/consents`, `DELETE /me` (C5) | P1 |
| `/mis-pedidos`, `/mis-pedidos/:id` | Lista con filtros y búsqueda; detalle con línea de tiempo, envío y guía; cancelar, comprar de nuevo, comprobante imprimible, reportar un problema, reseñar | — | `GET /me/orders`, `GET /me/orders/{id}`, `POST …/cancel`, `POST …/issues` (C6) | P0 |
| `/cuenta/garaje`, `/cuenta/garaje/:id` | Vehículos guardados (apodo, marca, modelo, año, versión, placa opcional), vehículo principal, historial de mantenimiento y recordatorios definidos por el usuario | — | `/me/vehicles`, `…/maintenance`, `…/reminders` (C8) | P1 / P2 |
| `/cuenta/favoritos` | Repuestos guardados con precio actual; mover al carrito | — | `/me/favorites` (C10) | P1 |
| `/cuenta/alertas` | Alertas de precio (baja de X) y de "vuelve a estar disponible" | — | `/me/alerts` (C11) | P1 |
| `/cuenta/busquedas` | Búsquedas guardadas (con aviso opcional) | — | `/me/saved-searches` (C12) | P2 |
| `/cuenta/resenas` | Mis reseñas: editar, borrar | — | `/me/reviews` (C13) | P2 |
| `/cuenta/consultas`, `/:id` | Conversaciones con tiendas: estado (abierta, respondida, cerrada), hilo de mensajes, cotización de la tienda, cerrar | — | `/me/inquiries` (C19) | P1 |
| `/cuenta/cotizaciones`, `/:id` | Pedir cotización de varios repuestos a tiendas; comparar ofertas; aceptar una | — | `/me/quote-requests` (C15) | P2 |
| `/cuenta/soporte` | Tickets: crear, ver hilo, adjuntar número de pedido | — | `/me/support-tickets` (C16) | P2 |

### 3.2 Descubrimiento, producto, tienda y carrito (públicas)

| Ruta o zona | Qué suma | Backend hoy | Backend nuevo | Prior. |
|---|---|---|---|---|
| Producto `/producto/:id` | **Botón "Consultar / Cotizar"** (con el repuesto ya referenciado; si está agotado pasa a ser la acción principal junto a "Avisarme"); selector **"Otras tiendas con este repuesto"** (hoy solo se ve la oferta más barata); "¿Sirve para mi carro?" con estado de evidencia; favorito; alerta de precio o stock; compartir; reportar publicación; reseñas | `GET /marketplace/products/{id}` | `GET …/{id}/listings` (C9), `…/fitment` (C8), reseñas (C13), reportes (C14) | P0 / P1 |
| Búsqueda `/buscar` | Sugerencias al escribir, historial reciente (**solo local**), guardar búsqueda, filtrar por "mi vehículo", orden por distancia | `GET /marketplace/products`, `/product-options` | `GET /marketplace/search/suggestions` (C12) | P1 |
| Comparador `/comparar` | Hasta 4 repuestos lado a lado (precio, tienda, especificaciones, compatibilidad) | usa los endpoints de producto | — | P1 |
| Inicio `/` | "Sigue donde quedaste" (vistos), "Para tu vehículo", ofertas reales, tiendas | existentes | opcional `GET /marketplace/promotions` | P1 |
| Tienda `/tienda/:id` (F7) | **"Escribir a la tienda"**, perfil, verificación real, descripción, sucursales con horario y mapa, contacto, políticas, catálogo con filtros, reseñas, seguir tienda, reportar | `GET /marketplace/stores` | `GET /marketplace/stores/{id}`, `/me/followed-stores` (C14) | P0 / P1 |
| Carrito `/carrito` | **"Coordinar envío con esta tienda"** por tienda (la consulta lleva las líneas del carrito), validar precio y disponibilidad al abrir, guardar para después, nota por tienda, compartir cotización | — | `POST /marketplace/cart/validate` (C9) | P1 |
| Checkout `/checkout` | Entrega a domicilio o **recoger en tienda**, notas de entrega, datos de factura (NIT) | `POST /marketplace/orders` | campos nuevos en B1 (C17) | P1 |
| Ayuda `/ayuda` | Preguntas frecuentes, cómo comprar, devoluciones, contacto | contenido estático | — | P1 |

### 3.3 Transversal

Consentimiento de cookies y *habeas data* (P1), analítica de eventos (P1, C18), SEO (`<title>` y metadatos por página), página de error y de "sin conexión", y registro de vistos recientemente (local).

---

## 4. Contratos con el backend (lo que el usuario implementará)

### 4.1 Convenciones (iguales a las del checkout)

- Prefijo `/api/v1`, `Authorization: Bearer <accessToken>` en `/me/**`. Respuestas exitosas **sin envoltorio** `data`. Errores `{ "error": { "code", "message", "details" } }` con `message` seguro de mostrar. Dinero como número en COP. Fechas `LocalDateTime` sin zona.
- **Paginación** (decisión D3): `?page=1&limit=20` (máx. 100) y respuesta `{ items, page, limit, total, totalPages }`.
- Todo recurso de `/me/**` es del usuario autenticado; uno ajeno responde **404**, nunca 403.
- Campos desconocidos en el cuerpo → 400 `INVALID_REQUEST_BODY` (ya configurado).
- Cada tanda del backend debe pasar por el agente `arquitecto` (ADR si cambia una decisión). Los tipos de abajo se copian a `frontend/src/features/<x>/api/types.ts`.

```ts
export interface Page<T> { items: T[]; page: number; limit: number; total: number; totalPages: number }
```

### C1 Perfil (existe + ampliaciones)

```ts
// Existe: GET /me/profile y PATCH /me/profile (todos los campos opcionales; al menos uno)
export interface ProfileDto {
  accountId: string; personId: string; email: string
  name: string; surname: string; identification: string
  dateBirth: string          // 'AAAA-MM-DD'
  numberPhone: string
  // NUEVO (P1):
  emailVerified?: boolean; avatarUrl?: string | null; createdAt?: string
}
// NUEVO: POST /me/email/verification  -> 204   (reenvía el correo; 429 si se abusa)
// NUEVO (P2): PUT /me/avatar (multipart, imagen PNG/JPEG ≤ 2 MB) -> { avatarUrl }; DELETE /me/avatar -> 204
```

### C2 Direcciones (existe)

`GET/POST/PUT/DELETE /me/addresses` con `PersonAddressDto` y `PersonAddressRequestDto` (ver plan del checkout §2.5). **Nuevo (P1):** `PATCH /me/addresses/{id}/default` → `PersonAddressDto`. Borrar la predeterminada: el servidor elige otra o ninguna.

### C3 Seguridad

```ts
// Existe: GET /me/sessions -> AccountSession[]; DELETE /me/sessions/{id} -> 204; POST /auth/logout/others
export interface AccountSession { sessionId: string; ipAddress: string; userAgent: string; createdAt: string; expiresAt: string; current: boolean }
// NUEVO (P0): POST /me/password  { currentPassword: string; newPassword: string; signOutOthers?: boolean } -> 204
//   400 VALIDATION_ERROR (misma política que el registro) · 400 CURRENT_PASSWORD_INVALID
// NUEVO (P2): GET /me/security-events -> Page<{ at: string; type: 'LOGIN'|'LOGIN_FAILED'|'PASSWORD_CHANGED'|'SESSION_REVOKED'; ipAddress: string; userAgent: string }>
```

### C4 Notificaciones (existe + ampliaciones)

```ts
// Existe: GET /me/notifications -> NotificationDto[]; PATCH /me/notifications/{id}/read
export interface NotificationDto {
  id: string; title: string; message: string; read: boolean; createdAt: string
  type?: 'ORDER_UPDATE'|'PRICE_ALERT'|'STOCK_ALERT'|'QUOTE_OFFER'|'INQUIRY_REPLY'|'SECURITY'|'PROMOTION'|'SYSTEM'  // NUEVO
  link?: string | null       // NUEVO: ruta interna, p. ej. '/mis-pedidos/3f9b…'
}
// NUEVO (P0): GET /me/notifications?unread=true&page=1&limit=20 -> Page<NotificationDto>
//             GET /me/notifications/unread-count -> { count: number }
//             POST /me/notifications/read-all -> 204
// NUEVO (P1): GET/PUT /me/notification-preferences
export interface NotificationPreferences {
  types: Record<'ORDER_UPDATE'|'PRICE_ALERT'|'STOCK_ALERT'|'QUOTE_OFFER'|'INQUIRY_REPLY'|'PROMOTION', { inApp: boolean; email: boolean }>
  // SECURITY y SYSTEM no se pueden apagar.
}
```

### C5 Privacidad (Ley 1581)

```ts
// GET /me/data-export -> 200 application/json (adjunto) con perfil, direcciones, pedidos, garaje, favoritos, reseñas
// GET/PUT /me/consents -> { marketing: boolean; analytics: boolean; updatedAt: string }
// DELETE /me  { password: string } -> 204   (anonimiza datos personales; conserva pedidos por obligación contable)
//   409 ACCOUNT_HAS_OPEN_ORDERS  details: { orderIds: string[] }
```

### C6 Pedidos del comprador (B2) y acciones

```ts
// GET /me/orders?status=&q=&page=&limit=  -> Page<OrderSummaryDto>      (orden: más reciente primero)
export interface OrderSummaryDto { orderId: string; orderNumber: number; status: string; createdAt: string; total: number; itemCount: number; storeNames: string[]; firstItemImageUrl?: string | null }
// GET /me/orders/{id} -> OrderDetailDto = BuyerOrderDto (checkout plan §2.5) + :
export interface OrderDetailDto extends BuyerOrderDto {
  timeline: Array<{ at: string; status: string; label: string; saleId?: string }>
  sales: Array<BuyerOrderDto['sales'][number] & { shipment?: ShipmentDto | null; pickup?: { address: string; readyAt: string | null } | null }>
  cancellable: boolean       // lo decide el servidor
}
export interface ShipmentDto { carrier: string | null; trackingNumber: string | null; trackingUrl: string | null; status: string; estimatedDelivery: string | null; events: Array<{ at: string; status: string; description: string }> }
// POST /me/orders/{id}/cancel  { reason?: string }  -> OrderDetailDto   · 409 ORDER_NOT_CANCELLABLE
// POST /me/orders/{id}/issues  { saleId?: string; category: 'NOT_RECEIVED'|'WRONG_ITEM'|'DAMAGED'|'OTHER'; description: string } -> 201 { issueId, status }
```

Los códigos de estado los define el servidor (tabla `states`); el frontend mapea los conocidos con `statusLabel()` y muestra el resto tal cual. La máquina de estados no se decide en el cliente.

### C8 Garaje, mantenimiento y compatibilidad

```ts
export interface VehicleDto { id: string; nickname: string; catalogVehicleId?: string | null; make: string; model: string; year: number; version?: string | null; plate?: string | null; mileageKm?: number | null; isPrimary: boolean; notes?: string | null }
// GET /me/vehicles -> VehicleDto[] · POST -> 201 · PUT /me/vehicles/{id} · DELETE -> 204 · PATCH /me/vehicles/{id}/primary
// P2: GET/POST /me/vehicles/{id}/maintenance  { type: string; performedAt: string; mileageKm?: number; notes?: string; orderId?: string; productId?: string }
// P2: GET/POST /me/vehicles/{id}/reminders    { title: string; dueDate?: string; dueMileageKm?: number }   // solo los define el usuario
// "¿Sirve para mi carro?":  GET /marketplace/products/{id}/fitment?vehicleId={catalogVehicleId}
export interface FitmentDto { status: 'CONFIRMED'|'SELLER_DECLARED'|'SUGGESTED'|'UNKNOWN'; source?: string | null; note?: string | null }
```

La placa es dato personal: solo en servidor, nunca en `localStorage` ni en analítica.

### C9 Ofertas por tienda y validación del carrito

```ts
// GET /marketplace/products/{id}/listings?lat=&lng= -> ProductListingDto[]  (más barato primero)
export interface ProductListingDto { storeId: string; storeName: string; storeVerified: boolean; branchId: string; branchName: string; city: string; price: number; effectivePrice: number; discountAmount: number; quantity: number; distanceKm?: number | null; pickupAvailable: boolean; deliveryAvailable: boolean }
// POST /marketplace/cart/validate  { items: Array<{ productId; branchId; quantity }> } -> { lines: CartValidationLine[] }   (público)
export interface CartValidationLine { productId: string; branchId: string; status: 'OK'|'PRICE_CHANGED'|'LOW_STOCK'|'UNAVAILABLE'; currentUnitPrice: number | null; availableQuantity: number }
```

### C10–C12 Favoritos, alertas, búsqueda

```ts
// GET /me/favorites?page&limit -> Page<{ productId: string; branchId: string | null; addedAt: string; name: string; imageUrl?: string; effectivePrice: number; inStock: boolean }>
// GET /me/favorites/ids -> string[]   · PUT /me/favorites/{productId} -> 204 · DELETE /me/favorites/{productId} -> 204
// GET /me/alerts -> AlertDto[] · POST /me/alerts -> 201 · DELETE /me/alerts/{id} -> 204
export interface AlertDto { id: string; productId: string; branchId?: string | null; type: 'PRICE_BELOW'|'BACK_IN_STOCK'; targetPrice?: number | null; active: boolean; createdAt: string }
// GET /marketplace/search/suggestions?q= -> { products: Array<{ id; name; partNumber?: string }>; categories: Array<{ id; slug; name }>; brands: Array<{ id; name }> }
// GET/POST /me/saved-searches · DELETE /me/saved-searches/{id}: { id; name: string; query: Record<string,string>; notify: boolean }
```

El historial de búsquedas reciente **no** se guarda en servidor (decisión D4).

### C13–C14 Reseñas, reportes y tienda

```ts
// GET /marketplace/products/{id}/reviews?page&limit · GET /marketplace/stores/{id}/reviews?page&limit -> Page<ReviewDto> + summary
export interface ReviewDto { id: string; rating: 1|2|3|4|5; title?: string; comment: string; authorName: string; verifiedPurchase: boolean; createdAt: string; storeReply?: { message: string; at: string } | null }
export interface ReviewSummary { average: number; count: number; distribution: Record<'1'|'2'|'3'|'4'|'5', number> }
// POST /me/reviews { targetType: 'PRODUCT'|'STORE'; targetId: string; orderId?: string; rating; title?; comment } -> 201 · PUT/DELETE /me/reviews/{id}
//   409 REVIEW_ALREADY_EXISTS · el texto se renderiza SIEMPRE como texto plano (sin HTML)
// POST /marketplace/reports { targetType: 'PRODUCT'|'STORE'|'REVIEW'; targetId: string; category: string; description: string } -> 201 { reportId, status }
// GET /marketplace/stores/{id} -> StoreProfileDto
export interface StoreProfileDto { id: string; name: string; description?: string | null; verified: boolean; recommended: boolean; city?: string; logoUrl?: string | null; phone?: string | null; email?: string | null; policies?: { warranty?: string | null; returns?: string | null }; rating?: ReviewSummary
  branches: Array<{ id: string; name: string; addressLine: string; city: string; latitude?: number | null; longitude?: number | null; phone?: string | null; openingHours: Array<{ day: 1|2|3|4|5|6|7; opens: string; closes: string }> }> }
// PUT/DELETE /me/followed-stores/{storeId} -> 204 · GET /me/followed-stores -> StoreSummary[]
```

### C15–C16 Cotizaciones y soporte (P2)

```ts
// POST /me/quote-requests { items: Array<{ description: string; partNumber?: string; quantity: number; vehicleId?: string }>; city: string; notes?: string; validUntil?: string } -> 201 QuoteRequestDto
// GET /me/quote-requests -> Page<QuoteRequestDto> · GET /me/quote-requests/{id} -> QuoteRequestDto
export interface QuoteRequestDto { id: string; status: 'OPEN'|'ANSWERED'|'ACCEPTED'|'EXPIRED'|'CANCELLED'; createdAt: string; items: Array<{ description: string; partNumber?: string | null; quantity: number }>; offers: Array<{ id: string; storeId: string; storeName: string; branchId: string; totalPrice: number; availability: string; validUntil: string; message?: string | null }> }
// POST /me/quote-requests/{id}/offers/{offerId}/accept -> { cartLines: CartLine[] }   (el frontend las agrega al carrito; el pago pasa por B1)
// Soporte: POST /me/support-tickets { subject; category; orderId?; message } -> 201 · GET list/detail · POST /me/support-tickets/{id}/messages { message }
```

### C19 Consultas y cotizaciones a una tienda

Conversación **1 a 1** entre el comprador y una tienda sobre un repuesto (o sobre su carrito). No reemplaza a las cotizaciones de C15 (varios repuestos a varias tiendas) ni al soporte de C16 (problemas con la plataforma o con un pedido).

```ts
export type InquiryType = 'AVAILABILITY' | 'QUOTE' | 'SHIPPING' | 'COMPATIBILITY' | 'OTHER'

// POST /me/inquiries -> 201 InquiryDto   (requiere sesión)
export interface CreateInquiryRequest {
  storeId: string; branchId?: string | null; productId?: string | null
  type: InquiryType
  message: string                              // 1..500, texto plano
  quantity?: number                            // 1..999, para QUOTE
  vehicleId?: string                           // vehículo del garaje (se comparte marca, modelo y año, no la placa)
  cartLines?: Array<{ productId: string; branchId: string; quantity: number }>   // SHIPPING desde el carrito
  shareContact: boolean                        // true = la tienda ve el teléfono del perfil; por defecto false
}
// GET /me/inquiries?status=&page=&limit -> Page<InquirySummaryDto>
// GET /me/inquiries/{id}                    -> InquiryDto
// POST /me/inquiries/{id}/messages { message } -> 201 InquiryMessageDto     · 409 INQUIRY_CLOSED
// POST /me/inquiries/{id}/close -> 204
export interface InquiryDto {
  id: string; type: InquiryType; status: 'OPEN' | 'ANSWERED' | 'CLOSED'
  storeId: string; storeName: string; branchId?: string | null; branchName?: string | null
  product?: { id: string; name: string; imageUrl?: string | null } | null
  createdAt: string; updatedAt: string
  messages: InquiryMessageDto[]
  quote?: { unitPrice: number; quantity: number; total: number; validUntil: string; note?: string | null } | null   // informativa
}
export interface InquiryMessageDto { id: string; from: 'BUYER' | 'STORE'; body: string; at: string }
export interface InquirySummaryDto { id: string; type: InquiryType; status: InquiryDto['status']; storeName: string; productName?: string | null; lastMessage: string; updatedAt: string; unread: boolean }
```

Reglas: la tienda responde desde su portal (producto B, fuera de este plan); al responder se crea una notificación `INQUIRY_REPLY` con `link: /cuenta/consultas/{id}`. Límite sugerido para el backend: 5 consultas nuevas por día y por tienda y 20 mensajes por conversación (429 `INQUIRY_RATE_LIMITED`). La `quote` es **informativa**: pagar sigue usando el precio publicado del listado; honrar un precio negociado exige una decisión de negocio aparte (riesgo 11).

### C17 Ampliación del checkout (B1)

`PlaceOrderRequest` añade `deliveryMethod: 'DELIVERY'|'PICKUP'` (por defecto `DELIVERY`), `deliveryNotes?: string` (≤ 255) y `invoice?: { taxId: string; businessName: string }`. `PICKUP` no exige `addressId`. La respuesta puede traer `shippingTotal > 0` y `sales[].pickup`.

### C18 Analítica

`POST /events` → 202, `{ events: Array<{ name; at; sessionId; props?: Record<string, string|number|boolean> }> }`. Nombres (blueprint §11.1): `search_performed`, `product_viewed`, `add_to_cart`, `checkout_started`, `order_placed`, `favorite_added`, `alert_created`, `review_submitted`. Solo se envía con consentimiento `analytics`; nunca placa, correo ni direcciones.

### Tandas sugeridas para el backend

| Tanda | Contenido | Desbloquea |
|---|---|---|
| **BE-1 (P0)** | C6 `GET /me/orders` y detalle (B2), C9 listings y `cart/validate`, C3 `POST /me/password`, C4 `unread-count`/`read-all`/`type`/`link` | T8, T10, T11, T5, T6 |
| **BE-2 (P1)** | C1 ampliaciones, C2 default, C5 privacidad, C4 preferencias, C8 garaje y `fitment`, C10 favoritos, C11 alertas, C12 sugerencias, C14 tienda pública, C17 entrega/recogida, **C19 consultas**, cancelar pedido | T3, T7, T9, T12–T16, T18, T20b |
| **BE-3 (P2)** | C13 reseñas y reportes, C15 cotizaciones, C16 soporte, C18 eventos, mantenimiento y recordatorios, seguimiento de envío, `security-events`, avatar | T19–T22, T24 |

---

## 5. Decisiones clave

| # | Decisión | Justificación | Alternativas descartadas |
|---|---|---|---|
| D1 | Fuente de datos **por función** (`VITE_MOCK_FEATURES`) además de la global | El backend se implementará por tandas; la demo debe mostrar todas las funciones hoy y migrar una a una sin tocar código | Un interruptor global (la demo solo funcionaría cuando el backend terminara todo); banderas servidas por el backend (más trabajo y un endpoint más) |
| D2 | El contrato nace en el frontend (§4) y el backend lo implementa o lo corrige por ADR | El usuario pidió dejar la UI lista; el mock y el http comparten tipos y no divergen | Esperar a que cada endpoint exista (bloquea la UI meses) |
| D3 | Paginación `?page=1&limit=20` con `{ items, page, limit, total, totalPages }` sin `data` | `conventions.md §8` pide `page`/`limit`; el código no usa envoltorio (decisión D9 del plan de checkout) | `data` + `pagination` solo en listas (inconsistente con el resto) |
| D4 | Lo efímero o sensible queda **local**: carrito, vistos recientemente, historial de búsqueda, comparador. Lo personal y duradero va al servidor (garaje, favoritos, alertas) | Privacidad y Ley 1581: el servidor guarda lo que el usuario espera conservar entre dispositivos | Todo en servidor (más datos personales); todo local (se pierde al cambiar de dispositivo) |
| D5 | `/cuenta` es un *layout* con rutas hijas y un `RequireSession` común (se reutiliza en `/checkout` y `/mis-pedidos`) | Un solo lugar espera a que la sesión esté lista y redirige a `/ingresar` conservando el destino | Repetir la lógica en cada página (ya hay una copia en el checkout) |
| D6 | Los cambios se confirman con el servidor; no hay actualización optimista en datos sensibles (perfil, seguridad, privacidad) | El servidor valida y puede rechazar; evita mostrar un estado falso | Optimista en todo (rápido pero engañoso en errores) |
| D7 | Acciones destructivas (eliminar cuenta, cerrar sesiones, borrar dirección o vehículo, cancelar pedido) piden confirmación en un diálogo accesible; eliminar la cuenta exige la contraseña | Evita errores irreversibles; `components/ui` no tiene `dialog`, se escribe sobre `@base-ui/react/dialog` sin dependencias nuevas | `window.confirm` (no accesible ni con el estilo del sitio) |
| D8 | El estado de los servicios `mock` persiste en `sessionStorage` (`autopartco.mock.<función>`) | Una demo híbrida no debe "olvidar" al navegar o recargar la pestaña | Solo memoria (se pierde al recargar); `localStorage` (mezcla demos entre pestañas) |
| D9 | Reseñas, notas y descripciones de usuarios se renderizan como **texto plano** | Evita XSS almacenado | Markdown/HTML sanitizado (superficie mayor) |
| D10 | Producción: guardas de build, `application-prod.yaml`, par RSA propio y lista de verificación (ver `docs/deploy/ENVIRONMENTS.md`). El simulador de pagos sigue activo por decisión del usuario (2026-10-14) hasta integrar Wompi | Evita publicar datos simulados o una API sin HTTPS por descuido | Confiar en la disciplina del equipo |
| D11 | **Consulta 1 a 1 con la tienda** como entidad propia (C19), con un solo botón "Consultar / Cotizar" y un motivo (disponibilidad, precio o cantidad, envío o recogida, compatibilidad, otro) | Cubre muchos casos con un solo componente y un solo hilo; el motivo ordena la bandeja de la tienda y mide la demanda de productos agotados | Un botón por caso (ruido en la ficha); reutilizar tickets de soporte (mezcla la relación comercial con la plataforma); chat en tiempo real (sin infraestructura ni necesidad en el MVP) |

---

## 6. Tareas

Convenciones: **Modelo** según el reparto del repositorio (Haiku mecánico, Sonnet funcionalidades, Opus decisiones y seguridad). **Agente** del proyecto. Todas se ejecutan en una rama `feature/*` creada desde `develop`, **una a la vez en el área `frontend`** (regla de no edición simultánea), con un PR por tarea o por fase pequeña. Aceptación = **C0** (§2) + lo indicado. Contratos en §4; no se repiten aquí. Las que dependen de un endpoint nuevo se prueban con el servicio `mock` y quedan en `VITE_MOCK_FEATURES` hasta que el backend lo tenga.

### Fase 0 — Cimientos

#### T1 — Fuente de datos por función y etiqueta de demostración

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/lib/api.ts`, nuevo `frontend/src/lib/feature-source.ts`, nuevo `frontend/src/lib/mock-store.ts`, `frontend/src/components/common/demo-badge.tsx`, `frontend/src/env.d.ts`, y los tres `frontend/.env*.example` (regla de ambientes).
- **Contexto mínimo:** hoy `DATA_SOURCE` es global (`lib/api.ts`) y cada `getXService()` lo lee. Crea `resolveSource(feature: string): 'mock' | 'http'` que devuelve `mock` si `DATA_SOURCE === 'mock'` o si la función está en `VITE_MOCK_FEATURES`. `createMockStore<T>(key, initial)` guarda en `sessionStorage` bajo `autopartco.mock.<key>` con *try/catch* (D8). `<DemoBadge feature />` solo se pinta si la función es simulada **dentro** de un ambiente `http`: "Datos de demostración". Migra `getCheckoutService`/`getAddressService` (no cambian de comportamiento: `checkout` y `addresses` no estarán en la lista).
- **Aceptación:** C0. Con `VITE_MARKETPLACE_DATA_SOURCE=http` y `VITE_MOCK_FEATURES=garage`, `resolveSource('garage')` es `mock` y `resolveSource('addresses')` es `http` (verificar con una prueba manual en consola). Variable documentada en los tres `*.example`.

#### T2 — Cuenta: *layout*, `RequireSession`, diálogo y navegación

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/account/{components/account-shell.tsx,components/account-nav.tsx,lib/require-session.tsx}`, `frontend/src/components/ui/dialog.tsx`, `frontend/src/features/account/pages/account-home-page.tsx`, `frontend/src/app/{router.tsx,lazy-pages.tsx}`, `frontend/src/features/checkout/pages/checkout-page.tsx` (usar `RequireSession`), `frontend/src/features/auth/components/account-menu.tsx` (enlaces a `/cuenta`, `/mis-pedidos`).
- **Contexto mínimo:** D5 y D7. `AccountShell`: barra lateral desde `lg` y *tabs* con desplazamiento horizontal (o `Sheet`) en móvil; el ítem activo lleva el tramo del hilo rojo, como `site-header`. `RequireSession` espera `useSessionReady` (hoy en `features/checkout/lib/use-session-ready.ts`; muévelo a `features/auth/`) y redirige a `/ingresar` con `state.from`. `dialog.tsx` sobre `@base-ui/react/dialog`, con foco atrapado, `Escape`, y título/descripción asociados. Rutas hijas de `/cuenta` con página "Próximamente" simple para las que aún no existen.
- **Aceptación:** C0; sin sesión, `/cuenta` y `/mis-pedidos` redirigen a `/ingresar` y regresan después del login; el diálogo se cierra con `Escape` y devuelve el foco al disparador; el menú de cuenta enlaza al área nueva.

### Fase 1 — Cuenta (la mayoría ya tiene backend)

#### T3 — Perfil

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/account/api/{types.ts,profile-service.ts,http/http-profile-service.ts,mock/mock-profile-service.ts}`, `frontend/src/features/account/pages/profile-page.tsx`.
- **Contexto mínimo:** C1. **Existe** `GET/PATCH /me/profile`: el formulario envía **solo los campos cambiados** (el backend exige al menos uno). Validación igual a `ProfileUpdateRequest` (nombre ≤ 100, identificación ≤ 20, teléfono ≤ 20, fecha `AAAA-MM-DD` pasada). Correo solo lectura con insignia "Verificado / Sin verificar" y botón "Reenviar verificación" si `emailVerified === false`. Al guardar, actualiza el nombre de `Session` en `session-store` para que el header lo refleje. Foto (P2): *placeholder* con iniciales ya existente (`initials`).
- **Aceptación:** C0; editar el teléfono en `http` contra el backend local persiste y se ve tras recargar la página (iniciando sesión de nuevo); un 400 muestra el mensaje por campo; "Guardar" se desactiva sin cambios.

#### T4 — Direcciones

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/account/pages/addresses-page.tsx`, `frontend/src/features/account/components/{address-card.tsx,department-city-fields.tsx}`, `frontend/src/features/location/data/divipola.ts`; ampliar `features/checkout/api/{types.ts,mock/mock-address-service.ts,http/http-address-service.ts}` con `update`, `remove`, `setDefault`.
- **Contexto mínimo:** C2. Reutiliza `AddressForm` del checkout moviéndolo a un componente compartido. `update` → `PUT /me/addresses/{id}`; `remove` → `DELETE` (diálogo de confirmación, D7); `setDefault` → `PATCH …/default` (mientras no exista, `PUT` con `isDefault: true`). `divipola.ts` lista los 32 departamentos y Bogotá D.C. con sus códigos y, al menos, las ciudades capitales (datos públicos del DANE); el selector usa `<datalist>` para no impedir escribir una ciudad que falte.
- **Aceptación:** C0; crear, editar, marcar predeterminada y borrar contra el backend local; no se puede borrar sin confirmar; la lista del checkout refleja los cambios.

#### T5 — Seguridad y sesiones

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/account/api/{security-service.ts,http/http-security-service.ts,mock/mock-security-service.ts}`, `frontend/src/features/account/pages/security-page.tsx`; ampliar `AuthService` solo si hace falta `logoutOthers()`.
- **Contexto mínimo:** C3. **Existe** `GET /me/sessions`, `DELETE /me/sessions/{id}` y `POST /auth/logout/others` (leer `AuthController` para el cuerpo exacto). Mostrar dispositivo (interpretar `userAgent` de forma simple: navegador y sistema), IP, creada, vence y la etiqueta "Esta sesión". Cerrar la sesión actual equivale a cerrar sesión. **Cambiar contraseña** (`POST /me/password`, nuevo) con la política del registro (`newPasswordError` en `auth/lib/validation.ts`), campo para confirmar y la opción "Cerrar mis otras sesiones".
- **Aceptación:** C0; en `http` se listan las sesiones reales y se revoca una ajena; nunca se escribe la contraseña en logs ni almacenamiento; tras cambiar la contraseña se muestra confirmación con `role="status"`.

#### T6 — Notificaciones: bandeja, indicador y preferencias

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/notifications/{api/…,pages/notifications-page.tsx,components/notification-bell.tsx,components/preferences-form.tsx}`, `frontend/src/app/site-header.tsx` (campana junto al carrito).
- **Contexto mínimo:** C4. **Existe** la lista y `PATCH …/read`. Hasta que exista `unread-count`, el contador se calcula sobre la lista (limite 20) y se marca `~`. El *polling* es de 60 s solo con la pestaña visible (`document.visibilityState`). `link` navega a la ruta interna (validar que empiece con `/` y no con `//`). Preferencias (P1) con interruptores accesibles (`role="switch"`); `SECURITY` y `SYSTEM` aparecen bloqueados con explicación.
- **Aceptación:** C0; marcar una como leída actualiza el contador; la campana anuncia el número con `aria-label`; sin sesión no se hace ninguna petición.

#### T7 — Privacidad y datos personales

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/account/{api/privacy-service.ts,…,pages/privacy-page.tsx}`.
- **Contexto mínimo:** C5. Tres bloques: consentimientos (interruptores con fecha de la última actualización), "Descargar mis datos" (descarga el JSON recibido con un `Blob` y un enlace temporal), y "Eliminar mi cuenta" en una "zona de peligro" con diálogo que pide la contraseña y escribir `ELIMINAR`. 409 `ACCOUNT_HAS_OPEN_ORDERS` lista los pedidos abiertos con enlace. Tras eliminar, `setSession(undefined)` y redirigir a `/` con un mensaje.
- **Aceptación:** C0; el botón de eliminar permanece desactivado hasta cumplir ambos requisitos; el archivo descargado en `mock` es JSON válido.

### Fase 2 — Compra

#### T8 — Mis pedidos (F5): lista, detalle y seguimiento

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/orders/{api/{types.ts,orders-service.ts,http/…,mock/…},pages/{orders-page.tsx,order-detail-page.tsx},components/{order-timeline.tsx,shipment-card.tsx,order-status-badge.tsx}}`; reutiliza `features/checkout/components/order-sales-list.tsx`.
- **Contexto mínimo:** C6. Lista con filtro por estado, búsqueda por número o repuesto y paginación (`Page`). Detalle: cabecera con número y total, `order-timeline` (eventos por fecha con el estado actual resaltado), una `shipment-card` por venta (transportadora, guía con botón "Copiar", enlace de rastreo `rel="noopener noreferrer"`), dirección de entrega o punto de recogida. `order-result-page` enlaza a "Ver mi pedido". El mock genera 6 pedidos en estados distintos (incluye uno `cancellable` y uno con envío).
- **Aceptación:** C0; con un pedido real creado por el checkout (`http`) aparece en la lista una vez exista B2; un id ajeno o inexistente muestra "No encontramos este pedido" (404).

#### T9 — Acciones sobre el pedido

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/orders/{api/…,components/{cancel-order-dialog.tsx,report-issue-dialog.tsx,reorder-button.tsx,receipt.tsx}}`, hoja de estilos de impresión en el componente `receipt.tsx`.
- **Contexto mínimo:** C6 y D7. **Cancelar** solo si `cancellable` (el botón no se pinta si es falso; el 409 se muestra si el estado cambió). **Comprar de nuevo**: agrega las líneas al carrito con `addCartLine` (usa los ids y cantidades del pedido; el precio lo revalida el servidor en el checkout) y avisa de las que ya no existan tras `cart/validate`. **Comprobante**: vista imprimible (`window.print()` con clases `print:`), marcada "Comprobante de demostración" mientras el pago sea simulado. **Reportar un problema** con categoría y descripción.
- **Aceptación:** C0; "Comprar de nuevo" deja el carrito con las líneas esperadas; el comprobante imprime en una sola hoja A4 sin la cabecera del sitio.

#### T10 — Producto: ofertas de otras tiendas

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/marketplace/components/listing-selector.tsx`, `frontend/src/features/marketplace/api/…` (método `listings(productId)`), cambios en `features/marketplace/pages/product-page.tsx` y `features/cart/components/add-to-cart.tsx` (acepta una oferta elegida).
- **Contexto mínimo:** C9 (`ProductListingDto`). Tabla/lista ordenada por precio con tienda (y distancia si el usuario tiene ciudad, `city-store`), sucursal, existencias, "Recoger" / "Domicilio", y botón "Agregar" por fila. La oferta más barata viene preseleccionada. Hasta que exista el endpoint, `http` devuelve una sola oferta derivada de `GET /marketplace/products/{id}` y la sección no se pinta si hay una sola.
- **Aceptación:** C0; agregar de una oferta distinta guarda su `branchId` en el carrito; el checkout paga esa sucursal.

#### T11 — Carrito: validación, guardar para después y notas

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/cart/pages/cart-page.tsx`, nuevos `frontend/src/features/cart/{api/…,components/{line-status.tsx,saved-for-later.tsx}}`, `frontend/src/features/checkout/lib/cart-source.ts` (campos `savedForLater?`, `note?` por tienda; subir `version` a 1 con campos opcionales para no invalidar carritos existentes).
- **Contexto mínimo:** C9. Al abrir `/carrito` llama `POST /marketplace/cart/validate`; muestra por línea "Bajó/Subió de precio", "Quedan N", "Ya no está disponible" y un botón "Actualizar carrito". "Guardar para después" saca la línea del pago sin borrarla. Nota por tienda (≤ 255). Texto plano (D9).
- **Aceptación:** C0; con `PRICE_CHANGED` el total informativo muestra el precio nuevo tras "Actualizar"; las líneas guardadas no entran al checkout.

#### T12 — Checkout: entrega a domicilio o recoger en tienda

- **Modelo:** Sonnet (el cambio de contrato C17 lo aprueba antes `arquitecto`). **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/checkout/{api/{types.ts,mock/…,http/…},components/delivery-method-picker.tsx,components/invoice-fields.tsx,pages/checkout-page.tsx}`.
- **Contexto mínimo:** C17. `RadioGroup` "Domicilio / Recoger en tienda"; con "Recoger" el bloque de dirección se oculta y se muestra la sucursal de cada venta. Notas de entrega ≤ 255. Factura (NIT y razón social) en un bloque plegable, validación de formato de NIT colombiano (dígitos y dígito de verificación opcional). El cuerpo solo incluye los campos nuevos si el usuario los usó; hasta que el backend los acepte, `http` los omite (verificar con `INVALID_REQUEST_BODY`).
- **Aceptación:** C0; en `mock` un pedido con "Recoger" no exige dirección y la confirmación muestra la sucursal.

### Fase 3 — Personalización

#### T13 — Garaje

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/garage/{api/…,pages/{garage-page.tsx,vehicle-page.tsx},components/{vehicle-form.tsx,vehicle-card.tsx,vehicle-switcher.tsx}}`.
- **Contexto mínimo:** C8. Formulario con marca/modelo/año/versión usando `vehicle-picker.tsx` de marketplace (catálogo), apodo, placa opcional (formato colombiano `ABC123` o `ABC12D`), kilometraje y notas. Vehículo principal. `vehicle-switcher` en el *header* o en la búsqueda: "Mi carro: Mazda 3 2016" que filtra los resultados (guarda el id seleccionado en `localStorage`, **no** la placa). Mantenimiento y recordatorios (P2) en `vehicle-page`: línea de tiempo y tareas propias del usuario (D4).
- **Aceptación:** C0; la placa no aparece en `localStorage` ni en la URL; borrar un vehículo pide confirmación.

#### T14 — "¿Sirve para mi carro?" y filtro por vehículo

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/marketplace/pages/product-page.tsx`, nuevo `frontend/src/features/marketplace/components/fitment-badge.tsx`, `frontend/src/features/marketplace/lib/product-query.ts`.
- **Contexto mínimo:** C8 (`FitmentDto`). Cuatro estados visualmente distintos y con texto (no solo color): "Compatibilidad confirmada", "Declarada por la tienda", "Coincidencia sugerida", "Sin datos suficientes" (blueprint §5.4). Sin sesión o sin vehículo: invitación a guardar uno. Los resultados de búsqueda aceptan `make/model/year` del vehículo activo.
- **Aceptación:** C0; "Sin datos suficientes" nunca se muestra como compatible; las cuatro variantes pasan revisión de contraste.

#### T15 — Favoritos y alertas

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/favorites/{api/…,hooks.ts,components/favorite-button.tsx,pages/{favorites-page.tsx,alerts-page.tsx}}`, `frontend/src/features/marketplace/components/product-card.tsx`.
- **Contexto mínimo:** C10, C11. `favorite-button` (corazón con `aria-pressed`) en la tarjeta y el detalle; sin sesión lleva a `/ingresar` conservando la acción. `useFavoriteIds()` carga `GET /me/favorites/ids` una vez. Alerta de precio: campo "Avísame si baja de $X" con el precio actual como sugerencia; alerta de stock cuando está agotado.
- **Aceptación:** C0; el corazón cambia al instante y revierte con mensaje si el servidor falla; las alertas aparecen en `/cuenta/alertas` con opción de eliminar.

#### T16 — Búsqueda: sugerencias, historial, guardadas, vistos y comparador

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/marketplace/components/search-bar.tsx`, nuevos `frontend/src/features/marketplace/{lib/{recent-searches.ts,recently-viewed.ts,compare-store.ts},components/{search-suggestions.tsx,compare-bar.tsx},pages/compare-page.tsx}`, `frontend/src/features/account/pages/saved-searches-page.tsx`.
- **Contexto mínimo:** C12 y D4. Sugerencias con patrón *combobox* accesible (flechas, `Enter`, `Escape`, `aria-activedescendant`), con *debounce* de 250 ms. Historial reciente y vistos en `localStorage` (máx. 10 y 12), con "Borrar historial". El comparador acepta hasta 4 productos desde `product-card` ("Comparar"), muestra una barra fija inferior y la tabla en `/comparar` (en móvil, columnas desplazables con el nombre fijo).
- **Aceptación:** C0; el historial no sale del navegador (comprobar en la pestaña de red); el comparador avisa al intentar agregar un quinto producto.

#### T17 — Inicio personalizado

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** `frontend/src/features/marketplace/home/home-sections.tsx`, `frontend/src/features/marketplace/pages/home-page.tsx`.
- **Contexto mínimo:** bloques opcionales que solo se pintan si hay datos reales: "Sigue donde quedaste" (vistos recientemente), "Para tu {vehículo}" (resultados filtrados por el vehículo activo), "Pedidos en camino" (T8). No se inventan ofertas.
- **Aceptación:** C0; sin datos personales, la página inicial queda igual que hoy.

### Fase 4 — Confianza, tiendas y servicios

#### T18 — Página de tienda (F7)

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/store/{api/…,pages/store-page.tsx,components/{store-header.tsx,branch-list.tsx,opening-hours.tsx,follow-button.tsx}}`; enlazar desde `store-card.tsx` y `product-page.tsx`.
- **Contexto mínimo:** C14. Insignias solo si el servidor las informa (`verified`, `recommended`). Sucursales con horario (hoy abierto/cerrado calculado con la hora local de Colombia), teléfono con `tel:`, mapa estático con el componente existente `city-map` o un enlace a mapas. Catálogo reutiliza `search-results-view` con `store=`. Seguir tienda (P1) y reportar (T19).
- **Aceptación:** C0; una tienda sin sucursales o sin descripción no deja huecos; el horario muestra "Abierto ahora" correctamente en los dos husos de prueba.

#### T19 — Reseñas y reportes

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/reviews/{api/…,components/{rating-summary.tsx,review-list.tsx,review-form.tsx,report-dialog.tsx},pages/my-reviews-page.tsx}`; integrar en `product-page.tsx`, `store-page.tsx` y `order-detail-page.tsx` ("Reseñar").
- **Contexto mínimo:** C13 y D9. Distribución por estrellas con barras accesibles (texto + número, no solo color). Insignia "Compra verificada" solo si `verifiedPurchase`. Una reseña por producto y usuario (409 → "Ya reseñaste este repuesto; edítala"). Reporte con categoría y descripción; confirmación sin revelar datos del reportado.
- **Aceptación:** C0; un `<script>` escrito en una reseña de prueba se ve como texto; el formulario no permite enviar sin calificación.

#### T20 — Cotizaciones para talleres (P2)

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/quotes/{api/…,pages/{quotes-page.tsx,quote-new-page.tsx,quote-detail-page.tsx},components/{quote-item-fields.tsx,offer-card.tsx}}`.
- **Contexto mínimo:** C15. Formulario de varias líneas (descripción o referencia, cantidad, vehículo del garaje), ciudad y vigencia. El detalle compara ofertas por total y disponibilidad; "Aceptar oferta" agrega las líneas al carrito y lleva al checkout (el pago sigue el flujo de B1). Los almacenes responden desde su portal (fuera de alcance).
- **Aceptación:** C0; en `mock`, una cotización pasa de `OPEN` a `ANSWERED` con 2 ofertas simuladas tras 5 s.

#### T20b — Consultar / Cotizar a una tienda

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/inquiries/{api/{types.ts,inquiries-service.ts,http/…,mock/…},components/{inquiry-button.tsx,inquiry-dialog.tsx,inquiry-thread.tsx},pages/{inquiries-page.tsx,inquiry-detail-page.tsx}}`; integrar en `features/marketplace/pages/product-page.tsx`, `features/store/pages/store-page.tsx` (T18), `features/cart/pages/cart-page.tsx` y `features/orders/pages/order-detail-page.tsx` (T8).
- **Contexto mínimo:** C19, D7, D9 y D11. `InquiryButton` recibe `{ store, product?, branch?, cartLines?, defaultType }` y abre `InquiryDialog` (T2) con: motivo (cuatro opciones con texto guía: "¿Cuándo llega?", "Quiero un precio por cantidad", "Coordinar envío o recogida", "¿Sirve para mi carro?"); mensaje ≤ 500 con contador, precargado según el motivo y el repuesto; cantidad opcional; vehículo del garaje opcional (T13; se manda marca, modelo y año, nunca la placa); e interruptor **"Compartir mi teléfono con la tienda"**, apagado por defecto, que muestra el número que se compartiría. Aviso fijo: "No compartas contraseñas ni datos de tarjeta." Sin sesión lleva a `/ingresar` conservando el borrador en `sessionStorage`. Puntos de entrada: ficha de producto (secundario si hay stock; **principal** junto a "Avisarme" si está agotado o si la compatibilidad dice "sin datos suficientes"), tienda ("Escribir a la tienda"), carrito (por tienda: "Coordinar envío", con `cartLines`) y detalle de pedido ("Coordinar entrega"). Bandeja `/cuenta/consultas` con filtro por estado y punto de no leído; detalle con el hilo (mensajes del comprador y de la tienda, la cotización informativa destacada con su vigencia) y caja para responder o "Cerrar consulta". Mock: a los 8 s el hilo recibe una respuesta de la tienda y, si el motivo fue `QUOTE`, una `quote` informativa. Nunca prometer un plazo de respuesta que el servidor no informe.
- **Aceptación:** C0; el diálogo se abre y cierra con teclado y devuelve el foco al botón; "Enviar" se desactiva durante la petición (sin doble envío) y con el mensaje vacío; el teléfono no se envía si el interruptor está apagado (verificar el cuerpo en la pestaña de red); una consulta desde el carrito incluye solo las líneas de esa tienda; un mensaje con HTML se muestra como texto.

#### T21 — Ayuda y soporte

- **Modelo:** Haiku (contenido) + Sonnet (tickets). **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/help/{content/faq.ts,pages/help-page.tsx}`, `frontend/src/features/account/pages/support-page.tsx`, `frontend/src/features/support/api/…`.
- **Contexto mínimo:** C16. FAQ en español (cómo comprar, pagos de demostración, envíos y recogida, devoluciones, privacidad) con `<details>` accesibles; **no inventar** políticas: lo no definido dice "Lo define cada tienda". Tickets: lista, creación con pedido opcional y detalle con mensajes.
- **Aceptación:** C0; la FAQ se navega solo con teclado; cada pregunta tiene ancla `#id`.

### Fase 5 — Transversal y calidad

#### T22 — Consentimiento y analítica

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:** nuevos `frontend/src/features/analytics/{track.ts,consent-store.ts,components/consent-banner.tsx}`, `frontend/src/app/root-layout.tsx`.
- **Contexto mínimo:** C5 y C18. Banner de *habeas data* con "Aceptar", "Rechazar analítica" y "Configurar" (decline por defecto, no bloquea el uso). `track(name, props)` es un *no-op* sin consentimiento; con él acumula y envía por lotes (`navigator.sendBeacon` o `fetch`) cada 10 s. Lista cerrada de eventos y propiedades permitidas; nunca placa, correo, dirección ni texto libre.
- **Aceptación:** C0; sin consentimiento no sale ninguna petición a `/events` (pestaña de red); el consentimiento se puede cambiar en `/cuenta/privacidad`.

#### T23 — Pruebas E2E con Playwright

- **Modelo:** Sonnet. **Agente:** `qa-seguridad` (solo lectura del código; escribe en `tests/` con autorización).
- **Archivos:** `tests/e2e/*.spec.ts`, `playwright.config.ts` (ya existen en la raíz del repo).
- **Contexto mínimo:** flujos en `mock`: comprar (aprobado, rechazado, stock), editar perfil, agregar y borrar una dirección, favoritos, comparar, ver pedido. Un flujo `http` solo si el backend local está arriba y con autorización del usuario (la BD es la de `backend/.env`).
- **Aceptación:** `npx playwright test` en verde y evidencia guardada; fallos reportados con captura.

#### T24 — Revisión de seguridad y accesibilidad

- **Modelo:** Opus. **Agente:** `qa-seguridad`.
- **Archivos:** ninguno (solo lectura).
- **Contexto mínimo:** revisar con las skills `security-review` y `design:accessibility-review`: (1) datos personales en `localStorage`/`sessionStorage`/URL/logs (placa, correo, direcciones); (2) texto de usuarios renderizado como HTML; (3) redirecciones abiertas (`from`, `link` de notificaciones, `trackingUrl`); (4) el cliente nunca decide autorización ni precio; (5) todos los `/me/**` devuelven 404 para recursos ajenos (probar con dos cuentas cuando el backend exista); (6) foco, etiquetas, contraste y uso a 360 px de cada pantalla nueva; (7) cabeceras de caché de respuestas con datos personales.
- **Aceptación:** informe con severidades y pasos para reproducir; cada hallazgo alto o crítico tiene tarea de corrección.

### Ambientes y entrega

#### T25 — Datos de demostración y cuentas demo (B4)

- **Modelo:** Sonnet. **Agente:** `database`.
- **Archivos:** nuevo `database/seed/demo-data.sql` (idempotente, `ON CONFLICT DO NOTHING`) y `docs/deploy/DEMO.md`.
- **Contexto mínimo:** la BD local ya trae un catálogo grande; el script solo asegura lo que la demo necesita: una cuenta de cliente, una de almacén aprobada con sucursal y productos, y una ROOT, **ya verificadas** (hash generado con el *encoder* del backend, contraseña de demostración documentada en `DEMO.md`, nunca la de una cuenta real), más 3–4 pedidos en estados distintos para "Mis pedidos". **Lo ejecuta el usuario** contra la BD de demo; el agente no ejecuta SQL.
- **Aceptación:** revisión del script (idempotente, sin `DROP`/`TRUNCATE`, sin tocar cuentas reales); `DEMO.md` indica cómo ejecutarlo y cómo restablecer.

#### T26 — CI del frontend por ambiente

- **Modelo:** Sonnet. **Agente:** `frontend` (archivo en `.github/workflows/`; requiere autorización del usuario por ser CI y secretos).
- **Archivos:** nuevo `.github/workflows/frontend-ci.yml`.
- **Contexto mínimo:** en cada PR: `npm ci`, `npm run lint`, `npm run build`. En un *tag* `v*`: `npm run build:prod` con `VITE_MARKETPLACE_DATA_SOURCE` y `VITE_API_BASE_URL` tomadas de las variables del repositorio (`vars.*`, no secretos: son públicas). Sin despliegue automático hasta resolver A1 de `ENVIRONMENTS.md`.
- **Aceptación:** el workflow pasa en un PR de prueba; el build de producción falla con una `VITE_API_BASE_URL` `http://` (guarda de `vite.config.ts`).

#### Tareas del backend (las implementa el usuario; el agente `arquitecto` las divide en ADR/PR)

| Id | Contenido | Modelo sugerido |
|---|---|---|
| BE-1 | Tanda BE-1 de §4 (B2, listings, `cart/validate`, cambio de contraseña, notificaciones) | Sonnet; `cart/validate` y B2 con revisión Opus |
| BE-2 | Tanda BE-2 de §4 | Sonnet; privacidad (`DELETE /me`, exportación) Opus |
| BE-3 | Tanda BE-3 de §4 | Sonnet |
| BE-4 | Parametrizar `app.cors.allowed-origins` también en el perfil base y revisar el perfil `prod` (`ddl-auto: validate` cuando el esquema esté reconciliado) | Haiku |
| BE-5 | Aplicar las migraciones pendientes y verificar el checkout; Wompi para pedidos antes de desactivar el simulador | Opus |

---

## 7. Ambientes

Ya incluidos en esta rama (ver `docs/deploy/ENVIRONMENTS.md`):

- **Local-mock / Local-http:** `frontend/.env.example`, `backend/.env.example` (perfil `dev`).
- **Demo:** `frontend/.env.demo.example` + `npm run dev:demo` / `build:demo`; `backend/.env.demo.example`. Con T1 se vuelve **híbrido** (las funciones sin backend se ven simuladas y etiquetadas); con T25 tiene cuentas y datos propios.
- **Producción:** `frontend/.env.production.example` + `npm run build:prod` (el build **falla** si la fuente no es `http` o la API no es `https://`); `backend/.env.production.example` + `backend/src/main/resources/application-prod.yaml` (`ddl-auto: none`, CORS obligatorio, sin trazas) + lista de verificación de publicación.
- **Regla permanente:** toda función que necesite configuración, claves de API o secretos añade sus variables a los `*.example` de **cada** ambiente en el mismo PR (T1 lo hace con `VITE_MOCK_FEATURES`).

---

## 8. Riesgos y puntos a verificar

1. **Deriva de contrato.** Mock y backend pueden divergir. Mitigación: los tipos de `api/types.ts` salen de §4; cada tanda del backend actualiza este documento (o un ADR) si cambia algo; T23 y T24 lo comprueban cuando exista el backend.
2. **Demo engañosa.** Una función simulada puede parecer real. Mitigación: `DemoBadge` obligatorio (T1) y la lista `VITE_MOCK_FEATURES` visible en `DEMO.md`.
3. **Datos personales.** Placa, direcciones, historial y privacidad (Ley 1581). Reglas: placa solo en servidor, historial de búsqueda y vistos solo locales, analítica con consentimiento y sin datos personales, exportación y eliminación (T7).
4. **Alcance.** Son 23 tareas de frontend (T1–T22 y T20b); mucha UI nueva. Mitigación: fases y prioridades; un PR pequeño por tarea; la Fase 1 y T8 ya cubren lo que el backend tiene o tendrá primero (BE-1).
5. **Sesión en memoria.** En `http` recargar cierra la sesión (limitación conocida). Afecta a toda el área `/cuenta`; resolverlo con cookie HttpOnly es una decisión de seguridad del backend.
6. **Simulador de pagos en producción.** Aceptado por el usuario para la demo; debe desactivarse antes de clientes reales (`ENVIRONMENTS.md` §4.2).
7. **Colisión con el backend.** Las tareas de frontend no tocan `backend/`. Los archivos de ambiente de `backend/` (`.env*.example`, `application-prod.yaml`) viajan en esta rama; si el agente backend cambia `application.yaml`, fusionar con cuidado.
8. **Rendimiento.** Cada pantalla nueva es *lazy*; vigilar el tamaño del *chunk* de `page-header` (139 kB hoy) y no importar `mock` desde código `http`.
9. **Accesibilidad de componentes nuevos** (`dialog`, *combobox*, `switch`): son los más propensos a errores; T24 los revisa con teclado y lector de pantalla.
10. **Decisiones abiertas de ambiente:** A1 (hosting), A2 (staging) y A3 (dominios) en `ENVIRONMENTS.md §5`.
11. **Consultas (C19).** Dependen de que las tiendas respondan desde su portal (producto B); sin ese portal, en la demo responde el simulador. Riesgos de abuso (*spam* hacia las tiendas) y de datos personales en los mensajes: límites en servidor, teléfono compartido solo con consentimiento y texto plano. Decisión pendiente: si una cotización negociada debe poder cobrarse a su precio (exige un precio especial por comprador o una promoción dirigida); por ahora es solo informativa.
