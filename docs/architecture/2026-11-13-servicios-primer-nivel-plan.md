# Servicios como producto de primer nivel: búsqueda, home, agenda, WhatsApp, mapa y promociones

Rama: `feature/servicios` desde `develop`. Secuencia: `database` → `backend` → `frontend` → `qa-seguridad`.
Base: plan [2026-11-11 perfil de almacén v2](2026-11-11-perfil-almacen-v2-plan.md) (T1–T4 ✅: `store_services`, `branches.whatsapp/opening_hours`, `WHATSAPP_CLICK`/`DIRECTIONS_CLICK` con `serviceId`, CRUD de servicios en API del portal). Sus T5–T6 (frontend) se integran aquí.

## Objetivo y alcance

Hoy los servicios solo aparecen dentro del perfil de una tienda. Objetivo: que tengan el mismo peso que los repuestos.

Entra:
1. **Talleres como vendedores**: un almacén declara si vende repuestos, presta servicios o ambos; un taller puede operar sin productos.
2. **Sección Servicios** (`/servicios`) al nivel del catálogo, con categorías, filtros por ciudad/cercanía/precio/promoción y página de detalle `/servicio/:id`.
3. **Búsqueda unificada**: una consulta devuelve repuestos y servicios (pestañas Todo / Repuestos / Servicios); autocompletado con servicios; la interpretación reconoce intención de servicio («alineación», «cambio de aceite»).
4. **Home**: bloque de categorías de servicio, servicios cercanos y servicios en promoción.
5. **Agenda**: el comprador solicita cita (servicio, sucursal, fecha y hora, vehículo); el almacén confirma, propone otra hora o rechaza; notificaciones a ambos.
6. **WhatsApp para servicios** con mensaje propio, y **Cómo llegar** (Google Maps, Waze, Apple Maps) en la tarjeta y el detalle del servicio.
7. **Promociones de servicios** con el motor de promociones existente.
8. **Portal**: servicios (CRUD, imagen, agenda activable), bandeja de citas y métricas de servicios.

No entra: pago en línea de servicios, calendario con capacidad por bahía/técnico, recordatorios por SMS/WhatsApp API, reseñas de servicios (se habilitan después sobre citas `COMPLETED`), anuncios patrocinados de servicios, sincronización con Google Calendar.

## Componentes

| Capa | Componente | Responsabilidad |
|---|---|---|
| BD | `store.business_type` | `PARTS`, `SERVICES`, `BOTH` |
| BD | `store_services` (+`image_url`, `booking_enabled`, `slot_minutes`) | Datos de vitrina y agenda |
| BD | `search_services` | Índice de búsqueda: una fila por servicio×sucursal |
| BD | `service_bookings` | Citas y su estado |
| BD | `promotions` (+ target `SERVICE`, `SERVICE_CATEGORY`) | Promociones de servicios |
| BD | `store_visibility_events` (+ kind `SERVICE_VIEW`) | Vistas de servicio |
| backend | `core/search` (`ServiceSearchIndexer`, `ServiceSearchSql`) | Indexar y buscar servicios |
| backend | `core/booking` (nuevo módulo) | Reglas de agenda, estados, notificaciones |
| backend | `core/pricing` | Precio «desde» con promoción vigente |
| backend | `marketplace/services` (nuevo) | Endpoints públicos de servicios |
| backend | `marketplace/home`, `marketplace/catalog/SearchController` | Bloques y resultados de servicios |
| backend | `storesuite/services`, `storesuite/bookings` | Portal |
| frontend | `features/services` (nuevo) | `/servicios`, `/servicio/:id`, tarjeta, formulario de cita |
| frontend | `marketplace` (home, búsqueda, cabecera, tienda) | Integración de servicios |
| frontend | `portal` (servicios, citas, métricas) | Gestión del almacén |
| frontend | `account` (`/mis-citas`) | Citas del comprador |

## Contratos

### Datos (Supabase `autopartco-dev`, no en el repo)

```sql
alter table store add column business_type varchar(10) not null default 'PARTS'
  check (business_type in ('PARTS','SERVICES','BOTH'));

alter table store_services
  add column image_url varchar(500) null,
  add column booking_enabled boolean not null default true,
  add column slot_minutes int not null default 60 check (slot_minutes in (30,60,90,120,180,240));

create table search_services (
  service_key text primary key,                  -- service_id:branch_id
  service_id uuid not null references store_services(id) on delete cascade,
  branch_id uuid not null references branches(id) on delete cascade,
  store_id uuid not null,
  category varchar(30) not null,
  name varchar(120) not null,
  price_from numeric(14,2) null,                 -- general, con promoción vigente aplicada
  base_price_from numeric(14,2) null,
  has_promotion boolean not null default false,
  booking_enabled boolean not null,
  city_code varchar(5), department_code varchar(2),
  latitude double precision, longitude double precision,
  store_rating numeric(3,2),
  document tsvector not null,                    -- A: nombre; B: categoría + sinónimos; C: descripción + almacén
  search_text text not null,                     -- para pg_trgm (autocompletado)
  updated_at timestamptz not null default now()
);
create index ix_search_services_doc on search_services using gin (document);
create index ix_search_services_trgm on search_services using gin (search_text gin_trgm_ops);
create index ix_search_services_cat_city on search_services (category, city_code);

create table service_bookings (
  id uuid primary key default gen_random_uuid(),
  code varchar(12) not null unique,              -- visible: «CT-7K3P9Q»
  service_id uuid not null references store_services(id),
  store_id uuid not null references store(id),
  branch_id uuid not null references branches(id),
  account_id uuid not null references accounts(id),
  starts_at timestamptz not null,
  ends_at timestamptz not null,
  proposed_starts_at timestamptz null,           -- contrapropuesta del almacén
  status varchar(20) not null check (status in
    ('REQUESTED','PROPOSED','CONFIRMED','CANCELLED_BY_BUYER','REJECTED','CANCELLED_BY_STORE','COMPLETED','NO_SHOW','EXPIRED')),
  vehicle jsonb null,                            -- {makeId, modelId, year, plate?} ; plate opcional, ≤ 7
  contact_phone varchar(13) not null check (contact_phone ~ '^\+57\d{10}$'),
  buyer_note varchar(500) null,
  store_note varchar(500) null,
  quoted_price numeric(14,2) null,               -- precio mostrado al pedir (con promoción)
  promotion_id uuid null references promotions(id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  version bigint not null default 0,
  check (ends_at > starts_at)
);
create index ix_bookings_store_status on service_bookings (store_id, status, starts_at);
create index ix_bookings_branch_day on service_bookings (branch_id, starts_at) where status in ('REQUESTED','PROPOSED','CONFIRMED');
create index ix_bookings_account on service_bookings (account_id, created_at desc);

-- promociones: ampliar el check de target_type con 'SERVICE','SERVICE_CATEGORY' (verificar nombre real)
-- store_visibility_events: ampliar kind con 'SERVICE_VIEW' (exige service_id)
alter table search_services enable row level security;
alter table service_bookings enable row level security;
```
(Verificar nombres reales con `list_tables` antes de aplicar.)

### Reglas de negocio

- `business_type`: `SERVICES` no exige productos en el checklist de perfil ni en la publicación; `PARTS` no muestra la pestaña de servicios del portal en el menú principal (sí en «Más»). Un almacén con servicios activos aparece en `/servicios` sin importar el tipo.
- Servicio visible en el marketplace: `active`, almacén publicado, al menos una sucursal `ACTIVE`.
- **Agenda**:
  - Solo si `booking_enabled` y la sucursal tiene `opening_hours`; si no, el detalle ofrece solo WhatsApp.
  - `starts_at` en hora de Bogotá, alineado a `slot_minutes`, entre +2 h y +60 días, y `[starts_at, ends_at)` dentro de una franja de `opening_hours`. `ends_at = starts_at + slot_minutes`.
  - Sin capacidad: varias solicitudes pueden pedir la misma hora; el almacén decide. `GET …/availability` marca como `busy` las franjas con una cita `CONFIRMED` (orientativo).
  - Máx. 3 citas activas (`REQUESTED|PROPOSED|CONFIRMED`) por comprador y almacén; máx. 10 solicitudes por comprador por día.
  - Transiciones: `REQUESTED → CONFIRMED | PROPOSED | REJECTED | CANCELLED_BY_BUYER | EXPIRED`; `PROPOSED → CONFIRMED (acepta comprador) | CANCELLED_BY_BUYER | EXPIRED`; `CONFIRMED → COMPLETED | NO_SHOW | CANCELLED_BY_STORE | CANCELLED_BY_BUYER`. `COMPLETED/NO_SHOW` solo después de `starts_at`. Cancelar exige nota del almacén. Otra transición → 409 `BOOKING_INVALID_TRANSITION`.
  - Job cada 15 min: `REQUESTED/PROPOSED` con `starts_at` vencido → `EXPIRED`.
  - Notificaciones (correo + notificación interna existente): nueva solicitud → destinatarios del almacén (`notifications.recipients` + dueño); cambio de estado → comprador; recordatorio 24 h antes de `CONFIRMED` → comprador.
- **Promociones de servicios**: `target_type = SERVICE` (id de servicio, scope `STORE`, del mismo almacén) o `SERVICE_CATEGORY` (enum en la columna `promotions.target_key`, `target_id` nulo, scope `PLATFORM` o `STORE`). Audiencia solo general (sin checkout no hay comprador que identificar). Se aplica sobre `price_from`; sin `price_from` solo se muestra el texto de la promoción. La cita guarda `quoted_price` y `promotion_id` vigentes al pedirla: el almacén se compromete a ese precio. Promociones de tipo `STORE`/`ALL` existentes **no** afectan servicios.

### API pública

Formato común `ServiceCard`:
```json
{ "id": "uuid", "name": "Alineación y balanceo", "category": "ALIGNMENT_BALANCING", "imageUrl": "…|null",
  "priceFrom": 51000, "basePriceFrom": 60000, "promotion": { "id": "uuid", "label": "-15%", "endsAt": "…" } ,
  "durationMinutes": 45, "bookingEnabled": true,
  "store": { "id": "uuid", "name": "…", "logoUrl": "…", "rating": 4.6, "businessType": "BOTH" },
  "branch": { "id": "uuid", "name": "…", "city": "Bogotá", "address": "…", "latitude": 4.6, "longitude": -74.0, "openNow": true },
  "distanceKm": 2.3 }
```
`branch` = la sucursal más cercana (con ubicación) o la primera; `promotion` y `distanceKm` pueden ser `null`.

- `GET /api/v1/marketplace/services/search?q=&category=&cityCode=&departmentCode=&lat=&lng=&radiusKm=&storeId=&priceMax=&onSale=&bookable=&openNow=&sort=relevance|nearest|price_asc|rating&page=&size=&facets=` → `{ items: ServiceCard[], page, size, total, facets: { categories: [{category,count}], cities: [{code,name,count}] } | null }`. Un resultado por servicio (no por sucursal). Errores y límites iguales a `/marketplace/search` (`SEARCH_SIZE_INVALID`, `LOCATION_REQUIRED`, cookie `ap_area`, 120/min por IP).
- `GET /api/v1/marketplace/services/categories?cityCode=` → `[{ category, label, icon, count }]` (caché 5 min).
- `GET /api/v1/marketplace/services/{serviceId}` → `ServiceCard` + `description`, `branches: StoreBranch[]` (mismo formato que `/stores/{id}/branches`, solo las que lo prestan), `whatsappEnabled`, `whatsappServiceMessage`, `related: ServiceCard[≤6]` (misma categoría, cerca). 404 `SERVICE_NOT_FOUND` si no es visible. Registra `SERVICE_VIEW` desde el frontend (evento, no en el GET).
- `GET /api/v1/marketplace/services/{serviceId}/availability?branchId=&from=YYYY-MM-DD&days=1..14` → `{ slotMinutes, days: [{ date, slots: [{ startsAt, busy }] }] }`. 400 `BRANCH_NOT_IN_SERVICE`, `BOOKING_DISABLED`.
- `GET /api/v1/marketplace/search` (existente) añade al cuerpo `services: { items: ServiceCard[≤4], total }` cuando hay `q` o `categoryId` mapeable; mismos `cityCode/lat/lng`. `interpreted` añade `serviceCategory|null`. Con `interpret=true` y intención clara de servicio (`serviceCategory` y ningún producto/vehículo), añade `"suggestedTab": "services"`.
- `GET /api/v1/marketplace/search/suggest` añade `services: [{ id, name, category, storeName }]` (≤3) y `serviceCategories: [category]` (≤2).
- `GET /api/v1/marketplace/home` añade `services: { categories: [{category,label,icon,count}], nearby: ServiceCard[≤8], promoted: ServiceCard[≤8] }` (cercanía con la cookie de área; sin área, por ciudad más poblada con oferta).
- `GET /api/v1/marketplace/stores?businessType=SERVICES|PARTS|BOTH&serviceCategory=` (filtros nuevos en el listado existente). `StorePublicProfile` añade `businessType`; `services[]` añade `imageUrl`, `bookingEnabled`, `priceFrom` con promoción y `promotion`.
- `POST /api/v1/events/visibility` acepta `SERVICE_VIEW` (exige `serviceId` del almacén).

### Sinónimos de búsqueda (`SearchDictionary`)

Cada `ServiceCategory` con etiqueta e ícono Lucide y términos que la activan, p. ej.: `ALIGNMENT_BALANCING` ← alineación, balanceo, alinear; `LUBRICATION` ← cambio de aceite, lubricación, aceite y filtro; `DIAGNOSTICS` ← escáner, diagnóstico, revisión; `AC` ← aire acondicionado, carga de gas; `INSTALLATION` ← instalación, montaje; `TIRES` ← montallantas, despinche, llantas (ambigua: muestra ambos). Lista completa en `SearchDictionary`; «llanta» sola sigue priorizando productos.

### API del comprador (sesión)

- `POST /api/v1/bookings` `{ serviceId, branchId, startsAt, contactPhone, vehicle?, note? }` → 201 `Booking`. 400 `BOOKING_SLOT_INVALID` (fuera de horario, no alineado, fuera de ventana), `BRANCH_NOT_IN_SERVICE`, `BOOKING_DISABLED`; 429 `BOOKING_LIMIT_REACHED`. `contactPhone` por defecto el del perfil.
- `GET /api/v1/bookings?status=&page=` → `Booking[]` propias. `GET /api/v1/bookings/{id}` (solo propia; ajena → 404).
- `POST /api/v1/bookings/{id}/accept-proposal` · `POST /api/v1/bookings/{id}/cancel` `{ reason? }` → `Booking`.
- `Booking`: `{ id, code, status, startsAt, endsAt, proposedStartsAt, service: {id,name,category,imageUrl}, store: {id,name,logoUrl}, branch: StoreBranch, quotedPrice, promotion, vehicle, contactPhone, buyerNote, storeNote, createdAt }`.

### API del portal

- Servicios (existente) añade `imageUrl` (subida con el mismo flujo de imágenes de producto: `POST /stores/{storeId}/services/{id}/image`), `bookingEnabled`, `slotMinutes`. Escribir: `STORE_UPDATE`.
- `PATCH /api/v1/stores/{storeId}` acepta `businessType`.
- `GET /api/v1/stores/{storeId}/bookings?status=&branchId=&from=&to=&page=` → `Booking` + `buyer: { name, phone }` (el teléfono solo se expone con cita no cancelada). Permiso nuevo `BOOKING_READ`.
- `POST /api/v1/stores/{storeId}/bookings/{id}/{confirm|propose|reject|cancel|complete|no-show}`; `propose` lleva `{ startsAt }` (mismas reglas de franja), `reject/cancel` llevan `{ note }` obligatoria. Permiso nuevo `BOOKING_MANAGE`; cita de otro almacén → 404. Concurrencia con `version` → 409 `BOOKING_VERSION_CONFLICT`.
- Promociones (existente): acepta `targetType: SERVICE | SERVICE_CATEGORY`; `SERVICE` ajeno → 400 `PROMOTION_TARGET_INVALID`.
- `GET /analytics/visibility` añade `serviceViews`, `topServices: [{ serviceId, name, views, whatsappClicks, directionsClicks, bookings }]`, `bookings: { requested, confirmed, completed, noShow }`.
- Roles predefinidos: dueño y administrador reciben `BOOKING_READ` y `BOOKING_MANAGE`; vendedor/mostrador `BOOKING_READ` y `BOOKING_MANAGE`; bodega ninguno.

### Configuración del almacén (`store.configuration`)

`contact.whatsappServiceMessage` (≤300, debe contener `{enlace}`), por defecto: `"Hola, vi el servicio {servicio} en Autopartco y quiero información para agendar: {enlace}"`. Placeholders admitidos: `{servicio}`, `{sucursal}`, `{enlace}`. `notifications.newBooking: true`.

### Enlaces (frontend)

- WhatsApp de servicio: `https://wa.me/<digits>?text=<encodeURIComponent(mensaje)>` con `{servicio}` = nombre, `{sucursal}` = nombre de la sucursal, `{enlace}` = `https://autopartco.co/servicio/<id>`. Registra `WHATSAPP_CLICK` con `serviceId` y `branchId`. Si hay promoción vigente, se añade al final «(Promoción: <label>)».
- Cómo llegar: reutilizar `directionsLinks` y `DirectionsMenu` (Google Maps, Waze, Apple Maps) con `serviceId` en el evento.

### Frontend

Todo según `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md` (§17, §20.15).
- **Cabecera**: navegación primaria «Repuestos · Servicios · Tiendas»; el buscador indica «Busca repuestos o servicios».
- **`/servicios`**: categorías con ícono (grilla), filtros (ciudad/cercanía, categoría, precio máx., en promoción, agenda en línea, abierto ahora), orden, vista lista + mapa (MapLibre de `features/location`, carga diferida), paginación. `/servicios?category=AC` es la URL canónica por categoría.
- **`/servicio/:id`**: imagen, nombre, almacén (enlace), precio «desde» con promoción, duración; acciones principales **Agendar cita** (si `bookingEnabled`), **WhatsApp**, **Cómo llegar**; sucursales que lo prestan con horario y «Abierto ahora»; mapa; relacionados.
- **`ServiceCard`**: componente reutilizable en home, búsqueda, tienda y relacionados; distingue visualmente de la tarjeta de producto (etiqueta «Servicio», ícono de categoría) sin estilos nuevos fuera del sistema.
- **Agendar**: diálogo/página en pasos: sucursal → día → hora (de `availability`) → vehículo (garaje del comprador si existe) y teléfono → confirmación. Sin sesión: redirige a ingresar y vuelve con el estado.
- **Búsqueda**: pestañas «Todo · Repuestos · Servicios» en `/buscar?tab=`; «Todo» muestra una franja de hasta 4 servicios arriba (o abajo si la intención es de producto) con «Ver todos los servicios»; «Servicios» usa `/services/search`. Autocompletado con grupo «Servicios».
- **Home**: sección «Servicios cerca de ti» (categorías + carrusel) y «Servicios en promoción», con el mismo peso visual que los bloques de productos.
- **Tienda**: con `businessType=SERVICES` la pestaña inicial es Servicios; tarjetas con `ServiceCard`.
- **`/mis-citas`** (cuenta): lista y detalle, aceptar contrapropuesta, cancelar, WhatsApp y Cómo llegar.
- **Portal**: servicios (CRUD, imagen, agenda, duración de franja, sucursales), **Citas** (bandeja por estado, vista de día por sucursal, acciones), tipo de negocio en el perfil y en el registro de vendedor, mensaje de WhatsApp de servicios en configuración, métricas de servicios, promociones con objetivo servicio.

## Decisiones

- **Índice propio `search_services` y no mezclar en `search_listings`:** las filas de listados tienen precio, stock, vehículos y facetas de producto; mezclar rompe totales, facetas y ranking. La búsqueda unificada se compone en la respuesta (franja + pestaña). Descartado: un único índice polimórfico.
- **Endpoint aparte + franja en `/search`:** no cambia el formato de `items` que ya consume el frontend; el campo `services` es aditivo.
- **Agenda como solicitud con confirmación, sin capacidad:** los talleres no tienen calendarios digitales; exigir capacidad por bahía frenaría la adopción. La contrapropuesta cubre los choques. Descartado: reserva instantánea con cupos (queda para un plan posterior si hay demanda).
- **Promociones con el motor existente (`SERVICE`, `SERVICE_CATEGORY`):** reutiliza vigencia, presupuesto, resultados y validaciones; solo audiencia general porque no hay checkout. Descartado: texto libre de promoción en el servicio (no medible ni vencible).
- **`business_type` explícito:** necesario para que un taller sin productos complete su perfil y para filtrar tiendas; inferirlo del contenido falla el primer día.
- **Mensaje de WhatsApp separado para servicios:** el de cotizar producto habla de «cotizarlo»; el de servicio pide información para agendar.
- **Un resultado por servicio con la sucursal más cercana:** evita repetir el mismo servicio N veces en la lista.

## Tareas

### T1 — Esquema ✅ (2026-11-13)

Notas de implementación (migración `services_first_class`):
- **Promociones:**
  - `promotions` no tenía dónde guardar una categoría, así que se añadió `target_key varchar(30)`. `SERVICE_CATEGORY` exige `target_id` nulo y un `target_key` de `ServiceCategory`. Los demás tipos exigen `target_key` nulo (`chk_promotions__target_key`).
  - `SERVICE` exige `scope = STORE` y `target_id`.
  - El trigger `tg_promotions__product_same_store` rechaza ahora un servicio de otro almacén (`chk_promotions__service_same_store`, errcode 23514).
- **Índices de `search_services`:** llevan el prefijo `ix_srv_search_*`, porque `ix_ss_*` ya lo usa `search_suggestions`. También tiene FK a `store` e índices en `service_id`, `branch_id`, `store_id` y en la geolocalización.
- **`service_bookings`:**
  - Trigger `set_updated_at`.
  - Checks `chk_bookings__*`: `PROPOSED` exige `proposed_starts_at`, `vehicle` debe ser un objeto y `quoted_price` ≥ 0.
- **`store_visibility_events`:** `chk_sve__service_required`, es decir, `SERVICE_VIEW` exige `service_id`.
- **Otros checks:** `chk_store__business_type` y `chk_store_services__slot_minutes`.
- **Verificación:** las inserciones válidas pasan y las inválidas se rechazan (prueba con rollback).
- **Advisors:** solo el INFO de RLS sin políticas y el de índices sin uso, que son esperados.
- **Modelo:** Haiku · **Agente:** database
- **Archivos:** ninguno (SQL solo en Supabase `autopartco-dev`).
- **Contexto:** este documento, sección «Datos». Verificar con `list_tables` nombres reales (`store`, `promotions` y su check de `target_type`, check de `kind` en `store_visibility_events`) y aplicar con `apply_migration` nombre `services_first_class`. **Requiere autorización.**
- **Aceptación:** existen columnas, tablas e índices; `target_type='SERVICE'` y `kind='SERVICE_VIEW'` se pueden insertar; `get_advisors` sin alertas nuevas salvo el INFO de RLS sin políticas.

### T2 — Índice y búsqueda de servicios ✅ (2026-11-13)

Notas de implementación:
- **Diccionario de servicios** en `core.search.ServiceTerms` (estático; `SearchDictionary` depende de la BD). Alimenta el parser, el `document` y las sugerencias. `core.search` ahora depende de `core.organization`.
- **Visibilidad:** servicio `active`, almacén `ACTIVE` y sucursal `ACTIVE` enlazada (mismo criterio que el índice de productos).
- **Ciudad interpretada en `q`:** filtra solo si no llega `cityCode`/`departmentCode`; con 0 resultados se reintenta sin ella. La categoría interpretada solo ordena (×2). Después viene el respaldo por trigramas.
- **`openNow`:** se resuelve en Java (`OpeningHours.openNow`, reloj de Bogotá) y pasa al SQL la lista de sucursales abiertas.
- **Franja `services` en `/search`:** solo con `q` no vacío, porque `categoryId` de producto no se corresponde con las categorías de servicio. Un fallo no rompe la búsqueda de productos. `services` y `suggestedTab` se omiten del JSON si son nulos.
- **Códigos nuevos:** 400 `SERVICE_CATEGORY_INVALID` (search) y `SEARCH_PLACE_INVALID` (categories). El detalle acepta `lat`/`lng` o la cookie `ap_area`.
- **Reindexación:**
  - `ServicesChanged` (publicado por `StoreServices`) reindexa esos servicios.
  - `ListingsChanged` reindexa los servicios del almacén o la sucursal.
  - El rebuild total y el nocturno incluyen servicios.
  - Al arrancar, si el índice está vacío y hay servicios, se reconstruye.
- **`WebSecurityConfig`:** `GET /marketplace/services/*` es pública (aprobada por el usuario).
- **SQL verificado contra Supabase con rollback:** 193 servicios indexados; «diagnostico» → 45, «instalacion» → 148, trigramas «diagnstico» → 45.
- **Pruebas en verde:**
  - Las 5 clases del criterio de aceptación y `ServiceSearchIndexerTest`.
  - Las existentes de búsqueda, settings y cache (137 tests en total).
  - `WebSecurityConfigTest` (198).
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `core/search/{ServiceSearchIndexer,ServiceSearchSql,ServiceSearchService,ServiceSearchQuery,SearchDictionary,QueryParser,Suggestions,SearchIndexJobs}.java`; nuevo `marketplace/services/{ServiceBrowseController,ServiceCard,ServiceDetail}.java`; `marketplace/catalog/{SearchController,SearchResponse}.java`; `core/organization` (eventos de cambio de servicio/sucursal para reindexar); tests en `backend/src/test/java/com/autopartco/backend/core/search/` y `marketplace/services/`.
- **Contexto:** contratos «API pública» (salvo home, availability y promociones) y «Sinónimos». Reindexar al crear/editar/desactivar servicio, cambiar sucursal o publicar/ocultar almacén; `reindexAll` incluye servicios. Precio con promoción se deja en `base_price_from` hasta T4 (`price_from = base`). Depende de T1.
- **Aceptación:** `./mvnw -q test -Dtest=ServiceSearchSqlTest,ServiceSearchServiceTest,ServiceBrowseControllerTest,SearchControllerServicesTest,QueryParserServiceIntentTest` en verde: «alineacion bogota» devuelve servicios `ALIGNMENT_BALANCING` de Bogotá sin tildes; servicio inactivo o de almacén no publicado no aparece; un resultado por servicio con la sucursal más cercana; `/search?q=cambio de aceite` trae `services.items` y `interpreted.serviceCategory=LUBRICATION`; formato de `items` de productos sin cambios.

### T3 — Agenda de citas ✅ (2026-11-15)

Notas de implementación:
- **Permisos en BD:** migración `booking_permissions` en Supabase: crea `BOOKING_READ`/`BOOKING_MANAGE` y los asigna a los roles `OWNER` existentes. Los roles `ADMIN`/`SALES` los toman al crearse (`StaffRoles.ensure`).
- **`WebSecurityConfig`:** `GET /marketplace/services/*/availability` es pública (aprobada; límite de 60/min por IP). `/bookings` y `/stores/*/bookings` exigen sesión.
- **Concurrencia:** el alta toma un advisory lock por comprador (límites 3 activas/almacén y 10/día) y las acciones del almacén usan `version`.
- **Notificaciones:** `BookingChanged` → `BookingNotifications` (`AFTER_COMMIT`, asíncrono). El recordatorio de 24 h se envía una sola vez por cita (marca en auditoría `BOOKING_REMINDER_SENT`).
- **Pendiente:** las citas aún no entran en la exportación/borrado de cuenta (Ley 1581); va con T9.
- **Pruebas en verde:** las 6 clases del criterio, `ServiceAvailabilityControllerTest`, `SettingsServiceTest`, `ModularityTests`, `WebSecurityConfigTest` (209) y `StaffRolesTest`.
- **Modelo:** Opus · **Agente:** backend
- **Archivos:** nuevo módulo `core/booking/{Booking,BookingStatus,BookingService,BookingRules,AvailabilityService,BookingExpiryJob,BookingReminderJob,BookingNotifications}.java` (+ `internal/`), `marketplace/bookings/BuyerBookingController.java`, `marketplace/services/ServiceAvailabilityController.java`, `storesuite/bookings/StoreBookingController.java`, permisos `BOOKING_READ/BOOKING_MANAGE` en el catálogo de permisos y roles predefinidos, `core/notification` (plantillas), `core/settings` (`whatsappServiceMessage`, `newBooking`); `WebSecurityConfig` si hace falta ruta pública de availability (**requiere aprobación**); tests en `backend/src/test/java/com/autopartco/backend/core/booking/`.
- **Contexto:** «Reglas de negocio → Agenda», «API del comprador», «API del portal → citas», «Configuración del almacén». Reloj inyectable, zona `America/Bogota`. Depende de T1.
- **Aceptación:** `./mvnw -q test -Dtest=BookingRulesTest,BookingServiceTest,AvailabilityServiceTest,StoreBookingControllerTest,BuyerBookingControllerTest,BookingExpiryJobTest` en verde: hora fuera de horario/no alineada/<2 h → 400 `BOOKING_SLOT_INVALID`; 4.ª cita activa en el mismo almacén → 429; transición inválida → 409; cita ajena (comprador u otro almacén) → 404; `COMPLETED` antes de `starts_at` → 409; vencidas pasan a `EXPIRED`; notificación tras commit a almacén y comprador; teléfono del comprador oculto en canceladas.

### T4 — Promociones de servicios y precio «desde» ✅ (2026-11-15)

Notas de implementación:
- **Código de error:** se mantiene el existente 400 `INVALID_PROMOTION_TARGET` (no `PROMOTION_TARGET_INVALID`). Una audiencia distinta de `ALL` da 400 `INVALID_PROMOTION_AUDIENCE`.
- **API:** `PromotionUpsertRequest`/`PromotionResponse` añaden `targetKey` (categoría de servicio, normalizada en mayúsculas).
- **Cálculo:** `PriceCalculator.servicePrice` y `ServicePricing`, con reglas vigentes y una caché de 30 s para las tarjetas. Etiqueta `-15%` o `-$9.000`. Las promociones de servicios devuelven `false` en `appliesTo` de productos.
- **Índice:**
  - El upsert deja el precio base. Después, en la misma transacción, `applyPromotions` escribe `price_from` con descuento y `has_promotion`.
  - El job `SearchIndexJobs.servicePromotions` (cada 15 min) reindexa los servicios con promoción y los recién alcanzados: al vencer vuelven al precio base.
  - Las tarjetas recalculan precio y promoción al leer.
- **Cita:** `quoted_price` y `promotion_id` vigentes al pedir (`ServicePricing.current`).
- **Pendiente para T5:** `promotion` y precio con descuento en `StorePublicProfile.services[]`.
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `core/pricing/{PromotionTargetType,PromotionService,PriceCalculator,PromotionTargets}.java`, `core/catalog/internal/CatalogPromotionTargets.java` (o implementación en `core/organization`), `ServiceSearchIndexer` (aplica `price_from` con promoción y `has_promotion`, reindexa al crear/vencer promoción), `StorePublicProfile`/`ServiceCard` (`promotion`), `BookingService` (`quoted_price`, `promotion_id`); tests en `core/pricing/`.
- **Contexto:** «Reglas de negocio → Promociones de servicios». Depende de T2 y T3.
- **Aceptación:** `./mvnw -q test -Dtest=ServicePromotionTest,PriceCalculatorServiceTest,ServiceSearchIndexerPromotionTest` en verde: `SERVICE` de otro almacén → 400; `-15%` sobre 60 000 → `priceFrom` 51 000 y `basePriceFrom` 60 000; promoción `STORE`/`ALL` no altera servicios; al vencer, el índice vuelve al precio base; la cita guarda el precio vigente al pedir.

### T5 — Home, tiendas, perfil, métricas y tipo de negocio ✅ (2026-11-15)

Notas de implementación:
- **`businessType`:** viaja en `StoreRegistrationRequest` (solicitud y alta de vendedor; por defecto `PARTS`) y en `PATCH /stores/{id}` (`StoreUpdateRequest`). Lo devuelven `StoreResponse`, `StoreProfileResponse`, `StorePublicProfile` y `PublicStoreSummary`. Cambiarlo reindexa los servicios del almacén.
- **Checklist:** vive en el frontend (`publish-checklist.ts`). El backend expone `businessType` y `BusinessType.requiresProducts()` como referencia: `SERVICES` no exige productos.
- **`GET /marketplace/stores?businessType=&serviceCategory=`:** `BOTH` aparece al filtrar por `PARTS` o `SERVICES`.
- **Perfil público:** `services[]` es `StoreServiceItem`, con `basePriceFrom`, `promotion`, `imageUrl` y `bookingEnabled`. El precio con promoción se aplica al leer, fuera de la caché del perfil.
- **Portal de servicios:**
  - `bookingEnabled` y `slotMinutes` (30/60/90/120/180/240) en el request y la vista.
  - `POST /stores/{storeId}/services/{id}/image` (multipart `file`, PNG/JPEG/WebP ≤ 5 MB) y `DELETE …/image`.
  - La imagen va al bucket público de Storage (como las de producto) y guarda su URL, así que no se abre ninguna ruta pública nueva. La anterior se borra con best-effort.
- **Home:** `services.categories/nearby/promoted` (≤ 8).
  - Con área: cercanos en 50 km y, si no hay, sin radio.
  - Sin área: ciudad del área o la de más servicios (`ServiceSearchService.topCity`).
  - «En promoción» cae a todo el país si el área no tiene.
  - Un fallo deja el bloque vacío sin romper la home. Va en la caché de 60 s.
- **Métricas:**
  - `SERVICE_VIEW` exige `serviceId` de un servicio activo del almacén.
  - El reporte añade `serviceViews`, `topServices` (≤ 20, con vistas, clics y citas) y `bookings { requested, confirmed, completed, noShow }`. Las citas se cuentan por fecha de solicitud en Bogotá; `confirmed` incluye las completadas y las de no asistencia.
- **Pruebas:** las 4 del criterio en verde, más las existentes afectadas.
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `marketplace/home/{HomeService,HomeResponse,HomeContent}.java`, `marketplace/stores/{StoreBrowseController,StorePublicProfile}.java`, `storesuite/store/UpdateStoreProfile.java` y registro de vendedor (`businessType`), checklist de perfil (no exige productos con `SERVICES`), `storesuite/services` (imagen, `bookingEnabled`, `slotMinutes`), `core/analytics/{VisibilityRecorder,VisibilityQueries,VisibilityReport}.java` (`SERVICE_VIEW`, `topServices`, `bookings`); tests.
- **Contexto:** «API pública → home, stores», «API del portal», «Reglas → business_type». Depende de T2–T4.
- **Aceptación:** `./mvnw -q test -Dtest=HomeServiceServicesTest,StoreBrowseBusinessTypeTest,StoreProfileChecklistTest,VisibilityServiceMetricsTest` en verde: home trae `services.categories/nearby/promoted` con área y sin área; filtro `businessType=SERVICES` funciona; almacén `SERVICES` sin productos tiene perfil completo; `SERVICE_VIEW` sin `serviceId` o ajeno no se registra; reporte con `topServices` y conteo de citas.

### T6 — Frontend marketplace: sección, detalle, búsqueda, home y WhatsApp
- **Modelo:** Sonnet · **Agente:** frontend (cuenta de frontend)
- **Archivos:** nuevo `features/services/` (`pages/{services-page,service-page}.tsx`, `components/{service-card,service-categories,service-filters,service-actions,service-map}.tsx`, `api/*`), `app/router.tsx` + `lazy-pages.tsx`, `app/site-header.tsx`, `features/marketplace/pages/{home-page,search-page,search-results-view,store-page}.tsx`, autocompletado, `features/marketplace/lib/store-links.ts` (mensaje de servicio), `lib/visibility.ts` (`SERVICE_VIEW`). Reutilizar `DirectionsMenu`, `WhatsappButton`, `BranchMap`; sin dependencias nuevas.
- **Contexto:** contratos «API pública», «Enlaces» y «Frontend» (sin agenda ni portal). Incluye el T5 pendiente del plan 2026-11-11 si aún falta. Depende de T2, T4 y T5.
- **Aceptación:** build y lint en verde; manual: «Servicios» en la cabecera; `/servicios` filtra por categoría/ciudad y muestra mapa diferido; `/buscar?q=alineacion` muestra franja de servicios y pestaña Servicios; autocompletado agrupa servicios; home con bloques de servicios; detalle abre `wa.me` con mensaje de servicio y registra el clic; «Cómo llegar» abre Google Maps/Waze/Apple Maps; promoción visible con precio tachado; 360 px sin scroll horizontal; teclado operable.

### T7 — Frontend agenda del comprador
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `features/services/components/booking-flow/*`, `features/account/pages/{bookings-page,booking-detail-page}.tsx`, rutas `/mis-citas` y `/mis-citas/:id`, menú de cuenta.
- **Contexto:** «API del comprador», `availability`, «Frontend → Agendar, /mis-citas». Depende de T3 y T6.
- **Aceptación:** build y lint; manual: sin sesión, «Agendar» lleva a ingresar y vuelve al paso; solo se ofrecen horas de `availability`; error `BOOKING_SLOT_INVALID` se muestra en el paso de hora; aceptar contrapropuesta y cancelar actualizan el estado; detalle con WhatsApp y Cómo llegar.

### T8 — Frontend portal: servicios, citas, tipo de negocio y métricas
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `features/portal/pages/{services-page,bookings-page,store-profile-page,store-settings-page,promotions-page,intelligence-visibility-page}.tsx`, navegación del portal con `RequirePermission` (`BOOKING_READ`), registro de vendedor (`businessType`).
- **Contexto:** «API del portal», «Configuración del almacén». Incluye el T6 pendiente del plan 2026-11-11 (servicios/sucursales) si aún falta. Depende de T3–T5.
- **Aceptación:** build y lint; manual: CRUD de servicio con imagen y agenda; bandeja de citas por estado con confirmar/proponer/rechazar (nota obligatoria); promoción con objetivo servicio; mensaje de WhatsApp de servicio sin `{enlace}` marca error; métricas muestran top servicios y citas; usuario sin `BOOKING_READ` no ve «Citas».

### T9 — QA del diff
- **Modelo:** Opus · **Agente:** qa-seguridad
- **Contexto:** solo el diff de T2–T8: IDOR en citas (comprador y almacén), exposición del teléfono del comprador, abuso de solicitudes (límites), inyección en `whatsappServiceMessage` y URLs, XSS en notas/`vehicle`, promociones sobre servicios ajenos, consistencia del índice al desactivar servicio/almacén, zona horaria.
- **Aceptación:** informe clasificado; tests de T2–T5 con `-Dtest=` explícito.

## Riesgos y puntos a verificar

- **Citas sin respuesta:** si los talleres no confirman, la agenda pierde confianza. Mostrar al comprador «Respuesta habitual en X h» (después) y en el portal un contador de pendientes; correo al almacén a las 4 h sin respuesta.
- **Ambigüedad producto/servicio** («llantas», «batería»): la franja de servicios en «Todo» cubre ambos; revisar el diccionario con las búsquedas reales del `SearchLog`.
- **Precio «desde» comprometido en la cita:** dejar claro en la interfaz que es precio de referencia sujeto a revisión del vehículo.
- **Datos personales (Ley 1581):** teléfono y placa solo visibles para el almacén de la cita; no guardar placa si no se pide; incluir citas en exportación/borrado de cuenta.
- **Carga del índice:** `search_services` es pequeño; el reindex por cambio de promoción debe ir por lote.
- **`WebSecurityConfig` y permisos nuevos:** T3 y T5 los tocan; en secuencia y con aprobación.
- **Frontend en otra cuenta:** T6–T8 usan este documento como contrato.
