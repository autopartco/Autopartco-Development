import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { AlertTriangleIcon, CreditCardIcon, ShoppingBagIcon, type LucideProps } from 'lucide-react'
import type { ComponentType } from 'react'
import { cn } from '@/lib/utils'
import { getSalesService } from '../api/sales-service'
import type { SalesEvent, SalesEventKind } from '../api/types'
import { usePortal } from '../context'
import { useInView, useRevealed } from '../lib/motion'

const POLL_MS = 15_000
const LIMIT = 20

const KIND: Record<SalesEventKind, { icon: ComponentType<LucideProps>; label: string; tone: string }> = {
  ORDER: { icon: ShoppingBagIcon, label: 'Pedido', tone: 'bg-recommended-soft text-recommended' },
  PAYMENT: { icon: CreditCardIcon, label: 'Pago', tone: 'bg-stock/10 text-stock' },
  LOW_STOCK: { icon: AlertTriangleIcon, label: 'Stock', tone: 'bg-stock-low/10 text-stock-low' },
}

const relative = new Intl.RelativeTimeFormat('es-CO', { numeric: 'auto' })

function ago(iso: string, now: number): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) return 'ahora'
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute')
  if (abs < 86_400) return relative.format(Math.round(seconds / 3600), 'hour')
  return relative.format(Math.round(seconds / 86_400), 'day')
}

/**
 * Actividad reciente: últimos 20 eventos. Consulta al servidor cada 15 s y los eventos nuevos
 * se deslizan desde arriba. La consulta se pausa al pasar el puntero o enfocar la lista, con la
 * pestaña oculta o con el panel fuera de pantalla.
 */
export function LiveActivity({ initial }: { initial: SalesEvent[] }) {
  const { storeId, can } = usePortal()
  const revealed = useRevealed()
  const [rootRef, inView] = useInView<HTMLDivElement>()
  const [events, setEvents] = useState(initial.slice(0, LIMIT))
  const [fresh, setFresh] = useState<ReadonlySet<string>>(new Set())
  const [paused, setPaused] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const seen = useRef(new Set(initial.map((event) => event.id)))

  const running = inView && !paused

  useEffect(() => {
    if (!running) return
    let cancelled = false
    const timer = window.setInterval(async () => {
      if (document.hidden) return
      try {
        const next = (await (await getSalesService()).activity(storeId)).slice(0, LIMIT)
        if (cancelled) return
        const added = next.filter((event) => !seen.current.has(event.id))
        added.forEach((event) => seen.current.add(event.id))
        setNow(Date.now())
        if (added.length === 0) return
        setFresh(new Set(added.map((event) => event.id)))
        setEvents(next)
      } catch {
        // Se conserva lo ya mostrado; el siguiente ciclo reintenta.
      }
    }, POLL_MS)
    return () => {
      cancelled = true
      window.clearInterval(timer)
    }
  }, [running, storeId])

  const base = `/portal/${encodeURIComponent(storeId)}`

  return (
    <div
      ref={rootRef}
      onPointerEnter={() => setPaused(true)}
      onPointerLeave={() => setPaused(false)}
      onFocus={() => setPaused(true)}
      onBlur={() => setPaused(false)}
      className="flex min-h-0 flex-1 flex-col gap-2"
    >
      <p className="flex items-center gap-2 text-xs text-muted-foreground">
        <span aria-hidden className={cn('size-2 rounded-full', running ? 'bg-stock motion-safe:animate-pulse' : 'bg-muted-foreground')} />
        {running ? 'En vivo · se actualiza cada 15 s' : 'En pausa mientras la revisas'}
      </p>
      {events.length === 0 ? (
        <p className="rounded-lg bg-muted p-4 text-sm text-muted-foreground">Aún no hay actividad. Cuando entre un pedido o un pago, lo verás aquí.</p>
      ) : (
        <ol role="log" aria-label="Actividad en vivo" className="flex max-h-[28rem] flex-col gap-1.5 overflow-y-auto pr-1">
          {events.map((event, index) => {
            const kind = KIND[event.kind]
            const Icon = kind.icon
            const body = (
              <>
                <span aria-hidden className={cn('grid size-8 shrink-0 place-items-center rounded-full', kind.tone)}>
                  <Icon className="size-4" />
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold">
                    <span className="sr-only">{kind.label}: </span>
                    {event.title}
                  </span>
                  <span className="block truncate text-xs text-muted-foreground">{event.detail}</span>
                </span>
                <time dateTime={event.at} className="shrink-0 text-xs text-muted-foreground">
                  {ago(event.at, now)}
                </time>
              </>
            )
            const permitted = event.to && (event.kind === 'LOW_STOCK' ? can('PRODUCT_READ') : can('ORDER_READ'))
            const row = 'flex items-center gap-3 rounded-lg p-2 transition-colors hover:bg-muted'
            return (
              <li
                key={event.id}
                style={{ transitionDelay: `${Math.min(index, 8) * 75}ms` }}
                className={cn(
                  fresh.has(event.id)
                    ? 'motion-safe:animate-in motion-safe:fade-in-0 motion-safe:slide-in-from-top-3 motion-safe:duration-500 motion-safe:ease-out-thread'
                    : 'translate-y-2 opacity-0 transition-[opacity,transform] duration-500 ease-out-thread motion-reduce:translate-y-0 motion-reduce:opacity-100 motion-reduce:transition-none',
                  !fresh.has(event.id) && revealed && 'translate-y-0 opacity-100',
                )}
              >
                {permitted ? (
                  <Link to={`${base}/${event.to}`} className={row}>
                    {body}
                  </Link>
                ) : (
                  <div className={row}>{body}</div>
                )}
              </li>
            )
          })}
        </ol>
      )}
    </div>
  )
}
