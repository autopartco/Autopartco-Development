import { Link } from 'react-router'
import { BellIcon } from 'lucide-react'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuLabel, DropdownMenuGroup, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useSession } from '@/features/auth/hooks'
import { relativeDate } from '@/features/marketplace/lib/review-labels'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { listNotifications, markNotificationRead, type AppNotification } from './api'
import { internalLink } from './link'

const KEY_PREFIX = 'portal:notifications:'
const MAX_SHOWN = 15

function markRead(notification: AppNotification) {
  if (notification.read) return
  void markNotificationRead(notification.id)
    .catch(() => undefined)
    .finally(() => invalidateAsync(KEY_PREFIX))
}

function NotificationBody({ notification }: { notification: AppNotification }) {
  return (
    <span className="flex min-w-0 flex-1 flex-col gap-0.5 py-1.5">
      <span className={cn('text-sm break-words text-ap-ink', notification.read ? 'font-medium' : 'font-semibold')}>
        {notification.read ? null : <span className="sr-only">No leída: </span>}
        {notification.title}
      </span>
      <span className="text-sm font-normal break-words text-ap-muted">{notification.message}</span>
      <time dateTime={notification.createdAt} className="text-xs font-normal text-ap-muted">
        {relativeDate(notification.createdAt)}
      </time>
    </span>
  )
}

/**
 * Campana de notificaciones de la cuenta. Cada notificación con `link` navega a esa ruta interna con el router y
 * se marca como leída. Sin sesión no se dibuja; si la consulta falla, la campana sigue sin contador.
 */
export function NotificationsMenu({ className }: { className?: string }) {
  const session = useSession()
  const result = useAsync(`${KEY_PREFIX}${session?.user.email ?? 'none'}`, async () => (session ? (await listNotifications('active', 1, MAX_SHOWN)).items : []))
  if (!session) return null

  const items = result.data ?? []
  const unread = items.filter((item) => !item.read).length
  return (
    <DropdownMenu onOpenChange={(open) => open && invalidateAsync(KEY_PREFIX)}>
      <DropdownMenuTrigger
        aria-label={unread > 0 ? `Notificaciones, ${unread} sin leer` : 'Notificaciones'}
        className={cn(
          'relative inline-flex size-11 items-center justify-center rounded-md text-white transition-colors hover:bg-sidebar-accent hover:text-white data-popup-open:bg-sidebar-accent lg:size-10',
          className,
        )}
      >
        <BellIcon className="size-5" aria-hidden />
        {unread > 0 ? (
          <span aria-hidden className="absolute top-1 right-1 inline-flex min-w-4 items-center justify-center rounded-full bg-ap-red-on-dark px-1 text-[0.625rem] leading-4 font-semibold text-ap-graphite tabular-nums">
            {unread > 9 ? '9+' : unread}
          </span>
        ) : null}
      </DropdownMenuTrigger>
      <DropdownMenuContent className="w-80 max-w-[calc(100vw-1.5rem)]">
        <DropdownMenuGroup>
          <DropdownMenuLabel>Notificaciones</DropdownMenuLabel>
          {result.status === 'error' ? (
            <p role="alert" className="px-3 py-3 text-sm text-ap-ink">
              No pudimos cargar tus notificaciones. Cierra el menú y vuelve a abrirlo.
            </p>
          ) : items.length === 0 ? (
            <p role="status" className="px-3 py-3 text-sm text-ap-muted">
              {result.data ? 'No tienes notificaciones. Aquí te avisamos cuando respondan tus preguntas o llegue algo nuevo.' : 'Cargando…'}
            </p>
          ) : (
            items.slice(0, MAX_SHOWN).map((notification) => {
              const to = internalLink(notification.link)
              return to ? (
                <DropdownMenuItem key={notification.id} className="items-start" render={<Link to={to} />} onClick={() => markRead(notification)}>
                  <NotificationBody notification={notification} />
                </DropdownMenuItem>
              ) : (
                <DropdownMenuItem key={notification.id} className="items-start" onClick={() => markRead(notification)}>
                  <NotificationBody notification={notification} />
                </DropdownMenuItem>
              )
            })
          )}
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
