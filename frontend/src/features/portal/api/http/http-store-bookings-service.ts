import type { StoreBookingPage, StoreBookingsService } from '../store-bookings-service'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/bookings/StoreBookingController`. */
export function createHttpStoreBookingsService(): StoreBookingsService {
  return {
    list(storeId, { status, branchId, from, to, page, size }) {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (status) params.set('status', status)
      if (branchId) params.set('branchId', branchId)
      if (from) params.set('from', from)
      if (to) params.set('to', to)
      return portalRequest<StoreBookingPage>(`${storePath(storeId, '/bookings')}?${params}`)
    },
    decide(storeId, bookingId, action, body) {
      return portalRequest(storePath(storeId, `/bookings/${encodeURIComponent(bookingId)}/${action}`), { method: 'POST', body })
    },
  }
}
