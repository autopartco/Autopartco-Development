import { DATA_SOURCE, apiRequest } from '@/lib/api'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'

/** `NotificationResponse`: `link` es una ruta interna del frontend (`/…`) o `null`. */
export interface AppNotification {
  id: string
  title: string
  message: string
  link: string | null
  read: boolean
  createdAt: string
}

/** `GET /me/notifications`. Con la fuente `mock` no hay notificaciones. */
export async function listNotifications(): Promise<AppNotification[]> {
  if (DATA_SOURCE !== 'http') return []
  return apiRequest<AppNotification[]>('/me/notifications', { token: await requireAccessToken() })
}

/** `PATCH /me/notifications/{id}/read`. */
export async function markNotificationRead(id: string): Promise<void> {
  if (DATA_SOURCE !== 'http') return
  await apiRequest<void>(`/me/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH', token: await requireAccessToken() })
}
