import { useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { CalendarClockIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber } from '@/lib/format'
import { getAnalyticsService } from '../api/analytics-service'
import type { Forecast, ForecastProduct } from '../api/analytics-types'
import { AnalyticsChart, type ChartPoint } from '../components/analytics-chart'
import { BentoTile } from '../components/bento'
import { AnalyticsBoundary, BranchSelect, IntelligencePage } from '../components/intelligence-shell'
import { useStoreQuery } from '../hooks'
import { monthLabel, periodLabel } from '../lib/analytics'

const compactCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', notation: 'compact', maximumFractionDigits: 1 })

const PRODUCT_COLUMNS: DataColumn<ForecastProduct>[] = [
  { id: 'producto', header: 'Producto', rowHeader: true, sortValue: (row) => row.name, cell: (row) => <span className="font-semibold">{row.name}</span> },
  { id: 'unidades', header: 'Unidades esperadas', align: 'end', sortValue: (row) => row.expectedUnits, cell: (row) => formatNumber(row.expectedUnits) },
  { id: 'ingresos', header: 'Ingresos esperados', align: 'end', sortValue: (row) => row.expectedRevenue, cell: (row) => formatCOP(row.expectedRevenue) },
]

function ForecastSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Cargando la proyección…</span>
      <div aria-hidden className="flex flex-col gap-4">
        <Skeleton className="h-32" />
        <Skeleton className="h-72" />
      </div>
    </div>
  )
}

function ForecastView({ forecast }: { forecast: Forecast }) {
  const points = useMemo<ChartPoint[]>(
    () =>
      forecast.weekly.map((week) => {
        const label = periodLabel(week.weekStart, 'WEEK')
        const detail =
          week.actual !== null
            ? `${label}: ${formatCOP(week.actual)} vendidos`
            : `${label}: previsión de ${formatCOP(week.forecast ?? 0)} (entre ${formatCOP(week.low ?? 0)} y ${formatCOP(week.high ?? 0)})`
        return { label, detail, actual: week.actual, forecast: week.forecast, low: week.low, high: week.high }
      }),
    [forecast.weekly],
  )

  if (forecast.status === 'INSUFFICIENT_HISTORY' || !forecast.nextMonth) {
    const done = Math.min(forecast.weeksAvailable, forecast.weeksRequired)
    const missing = Math.max(0, forecast.weeksRequired - forecast.weeksAvailable)
    return (
      <EmptyState
        icon={<CalendarClockIcon />}
        title="Aún no hay historial suficiente para proyectar"
        description={`Necesitamos al menos ${forecast.weeksRequired} semanas de ventas para proyectar el próximo mes. Llevas ${forecast.weeksAvailable} ${forecast.weeksAvailable === 1 ? 'semana' : 'semanas'}${missing > 0 ? ` y te faltan ${missing}` : ''}. Seguimos contando con cada pedido que recibes.`}
      >
        <div className="flex w-full max-w-md flex-col gap-2">
          <Progress value={done} max={forecast.weeksRequired} aria-label="Semanas de ventas acumuladas" />
          <p className="text-sm font-semibold tabular-nums" aria-hidden>
            {done} de {forecast.weeksRequired} semanas
          </p>
        </div>
      </EmptyState>
    )
  }

  const { nextMonth } = forecast
  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="proy-mes" className="flex flex-col gap-2 rounded-xl bg-card p-5 ring-1 ring-foreground/10 sm:p-6">
        <h2 id="proy-mes" className="text-sm font-semibold text-muted-foreground">
          Ventas esperadas en {monthLabel(nextMonth.month)}
        </h2>
        <p className="text-4xl leading-none font-extrabold tracking-[-0.035em] tabular-nums">{formatCOP(nextMonth.expected)}</p>
        <p className="text-sm text-muted-foreground">
          Lo más probable es que estén entre <strong className="font-semibold text-foreground">{formatCOP(nextMonth.low)}</strong> y{' '}
          <strong className="font-semibold text-foreground">{formatCOP(nextMonth.high)}</strong>.
        </p>
        <p className="text-xs text-muted-foreground">
          Es una estimación a partir de tus últimas {forecast.weeksRequired} semanas ({forecast.method.toLowerCase()}), no una garantía de ventas.
        </p>
      </section>

      <BentoTile
        title="Ventas por semana: reales y previstas"
        aside={
          <span className="flex flex-wrap gap-x-3 gap-y-1">
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden className="h-0.5 w-4 bg-foreground" />
              Real
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden className="h-0.5 w-4 border-t-2 border-dashed border-primary" />
              Previsión
            </span>
            <span className="inline-flex items-center gap-1.5">
              <span aria-hidden className="size-3 rounded-sm bg-primary/20" />
              Rango probable
            </span>
          </span>
        }
      >
        <AnalyticsChart mode="forecast" points={points} label="Ventas semanales reales y previsión con rango probable" formatAxis={(value) => compactCOP.format(value)} />
      </BentoTile>

      <section aria-labelledby="proy-productos" className="flex flex-col gap-3">
        <h2 id="proy-productos" className="text-lg font-semibold tracking-tight">
          Productos que más se esperan vender
        </h2>
        {forecast.topProducts.length === 0 ? (
          <p className="text-sm text-muted-foreground">Todavía no hay productos con ventas suficientes para estimar.</p>
        ) : (
          <DataTable caption="Productos con más ventas esperadas el próximo mes" columns={PRODUCT_COLUMNS} rows={forecast.topProducts} getRowId={(row) => row.productId} defaultSort={{ columnId: 'ingresos', direction: 'desc' }} />
        )}
      </section>
    </div>
  )
}

/** `/portal/:storeId/inteligencia/proyeccion`: proyección de ventas del próximo mes. */
export default function IntelligenceForecastPage() {
  const [params, setParams] = useSearchParams()
  const branchId = params.get('sucursal') ?? ''
  const result = useStoreQuery(`analytics:forecast:${branchId}`, async (id) => (await getAnalyticsService()).forecast(id, branchId || undefined))
  return (
    <IntelligencePage title="Proyección" description="Cuánto podrías vender el próximo mes según el ritmo de tus ventas recientes.">
      <div className="flex flex-col gap-5">
        <form role="search" aria-label="Filtros de la proyección" className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={(event) => event.preventDefault()}>
          <BranchSelect
            value={branchId}
            onChange={(value) =>
              setParams(
                (current) => {
                  const next = new URLSearchParams(current)
                  if (value) next.set('sucursal', value)
                  else next.delete('sucursal')
                  return next
                },
                { replace: true },
              )
            }
          />
        </form>
        <AnalyticsBoundary result={result} what="la proyección" keepPrevious skeleton={<ForecastSkeleton />}>
          {(forecast) => <ForecastView forecast={forecast} />}
        </AnalyticsBoundary>
      </div>
    </IntelligencePage>
  )
}
