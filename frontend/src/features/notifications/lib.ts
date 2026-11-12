import { invalidateAsync } from '@/lib/use-async'

/** Prefijo de caché: empieza por `portal:` para que se descarte al cerrar sesión (ver RequireSession). */
export const NOTIFICATIONS_KEY = 'portal:notifications:'

/** Vuelve a pedir menú, contador y páginas de la bandeja tras cualquier cambio. */
export function refreshNotifications(): void {
  invalidateAsync(NOTIFICATIONS_KEY)
}

const MINUTE = 60_000
const HOUR = 60 * MINUTE
const DAY = 24 * HOUR

/** «Ahora», «hace 5 min», «hace 3 h», «ayer», «hace 4 días» o la fecha corta. */
export function notificationTime(iso: string, now = Date.now()): string {
  const time = new Date(iso).getTime()
  if (Number.isNaN(time)) return ''
  const elapsed = Math.max(0, now - time)
  if (elapsed < MINUTE) return 'Ahora'
  if (elapsed < HOUR) return `Hace ${Math.floor(elapsed / MINUTE)} min`
  if (elapsed < DAY) return `Hace ${Math.floor(elapsed / HOUR)} h`
  const days = Math.floor(elapsed / DAY)
  if (days === 1) return 'Ayer'
  if (days < 7) return `Hace ${days} días`
  return new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: days > 300 ? 'numeric' : undefined }).format(time)
}
