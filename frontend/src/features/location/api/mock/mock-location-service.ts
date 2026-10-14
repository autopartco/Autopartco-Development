import { getMarketplaceService } from '@/features/marketplace/api/marketplace-service'
import { haversineKm } from '../../lib/geo'
import type { City, LocationService, NearbyStore } from '../types'
import { DEFAULT_CITY_ID, MOCK_BRANCHES, MOCK_CITIES } from './mock-locations'

/**
 * Implementación simulada: ciudad por defecto, sedes con coordenadas y distancias
 * calculadas en el cliente desde el punto de referencia de cada ciudad.
 * Los datos de cada tienda (nombre, verificación, repuestos) vienen del marketplace.
 */
export function createMockLocationService(): LocationService {
  return {
    capabilities: { citySelection: true, distances: true, geolocation: false },
    defaultCityId: DEFAULT_CITY_ID,

    async listCities(): Promise<City[]> {
      return MOCK_CITIES.map(({ id, name, department }) => ({ id, name, department }))
    },

    async getNearbyStores(cityId) {
      const city = MOCK_CITIES.find((item) => item.id === cityId) ?? MOCK_CITIES.find((item) => item.id === DEFAULT_CITY_ID)
      if (!city) return { stores: [] }
      const marketplace = await getMarketplaceService()
      const stores = new Map((await marketplace.listStores()).map((store) => [store.id, store]))

      const nearby: NearbyStore[] = []
      for (const branch of MOCK_BRANCHES) {
        if (branch.cityId !== city.id) continue
        const store = stores.get(branch.storeId)
        if (!store) continue
        nearby.push({
          key: `${branch.storeId}:${branch.cityId}`,
          store,
          branchName: branch.branchName,
          area: branch.area,
          point: branch.point,
          distanceKm: haversineKm(city.origin.point, branch.point),
        })
      }

      return {
        city: { id: city.id, name: city.name, department: city.department },
        origin: city.origin,
        stores: nearby.toSorted((a, b) => (a.distanceKm ?? 0) - (b.distanceKm ?? 0)),
      }
    },
  }
}
