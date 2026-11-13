# Plan: mini mapa con todos los almacenes del área + almacenes recomendados (pagados)

Extiende `2026-11-08-mapa-cobertura-plan.md`.

## 1. Objetivo y alcance

Entra:
- El mini mapa del inicio (`CoverageMap`) muestra todas (o casi todas) las sedes activas dentro del área elegida, no 4–5.
- Agrupación (clusters) para que muchas sedes sigan siendo legibles en el mapa pequeño.
- Nueva ubicación publicitaria `MAP_FEATURED`: el almacén paga (campaña prepagada por día, Wompi, flujo actual de `core.advertising`) para salir como **Recomendado** en el mapa del área donde tiene sedes.
- Opción "Mapa del inicio" en el formulario de campañas del portal.

No entra:
- Talleres: no existen en el modelo (no hay entidad ni tabla). El mapa sigue mostrando solo sedes de almacenes. Modelar talleres es un plan aparte.
- Recarga al mover/hacer zoom (el área sigue siendo la elegida en el selector).
- Segmentación por palabras clave/vehículo en `MAP_FEATURED` (la "segmentación" es la ubicación de la sede).

## 2. Componentes

| Componente | Capa | Cambio |
|---|---|---|
| `ad_campaigns`, `ad_placement_prices`, `locations` | database (Supabase) | Admitir `MAP_FEATURED`; tarifa inicial; índice geográfico. |
| `AdPlacement` | backend `core.advertising` | Nuevo valor `MAP_FEATURED(3)`. |
| `AdCampaigns` / `AdCampaignRules` | backend `core.advertising` | Productos opcionales si la única ubicación es `MAP_FEATURED`. |
| `MapFeaturedStores` (nuevo) | backend `core.advertising` | Almacenes con campaña `MAP_FEATURED` vigente; elige hasta 3 y firma token de impresión. |
| `BranchMapQueries` / `BranchMapController` | backend | Límite mayor; compone sedes + recomendados. |
| `marketplace/package-info.java` | backend | Añadir `core.advertising` a `allowedDependencies`. |
| `features/location` (`types.ts`, servicio HTTP/mock, `coverage-map.tsx`) | frontend | Pedir más sedes, clusters, marcador y popup "Recomendado · Patrocinado". |
| `features/portal/pages/ads/campaign-form-page.tsx` | frontend | Ubicación "Mapa del inicio"; paso de productos opcional si es la única. |

## 3. Contratos

### 3.1 Base de datos (aplicar en Supabase `autopartco-dev`, no en el repo)

- Si `ad_campaigns.placements` o `ad_placement_prices.placement` tienen CHECK con la lista de valores, añadir `'MAP_FEATURED'`.
- `insert into ad_placement_prices (placement, daily_price, ...) values ('MAP_FEATURED', <precio que decida el usuario>, ...)` (columnas reales según la tabla).
- `create index if not exists idx_locations_lat_lng on locations (latitude, longitude) where deleted_at is null and latitude is not null;`
- `ad_campaign_products` (o la tabla de `productIds`) debe admitir campañas sin filas (sin cambios si no hay trigger que lo impida).

### 3.2 Backend

`GET /api/v1/marketplace/branches/map` — mismos parámetros; cambian:

| Parámetro | Antes | Ahora |
|---|---|---|
| `limit` | 1–8, defecto 4 | 1–200, defecto 100 |

Orden: recomendados primero; luego distancia (con `lat/lng`) o `random()` (sin ellos). Los recomendados no consumen `limit`.

Respuesta: cada elemento añade dos campos.

```json
{
  "...": "campos actuales",
  "recommended": true,
  "adImpressionToken": "string | null"   // solo si recommended; mismo formato que AdPick
}
```

Reglas de recomendados:
- Campaña `ACTIVE`, vigente hoy (`America/Bogota`), con `MAP_FEATURED` en `placements`, almacén publicado.
- Se marcan todas las sedes de ese almacén dentro del rectángulo; si el almacén no tiene sedes en el área, no aparece.
- Máx. 3 almacenes recomendados por respuesta (`AdPlacement.MAP_FEATURED.slots()`); si hay más, rotación aleatoria.
- Impresión/clic: mismo registro de eventos que el resto de anuncios con `productId = null`.

```java
// core.advertising
public record MapFeaturedPick(UUID campaignId, UUID storeId, String adImpressionToken) {}

@Service
public class MapFeaturedStores {
    /** Almacenes con MAP_FEATURED vigente entre {@code candidateStoreIds}; máx. {@code AdPlacement.MAP_FEATURED.slots()}. */
    public List<MapFeaturedPick> pick(Collection<UUID> candidateStoreIds, String visitorKey);
}

// core.organization
public List<UUID> storeIdsWithBranchesIn(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east); // en BranchMapQueries
public List<MapBranchView> findForStores(Collection<UUID> storeIds, bounds..., BigDecimal lat, BigDecimal lng); // en BranchMapQueries
```

`MapBranchView` añade `boolean recommended, String adImpressionToken` (el controlador construye la lista final).

Campañas (`POST /stores/{storeId}/ads/campaigns`): si `placements == [MAP_FEATURED]`, `productIds` puede venir vacío; si incluye otra ubicación, sigue siendo 1–20 productos (`AD_TOO_MANY_PRODUCTS` / nuevo `AD_PRODUCTS_REQUIRED`). `AdCampaignRequest.productIds` pasa de `@NotEmpty` a `@NotNull`.

### 3.3 Frontend

```ts
// features/location/api/types.ts
export interface MapBranch {
  // ...campos actuales
  recommended: boolean
  adImpressionToken?: string
}
// getMapBranches: exact → lat/lng + limit 100; zone → limit 150
```

`CoverageMap`:
- Fuente GeoJSON con `cluster: true` (`clusterRadius` 40, `clusterMaxZoom` 14) para sedes no recomendadas. Clusters y puntos sueltos como marcadores HTML `<button>` (patrón "HTML clusters": recalcular en `render` a partir de `querySourceFeatures`). Clic en cluster → `getClusterExpansionZoom` y `easeTo` (sin animación con `prefers-reduced-motion`).
- Recomendados: siempre como marcadores HTML individuales, nunca agrupados, encima de los demás; forma distinta (pin con estrella `lucide` `Star`, borde grafito), no solo color. Popup con etiqueta `Patrocinado` (reutilizar el estilo de `sponsored-listing.tsx`) y "Recomendado en {label}".
- Impresión al mostrarse y clic en "Ver repuestos" con el `adImpressionToken` (mismo cliente de eventos que los demás anuncios).
- Leyenda: "Recomendado (patrocinado)" + "Almacén" + "Tu ubicación" (solo `exact`). Texto bajo el mapa: "{n} almacenes en {label}".
- `fitBounds` a todas las sedes + usuario; con `exact`, encuadrar las 20 más cercanas.
- Si la respuesta llega al límite: "Mostrando {n} de los almacenes de {label}. Acerca la búsqueda para ver más."

Portal (`campaign-form-page.tsx`): ubicación "Mapa del inicio — tu almacén aparece como recomendado en el mapa de las zonas donde tienes sedes" con precio diario de `/ads/prices`. Si es la única ubicación marcada, ocultar/omitir el paso de productos. Aviso si el almacén no tiene sedes con coordenadas.

## 4. Decisiones

| Decisión | Motivo | Descartado |
|---|---|---|
| Reutilizar `core.advertising` con nueva ubicación | Cobro Wompi, estados, pausa/rechazo admin, estadísticas y tarifas ya existen. | Tabla/flujo de pago propio para el mapa. |
| Campaña de nivel almacén sin productos | Lo que se promociona es la sede, no un producto. | Exigir un producto ficticio. |
| Recomendados fuera del `limit` y nunca agrupados | Es lo que el almacén paga; debe verse siempre. | Mezclarlos en clusters. |
| Etiqueta "Patrocinado" visible | Publicidad identificable (SIC, Estatuto del Consumidor); coherente con `sponsored-listing.tsx`. | "Recomendado" a secas. |
| Clusters de MapLibre + marcadores HTML | Sin dependencias nuevas; mantiene foco y teclado de los marcadores. | `supercluster` (dependencia extra); capas de símbolos (peor accesibilidad). |
| Límite 200 por petición, sin recarga al mover | Una petición por área; respuesta < 60 KB. | Paginación por viewport (más complejo, no necesario hoy). |

## 5. Tareas

Rama `feature/mapa-recomendados` desde `develop`. Orden: 1 → 2 → 3 → 4 → 5 → 6 (4 y 5 pueden ir en paralelo tras 3).

### Tarea 1 — Supabase: ubicación y tarifa `MAP_FEATURED`, índice
- Modelo: Haiku. Agente: `database`. Requiere autorización (comandos contra BD) y el precio diario lo define el usuario.
- Contexto: §3.1. Inspeccionar con `list_tables`/`execute_sql` los CHECK de `ad_campaigns.placements`, `ad_placement_prices.placement` y la tabla de productos de campaña antes de cambiar nada. Aplicar con `apply_migration` en `autopartco-dev`; no escribir SQL en el repo.
- Aceptación: `select placement from ad_placement_prices` incluye `MAP_FEATURED`; insertar una campaña de prueba con `placements = '{MAP_FEATURED}'` y sin productos no viola restricciones (en transacción con rollback); índice creado.

### Tarea 2 — Backend: ubicación `MAP_FEATURED` y campañas sin productos
- Modelo: Sonnet. Agente: `backend`.
- Archivos: `core/advertising/AdPlacement.java`, `AdCampaignRequest.java`, `AdCampaigns.java` (`validate`), `AdCampaignRules.java`, `MapFeaturedStores.java` + `MapFeaturedPick.java` (nuevos), `AdEvents.java` (aceptar `productId` nulo si hace falta), `AdServer.java` (excluir `MAP_FEATURED` de `pick` de productos), tests `AdCampaignsTest`, `MapFeaturedStoresTest`.
- Contexto: §3.2. Fechas `America/Bogota`. Token con `AdTokenSigner` como `AdPick`.
- Aceptación: `mvn -Dtest=AdCampaignsTest,MapFeaturedStoresTest test` verde cubriendo: campaña solo `MAP_FEATURED` sin productos → OK; `MAP_FEATURED` + `SEARCH_TOP` sin productos → 400 `AD_PRODUCTS_REQUIRED`; `pick` devuelve solo campañas activas y vigentes de los almacenes candidatos, máx. 3; campaña pausada o vencida excluida.

### Tarea 3 — Backend: endpoint del mapa con más sedes y recomendados
- Modelo: Sonnet. Agente: `backend`.
- Archivos: `core/organization/BranchMapQueries.java`, `MapBranchView.java`, `marketplace/stores/BranchMapController.java`, `marketplace/package-info.java`, tests `BranchMapQueriesTest`, `BranchMapControllerTest`.
- Contexto: §3.2. Flujo: `storeIdsWithBranchesIn(bounds)` → `MapFeaturedStores.pick(...)` → `findForStores(recomendados)` + `find(..., limit)` excluyendo esas sedes → concatenar. `visitorKey` = IP (como otros anuncios).
- Aceptación: `mvn -Dtest=BranchMapQueriesTest,BranchMapControllerTest test` verde: `limit=200` OK, `201` → 400; sin `limit` devuelve hasta 100; recomendados primero con `recommended=true` y token; sedes no recomendadas con `recommended=false` y token nulo; un almacén recomendado sin sedes en el área no aparece; ruta pública sin token.

### Tarea 4 — Frontend: mapa con clusters y recomendados
- Modelo: Sonnet (Opus si los clusters HTML fallan tras 2 intentos). Agente: `frontend`.
- Archivos: `features/location/api/types.ts`, `api/http/http-location-service.ts`, `api/mock/mock-location-service.ts` (≥60 sedes mock, 2 recomendadas), `components/coverage-map.tsx`.
- Contexto: §3.3; leer `docs/design/AUTOPARTCO_DESIGN_SYSTEM.md` y `components/ads/sponsored-listing.tsx`.
- Aceptación: en `/` con mock y con API, el mapa muestra todas las sedes del área (clusters con conteo), recomendados destacados con "Patrocinado" en el popup; clic en cluster acerca; teclado: Tab recorre recomendados → clusters/puntos, Enter abre, Esc cierra; impresión registrada una vez por carga; sin errores de consola; 375 px y escritorio; §17 y §20.15 cumplidos.

### Tarea 5 — Frontend: "Mapa del inicio" en campañas del portal
- Modelo: Sonnet. Agente: `frontend`.
- Archivos: `features/portal/pages/ads/campaign-form-page.tsx` (y tipos/servicio de anuncios del portal si listan las ubicaciones).
- Contexto: §3.2 (reglas de productos) y §3.3 (texto). Precio desde `GET /stores/{storeId}/ads/prices`.
- Aceptación: crear y pagar (Wompi sandbox) una campaña solo `MAP_FEATURED` sin productos; combinarla con otra ubicación exige productos; el almacén aparece como recomendado en `/` al activarse.

### Tarea 6 — QA
- Modelo: Opus. Agente: `qa-seguridad`. Solo el diff de la rama.
- Revisar: el endpoint no expone datos nuevos sensibles (token firmado, sin datos de campaña/precio); `limit` validado y límite por IP intacto; un almacén no puede crear `MAP_FEATURED` para otro (IDOR en `/stores/{storeId}/ads`); campañas sin productos no rompen estadísticas, atribución de pedidos ni conciliación de pagos; etiqueta "Patrocinado" siempre visible.
- Aceptación: informe sin hallazgos altos/críticos abiertos.

## 5.1 Estado (2026-11-13)

- Tarea 1 aplicada en Supabase (`ads_map_featured_placement`): `MAP_FEATURED` en los CHECK de `ad_campaigns` (hasta 5 ubicaciones), `ad_placement_prices` y `ad_events`; tarifa inicial 10.000 COP/día (editable desde Admin → Publicidad); `ad_events.product_id` admite nulo solo con `MAP_FEATURED`; índice `idx_locations__lat_lng`.
- Tareas 2 y 3 hechas en `feature/mapa-recomendados`. Cambios respecto a §3.2: `MapFeaturedStores.pick(candidateStoreIds)` sin `visitorKey`; `BranchMapQueries.storeIdsIn(...)`, `findForStores(...)` y `find(..., excludedStoreIds)`; códigos 400 `AD_PRODUCTS_REQUIRED` (otra ubicación sin productos) y `AD_PRODUCTS_NOT_ALLOWED` (solo mapa con productos); `AdCampaignRequest.placements` hasta 5.
- Eventos: `POST /events/ads` acepta el `adImpressionToken` del mapa (impresión y clic) sin cambios de contrato.
- Pendiente: tareas 4 y 5 (frontend) y 6 (QA).

## 6. Riesgos y verificación

- `AdStatsCalculator`, `AdOrderAttribution` y `AdCandidateSource` asumen productos: verificar en tarea 2 que una campaña sin productos no rompe consultas (joins internos).
- Rendimiento: con miles de sedes, `ORDER BY random()` + 200 filas; el índice de la tarea 1 acota por rectángulo.
- Densidad visual en Colombia completa: clusters obligatorios; sin ellos, 150 marcadores tapan el mapa.
- Precio de `MAP_FEATURED`: decisión del usuario antes de la tarea 1.
- Talleres siguen fuera del mapa hasta que exista su modelo.
