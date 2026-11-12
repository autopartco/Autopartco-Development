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


// --------------------------------------------------------------- simulado (fuente `mock`)

const ago = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString()
const MOCK: AppNotification[] = [
  { id: 'n1', kind: 'ORDER_STATUS', title: 'Pedido actualizado', message: 'Tu pedido #1042 está en camino.', link: '/mis-pedidos', read: false, archived: false, createdAt: ago(4) },
  { id: 'n2', kind: 'ANSWER', title: 'Respondieron tu pregunta', message: 'Repuestos La 13 respondió tu pregunta sobre Filtro de aceite.', link: '/buscar', read: false, archived: false, createdAt: ago(95) },
  { id: 'n3', kind: 'REVIEW_REQUEST', title: '¿Qué tal tu compra?', message: 'Califica las pastillas de freno que recibiste.', link: '/cuenta/calificar', read: true, archived: false, createdAt: ago(60 * 26) },
  { id: 'n4', kind: 'ORDER_STATUS', title: 'Envío registrado', message: 'Se registró el envío de tu pedido #1038.', link: null, read: true, archived: false, createdAt: ago(60 * 24 * 3) },
  { id: 'n5', kind: 'SYSTEM', title: 'Bienvenido a Autopartco', message: 'Busca repuestos por vehículo y compara almacenes cerca de ti.', link: null, read: true, archived: false, createdAt: ago(60 * 24 * 9) },
  { id: 'n6', kind: 'ORDER_STATUS', title: 'Pedido actualizado', message: 'Tu pedido #1031 fue entregado.', link: '/mis-pedidos', read: true, archived: false, createdAt: ago(60 * 24 * 12) },
  { id: 'n7', kind: 'ORDER_STATUS', title: 'Pedido actualizado', message: 'Tu pedido #1029 fue confirmado.', link: '/mis-pedidos', read: true, archived: false, createdAt: ago(60 * 24 * 15) },
  { id: 'n8', kind: 'ANSWER', title: 'Respondieron tu pregunta', message: 'Autopartes del Valle respondió sobre una batería.', link: '/buscar', read: true, archived: true, createdAt: ago(60 * 24 * 30) },
]

function mockPage(status: NotificationStatus, page: number, size: number): NotificationPage {
  const all = MOCK.filter((item) => item.archived === (status === 'archived'))
  const totalPages = Math.ceil(all.length / size)
  return { items: structuredClone(all.slice((page - 1) * size, page * size)), page, size, total: all.length, totalPages }
}

function mockUpdate(match: (item: AppNotification) => boolean, change: Partial<AppNotification>) {
  for (const item of MOCK) if (match(item)) Object.assign(item, change)
}

async function token() {
  return requireAccessToken()
}

/** `GET /me/notifications?status=&page=&size=`. Con la fuente `mock`, datos simulados en memoria. */
export async function listNotifications(status: NotificationStatus = 'active', page = 1, size = 20): Promise<NotificationPage> {
  if (DATA_SOURCE !== 'http') return mockPage(status, page, size)
  const params = new URLSearchParams({ status, page: String(page), size: String(size) })
  return apiRequest<NotificationPage>(`/me/notifications?${params}`, { token: await token() })
}

/** `GET /me/notifications/unread-count` (solo la bandeja activa). */
export async function unreadNotificationCount(): Promise<number> {
  if (DATA_SOURCE !== 'http') return MOCK.filter((item) => !item.read && !item.archived).length
  return (await apiRequest<{ count: number }>('/me/notifications/unread-count', { token: await token() })).count
}

/** `PATCH /me/notifications/{id}/read`. */
export async function markNotificationRead(id: string): Promise<void> {
  if (DATA_SOURCE !== 'http') return mockUpdate((item) => item.id === id, { read: true })
  await apiRequest<void>(`/me/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH', token: await token() })
}

/** `POST /me/notifications/read-all`. */
export async function markAllNotificationsRead(): Promise<void> {
  if (DATA_SOURCE !== 'http') return mockUpdate((item) => !item.archived, { read: true })
  await apiRequest<void>('/me/notifications/read-all', { method: 'POST', token: await token() })
}

/** `POST /me/notifications/{id}/archive`: pasa al histórico. */
export async function archiveNotification(id: string): Promise<void> {
  if (DATA_SOURCE !== 'http') return mockUpdate((item) => item.id === id, { archived: true, read: true })
  await apiRequest<void>(`/me/notifications/${encodeURIComponent(id)}/archive`, { method: 'POST', token: await token() })
}

/** `POST /me/notifications/archive-read`: «Limpiar» las leídas. */
export async function archiveReadNotifications(): Promise<void> {
  if (DATA_SOURCE !== 'http') return mockUpdate((item) => item.read && !item.archived, { archived: true })
  await apiRequest<void>('/me/notifications/archive-read', { method: 'POST', token: await token() })
}
