import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { cn } from '@/lib/utils'
import { getSalesService } from '../api/sales-service'
import type { SalesEvent, SalesEventKind } from '../api/types'
import { usePortal } from '../context'
import { useInView } from '../lib/motion'

const POLL_MS = 15_000
const LIMIT = 20

/** Tipo de evento como etiqueta de texto (no solo color): el acento lateral repite el tono semántico. */
const KIND: Record<SalesEventKind, { label: string; accent: string }> = {
  ORDER: { label: 'Pedido', accent: 'border-ap-info' },
  PAYMENT: { label: 'Pago', accent: 'border-ap-success' },
  LOW_STOCK: { label: 'Stock', accent: 'border-ap-warning' },
}

const relative = new Intl.RelativeTimeFormat('es-CO', { numeric: 'auto' })
const clock = new Intl.DateTimeFormat('es-CO', { hour: '2-digit', minute: '2-digit' })

function ago(iso: string, now: number): string {
  const seconds = Math.round((new Date(iso).getTime() - now) / 1000)
  const abs = Math.abs(seconds)
  if (abs < 60) return 'ahora'
  if (abs < 3600) return relative.format(Math.round(seconds / 60), 'minute')
  if (abs < 86_400) return relative.format(Math.round(seconds / 3600), 'hour')
  return relative.format(Math.round(seconds / 86_400), 'day')
}

/**
 * Actividad reciente: últimos 20 eventos, como lista con divisores (sin animaciones de entrada ni
 * indicadores pulsantes, §10). Consulta al servidor cada 15 s; se pausa al pasar el puntero o enfocar la
 * lista, con la pestaña oculta o con el panel fuera de pantalla. Si una consulta falla, se conserva lo
 * mostrado y se indica la hora de la última actualización (§20.9, datos desactualizados).
 */
export function LiveActivity({ initial }: { initial: SalesEvent[] }) {
  const { storeId, can, branchId } = usePortal()
  const [rootRef, inView] = useInView<HTMLDivElement>()
  const [events, setEvents] = useState(initial.slice(0, LIMIT))
  const [paused, setPaused] = useState(false)
  const [now, setNow] = useState(() => Date.now())
  const [updatedAt, setUpdatedAt] = useState(() => Date.now())
  const [stale, setStale] = useState(false)
  const seen = useRef(new Set(initial.map((event) => event.id)))

  const running = inView && !paused

  useEffect(() => {
    if (!running) return
    let cancelled = false
    const timer = window.setInterval(async () => {
      if (document.hidden) return
      try {
        const next = (await (await getSalesService()).activity(storeId, branchId)).slice(0, LIMIT)
        if (cancelled) return
        const added = next.filter((event) => !seen.current.has(event.id))
        added.forEach((event) => seen.current.add(event.id))
        const at = Date.now()
        setNow(at)
        setUpdatedAt(at)
        setStale(false)
        if (added.length === 0) return
        setEvents(next)
      } catch {
        // Se conserva lo ya mostrado; el siguiente ciclo reintenta.
        if (!cancelled) setStale(true)
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
      <p className="flex flex-wrap items-center gap-x-2 text-xs text-ap-muted">
        <span aria-hidden className={cn('size-2 rounded-[2px]', stale ? 'bg-ap-warning' : running ? 'bg-ap-success' : 'bg-ap-steel')} />
        <span className={cn(stale && 'font-medium text-ap-warning')}>
          {stale ? 'No pudimos actualizar; reintentamos en 15 s' : running ? 'Se actualiza cada 15 s' : 'En pausa mientras la revisas'}
        </span>
        <span className="ap-ref">· Actualizado {clock.format(updatedAt)}</span>
      </p>
      {events.length === 0 ? (
        <p className="border-l-2 border-ap-line-strong py-1 pl-3 text-sm text-ap-muted">Aún no hay actividad. Cuando entre un pedido o un pago, lo verás aquí.</p>
      ) : (
        <ol role="log" aria-label="Actividad reciente" className="flex max-h-[28rem] flex-col divide-y divide-ap-line overflow-y-auto border-y border-ap-line">
          {events.map((event) => {
            const kind = KIND[event.kind]
            const body = (
              <>
                <span className={cn('ap-index w-14 shrink-0 border-l-2 pl-2 text-ap-ink', kind.accent)}>{kind.label}</span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate text-sm font-semibold text-ap-ink">{event.title}</span>
                  <span className="block truncate text-xs text-ap-muted">{event.detail}</span>
                </span>
                <time dateTime={event.at} className="ap-ref shrink-0 text-xs text-ap-muted">
                  {ago(event.at, now)}
                </time>
              </>
            )
            const permitted = event.to && (event.kind === 'LOW_STOCK' ? can('PRODUCT_READ') : can('ORDER_READ'))
            const row = 'flex min-h-11 items-center gap-3 py-2 pr-1'
            return (
              <li key={event.id}>
                {permitted ? (
                  <Link to={`${base}/${event.to}`} className={cn(row, 'transition-colors duration-150 hover:bg-ap-paper')}>
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
