import { useId } from 'react'
import { cn } from '@/lib/utils'
import { formatDate } from '@/features/portal/lib/labels'
import type { StoreApplication } from '../api/types'
import { DAY_MS } from '../lib/labels'
import { dayOffset, spanDays, type DayRange } from '../lib/timeline'

interface TimelineScrubberProps {
  applications: readonly StoreApplication[]
  range: DayRange
  onRangeChange: (range: DayRange) => void
  selectedId?: string
  onSelect: (applicationId: string) => void
}

/**
 * Filtro por fecha de envío: un punto por solicitud sobre una línea de tiempo y dos deslizadores nativos
 * (Desde / Hasta, con teclado y lector de pantalla) que acotan la tabla. Los puntos dentro del rango van en
 * rojo y los de fuera en blanco con borde (forma además de color); cada punto abre su solicitud.
 * La misma información está en la tabla (columna «Enviada»), que es la alternativa accesible.
 */
export function TimelineScrubber({ applications, range, onRangeChange, selectedId, onSelect }: TimelineScrubberProps) {
  const id = useId()
  const { start, days } = spanDays(applications)
  const lo = Math.min(range.from, days)
  const hi = Math.min(Math.max(range.to, lo), days)
  const position = (offset: number) => (days === 0 ? 50 : (offset / days) * 100)
  const dateAt = (offset: number) => formatDate(new Date(start + offset * DAY_MS).toISOString())
  const inRange = applications.filter((item) => {
    const offset = dayOffset(item.createdAt, start)
    return offset >= lo && offset <= hi
  }).length

  return (
    <fieldset className="flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4">
      <legend className="sr-only">Fecha de envío</legend>
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <div className="flex flex-col gap-1">
          <p className="text-base font-semibold text-ap-ink" aria-hidden>
            ¿Cuándo llegaron las solicitudes?
          </p>
          <p className="ap-ref text-ap-muted">
            {dateAt(0)} – {dateAt(days)} · una marca por solicitud
          </p>
        </div>
        <p className="text-sm text-ap-muted" aria-live="polite">
          <span className="ap-num font-semibold text-ap-ink">{inRange}</span> de {applications.length} en el rango
        </p>
      </div>

      <div className="relative h-11 px-2">
        <div aria-hidden className="absolute inset-x-2 top-1/2 h-px -translate-y-1/2 bg-ap-line-strong" />
        {days > 0 ? (
          <div
            aria-hidden
            className="absolute top-1/2 h-0.5 -translate-y-1/2 bg-ap-red"
            style={{ left: `calc(0.5rem + (100% - 1rem) * ${position(lo) / 100})`, width: `calc((100% - 1rem) * ${(position(hi) - position(lo)) / 100})` }}
          />
        ) : null}
        <ol className="absolute inset-x-2 inset-y-0 m-0 list-none p-0">
          {applications.map((item) => {
            const offset = dayOffset(item.createdAt, start)
            const active = offset >= lo && offset <= hi
            const selected = item.applicationId === selectedId
            return (
              <li key={item.applicationId} className="absolute top-1/2" style={{ left: `${position(offset)}%` }}>
                <button
                  type="button"
                  onClick={() => onSelect(item.applicationId)}
                  aria-label={`Abrir la solicitud de ${item.businessName}, enviada el ${formatDate(item.createdAt)}${active ? '' : ' (fuera del rango)'}`}
                  aria-current={selected || undefined}
                  title={`${item.businessName} · ${formatDate(item.createdAt)}`}
                  className={cn(
                    'relative block size-3 -translate-x-1/2 -translate-y-1/2 rounded-full border-2 transition-colors duration-150 after:absolute after:-inset-3 after:content-[""] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-ap-red',
                    active ? 'border-ap-red bg-ap-red' : 'border-ap-steel bg-ap-white',
                    selected && 'size-4 border-ap-graphite',
                  )}
                />
              </li>
            )
          })}
        </ol>
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        {(
          [
            ['desde', 'Desde', lo, 0, hi],
            ['hasta', 'Hasta', hi, lo, days],
          ] as const
        ).map(([key, label, value, min, max]) => (
          <div key={key} className="flex flex-col gap-1">
            <label htmlFor={`${id}-${key}`} className="flex items-baseline justify-between gap-2 text-sm">
              <span className="font-semibold text-ap-ink">{label}</span>
              <span className="ap-ref text-ap-muted">{dateAt(value)}</span>
            </label>
            <input
              id={`${id}-${key}`}
              type="range"
              className="h-11 w-full accent-ap-red disabled:opacity-50"
              min={min}
              max={max}
              step={1}
              value={value}
              disabled={days === 0}
              aria-valuetext={dateAt(value)}
              onChange={(event) => {
                const next = Number(event.target.value)
                onRangeChange(key === 'desde' ? { from: next, to: hi } : { from: lo, to: next })
              }}
            />
          </div>
        ))}
      </div>
      {days === 0 ? <p className="text-xs text-ap-muted">Todas las solicitudes llegaron el mismo día: no hay rango que acotar.</p> : null}
    </fieldset>
  )
}
