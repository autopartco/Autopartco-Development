import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { cn } from '@/lib/utils'
import { CHART_COLOR } from '../lib/chart-palette'

export interface SparkPoint {
  label: string
  value: number
}

interface SparklineProps {
  points: SparkPoint[]
  formatValue: (value: number) => string
  /** Qué mide la serie ("Ventas diarias"): nombra el gráfico para lectores de pantalla. */
  label: string
  /** `primary` (rojo) para la métrica principal del panel; `secondary` (grafito) para las demás. */
  tone?: 'primary' | 'secondary'
  className?: string
}

const W = 300
const H = 80
const PAD = 5

/**
 * Minigráfico de línea (§20.4 A) con tooltip por punto. La escala parte de 0 (sin truncar) y se
 * rotulan el primer y el último día; sin relleno degradado ni animación de entrada. Con puntero: pasa
 * el cursor; con teclado: enfoca el gráfico y usa las flechas (Inicio/Fin saltan a los extremos).
 */
export function Sparkline({ points, formatValue, label, tone = 'primary', className }: SparklineProps) {
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
  const color = tone === 'primary' ? CHART_COLOR.primary : CHART_COLOR.secondary

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
    <div className={cn('mt-5 flex flex-col gap-1', className)}>
      <div
        ref={wrapper}
        role="group"
        tabIndex={0}
        aria-label={`${label}, del ${points[0]?.label} al ${points[count - 1]?.label}. Usa las flechas izquierda y derecha para recorrer los puntos.`}
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
        className="relative touch-pan-y rounded-sm"
      >
        <svg aria-hidden viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="block h-20 w-full overflow-visible">
          <line x1="0" x2={W} y1={yOf(0)} y2={yOf(0)} stroke={CHART_COLOR.reference} strokeWidth="1" vectorEffect="non-scaling-stroke" />
          <path d={line} fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
        </svg>
        {point ? (
          <>
            <span
              aria-hidden
              className="pointer-events-none absolute size-2.5 -translate-x-1/2 -translate-y-1/2 rounded-full ring-2 ring-ap-white"
              style={{ left: `${left}%`, top: `${top}%`, backgroundColor: color }}
            />
            <span
              aria-hidden
              className="pointer-events-none absolute bottom-full z-10 mb-2 rounded-md bg-ap-graphite px-2 py-1 text-xs whitespace-nowrap text-ap-white shadow-overlay"
              style={{ left: `${left}%`, transform: `translateX(${tooltipShift})` }}
            >
              <span className="text-ap-on-dark-muted">{point.label}</span> <strong className="font-semibold tabular-nums">{formatValue(point.value)}</strong>
            </span>
          </>
        ) : null}
        <span role="status" className="sr-only">
          {announce}
        </span>
      </div>
      <div aria-hidden className="flex justify-between ap-ref text-xs text-ap-muted">
        <span>{points[0]?.label}</span>
        <span>{points[count - 1]?.label}</span>
      </div>
    </div>
  )
}
