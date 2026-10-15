# Plan: Marketplace del MVP (comprador)

> Orden de ejecución, despliegues conjuntos y aprobaciones: [`2026-10-14-mvp-orden-global.md`](2026-10-14-mvp-orden-global.md).

- Fecha: 2026-10-14 · Base: `chore/backend-pending-migrations` (sobre `develop` `5252930`)
- Origen: `docs/architecture/mvp-backlog.md` (BL-B2, BL-B7, BL-F1, BL-F2, BL-F5, BL-F6, BL-F7)
- Reutiliza sin rehacer: `docs/architecture/2026-10-14-mvp-checkout-plan.md` (en adelante **PC**): BL-B1, BL-F3, BL-F4, tareas CK-T1–CK-T13.
- Dueño único de piezas compartidas: `PageResponse` (MK-B1, §4.0) y tokens y componentes base de UI (MK-F0, §6).
- Planes paralelos (fuera de este plan): `2026-10-14-mvp-saas-vendedores-plan.md` (portal `/portal`), `2026-10-14-mvp-admin-operaciones-plan.md` (solicitudes y `/admin`).

---

## 1. Alcance

**Entra**

| Paso | Ruta | Ítem |
|---|---|---|
| Home | `/` | existente; solo ajustes de CTA |
| Búsqueda y filtros por vehículo | `/buscar`, `/categoria/:slug` | BL-B7 + adaptación http |
| Detalle con compatibilidad | `/producto/:id` | BL-F1 (agregar al carrito) + "Mi vehículo" |
| Tienda | `/tienda/:id` | BL-F7 + endpoint de tienda pública |
| Carrito | `/carrito` | BL-F1, BL-F2 |
| Checkout y resultado | `/checkout`, `/pedido/:id/resultado` | **PC** CK-T1–CK-T11 (por referencia) |
| Mis pedidos y seguimiento | `/mis-pedidos`, `/mis-pedidos/:id` | BL-B2, BL-F5 |
| Cuenta | `/cuenta`, `/cuenta/direcciones` | BL-F6 |
| Reseñas de tienda | en `/tienda/:id` y `/mis-pedidos/:id` | P2 opcional |

**No entra**: portal del vendedor, CRUD de productos/inventario, planes SaaS, consola admin, aprobación de vendedores, pasarela real (Wompi), carrito en servidor, reservas de stock, costos de envío, búsqueda por foto, relacionados, búsqueda full-text con índices (`pg_trgm`), filtro por año, distintivo "Recomendado", cupones, cancelación por el comprador, correos.

**Supuesto de datos**: el marketplace solo muestra listados con producto, almacén y sucursal `ACTIVE` y `stock > 0` (filtro actual de `ListingQueries.PUBLISHED_*`). Un almacén `ACTIVE` es un almacén aprobado por el plan admin.

---

## 2. Estado actual verificado

### Backend

| Endpoint | Estado | Nota |
|---|---|---|
| `GET /marketplace/products` | **Parcial** | Devuelve todo el catálogo publicado, sin `q`, filtros, orden ni paginación |
| `GET /marketplace/products/{id}` | Existe | Solo el listado más barato del producto |
| `GET /marketplace/product-options[/manufacturers\|/categories\|/compatibilities]` | Existe | Categoría solo trae `code`; compatibilidad sin años |
| `GET /marketplace/stores` | **Parcial** | `StoreResponse` expone `nit` y `businessEmail`; sin ciudad ni conteo |
| `GET /marketplace/stores/{id}/brand/{assetType}` | Existe | Logo y banner |
| `GET /marketplace/stores/{id}` | **Falta** | |
| `POST /marketplace/orders` | **Falta** | Cubierto por PC (CK-T1–CK-T6) |
| `GET /me/orders`, `GET /me/orders/{id}` | **Falta** | `marketplace/tracking` solo tiene `package-info` |
| `GET/PATCH /me/profile` | Existe | `ProfileResponse`, `ProfileUpdateRequest` |
| `GET/POST/PUT/DELETE /me/addresses` | Existe | |
| `GET /me/notifications`, `PATCH /me/notifications/{id}/read` | Existe | `SaleStatusService.notifyCustomer` ya notifica (BL-B8 cubierto en backend) |
| Reseñas | **Falta** | Tabla `store_reviews` existe en `init.sql` |
| Esquema | Sin cambios | Todas las tablas existen |

### Frontend

| Vista / pieza | Estado | Nota |
|---|---|---|
| `RootLayout`, `SiteHeader`, `SiteFooter`, `Logo` (`/logo.svg`) | Existe | Header carbón con buscador, ciudad, cuenta, menú móvil |
| `/`, `/buscar`, `/categorias`, `/categoria/:slug`, `/producto/:id`, `/tiendas`, `/vender`, `/vender/registro` | Existe | |
| Servicio http del marketplace | **Parcial** | Capabilities en falso; **bug**: lee `body.message`/`body.code` en vez de `body.error.*` |
| Botón "Agregar al carrito" | **Falta** | El detalle muestra "La compra en línea aún no está disponible" |
| Indicador del carrito en header | **Falta** | |
| `/carrito` | **Falta** | |
| `/checkout`, `/pedido/:id/resultado` | **Falta** | PC CK-T8–CK-T11 |
| `/tienda/:id` | **Falta** | Hoy los enlaces van a `/buscar?store=` |
| `/mis-pedidos[/:id]` | **Falta** | |
| `/cuenta` | **Falta** | Menú de cuenta solo tiene "Cerrar sesión" |
| `apiRequest` | **Parcial** | Solo `GET`/`POST`; sin `details` (CK-T8 lo añade); sin `PUT`/`PATCH`/`DELETE` (MK-F0 los añade) |
| Fuente tipográfica | **Geist** (no Inter) | Ver D8 |

---

## 3. Componentes y responsabilidades

| Capa | Paquete / carpeta | Responsabilidad |
|---|---|---|
| Backend | `core.catalog.ListingQueries` | + `searchPublished(criteria)` y `countPublishedByStore(storeId)` |
| Backend | `shared.web` (nuevo, dueño MK-B1) | `PageResponse<T>` genérico, compartido con SaaS y admin |
| Backend | `core.catalog` (nuevos) | `ProductSearchCriteria` |
| Backend | `core.organization` (nuevos) | `PublicStoreQueries`, `PublicStoreResponse` |
| Backend | `core.order.BuyerOrderQueries` (de CK-T5) | + `list(personId, page, size)` y `detail(personId, orderId)` con envío |
| Backend | `marketplace.catalog.ProductBrowseController` | Búsqueda paginada |
| Backend | `marketplace.stores.StoreBrowseController` | Lista y detalle públicos |
| Backend | `marketplace.tracking` (nuevo `MyOrdersController`) | `/me/orders` |
| Backend | `marketplace.reviews` (P2) | Reseñas |
| Frontend | `features/marketplace` | Búsqueda http paginada, detalle con carrito, tienda, mi vehículo |
| Frontend | `features/checkout` (de PC) | Fuente del carrito (`cart-source.ts`), direcciones, checkout |
| Frontend | `features/cart` (nuevo) | `/carrito`, `CartIndicator` |
| Frontend | `features/orders` (nuevo) | `/mis-pedidos[/:id]` |
| Frontend | `features/account` (nuevo) | `/cuenta`, `/cuenta/direcciones` |
| Frontend | `components/common`, `components/ui`, `src/lib` | Tokens y componentes base del §6 (dueño único MK-F0; los usan portal y consola) |

---

## 4. Contratos

Convenciones: las de **PC §2.1** (sin envoltorio `data`, errores `{ "error": { code, message, details } }`, dinero `BigDecimal` → número JSON, moneda `COP`, fechas `LocalDateTime` sin zona, `FAIL_ON_UNKNOWN_PROPERTIES`).

### 4.0 Paginación genérica `PageResponse` (dueño único: MK-B1 backend, MK-F1 frontend)

Contrato único para toda lista paginada del MVP (marketplace, portal SaaS y consola admin). Los otros planes solo dicen "depende de MK-B1 / MK-F1".

- Query: `page` entero ≥ 1 (defecto 1) y `size` entero ≥ 1 (defecto y máximo los fija cada endpoint). Fuera de rango → 400 `VALIDATION_ERROR` con `details` por campo. Una `page` mayor que `totalPages` devuelve `items: []` con `total` correcto.
- Respuesta, sin envoltorio `data` (D9 de PC): `{ "items": [ … ], "page": 1, "size": 24, "total": 137, "totalPages": 6 }`. `totalPages = ceil(total / size)`; 0 si `total = 0`.
- Coincide con `docs/api/conventions.md` §8 (actualizado el 2026-10-14).

```java
// shared/web/PageResponse.java (MK-B1)
public record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages) {
    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total);   // calcula totalPages
}
```

```ts
// frontend/src/lib/page.ts (MK-F1)
export interface PageResponseDto<T> { items: T[]; page: number; size: number; total: number; totalPages: number }
```

### 4.1 `GET /api/v1/marketplace/products` (público, reemplaza la respuesta actual)

Query (todos opcionales):

| Param | Tipo | Regla | Filtro SQL |
|---|---|---|---|
| `q` | string ≤ 100 | trim; vacío = sin filtro | `lower(p.name) like` **o** `lower(p.part_number) like` **o** `lower(p.sku) like` **o** `lower(m.name) like`, con `%`, `_`, `\` escapados y `escape '\'` |
| `categoryId` | UUID | | `p.category_id = ?` |
| `manufacturerId` | UUID, repetible (≤ 20) | | `p.manufacturer_id in (…)` |
| `make` | string ≤ 200 | | `exists` sobre `products_compatibilies` → `compatibilities` → `make.name = ?` |
| `model` | string ≤ 100 | requiere `make` | mismo `exists`, `model.name = ?` |
| `compatibilityId` | UUID | | mismo `exists`, `co.id = ?` |
| `storeId` | UUID | | `b.store_id = ?` |
| `minPrice`, `maxPrice` | decimal ≥ 0 | `min ≤ max` | sobre `i.price` (precio base, D2) |
| `sort` | `relevance` \| `price_asc` \| `price_desc` | defecto `relevance` | `relevance` = `p.name, s.<nombre>, b.name`; precio = `i.price` y desempate `p.name, b.name` |
| `page` | int ≥ 1 | defecto 1 | |
| `size` | int 1–48 | defecto 24 | |

Todos los parámetros se enlazan con `?` (nunca concatenados). Mismos joins y filtros que `PUBLISHED_LISTINGS_SQL` (incluye `i.stock > 0`).

Response 200 (`PageResponse<ProductResponse>`, §4.0):

```json
{ "items": [ /* ProductResponse, forma actual sin cambios */ ],
  "page": 1, "size": 24, "total": 137, "totalPages": 6 }
```

Errores: 400 `VALIDATION_ERROR` (details campo → mensaje; p. ej. `size`, `maxPrice`, `model`), 400 `INVALID_REQUEST_BODY` para UUID inválido (vía `MethodArgumentTypeMismatchException`; verificar que `GlobalExceptionHandler` lo mapea; si no, añadirlo con ese código).

```java
// core.catalog
public record ProductSearchCriteria(String q, UUID categoryId, Set<UUID> manufacturerIds, String make, String model,
        UUID compatibilityId, UUID storeId, BigDecimal minPrice, BigDecimal maxPrice, Sort sort, int page, int size) {
    public enum Sort { RELEVANCE, PRICE_ASC, PRICE_DESC }
}
// ListingQueries
@Transactional(readOnly = true) public PageResponse<ProductResponse> searchPublished(ProductSearchCriteria criteria);
@Transactional(readOnly = true) public long countPublishedByStore(UUID storeId);   // listados publicados del almacén
```

### 4.2 Tiendas públicas

`GET /api/v1/marketplace/stores` → `PublicStoreResponse[]` (sin `branches`, ordenado por nombre).
`GET /api/v1/marketplace/stores/{storeId}` → `PublicStoreResponse` con `branches`.

```json
{
  "id": "…", "businessName": "Repuestos El Cruce", "state": "ACTIVE",
  "city": "Bogotá", "department": "Cundinamarca",
  "logoUrl": "/api/v1/marketplace/stores/…/brand/logo",
  "bannerUrl": null,
  "productCount": 42,
  "branches": [ { "id": "…", "name": "Sede Norte", "city": "Bogotá", "department": "Cundinamarca" } ]
}
```

- Solo almacenes `ACTIVE` y sin borrar; `branches` solo `ACTIVE` y sin borrar, por nombre.
- `city`/`department`: de la sucursal activa más antigua (`created_at`); `null` si no tiene.
- `logoUrl`/`bannerUrl`: ruta relativa a la raíz del servidor si `logo_path`/`banner_path` no es nulo; si no, `null`.
- `productCount`: `ListingQueries.countPublishedByStore`. En la lista se calcula con una sola consulta agrupada (sin N+1).
- **Se retiran `nit` y `businessEmail`** de la respuesta pública (D5). `StoreResponse` sigue intacto para `storesuite`.
- Errores: 404 `STORE_NOT_FOUND` ("No se encontró la tienda.") si no existe o no está `ACTIVE`; 400 `INVALID_REQUEST_BODY` por UUID inválido.
- Seguridad: `new PublicRoute(GET, ApiPaths.MARKETPLACE + "/stores/*")` en `WebSecurityConfig.PUBLIC_ROUTES`. La añade AD-B3 (dueño único de `WebSecurityConfig`); **requiere aprobación**.

```java
// core.organization
public record PublicStoreResponse(UUID id, String businessName, String state, String city, String department,
        String logoUrl, String bannerUrl, long productCount, List<Branch> branches) {
    public record Branch(UUID id, String name, String city, String department) { }
}
@Service public class PublicStoreQueries {
    @Transactional(readOnly = true) public List<PublicStoreResponse> published(Map<UUID, Long> productCounts);
    @Transactional(readOnly = true) public Optional<PublicStoreResponse> publishedById(UUID storeId, long productCount);
}
// core.catalog.ListingQueries
@Transactional(readOnly = true) public Map<UUID, Long> countPublishedByStores();
```

`marketplace.stores.StoreBrowseController` compone ambos (no hay dependencia `core.organization` ↔ `core.catalog`).

### 4.3 Mis pedidos (BL-B2, autenticado)

`GET /api/v1/me/orders?page=1&size=10` (size 1–50) → `PageResponse<BuyerOrderSummary>` (§4.0):

```json
{ "items": [ {
    "orderId": "…", "orderNumber": 1042, "status": "CONFIRMED", "createdAt": "2026-10-14T15:30:00",
    "currency": "COP", "total": 230000.00, "itemCount": 3,
    "sales": [ { "saleId": "…", "storeId": "…", "storeName": "Repuestos El Cruce", "status": "SHIPPED" } ] } ],
  "page": 1, "size": 10, "total": 4, "totalPages": 1 }
```

Orden: `createdAt desc`. `itemCount` = suma de `units`. Solo pedidos de `orders.person_id = personId` y `deleted_at is null`.

`GET /api/v1/me/orders/{orderId}` → `BuyerOrderResponse` de **PC §2.3** con un campo nuevo por venta:

```json
"sales": [ { "...": "campos de PC §2.3",
  "shipment": { "carrier": "SIMULATED", "trackingNumber": "AP-000123", "status": "IN_TRANSIT",
                "shippedAt": "2026-10-15T10:00:00", "deliveredAt": null,
                "events": [ { "status": "DISPATCHED", "notes": null, "occurredAt": "2026-10-15T10:00:00" } ] } } ]
```

- `shipment` es `null` si la venta no tiene envío. En `POST /marketplace/orders` (PC) siempre es `null` (cambio aditivo).
- `events` por `event_at asc`.
- Errores: 401 `UNAUTHORIZED`; 404 `ORDER_NOT_FOUND` ("No se encontró el pedido.") si no existe o es ajeno (no se distingue); 400 `VALIDATION_ERROR` por paginación.

```java
// core.order
public record BuyerOrderSummary(UUID orderId, long orderNumber, String status, LocalDateTime createdAt, String currency,
        BigDecimal total, int itemCount, List<SaleRef> sales) {
    public record SaleRef(UUID saleId, UUID storeId, String storeName, String status) { }
}
// BuyerOrderResponse.Sale (+ campo final) Shipment shipment
public record Shipment(String carrier, String trackingNumber, String status, LocalDateTime shippedAt,
                       LocalDateTime deliveredAt, List<Event> events) {          // anidado en BuyerOrderResponse
    public record Event(String status, String notes, LocalDateTime occurredAt) { }
}
// BuyerOrderQueries
@Transactional(readOnly = true) public PageResponse<BuyerOrderSummary> list(UUID personId, int page, int size);
@Transactional(readOnly = true) public BuyerOrderResponse detail(UUID personId, UUID orderId); // receipt + shipment
// marketplace.tracking.MyOrdersController: GET ApiPaths.ME + "/orders", GET ApiPaths.ME + "/orders/{orderId}"
```

### 4.4 Reseñas (P2, opcional)

`GET /api/v1/marketplace/stores/{storeId}/reviews?page=1&size=10` (público solo si se añade `PublicRoute(GET, MARKETPLACE + "/stores/*/reviews")`, que también añade AD-B3; **requiere aprobación**). Mismos campos de paginación que §4.0 más `averageRating` y `count`:

```json
{ "averageRating": 4.5, "count": 12,
  "items": [ { "rating": 5, "comment": "Llegó rápido", "authorName": "Ana M.", "createdAt": "…" } ],
  "page": 1, "size": 10, "total": 12, "totalPages": 2 }
```

`authorName` = nombre + inicial del apellido (sin datos personales completos).

`POST /api/v1/me/orders/{orderId}/reviews` (autenticado) body `{ "storeId": "uuid", "rating": 1-5, "comment": "≤ 1000, opcional" }` → 201 con el ítem.
Errores: 404 `ORDER_NOT_FOUND` (ajeno o inexistente); 409 `REVIEW_NOT_ALLOWED` (no hay venta de esa tienda en el pedido con estado `DELIVERED`); 409 `REVIEW_ALREADY_EXISTS` (`uq_review_person_store_order`); 400 `VALIDATION_ERROR`.

### 4.5 Rutas del frontend

| Ruta | Página | Sesión | Tarea |
|---|---|---|---|
| `/tienda/:id` | `features/marketplace/pages/store-page.tsx` | No | MK-F4 |
| `/carrito` | `features/cart/pages/cart-page.tsx` | No | MK-F3 |
| `/checkout`, `/pedido/:id/resultado` | PC | Sí / No | CK-T10–CK-T11 |
| `/mis-pedidos` | `features/orders/pages/orders-page.tsx` | Sí | MK-F6 |
| `/mis-pedidos/:id` | `features/orders/pages/order-detail-page.tsx` | Sí | MK-F6 |
| `/cuenta` | `features/account/pages/profile-page.tsx` | Sí | MK-F7 |
| `/cuenta/direcciones` | `features/account/pages/addresses-page.tsx` | Sí | MK-F7 |

Rutas con sesión: `src/app/require-session.tsx` → `<Navigate to="/ingresar" replace state={{ from: location.pathname + location.search }} />` si `useSession()` es `undefined`.

### 4.6 Tipos y servicios del frontend

```ts
// features/marketplace/api/dto.ts (MK-F1): la búsqueda devuelve PageResponseDto<ProductResponseDto> (§4.0)
export interface PublicStoreResponseDto {            // reemplaza StoreResponseDto (MK-F4)
  id: string; businessName: string; state: string; city: string | null; department: string | null
  logoUrl: string | null; bannerUrl: string | null; productCount: number
  branches?: Array<{ id: string; name: string; city: string | null; department: string | null }>
}
// features/marketplace/api/types.ts (MK-F4): StoreSummary + department?, bannerUrl?, branches?
// MarketplaceService (MK-F4): getStore(id: string): Promise<StoreSummary>   // 404 -> MarketplaceError status 404

// features/marketplace/lib/my-vehicle.ts (MK-F5)
/** localStorage 'autopartco.vehicle.v1'; evento 'autopartco:vehicle-changed'. Sin datos personales. */
export interface MyVehicle { version: 1; make: string; model: string; compatibilityId?: string; label: string }
export function useMyVehicle(): MyVehicle | undefined
export function setMyVehicle(v: MyVehicle | undefined): void
export function fitsMyVehicle(product: Product, v: MyVehicle): boolean   // por compatibilityId o make+model

// features/checkout/lib/cart-source.ts (MK-F2 añade; CK-T9 crea el resto)
export function addCartLine(line: Omit<CartLine, 'addedAt'>): void       // suma cantidad si existe el par; tope 99
export function cartCount(lines: CartLine[]): number                     // suma de quantity

// features/orders/api/types.ts (MK-F6)
export interface BuyerOrderSummaryDto {
  orderId: string; orderNumber: number; status: string; createdAt: string; currency: 'COP'; total: number; itemCount: number
  sales: Array<{ saleId: string; storeId: string; storeName: string; status: SaleStatus }>
}
export type BuyerOrderPageDto = PageResponseDto<BuyerOrderSummaryDto>   // src/lib/page.ts
// SaleStatus y ShipmentStatus se importan de src/lib/sale-status.ts (MK-F0)
export interface ShipmentDto { carrier: string; trackingNumber: string; status: ShipmentStatus; shippedAt: string | null
  deliveredAt: string | null; events: Array<{ status: ShipmentStatus; notes: string | null; occurredAt: string }> }
export type BuyerOrderDetailDto = Omit<BuyerOrderDto, 'sales'> & { sales: Array<BuyerOrderDto['sales'][number] & { shipment: ShipmentDto | null }> }
export interface OrdersService { readonly simulated: boolean
  list(page: number, size: number): Promise<BuyerOrderPageDto>; get(orderId: string): Promise<BuyerOrderDetailDto> }
export function getOrdersService(): Promise<OrdersService>

// features/account/api/types.ts (MK-F7)
export interface ProfileDto { accountId: string; personId: string; email: string; name: string; surname: string
  identification: string; dateBirth: string; numberPhone: string }
export type ProfileUpdateDto = Partial<Pick<ProfileDto, 'name' | 'surname' | 'identification' | 'dateBirth' | 'numberPhone'>>
export interface ProfileService { readonly simulated: boolean; get(): Promise<ProfileDto>; update(p: ProfileUpdateDto): Promise<ProfileDto> }
// AddressService de PC (features/checkout/api/types.ts) + update(id, input) y remove(id)
```

Mapa de estados de venta y envío (`frontend/src/lib/sale-status.ts`, MK-F0; único para comprador y portal SaaS: el portal reutiliza tipos y tonos y puede usar sus propios textos):

```ts
export type SaleStatus = 'PENDING' | 'CONFIRMED' | 'PREPARING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED'
export type ShipmentStatus = 'PREPARING' | 'DISPATCHED' | 'IN_TRANSIT' | 'OUT_FOR_DELIVERY' | 'DELIVERED'
export type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger'
export function saleStatusView(code: string): { label: string; tone: StatusTone }      // desconocido -> { code, 'neutral' }
export function shipmentStatusView(code: string): { label: string; tone: StatusTone }
```

| Código | Texto | Tono |
|---|---|---|
| `PENDING` | Esperando confirmación de la tienda | neutral |
| `CONFIRMED` | Confirmado | info |
| `PREPARING` | Preparando | info |
| `SHIPPED` | Enviado | info |
| `DELIVERED` | Entregado | success |
| `CANCELLED` | Cancelado | danger |
| envío `DISPATCHED` / `IN_TRANSIT` / `OUT_FOR_DELIVERY` | Despachado / En tránsito / En reparto | info |
| desconocido | el código tal cual | neutral |

Línea de tiempo de la venta: `PENDING → CONFIRMED → PREPARING → SHIPPED → DELIVERED`; `CANCELLED` corta la línea.

---

## 5. Decisiones

| # | Decisión | Descartado |
|---|---|---|
| D1 | `GET /marketplace/products` pasa a respuesta paginada (rompe la forma actual; único consumidor es el servicio http del frontend, MK-F1 lo adapta) | Endpoint nuevo `/products/search`: duplica rutas públicas |
| D2 | Filtro y orden por precio sobre `i.price` (base) en SQL; la UI muestra `effectivePrice` | Calcular precio efectivo de todos los candidatos en Java antes de paginar: costoso |
| D3 | Búsqueda con `LIKE` escapado, sin índices nuevos | `pg_trgm`/`tsvector`: extensión y migración, fuera del MVP |
| D4 | Filtro de vehículo por `make`/`model` (nombres) o `compatibilityId`; sin año | Año: la tabla `versions` no tiene año estructurado |
| D5 | Respuesta pública de tienda propia (`PublicStoreResponse`) sin `nit` ni correo | Reutilizar `StoreResponse`: expone datos que el comprador no necesita |
| D6 | Mis pedidos reutiliza `BuyerOrderResponse` de PC + `shipment` aditivo | Forma distinta para detalle: dos tipos para el mismo recurso |
| D7 | Carrito solo en `localStorage` con el formato de PC §2.6; `addCartLine` se añade al mismo módulo | Carrito en servidor: fuera del MVP |
| D8 | Se mantiene **Geist** (ya instalada y usada en todo el diseño) | Cambiar a Inter: nueva dependencia (`@fontsource-variable/inter`) y regresión visual; **requiere aprobación** si se quiere |
| D9 | "Mi vehículo" en `localStorage`, sin cuenta | Garaje en servidor: sin endpoint ni tabla |
| D10 | BL-F4 (CK-T11) consulta `GET /me/orders/{id}` en modo http tras BL-B2 (MK-B3); `sessionStorage` queda como respaldo | Mantener solo `sessionStorage` |
| D12 | `PageResponse` genérico en `shared.web` (MK-B1) para todas las listas paginadas del MVP | Un record de página por recurso (`ProductPageResponse`, `BuyerOrderPage`, `Page<T>` del SaaS, `{data, pagination}` del admin): tres formas para lo mismo |
| D13 | MK-F0 es dueño único de tokens y componentes base; portal y consola solo definen deltas | Sistemas visuales por plan: duplica `StatusBadge`, tablas y diálogos |
| D11 | Reseñas P2, solo si sobra tiempo tras QA | Incluirlas en P0 |

---

## 6. Sistema visual

**Dueño único: MK-F0.** Tokens, componentes base y reglas de estados, formularios y accesibilidad de esta sección valen para marketplace, portal SaaS (`/portal`) y consola admin (`/admin`). El SaaS (§5 de su plan) y el admin (§6 de su plan) solo definen deltas (shell y contenido propio) y dependen de MK-F0.

Base existente en `frontend/src/index.css` (`@theme inline`, sin `tailwind.config.js`). No se cambian colores de marca ni el logo (`components/common/logo.tsx` → `/logo.svg`).

**Tokens existentes (no cambiar)**: `--color-brand #c81e2c`, `--color-brand-deep`, `--color-asphalt #171b20`, `--color-paper #f5f6f8`, `--color-concrete #eceef1`, `--muted-foreground #5b6169`, `--border #dfe2e6`, `--input #878d96`, `--destructive #b42318`, `--color-stock #1d7a46`, `--color-stock-low #8a5300`, `--color-recommended #1f5f8b`, `--radius 0.5rem`, `--header-h`, utilidades `text-display`, `text-section`, `text-page-title`, `text-block-title`, `label-mono`.

**Tokens nuevos (MK-F0)**, en `@theme inline`:

```css
--color-success: #1d7a46;        --color-success-soft: #e3f2e9;   /* 5.3:1 sobre soft */
--color-warning: #8a5300;        --color-warning-soft: #fbf0dc;
--color-info: #1f5f8b;           --color-info-soft: #e4eef6;
--color-danger: var(--destructive); --color-danger-soft: #fbe9e7;
```

Cada par texto/fondo se verifica ≥ 4.5:1 y se anota en el comentario de contrastes.

**Tipografía**: Geist Variable (D8); `tabular-nums` en precios, cantidades y números de pedido; `label-mono` solo para datos (referencias, número de pedido, guía).

**Espaciado y layout**: `Container` existente; ritmo vertical de página `gap-8 lg:gap-12`; tarjetas `rounded-xl bg-card p-4 sm:p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07)]` (patrón actual del detalle).

**Densidad de gestión** (portal y consola): texto base `text-sm`, filas de tabla `h-10`, `tabular-nums` y alineación a la derecha en dinero, stock, NIT, fechas y conteos. Se activa con `density="compact"` en `DataTable`.

**Componentes base nuevos** (`components/common`, MK-F0):

| Componente | Props | Uso |
|---|---|---|
| `StatusBadge` | `{ tone: StatusTone; icon?: LucideIcon; variant?: 'soft'\|'outline'; children }` (`StatusTone` de `src/lib/sale-status.ts`) | Estados de pedido, envío, solicitud, tienda, stock; siempre texto (+ icono opcional) |
| `QuantityStepper` | `{ value; min=1; max=99; onChange; label }` | Detalle y carrito; botones ±, `aria-label`, input numérico |
| `PriceSummary` | `{ rows: {label; value: number}[]; total: number; note?: string }` | Carrito, checkout, detalle de pedido |
| `OrderTimeline` | `{ steps: {label; done; current}[] }` | Detalle del pedido (`<ol>`, `aria-current="step"`) |
| `ListSkeleton` | `{ rows: number }` | Carga de listas y tablas |
| `DataTable` | `{ caption; columns: {key; header; align?; render?}[]; rows; rowKey; rowHref?; density?: 'comfortable'\|'compact'; loading?; empty?: ReactNode; error?: ReactNode }` | Tablas de portal y consola: cabecera `sticky`, `<caption class="sr-only">`, `th scope="col"`, fila enlazable con foco visible, lista de tarjetas en `< md`; carga con `ListSkeleton`, vacío con `EmptyState`, error con `ErrorState` |
| `ConfirmDialog` | `{ open; onOpenChange; title; description?; confirmLabel; tone?: 'default'\|'destructive'; pending: boolean; onConfirm; children? }` | Confirmaciones y acciones destructivas (sobre `alert-dialog`): foco inicial en el primer campo, `Esc` cierra, el foco vuelve al disparador, botón `disabled` + `aria-busy` mientras `pending` |
| `RequireSession` | (en `src/app/require-session.tsx`) | Rutas con sesión |

**Primitivas shadcn/Base UI nuevas** (`components/ui`, MK-F0): `table`, `dialog`, `alert-dialog`, `dropdown-menu`, `tabs`, `textarea`, `switch`, `progress`, `avatar`, `sidebar`. Solo generan archivos en `components/ui/`; si alguna exige un paquete npm nuevo o el CLI modifica `package.json`, se detiene y **requiere aprobación**.

**Cliente http** (`src/lib/api.ts`, MK-F0): `RequestOptions.method` admite `'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'`; una respuesta 204 devuelve `undefined`. `FormData` ya está soportado.

Reutilizar: `ErrorState`, `EmptyState`, `PageIntro`, `Breadcrumbs`, `FormAlert`, `TextField`, `components/ui/*` (button, card, badge, radio-group, select, sheet, skeleton, pagination), `sonner` para confirmaciones breves.

**Patrones de página**

- Listado: `PageIntro` + filtros (`Sheet` en móvil, columna en `lg`) + grilla `grid-cols-2 md:grid-cols-3 xl:grid-cols-4` + `ResultsPagination`.
- Detalle: dos columnas desde `lg` (galería | compra); compatibilidad debajo; CTA fijo inferior en móvil (`sticky bottom-0`) con precio y "Agregar al carrito".
- Flujo de compra (carrito/checkout): contenido + resumen lateral `lg:sticky lg:top-[calc(var(--header-h)+1rem)]`; en móvil el resumen va al final con botón principal a todo el ancho.
- Cuenta: navegación secundaria (Perfil, Direcciones, Mis pedidos) en pestañas horizontales desplazables en móvil y columna en `lg`.

**Estados obligatorios por vista**: carga (skeleton con la forma final, no spinner global), vacío (`EmptyState` con acción), error (`ErrorState` con "Reintentar"; `role="alert"`), éxito (toast `sonner` o panel con `role="status"`). Botones con petición en curso: `disabled` + `aria-busy`.

**Breakpoints**: Tailwind por defecto (`sm 40rem`, `md 48rem`, `lg 64rem`, `xl 80rem`); diseño mínimo 360 px sin scroll horizontal.

**Microcopy**: español de Colombia, tuteo, acción concreta ("Agregar al carrito", "Ver mis pedidos"); dinero con `formatCOP` (sin decimales).

**Checklist a11y (cada tarea de UI)**

- [ ] Un `h1` por página; jerarquía sin saltos.
- [ ] Todo control accesible con teclado; foco visible (regla global existente); orden lógico.
- [ ] Tras navegar, el foco va a `#contenido` (ya existe); tras un error de envío, el foco va al `role="alert"`.
- [ ] Controles con etiqueta visible o `aria-label`; grupos de opciones con `fieldset`/`legend` o `aria-labelledby`.
- [ ] Objetivos táctiles ≥ 44 px (`h-11`).
- [ ] Contraste ≥ 4.5:1 en texto y ≥ 3:1 en bordes de control.
- [ ] Estado no solo por color (texto en `StatusBadge`).
- [ ] Imágenes con `alt`; decorativas con `alt=""`/`aria-hidden`.
- [ ] Cambios de cantidad del carrito anunciados (`aria-live="polite"` en el contador del header).
- [ ] `prefers-reduced-motion` respetado (regla global existente).

---

## 7. Tareas

Pruebas backend desde `backend/`: `./mvnw -q test -Dtest=<Clase>` (nunca sin `-Dtest`). Frontend desde `frontend/`: `npm run build && npm run lint`. Ninguna tarea ejecuta comandos contra la base de datos ni instala dependencias.

### Secuencia

Orden relativo de este plan; el intercalado con SaaS y admin está en el orden global.

- **database**: sin tareas (sin cambios de esquema; datos de demostración BL-B4 en `database/seeds/demo/`, fuera de este plan).
- **backend** (un agente, en orden): CK-T1–CK-T6 → **MK-B1 → MK-B3** → AD-B3 → **MK-B2** → (P2) MK-B4.
- **frontend** (un agente, en orden): CK-T8 → CK-T9 → **MK-F0 → MK-F1 → MK-F2 → MK-F3** → CK-T10 → CK-T11 → **MK-F4 → MK-F5 → MK-F6 → MK-F7 → MK-F8** → (P2) MK-F9.
- **qa-seguridad**: CK-T7 y CK-T12 → **MK-Q1** (tras MK-B2 y MK-B3) → **MK-Q2** (tras MK-F8).
- Backend y frontend pueden avanzar en paralelo (áreas distintas; frontend usa mock).

---

#### MK-B1 — Búsqueda paginada del catálogo

- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** nuevo `backend/src/main/java/com/autopartco/backend/shared/web/PageResponse.java`; `backend/src/main/java/com/autopartco/backend/core/catalog/{ListingQueries,ProductSearchCriteria}.java`; `marketplace/catalog/ProductBrowseController.java`; tests nuevo `backend/src/test/java/com/autopartco/backend/shared/web/PageResponseTest.java`, `backend/src/test/java/com/autopartco/backend/core/catalog/ListingQueriesTest.java`, nuevo `.../marketplace/catalog/ProductBrowseControllerTest.java`.
- **Contexto:** contratos §4.0 (dueño único de `PageResponse`; lo reutilizan MK-B3, SA-B5, AD-B2/AD-B4/AD-B6) y §4.1 de `docs/architecture/2026-10-14-mvp-marketplace-plan.md`. Se despliega junto con MK-F1. Reutilizar joins/filtros de `PUBLISHED_LISTINGS_SQL`, `mapRow` y `toResponses`. SQL dinámico solo con placeholders `?`. Dos consultas: `count(*)` y página con `limit ? offset ?`. Usar el nombre de columna del almacén vigente en la rama (`business_name` tras la rama de migraciones pendientes; verificar). `JdbcTemplate`, como el resto de la clase. Validación de parámetros con `@Validated` + `@RequestParam` restricciones o un record `@ModelAttribute` validado.
- **Aceptación:** `./mvnw -q test -Dtest=PageResponseTest,ListingQueriesTest,ProductBrowseControllerTest,ModularityTests` en verde, con pruebas que comprueban: (a) sin filtros el SQL contiene `i.stock > 0` y `limit`/`offset`; (b) `q="50%_x"` se envía escapado como parámetro y no aparece en el texto SQL; (c) `make`+`model` generan un `exists` con dos parámetros; (d) `size=49` y `minPrice>maxPrice` → 400 `VALIDATION_ERROR`; (e) `model` sin `make` → 400; (f) `totalPages = ceil(total/size)`; (g) `page` fuera de rango devuelve `items: []` con `total` correcto.

#### MK-B2 — Tienda pública (lista y detalle)

- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** nuevos `core/organization/{PublicStoreQueries,PublicStoreResponse}.java`; `core/catalog/ListingQueries.java` (+ `countPublishedByStore`, `countPublishedByStores`); `marketplace/stores/StoreBrowseController.java`; tests nuevos `core/organization/PublicStoreQueriesTest.java`, `marketplace/stores/StoreBrowseControllerTest.java`. No toca `WebSecurityConfig` (la `PublicRoute` la añade AD-B3).
- **Contexto:** depende de AD-B3 (ruta pública). Contrato §4.2 del plan. Tablas `store`, `branches`, `locations`, `states` en `database/init.sql`. `StoreResponse` y `StoreService.published()` no se modifican (los usa `storesuite`). `marketplace` ya puede depender de `core.organization` y `core.catalog`. Conteo de la lista en una sola consulta agrupada.
- **Aceptación:** `./mvnw -q test -Dtest=PublicStoreQueriesTest,StoreBrowseControllerTest,ModularityTests` en verde, comprobando: (a) el JSON no contiene `nit` ni `businessEmail`; (b) almacén no `ACTIVE` → 404 `STORE_NOT_FOUND`; (c) `logoUrl` nulo si `logo_path` es nulo; (d) la lista hace una sola llamada de conteo; (e) `./mvnw -q test -Dtest=WebSecurityConfigTest` (de AD-B3) en verde: `GET /marketplace/stores/{id}` sin token no responde 401.

#### MK-B3 — Mis pedidos (BL-B2)

- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `core/order/{BuyerOrderQueries,BuyerOrderResponse}.java` (creados por CK-T5); nuevo `core/order/BuyerOrderSummary.java` (la página es `shared.web.PageResponse` de MK-B1); nuevo `marketplace/tracking/MyOrdersController.java`; actualizar Javadoc de `marketplace/tracking/package-info.java`; tests `core/order/BuyerOrderQueriesTest.java`, nuevo `marketplace/tracking/MyOrdersControllerTest.java`.
- **Contexto:** requiere CK-T5–CK-T6 y MK-B1 terminadas. Contrato §4.3 del plan y §2.3 de `docs/architecture/2026-10-14-mvp-checkout-plan.md`. Añadir `Shipment shipment` al final de `BuyerOrderResponse.Sale` (en `receipt` va `null`). Tablas `shipments`, `shipment_statuses`, `shipment_events`. Estilo `JdbcClient` (decisión D8 de PC). Persona por `AccountLookup.requireByEmail(authentication.getName()).personId()`. Filtro siempre `orders.person_id = :personId`.
- **Aceptación:** `./mvnw -q test -Dtest=BuyerOrderQueriesTest,MyOrdersControllerTest,PlaceMarketplaceOrderTest,ModularityTests` en verde, comprobando: (a) todas las consultas incluyen `person_id`; (b) pedido ajeno → 404 `ORDER_NOT_FOUND`; (c) `itemCount` = suma de unidades; (d) `shipment` null sin envío y con eventos ordenados con envío; (e) orden `created_at desc`; (f) `size=51` → 400.

#### MK-B4 — Reseñas de tienda (P2, opcional)

- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** nuevos `marketplace/reviews/{StoreReviewController,StoreReviewService,StoreReviewRequest,StoreReviewResponse,StoreReviewPage}.java`; tests nuevos en `backend/src/test/java/com/autopartco/backend/marketplace/reviews/`. No toca `WebSecurityConfig` (ruta pública en AD-B3, **con aprobación**).
- **Contexto:** depende de AD-B3. Contrato §4.4. Tabla `store_reviews` (`uq_review_person_store_order`, `rating 1..5`). Elegibilidad: existe venta del pedido, de una sucursal de `storeId`, con estado `DELIVERED`, y el pedido es de la persona. `DuplicateKeyException` → 409 `REVIEW_ALREADY_EXISTS`.
- **Aceptación:** `./mvnw -q test -Dtest=StoreReviewServiceTest,ModularityTests` en verde: elegible → 201; no entregado → 409 `REVIEW_NOT_ALLOWED`; ajeno → 404; duplicado → 409; `authorName` = nombre + inicial.

---

#### MK-F0 — Tokens y componentes base (dueño único para marketplace, portal y consola)

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `frontend/src/index.css`; `frontend/src/lib/api.ts` (solo `method` y 204); nuevos `frontend/src/components/common/{status-badge,quantity-stepper,price-summary,order-timeline,list-skeleton,data-table,confirm-dialog}.tsx`; nuevos `frontend/src/components/ui/{table,dialog,alert-dialog,dropdown-menu,tabs,textarea,switch,progress,avatar,sidebar}.tsx`; nuevo `frontend/src/app/require-session.tsx`; nuevo `frontend/src/lib/sale-status.ts`.
- **Contexto:** depende de CK-T8 y CK-T9. §6 y mapa de estados de §4.6 del plan. Tailwind v4 sin `tailwind.config.js`; estilo de componentes como `components/ui/badge.tsx` (cva). `useSession()` en `features/auth/hooks.ts`. No añadir dependencias (primitivas shadcn sobre Base UI ya instalado; si piden paquete nuevo, parar y pedir aprobación). SA-F1 y AD-F1 consumen estos componentes y no crean equivalentes.
- **Aceptación:** build y lint en verde; contrastes nuevos anotados en el comentario de `index.css`; `QuantityStepper` no baja de `min` ni sube de `max` y sus botones tienen `aria-label`; `OrderTimeline` usa `<ol>` y `aria-current="step"`; `RequireSession` redirige a `/ingresar` con `state.from`; `DataTable` tiene `caption`, `th scope="col"` y vista de tarjetas a 360 px; `ConfirmDialog` devuelve el foco al disparador y desactiva el botón con `pending`; `apiRequest` acepta `PATCH`/`PUT`/`DELETE` y devuelve `undefined` en 204; `git diff --stat frontend/package.json` vacío.

#### MK-F1 — Búsqueda http paginada y corrección de errores

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** nuevo `frontend/src/lib/page.ts` (`PageResponseDto<T>`, dueño único; lo reutilizan MK-F6, SA-F1 y AD-F2); `frontend/src/features/marketplace/api/{dto.ts,http/http-service.ts}`; `frontend/src/features/marketplace/api/mock/mock-service.ts` (solo si hay que alinear capacidades).
- **Contexto:** se despliega junto con MK-B1. §4.0, §4.1 y §4.6. `searchProducts(query)` envía `q`, `categoryId` (resuelto desde `query.category` slug con el mapa de categorías), `manufacturerId` repetido (`query.brands`), `make`, `model`, `storeId`, `minPrice`, `maxPrice`, `sort`, `page`, `size=query.pageSize`. Capabilities http: `textSearch`, `sorting`, `pagination` en `true`; `filters = {'category','brand','vehicle','price','store'}` (sin `year`, `availability`, `recommendedStore`). Corregir lectura de errores: `body.error.message` y `body.error.code`.
- **Aceptación:** build y lint en verde; revisión: (a) ningún filtro se aplica en cliente en modo http; (b) un 404 del backend llega como `MarketplaceError` con `status 404` y el `message` del servidor; (c) con `VITE_MARKETPLACE_DATA_SOURCE=http` y MK-B1 activo, `/buscar?q=freno&page=2` muestra la página 2 (verificación manual opcional).

#### MK-F2 — Agregar al carrito e indicador en el header (BL-F1)

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `frontend/src/features/checkout/lib/cart-source.ts` (añadir `addCartLine`, `cartCount`); `frontend/src/features/marketplace/api/types.ts` (+ `branchId` y `storeId` en `Product`); `http/http-service.ts` y `mock/mock-service.ts` (mapear `branchId`; mock usa `storeId`); `frontend/src/features/marketplace/pages/product-page.tsx`; nuevo `frontend/src/features/cart/components/cart-indicator.tsx`; `frontend/src/app/site-header.tsx`.
- **Contexto:** requiere CK-T9 (`cart-source.ts`). Formato de PC §2.6 (clave `autopartco.cart.v1`, evento `autopartco:cart-changed`). En el detalle reemplazar el aviso "La compra en línea aún no está disponible" por `QuantityStepper` (máx. `min(99, availability.quantity)`) + botón "Agregar al carrito" + toast "Agregado al carrito" con acción "Ver carrito". CTA fijo inferior en móvil. `CartIndicator`: icono `ShoppingCartIcon` + contador, enlace a `/carrito`, `aria-label="Carrito, N repuestos"`, contador en `aria-live="polite"`.
- **Aceptación:** build y lint en verde; manual en mock: agregar 2 unidades crea una línea con `productId`, `branchId`, `unitPrice`; agregar de nuevo suma cantidad (tope 99 y stock); el contador se actualiza en la misma pestaña y en otra; navegable con teclado; sin scroll horizontal a 360 px.

#### MK-F3 — Página `/carrito` (BL-F2)

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** nuevo `frontend/src/features/cart/pages/cart-page.tsx`, `frontend/src/features/cart/components/cart-store-group.tsx`; `frontend/src/app/{router.tsx,lazy-pages.tsx}` (solo la ruta `carrito`).
- **Contexto:** `useCartLines`, `setCartLineQuantity`, `removeCartLines` de `features/checkout/lib/cart-source.ts`. Líneas agrupadas por `storeName` (y `branchName`); `QuantityStepper`; "Quitar"; `PriceSummary` con nota "Precios informativos; el total final lo confirma Autopartco al pagar". Botón "Continuar al pago" → `/checkout`. Vacío: `EmptyState` con enlace a `/buscar`.
- **Aceptación:** build y lint en verde; manual en mock: cambiar cantidad y quitar actualizan el header; vacío muestra el estado vacío; resumen sticky en `lg`, al final en móvil; teclado completo.

#### MK-F4 — Página de tienda `/tienda/:id` (BL-F7)

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** nuevo `frontend/src/features/marketplace/pages/store-page.tsx`; `features/marketplace/api/{dto.ts,types.ts,http/http-service.ts,mock/mock-service.ts}` (`getStore`, `PublicStoreResponseDto`); `features/marketplace/hooks.ts` (`useStore`); `features/marketplace/lib/paths.ts` (`storePath(id)`); `features/marketplace/components/store-card.tsx` y `pages/product-page.tsx` (enlaces a `storePath`); `src/app/{router.tsx,lazy-pages.tsx}`.
- **Contexto:** §4.2. Encabezado con banner/logo (fallback `initials`), nombre, ciudad, número de repuestos, sucursales; debajo, la búsqueda con `store` fijo reutilizando `search-results-view.tsx`. 404 → `EmptyState` "Esta tienda no está disponible" con enlace a `/tiendas`.
- **Aceptación:** build y lint en verde; manual en mock: tarjeta de tienda y "Vendido por" llevan a `/tienda/:id`; filtros y paginación conservan la tienda; id inexistente muestra el estado de 404.

#### MK-F5 — "Mi vehículo" y compatibilidad destacada

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** nuevo `frontend/src/features/marketplace/lib/my-vehicle.ts`, nuevo `features/marketplace/components/my-vehicle-chip.tsx`; `features/marketplace/components/{hero-search.tsx,filters-panel.tsx,product-card.tsx}`; `pages/product-page.tsx`.
- **Contexto:** firmas en §4.6 (`useMyVehicle` con `useSyncExternalStore`). Al elegir marca y modelo en `VehiclePicker`, ofrecer "Guardar como mi vehículo". Si hay vehículo: chip en el buscador que aplica `make`/`model`; en tarjeta y detalle, `StatusBadge` success "Compatible con tu {label}" o neutral "No confirmada para tu {label}". Texto: la compatibilidad la declara la tienda.
- **Aceptación:** build y lint en verde; manual: guardar, recargar y ver el chip; quitarlo; el badge cambia según `compatibilities`; no se guarda nada fuera de `autopartco.vehicle.v1`.

#### MK-F6 — Mis pedidos y seguimiento (BL-F5)

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** nuevos `frontend/src/features/orders/api/{types.ts,orders-service.ts,mock/mock-orders-service.ts,http/http-orders-service.ts}`, `features/orders/pages/{orders-page.tsx,order-detail-page.tsx}`, `features/orders/components/{order-card.tsx,sale-tracking.tsx}`; `src/app/{router.tsx,lazy-pages.tsx}`; `features/checkout/pages/order-result-page.tsx` (D10: en http usa `getOrdersService().get(id)`; respaldo `loadOrderResult`) y su enlace "Ver mis pedidos".
- **Contexto:** §4.3 y tipos §4.6 (`PageResponseDto` de MK-F1, `saleStatusView` de MK-F0). Patrón mock/http como `features/seller/api/seller-service.ts`; token con `getAccessToken()` (CK-T8). Mock: 4 pedidos fijos con ventas `PENDING`, `PREPARING`, `SHIPPED` (con envío `IN_TRANSIT`) y `DELIVERED`. Lista paginada (`size=10`) con `OrderCard` (número `#`, fecha, total, tiendas y `StatusBadge`). Detalle: `OrderSalesList` de PC + `OrderTimeline` por venta + guía y eventos de envío + dirección. 404 → `EmptyState` "No encontramos este pedido". 401 → sesión cerrada y `/ingresar`.
- **Aceptación:** build y lint en verde; manual en mock: lista con 4 pedidos y estados legibles sin color; detalle con línea de tiempo y eventos; id inexistente → estado vacío; rutas sin sesión → login y regreso.

#### MK-F7 — Cuenta: perfil y direcciones (BL-F6)

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** (los métodos `PUT`/`PATCH`/`DELETE` de `lib/api.ts` vienen de MK-F0) nuevos `frontend/src/features/account/api/{types.ts,profile-service.ts,mock/mock-profile-service.ts,http/http-profile-service.ts}`, `features/account/pages/{profile-page.tsx,addresses-page.tsx}`, `features/account/components/account-nav.tsx`; `features/checkout/api/{types.ts,mock/mock-address-service.ts,http/http-address-service.ts}` (+ `update`, `remove`); `features/checkout/components/address-form.tsx` (reutilizar en cuenta, sin cambiar su contrato con el checkout); `src/app/{router.tsx,lazy-pages.tsx}`.
- **Contexto:** endpoints existentes `GET/PATCH /me/profile`, `GET/POST/PUT/DELETE /me/addresses` (tipos en PC §2.5 y §4.6). Perfil: correo solo lectura; `PATCH` solo con campos cambiados; `fieldErrors` por campo. Direcciones: lista, crear, editar, eliminar con confirmación, marcar predeterminada. Departamento/ciudad como texto (DIVIPOLA estructurado fuera de alcance). Pie con enlace a la política de tratamiento de datos (Ley 1581) si existe la ruta; si no, texto informativo.
- **Aceptación:** build y lint en verde; manual en mock: editar nombre muestra éxito; error 400 se muestra en el campo; CRUD de direcciones con estados de carga/vacío/error; eliminar pide confirmación (`ConfirmDialog` de MK-F0) y devuelve el foco a la lista.

#### MK-F8 — Navegación de cuenta y CTA de vendedor

- **Modelo:** Haiku · **Agente:** frontend
- **Archivos:** `frontend/src/features/auth/components/account-menu.tsx`; `frontend/src/app/site-footer.tsx`.
- **Contexto:** añadir al menú de cuenta (escritorio y móvil) "Mis pedidos" (`/mis-pedidos`) y "Mi cuenta" (`/cuenta`) antes de "Cerrar sesión", con iconos Lucide `PackageIcon` y `UserIcon`. En el pie, CTA "Vende en Autopartco" → `/vender` (la solicitud `/vender/registro` pertenece al plan admin). Mantener `min-h-11`.
- **Aceptación:** build y lint en verde; manual: los enlaces aparecen solo con sesión y se recorren con teclado.

#### MK-F9 — Reseñas en tienda y pedido (P2, opcional)

- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** nuevos `frontend/src/features/reviews/{api/*,components/store-reviews.tsx,components/review-form.tsx}`; `features/marketplace/pages/store-page.tsx`; `features/orders/pages/order-detail-page.tsx`.
- **Contexto:** §4.4. Formulario solo en ventas `DELIVERED`; estrellas como `radio-group` accesible; maneja 409 `REVIEW_ALREADY_EXISTS` y `REVIEW_NOT_ALLOWED`.
- **Aceptación:** build y lint en verde; manual en mock: enviar una reseña, ver el promedio; segundo envío muestra el 409.

---

#### MK-Q1 — Revisión de seguridad del backend

- **Modelo:** Opus · **Agente:** qa-seguridad
- **Archivos:** ninguno.
- **Contexto:** §4 del plan; MK-B1–MK-B3 (y MK-B4 si existe) terminadas.
- **Revisar:** IDOR en `/me/orders/**` (siempre `person_id`); inyección SQL en la búsqueda dinámica (solo placeholders, `LIKE` escapado); límites de `size`/`q`; `PublicStoreResponse` sin `nit`/correo; nuevas `PublicRoute` mínimas; logs sin correo ni dirección.
- **Aceptación:** `./mvnw -q test -Dtest=ListingQueriesTest,ProductBrowseControllerTest,PublicStoreQueriesTest,StoreBrowseControllerTest,BuyerOrderQueriesTest,MyOrdersControllerTest,ModularityTests` en verde (salida en el informe) + informe con severidad.

#### MK-Q2 — E2E del recorrido del comprador y accesibilidad

- **Modelo:** Sonnet · **Agente:** qa-seguridad
- **Archivos:** ninguno en el código del producto; las pruebas Playwright van donde el usuario indique (raíz `tests/` ya existe con `playwright.config.ts`).
- **Contexto:** recorrido §1 en modo mock: home → buscar con vehículo → detalle → agregar → carrito → checkout aprobado → resultado → mis pedidos → detalle → cuenta/direcciones; más rechazo de pago y stock insuficiente (PC §5.3). Viewports 360 px y 1280 px.
- **Aceptación:** `npm run build && npm run lint` en verde; recorrido con capturas a 360 y 1280; checklist a11y del §6 verificado por vista; sin scroll horizontal; con backend local y autorización del usuario, repetir en `http`.

---

## 8. Riesgos

1. **Cambio de forma de `GET /marketplace/products` (D1)**: desplegar MK-B1 sin MK-F1 rompe el modo http. Publicar ambos en la misma versión.
2. **Nombre de columna del almacén**: `init.sql` usa `bussnies_name`; la rama `chore/backend-pending-migrations` cambia el código a `business_name`. MK-B1/MK-B2 deben usar el nombre vigente al implementarse.
3. **Precio base vs efectivo (D2)**: con promociones, un producto puede aparecer fuera del rango de precio mostrado u ordenado distinto. Aceptado para el MVP.
4. **Rendimiento de `LIKE '%q%'`** sin índice: aceptable con el volumen de la demo; revisar antes de producción.
5. **Detalle muestra solo el listado más barato** (PC riesgo 7): el carrito usa esa sucursal.
6. **Sesión http en memoria**: recargar en rutas con sesión pide volver a ingresar.
7. **Datos de demo** (`database/seeds/demo`) apuntan al esquema migrado; sin migraciones aplicadas puede fallar. Coordinar con el plan de migraciones.
8. **Fuente**: el contexto pide Inter y el código usa Geist (D8).

## 9. Requiere aprobación del usuario

1. Añadir `PublicRoute(GET, /marketplace/stores/*)` (para MK-B2) y, si se hace P2, `/marketplace/stores/*/reviews` (para MK-B4) en `WebSecurityConfig`; las añade AD-B3.
2. Retirar `nit` y `businessEmail` de la respuesta pública de tiendas (D5).
3. Cambio de forma de `GET /marketplace/products` (D1).
4. Mantener Geist en lugar de Inter (D8), o aprobar la dependencia `@fontsource-variable/inter`.
5. Verificación MK-Q2 en modo `http` contra la base de datos de `backend/.env`.
