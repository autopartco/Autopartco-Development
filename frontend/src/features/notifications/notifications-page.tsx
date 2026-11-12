import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ArchiveIcon, BellOffIcon, CheckCheckIcon, ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { SEGMENT_GROUP_CLASS, segmentClass } from '@/features/portal/components/segmented'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import {
  archiveNotification,
  archiveReadNotifications,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
  type AppNotification,
  type NotificationStatus,
} from './api'
import { NOTIFICATIONS_KEY, refreshNotifications } from './lib'
import { internalLink } from './link'
import { NotificationRow } from './notifications-menu'

const PAGE_SIZE = 20

const TABS: { status: NotificationStatus; label: string }[] = [
  { status: 'active', label: 'Bandeja' },
  { status: 'archived', label: 'Historial' },
]

function Row({ notification, onArchive }: { notification: AppNotification; onArchive?: () => void }) {
  const to = internalLink(notification.link)
  const markRead = () => {
    if (!notification.read) void markNotificationRead(notification.id).catch(() => undefined).finally(refreshNotifications)
  }
  return (
    <li className={cn('flex items-start gap-2 px-2 py-2 sm:px-3', !notification.read && 'bg-ap-paper')}>
      {to ? (
        <Link to={to} onClick={markRead} className="min-w-0 flex-1 rounded-md px-1 outline-none hover:bg-ap-paper focus-visible:ring-3 focus-visible:ring-ring/50">
          <NotificationRow notification={notification} linked />
        </Link>
      ) : (
        <div className="min-w-0 flex-1 px-1">
          <NotificationRow notification={notification} linked={false} />
        </div>
      )}
      {onArchive ? (
        <Button variant="ghost" size="icon" className="size-11 shrink-0 text-ap-muted sm:size-9" onClick={onArchive} aria-label={`Archivar: ${notification.title}`} title="Archivar">
          <ArchiveIcon aria-hidden />
        </Button>
      ) : null}
    </li>
  )
}

/** `/cuenta/notificaciones`: bandeja completa e historial (archivadas), con paginación y acciones en bloque. */
export default function NotificationsPage() {
  const [params, setParams] = useSearchParams()
  const status: NotificationStatus = params.get('ver') === 'historial' ? 'archived' : 'active'
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const [busy, setBusy] = useState(false)
  const result = useAsync(`${NOTIFICATIONS_KEY}page:${status}:${page}`, () => listNotifications(status, page, PAGE_SIZE))
  const data = result.data
  const active = status === 'active'

  // Tras archivar la última de una página, vuelve a la última página que aún tiene notificaciones.
  useEffect(() => {
    if (data && data.items.length === 0 && data.total > 0 && page > data.totalPages) go({ page: Math.max(1, data.totalPages) })
  })

  function go(next: { status?: NotificationStatus; page?: number }) {
    const nextStatus = next.status ?? status
    const nextPage = next.page ?? 1
    const search = new URLSearchParams()
    if (nextStatus === 'archived') search.set('ver', 'historial')
    if (nextPage > 1) search.set('pagina', String(nextPage))
    setParams(search)
  }

  async function bulk(action: () => Promise<void>, done: string) {
    setBusy(true)
    try {
      await action()
      toast.success(done)
    } catch {
      toast.error('No pudimos actualizar tus notificaciones. Inténtalo de nuevo.')
    } finally {
      setBusy(false)
      refreshNotifications()
    }
  }

  const items = data?.items ?? []
  const anyUnread = items.some((item) => !item.read)
  const anyRead = items.some((item) => item.read)

  return (
    <Container className="py-6 sm:py-8">
      <title>Notificaciones | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Notificaciones' }]}
        label="Tu cuenta"
        title="Notificaciones"
        description="Avisos de tus pedidos, preguntas y respuestas. Limpiar las pasa al historial; se guardan 180 días."
      />

      <div className="mb-4 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div className={SEGMENT_GROUP_CLASS} role="group" aria-label="Ver">
          {TABS.map((tab) => (
            <button key={tab.status} type="button" aria-pressed={tab.status === status} className={segmentClass(tab.status === status)} onClick={() => go({ status: tab.status })}>
              {tab.label}
            </button>
          ))}
        </div>
        {active && data && items.length > 0 ? (
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" disabled={busy || !anyUnread} onClick={() => void bulk(markAllNotificationsRead, 'Marcamos todo como leído.')}>
              <CheckCheckIcon aria-hidden />
              Marcar todo como leído
            </Button>
            <Button variant="outline" disabled={busy || !anyRead} onClick={() => void bulk(archiveReadNotifications, 'Las leídas pasaron al historial.')}>
              <ArchiveIcon aria-hidden />
              Limpiar leídas
            </Button>
          </div>
        ) : null}
      </div>

      {result.status === 'error' ? (
        <ErrorState title="No pudimos cargar tus notificaciones" onRetry={result.retry} />
      ) : !data ? (
        <div className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
          <span className="sr-only" role="status">
            Cargando tus notificaciones
          </span>
          {[0, 1, 2, 3].map((key) => (
            <div key={key} className="flex flex-col gap-2 px-3 py-3" aria-hidden>
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-4 w-2/3" />
            </div>
          ))}
        </div>
      ) : items.length === 0 && data.total === 0 ? (
        <EmptyState
          icon={<BellOffIcon />}
          title={active ? 'No tienes notificaciones' : 'El historial está vacío'}
          description={active ? 'Aquí te avisamos cuando cambie el estado de tus pedidos o respondan tus preguntas.' : 'Cuando limpies o archives notificaciones de la bandeja, aparecerán aquí.'}
        >
          {active ? null : (
            <Button variant="outline" onClick={() => go({ status: 'active' })}>
              Volver a la bandeja
            </Button>
          )}
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-4" aria-busy={result.isStale || busy || undefined}>
          <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line transition-opacity duration-150', result.isStale && 'opacity-60')}>
            {items.map((notification) => (
              <Row
                key={notification.id}
                notification={notification}
                onArchive={active ? () => void archiveNotification(notification.id).catch(() => toast.error('No pudimos archivarla.')).finally(refreshNotifications) : undefined}
              />
            ))}
          </ul>
          {data.totalPages > 1 ? (
            <nav aria-label="Paginación de notificaciones" className="flex items-center justify-between gap-3">
              <Button variant="outline" disabled={page <= 1} onClick={() => go({ page: page - 1 })}>
                <ChevronLeftIcon aria-hidden />
                Más recientes
              </Button>
              <p className="font-mono text-sm text-ap-muted tabular-nums" aria-live="polite">
                Página {data.page} de {data.totalPages}
              </p>
              <Button variant="outline" disabled={page >= data.totalPages} onClick={() => go({ page: page + 1 })}>
                Anteriores
                <ChevronRightIcon aria-hidden />
              </Button>
            </nav>
          ) : null}
        </div>
      )}
    </Container>
  )
}
