import type { SubscriptionStatus } from '../api/types'

export const SUBSCRIPTION_STATUS_LABEL: Record<SubscriptionStatus, string> = {
  PENDING_SETUP: 'Configuración pendiente',
  TRIALING: 'Prueba gratuita',
  ACTIVE: 'Activa',
  EXPIRED: 'Vencida',
}

/** Texto del uso de un cupo, p. ej. "40 de 100" o "1 de ilimitado". */
export function usageText(used: number, limit: number | null): string {
  return limit === null ? `${used} de ilimitado` : `${used} de ${limit}`
}

export function formatDate(value: string | null): string {
  if (!value) return '—'
  return new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium' }).format(new Date(value))
}
