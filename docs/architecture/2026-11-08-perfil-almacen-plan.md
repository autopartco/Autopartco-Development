# Perfil público del almacén, métricas de visibilidad y compartir con origen

Rama: `feature/perfil-almacen` desde `develop`.

## Objetivo y alcance

Entra:
- Perfil público del almacén `/tiendas/:storeId`: banner, logo, nombre, descripción, contacto público, sedes (ciudad), calificación, reseñas y productos paginados.
- Acceso desde `/tiendas` (tarjeta → perfil) y desde el detalle de producto («Vendido por» → enlace al perfil).
- Edición del perfil por el almacén en `/portal/:storeId/tienda`: logo, banner, descripción y contacto público.
- Reseñas de tienda (MK-B4): listar público; crear con pedido entregado.
- Métricas para el vendedor: visitas al perfil, visitas a cada publicación (únicas y totales), compartidos y tráfico por origen.
- Botón compartir (icono `Share2`) en producto y perfil: copia al portapapeles un enlace con origen (`?ref=`).

No entra: respuestas del vendedor a reseñas, moderación de reseñas, slug amigable (`/tiendas/mi-almacen`), reseñas de producto, métricas por sucursal, límite de métricas por plan (ver riesgos).

## Estado actual (lo que se reutiliza)

- `store`: ya tiene `logo_path`, `banner_path`, `configuration` (jsonb). `store_reviews` ya existe (`rating 1..5`, `order_id`).
- `StoreFiles.uploadBrandAsset/publicBrandAsset` y `GET /marketplace/stores/{id}/brand/{assetType}` ya sirven logo/banner; falta endpoint de carga en el portal.
- `StoreProfileResponse` ya tiene `logoUrl`/`bannerUrl` (hoy nulos); `PATCH /stores/{id}` ya edita `description`.
- `TrafficController` (`POST /events/pageview`) ya tiene límite por IP y `TrafficRecorder` ya hace hash diario del visitante y extrae dominio del referente: se reutilizan.
- Búsqueda ya filtra por `storeId` (`storeProductsPath`).

## Componentes

| Capa | Componente | Responsabilidad |
|---|---|---|
| BD | `store_visibility_events` | Evento crudo sin datos personales |
| BD | `store.configuration.publicProfile` | Contacto público (jsonb, sin columna nueva) |
| backend | `marketplace/stores` | Perfil público |
| backend | `marketplace/reviews` | Reseñas de tienda |
| backend | `core/analytics/VisibilityRecorder` + `VisibilityQueries` | Registrar y agregar eventos |
| backend | `core/analytics/TrafficSource` | Clasificar origen (`ref`, `utm_source`, referente) |
| backend | `storesuite/store` | Carga de logo/banner y contacto público |
| backend | `storesuite/analytics` | Reporte de visibilidad |
| frontend | `marketplace/pages/store-page.tsx`, `share-button.tsx`, `lib/visibility.ts` | Perfil, compartir y eventos |
| frontend | `portal/pages/store-profile-page.tsx`, `intelligence-visibility-page.tsx` | Edición y estadísticas |

## Contratos

### Datos (Supabase `autopartco-dev`, no en el repo)

```sql
create table store_visibility_events (
  id uuid primary key default gen_random_uuid(),
  day date not null,
  store_id uuid not null references store(id),
  product_id uuid null references products(id),
  kind varchar(20) not null check (kind in ('PROFILE_VIEW','PRODUCT_VIEW','SHARE')),
  source varchar(20) not null,          -- TrafficSource
  visitor_hash char(64) not null,       -- sha256(sal|día|anonymousId), igual que traffic_events
  created_at timestamptz not null default now()
);
create index ix_sve_store_day on store_visibility_events (store_id, day);
create index ix_sve_store_product_day on store_visibility_events (store_id, product_id, day) where product_id is not null;
create index ix_sve_product on store_visibility_events (product_id) where product_id is not null;
alter table store_visibility_events enable row level security; -- igual que el resto: solo el backend accede
```
Aplicada en Supabase el 2026-11-08 (migración `store_visibility_events`).
Retención: borrar eventos con más de 400 días (mismo job que `traffic_events` si existe; si no, en riesgos).

`store.configuration -> 'publicProfile'`: `{ "whatsapp": "+573001234567", "phone": "...", "website": "https://...", "instagram": "usuario", "facebook": "usuario", "tiktok": "usuario" }` (todos opcionales).

### `TrafficSource` (enum)

`WHATSAPP, FACEBOOK, INSTAGRAM, TIKTOK, X, TELEGRAM, CHATGPT, OTHER_AI, GOOGLE, OTHER_SEARCH, COPY_LINK, INTERNAL, DIRECT, OTHER`.

Resolución, en orden:
1. `ref` del enlace compartido: `wa|fb|ig|tt|x|tg|link` → `WHATSAPP…COPY_LINK`.
2. `utm_source`: `chatgpt.com` → `CHATGPT`; `perplexity|gemini|claude|copilot` → `OTHER_AI`; resto → por dominio.
3. Dominio del referente: `whatsapp.com|wa.me` → WHATSAPP; `facebook.com|fb.com|m.facebook.com|l.facebook.com` → FACEBOOK; `instagram.com|l.instagram.com` → INSTAGRAM; `tiktok.com` → TIKTOK; `t.co|x.com|twitter.com` → X; `t.me|telegram.org` → TELEGRAM; `chatgpt.com|chat.openai.com` → CHATGPT; `perplexity.ai|gemini.google.com|claude.ai|copilot.microsoft.com` → OTHER_AI; `google.*` → GOOGLE; `bing.com|duckduckgo.com|search.yahoo.com` → OTHER_SEARCH; dominio propio → INTERNAL; otro → OTHER.
4. Sin nada → `DIRECT`.

### API pública

`GET /api/v1/marketplace/stores/{storeId}` → 200 `StorePublicProfile` · 404 si el almacén no está `ACTIVE`/publicado.
```json
{ "id": "uuid", "name": "Repuestos X", "description": "…", "logoUrl": "/api/v1/marketplace/stores/{id}/brand/logo|null",
  "bannerUrl": "…|null", "cities": ["Bogotá"], "branchCount": 2, "memberSince": "2026-10-01",
  "rating": { "average": 4.6 /* null sin reseñas */, "count": 18 }, "publishedProducts": 120,
  "contact": { "whatsapp": "…", "phone": null, "website": null, "instagram": "…", "facebook": null, "tiktok": null } }
```
Productos del perfil: `GET /api/v1/marketplace/products?storeId=<uuid>&page=0&size=24` (filtro `storeId` añadido en T2; esa búsqueda pagina desde 0).

`GET /api/v1/marketplace/stores/{storeId}/reviews?page=1&size=10` (páginas desde 1, `size` ≤ 50) → `PageResponse<StoreReviewResponse>` `{ items, page, size, total, totalPages }`
`StoreReviewResponse { id, rating, comment, authorName /* «Ana P.» */, createdAt }`, orden `created_at desc`.

`POST /api/v1/marketplace/stores/{storeId}/reviews` (sesión de comprador) `{ orderId, rating 1..5, comment ≤1000 }` → 201 `StoreReviewResponse` · 404 `ORDER_NOT_FOUND` (pedido ajeno o inexistente) · 404 `STORE_NOT_FOUND` · 409 `REVIEW_NOT_ALLOWED` (sin venta `DELIVERED` de una sucursal del almacén) · 409 `REVIEW_ALREADY_EXISTS`.

`GET /api/v1/marketplace/stores/{storeId}/reviews/eligibility` (sesión) → `{ eligibleOrderIds: [uuid] }` para mostrar el formulario.

`POST /api/v1/events/visibility` (público, mismo límite por IP que pageview) → 204 siempre (datos inválidos se ignoran).
```json
{ "anonymousId": "…", "kind": "PROFILE_VIEW|PRODUCT_VIEW|SHARE", "storeId": "uuid", "productId": "uuid|null",
  "ref": "wa|null", "utmSource": "chatgpt.com|null", "referrer": "https://…|null", "channel": "wa|null" }
```
`SHARE` usa `channel` (mismos códigos que `ref`) como origen; `PRODUCT_VIEW` exige `productId` del almacén; `PROFILE_VIEW` ignora `productId`. Solo se guarda si el almacén está `ACTIVE`. Comparte con `/pageview` el límite de 60 llamadas/min por IP. Tras la primera vista de la sesión, el frontend envía `referrer` = URL absoluta de la ruta interna anterior (dominio propio → `INTERNAL`), porque `document.referrer` no cambia en una SPA.

### API del portal

`POST /api/v1/stores/{storeId}/brand/{logo|banner}` multipart `file` (PNG/JPEG/WebP por firma real; logo ≤2 MB, banner ≤4 MB → 413 `BRAND_IMAGE_TOO_LARGE`; otro tipo → 400 `UNSUPPORTED_FILE_TYPE`; tipo de recurso desconocido → 404) → 200 `StoreProfileResponse`. Permiso `STORE_UPDATE`.
`DELETE /api/v1/stores/{storeId}/brand/{logo|banner}` → 204.
`PATCH /api/v1/stores/{storeId}`: `StoreUpdateRequest` + `publicProfile` (opcional, mismas claves; `null` no cambia, `""` borra; `whatsapp/phone` E.164 Colombia `^\+57\d{10}$`, `website` https, redes `^[A-Za-z0-9._]{1,30}$`). `StoreProfileResponse` + `publicProfile` (siempre objeto, campos nulos si no hay). `logoUrl/bannerUrl` = `/api/v1/marketplace/stores/{id}/brand/{tipo}?v=<versión>` (cambia en cada carga; solo responde si el almacén está publicado: en almacenes pendientes el portal muestra la vista previa local del archivo).

`GET /api/v1/stores/{storeId}/analytics/visibility?from=YYYY-MM-DD&to=YYYY-MM-DD` (permiso `ANALYTICS_READ`, máx. 366 días) →
```json
{ "from": "2026-10-10", "to": "2026-11-08", "profileViews": 340, "profileVisitors": 210, "productViews": 1250, "productVisitors": 780, "shares": 42,
  "bySource": [{ "source": "WHATSAPP", "views": 300, "visitors": 190, "shares": 12 }],
  "topProducts": [{ "productId": "uuid", "name": "…", "views": 120, "visitors": 90, "shares": 5 }],
  "daily": [{ "day": "2026-11-01", "profileViews": 12, "productViews": 40 }] }
```
Sin `from`/`to`: últimos 30 días hasta hoy (Bogotá); rango inválido o >366 días → 400 `INVALID_RANGE`. Solo `ANALYTICS_READ` (todos los planes, sin `ADVANCED_REPORTS`). Visitantes = `count(distinct visitor_hash)` por día sumado (el hash cambia cada día: se rotula «visitantes diarios»). `daily` trae todos los días del rango (ceros incluidos); `topProducts` máximo 20; `bySource.views` = perfil + publicaciones.

### Frontend

- Ruta `tiendas/:storeId` → `StorePage`; `storePath(id)` en `lib/paths.ts`.
- `ShareButton({ url, title, storeId, productId? })`: icono `Share2`, `aria-label="Compartir"`; abre menú con WhatsApp, Facebook, TikTok, Instagram, X, Copiar enlace. Cada opción copia al portapapeles `url?ref=<código>` (WhatsApp/Facebook/X además abren su URL de compartir), muestra toast «Enlace copiado» y envía `SHARE`. En móvil, primera opción «Más opciones» con `navigator.share`.
- `lib/visibility.ts`: `trackVisibility(kind, storeId, productId?)`; lee `ref`/`utm_source` de la URL solo en la primera vista de la sesión, después envía `INTERNAL`; elimina `ref` de la URL con `history.replaceState` para que no se vuelva a compartir con el origen de otro.

## Decisiones

- **Menú de canales con `?ref=` en lugar de solo copiar:** WhatsApp, TikTok e Instagram abren enlaces desde la app sin referente; sin `ref` todo caería en `DIRECT`. Descartado: enlace corto por usuario (más tablas, sin ganancia para el vendedor).
- **Tabla de eventos propia, no `traffic_events`:** `path_group` agrupa ids en `:id` y pierde el almacén. Descartado: agregados diarios precalculados (pierde visitantes únicos; el volumen actual no lo exige).
- **No `tracking.*` (revisado 2026-11-08):** está vacío y sin código (`core.tracking` no existe); por diseño (§8 del modelo de datos) descarta los eventos sin consentimiento `ANALYTICS_TRACKING`, lo que subcontaría casi todas las visitas anónimas; liga `anonymous_id`/`subject_key` a la persona y el origen vive en `tracking.sessions`. `store_visibility_events` sigue el criterio de `traffic_events` (hash con sal diaria, sin identidad), apto sin consentimiento. Cuando exista `core.tracking`, `product_view`/`share` pueden escribirse también allí sin cambiar estas métricas.
- **Contacto público en `configuration` jsonb:** sin migración de columnas; se lee junto al perfil.
- **Perfil por id, no slug:** los enlaces ya usan id; slug queda para después.
- **Productos del perfil con la búsqueda existente:** un solo motor de filtros y orden.

## Tareas

### T1 — Tabla de eventos de visibilidad ✅ (2026-11-08)
- **Modelo:** Haiku · **Agente:** database
- **Archivos:** ninguno en el repo (SQL solo en Supabase).
- **Contexto:** aplicar en Supabase `autopartco-dev` (`hwojkaguqmegvdxlcysd`) el SQL de «Datos» de este plan con `apply_migration` nombre `store_visibility_events`. Requiere autorización del usuario antes de ejecutar.
- **Aceptación:** `select * from store_visibility_events limit 0` funciona; existen `ix_sve_store_day` e `ix_sve_store_product_day`; `get_advisors` sin alertas nuevas de seguridad para la tabla.

### T2 — Perfil público y reseñas de tienda ✅ (2026-11-08)
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `marketplace/stores/{StoreBrowseController,StorePublicProfile}.java`; nuevos `marketplace/reviews/{StoreReviewController,StoreReviewService,StoreReviewRequest,StoreReviewResponse}.java`; consultas en `core/organization` si hace falta; `shared/security/WebSecurityConfig.java` (rutas GET públicas, **requiere aprobación**); tests en `backend/src/test/java/com/autopartco/backend/marketplace/{stores,reviews}/`.
- **Contexto:** contratos «API pública» de este plan. `store_reviews` ya existe. Elegibilidad: venta del pedido de una sucursal del almacén con estado `DELIVERED` y pedido de la persona. `authorName` = nombre + inicial del apellido. `publishedProducts` = mismo filtro que `ListingQueries.publishedListings`.
- **Aceptación:** `./mvnw -q test -Dtest=StorePublicProfileTest,StoreReviewServiceTest,ModularityTests` en verde: almacén no activo → 404; `logoUrl` nulo si no hay `logo_path`; elegible → 201; no entregado → 409 `REVIEW_NOT_ALLOWED`; ajeno → 404; duplicado → 409 `REVIEW_ALREADY_EXISTS`; promedio con un decimal.

### T3 — Edición de marca y contacto público ✅ (2026-11-08)
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** `storesuite/store/{StoreProfileController,UpdateStoreProfile,GetStoreProfile}.java`, nuevo `storesuite/store/UploadStoreBrand.java`; `StoreUpdateRequest`, `StoreProfileResponse`; tests en `backend/src/test/java/com/autopartco/backend/storesuite/store/`.
- **Contexto:** contratos «API del portal» (brand y PATCH). Reutilizar `StoreFiles.uploadBrandAsset`; guardar ruta en `store.logo_path/banner_path`; `publicProfile` dentro de `store.configuration`. Permiso `STORE_UPDATE`. Sin auditoría: el PATCH de perfil existente tampoco audita.
- **Aceptación:** `./mvnw -q test -Dtest=UploadStoreBrandTest,UpdateStoreProfileTest` en verde: tipo no permitido → 400; >2 MB logo → 400; sin permiso → 403; otro almacén → 404; whatsapp inválido → 400 con mensaje en español; `GET profile` devuelve `logoUrl` tras cargar.

### T4 — Registro y reporte de visibilidad ✅ (2026-11-08)
- **Modelo:** Sonnet · **Agente:** backend
- **Archivos:** nuevos `core/analytics/{TrafficSource,VisibilityRecorder,VisibilityQueries,VisibilityReport}.java`; `operations/metrics/TrafficController.java` (endpoint `/events/visibility`, mismo `acquire`); `storesuite/analytics/AnalyticsController.java` (`/analytics/visibility`); `WebSecurityConfig` (POST público, **requiere aprobación**); tests en `backend/src/test/java/com/autopartco/backend/core/analytics/`.
- **Contexto:** depende de T1. Contratos `TrafficSource`, `POST /events/visibility` y `GET /analytics/visibility`. Hash del visitante y dominio del referente iguales a `TrafficRecorder` (extraer helpers compartidos, no duplicar). Nunca guardar IP, user-agent ni `account_id`.
- **Aceptación:** `./mvnw -q test -Dtest=TrafficSourceTest,VisibilityRecorderTest,VisibilityQueriesTest,TrafficControllerTest` en verde: tabla de casos de §TrafficSource (incluye `ref` sobre referente y `chatgpt.com` por `utm_source`); producto de otro almacén → no se registra; rango >366 días → 400; agregados `bySource`/`topProducts`/`daily` correctos con datos fijos.

### T5 — Perfil público, enlace desde producto y compartir
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** nuevos `frontend/src/features/marketplace/pages/store-page.tsx`, `frontend/src/features/marketplace/components/share-button.tsx`, `frontend/src/lib/visibility.ts`; `frontend/src/app/router.tsx`, `lazy-pages.tsx`; `features/marketplace/lib/paths.ts` (`storePath`); `features/marketplace/api/{dto.ts,http/http-service.ts,types.ts}`; `pages/product-page.tsx` (nombre en «Vendido por» como `Link` a `storePath`, `ShareButton`, `PRODUCT_VIEW`); `pages/stores-page.tsx` (tarjeta → perfil, con logo).
- **Contexto:** depende de T2 y T4. Contratos «API pública» y «Frontend». Seguir `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md` (§17, §20.15). Perfil: banner + logo (iniciales si falta), nombre, calificación, ciudades, contacto, pestañas o secciones «Repuestos» (búsqueda con `storeId`, paginada) y «Reseñas» (paginadas; formulario si `eligibility` trae pedidos). Sin dependencias nuevas.
- **Aceptación:** build y lint en verde; manual: desde `/tiendas` y desde un producto se llega a `/tiendas/:id`; compartir por WhatsApp copia `…?ref=wa` y muestra toast; abrir ese enlace envía `PRODUCT_VIEW` con `ref=wa` una vez y la URL queda sin `ref`; navegación interna posterior envía sin `ref`; sin scroll horizontal a 360 px; menú de compartir usable con teclado y `Escape`.

### T6 — Edición del perfil y estadísticas en el portal
- **Modelo:** Sonnet · **Agente:** frontend
- **Archivos:** `frontend/src/features/portal/pages/store-profile-page.tsx` (logo, banner con vista previa, contacto público, enlace «Ver mi perfil público»); nuevo `frontend/src/features/portal/pages/intelligence-visibility-page.tsx`; `router.tsx` (`inteligencia/visibilidad`, permiso `ANALYTICS_READ`); navegación del portal; API del portal.
- **Contexto:** depende de T3, T4 y T5. Contratos «API del portal». Estadísticas: totales (visitas al perfil, visitas a publicaciones, compartidos), serie diaria, tabla por origen y top de publicaciones con selector de rango; rotular «visitantes diarios».
- **Aceptación:** build y lint en verde; manual: subir logo lo muestra en el perfil público; archivo >2 MB muestra el error del servidor; whatsapp inválido marca el campo; la página de visibilidad muestra los datos de T4 y el estado vacío sin errores; tablas con `caption` y `th scope="col"`.

### T7 — QA del diff
- **Modelo:** Opus · **Agente:** qa-seguridad
- **Archivos:** solo lectura.
- **Contexto:** revisar solo el diff de T2–T6: IDOR en brand/visibility/reviews, validación de carga (tipo real, tamaño), abuso de `/events/visibility` (inflado de métricas, límite por IP), ausencia de datos personales en eventos (Ley 1581), XSS en descripción/comentarios, enlaces de redes construidos solo desde usuario validado.
- **Aceptación:** informe con hallazgos clasificados; tests de T2–T4 ejecutados con `-Dtest=` explícito.

## Riesgos y puntos a verificar

- **Inflado de métricas:** el límite por IP mitiga pero no evita bots; cuenta única por `visitor_hash`+día y producto como cifra principal.
- **Origen aproximado:** enlaces copiados fuera del menú o compartidos de nuevo heredan el `ref` original; se documenta en la UI como «origen estimado».
- **Plan comercial:** decidir si las estadísticas de visibilidad son de todos los planes o premium (`PlanEntitlements`); por defecto, todos.
- **Retención:** confirmar si existe job de purga de `traffic_events`; si no, añadir uno para ambas tablas (tarea aparte).
- **`WebSecurityConfig`:** T2 y T4 lo tocan; ejecutar en secuencia y con aprobación.
- **Frontend en otra cuenta:** T5 y T6 se ejecutan desde la cuenta de frontend con este documento como contrato.
