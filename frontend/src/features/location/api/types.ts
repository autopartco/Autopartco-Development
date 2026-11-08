import type { StoreSummary } from '@/features/marketplace/api/types'

/**
 * Modelo de UI de ubicación: ciudad elegida, punto de referencia y almacenes
 * cercanos. Las distancias solo existen si el servicio las calcula; la UI nunca
 * las inventa.
 */

export interface GeoPoint {
  lat: number
  lng: number
}

export interface City {
  id: string
  name: string
  department?: string
}

/** Punto desde el que se miden las distancias (hoy, un barrio de referencia; no la ubicación real). */
export interface ReferencePoint {
  label: string
  point?: GeoPoint
}

export interface NearbyStore {
  /** Clave única de la sede; úsala como `key` en listas. */
  key: string
  store: StoreSummary
  branchName?: string
  /** Barrio o zona de la sede. */
  area?: string
  point?: GeoPoint
  /** Distancia en línea recta desde el punto de referencia, en km. */
  distanceKm?: number
}

export interface NearbyStores {
  city?: City
  origin?: ReferencePoint
  /** Ordenadas de la más cercana a la más lejana cuando hay distancias. */
  stores: NearbyStore[]
}

export interface LocationCapabilities {
  /** El usuario puede elegir ciudad. */
  citySelection: boolean
  /** El servicio devuelve distancias. */
  distances: boolean
  /** Se puede usar la geolocalización del navegador. */
  geolocation: boolean
}

export type AreaPrecision = 'exact' | 'zone' // exact = geolocalización

export interface MapArea {
  /** "Chapinero, Bogotá" / "Antioquia" / "Colombia" */
  label: string
  precision: AreaPrecision
  bounds: { south: number; west: number; north: number; east: number }
  center: GeoPoint
  /** Solo con precision 'exact'; nunca se persiste. */
  user?: GeoPoint
}

export interface MapBranch {
  /** branchId */
  key: string
  storeId: string
  storeName: string
  branchName: string
  area?: string
  city: string
  point: GeoPoint
  distanceKm?: number
}

export interface PlaceSuggestion {
  /** `${osm_type}:${osm_id}` */
  id: string
  /** "Chapinero, Bogotá, Colombia" */
  label: string
  kind: 'country' | 'state' | 'city' | 'district' | 'locality'
  /** precision 'zone' */
  area: MapArea
}

export interface LocationService {
  readonly capabilities: LocationCapabilities
  readonly defaultCityId?: string
  listCities(): Promise<City[]>
  /** `cityId` sin definir usa la ciudad por defecto del servicio. */
  getNearbyStores(cityId: string | undefined): Promise<NearbyStores>
  /** exact → lat/lng y límite 5 con distancia; zone → límite 4 aleatorio, sin distancia. */
  getMapBranches(area: MapArea): Promise<MapBranch[]>
}
