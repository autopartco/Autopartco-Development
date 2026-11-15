import type { StoreSettings, StoreSettingsService } from '../store-settings-service'
import { portalRequest, storePath } from './portal-http'

export function createHttpStoreSettingsService(): StoreSettingsService {
  return {
    get: (storeId) => portalRequest<StoreSettings>(storePath(storeId, '/settings')),
    patch: (storeId, patch) => portalRequest<StoreSettings>(storePath(storeId, '/settings'), { method: 'PATCH', body: patch }),
  }
}
