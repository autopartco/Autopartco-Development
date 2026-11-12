import { apiAssetUrl } from '@/lib/api'
import type { PortalService } from '../portal-service'
import type { MyStore, StorePermission, StoreProfile } from '../types'
import { portalRequest, storePath } from './portal-http'

/** El backend envía logo y banner como rutas de la API: aquí se vuelven URLs cargables (API en otro dominio). */
function withAssets(profile: StoreProfile): StoreProfile {
  return { ...profile, logoUrl: apiAssetUrl(profile.logoUrl), bannerUrl: apiAssetUrl(profile.bannerUrl) }
}

/** `storesuite/store/MeStoreController` y `StoreProfileController`. */
export function createHttpPortalService(): PortalService {
  return {
    myStores: async () => (await portalRequest<MyStore[]>('/me/stores')).map((store) => ({ ...store, logoUrl: apiAssetUrl(store.logoUrl) })),
    permissions: (storeId) => portalRequest<StorePermission[]>(storePath(storeId, '/my-permissions')),
    profile: async (storeId) => withAssets(await portalRequest<StoreProfile>(storePath(storeId, '/profile'))),
    async updateProfile(storeId, input) {
      await portalRequest(storePath(storeId), { method: 'PATCH', body: input })
    },
    async uploadBrand(storeId, asset, file) {
      const body = new FormData()
      body.append('file', file)
      return withAssets(await portalRequest<StoreProfile>(storePath(storeId, `/brand/${asset}`), { method: 'POST', body }))
    },
    async removeBrand(storeId, asset) {
      await portalRequest(storePath(storeId, `/brand/${asset}`), { method: 'DELETE' })
    },
  }
}
