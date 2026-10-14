# Backlog del MVP: implementaciones de backend y vistas de frontend

- Fecha: 2026-10-14
- Base revisada: rama `develop` (commit `50a92aa`)
- Uso: cada ítem se planifica por separado con el agente `arquitecto`.

## Estado de partida

- **Backend:** identidad, catálogo, inventario, onboarding y portal del almacén tienen API.
- **Lo que falta en backend:** carrito, checkout, "mis pedidos" y pagos de pedidos (en `marketplace/` solo hay `package-info`), además de un endpoint para que el frontend sepa el `storeId` del vendedor.
- **Frontend:** solo existen marketplace público, cuentas y la solicitud de almacén.

La ruta del MVP es: comprador busca → carrito → paga (simulado) → ve su pedido; el almacén lo gestiona hasta entregarlo.

Prioridades: **P0** obligatoria para la demo, **P1** recomendable, **P2** opcional.

## Backend

| # | Prioridad | Implementación | Qué cubre | Depende de |
|---|---|---|---|---|
| B1 | P0 | **Checkout de pedido** `POST /marketplace/orders` | Crea el pedido, la dirección, una venta por sucursal y el detalle. Descuenta stock de forma atómica. Precios calculados en el servidor. Pago `SIMULATED` aprobado o rechazado; si se rechaza, se revierte todo | — |
| B2 | P0 | **Mis pedidos** `GET /me/orders` y `GET /me/orders/{id}` | Lista y detalle del comprador: ventas por tienda, ítems, estado y envío. Un pedido ajeno responde 404 | B1 |
| B3 | P0 | **Mis tiendas** `GET /me/stores` | Devuelve `storeId`, nombre y estado de las tiendas del gestor o colaborador. Hoy no existe ninguna forma de obtenerlo | — |
| B4 | P0 | **Datos de demostración** (script SQL idempotente) | Categorías, fabricantes, vehículos y compatibilidades, unos 30 productos con inventario, y cómo dejar una cuenta como ROOT | — (lo hace el agente `database`; lo ejecuta el usuario) |
| B5 | P1 | Verificar que el portal del almacén lee los pedidos nuevos | `StoreOrderController` y `SaleQueries` deben mostrar las ventas de B1, con transiciones de estado y creación del envío | B1 |
| B6 | P1 | Movimiento de inventario `SALE` | Dejar registro en `inventory_movements` de cada descuento de stock de B1 | B1 |
| B7 | P1 | Paginación y filtros en el servidor para `GET /marketplace/products` | Hoy devuelve el catálogo completo | — |
| B8 | P1 | Notificación al comprador cuando cambia el estado de su venta | Usa `core.notification`, que ya existe, y el endpoint `/me/notifications` | B1 |

## Frontend

### Comprador

| # | Prioridad | Vista o ruta | Consume | Depende de |
|---|---|---|---|---|
| F1 | P0 | **Botón "Agregar al carrito"** en el detalle de producto e **indicador del carrito** en el header | Estado local del navegador (`localStorage`) | — |
| F2 | P0 | **`/carrito`** | Ítems agrupados por tienda, cantidades, subtotal informativo | F1 |
| F3 | P0 | **`/checkout`** (requiere sesión) | Dirección (`/me/addresses`), método de pago simulado y B1. Muestra errores de stock insuficiente (409), pago rechazado (402) y validación (400) | F2, B1 |
| F4 | P0 | **`/pedido/:id/resultado`** | Confirmación o rechazo del pago, con número de pedido | F3 |
| F5 | P0 | **`/mis-pedidos`** y **`/mis-pedidos/:id`** | B2: estado por tienda y seguimiento del envío | B2 |
| F6 | P1 | **`/cuenta`**: perfil y direcciones | `/me/profile` y `/me/addresses`, que ya existen | — |
| F7 | P1 | Página de tienda **`/tienda/:id`** | Catálogo filtrado por tienda | — |

### Portal del almacén (`/portal`)

| # | Prioridad | Vista o ruta | Consume (endpoints ya existentes salvo B3) | Depende de |
|---|---|---|---|---|
| F8 | P0 | **Layout del portal y selector de tienda** con navegación lateral y ruta protegida | B3 y `/stores/{id}/my-permissions` | B3 |
| F9 | P0 | **Dashboard** | `/stores/{id}/dashboard` | F8 |
| F10 | P0 | **Pedidos: lista y detalle** | `/stores/{id}/orders` y `/{saleId}` | F8, B1 |
| F11 | P0 | **Pedido: cambio de estado y creación o actualización del envío** | `PATCH …/status` y `POST/PATCH …/shipment` | F10 |
| F12 | P0 | **Productos: lista** | `GET /stores/{id}/products` | F8 |
| F13 | P0 | **Productos: alta y edición**, con precio y stock por sucursal y publicar o despublicar | `POST /products`, `PATCH /branches/{b}/products/{p}` y `PATCH …/publication` | F12 |
| F14 | P1 | Productos: gestión de imágenes | `/products/{id}/images` | F13 |
| F15 | P1 | **Estado de mi solicitud de almacén** | `GET /me/store-applications` | — |
| F16 | P1 | Sucursales | `/stores/{id}/branches` | F8 |
| F17 | P1 | Perfil de la tienda | `PATCH /stores/{id}` | F8 |
| F18 | P2 | Promociones, colaboradores e invitaciones, plan y suscripción | Endpoints existentes | F8 |

### Administración (ROOT)

| # | Prioridad | Vista o ruta | Consume | Depende de |
|---|---|---|---|---|
| F19 | P1 | **`/admin/solicitudes`**: listar pendientes, ver documentos, aprobar o rechazar | `/admin/store-applications/*` | — |
| F20 | P2 | Catálogo maestro (categorías y fabricantes) y planes | `/admin/categories`, `/admin/manufacturers` y `/admin/plans` | — |

F19 es P1 porque la aprobación puede hacerse con `curl` antes de la demo. Si se quiere mostrar el ciclo completo del almacén, sube a P0.

## Fuera del MVP

Flyway y las migraciones de `database/migrations/` (se mantiene `init.sql` + `ddl-auto: update`), ERP Alegra y Siigo, Wompi para pedidos, carrito guardado en el servidor y reservas de stock, inteligencia artificial, reseñas, soporte, correos de pedido y analítica.

## Orden y paralelismo

- **Rutas críticas:** B1 → B2 → F5 y B3 → F8 → F10 y F11.
- **En paralelo desde el inicio:** B4, y F1–F4 y F12–F13 con datos simulados.
- **Un mismo contrato:** B1, F3 y F4 deben planificarse juntos para que el agente arquitecto defina una sola vez el JSON de request y response que comparten.
