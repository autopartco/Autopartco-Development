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

export interface LocationService {
  readonly capabilities: LocationCapabilities
  readonly defaultCityId?: string
  listCities(): Promise<City[]>
  /** `cityId` sin definir usa la ciudad por defecto del servicio. */
  getNearbyStores(cityId: string | undefined): Promise<NearbyStores>
}
