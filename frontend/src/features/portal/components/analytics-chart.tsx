import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react'
import { cn } from '@/lib/utils'
import { CHART_COLOR } from '../lib/chart-palette'

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
  /** Nombra el gráfico para lectores de pantalla y la tabla alternativa. */
  label: string
  formatAxis: (value: number) => string
  /** Encabezados de la tabla alternativa (periodo y valor). */
  tableHeaders?: [string, string]
  className?: string
}

const W = 1000
const H = 240

/** Máximo «redondo» del eje (1, 2, 2,5 o 5 × 10ⁿ) para que las marcas sean legibles. El eje siempre parte de 0. */
function niceMax(value: number): number {
  if (value <= 0) return 1
  const power = 10 ** Math.floor(Math.log10(value))
  const step = [1, 2, 2.5, 5, 10].find((candidate) => candidate * power >= value) ?? 10
  return step * power
}

/** Quita la etiqueta repetida al inicio del detalle («5 nov: $ 1.200» → «$ 1.200»). */
function valueOf(point: ChartPoint): string {
  const prefix = `${point.label}: `
  return point.detail.startsWith(prefix) ? point.detail.slice(prefix.length) : point.detail
}

/**
 * Gráfico SVG sin dependencias (§20): barras de una serie o línea real + previsión con rango probable.
 * Colores de `chart-palette`: real en rojo (serie principal), previsión en grafito discontinuo y zona
 * sombreada rotulada «Estimación». El eje Y parte siempre de 0 (no se trunca). Etiquetas en HTML (no se
 * deforman). Con puntero se lee pasando el cursor; con teclado, enfocando el gráfico y usando las flechas
 * (Inicio/Fin saltan a los extremos). Los valores exactos están también en una tabla bajo el gráfico.
 */
export function AnalyticsChart({ mode, points, label, formatAxis, tableHeaders = ['Periodo', 'Valor'], className }: AnalyticsChartProps) {
  const wrapper = useRef<HTMLDivElement>(null)
  const [active, setActive] = useState<number | null>(null)
  const [announce, setAnnounce] = useState('')
  const count = points.length
  if (count === 0) return null

  const values = points.flatMap((point) => [point.bar, point.actual, point.forecast, point.high]).filter((value): value is number => typeof value === 'number')
  const max = niceMax(Math.max(...values, 0))
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
  const firstForecast = points.findIndex((point, index) => index > lastActual && typeof point.forecast === 'number')
  const band = points.flatMap((point, index) => (typeof point.high === 'number' && typeof point.low === 'number' ? [{ index, low: point.low, high: point.high }] : []))
  const bandPath =
    band.length > 0
      ? [
          ...(lastActual >= 0 ? [{ x: xOf(lastActual), y: yOf(points[lastActual]?.actual ?? 0) }] : []),
          ...band.map((item) => ({ x: xOf(item.index), y: yOf(item.high) })),
          ...band.toReversed().map((item) => ({ x: xOf(item.index), y: yOf(item.low) })),
          ...(lastActual >= 0 ? [{ x: xOf(lastActual), y: yOf(points[lastActual]?.actual ?? 0) }] : []),
        ]
          .map((vertex, index) => `${index === 0 ? 'M' : 'L'}${vertex.x.toFixed(1)} ${vertex.y.toFixed(1)}`)
          .join(' ') + ' Z'
      : ''
  // Zona de estimación: desde la mitad entre el último dato real y la primera previsión hasta el final.
  const estimateStart = mode === 'forecast' && firstForecast >= 0 ? (firstForecast * step) : null

  const point = active === null ? undefined : points[active]
  const left = active === null ? 0 : (xOf(active) / W) * 100
  const tooltipShift = left < 20 ? '0%' : left > 80 ? '-100%' : '-50%'
  const middle = count > 2 ? points[Math.floor((count - 1) / 2)] : undefined

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex gap-2">
        <div aria-hidden className="flex w-14 shrink-0 flex-col justify-between text-right ap-ref text-[0.6875rem] leading-none text-ap-muted">
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
          className="relative min-w-0 flex-1 touch-pan-y rounded-sm"
        >
          <svg aria-hidden viewBox={`0 0 ${W} ${H}`} preserveAspectRatio="none" className="block h-56 w-full overflow-visible">
            {estimateStart !== null ? <rect x={estimateStart} y="0" width={W - estimateStart} height={H} fill="var(--ap-paper)" /> : null}
            {[2, H / 2].map((y) => (
              <line key={y} x1="0" x2={W} y1={y} y2={y} stroke={CHART_COLOR.grid} strokeWidth="1" vectorEffect="non-scaling-stroke" />
            ))}
            <line x1="0" x2={W} y1={H - 2} y2={H - 2} stroke={CHART_COLOR.reference} strokeWidth="1" vectorEffect="non-scaling-stroke" />
            {mode === 'bars'
              ? points.map((item, index) =>
                  typeof item.bar === 'number' ? (
                    <rect
                      key={index}
                      x={index * step + step * 0.14}
                      width={step * 0.72}
                      y={yOf(item.bar)}
                      height={Math.max(0, H - 2 - yOf(item.bar))}
                      fill={CHART_COLOR.primary}
                      opacity={active === null || active === index ? 1 : 0.5}
                    />
                  ) : null,
                )
              : (
                <>
                  {bandPath ? <path d={bandPath} fill={CHART_COLOR.reference} opacity="0.6" /> : null}
                  <path d={path('actual')} fill="none" stroke={CHART_COLOR.primary} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" vectorEffect="non-scaling-stroke" />
                  <path d={path('forecast', lastActual)} fill="none" stroke={CHART_COLOR.secondary} strokeWidth="2" strokeDasharray="6 5" strokeLinejoin="round" vectorEffect="non-scaling-stroke" />
                </>
              )}
          </svg>
          {estimateStart !== null ? (
            <span aria-hidden className="ap-index pointer-events-none absolute top-1 pl-2" style={{ left: `${(estimateStart / W) * 100}%` }}>
              Estimación
            </span>
          ) : null}
          {point ? (
            <>
              <span aria-hidden className="pointer-events-none absolute inset-y-0 w-px bg-ap-graphite/40" style={{ left: `${left}%` }} />
              <span
                aria-hidden
                className="pointer-events-none absolute bottom-full z-10 mb-2 rounded-md bg-ap-graphite px-2 py-1 text-xs whitespace-nowrap text-ap-white tabular-nums shadow-overlay"
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
      <div aria-hidden className="ml-16 flex justify-between ap-ref text-xs text-ap-muted">
        <span>{points[0]?.label}</span>
        {middle ? <span>{middle.label}</span> : null}
        <span>{points[count - 1]?.label}</span>
      </div>
      <details className="text-sm">
        <summary className="inline-flex min-h-11 cursor-pointer items-center font-semibold text-ap-ink underline underline-offset-4">Ver los valores exactos en una tabla</summary>
        <div className="max-h-64 overflow-auto rounded-md border border-ap-line" tabIndex={0} role="region" aria-label={`Valores de: ${label}`}>
          <table className="w-full text-left text-sm">
            <caption className="sr-only">{label}</caption>
            <thead className="sticky top-0 bg-ap-paper">
              <tr>
                <th scope="col" className="px-3 py-2 font-semibold text-ap-ink">
                  {tableHeaders[0]}
                </th>
                <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">
                  {tableHeaders[1]}
                </th>
              </tr>
            </thead>
            <tbody>
              {points.map((item, index) => (
                <tr key={index} className="border-t border-ap-line">
                  <th scope="row" className="ap-ref px-3 py-1.5 font-medium text-ap-ink">
                    {item.label}
                  </th>
                  <td className="ap-num px-3 py-1.5 text-ap-ink">{valueOf(item)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
