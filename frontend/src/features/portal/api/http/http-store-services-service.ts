import { apiAssetUrl } from '@/lib/api'
import type { StoreServiceItem, StoreServicesService } from '../store-services-service'
import { portalRequest, storePath } from './portal-http'

const withImage = (service: StoreServiceItem): StoreServiceItem => ({ ...service, imageUrl: apiAssetUrl(service.imageUrl) })

/** `storesuite/services/StoreServiceController`. */
export function createHttpStoreServicesService(): StoreServicesService {
  const path = (storeId: string, suffix = '') => storePath(storeId, `/services${suffix}`)
  const one = (serviceId: string) => `/${encodeURIComponent(serviceId)}`
  return {
    async list(storeId) {
      return (await portalRequest<StoreServiceItem[]>(`${path(storeId)}?includeInactive=true`)).map(withImage)
    },
    async create(storeId, input) {
      return withImage(await portalRequest<StoreServiceItem>(path(storeId), { method: 'POST', body: input }))
    },
    async update(storeId, serviceId, input) {
      return withImage(await portalRequest<StoreServiceItem>(path(storeId, one(serviceId)), { method: 'PUT', body: input }))
    },
    async deactivate(storeId, serviceId) {
      await portalRequest(path(storeId, one(serviceId)), { method: 'DELETE' })
    },
    async uploadImage(storeId, serviceId, file) {
      const body = new FormData()
      body.append('file', file)
      return withImage(await portalRequest<StoreServiceItem>(path(storeId, `${one(serviceId)}/image`), { method: 'POST', body }))
    },
    async removeImage(storeId, serviceId) {
      return withImage(await portalRequest<StoreServiceItem>(path(storeId, `${one(serviceId)}/image`), { method: 'DELETE' }))
    },
  }
}
