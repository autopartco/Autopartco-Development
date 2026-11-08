import type { GeoPoint } from '../api/types'

/** Estilo propio (versión sobria de OpenFreeMap), servido desde public/. */
export const MAP_STYLE_URL = '/map/autopartco-style.json'

/** Caja que contiene a Colombia continental e insular cercana. */
export const COLOMBIA_BOUNDS = { south: -4.3, west: -82, north: 13.5, east: -66.8 }

/** Indica si el punto cae dentro de la caja de Colombia. */
export function isInColombia(point: GeoPoint): boolean {
  return (
    point.lat >= COLOMBIA_BOUNDS.south &&
    point.lat <= COLOMBIA_BOUNDS.north &&
    point.lng >= COLOMBIA_BOUNDS.west &&
    point.lng <= COLOMBIA_BOUNDS.east
  )
}

type MapLibreModule = typeof import('maplibre-gl')

let cached: Promise<MapLibreModule> | null = null

/** Carga MapLibre y su CSS bajo demanda (chunk aparte); memoizado y reintentable si falla. */
export function loadMapLibre(): Promise<MapLibreModule> {
  if (!cached) {
    cached = Promise.all([import('maplibre-gl'), import('maplibre-gl/dist/maplibre-gl.css')])
      .then(([mod]) => mod)
      .catch((error) => {
        cached = null
        throw error
      })
  }
  return cached
}
