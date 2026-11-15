import type { ServiceCategory } from '@/features/marketplace/api/types'

export const SERVICES_PATH = '/servicios'

export function servicePath(id: string): string {
  return `/servicio/${encodeURIComponent(id)}`
}

/** Enlace para pedir cita (`?agendar=1`); el flujo de agenda lo atiende la pantalla del detalle. */
export function serviceBookingPath(id: string): string {
  return `${servicePath(id)}?agendar=1`
}

/** URL canónica por categoría: `/servicios?category=AC`. */
export function servicesCategoryPath(category: ServiceCategory): string {
  return `${SERVICES_PATH}?category=${category}`
}
