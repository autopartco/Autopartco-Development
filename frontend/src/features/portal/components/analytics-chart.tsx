import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { cn } from '@/lib/utils'

export interface ChartPoint {
  /** Etiqueta corta del eje y del tooltip («5 nov»). */
  label: string
  /** Texto completo para tooltip y lectores de pantalla («5 nov: $ 1.200.000»). */
  detail: string
  /** Barra (modo `bars`). */
  bar?: number | null
  /** Línea real (modo `forecast`). */
  actual?: number | null
  /** Línea de previsión y banda. */
  forecast?: number | null
  low?: number | null
  high?: number | null
}

interface AnalyticsChartProps {
  mode: 'bars' | 'forecast'
  points: ChartPoint[]
  /** Nombra el gráfico para lectores de pantalla. */
  label: string
  formatAxis: (value: number) => string
  className?: string
}

const W = 1000
const H = 240

/**
 * Gráfico SVG sin dependencias: barras (serie de ventas) o línea real + previsión con banda.
 * Las etiquetas son HTML (no se deforman). Con puntero se lee pasando el cursor; con teclado,
 * enfocando el gráfico y usando las flechas (Inicio/Fin saltan a los extremos). Los valores
 * también están disponibles en una tabla bajo el gráfico.
 */
export function AnalyticsChart({ mode, points, label, formatAxis, className }: AnalyticsChartProps) {
  const wrapper = useRef<HTMLDivElement>(null)
  const [active, setActive] = useState<number | null>(null)
  const [announce, setAnnounce] = useState('')
  const count = points.length
  if (count === 0) return null

  const values = points.flatMap((point) => [point.bar, point.actual, point.forecast, point.high]).filter((value): value is number => typeof value === 'number')
  const max = Math.max(...values, 1)
  const step = W / count
  const xOf = (index: number) => (index + 0.5) * step
  const yOf = (value: number) => H - (Math.max(0, value) / max) * (H - 8) - 2

  function move(index: number, announceIt: boolean) {
    const next = Math.min(count - 1, Math.max(0, index))
    setActive(next)
    const point = points[next]
    if (announceIt && point) setAnnounce(point.detail)
  }

  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    const rect = event.currentTarget.getBoundingClientRect()
    if (rect.width === 0) return
    move(Math.floor(((event.clientX - rect.left) / rect.width) * count), false)
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const current = active ?? count - 1
    const targets: Record<string, number> = { ArrowLeft: current - 1, ArrowRight: current + 1, Home: 0, End: count - 1 }
    const target = targets[event.key]
    if (target === undefined) return
    event.preventDefault()
    move(target, true)
  }

  const path = (key: 'actual' | 'forecast', joinFrom?: number) => {
    const parts: string[] = []
    if (joinFrom !== undefined && key === 'forecast') {
      const anchor = points[joinFrom]?.actual
      if (typeof anchor === 'number') parts.push(`M${xOf(joinFrom).toFixed(1)} ${yOf(anchor).toFixed(1)}`)
    }
    points.forEach((point, index) => {
      const value = point[key]
      if (typeof value === 'number') parts.push(`${parts.length === 0 ? 'M' : 'L'}${xOf(index).toFixed(1)} ${yOf(value).toFixed(1)}`)
    })
    return parts.join(' ')
  }

  const lastActual = points.reduce((found, point, index) => (typeof point.actual === 'number' ? index : found), -1)
  const band = points.flatMap((point, index) => (typeof point.high === 'number' && typeof point.low === 'number' ? [{ index, low: point.low, high: point.high }] : []))
  const bandPath =
    band.length > 0
      ? [
          ...(lastActual >= 0 ? [{ x: xOf(lastActual), y: yOf(points[lastActual]?.actual ?? 0), up: true }] : []),
          ...band.map((item) => ({ x: xOf(item.index), y: yOf(item.high), up: true })),
          ...band.toReversed().map((item) => ({ x: xOf(item.index), y: yOf(item.low), up: false })),
          ...(lastActual >= 0 ? [{ x: xOf(lastActual), y: yOf(points[lastActual]?.actual ?? 0), up: false }] : []),
        ]
          .map((vertex, index) => `${index === 0 ? 'M' : 'L'}${vertex.x.toFixed(1)} ${vertex.y.toFixed(1)}`)
          .join(' ') + ' Z'
      : ''

  const point = active === null ? undefined : points[active]
  const left = active === null ? 0 : (xOf(active) / W) * 100
  const tooltipShift = left < 20 ? '0%' : left > 80 ? '-100%' : '-50%'
  const first = points[0]
  const last = points[count - 1]

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex gap-2">
        <div aria-hidden className="flex w-14 shrink-0 flex-col justify-between text-right text-[0.6875rem] leading-none text-muted-foreground tabular-nums">
          <span>{formatAxis(max)}</span>
          <span>{formatAxis(max / 2)}</span>
          <span>{formatAxis(0)}</span>
        </div>
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
          className="relative min-w-0 flex-1 touch-pan-y rounded-md"
        >
          <svg aria-hidden viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="block h-56 w-full overflow-visible">
            {[2, H / 2, H - 2].map((y) => (
              <line key={y} x1="0" x2={W} y1={y} y2={y} stroke="var(--color-border)" strokeWidth="1" vectorEffect="non-scaling-stroke" />
            ))}
            {mode === 'bars'
              ? points.map((item, index) =>
                  typeof item.bar === 'number' ? (
                    <rect
                      key={index}
                      x={index * step + step * 0.14}
                      width={step * 0.72}
                      y={yOf(item.bar)}
                      height={Math.max(0, H - 2 - yOf(item.bar))}
                      rx="1"
                      fill="var(--color-thread)"
                      opacity={active === null || active === index ? 1 : 0.55}
                    />
                  ) : null,
                )
              : (
                <>
                  {bandPath ? <path d={bandPath} fill="var(--color-thread)" opacity="0.14" /> : null}
                  <path d={path('actual')} fill="none" stroke="var(--color-foreground)" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
                  <path d={path('forecast', lastActual)} fill="none" stroke="var(--color-thread)" strokeWidth="2.5" strokeDasharray="6 5" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
                </>
              )}
          </svg>
          {point ? (
            <>
              {mode === 'forecast' ? (
                <span
                  aria-hidden
                  className="pointer-events-none absolute inset-y-0 w-px bg-foreground/25"
                  style={{ left: `${left}%` }}
                />
              ) : null}
              <span
                aria-hidden
                className="pointer-events-none absolute bottom-full z-10 mb-2 rounded-md bg-asphalt px-2 py-1 text-xs whitespace-nowrap text-white shadow-md"
                style={{ left: `${left}%`, transform: `translateX(${tooltipShift})` }}
              >
                {point.detail}
              </span>
            </>
          ) : null}
          <span role="status" className="sr-only">
            {announce}
          </span>
        </div>
      </div>
      <div aria-hidden className="ml-16 flex justify-between text-xs text-muted-foreground">
        <span>{first?.label}</span>
        <span>{last?.label}</span>
      </div>
      <details className="text-sm">
        <summary className="inline-flex min-h-11 cursor-pointer items-center font-semibold underline underline-offset-4">Ver los valores en una tabla</summary>
        <div className="max-h-64 overflow-auto rounded-md ring-1 ring-foreground/10" tabIndex={0} role="region" aria-label={`Valores de: ${label}`}>
          <table className="w-full text-left text-sm">
            <caption className="sr-only">{label}</caption>
            <tbody>
              {points.map((item, index) => (
                <tr key={index} className="border-b border-border last:border-0">
                  <td className="px-3 py-1.5">{item.detail}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
