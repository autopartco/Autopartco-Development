import type { ServiceCategory } from '@/features/marketplace/api/types'

export const SERVICES_PATH = '/servicios'

export function servicePath(id: string): string {
  return `/servicio/${encodeURIComponent(id)}`
}

/** Estado del flujo de agenda que viaja en la URL (no contiene datos personales). */
export interface BookingFlowParams {
  branchId?: string
  day?: string
  startsAt?: string
}

/** Enlace para pedir cita (`?agendar=1&sede=&dia=&hora=`); el flujo lo muestra la pantalla del detalle. */
export function serviceBookingPath(id: string, state: BookingFlowParams = {}): string {
  const params = new URLSearchParams({ agendar: '1' })
  if (state.branchId) params.set('sede', state.branchId)
  if (state.day) params.set('dia', state.day)
  if (state.startsAt) params.set('hora', state.startsAt)
  return `${servicePath(id)}?${params}`
}

export const BOOKINGS_PATH = '/mis-citas'

export function bookingPath(id: string): string {
  return `${BOOKINGS_PATH}/${encodeURIComponent(id)}`
}

/** URL canónica por categoría: `/servicios?category=AC`. */
export function servicesCategoryPath(category: ServiceCategory): string {
  return `${SERVICES_PATH}?category=${category}`
}
