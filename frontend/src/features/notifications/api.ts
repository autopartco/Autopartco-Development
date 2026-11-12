import { DATA_SOURCE, apiRequest } from '@/lib/api'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'

export type NotificationKind =
  | 'ORDER_STATUS'
  | 'QUESTION'
  | 'ANSWER'
  | 'REVIEW_REQUEST'
  | 'REVIEW_REPLY'
  | 'STORE_ALERT'
  | 'ACCOUNT_SECURITY'
  | 'SYSTEM'

/** `NotificationResponse`: `link` es una ruta interna del frontend (`/…`) o `null`. */
export interface AppNotification {
  id: string
  kind: NotificationKind
  title: string
  message: string
  link: string | null
  read: boolean
  archived: boolean
  createdAt: string
}

/** `PageResponse` del backend (páginas desde 1). */
export interface NotificationPage {
  items: AppNotification[]
  page: number
  size: number
  total: number
  totalPages: number
}

export type NotificationStatus = 'active' | 'archived'

const EMPTY_PAGE: NotificationPage = { items: [], page: 1, size: 20, total: 0, totalPages: 0 }

async function token() {
  return requireAccessToken()
}

/** `GET /me/notifications?status=&page=&size=`. Con la fuente `mock` no hay notificaciones. */
export async function listNotifications(status: NotificationStatus = 'active', page = 1, size = 20): Promise<NotificationPage> {
  if (DATA_SOURCE !== 'http') return { ...EMPTY_PAGE, size }
  const params = new URLSearchParams({ status, page: String(page), size: String(size) })
  return apiRequest<NotificationPage>(`/me/notifications?${params}`, { token: await token() })
}

/** `GET /me/notifications/unread-count` (solo la bandeja activa). */
export async function unreadNotificationCount(): Promise<number> {
  if (DATA_SOURCE !== 'http') return 0
  return (await apiRequest<{ count: number }>('/me/notifications/unread-count', { token: await token() })).count
}

/** `PATCH /me/notifications/{id}/read`. */
export async function markNotificationRead(id: string): Promise<void> {
  if (DATA_SOURCE !== 'http') return
  await apiRequest<void>(`/me/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH', token: await token() })
}

/** `POST /me/notifications/read-all`. */
export async function markAllNotificationsRead(): Promise<void> {
  if (DATA_SOURCE !== 'http') return
  await apiRequest<void>('/me/notifications/read-all', { method: 'POST', token: await token() })
}

/** `POST /me/notifications/{id}/archive`: pasa al histórico. */
export async function archiveNotification(id: string): Promise<void> {
  if (DATA_SOURCE !== 'http') return
  await apiRequest<void>(`/me/notifications/${encodeURIComponent(id)}/archive`, { method: 'POST', token: await token() })
}

/** `POST /me/notifications/archive-read`: «Limpiar» las leídas. */
export async function archiveReadNotifications(): Promise<void> {
  if (DATA_SOURCE !== 'http') return
  await apiRequest<void>('/me/notifications/archive-read', { method: 'POST', token: await token() })
}
