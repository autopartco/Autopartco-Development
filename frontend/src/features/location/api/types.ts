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
  /** Almacén con campaña «Mapa del inicio» vigente: se muestra destacado y con la etiqueta «Patrocinado». */
  recommended: boolean
  /** Token firmado para registrar impresión y clic; solo en recomendados. */
  adImpressionToken?: string
}

/** Sedes que se piden por área (sin contar los recomendados). */
export const MAP_LIMIT_ZONE = 150
export const MAP_LIMIT_EXACT = 100

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
  /**
   * Todas (o casi todas) las sedes del área: exact → lat/lng, por distancia, hasta MAP_LIMIT_EXACT;
   * zone → aleatorio, hasta MAP_LIMIT_ZONE. Los recomendados llegan primero y fuera del límite.
   */
  getMapBranches(area: MapArea): Promise<MapBranch[]>
}
