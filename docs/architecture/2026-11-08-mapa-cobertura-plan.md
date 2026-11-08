# Plan: mini mapa de cobertura en `/` (MapLibre + OpenFreeMap + Photon)

## 1. Objetivo y alcance

Reemplazar la lista `NearbyStoresPanel` de `frontend/src/features/marketplace/home/home-hero.tsx` por un mini mapa monocromo (estilo dibujo/línea) que marca sedes de almacenes.

Entra:
- Selección de ubicación restringida a Colombia (país, departamento, ciudad, zona) con autocompletado Photon.
- Geolocalización del navegador, aceptada solo si cae en Colombia.
- Con ubicación exacta: marcador del usuario + sedes más cercanas (máx. 5).
- Sin ubicación exacta: 3–4 sedes aleatorias dentro de la zona elegida, distintas en cada carga.
- Lista accesible equivalente bajo el mapa (nombre, zona, distancia si existe, enlace a la tienda).

No entra: rutas, clustering, heatmaps, cambios de esquema, otros países, ubicación en checkout, servidores propios de teselas o geocodificación.

Sin claves, cuentas ni costes externos.

## 2. Componentes

| Componente | Capa | Responsabilidad |
|---|---|---|
| `BranchMapQueries` (`core/organization`) | backend | Sedes activas de tiendas publicadas con lat/lng dentro de un rectángulo; orden por distancia o aleatorio. |
| `BranchMapController` (`marketplace/stores`) | backend | Endpoint público `GET /marketplace/branches/map`. |
| `frontend/public/map/autopartco-style.json` | frontend | Estilo MapLibre derivado de OpenFreeMap Positron con la paleta §4.1. |
| `map-config.ts` (`features/location/lib`) | frontend | URL del estilo, `COLOMBIA_BOUNDS`, `isInColombia`, atribución. |
| `photon-geocoder.ts` (`features/location/api`) | frontend | Autocompletado y geocodificación inversa limitados a Colombia. |
| `location-store.ts` (`features/location`) | frontend | Sustituye `city-store.ts`: guarda `MapArea` elegida (localStorage `autopartco:area:v1`). |
| `LocationPicker` (`features/location/components`) | frontend | Reemplaza `city-picker.tsx`: autocompletado Colombia + "Usar mi ubicación". |
| `CoverageMap` (`features/location/components`) | frontend | Mapa MapLibre cargado en diferido, marcadores HTML de sedes y usuario, leyenda, estados. |
| `useMapBranches` (`features/location/hooks.ts`) | frontend | Llama al endpoint según la `MapArea`. |

## 3. Contratos

### 3.1 Backend

`GET /api/v1/marketplace/branches/map` (público, sin sesión, con límite por IP igual que `operations.metrics`).

Query:

| Parámetro | Tipo | Regla |
|---|---|---|
| `south`,`west`,`north`,`east` | decimal | Obligatorios. Rectángulo recortado a Colombia (lat −4.3…13.5, lng −82…−66.8); 400 si queda vacío. |
| `lat`,`lng` | decimal | Opcionales, ambos o ninguno. Con ellos: orden por distancia haversine ascendente. Sin ellos: `ORDER BY random()`. |
| `limit` | int | 1–8, por defecto 4. |

Respuesta `200`:

```json
[
  {
    "storeId": "uuid",
    "storeName": "string",
    "branchId": "uuid",
    "branchName": "string",
    "area": "string | null",        // address_complement o null
    "city": "string",
    "department": "string",
    "latitude": 4.6097100,
    "longitude": -74.0817500,
    "distanceKm": 1.6                // null si no se envió lat/lng
  }
]
```

Filtros: sede activa, tienda publicada (mismo criterio que `StoreService.published()`), `locations.latitude/longitude` no nulos, `deleted_at IS NULL`. Sin teléfono ni dirección exacta.

### 3.2 Servicios externos (públicos, sin clave)

| Uso | Petición | Regla |
|---|---|---|
| Teselas vectoriales | fuente `https://tiles.openfreemap.org/planet` (en el estilo) | Glifos: `https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf`. |
| Autocompletado | `GET https://photon.komoot.io/api/?q={texto}&limit=8&bbox=-82,-4.3,-66.8,13.5&layer=country&layer=state&layer=city&layer=district&layer=locality` | Descartar resultados con `properties.countrycode !== 'CO'`. Debounce 300 ms, mínimo 3 caracteres, caché en memoria por texto, `AbortController` por tecleo. |
| Inversa (geolocalización) | `GET https://photon.komoot.io/reverse?lat=&lon=` | Solo para confirmar `countrycode === 'CO'` y obtener la etiqueta (barrio, ciudad). |

`bounds` de un resultado Photon: `properties.extent` = `[west, north, east, south]`; si falta, rectángulo de ±0,05° alrededor de `geometry.coordinates`.

Atribución visible en el mapa: `© OpenFreeMap © OpenStreetMap` (control `AttributionControl` compacto, enlaces a las licencias).

### 3.3 Frontend

```ts
// features/location/api/types.ts
export type AreaPrecision = 'exact' | 'zone'   // exact = geolocalización

export interface MapArea {
  label: string                 // "Chapinero, Bogotá" / "Antioquia" / "Colombia"
  precision: AreaPrecision
  bounds: { south: number; west: number; north: number; east: number }
  center: GeoPoint
  user?: GeoPoint               // solo con precision 'exact'
}

export interface MapBranch {
  key: string                   // branchId
  storeId: string
  storeName: string
  branchName: string
  area?: string
  city: string
  point: GeoPoint
  distanceKm?: number
}

export interface PlaceSuggestion {
  id: string                    // `${osm_type}:${osm_id}`
  label: string                 // "Chapinero, Bogotá, Colombia"
  kind: 'country' | 'state' | 'city' | 'district' | 'locality'
  area: MapArea                 // precision 'zone'
}

export interface LocationService {
  readonly capabilities: LocationCapabilities   // geolocation: true
  getMapBranches(area: MapArea): Promise<MapBranch[]>   // exact → lat/lng + limit 5; zone → limit 4
}

// features/location/api/photon-geocoder.ts
export function searchPlaces(text: string, signal?: AbortSignal): Promise<PlaceSuggestion[]>
export function reverseInColombia(point: GeoPoint): Promise<{ label: string } | null>   // null fuera de CO
```

Área por defecto (sin elección ni permiso): Colombia completa, `precision: 'zone'`.

## 4. Decisiones

| Decisión | Motivo | Descartado |
|---|---|---|
| MapLibre GL JS + OpenFreeMap | Gratis, sin clave ni cuenta; vectorial, estilo editable. | Google Maps: exige facturación; sin clave sale oscurecido y sin estilo. Leaflet + teselas raster OSM: la política de uso de tile.openstreetmap.org no admite producción y no se puede recolorear. |
| Estilo propio en `public/map/` derivado de Positron | Paleta del sistema de diseño versionada en el repo. | Positron sin cambios (grises ajenos a la paleta). |
| Colores del estilo: tierra `#FFFFFF`, agua `#F4F3EF`, vías `#DDE0E2` (principales `#B9C0C5`), límites `#B9C0C5` discontinuo, etiquetas solo de lugares en `#626A70`, sin POI, edificios ni relieve | "Dibujo sin color" dentro de §4.1. | Mapa con colores o satelital. |
| Sedes: `maplibregl.Marker` con elemento HTML, chincheta grafito `#202326` con número; usuario: punto `ap-red` con halo | El rojo marca lo propio (§4.3); el estado no depende del color: lista numerada equivalente. | Capas de símbolos (peor accesibilidad y foco). |
| Photon con filtro `countrycode === 'CO'` + bbox | Autocompletado gratuito; cubre país/departamento/ciudad/barrio. | Nominatim para autocompletar (su política lo prohíbe). Lista DIVIPOLA propia (sin geometría). |
| Geolocalización confirmada con `reverseInColombia` | El bbox de Colombia incluye zonas de países vecinos. | Solo comprobar el bbox. |
| Aleatorio en backend (`ORDER BY random()`) | La rotación no expone todas las sedes de la zona. | Traer todas y muestrear en el cliente. |
| Distancias solo con `precision: 'exact'` | §20: no presentar aproximaciones como exactas. | Distancia al centro de la zona. |
| `maplibre-gl` cargado con `import()` diferido | No penaliza el LCP del hero. | Import estático en el bundle principal. |
| Interacción: `cooperativeGestures`, sin rotación ni inclinación, solo control de zoom | Mini mapa dentro del hero; no captura el scroll. | Mapa a pantalla completa. |

## 5. Tareas

Rama: `feature/mapa-cobertura` desde `develop` (worktree `C:\Development\Autopartco-dev-mapa`). Orden: 1 → 2 → 3 → 4 → 5 → 6 → 7.

### Tarea 1 — Endpoint de sedes en mapa
- Modelo: Sonnet. Agente: `backend`.
- Archivos: `backend/src/main/java/com/autopartco/backend/core/organization/BranchMapQueries.java` (nuevo, `JdbcClient`, patrón de `ListingQueries`), `.../core/organization/MapBranchView.java`, `.../marketplace/stores/BranchMapController.java`, `.../shared/security/WebSecurityConfig.java` (`PublicRoute GET /marketplace/branches/map` y límite por IP), `backend/src/test/.../BranchMapQueriesTest.java`, `BranchMapControllerTest.java`.
- Contexto: tablas `branches` (`state`, `store_id`, `location_id`), `locations` (`latitude`, `longitude`, `address_complement`, `city`, `department`, `deleted_at`). Contrato en §3.1. Sin migraciones.
- Aceptación: `mvn -Dtest=BranchMapQueriesTest,BranchMapControllerTest test` en verde cubriendo: bounds fuera de Colombia → 400; `lat` sin `lng` → 400; `limit` > 8 → 400; con lat/lng orden ascendente y `distanceKm` poblado; sin lat/lng `distanceKm` null; excluye sedes inactivas, sin coordenadas o de tiendas no publicadas; ruta accesible sin token.

### Tarea 2 — MapLibre, estilo y configuración
- Modelo: Sonnet. Agente: `frontend`. Requiere autorizar `npm i maplibre-gl@^5`.
- Archivos: `frontend/package.json`, `frontend/public/map/autopartco-style.json`, `frontend/src/features/location/lib/map-config.ts`.
- Contexto: partir de `https://tiles.openfreemap.org/styles/positron`, aplicar colores de §4, eliminar capas de POI, edificios y relieve. Exportar `MAP_STYLE_URL = '/map/autopartco-style.json'`, `COLOMBIA_BOUNDS`, `isInColombia(point)`, `loadMapLibre(): Promise<typeof import('maplibre-gl')>` (import diferido + CSS).
- Aceptación: `npm run build` y `npm run lint` en verde; `maplibre-gl` fuera del chunk principal (comprobar en `dist/assets`); el estilo carga sin errores en una página de prueba local.

### Tarea 3 — Servicio, geocodificador y estado de área
- Modelo: Sonnet. Agente: `frontend`.
- Archivos: `features/location/api/types.ts`, `api/photon-geocoder.ts` (nuevo), `api/http/http-location-service.ts`, `api/mock/mock-location-service.ts`, `location-store.ts` (nuevo), `hooks.ts`.
- Contexto: tipos y reglas de §3.2 y §3.3. HTTP llama a `/marketplace/branches/map`; mock filtra `MOCK_BRANCHES` por bounds y baraja. `useMapArea()` / `setMapArea()` con `useSyncExternalStore` y localStorage versionado (try/catch); no guardar `user` en localStorage. `useMapBranches()` con nonce por montaje para que `zone` rote en cada visita. `useStoreDistances` derivado de `useMapBranches` (solo con `exact`).
- Aceptación: build/lint en verde; `searchPlaces('medellin')` devuelve solo resultados CO con `area.bounds`; `searchPlaces('caracas')` devuelve vacío; con mock, dos recargas en la misma zona pueden dar conjuntos distintos de ≤4 sedes; con `exact` ≤5 ordenadas por distancia.

### Tarea 4 — Selector de ubicación (cabecera)
- Modelo: Sonnet. Agente: `frontend`.
- Archivos: `features/location/components/location-picker.tsx` (nuevo; reemplaza el uso de `city-picker.tsx` en la cabecera).
- Contexto: combobox accesible (patrón ARIA combobox con los primitivos Base UI ya usados) sobre `searchPlaces`; al elegir, `setMapArea(suggestion.area)`. Botón "Usar mi ubicación": `navigator.geolocation.getCurrentPosition` → `reverseInColombia`; si CO → `precision: 'exact'`, bounds de ~5 km, `user` en memoria; si no → toast "Solo mostramos almacenes en Colombia"; denegado/error → toast "No pudimos obtener tu ubicación", se conserva el área. Texto breve: "Usamos tu ubicación solo para mostrar almacenes cercanos; no la guardamos." Estilos de cabecera carbón (`TRIGGER_ON_DARK`).
- Aceptación: no se puede elegir un lugar fuera de Colombia; teclado completo (Tab, flechas, Enter, Esc); objetivos táctiles ≥44 px; estados de carga/sin resultados/error del autocompletado; §17 y §20.15 revisados.

### Tarea 5 — `CoverageMap` en el inicio
- Modelo: Sonnet (Opus si la integración falla tras 2 intentos). Agente: `frontend`.
- Archivos: `features/location/components/coverage-map.tsx` (nuevo), `features/marketplace/home/home-hero.tsx` (sustituye `NearbyStoresPanel`).
- Contexto: misma tarjeta actual (`rounded-xl border-ap-line bg-ap-white`, `ap-index` "Cobertura / {label}", título "Almacenes cerca de {label}"). Mapa 4:3 (≥240 px alto en móvil) con `MAP_STYLE_URL`, `cooperativeGestures`, `dragRotate: false`, `pitchWithRotate: false`, `NavigationControl({ showCompass: false })`, `AttributionControl({ compact: true })`, `fitBounds` a sedes + usuario con padding. Marcadores de §4; cada marcador es un `<a>` a `storeProductsPath(storeId)` con `aria-label` "{n}. {storeName}". Bajo el mapa, lista numerada coincidente (reutiliza el `<ol>` actual). Leyenda: "Tu ubicación" solo con `exact`; con `zone`: "Muestra de almacenes en {label}; cambia en cada visita". Estados: carga (skeleton del mapa), error de datos (`ErrorState` con reintentar), error de teselas/WebGL (solo lista), vacío ("Aún no hay almacenes en {label}"). `prefers-reduced-motion`: `fitBounds` sin animación. Destruir el mapa al desmontar.
- Aceptación: en el navegador, `/` muestra el mapa monocromo con 3–4 marcadores en zona y, con geolocalización, el marcador del usuario + cercanos; lista y marcadores coinciden; atribución visible; sin errores de consola; contraste AA; correcto a 375 px y en escritorio; §17 y §20.15 cumplidos.

### Tarea 6 — Limpieza
- Modelo: Haiku. Agente: `frontend`.
- Archivos: eliminar `city-picker.tsx`, `city-store.ts`, `listCities`, `getNearbyStores`, `NearbyStores`, `City`, `ReferencePoint` si ya no tienen usos.
- Aceptación: `grep -r "getNearbyStores\|city-store\|CityPicker" frontend/src` vacío; build/lint en verde.

### Tarea 7 — QA
- Modelo: Opus. Agente: `qa-seguridad`.
- Alcance: solo el diff de la rama.
- Revisar: endpoint sin datos sensibles, validación de parámetros, límite por IP; la ubicación exacta solo sale del navegador hacia `/marketplace/branches/map` y `photon.komoot.io/reverse`, nunca en logs, localStorage ni URL de la app; CSP (si existe) permite `tiles.openfreemap.org` y `photon.komoot.io`; tests de la tarea 1.
- Aceptación: informe sin hallazgos altos/críticos abiertos.

## 6. Riesgos y verificación

- Servicios públicos sin SLA (OpenFreeMap, Photon de komoot): con caída, el mapa degrada a lista y el selector a "sin resultados". Si el tráfico crece, alojar Photon/teselas propios.
- Política de uso de Photon: debounce, caché y una petición activa por vez; sin peticiones masivas.
- Ley 1581: la ubicación exacta se envía a Photon para geocodificación inversa; mencionarlo en la política de privacidad.
- `ORDER BY random()` escala mal; aceptable con < 10k sedes con coordenadas.
- Sedes sin coordenadas no aparecen: verificar en Supabase cuántas tienen `latitude/longitude` antes de la tarea 5.
- WebGL no disponible: `CoverageMap` muestra solo la lista.
