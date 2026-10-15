import type { PortalService } from '../portal-service'
import type { MyStore, StorePermission, StoreProfile } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/store/MeStoreController` y `StoreProfileController`. */
export function createHttpPortalService(): PortalService {
  return {
    myStores: () => portalRequest<MyStore[]>('/me/stores'),
    permissions: (storeId) => portalRequest<StorePermission[]>(storePath(storeId, '/my-permissions')),
    profile: (storeId) => portalRequest<StoreProfile>(storePath(storeId, '/profile')),
    async updateProfile(storeId, input) {
      await portalRequest(storePath(storeId), { method: 'PATCH', body: input })
    },
  }
}
