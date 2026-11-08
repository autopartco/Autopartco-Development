/**
 * Modelo de UI de ubicación: área del mapa y sedes. Las distancias solo existen
 * si el servicio las calcula; la UI nunca las inventa.
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

export interface LocationCapabilities {
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
  listCities(): Promise<City[]>
  /** exact → lat/lng y límite 5 con distancia; zone → límite 4 aleatorio, sin distancia. */
  getMapBranches(area: MapArea): Promise<MapBranch[]>
}
