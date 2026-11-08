import { useMemo, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { formatCOP, pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { SalesHeatCell } from '../api/types'
import { usePortal } from '../context'
import { HEAT_LEVEL_CLASS } from '../lib/chart-palette'
import { ORDER_STATUS_LABEL, ORDER_STATUS_TONE } from '../lib/labels'
import { StatusBadge } from './status-badge'

const DAYS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']
const HOURS = Array.from({ length: 24 }, (_, hour) => hour)

const hourLabel = (hour: number) => `${String(hour).padStart(2, '0')}:00`
const slotLabel = (hour: number) => `${hourLabel(hour)} a ${String(hour).padStart(2, '0')}:59`

/** Pedidos que representa cada nivel de intensidad, para que la leyenda diga qué significa cada tono (§20.10). */
function levelRanges(max: number): { level: number; text: string }[] {
  const ranges = [{ level: 0, text: '0' }]
  for (let level = 1; level <= 4; level += 1) {
    const low = Math.floor(((level - 1) * max) / 4) + 1
    const high = Math.floor((level * max) / 4)
    if (low <= high) ranges.push({ level, text: low === high ? String(low) : `${low}–${high}` })
  }
  return ranges
}

/**
 * Mapa de calor matricial día × hora (§20.4 C): responde «¿a qué horas entran más pedidos?». Un solo
 * tono (rojo) cuya intensidad es el número de pedidos de la franja; la leyenda dice cuántos pedidos
 * representa cada tono y un resumen en texto nombra la franja más activa. Es una cuadrícula con una sola
 * celda en el orden de tabulación (flechas para moverse); Enter o clic abren el detalle de esa franja.
 */
export function OrderHeatmap({ cells }: { cells: SalesHeatCell[] }) {
  const { storeId, can } = usePortal()
  const grid = useRef<HTMLDivElement>(null)
  const [focus, setFocus] = useState({ row: 0, col: 8 })
  const [selected, setSelected] = useState<{ day: number; hour: number }>()

  const byKey = useMemo(() => new Map(cells.map((cell) => [`${cell.day}-${cell.hour}`, cell])), [cells])
  const max = useMemo(() => Math.max(1, ...cells.map((cell) => cell.orders.length)), [cells])
  const busiest = useMemo(
    () => cells.reduce<SalesHeatCell | undefined>((found, cell) => (cell.orders.length > (found?.orders.length ?? 0) ? cell : found), undefined),
    [cells],
  )
  const detail = selected ? byKey.get(`${selected.day}-${selected.hour}`) : undefined

  function level(count: number): number {
    return count === 0 ? 0 : Math.max(1, Math.ceil((count / max) * 4))
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const moves: Record<string, [number, number]> = {
      ArrowUp: [-1, 0],
      ArrowDown: [1, 0],
      ArrowLeft: [0, -1],
      ArrowRight: [0, 1],
    }
    let next = focus
    if (event.key in moves) {
      const [dr, dc] = moves[event.key] ?? [0, 0]
      next = { row: Math.min(6, Math.max(0, focus.row + dr)), col: Math.min(23, Math.max(0, focus.col + dc)) }
    } else if (event.key === 'Home') next = { row: focus.row, col: 0 }
    else if (event.key === 'End') next = { row: focus.row, col: 23 }
    else return
    event.preventDefault()
    setFocus(next)
    grid.current?.querySelector<HTMLElement>(`[data-row="${next.row}"][data-col="${next.col}"]`)?.focus()
  }

  const base = `/portal/${encodeURIComponent(storeId)}`

  return (
    <>
      {busiest ? (
        <p className="text-sm text-ap-ink">
          La franja con más pedidos es el{' '}
          <strong className="font-semibold">
            {DAYS[busiest.day]?.toLowerCase()} de {slotLabel(busiest.hour)}
          </strong>
          , con {pluralize(busiest.orders.length, 'pedido', 'pedidos')}.
        </p>
      ) : null}
      <p className="text-xs text-ap-muted sm:hidden">Desliza hacia los lados para ver todas las horas.</p>
      <div className="relative -mx-1 overflow-x-auto px-1 pb-2">
        <div ref={grid} role="grid" aria-label="Pedidos por día de la semana y hora" onKeyDown={onKeyDown} className="w-full min-w-[34rem]">
          <div role="row" className="grid grid-cols-[5rem_repeat(24,minmax(0,1fr))] gap-0.5 pb-1">
            <span role="columnheader" className="sr-only">
              Día
            </span>
            <span aria-hidden />
            {HOURS.map((hour) => (
              <span key={hour} role="columnheader" className="ap-ref text-center text-[0.625rem] text-ap-muted">
                <span className="sr-only">{hourLabel(hour)}</span>
                <span aria-hidden>{hour % 3 === 0 ? hour : ''}</span>
              </span>
            ))}
          </div>
          {DAYS.map((day, row) => (
            <div key={day} role="row" className="mb-0.5 grid grid-cols-[5rem_repeat(24,minmax(0,1fr))] items-center gap-0.5">
              <span role="rowheader" className="pr-2 text-xs text-ap-muted">
                {day}
              </span>
              {HOURS.map((hour) => {
                const count = byKey.get(`${row}-${hour}`)?.orders.length ?? 0
                return (
                  <span key={hour} role="gridcell">
                    <button
                      type="button"
                      data-row={row}
                      data-col={hour}
                      tabIndex={focus.row === row && focus.col === hour ? 0 : -1}
                      aria-label={`${day} ${hourLabel(hour)}: ${count} ${pluralize(count, 'pedido', 'pedidos')}`}
                      onFocus={() => setFocus({ row, col: hour })}
                      onClick={() => setSelected({ day: row, hour })}
                      className={cn('block h-5 w-full rounded-[2px] hover:outline-2 hover:outline-offset-1 hover:outline-ap-graphite sm:h-6', HEAT_LEVEL_CLASS[level(count)])}
                    />
                  </span>
                )
              })}
            </div>
          ))}
        </div>
      </div>
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-2 text-xs text-ap-muted">
        <p>Cada celda es una hora; el tono indica cuántos pedidos entraron en esa franja durante el rango.</p>
        <ul aria-label="Leyenda: pedidos por franja" className="flex flex-wrap items-center gap-x-3 gap-y-1">
          {levelRanges(max).map((range) => (
            <li key={range.level} className="inline-flex items-center gap-1.5">
              <span aria-hidden className={cn('size-3 rounded-[2px]', HEAT_LEVEL_CLASS[range.level])} />
              <span className="ap-ref">{range.text}</span>
            </li>
          ))}
        </ul>
      </div>

      <Dialog open={selected !== undefined} onOpenChange={(open) => !open && setSelected(undefined)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{selected ? `${DAYS[selected.day]}, ${slotLabel(selected.hour)}` : 'Franja'}</DialogTitle>
            <DialogDescription>
              {detail
                ? `${detail.orders.length} ${pluralize(detail.orders.length, 'pedido', 'pedidos')} en esta franja durante el rango elegido.`
                : 'No hubo pedidos en esta franja durante el rango elegido.'}
            </DialogDescription>
          </DialogHeader>
          {detail ? (
            <ul className="flex max-h-72 flex-col divide-y divide-ap-line overflow-y-auto">
              {detail.orders.map((order) => (
                <li key={order.saleId} className="flex flex-wrap items-center justify-between gap-2 py-2.5">
                  <div className="min-w-0">
                    {can('ORDER_READ') ? (
                      <Link to={`${base}/pedidos/${encodeURIComponent(order.saleId)}`} className="ap-ref inline-flex min-h-11 items-center font-semibold text-ap-ink underline-offset-4 hover:underline">
                        Pedido #{order.orderNumber}
                      </Link>
                    ) : (
                      <span className="ap-ref font-semibold text-ap-ink">Pedido #{order.orderNumber}</span>
                    )}
                    <p className="truncate text-xs text-ap-muted">{order.customerName ?? 'Comprador'}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <StatusBadge tone={ORDER_STATUS_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</StatusBadge>
                    <span className="ap-price text-sm text-ap-ink">{formatCOP(order.total)}</span>
                  </div>
                </li>
              ))}
            </ul>
          ) : null}
        </DialogContent>
      </Dialog>
    </>
  )
}
