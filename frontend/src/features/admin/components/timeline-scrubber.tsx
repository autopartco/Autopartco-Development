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
 * Línea temporal de solicitudes por fecha: un punto por solicitud y dos deslizadores
 * (Desde / Hasta) que acotan el rango de la tabla. Los deslizadores son controles nativos
 * (teclado y lectores de pantalla); los puntos son botones que abren la solicitud.
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
    <div className="flex flex-col gap-3 rounded-xl bg-white/5 p-4 ring-1 ring-white/10">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <h2 className="label-mono text-asphalt-muted">Línea de tiempo</h2>
        <p className="text-sm" aria-live="polite">
          {inRange} de {applications.length} {applications.length === 1 ? 'solicitud' : 'solicitudes'} en el rango
        </p>
      </div>

      <div className="relative h-10 px-2">
        <div aria-hidden className="absolute inset-x-2 top-1/2 h-0.5 -translate-y-1/2 rounded-full bg-white/20" />
        {days > 0 ? (
          <div
            aria-hidden
            className="absolute top-1/2 h-0.5 -translate-y-1/2 rounded-full bg-thread-on-asphalt"
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
                  aria-label={`${item.businessName}, ${formatDate(item.createdAt)}`}
                  aria-current={selected || undefined}
                  title={`${item.businessName} · ${formatDate(item.createdAt)}`}
                  className={cn(
                    'timeline-dot relative -translate-x-1/2 -translate-y-1/2 rounded-full after:absolute after:-inset-2 after:content-[""] hover:scale-125',
                    'size-3.5 ring-2 ring-asphalt',
                    active ? 'bg-thread-on-asphalt' : 'bg-white/35',
                    selected && 'scale-125 bg-white ring-thread-on-asphalt',
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
              <span className="font-semibold">{label}</span>
              <span className="text-asphalt-muted">{dateAt(value)}</span>
            </label>
            <input
              id={`${id}-${key}`}
              type="range"
              className="timeline-range h-6 w-full"
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
    </div>
  )
}
