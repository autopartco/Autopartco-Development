import type { OpeningHours } from '@/features/marketplace/api/types'
import { WEEK_DAYS } from '@/features/marketplace/lib/store-links'
import type { ServiceDetail } from '../api/types'

/** El servicio admite agenda y al menos una de sus sedes publica horario semanal. */
export function canBookOnline(service: Pick<ServiceDetail, 'bookingEnabled' | 'branches'>): boolean {
  return service.bookingEnabled && service.branches.some((branch) => hasWeeklyHours(branch.openingHours))
}

/**
 * `true` si la sede tiene horario semanal: al menos un día con una franja. Un día ausente o vacío está cerrado, y
 * un horario con solo notas no basta para ofrecer horas de cita. Acepta el horario público y el del portal.
 */
export function hasWeeklyHours(openingHours: OpeningHours | Record<string, unknown> | null | undefined): boolean {
  if (!openingHours) return false
  const hours = openingHours as Record<string, unknown>
  return WEEK_DAYS.some((day) => Array.isArray(hours[day]) && hours[day].length > 0)
}
