import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getBookingService } from './api/booking-service'
import type { Availability, BookingService, BookingStatus } from './api/booking-types'
import { todayInBogota } from './lib/booking'

/** Días de agenda que se consultan de una vez (máximo del servidor). */
export const AVAILABILITY_DAYS = 14

const EMPTY: Availability = { slotMinutes: 0, days: [] }

const AVAILABILITY_PREFIX = 'services:availability:'
// Prefijo `portal:` para que la caché se descarte al cerrar sesión (ver RequireSession).
const BUYER_BOOKINGS_PREFIX = 'portal:buyer-booking'

function useBookings<T>(key: string, run: (service: BookingService) => Promise<T>) {
  return useAsync(key, () => getBookingService().then(run))
}

/** Horas de un servicio en una sede (público). Sin sede devuelve vacío sin consultar. */
export function useAvailability(serviceId: string, branchId: string | undefined) {
  const from = todayInBogota()
  return useBookings(`${AVAILABILITY_PREFIX}${serviceId}:${branchId ?? ''}:${from}`, (service) =>
    branchId ? service.availability({ serviceId, branchId, from, days: AVAILABILITY_DAYS }) : Promise.resolve(EMPTY),
  )
}

/** Descarta las horas en memoria (tras `BOOKING_SLOT_INVALID` o una cita nueva). */
export function refreshAvailability(): void {
  invalidateAsync(AVAILABILITY_PREFIX)
}

export function useBuyerBookings(status: BookingStatus | undefined, page: number, size: number) {
  return useBookings(`${BUYER_BOOKINGS_PREFIX}s:${status ?? 'all'}:${page}:${size}`, (service) => service.list({ status, page, size }))
}

export function useBuyerBooking(bookingId: string) {
  return useBookings(`${BUYER_BOOKINGS_PREFIX}:${bookingId}`, (service) => service.get(bookingId))
}

/** Tras crear, aceptar o cancelar: las listas y el detalle recargan. */
export function refreshBuyerBookings(): void {
  invalidateAsync(BUYER_BOOKINGS_PREFIX)
}
