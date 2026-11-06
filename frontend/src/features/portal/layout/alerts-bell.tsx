import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { BellIcon, BellOffIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle, SheetTrigger } from '@/components/ui/sheet'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getAlertService } from '../api/alert-service'
import { AlertCard } from '../components/alert-card'
import { ListSkeleton } from '../components/list-skeleton'
import { QueryBoundary } from '../components/query-boundary'
import { usePortal } from '../context'
import { useAlertActions } from '../lib/use-alert-actions'

/** Cada cuánto se vuelve a pedir el contador mientras la pestaña está visible. */
const REFRESH_MS = 3 * 60_000

function AlertsPanel({ onNavigate }: { onNavigate: () => void }) {
  const { storeId } = usePortal()
  const alerts = useAsync(`portal:${storeId}:alerts:list:open`, async () => (await getAlertService()).list(storeId, 'open'))
  const actions = useAlertActions()
  return (
    <div className="flex min-h-0 flex-1 flex-col gap-4 overflow-y-auto px-4 pb-4">
      <QueryBoundary result={alerts} what="las alertas" skeleton={<ListSkeleton rows={3} label="Cargando las alertas" />}>
        {(rows) =>
          rows.length === 0 ? (
            <div className="flex flex-col items-start gap-2 rounded-lg bg-concrete p-5">
              <BellOffIcon className="size-6 text-muted-foreground" aria-hidden />
              <p className="font-semibold">Todo en orden</p>
              <p className="text-sm text-muted-foreground">No tienes alertas abiertas. Te avisaremos aquí cuando algo necesite tu atención.</p>
            </div>
          ) : (
            <ul className="flex flex-col gap-3">
              {rows.slice(0, 8).map((alert) => (
                <li key={alert.id}>
                  <AlertCard alert={alert} actions={actions} onNavigate={onNavigate} compact />
                </li>
              ))}
            </ul>
          )
        }
      </QueryBoundary>
      <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`/portal/${encodeURIComponent(storeId)}/alertas`} onClick={onNavigate} />}>
        Ver todas las alertas
      </Button>
    </div>
  )
}

/**
 * Campana del encabezado: contador de alertas sin leer (`/alerts/summary`, se refresca al abrir y cada pocos minutos)
 * y panel con las alertas abiertas. Solo con `STORE_READ`; el servidor autoriza cada petición.
 */
export function AlertsBell() {
  const { storeId, can, permissionsReady } = usePortal()
  const allowed = permissionsReady && can('STORE_READ')
  const [open, setOpen] = useState(false)
  const summaryKey = `portal:${storeId}:alerts:summary`
  const summary = useAsync(`${summaryKey}:${allowed ? 'on' : 'off'}`, async () => (allowed ? (await getAlertService()).summary(storeId) : { unread: 0, critical: 0 }))

  useEffect(() => {
    if (!allowed) return
    const timer = window.setInterval(() => {
      if (document.visibilityState === 'visible') invalidateAsync(summaryKey)
    }, REFRESH_MS)
    return () => window.clearInterval(timer)
  }, [allowed, summaryKey])

  if (!allowed) return null
  const unread = summary.data?.unread ?? 0
  const critical = summary.data?.critical ?? 0
  const label = unread > 0 ? `Alertas: ${unread} sin leer${critical > 0 ? `, ${critical} crítica${critical === 1 ? '' : 's'}` : ''}` : 'Alertas: sin novedades'

  return (
    <Sheet
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        // Al abrir se pide el contador y la lista de nuevo.
        if (next) invalidateAsync(`portal:${storeId}:alerts`)
      }}
    >
      <SheetTrigger
        render={
          <Button
            variant="ghost"
            className="relative size-11 shrink-0 text-white hover:bg-header-hover hover:text-white aria-expanded:bg-header-hover aria-expanded:text-white"
            aria-label={label}
          />
        }
      >
        <BellIcon className="size-5" aria-hidden />
        {unread > 0 ? (
          <span
            aria-hidden
            className={`absolute top-1 right-1 grid min-w-5 place-items-center rounded-full px-1 text-[0.7rem] leading-5 font-bold tabular-nums ${critical > 0 ? 'bg-destructive text-white' : 'bg-white text-foreground'}`}
          >
            {unread > 99 ? '99+' : unread}
          </span>
        ) : null}
      </SheetTrigger>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-md">
        <SheetHeader className="p-4 pr-16">
          <SheetTitle className="text-lg font-extrabold tracking-tight">Alertas</SheetTitle>
          <SheetDescription>Lo que necesita tu atención en el almacén.</SheetDescription>
        </SheetHeader>
        <AlertsPanel onNavigate={() => setOpen(false)} />
      </SheetContent>
    </Sheet>
  )
}
