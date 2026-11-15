import { DATA_SOURCE } from '@/lib/api'
import type { BookingService } from './booking-types'

let bookingPromise: Promise<BookingService> | undefined

/** Misma selección `mock`/`http` que el resto de servicios; la implementación se descarga bajo demanda. */
export function getBookingService(): Promise<BookingService> {
  bookingPromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-booking-service').then((module) => module.createHttpBookingService())
      : import('./mock/mock-booking-service').then((module) => module.createMockBookingService())
  return bookingPromise
}
