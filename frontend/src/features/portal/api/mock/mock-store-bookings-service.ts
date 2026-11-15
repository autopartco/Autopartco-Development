import { ApiError } from '@/lib/api'
import type { StoreBookingsService } from '../store-bookings-service'
import { getStore, requirePermission, simulateNetwork } from './mock-state'

/** Sin citas de ejemplo: el modo simulado no inventa solicitudes de compradores. */
export function createMockStoreBookingsService(): StoreBookingsService {
  return {
    async list(storeId, query) {
      await simulateNetwork()
      requirePermission(getStore(storeId), 'BOOKING_READ')
      return { items: [], page: query.page, size: query.size, total: 0, totalPages: 1 }
    },
    async decide(storeId) {
      await simulateNetwork()
      requirePermission(getStore(storeId), 'BOOKING_MANAGE')
      throw new ApiError('No encontramos esa cita en tu almacén.', { status: 404, code: 'BOOKING_NOT_FOUND' })
    },
  }
}
