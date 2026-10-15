import { useId, useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { cn } from '@/lib/utils'
import { useRevealed } from '../lib/motion'

export interface SparkPoint {
  label: string
  value: number
}

interface SparklineProps {
  points: SparkPoint[]
  formatValue: (value: number) => string
  /** Qué mide la serie ("Ventas diarias"): nombra el gráfico para lectores de pantalla. */
  label: string
  className?: string
}

const W = 300
const H = 80
const PAD = 5

/**
 * Minigráfico de línea en SVG con tooltip por punto. Con puntero: pasa el cursor; con teclado:
 * enfoca el gráfico y usa las flechas (Inicio/Fin saltan a los extremos). La línea se dibuja
 * con un `scaleX` (solo transform) una vez al entrar en pantalla.
 */
export function Sparkline({ points, formatValue, label, className }: SparklineProps) {
  const revealed = useRevealed()
  const gradientId = useId()
  const wrapper = useRef<HTMLDivElement>(null)
  const [active, setActive] = useState<number | null>(null)
  const [announce, setAnnounce] = useState('')

  const count = points.length
  const values = points.map((point) => point.value)
  const min = Math.min(...values, 0)
  const max = Math.max(...values, 1)
  const span = max - min || 1
  const xOf = (index: number) => (count <= 1 ? W / 2 : (index / (count - 1)) * W)
  const yOf = (value: number) => H - PAD - ((value - min) / span) * (H - PAD * 2)
  const line = points.map((point, index) => `${index === 0 ? 'M' : 'L'}${xOf(index).toFixed(1)} ${yOf(point.value).toFixed(1)}`).join(' ')
  const area = `${line} L${xOf(count - 1).toFixed(1)} ${H} L${xOf(0).toFixed(1)} ${H} Z`

  function move(index: number, announceIt: boolean) {
    const next = Math.min(count - 1, Math.max(0, index))
    setActive(next)
    const point = points[next]
    if (announceIt && point) setAnnounce(`${point.label}: ${formatValue(point.value)}`)
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    const rect = event.currentTarget.getBoundingClientRect()
    if (rect.width === 0 || count === 0) return
    move(Math.round(((event.clientX - rect.left) / rect.width) * (count - 1)), false)
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const current = active ?? count - 1
    const targets: Record<string, number> = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: 0, End: count - 1 }
    const target = targets[event.key]
    if (target === undefined) return
    event.preventDefault()
    move(target, true)
  }

  const point = active === null ? undefined : points[active]
  const left = active === null ? 0 : (xOf(active) / W) * 100
  const top = point ? (yOf(point.value) / H) * 100 : 0
  const tooltipShift = left < 18 ? '0%' : left > 82 ? '-100%' : '-50%'

  if (count === 0) return null

  return (
    <div
      ref={wrapper}
      role="group"
      tabIndex={0}
      aria-label={`${label}. Usa las flechas izquierda y derecha para recorrer los puntos.`}
      onPointerMove={onPointerMove}
      onPointerLeave={() => {
        if (document.activeElement !== wrapper.current) setActive(null)
      }}
      onKeyDown={onKeyDown}
      onFocus={() => active === null && move(count - 1, true)}
      onBlur={() => {
        setActive(null)
        setAnnounce('')
      }}
      className={cn('relative mt-5 touch-pan-y rounded-md', className)}
    >
      <div
        aria-hidden
        className={cn(
          'origin-left scale-x-0 transition-transform duration-1000 ease-out-thread motion-reduce:scale-x-100 motion-reduce:transition-none',
          revealed && 'scale-x-100',
        )}
      >
        <svg viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="block h-20 w-full overflow-visible">
          <defs>
            <linearGradient id={gradientId} x1="0" x2="0" y1="0" y2="1">
              <stop offset="0%" stopColor="var(--color-thread)" stopOpacity="0.22" />
              <stop offset="100%" stopColor="var(--color-thread)" stopOpacity="0" />
            </linearGradient>
          </defs>
          <path d={area} fill={`url(#${gradientId})`} />
          <path d={line} fill="none" stroke="var(--color-thread)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
        </svg>
      </div>
      {point ? (
        <>
          <span
            aria-hidden
            className="pointer-events-none absolute size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full bg-primary ring-2 ring-card"
            style={{ left: `${left}%`, top: `${top}%` }}
          />
          <span
            aria-hidden
            className="pointer-events-none absolute bottom-full z-10 mb-2 rounded-md bg-asphalt px-2 py-1 text-xs whitespace-nowrap text-white shadow-md"
            style={{ left: `${left}%`, transform: `translateX(${tooltipShift})` }}
          >
            <span className="text-asphalt-muted">{point.label}</span> <strong className="font-semibold tabular-nums">{formatValue(point.value)}</strong>
          </span>
        </>
      ) : null}
      <span role="status" className="sr-only">
        {announce}
      </span>
    </div>
  )
}
