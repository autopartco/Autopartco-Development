import { ApiError } from '@/lib/api'
import type { StoreServiceItem, StoreServicesService } from '../store-services-service'
import { SERVICE_IMAGE_MAX_BYTES, SERVICE_IMAGE_TYPES } from '../store-services-service'
import { getStore, newId, requirePermission, simulateNetwork } from './mock-state'

const byStore = new Map<string, StoreServiceItem[]>()

const rows = (storeId: string) => {
  if (!byStore.has(storeId)) byStore.set(storeId, [])
  return byStore.get(storeId)!
}

function find(storeId: string, serviceId: string): StoreServiceItem {
  const found = rows(storeId).find((row) => row.id === serviceId)
  if (!found) throw new ApiError('No encontramos ese servicio en tu almacén.', { status: 404, code: 'SERVICE_NOT_FOUND' })
  return found
}

/** Servicios simulados en memoria (sin imagen real: la vista previa usa el archivo elegido). */
export function createMockStoreServicesService(): StoreServicesService {
  return {
    async list(storeId) {
      await simulateNetwork()
      requirePermission(getStore(storeId), 'STORE_READ')
      return structuredClone(rows(storeId))
    },
    async create(storeId, input) {
      await simulateNetwork(600)
      requirePermission(getStore(storeId), 'STORE_UPDATE')
      const item: StoreServiceItem = {
        id: newId('service'),
        category: input.category,
        name: input.name,
        description: input.description ?? null,
        priceFrom: input.priceFrom,
        durationMinutes: input.durationMinutes,
        active: true,
        sortOrder: input.sortOrder ?? 0,
        branchIds: input.branchIds,
        version: 0,
        imageUrl: null,
        bookingEnabled: input.bookingEnabled,
        slotMinutes: input.slotMinutes,
      }
      rows(storeId).push(item)
      return structuredClone(item)
    },
    async update(storeId, serviceId, input) {
      await simulateNetwork(600)
      requirePermission(getStore(storeId), 'STORE_UPDATE')
      const item = find(storeId, serviceId)
      if (input.version !== undefined && input.version !== item.version) {
        throw new ApiError('Este servicio cambió mientras lo editabas.', { status: 409, code: 'SERVICE_VERSION_CONFLICT' })
      }
      Object.assign(item, {
        category: input.category,
        name: input.name,
        description: input.description ?? null,
        priceFrom: input.priceFrom,
        durationMinutes: input.durationMinutes,
        branchIds: input.branchIds,
        active: input.active ?? item.active,
        bookingEnabled: input.bookingEnabled,
        slotMinutes: input.slotMinutes,
        version: item.version + 1,
      })
      return structuredClone(item)
    },
    async deactivate(storeId, serviceId) {
      await simulateNetwork()
      requirePermission(getStore(storeId), 'STORE_UPDATE')
      const item = find(storeId, serviceId)
      item.active = false
      item.version += 1
    },
    async uploadImage(storeId, serviceId, file) {
      await simulateNetwork(700)
      requirePermission(getStore(storeId), 'STORE_UPDATE')
      const item = find(storeId, serviceId)
      if (!SERVICE_IMAGE_TYPES.includes(file.type) || file.size > SERVICE_IMAGE_MAX_BYTES) {
        throw new ApiError('La imagen debe ser PNG, JPEG o WebP de hasta 5 MB.', { status: 400, code: 'INVALID_IMAGE' })
      }
      item.imageUrl = URL.createObjectURL(file)
      return structuredClone(item)
    },
    async removeImage(storeId, serviceId) {
      await simulateNetwork()
      requirePermission(getStore(storeId), 'STORE_UPDATE')
      const item = find(storeId, serviceId)
      item.imageUrl = null
      return structuredClone(item)
    },
  }
}
