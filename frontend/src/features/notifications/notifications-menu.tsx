import { Link } from 'react-router'
import { BellIcon, CheckCheckIcon, ChevronRightIcon, ArchiveIcon } from 'lucide-react'
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { useSession } from '@/features/auth/hooks'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { archiveReadNotifications, listNotifications, markAllNotificationsRead, markNotificationRead, unreadNotificationCount, type AppNotification } from './api'
import { NOTIFICATIONS_KEY, notificationTime, refreshNotifications } from './lib'
import { internalLink } from './link'

/** Cuántas caben en el menú; el resto está en «Ver todas». */
const MENU_SIZE = 6

function run(action: () => Promise<void>) {
  void action()
    .catch(() => undefined)
    .finally(refreshNotifications)
}

function markRead(notification: AppNotification) {
  if (!notification.read) run(() => markNotificationRead(notification.id))
}

/** Fila de notificación: compartida por el menú y la página de la bandeja. */
export function NotificationRow({ notification, linked }: { notification: AppNotification; linked: boolean }) {
  return (
    <span className="flex w-full min-w-0 items-start gap-2.5 py-1">
      <span aria-hidden className={cn('mt-1.5 size-2 shrink-0 rounded-full', notification.read ? 'bg-transparent' : 'bg-ap-red')} />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <span className={cn('text-sm break-words text-ap-ink', notification.read ? 'font-medium' : 'font-semibold')}>
          {notification.read ? null : <span className="sr-only">No leída: </span>}
          {notification.title}
        </span>
        <span className="line-clamp-2 text-sm font-normal break-words text-ap-muted">{notification.message}</span>
        <time dateTime={notification.createdAt} className="font-mono text-xs font-normal text-ap-muted tabular-nums">
          {notificationTime(notification.createdAt)}
        </time>
      </span>
      {linked ? <ChevronRightIcon aria-hidden className="mt-1 size-4 shrink-0 text-ap-muted" /> : null}
    </span>
  )
}

/**
 * Campana de la cuenta: las {@link MENU_SIZE} más recientes de la bandeja, el contador real de no leídas y las
 * acciones «Marcar todo como leído» y «Limpiar leídas» (pasan al histórico). Sin sesión no se dibuja.
 */
export function NotificationsMenu({ className }: { className?: string }) {
  const session = useSession()
  const owner = session?.user.email ?? 'none'
  const list = useAsync(`${NOTIFICATIONS_KEY}menu:${owner}`, async () => (session ? listNotifications('active', 1, MENU_SIZE) : null))
  const count = useAsync(`${NOTIFICATIONS_KEY}count:${owner}`, async () => (session ? unreadNotificationCount() : 0))
  if (!session) return null

  const page = list.data
  const items = page?.items ?? []
  const unread = count.data ?? items.filter((item) => !item.read).length
  const hasRead = items.some((item) => item.read)
  return (
    <DropdownMenu onOpenChange={(open) => open && refreshNotifications()}>
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
      <DropdownMenuContent className="w-[22rem] max-w-[calc(100vw-1.5rem)] p-0">
        <div className="flex items-baseline justify-between gap-3 border-b border-ap-line px-3 py-2.5">
          <span className="text-sm font-semibold text-ap-ink">Notificaciones</span>
          {unread > 0 ? <span className="font-mono text-xs text-ap-muted tabular-nums">{unread} sin leer</span> : null}
        </div>
        <DropdownMenuGroup className="max-h-[min(24rem,55vh)] overflow-y-auto p-1">
          {list.status === 'error' ? (
            <p role="alert" className="px-3 py-3 text-sm text-ap-ink">
              No pudimos cargar tus notificaciones. Cierra el menú y vuelve a abrirlo.
            </p>
          ) : !page ? (
            <p role="status" className="px-3 py-3 text-sm text-ap-muted">
              Cargando…
            </p>
          ) : items.length === 0 ? (
            <p role="status" className="px-3 py-4 text-sm text-ap-muted">
              Estás al día. Aquí te avisamos de tus pedidos, preguntas y respuestas.
            </p>
          ) : (
            items.map((notification) => {
              const to = internalLink(notification.link)
              return to ? (
                <DropdownMenuItem key={notification.id} className="items-start" render={<Link to={to} />} onClick={() => markRead(notification)}>
                  <NotificationRow notification={notification} linked />
                </DropdownMenuItem>
              ) : (
                // Sin destino: el clic solo la marca como leída; no se presenta como enlace.
                <DropdownMenuItem key={notification.id} className="cursor-default items-start" closeOnClick={false} onClick={() => markRead(notification)}>
                  <NotificationRow notification={notification} linked={false} />
                </DropdownMenuItem>
              )
            })
          )}
        </DropdownMenuGroup>
        <DropdownMenuSeparator className="my-0" />
        <div className="flex flex-col p-1">
          {unread > 0 ? (
            <DropdownMenuItem closeOnClick={false} onClick={() => run(markAllNotificationsRead)}>
              <CheckCheckIcon aria-hidden />
              Marcar todo como leído
            </DropdownMenuItem>
          ) : null}
          {hasRead ? (
            <DropdownMenuItem closeOnClick={false} onClick={() => run(archiveReadNotifications)}>
              <ArchiveIcon aria-hidden />
              Limpiar leídas
            </DropdownMenuItem>
          ) : null}
          <DropdownMenuItem className="font-semibold" render={<Link to="/cuenta/notificaciones" />}>
            Ver todas e historial
            {page && page.total > items.length ? <span className="ml-auto font-mono text-xs font-normal text-ap-muted tabular-nums">{page.total}</span> : null}
          </DropdownMenuItem>
        </div>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
