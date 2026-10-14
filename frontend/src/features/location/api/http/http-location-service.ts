import { getMarketplaceService } from '@/features/marketplace/api/marketplace-service'
import type { LocationService } from '../types'

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
    capabilities: { citySelection: false, distances: false, geolocation: false },

    async listCities() {
      return []
    },

    async getNearbyStores() {
      const marketplace = await getMarketplaceService()
      const stores = await marketplace.listStores()
      return { stores: stores.map((store) => ({ key: store.id, store })) }
    },
  }
}
