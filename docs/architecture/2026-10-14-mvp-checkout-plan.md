# Plan: checkout del MVP (BL-B1 + BL-F3 + BL-F4)

> Orden de ejecución, despliegues conjuntos y aprobaciones: [`2026-10-14-mvp-orden-global.md`](2026-10-14-mvp-orden-global.md).

- Fecha: 2026-10-14
- Base revisada: rama `chore/mvp-backlog` (commit `5fa7db8`, mismo código que `develop` en `50a92aa`)
- Origen: `docs/architecture/mvp-backlog.md`, ítems BL-B1, BL-F3 y BL-F4
- Estructura: (1) objetivo y alcance, (2) **contrato compartido**, (3) decisiones, (4) **backend**, (5) **frontend**, (6) QA, (7) riesgos y aprobaciones pendientes.
- La sección 5 (frontend) se puede ejecutar sola con las secciones 1 y 2, sin contexto adicional ni backend, usando el servicio `mock`.

---

## 1. Objetivo y alcance

**Objetivo.** El comprador autenticado confirma su carrito: elige una dirección guardada y un método de pago simulado. Si el pago se aprueba, ve una pantalla de confirmación con el número de pedido. Si se rechaza, ve el rechazo sin que se haya creado nada y puede volver a intentarlo.

**Dentro del alcance**

- BL-B1: `POST /api/v1/marketplace/orders`. Crea el pedido (`orders`), la dirección congelada (`order_addresses`), una venta por sucursal (`sales`), el detalle (`order_details`), el pago aprobado (`payments`) y su evento (`payment_events`). Descuenta el stock con un `UPDATE` condicional atómico. Calcula el precio en el servidor con `core.pricing`. Es idempotente con `clientRequestId`.
- BL-F3: vista `/checkout`, que requiere sesión. Muestra el resumen del carrito por tienda, la selección o creación de una dirección, el método de pago simulado y el resultado simulado. Maneja los errores 400, 401, 402, 404 y 409.
- BL-F4: vista `/pedido/:id/resultado`, la confirmación de un pedido aprobado.

**Fuera del alcance**

- El carrito y su UI (BL-F1–BL-F2: MK-F2, MK-F3), con una excepción: aquí se fija el formato de `localStorage` que el checkout lee (§2.6).
- "Mis pedidos" (BL-B2/BL-F5: MK-B3, MK-F6), "mis tiendas" (BL-B3: SA-B1), notificaciones (BL-B8), el movimiento de inventario `SALE` (BL-B6: SA-B5, dueño único del registro de movimientos dentro de `StockService.decrementIfAvailable`) y la auditoría.
- Wompi o cualquier pasarela real, los costos de envío (son `0` en el MVP), las reservas de stock, los cupones, la facturación y los correos de pedido.
- Cambios de esquema. Todas las tablas y semillas ya existen en `database/init.sql`.

---

## 2. Contrato compartido (backend ↔ frontend)

### 2.1 Convenciones que aplican

- Prefijo `/api/v1`. Autenticación con `Authorization: Bearer <accessToken>`. La ruta **no** es pública: la regla `anyRequest().authenticated()` de `WebSecurityConfig` ya la cubre.
- Las respuestas exitosas van **sin envoltorio `data`**, como define `docs/api/conventions.md` §6 y todos los endpoints existentes (`MeController`, `ProductBrowseController`).
- El formato de error es el de `docs/api/error-handling.md`: `{ "error": { "code", "message", "details" } }`. `message` siempre es seguro para mostrarlo al usuario.
- Dinero:
  - En el backend es `BigDecimal` con escala 2 y `RoundingMode.HALF_UP`.
  - En JSON viaja como número (`230000.00` se recibe como `230000`). Moneda: siempre `COP`.
  - El frontend **nunca** calcula montos que se cobren. Solo muestra los del servidor (los totales del carrito son informativos).
- Fechas: `LocalDateTime` sin zona (`"2026-10-14T15:30:00"`), hora del servidor, como en el resto de la API.
- El backend rechaza campos desconocidos (`JacksonConfig` activa `FAIL_ON_UNKNOWN_PROPERTIES`). Si el cliente envía, por ejemplo, `unitPrice`, recibe `400 INVALID_REQUEST_BODY`. **El cliente no envía precios.**

### 2.2 Request: `POST /api/v1/marketplace/orders`

```json
{
  "clientRequestId": "0b6f7a52-2f0e-4a3c-9d2e-6a1f8f6a4c11",
  "addressId": "6c1d1b8e-3b7a-4f53-a1c4-0f8f3f2f9a10",
  "paymentMethod": "SIMULATED_CARD",
  "simulatedOutcome": "APPROVED",
  "items": [
    { "productId": "8a0c…", "branchId": "41d2…", "quantity": 2 },
    { "productId": "c93e…", "branchId": "77aa…", "quantity": 1 }
  ]
}
```

| Campo | Tipo | Reglas | Error si falla |
|---|---|---|---|
| `clientRequestId` | UUID | Obligatorio. Lo genera el cliente una vez por intento de compra (`crypto.randomUUID()`) y lo reutiliza si reenvía la misma compra. Es la clave de idempotencia | 400 `VALIDATION_ERROR` (`clientRequestId`) |
| `addressId` | UUID | Obligatorio. Es el `id` de una dirección de `GET /api/v1/me/addresses` del mismo usuario | 400 `VALIDATION_ERROR`; ajena o borrada: 404 `ADDRESS_NOT_FOUND` |
| `paymentMethod` | string | Obligatorio: `SIMULATED_CARD`, `SIMULATED_TRANSFER` o `SIMULATED_CASH` | 400 `VALIDATION_ERROR` si está vacío; otro valor o desactivado: 400 `PAYMENT_METHOD_NOT_SUPPORTED` |
| `simulatedOutcome` | string, opcional | `APPROVED` (por defecto) o `REJECTED`. Solo existe porque el único proveedor es el simulador | 400 `VALIDATION_ERROR` (`simulatedOutcome`) |
| `items` | array | De 1 a 50 líneas. No se repite el par `productId`+`branchId` | 400 `VALIDATION_ERROR` (`items`); repetido: 400 `CHECKOUT_DUPLICATE_LINE` |
| `items[i].productId` | UUID | Obligatorio | 400 `VALIDATION_ERROR` (`items[0].productId`) |
| `items[i].branchId` | UUID | Obligatorio. Es la sucursal del listado que se agregó al carrito | 400 `VALIDATION_ERROR` (`items[0].branchId`) |
| `items[i].quantity` | entero | Entre 1 y 99 | 400 `VALIDATION_ERROR` (`items[0].quantity`) |

### 2.3 Response `201 Created` (y `200 OK` en una repetición idempotente)

- Cabecera `Location: /api/v1/me/orders/{orderId}`. BL-B2 (MK-B3) creará ese recurso más adelante.
- `201`: el pedido se creó en esta petición.
- `200`: ya existía un pedido de este usuario con el mismo `clientRequestId`. El cuerpo es idéntico y no hubo cobro ni descuento de stock nuevos. El frontend trata `200` y `201` igual.

```json
{
  "orderId": "3f9b2c1e-7d4a-4c5b-8e1f-2a3b4c5d6e7f",
  "orderNumber": 1042,
  "status": "CONFIRMED",
  "createdAt": "2026-10-14T15:30:00",
  "currency": "COP",
  "subtotal": 230000.00,
  "shippingTotal": 0.00,
  "total": 230000.00,
  "payment": {
    "status": "APPROVED",
    "method": "SIMULATED_CARD",
    "methodName": "Tarjeta simulada",
    "reference": "0b6f7a52-2f0e-4a3c-9d2e-6a1f8f6a4c11",
    "amount": 230000.00,
    "paidAt": "2026-10-14T15:30:00"
  },
  "deliveryAddress": {
    "addressLine": "Calle 10 # 20-30",
    "addressComplement": "Apto 401",
    "city": "Bogotá",
    "department": "Cundinamarca",
    "country": "Colombia",
    "postalCode": "110111"
  },
  "sales": [
    {
      "saleId": "a1b2…",
      "storeId": "s1…",
      "storeName": "Repuestos El Cruce",
      "branchId": "41d2…",
      "branchName": "Sede Norte",
      "status": "PENDING",
      "subtotal": 180000.00,
      "shippingCost": 0.00,
      "total": 180000.00,
      "items": [
        {
          "productId": "8a0c…",
          "name": "Pastillas de freno delanteras",
          "partNumber": "BP-1234",
          "quantity": 2,
          "unitPrice": 90000.00,
          "lineTotal": 180000.00
        }
      ]
    }
  ]
}
```

Semántica:

- `items[].unitPrice` es el precio efectivo por unidad (con la promoción vigente aplicada), el mismo valor que `ProductResponse.effectivePrice` en `GET /marketplace/products/{id}`. Es lo que se guarda en `order_details.unit_price`.
- `lineTotal` es `unitPrice × quantity`. `sales[].subtotal` es la suma de los `lineTotal` de la venta. `shippingCost` es `0.00` en el MVP. `sales[].total` es `subtotal + shippingCost`.
- `subtotal` del pedido es la suma de `sales[].subtotal`. `shippingTotal` es la suma de `shippingCost`. `total` es `subtotal + shippingTotal`, y `payment.amount` es igual a `total`.
- El pedido no informa el precio de lista ni el nombre de la promoción, porque no se guardan. Así BL-B2 (`GET /me/orders/{id}`) podrá devolver exactamente esta misma forma.
- `status` del pedido es `CONFIRMED` (pagado). `sales[].status` empieza en `PENDING`: el almacén lo pasa a `CONFIRMED` con el flujo existente de `SaleStatusService`.
- Orden: `sales` por `storeName` y luego `branchName`. `items` por `name`.

### 2.4 Errores

Todos usan `{ "error": { "code", "message", "details" } }`.

| HTTP | `code` | Cuándo | `details` |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | Falla la Bean Validation del request | Mapa campo → mensaje, p. ej. `{ "items[0].quantity": "La cantidad debe estar entre 1 y 99." }` |
| 400 | `INVALID_REQUEST_BODY` | JSON mal formado, UUID inválido o campo desconocido (p. ej. `unitPrice`) | `null` |
| 400 | `CHECKOUT_DUPLICATE_LINE` | Se repite el par `productId`+`branchId` | `{ "items": [ { "productId", "branchId" } ] }` |
| 400 | `PAYMENT_METHOD_NOT_SUPPORTED` | El método no existe, está inactivo o los pagos simulados están desactivados | `null` |
| 401 | `UNAUTHORIZED` | Falta el token o está vencido (`UnauthorizedEntryPoint`) | `null` |
| 402 | `PAYMENT_REJECTED` | El pago simulado se rechazó. **Se revirtió todo**: no hay pedido, venta, pago ni descuento de stock | `{ "paymentMethod": "SIMULATED_CARD", "amount": 230000.00, "currency": "COP" }` |
| 404 | `ADDRESS_NOT_FOUND` | `addressId` no existe, está borrada o es de otra persona (no se distingue cuál) | `null` |
| 404 | `PRODUCT_NOT_FOUND` | Una o más líneas no corresponden a un listado comprable (producto, almacén o sucursal inactivos o borrados, o un par producto-sucursal inexistente) | `{ "items": [ { "productId", "branchId" } ] }` con **todas** las líneas afectadas |
| 409 | `INSUFFICIENT_STOCK` | Una o más líneas piden más de lo disponible. Se revirtió todo | `{ "items": [ { "productId", "branchId", "requested": 3, "available": 1 } ] }` con todas las líneas afectadas |
| 409 | `CHECKOUT_LISTING_CHANGED` | El precio base del listado cambió durante la compra (carrera poco probable). Se revirtió todo y se puede reintentar | `{ "items": [ { "productId", "branchId" } ] }` |
| 409 | `CHECKOUT_DUPLICATE_REQUEST` | El `clientRequestId` ya lo usó otra persona, o dos envíos simultáneos con la misma clave chocaron | `null` |
| 500 | `INTERNAL_SERVER_ERROR` | Error inesperado | `null` |

Ejemplos:

```json
{ "error": { "code": "PAYMENT_REJECTED",
  "message": "El pago simulado fue rechazado. No se hizo ningún cobro ni se creó el pedido; puedes intentarlo con otro método.",
  "details": { "paymentMethod": "SIMULATED_CARD", "amount": 230000.00, "currency": "COP" } } }
```

```json
{ "error": { "code": "INSUFFICIENT_STOCK",
  "message": "No hay unidades suficientes de uno o más repuestos. Ajusta las cantidades e inténtalo de nuevo.",
  "details": { "items": [ { "productId": "8a0c…", "branchId": "41d2…", "requested": 3, "available": 1 } ] } } }
```

```json
{ "error": { "code": "PRODUCT_NOT_FOUND",
  "message": "Uno o más repuestos ya no están disponibles. Quítalos del carrito para continuar.",
  "details": { "items": [ { "productId": "c93e…", "branchId": "77aa…" } ] } } }
```

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "Los datos enviados no son válidos.",
  "details": { "items[0].quantity": "La cantidad debe estar entre 1 y 99." } } }
```

```json
{ "error": { "code": "ADDRESS_NOT_FOUND", "message": "No se encontró la dirección.", "details": null } }
```

> El código HTTP 402 no está en la tabla de `docs/api/error-handling.md §3`. La tarea CK-T13 lo documenta.

### 2.5 Tipos TypeScript del contrato (copiar tal cual en el frontend)

```ts
/** POST /api/v1/marketplace/orders — cuerpo. No incluir precios: el backend rechaza campos desconocidos. */
export interface PlaceOrderRequestDto {
  clientRequestId: string
  addressId: string
  paymentMethod: PaymentMethodCode
  simulatedOutcome?: 'APPROVED' | 'REJECTED'
  items: Array<{ productId: string; branchId: string; quantity: number }>
}

export type PaymentMethodCode = 'SIMULATED_CARD' | 'SIMULATED_TRANSFER' | 'SIMULATED_CASH'

/** Semilla `payment_methods` de init.sql. No hay endpoint de métodos en el MVP. */
export const PAYMENT_METHODS: ReadonlyArray<{ code: PaymentMethodCode; name: string }> = [
  { code: 'SIMULATED_CARD', name: 'Tarjeta simulada' },
  { code: 'SIMULATED_TRANSFER', name: 'Transferencia simulada' },
  { code: 'SIMULATED_CASH', name: 'Efectivo simulado' },
]

/** Respuesta 201/200. */
export interface BuyerOrderDto {
  orderId: string
  orderNumber: number
  status: string            // 'CONFIRMED'
  createdAt: string         // LocalDateTime sin zona
  currency: 'COP'
  subtotal: number
  shippingTotal: number
  total: number
  payment: {
    status: string          // 'APPROVED'
    method: PaymentMethodCode
    methodName: string
    reference: string
    amount: number
    paidAt: string | null
  }
  deliveryAddress: {
    addressLine: string
    addressComplement: string | null
    city: string
    department: string
    country: string
    postalCode: string | null
  }
  sales: Array<{
    saleId: string
    storeId: string
    storeName: string
    branchId: string
    branchName: string
    status: string          // 'PENDING'
    subtotal: number
    shippingCost: number
    total: number
    items: Array<{
      productId: string
      name: string
      partNumber: string | null
      quantity: number
      unitPrice: number
      lineTotal: number
    }>
  }>
}

/** `details` de los errores con líneas. */
export interface CheckoutLineRef { productId: string; branchId: string }
export interface StockShortage extends CheckoutLineRef { requested: number; available: number }
```

Direcciones (endpoints que ya existen en `core/identity/web/MeController`):

```ts
/** GET /api/v1/me/addresses -> PersonAddressDto[]  (sin envoltorio) */
export interface PersonAddressDto {
  id: string
  label: string
  isDefault: boolean
  addressLine: string
  addressComplement: string | null
  city: string
  department: string
  country: string
  postalCode: string | null
  latitude: number | null
  longitude: number | null
}

/** POST /api/v1/me/addresses -> 201 PersonAddressDto */
export interface PersonAddressRequestDto {
  label: string                    // obligatorio, máx. 100
  isDefault: boolean
  location: {
    addressLine: string            // obligatorio, máx. 255
    addressComplement?: string     // máx. 255
    city: string                   // obligatorio, máx. 150
    department: string             // obligatorio, máx. 150
    country?: string               // por defecto 'Colombia'
    postalCode?: string            // máx. 20
    latitude?: number              // -90..90
    longitude?: number             // -180..180
  }
}
```

### 2.6 Formato del carrito en `localStorage` (lo escribe BL-F1/BL-F2; lo lee el checkout)

```ts
/** Clave: 'autopartco.cart.v1'. Si el valor no cumple el esquema, se trata como carrito vacío. */
export interface StoredCart {
  version: 1
  updatedAt: string                // ISO
  lines: CartLine[]
}

/** Una línea por par productId + branchId (no se repite). */
export interface CartLine {
  productId: string
  branchId: string                 // en datos mock, igual a storeId (los mocks no tienen sucursales)
  quantity: number                 // entero de 1 a 99
  // Instantánea solo para mostrar; el servidor recalcula todo:
  name: string
  storeId: string
  storeName?: string
  branchName?: string
  unitPrice: number                // precio efectivo visto al agregar (informativo)
  imageUrl?: string
  addedAt: string                  // ISO
}
```

- Quien modifique el carrito escribe la clave completa y emite `window.dispatchEvent(new Event('autopartco:cart-changed'))`. El indicador del header (BL-F1) escucha ese evento y también `storage`, que llega desde otras pestañas.
- El modelo `Product` de `features/marketplace/api/types.ts` todavía no tiene `branchId`. Exponerlo es responsabilidad de BL-F1 (en http viene de `ProductResponse.branchId`; en mock se usa `storeId`).

---

## 3. Decisiones

| # | Decisión | Justificación | Alternativas descartadas |
|---|---|---|---|
| D1 | El rechazo del pago responde **402 sin persistir nada** y BL-F4 solo existe para pedidos aprobados. El rechazo se muestra dentro de `/checkout` | Es la decisión del usuario: si se rechaza, se revierte toda la transacción. Sin pedido no hay `:id` que mostrar en BL-F4 | Guardar el pedido `REJECTED` con su pago, que contradice la decisión del usuario. Usar `/pedido/rechazado/resultado`, que mete una pseudo-id en la ruta |
| D2 | Idempotencia con `clientRequestId` en el **cuerpo**, guardado como `payments.provider_reference` (la restricción `UNIQUE(provider, provider_reference)` ya existe) | No requiere cambios de esquema ni de CORS. La base de datos garantiza la unicidad. Como un rechazo no deja rastro, se puede reintentar con la misma clave | Cabecera `Idempotency-Key`: requiere una tabla nueva para guardar respuestas. Sin idempotencia: un doble clic crea dos pedidos y cobra dos veces |
| D3 | El precio sale de `ListingQueries` (nuevo `purchasableListings`, mismo `toResponses` que `effectivePrice`). Se lee **antes** del `UPDATE` de stock y el `UPDATE … RETURNING price` confirma que el precio base no cambió | Es el mismo cálculo que ve el comprador. `publishedListings` filtra `stock > 0` y ocultaría el producto después de descontarlo | Recalcular el precio después del `UPDATE`: el listado podría quedar en stock 0 y desaparecer de la consulta publicada. Aceptar el precio del cliente: prohibido |
| D4 | Descuento con `UPDATE inventory SET stock = stock - :n, version = version + 1 … WHERE … AND stock >= :n RETURNING id, stock, price`, recorriendo las líneas ordenadas por `(branchId, productId)` | Es atómico y bloquea las filas hasta el commit. El orden fijo evita interbloqueos entre compras simultáneas. Subir `version` mantiene coherente el `@Version` de la entidad `Inventory`, que otros flujos editan con JPA | `SELECT … FOR UPDATE` y luego restar en Java: dos viajes por línea. Bloqueo optimista con JPA: requiere entidades y reintentos |
| D5 | Se recorren **todas** las líneas antes de fallar, y se reportan todas las faltantes en `details.items` | El usuario corrige el carrito una sola vez. Un `UPDATE` que afecta 0 filas no aborta la transacción en PostgreSQL | Fallar en la primera línea: obliga a varios intentos |
| D6 | El pago se simula **antes** de insertar el pedido, con un puerto `CheckoutPaymentGateway` y su implementación `SimulatedCheckoutPaymentGateway` en `marketplace.checkout` | Las integraciones van detrás de un adaptador, así que Wompi podrá reemplazarlo. Si se rechaza, solo hay que revertir el stock | Llamar al simulador desde `core.order`: mezcla el dominio con el proveedor. Usar `core.billing`: es la facturación de suscripciones de almacenes |
| D7 | `core.order` escribe y lee las tablas de pedido con JDBC: `OrderPlacement` (escritura) y `BuyerOrderQueries.receipt` (lectura, la reutilizará BL-B2) | Respeta la decisión del usuario (sin JPA nuevo) y el patrón de `SaleQueries`. `core.order` sigue dependiendo solo de `shared` y `core.notification` | Poner el SQL en `marketplace`: duplicaría la propiedad de las tablas de pedido |
| D8 | Las clases nuevas sobre tablas de pedido usan `JdbcClient`. Los métodos que se añaden a clases existentes (`ListingQueries`, `StockService`) usan su mismo estilo: `JdbcTemplate` en `ListingQueries` y `JdbcClient` inyectado en `StockService` | Es la decisión del usuario, y no se mezclan estilos dentro de una misma clase | Migrar `SaleQueries` a `JdbcClient`: fuera de alcance |
| D9 | El envoltorio de éxito no lleva `data` | Coherente con `conventions.md` §6 y con todos los endpoints y servicios http actuales | Envoltorio `{ data }`: descartado en `conventions.md` |
| D10 | Se añade un interruptor `app.checkout.simulated-payments-enabled` (`@Value` con valor por defecto `true`, sin tocar `application.yaml`) | Permite apagar el simulador en un entorno real sin desplegar código. Si está apagado, responde 400 `PAYMENT_METHOD_NOT_SUPPORTED` | Ninguno: el simulador siempre activo es un riesgo en producción |
| D11 | El costo de envío es `0.00` y la moneda es `COP`, fija | MVP solo para Colombia, sin tarifas de envío definidas | Calcular el envío: no hay modelo de tarifas |
| D12 | Para mostrar BL-F4 tras recargar, se guarda un **resumen sin dirección** en `sessionStorage` (`autopartco.order-result.<orderId>`) hasta que exista BL-B2 | La sesión http vive en memoria y se pierde al recargar. El resumen no contiene datos personales de entrega | Guardar el pedido completo en `localStorage`: persiste datos personales. Depender de BL-B2: todavía no existe |

---

## 4. Backend (agente `backend`; después, `qa-seguridad`)

### 4.1 Componentes y firmas

Rutas relativas a `backend/src/main/java/com/autopartco/backend/`. No se tocan los `package-info.java` de los módulos: las dependencias nuevas (`marketplace` → `core.identity`, `core.catalog`, `core.inventory` y `core.order`) ya están permitidas.

**`shared/error`** (CK-T1)

```java
// Nuevo. 402. Lo mapea GlobalExceptionHandler.
public class PaymentRequiredException extends RuntimeException {
    public PaymentRequiredException(String code, String message, Object details) { … }
    public String getCode(); public Object getDetails();
}
// ConflictException y NotFoundException: nuevo constructor (code, message, Object details) y getDetails().
// El constructor actual (code, message) se conserva con details = null.
```

`GlobalExceptionHandler`:

- `handleConflict` y `handleNotFound` pasan `exception.getDetails()`.
- Nuevo `handlePaymentRequired` que responde `HttpStatus.PAYMENT_REQUIRED`.

**`core/inventory`** (CK-T2)

```java
public record StockDecrement(UUID inventoryId, int newStock, BigDecimal price) { }

// StockService (se añade un JdbcClient al constructor)
/** UPDATE condicional atómico; vacío si no hay listado vivo o si stock < units. Debe correr dentro de la transacción del llamador. */
@Transactional
public Optional<StockDecrement> decrementIfAvailable(UUID branchId, UUID productId, int units);
/** Stock actual del listado vivo, o 0 si no existe. Solo para los detalles de error. */
@Transactional(readOnly = true)
public int availableStock(UUID branchId, UUID productId);
```

SQL obligatorio de `decrementIfAvailable`:

```sql
UPDATE inventory
SET stock = stock - :units, version = version + 1, updated_at = now()
WHERE branch_id = :branchId AND product_id = :productId
  AND deleted_at IS NULL AND stock >= :units
RETURNING id, stock, price
```

**`core/catalog`** (CK-T3)

```java
public record ListingKey(UUID productId, UUID branchId) { }

// ListingQueries
/** Listados comprables: mismos filtros que PUBLISHED_LISTING_SQL (producto, almacén y sucursal ACTIVE, sin borrar)
 *  pero SIN `i.stock > 0`, restringidos a los pares pedidos. Las claves sin listado no aparecen en el mapa. */
@Transactional(readOnly = true)
public Map<ListingKey, ProductResponse> purchasableListings(Collection<ListingKey> keys);
```

Se implementa con `JdbcTemplate` y `VALUES (?, ?), …` generado según el número de claves (máximo 50, que ya valida la capa web). Reutiliza `toResponses`, así que `effectivePrice` sale idéntico al de `ProductResponse`.

**`core/identity`** (CK-T4)

```java
public record AddressSnapshot(String addressLine, String addressComplement, String city, String department,
                              String country, String postalCode, BigDecimal latitude, BigDecimal longitude) { }

// Nuevo, en el paquete raíz core.identity (API pública del módulo)
@Service
public class PersonAddressLookup {
    /** Dirección vigente de la persona; vacío si no existe, está borrada o es de otra persona. */
    @Transactional(readOnly = true)
    public Optional<AddressSnapshot> findOwned(UUID personId, UUID addressId);
}
```

Usa `PersonAddressRepository.findByIdAndPerson_Id`, que ya existe y respeta `@SQLRestriction`.

**`core/order`** (CK-T5)

```java
public record NewOrder(UUID personId, DeliveryAddress address, String paymentMethodCode,
                       String paymentReference, List<NewSale> sales) {
    public record DeliveryAddress(String addressLine, String addressComplement, String city, String department,
                                  String country, String postalCode, BigDecimal latitude, BigDecimal longitude) { }
    public record NewSale(UUID branchId, BigDecimal subtotal, BigDecimal shippingCost, BigDecimal total,
                          List<NewLine> lines) { }
    public record NewLine(UUID inventoryId, int units, BigDecimal unitPrice) { }
}

public record PaymentReferenceOwner(UUID orderId, UUID personId) { }

/** Escritura del pedido del comprador con JdbcClient. No valida permisos, precios ni stock: lo hace el caso de uso. */
@Service
public class OrderPlacement {
    @Transactional(readOnly = true) public boolean isActivePaymentMethod(String code);
    /** Pago con provider 'SIMULATED' y esa referencia, si existe. */
    @Transactional(readOnly = true) public Optional<PaymentReferenceOwner> findByPaymentReference(String reference);
    /** Inserta orders(CONFIRMED), order_addresses, sales(PENDING) y order_details por venta, payments(APPROVED,
     *  provider 'SIMULATED', currency 'COP', amount = suma de totales, paid_at now()) y payment_events(APPROVED).
     *  Devuelve orderId. Si la referencia está repetida, propaga DuplicateKeyException. */
    @Transactional public UUID placeApproved(NewOrder order);
}

/** Lecturas del pedido desde el punto de vista del comprador (BL-B2, MK-B3, añadirá list/detail aquí). */
@Service
public class BuyerOrderQueries {
    /** Pedido de esa persona con la forma de §2.3; si no existe o es ajeno, NotFoundException("ORDER_NOT_FOUND"). */
    @Transactional(readOnly = true) public BuyerOrderResponse receipt(UUID personId, UUID orderId);
}

public record BuyerOrderResponse(UUID orderId, long orderNumber, String status, LocalDateTime createdAt,
        String currency, BigDecimal subtotal, BigDecimal shippingTotal, BigDecimal total,
        Payment payment, DeliveryAddress deliveryAddress, List<Sale> sales) {
    public record Payment(String status, String method, String methodName, String reference,
                          BigDecimal amount, LocalDateTime paidAt) { }
    public record DeliveryAddress(String addressLine, String addressComplement, String city,
                                  String department, String country, String postalCode) { }
    public record Sale(UUID saleId, UUID storeId, String storeName, UUID branchId, String branchName,
                       String status, BigDecimal subtotal, BigDecimal shippingCost, BigDecimal total,
                       List<Item> items) { }
    public record Item(UUID productId, String name, String partNumber, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal) { }
}
```

- Los ids de estados y métodos se resuelven por código en el propio SQL: `(SELECT id FROM states WHERE code = 'CONFIRMED')`, `payment_methods.code`, `payment_statuses.code`.
- `storeName` sale de `store.bussnies_name` (así se llama la columna).
- `lineTotal` se calcula en SQL como `units * unit_price`, o en Java con escala 2.

**`marketplace/checkout`** (CK-T6)

```java
public record PlaceOrderRequest(
        @NotNull(message = "El identificador de la solicitud es obligatorio.") UUID clientRequestId,
        @NotNull(message = "La dirección es obligatoria.") UUID addressId,
        @NotBlank(message = "El método de pago es obligatorio.") String paymentMethod,
        @Pattern(regexp = "APPROVED|REJECTED", message = "El resultado simulado debe ser APPROVED o REJECTED.") String simulatedOutcome,
        @NotNull @Size(min = 1, max = 50, message = "El pedido debe tener entre 1 y 50 líneas.") @Valid List<Item> items) {
    public record Item(@NotNull UUID productId, @NotNull UUID branchId,
                       @NotNull @Min(value = 1, message = "La cantidad debe estar entre 1 y 99.")
                       @Max(value = 99, message = "La cantidad debe estar entre 1 y 99.") Integer quantity) { }
}

public record PaymentAttempt(String methodCode, BigDecimal amount, String currency, String reference,
                             String simulatedOutcome) { }
public enum PaymentDecision { APPROVED, REJECTED }

public interface CheckoutPaymentGateway {
    boolean enabled();
    PaymentDecision charge(PaymentAttempt attempt);
}

@Component
class SimulatedCheckoutPaymentGateway implements CheckoutPaymentGateway {
    // enabled() = @Value("${app.checkout.simulated-payments-enabled:true}")
    // charge(): "REJECTED".equals(simulatedOutcome) ? REJECTED : APPROVED. Sin llamadas externas ni aleatoriedad.
}

public record CheckoutResult(BuyerOrderResponse order, boolean replayed) { }

/** Caso de uso BL-B1. Una sola transacción. */
@Service
public class PlaceMarketplaceOrder {
    @Transactional
    public CheckoutResult execute(String email, PlaceOrderRequest request);
}

@RestController
public class MarketplaceOrderController {
    // POST ApiPaths.MARKETPLACE + "/orders"
    // 201 + Location ApiPaths.ME + "/orders/{orderId}", o 200 si result.replayed()
    public ResponseEntity<BuyerOrderResponse> place(@Valid @RequestBody PlaceOrderRequest request,
                                                    Authentication authentication);
}
```

Algoritmo de `PlaceMarketplaceOrder.execute` (normativo):

1. `account = accountLookup.requireByEmail(email)`; `personId = account.personId()`.
2. Si algún par `productId`+`branchId` se repite: `BadRequestException("CHECKOUT_DUPLICATE_LINE")`, con los pares en `details`. `BadRequestException` no tiene `details`: hay que añadirle el mismo constructor en CK-T1.
3. Si `!gateway.enabled() || !orderPlacement.isActivePaymentMethod(request.paymentMethod())`: 400 `PAYMENT_METHOD_NOT_SUPPORTED`.
4. `reference = request.clientRequestId().toString()`. Si `orderPlacement.findByPaymentReference(reference)` devuelve un dueño:
   - si es la misma persona, `return new CheckoutResult(buyerOrderQueries.receipt(personId, orderId), true)`;
   - si no, 409 `CHECKOUT_DUPLICATE_REQUEST`.
5. `address = personAddressLookup.findOwned(personId, addressId)`; si no existe, 404 `ADDRESS_NOT_FOUND` ("No se encontró la dirección.").
6. `listings = listingQueries.purchasableListings(keys)`. Si faltan claves, 404 `PRODUCT_NOT_FOUND` con todas las faltantes en `details.items`.
7. Por cada línea ordenada por `(branchId, productId)`: `stockService.decrementIfAvailable(...)`.
   - Si queda vacío, se acumula `{productId, branchId, requested, available = stockService.availableStock(...)}`.
   - Si `decrement.price().compareTo(listing.price()) != 0`, se acumula en "cambiados".
8. Si hay faltantes: 409 `INSUFFICIENT_STOCK`. Si no, pero hay cambiados: 409 `CHECKOUT_LISTING_CHANGED`. Lanzar la excepción revierte los `UPDATE`.
9. Agrupar por `branchId`:
   - `unitPrice = listing.effectivePrice()`;
   - `lineTotal = unitPrice × quantity`, escala 2, `HALF_UP`;
   - `subtotal` es la suma; `shippingCost = 0.00`; `total = subtotal`.
   - `orderTotal` es la suma de los totales.
10. `gateway.charge(new PaymentAttempt(method, orderTotal, "COP", reference, outcome))`. Si es `REJECTED`: `PaymentRequiredException("PAYMENT_REJECTED", …, {paymentMethod, amount, currency})`, y se revierte todo. Se registra en log nivel INFO solo la referencia y el monto, sin correo ni dirección.
11. `orderId = orderPlacement.placeApproved(newOrder)`. Una `DuplicateKeyException` se convierte en 409 `CHECKOUT_DUPLICATE_REQUEST`.
12. `return new CheckoutResult(buyerOrderQueries.receipt(personId, orderId), false)`.

### 4.2 Tareas de backend

Las tareas se ejecutan **en orden** (CK-T1 → CK-T6), porque todas editan el área del agente `backend` y no puede haber dos ediciones simultáneas. Las pruebas se corren desde `backend/` con `./mvnw -q test -Dtest=<Clase>` y **nunca** sin `-Dtest`: `BackendApplicationTests` modifica la base de datos de `backend/.env`. Ninguna tarea ejecuta comandos contra la base de datos.

---

#### CK-T1 — Errores con `details` y 402

- **Modelo:** Haiku. **Agente:** `backend`.
- **Archivos:**
  - `backend/src/main/java/com/autopartco/backend/shared/error/{BadRequestException,ConflictException,NotFoundException,GlobalExceptionHandler}.java`;
  - nuevo `shared/error/PaymentRequiredException.java`;
  - `backend/src/test/java/com/autopartco/backend/shared/error/GlobalExceptionHandlerTest.java`.
- **Contexto mínimo:**
  - El formato de error es `ErrorResponse(ApiError(code, message, details))`.
  - Añade a `BadRequestException`, `ConflictException` y `NotFoundException` un constructor `(String code, String message, Object details)` y `getDetails()`, sin romper el constructor actual (`details = null`).
  - Crea `PaymentRequiredException(code, message, details)`.
  - En `GlobalExceptionHandler`, los handlers de 400, 404 y 409 de esas clases devuelven `getDetails()`; el nuevo handler responde `HttpStatus.PAYMENT_REQUIRED` (402).
  - No cambies los mensajes ni los handlers existentes.
- **Aceptación:**
  - `./mvnw -q test -Dtest=GlobalExceptionHandlerTest` pasa, con pruebas nuevas que comprueban: (a) 402 con code, message y details; (b) 409 con details no nulos; (c) 404 y 400 creados con el constructor antiguo siguen devolviendo `details` null.
  - `./mvnw -q test -Dtest=ModularityTests` pasa.

#### CK-T2 — Descuento atómico de stock en `core.inventory`

- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:**
  - `core/inventory/StockService.java`;
  - nuevo `core/inventory/StockDecrement.java`;
  - nuevo `backend/src/test/java/com/autopartco/backend/core/inventory/StockServiceTest.java`.
- **Contexto mínimo:**
  - Añade `JdbcClient` al constructor de `StockService` (Lombok `@AllArgsConstructor`).
  - Implementa `Optional<StockDecrement> decrementIfAvailable(UUID branchId, UUID productId, int units)` con el SQL exacto de §4.1 (`RETURNING id, stock, price`), con `@Transactional` (propagación por defecto) para unirse a la transacción del llamador.
  - Implementa `int availableStock(UUID branchId, UUID productId)` (`SELECT stock … WHERE deleted_at IS NULL`, o 0 si no existe).
  - Si `units < 1`, lanza `IllegalArgumentException`.
  - No modifiques `Inventory` ni `InventoryRepository`.
  - No registres movimientos en `inventory_movements`: el movimiento de venta dentro de `decrementIfAvailable` lo añade después SA-B5 (dueño único del ledger), sin cambiar esta firma ni el SQL.
- **Aceptación:**
  - `./mvnw -q test -Dtest=StockServiceTest` pasa, con Mockito (`RETURNS_DEEP_STUBS` para `JdbcClient` o un `ArgumentCaptor` del SQL). Comprueba que el SQL contiene `stock >= :units`, `version = version + 1` y `deleted_at IS NULL`; que una lista vacía devuelve `Optional.empty()`; y que `units = 0` lanza la excepción.
  - `./mvnw -q test -Dtest=ModularityTests` pasa.

#### CK-T3 — Listados comprables por par producto-sucursal en `core.catalog`

- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:**
  - `core/catalog/ListingQueries.java`;
  - nuevo `core/catalog/ListingKey.java`;
  - `backend/src/test/java/com/autopartco/backend/core/catalog/ListingQueriesTest.java`.
- **Contexto mínimo:**
  - Añade `Map<ListingKey, ProductResponse> purchasableListings(Collection<ListingKey> keys)`.
  - El SQL copia `PUBLISHED_LISTING_SQL` (joins, `deleted_at is null` y estados `ACTIVE` de producto, almacén y sucursal) **sin** `i.stock > 0`, y filtra con `join (values (?::uuid, ?::uuid), …) as k(product_id, branch_id) on k.product_id = i.product_id and k.branch_id = i.branch_id`.
  - Mapea las filas con `mapRow` y construye las respuestas con `toResponses`. La clave del mapa es `(productId, branchId)`.
  - Si `keys` está vacío, devuelve un mapa vacío sin consultar.
  - Usa `JdbcTemplate`, como el resto de la clase.
- **Aceptación:**
  - `./mvnw -q test -Dtest=ListingQueriesTest` pasa, con pruebas nuevas que comprueban: (a) con claves vacías no hay interacción con `jdbcTemplate`; (b) el SQL enviado no contiene `stock > 0` y sí `ps.code = 'ACTIVE'`, `ss.code = 'ACTIVE'` y `bs.code = 'ACTIVE'`; (c) `effectivePrice` refleja una promoción activa igual que en `publishedListing`.
  - `ModularityTests` pasa.

#### CK-T4 — Consulta pública de direcciones en `core.identity`

- **Modelo:** Haiku. **Agente:** `backend`.
- **Archivos:**
  - nuevos `core/identity/PersonAddressLookup.java` y `core/identity/AddressSnapshot.java`;
  - nuevo `backend/src/test/java/com/autopartco/backend/core/identity/PersonAddressLookupTest.java`.
- **Contexto mínimo:**
  - `findOwned(UUID personId, UUID addressId)` usa `PersonAddressRepository.findByIdAndPerson_Id` (en `core/identity/internal`) y mapea la `Location` de la dirección a `AddressSnapshot`.
  - Si no hay resultado, devuelve vacío. No lanza excepciones; el caso de uso decide el 404.
  - Las clases van en el paquete raíz `core.identity`, porque solo ese paquete es API pública para Spring Modulith.
- **Aceptación:**
  - `./mvnw -q test -Dtest=PersonAddressLookupTest` pasa: devuelve el snapshot de la dirección propia y vacío para una ajena.
  - `ModularityTests` pasa.

#### CK-T5 — Escritura y lectura del pedido en `core.order`

- **Modelo:** Sonnet. **Agente:** `backend`.
- **Archivos:**
  - nuevos `core/order/{NewOrder,PaymentReferenceOwner,OrderPlacement,BuyerOrderQueries,BuyerOrderResponse}.java`;
  - nuevos `backend/src/test/java/com/autopartco/backend/core/order/{OrderPlacementTest,BuyerOrderQueriesTest}.java`.
- **Contexto mínimo:**
  - Las tablas están en `database/init.sql`: `orders` (`order_number` identity), `order_addresses` (`order_id` único), `sales` (`UNIQUE(order_id, branch_id)`), `order_details`, `payments` (`UNIQUE(provider, provider_reference)`) y `payment_events`.
  - Las semillas de `states`, `payment_methods` y `payment_statuses` ya existen.
  - Usa `JdbcClient`. No crees entidades JPA.
  - Firmas y comportamiento: §4.1. Estados: pedido `CONFIRMED`, venta `PENDING`, pago `APPROVED`, evento `APPROVED` con el mensaje "Pago simulado aprobado".
  - Inserta en este orden: `orders RETURNING id` → `order_addresses` → por venta `sales RETURNING id` → `order_details` (con `order_id` y `sale_id`) → `payments RETURNING id` → `payment_events`.
  - `BuyerOrderQueries.receipt` filtra por `orders.person_id = :personId` y `deleted_at IS NULL`, y toma el último pago (`ORDER BY created_at DESC LIMIT 1`). La forma y el orden de los datos están en §2.3. Toma como referencia de estilo `SaleQueries.detail`.
  - Si no hay resultado: `NotFoundException("ORDER_NOT_FOUND", "No se encontró el pedido.")`.
- **Aceptación:**
  - `./mvnw -q test -Dtest=OrderPlacementTest,BuyerOrderQueriesTest` pasa, con mocks. Comprueba: (a) el orden de inserción y los códigos de estado en los parámetros; (b) que `amount` es la suma de los `total` de las ventas; (c) que `receipt` con otra persona lanza `ORDER_NOT_FOUND`.
  - `ModularityTests` pasa: `core.order` no importa `core.identity`, `core.catalog` ni `core.inventory`.

#### CK-T6 — Caso de uso, adaptador de pago y endpoint en `marketplace.checkout`

- **Modelo:** Opus (lógica crítica de dinero, stock y transacciones). **Agente:** `backend`.
- **Archivos:**
  - nuevos `marketplace/checkout/{PlaceOrderRequest,PaymentAttempt,PaymentDecision,CheckoutPaymentGateway,SimulatedCheckoutPaymentGateway,CheckoutResult,PlaceMarketplaceOrder,MarketplaceOrderController}.java`;
  - actualizar el Javadoc de `marketplace/checkout/package-info.java`, que hoy dice "No implementado";
  - nuevos `backend/src/test/java/com/autopartco/backend/marketplace/checkout/{PlaceMarketplaceOrderTest,SimulatedCheckoutPaymentGatewayTest}.java`.
- **Contexto mínimo:**
  - Depende de CK-T1–CK-T5.
  - El algoritmo normativo de 12 pasos está en §4.1. El contrato HTTP completo, en §2.
  - `marketplace/package-info.java` ya permite depender de `core.identity`, `core.catalog`, `core.inventory`, `core.pricing` y `core.order`; no lo modifiques.
  - El email del usuario se obtiene con `authentication.getName()`, como en `MeController`.
  - `@Transactional` va en `execute`. Todas las excepciones son `RuntimeException`, así que revierten la transacción.
  - El controlador devuelve 201 con `Location` (`ApiPaths.ME + "/orders/" + orderId`) o 200 si `replayed`.
  - Nombres de bean únicos: `ModularityTests.springBeanAndEntityNamesAreUnique`.
- **Aceptación:**
  - `./mvnw -q test -Dtest=PlaceMarketplaceOrderTest,SimulatedCheckoutPaymentGatewayTest,ModularityTests` pasa. `PlaceMarketplaceOrderTest` (Mockito) cubre como mínimo:
    1. Pedido aprobado con dos sucursales: `placeApproved` recibe dos `NewSale` con `unitPrice = effectivePrice` del listado (**no** un precio del request) y los totales correctos.
    2. `REJECTED`: lanza `PaymentRequiredException` con code `PAYMENT_REJECTED` y `placeApproved` nunca se llama.
    3. Dos líneas sin stock: `INSUFFICIENT_STOCK` con dos elementos en `details.items`, y el gateway nunca se llama.
    4. Listado faltante: 404 `PRODUCT_NOT_FOUND` sin llamar a `decrementIfAvailable`.
    5. Dirección ajena: 404 `ADDRESS_NOT_FOUND`.
    6. Línea duplicada: 400 `CHECKOUT_DUPLICATE_LINE`.
    7. Referencia existente de la misma persona: `replayed = true` sin descontar stock.
    8. Referencia existente de otra persona: 409 `CHECKOUT_DUPLICATE_REQUEST`.
    9. Precio devuelto por el `UPDATE` distinto del precio del listado: 409 `CHECKOUT_LISTING_CHANGED`.
    10. Los `decrementIfAvailable` se llaman en orden `(branchId, productId)`.
    11. Simulador desactivado: 400 `PAYMENT_METHOD_NOT_SUPPORTED`.

---

## 5. Frontend (agente `frontend`)

> **Esta sección es autocontenida.** Quien la ejecute solo necesita las secciones 1 y 2 de este documento y el repositorio. Mientras el backend no esté listo, todo se desarrolla y prueba con `VITE_MARKETPLACE_DATA_SOURCE=mock` (el valor por defecto). Con `http`, el contrato de §2 es el que implementará el backend.

### 5.1 Contexto del frontend existente (verificado en el código)

- Stack: React 19, TypeScript, Vite 8, Tailwind v4 (sin `tailwind.config.js`), componentes shadcn/Base UI en `src/components/ui`, `lucide-react` y `sonner` para toasts, React Router 8 (`createBrowserRouter` en `src/app/router.tsx`, páginas lazy en `src/app/lazy-pages.tsx`). **No añadas dependencias.**
- Selección de fuente de datos: `DATA_SOURCE` en `src/lib/api.ts`. Los servicios siguen el patrón `features/<x>/api/{types.ts, <x>-service.ts, http/…, mock/…}` con import dinámico (ver `features/seller/api/seller-service.ts`). Para latencia simulada, `simulateLatency()`.
- `apiRequest<T>(path, { method, body, token })` en `src/lib/api.ts` lanza `ApiError(message, { status, code, fieldErrors })`. Hoy **descarta** los `details` que no son cadenas, como los arrays de `items`.
- Sesión:
  - `useSession()` en `features/auth/hooks.ts` devuelve `Session | undefined`.
  - Los tokens viven solo en memoria dentro de `createHttpAuthService()` (`features/auth/api/http/http-auth-service.ts`), y la interfaz `AuthService` no los expone. Recargar la página en modo `http` cierra la sesión.
  - El login vuelve a `location.state.from` si es una ruta que empieza por `/` (`safeReturnPath` en `features/auth/pages/login-page.tsx`).
- Componentes reutilizables: `ErrorState` y `EmptyState` en `components/common/states.tsx`, `PageHeader`, `form-fields.tsx` (`TextField`, `FormAlert`), y `radio-group`, `card`, `badge`, `separator` y `skeleton` en `components/ui`. Para formato, `formatCOP` y `pluralize` en `src/lib/format.ts`.
- UX obligatoria:
  - estados de carga, vacío, error y éxito;
  - foco visible y navegación con teclado;
  - `role="alert"` en los errores;
  - microcopy en español de Colombia, accionable;
  - responsive desde 360 px;
  - el botón de pago se desactiva mientras hay una petición en curso (doble clic).

### 5.2 Componentes y firmas

```ts
// src/lib/api.ts (CK-T8): ApiError conserva el `details` crudo
export class ApiError extends Error { readonly details?: unknown /* + lo existente */ }

// features/auth/api/types.ts (CK-T8)
export interface AuthService { /* … existente … */ getAccessToken(): Promise<string | undefined> }

// features/checkout/api/types.ts (CK-T9): los tipos de §2.5 y, además:
export interface CheckoutService {
  readonly simulated: boolean
  placeOrder(input: PlaceOrderRequestDto): Promise<BuyerOrderDto>   // lanza ApiError
}
export interface AddressService {
  readonly simulated: boolean
  list(): Promise<PersonAddressDto[]>
  create(input: PersonAddressRequestDto): Promise<PersonAddressDto>
}

// features/checkout/api/checkout-service.ts (CK-T9)
export function getCheckoutService(): Promise<CheckoutService>
export function getAddressService(): Promise<AddressService>

// features/checkout/lib/cart-source.ts (CK-T9): lee y escribe la clave de §2.6
export function readCartLines(): CartLine[]                  // tolerante: si el esquema no es válido, devuelve []
export function removeCartLines(refs: CheckoutLineRef[]): void
export function setCartLineQuantity(ref: CheckoutLineRef, quantity: number): void
export function clearCart(): void                            // todas las escrituras emiten 'autopartco:cart-changed'
export function useCartLines(): CartLine[]                   // useSyncExternalStore con 'autopartco:cart-changed' y 'storage'

// features/checkout/lib/order-result-store.ts (CK-T9)
export interface OrderResultSummary {                         // BuyerOrderDto sin deliveryAddress
  orderId: string; orderNumber: number; status: string; createdAt: string; total: number
  payment: BuyerOrderDto['payment']; sales: BuyerOrderDto['sales']
}
export function saveOrderResult(order: BuyerOrderDto): void   // sessionStorage 'autopartco.order-result.<orderId>'
export function loadOrderResult(orderId: string): OrderResultSummary | undefined

// Rutas
// src/app/router.tsx: { path: 'checkout', element: <CheckoutPage /> }
//                     { path: 'pedido/:id/resultado', element: <OrderResultPage /> }
// src/features/checkout/lib/paths.ts: checkoutPath() y orderResultPath(orderId)
```

Comportamiento del servicio **mock** (`features/checkout/api/mock/mock-checkout-service.ts`). Debe ser determinista y está documentado en el JSDoc:

1. `simulatedOutcome === 'REJECTED'`: `ApiError` status 402, code `PAYMENT_REJECTED`, con `details { paymentMethod, amount, currency: 'COP' }`.
2. Una línea con `quantity > 5`: 409 `INSUFFICIENT_STOCK` con `details.items = [{ productId, branchId, requested, available: 5 }]` para **todas** esas líneas.
3. Una línea cuyo `productId` empieza por `unavailable-`: 404 `PRODUCT_NOT_FOUND` con `details.items`.
4. Un `addressId` que no está en la lista del mock de direcciones: 404 `ADDRESS_NOT_FOUND`.
5. En otro caso: un `BuyerOrderDto` con `orderNumber` incremental (desde 1001), una venta por `branchId`, `unitPrice` tomado de una tabla interna de precios por `productId` del mock del marketplace (o 50000 si no está), `status` `CONFIRMED`, ventas en `PENDING` y pago `APPROVED`. Si se repite el mismo `clientRequestId`, devuelve el mismo pedido (idempotencia).

El mock de direcciones arranca con dos direcciones (Bogotá, Cundinamarca, por defecto; Medellín, Antioquia) en memoria, y `create` las agrega.

Servicio **http**:

- `placeOrder`: `apiRequest<BuyerOrderDto>('/marketplace/orders', { method: 'POST', body, token })`.
- Direcciones: `GET /me/addresses` y `POST /me/addresses` (requiere añadir `'PUT' | 'DELETE'` a `RequestOptions.method` solo si lo necesitas; para CK-T9 basta `GET` y `POST`).
- El token se obtiene con `(await getAuthService()).getAccessToken()`. Si no hay token, lanza `ApiError` con status 401 y code `UNAUTHORIZED` sin llamar al servidor.

### 5.3 Comportamiento de las vistas

**`/checkout` (BL-F3)**

1. Sin sesión: `<Navigate to="/ingresar" replace state={{ from: '/checkout' }} />`. El carrito se conserva en `localStorage`.
2. Carrito vacío: `EmptyState` con enlace a `/buscar`.
3. Tres bloques:
   - **Resumen** agrupado por `storeName` (o `storeId`), con nombre, cantidad, precio unitario informativo y subtotal informativo, más el aviso "El total final lo confirma Autopartco al pagar".
   - **Dirección de entrega**: `RadioGroup` con las direcciones de `list()`. La predeterminada (`isDefault`) viene preseleccionada. Hay un formulario plegable "Agregar dirección" (etiqueta, dirección, complemento, ciudad, departamento, código postal) que llama a `create` y selecciona la nueva. Los `fieldErrors` de un 400 se muestran por campo.
   - **Pago simulado**: `RadioGroup` con `PAYMENT_METHODS` y un control "Resultado de la simulación" (Aprobado o Rechazado, por defecto Aprobado), con el distintivo "Demostración: no se hace ningún cobro real".
4. `clientRequestId`: `crypto.randomUUID()` al montar. Se conserva entre reintentos de la misma compra y se regenera después de un 402, 404 o 409 si el usuario cambia el carrito o la dirección. El backend no guarda nada en esos casos, así que también es válido reutilizarlo.
5. Botón "Pagar {formatCOP(total informativo)}": desactivado sin dirección o sin método, o con una petición en curso (`aria-busy`).
6. Resultados de `placeOrder`:
   - **Éxito (200/201):** `saveOrderResult(order)`, `clearCart()` y `navigate(orderResultPath(order.orderId), { state: { order } })`.
   - **402 `PAYMENT_REJECTED`:** se queda en la página y muestra un panel `role="alert"` con el texto "Pago rechazado. No se hizo ningún cobro y tu carrito sigue igual", el monto de `details`, y las acciones "Intentar con otro método" (enfoca el grupo de métodos) y "Volver al carrito" (`/carrito`).
   - **409 `INSUFFICIENT_STOCK`:** marca cada línea de `details.items` con "Solo quedan {available}". Ofrece "Ajustar cantidades" (usa `setCartLineQuantity` con `available`, o `removeCartLines` si es 0) y vuelve a habilitar el pago.
   - **404 `PRODUCT_NOT_FOUND`:** marca las líneas como "Ya no está disponible" con la acción "Quitar del carrito" (`removeCartLines`).
   - **404 `ADDRESS_NOT_FOUND`:** recarga las direcciones y pide elegir otra.
   - **409 `CHECKOUT_LISTING_CHANGED`:** mensaje "Un precio cambió mientras pagabas. Revisa el resumen e inténtalo de nuevo".
   - **409 `CHECKOUT_DUPLICATE_REQUEST`:** mensaje "Este pedido ya se está procesando. Revisa tus pedidos antes de volver a pagar", con enlace a `/`, ya que BL-F5 no existe todavía.
   - **400** (`VALIDATION_ERROR`, `CHECKOUT_DUPLICATE_LINE`, `PAYMENT_METHOD_NOT_SUPPORTED`, `INVALID_REQUEST_BODY`): `FormAlert` con `error.message`.
   - **401:** `setSession(undefined)` y redirección a `/ingresar` con `state.from = '/checkout'`.
   - **Error de red o 500:** `ErrorState` con "Reintentar" (el mismo `clientRequestId`).

**`/pedido/:id/resultado` (BL-F4)**

1. Datos: `location.state.order` si coincide con `:id`; si no, `loadOrderResult(id)`. Si no hay ninguno: `EmptyState` con "No encontramos el resumen de este pedido en este dispositivo" y enlaces a `/` y `/buscar`. Cuando exista BL-B2 se cambiará por `GET /me/orders/{id}`, pero eso queda fuera de alcance.
2. Contenido:
   - encabezado de éxito (icono y "¡Pago aprobado!") con **número de pedido `#orderNumber`** destacado;
   - total con `formatCOP`, método (`payment.methodName`), referencia y fecha;
   - una tarjeta por venta (tienda, sucursal, estado "Pendiente de confirmación por la tienda", ítems y subtotal);
   - en el estado de navegación, la dirección de entrega (no se guarda en `sessionStorage`);
   - aviso "Pago de demostración: no se realizó ningún cobro real";
   - acciones "Seguir comprando" (`/buscar`) e "Ir al inicio".
3. Mapa de estados para mostrar: `CONFIRMED` es "Confirmado", `PENDING` es "Pendiente de confirmación por la tienda" y `APPROVED` es "Aprobado". Un código desconocido se muestra tal cual.
4. El componente que muestra la venta y sus ítems se llama `OrderSalesList` y vive en `features/checkout/components/order-sales-list.tsx`, para que BL-F5 pueda reutilizarlo.

### 5.4 Tareas de frontend

Se ejecutan en orden: CK-T8 → CK-T9 → CK-T10 → CK-T11. Todas usan el agente `frontend` y no deben coincidir con BL-F1–BL-F2 (MK-F2, MK-F3) en los mismos archivos: el checkout **no** edita `features/marketplace/**` ni `src/app/site-header.tsx`.

---

#### CK-T8 — Base compartida: `details` en `ApiError` y token para servicios

- **Modelo:** Haiku. **Agente:** `frontend`.
- **Archivos:**
  - `frontend/src/lib/api.ts`;
  - `frontend/src/features/auth/api/types.ts`;
  - `frontend/src/features/auth/api/http/http-auth-service.ts`;
  - `frontend/src/features/auth/api/mock/mock-auth-service.ts`.
- **Contexto mínimo:**
  - `ApiError` debe exponer `readonly details?: unknown`, rellenado en `apiRequest` con `payload.error.details` sin perder `fieldErrors`.
  - Añade `getAccessToken(): Promise<string | undefined>` a `AuthService`:
    - en http devuelve el resultado de la función interna `accessToken()`, que ya renueva el token;
    - en mock devuelve `'mock-access-token'` si hay sesión simulada y `undefined` si no.
  - El token sigue sin guardarse en ningún almacenamiento ni en React.
  - Si la base de `RequestOptions.method` no lo admite, no añadas métodos HTTP en esta tarea.
- **Aceptación:**
  - `cd frontend && npm run build && npm run lint` terminan sin errores.
  - En el diff, `getAccessToken` no escribe en `localStorage` ni en `sessionStorage`.
  - Todas las llamadas existentes a `new ApiError(...)` siguen compilando.

#### CK-T9 — Capa de datos del checkout (tipos, mock, http y fuente del carrito)

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:**
  - nuevos `frontend/src/features/checkout/api/{types.ts,checkout-service.ts}`;
  - `api/mock/{mock-checkout-service.ts,mock-address-service.ts}`;
  - `api/http/{http-checkout-service.ts,http-address-service.ts}`;
  - `lib/{cart-source.ts,paths.ts,order-result-store.ts}`.
- **Contexto mínimo:**
  - Contrato completo en §2 (copia los tipos de §2.5 y §2.6 en `types.ts`). Firmas en §5.2. Reglas deterministas del mock en §5.2.
  - Patrón de selección mock/http igual a `features/seller/api/seller-service.ts`.
  - `cart-source.ts`:
    - valida el JSON de `localStorage['autopartco.cart.v1']` campo por campo;
    - descarta las líneas inválidas;
    - expone `useCartLines` con `useSyncExternalStore`, suscrito a `'autopartco:cart-changed'` y a `storage`;
    - cada escritura emite `'autopartco:cart-changed'`.
  - Para precios del mock, puedes leer `features/marketplace/api/mock/mock-data.ts` **solo en modo lectura**, sin editarlo.
- **Aceptación:**
  - `npm run build && npm run lint` sin errores.
  - Revisión del código:
    1. El mock aplica las 5 reglas de §5.2 en ese orden de prioridad.
    2. El servicio http no envía ningún campo de precio.
    3. Con un JSON corrupto en la clave, `readCartLines()` devuelve `[]` sin lanzar excepciones.
    4. Con `DATA_SOURCE = 'mock'`, el bundle no incluye `http-checkout-service` (import dinámico).
    5. `saveOrderResult` guarda en `sessionStorage` el resumen **sin** `deliveryAddress` (D12).

#### CK-T10 — Vista `/checkout` (BL-F3)

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:**
  - nuevos `frontend/src/features/checkout/pages/checkout-page.tsx` y `frontend/src/features/checkout/components/{address-selector.tsx,address-form.tsx,payment-method-picker.tsx,checkout-summary.tsx,payment-rejected-panel.tsx}`;
  - `frontend/src/app/router.tsx` y `frontend/src/app/lazy-pages.tsx` (solo para añadir las dos rutas, `checkout` y `pedido/:id/resultado`; la segunda apunta a la página de CK-T11, así que crea en esta tarea un stub mínimo `features/checkout/pages/order-result-page.tsx` que CK-T11 reemplazará).
- **Contexto mínimo:** §5.1 y §5.3 (todos los casos de `/checkout`), con los servicios de CK-T9 y `getAccessToken` de CK-T8. Reutiliza los componentes de `components/ui` y `components/common`. No toques `features/marketplace/**` ni `site-header.tsx`.
- **Aceptación:**
  - `npm run build && npm run lint` sin errores.
  - Prueba manual con `npm run dev` en modo mock y sesión simulada (ingresar en `/ingresar`), con carrito sembrado en la consola: `localStorage.setItem('autopartco.cart.v1', JSON.stringify({version:1,updatedAt:new Date().toISOString(),lines:[{productId:'p1',branchId:'b1',quantity:2,name:'Filtro',storeId:'s1',storeName:'Tienda 1',unitPrice:45000,addedAt:new Date().toISOString()}]}))`. Debe cumplirse:
    1. Sin sesión, `/checkout` redirige a `/ingresar` y vuelve después del login.
    2. Aprobado: navega a `/pedido/<id>/resultado` y el carrito queda vacío.
    3. Rechazado: se ve el panel de rechazo y el carrito sigue intacto.
    4. `quantity: 6`: se ve el aviso "Solo quedan 5" y "Ajustar cantidades" deja la cantidad en 5.
    5. `productId: 'unavailable-1'`: aparece "Quitar del carrito".
    6. Durante la petición, el botón de pago está desactivado.
    7. Todo el flujo funciona con el teclado.

#### CK-T11 — Vista `/pedido/:id/resultado` (BL-F4)

- **Modelo:** Sonnet. **Agente:** `frontend`.
- **Archivos:**
  - `frontend/src/features/checkout/pages/order-result-page.tsx` (reemplaza el stub de CK-T10);
  - nuevo `frontend/src/features/checkout/components/order-sales-list.tsx`.
- **Contexto mínimo:**
  - §5.3 (BL-F4) y la decisión D12.
  - `loadOrderResult` existe desde CK-T9 (`features/checkout/lib/order-result-store.ts`), y CK-T10 ya llama a `saveOrderResult`; no los modifiques.
- **Aceptación:**
  - `npm run build && npm run lint` sin errores.
  - Prueba manual en modo mock:
    1. Después de un pago aprobado se ven `#orderNumber`, el total, el método, las ventas y la dirección.
    2. Al recargar la página se ven el número y el total, pero no la dirección.
    3. Con un id inexistente se ve el estado vacío con enlaces.
    4. En las DevTools, `sessionStorage` no contiene `addressLine`.

---

## 6. QA (agente `qa-seguridad`, solo lectura)

#### CK-T7 — Revisión de seguridad y pruebas del backend

- **Modelo:** Opus. **Agente:** `qa-seguridad`.
- **Archivos:** ninguno (solo lectura).
- **Contexto mínimo:** §2 y §4 de este plan. Requiere CK-T1–CK-T6 terminadas.
- **Qué revisar:**
  1. El precio solo sale de `effectivePrice`.
  2. Ningún campo del request llega a SQL por concatenación.
  3. El 404 de dirección no distingue entre dirección ajena e inexistente.
  4. Los logs no contienen correo ni dirección.
  5. Ante cualquier excepción posterior al `UPDATE`, la transacción se revierte. Revisar que no existan `try/catch` que traguen excepciones.
  6. El orden de bloqueo es `(branchId, productId)`.
  7. `version = version + 1` está en el `UPDATE`.
  8. La idempotencia funciona entre personas.
- **Aceptación:**
  - Desde `backend/`: `./mvnw -q test -Dtest=GlobalExceptionHandlerTest,StockServiceTest,ListingQueriesTest,PersonAddressLookupTest,OrderPlacementTest,BuyerOrderQueriesTest,PlaceMarketplaceOrderTest,SimulatedCheckoutPaymentGatewayTest,ModularityTests` termina en verde. El informe incluye la salida.
  - Informe de hallazgos con su severidad.
  - Las pruebas manuales con `curl` contra la base de datos de desarrollo (aprobado, rechazado, stock insuficiente, repetición idempotente, dos compras simultáneas del último ítem) **solo se ejecutan con autorización explícita del usuario**. Mientras tanto se entregan como guion.

#### CK-T12 — Verificación del frontend

- **Modelo:** Sonnet. **Agente:** `qa-seguridad`.
- **Archivos:** ninguno.
- **Contexto mínimo:** §5 y CK-T8–CK-T11 terminadas.
- **Aceptación:**
  - `cd frontend && npm run build && npm run lint` en verde.
  - Recorrido de los 7 casos de CK-T10 y los 4 de CK-T11 en modo mock, con evidencia (con Playwright, ya instalado por el usuario).
  - Revisión de accesibilidad: foco tras error, `role="alert"`, etiquetas en los grupos de opciones y uso a 360 px.
  - Cuando CK-T6 esté desplegado en local: repetir el flujo aprobado y rechazado con `VITE_MARKETPLACE_DATA_SOURCE=http`.

#### CK-T13 — Documentar el 402 (opcional)

- **Modelo:** Haiku. **Agente:** `redactor` (OpenCode, skill `opencode-delegate`), o el usuario.
- **Archivos:** `docs/api/error-handling.md`.
- **Contexto:** añadir la fila `402 Payment Required` a la tabla §3 y una sección corta con el ejemplo `PAYMENT_REJECTED` de §2.4 de este plan.
- **Aceptación:** el documento lista el 402 con un ejemplo idéntico al del contrato.

### Orden y paralelismo

- Backend: CK-T1 → CK-T2 → CK-T3 → CK-T4 → CK-T5 → CK-T6 → CK-T7.
- Frontend: CK-T8 → CK-T9 → CK-T10 → CK-T11 → CK-T12. Puede arrancar **en paralelo** con el backend, porque son áreas distintas y usa el mock. La verificación `http` de CK-T12 espera a CK-T6.
- CK-T13 puede hacerse en cualquier momento.

---

## 7. Riesgos y puntos a verificar

1. **Sin pruebas de integración contra PostgreSQL.** El repositorio no tiene Testcontainers, y `BackendApplicationTests` toca la base de datos real. El `UPDATE` condicional, las restricciones únicas y la reversión solo se prueban con mocks y con el guion manual de CK-T7. Añadir Testcontainers es una dependencia nueva y **requiere aprobación**.
2. **Simulador expuesto en producción.** Cualquier usuario autenticado puede crear pedidos "pagados" con `simulatedOutcome`. El interruptor `app.checkout.simulated-payments-enabled` existe, pero su valor por defecto es `true`. Antes de etiquetar una versión para `main` hay que decidir si se desactiva (**requiere aprobación**: despliegue y seguridad).
3. **Llamada al proveedor dentro de la transacción.** Con el simulador no importa. Con Wompi habrá que separarlo en un pedido `PENDING` más un webhook idempotente, sin mantener bloqueos de stock durante la llamada externa. No se debe reutilizar este flujo síncrono para un proveedor real.
4. **Diferencia entre el precio del carrito y el cobrado.** Si una promoción vence entre la vista del carrito y el pago, el servidor cobra el precio nuevo sin pedir confirmación. La respuesta muestra el monto real. Si molesta en la demo, se puede añadir más adelante un `expectedTotal` con 409, como decisión aparte.
5. **Sin límite de frecuencia** en `POST /marketplace/orders`. Para el MVP lo mitigan la autenticación y el límite de 50 líneas × 99 unidades. Pendiente para seguridad.
6. **Migraciones sin aplicar** (`database/migrations/U202610130925__tenant_isolation_triggers.sql` y `…0930__append_only_tables.sql`). El diseño ya las cumple (`order_details` con la misma sucursal y pedido que la venta, un solo pago `APPROVED` por pedido, `payment_events` sin `UPDATE`). Hay que verificarlo de nuevo cuando se apliquen.
7. **`ProductResponse` del detalle devuelve solo el listado más barato.** BL-F1 debe guardar el `branchId` de ese listado. Si el comprador quiere otra sucursal, la UI actual no lo permite (fuera de alcance).
8. **Sesión http en memoria.** Recargar en `/checkout` obliga a volver a ingresar. El carrito sobrevive porque está en `localStorage`. En BL-F4, después de recargar, la dirección no se muestra (D12).
9. **`SIMULATED_CASH`** se aprueba al instante, aunque el efectivo contra entrega real no funciona así. Es aceptable para la demo.

### Decisiones del usuario (2026-10-14)

| # | Decisión | Respuesta | Efecto en el plan |
|---|---|---|---|
| 1 | Pagos simulados en builds publicados a `main` | **Se mantienen activos para la demo** | `app.checkout.simulated-payments-enabled` queda en `true`. El riesgo 2 se acepta hasta después de la demo; desactivarlo exige una nueva decisión |
| 2 | Testcontainers | **No** | No se añade la dependencia. El `UPDATE` atómico y la reversión se cubren con pruebas unitarias con mocks (CK-T6) y con el guion manual de CK-T7. El riesgo 1 sigue abierto |
| 3 | Guion `curl` de CK-T7 contra la BD de `backend/.env` | **Sí, autorizado** | CK-T7 puede ejecutarlo contra la BD de desarrollo |
| 4 | `getAccessToken()` en `AuthService` (CK-T8) | **Sí, autorizado** | CK-T8 se ejecuta tal como está descrita |
| 5 | Instalar Playwright para CK-T12 | **No hace falta: el usuario ya lo instaló** | CK-T12 usa Playwright para la evidencia, sin instalar nada |
