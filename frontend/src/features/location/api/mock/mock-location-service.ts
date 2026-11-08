import { getMarketplaceService } from '@/features/marketplace/api/marketplace-service'
import { haversineKm } from '../../lib/geo'
import type { City, LocationService, MapBranch, NearbyStore } from '../types'
import { DEFAULT_CITY_ID, MOCK_BRANCHES, MOCK_CITIES } from './mock-locations'

/** Fisher–Yates. */
function shuffle<T>(items: T[]): T[] {
  const copy = [...items]
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[copy[i], copy[j]] = [copy[j], copy[i]]
  }
  return copy
}

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

    async getMapBranches(area) {
      const { bounds, precision, user } = area
      const marketplace = await getMarketplaceService()
      const stores = new Map((await marketplace.listStores()).map((store) => [store.id, store]))
      const inside: MapBranch[] = []
      for (const branch of MOCK_BRANCHES) {
        const { lat, lng } = branch.point
        if (lat < bounds.south || lat > bounds.north || lng < bounds.west || lng > bounds.east) continue
        const store = stores.get(branch.storeId)
        if (!store) continue
        inside.push({
          key: `${branch.storeId}:${branch.cityId}:${branch.branchName}`,
          storeId: branch.storeId,
          storeName: store.name,
          branchName: branch.branchName,
          area: branch.area,
          city: MOCK_CITIES.find((item) => item.id === branch.cityId)?.name ?? '',
          point: branch.point,
        })
      }
      if (precision === 'exact' && user) {
        return inside
          .map((branch) => ({ ...branch, distanceKm: haversineKm(user, branch.point) }))
          .sort((a, b) => a.distanceKm - b.distanceKm)
          .slice(0, 5)
      }
      return shuffle(inside).slice(0, 4)
    },
  }
}
