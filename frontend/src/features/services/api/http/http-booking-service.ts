import { apiRequest } from '@/lib/api'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'
import type { Availability, Booking, BookingPage, BookingService } from '../booking-types'

const id = encodeURIComponent

/** `BuyerBookingController` (con sesión) y `ServiceAvailabilityController` (público). */
export function createHttpBookingService(): BookingService {
  const call = async <T>(path: string, options: { method?: 'POST'; body?: unknown } = {}) =>
    apiRequest<T>(path, { ...options, token: await requireAccessToken() })

  return {
    simulated: false,
    availability({ serviceId, branchId, from, days }) {
      const params = new URLSearchParams({ branchId, from, days: String(days) })
      return apiRequest<Availability>(`/marketplace/services/${id(serviceId)}/availability?${params}`)
    },
    create: (body) => call<Booking>('/bookings', { method: 'POST', body }),
    list({ status, page, size }) {
      const params = new URLSearchParams({ page: String(page), size: String(size) })
      if (status) params.set('status', status)
      return call<BookingPage>(`/bookings?${params}`)
    },
    get: (bookingId) => call<Booking>(`/bookings/${id(bookingId)}`),
    acceptProposal: (bookingId) => call<Booking>(`/bookings/${id(bookingId)}/accept-proposal`, { method: 'POST' }),
    cancel: (bookingId, reason) => call<Booking>(`/bookings/${id(bookingId)}/cancel`, { method: 'POST', body: reason ? { reason } : {} }),
  }
}
