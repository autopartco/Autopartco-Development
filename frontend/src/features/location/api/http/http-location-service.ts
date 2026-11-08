import { apiRequest } from '@/lib/api'
import type { LocationService, MapBranch } from '../types'

interface MapBranchDto {
  storeId: string
  storeName: string
  branchId: string
  branchName: string
  area: string | null
  city: string
  latitude: number
  longitude: number
  distanceKm: number | null
}

/**
 * Implementación contra el backend real. Hoy no hay endpoints públicos de ciudades
 * ni de sedes con ubicación, así que devuelve las tiendas publicadas SIN distancia
 * y la UI no muestra kilómetros.
 *
 * TODO(api): exponer en /marketplace (sin sesión):
 *   GET /marketplace/cities -> [{ id, name, department }]
 *   GET /marketplace/stores/nearby?cityId=&lat=&lng=&limit=
 *     -> [{ storeId, branchId, branchName, area, latitude, longitude, distanceKm }]
 *   El backend ya guarda latitud y longitud de cada sede (BranchLocationResponse),
 *   pero no las publica en el marketplace.
 */
export function createHttpLocationService(): LocationService {
  return {
    capabilities: { geolocation: true },

    async listCities() {
      return []
    },

    async getMapBranches(area) {
      const { bounds, precision, user } = area
      const exact = precision === 'exact' && user !== undefined
      const params = new URLSearchParams({
        south: String(bounds.south),
        west: String(bounds.west),
        north: String(bounds.north),
        east: String(bounds.east),
        limit: exact ? '5' : '4',
      })
      if (exact) {
        params.set('lat', String(user.lat))
        params.set('lng', String(user.lng))
      }
      const rows = await apiRequest<MapBranchDto[]>(`/marketplace/branches/map?${params}`)
      return rows.map(
        (row): MapBranch => ({
          key: row.branchId,
          storeId: row.storeId,
          storeName: row.storeName,
          branchName: row.branchName,
          area: row.area ?? undefined,
          city: row.city,
          point: { lat: row.latitude, lng: row.longitude },
          distanceKm: row.distanceKm ?? undefined,
        }),
      )
    },
  }
}
