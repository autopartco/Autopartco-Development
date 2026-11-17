import { useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { CalendarClockIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Progress } from '@/components/ui/progress'
import { formatCOP, formatNumber } from '@/lib/format'
import { getAnalyticsService } from '../api/analytics-service'
import type { DemandForecastItem, DemandForecastReport, Forecast, ForecastProduct } from '../api/analytics-types'
import { AnalyticsChart, type ChartPoint } from '../components/analytics-chart'
import { ChartEmpty, ChartSection, ChartSkeleton, LegendItem } from '../components/chart-section'
import { AnalyticsBoundary, BranchSelect, IntelligencePage } from '../components/intelligence-shell'
import { branchParamValue, useActiveBranches, useBranchSearchParam, useStoreQuery } from '../hooks'
import { monthLabel, periodLabel } from '../lib/analytics'
import { formatDateTime, humanizeCode } from '../lib/labels'
import { CHART_COLOR } from '../lib/chart-palette'

const compactCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', notation: 'compact', maximumFractionDigits: 1 })

const PRODUCT_COLUMNS: DataColumn<ForecastProduct>[] = [
  { id: 'producto', header: 'Producto', rowHeader: true, sortValue: (row) => row.name, cell: (row) => <span className="font-semibold text-ap-ink">{row.name}</span> },
  { id: 'unidades', header: 'Unidades estimadas', align: 'end', sortValue: (row) => row.expectedUnits, cell: (row) => formatNumber(row.expectedUnits) },
  { id: 'ingresos', header: 'Ingresos estimados', align: 'end', sortValue: (row) => row.expectedRevenue, cell: (row) => formatCOP(row.expectedRevenue) },
]

const DEMAND_COLUMNS: DataColumn<DemandForecastItem>[] = [
  {
    id: 'categoria',
    header: 'Categoría',
    rowHeader: true,
    sortValue: (row) => humanizeCode(row.categoryCode),
    cell: (row) => <span className="font-semibold text-ap-ink">{humanizeCode(row.categoryCode)}</span>,
  },
  { id: 'sucursal', header: 'Sucursal', sortValue: (row) => row.branchName, cell: (row) => row.branchName },
  {
    id: 'unidades',
    header: 'Unidades estimadas',
    align: 'end',
    sortValue: (row) => row.expectedUnits,
    cell: (row) => (
      <span className="font-semibold text-ap-ink tabular-nums">
        {formatNumber(row.expectedUnits)}
        {row.method === 'RECENT_RATE' ? (
          <>
            <span aria-hidden> *</span>
            <span className="sr-only"> (estimada con tu ritmo de ventas reciente)</span>
          </>
        ) : null}
      </span>
    ),
  },
  {
    id: 'rango',
    header: 'Rango probable',
    align: 'end',
    sortValue: (row) => row.high,
    cell: (row) => (
      <span className="tabular-nums">
        {formatNumber(row.low)} a {formatNumber(row.high)}
      </span>
    ),
  },
]

/**
 * Demanda por categoría (modelo LightGBM, cálculo nocturno). Pregunta: «¿qué categorías debo tener abastecidas las
 * próximas semanas?». Tabla ordenable porque se comparan pocas filas exactas (§20.15.10); se rotula como estimación.
 */
function DemandSection({ report, multiBranch }: { report: DemandForecastReport; multiBranch: boolean }) {
  const weeks = Math.round(report.horizonDays / 7)
  const byRecentRate = report.items.some((item) => item.method === 'RECENT_RATE')
  const columns = multiBranch ? DEMAND_COLUMNS : DEMAND_COLUMNS.filter((column) => column.id !== 'sucursal')
  return (
    <section aria-labelledby="proy-demanda" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
      <div className="flex flex-col gap-1">
        <h2 id="proy-demanda" className="text-base font-semibold text-ap-ink">
          ¿Qué categorías venderás en las próximas {weeks} semanas?
        </h2>
        {report.generatedAt ? (
          <p className="ap-ref text-ap-muted">
            Estimación · actualizada {formatDateTime(report.generatedAt)} · se recalcula cada noche
          </p>
        ) : null}
      </div>
      {report.items.length === 0 ? (
        <ChartEmpty>
          {report.generatedAt
            ? 'Todavía no hay categorías con ventas suficientes para estimar su demanda.'
            : 'La estimación por categoría se calcula cada noche con las ventas de los últimos 6 meses. Vuelve mañana para verla.'}
        </ChartEmpty>
      ) : (
        <>
          <p className="max-w-prose text-sm text-ap-muted">
            Unidades que se espera vender por categoría. El rango probable es el intervalo del 80 % que estima el modelo. Úsala para decidir qué
            reponer, no como una cifra exacta.
          </p>
          <DataTable
            caption={`Unidades estimadas por categoría en los próximos ${report.horizonDays} días`}
            columns={columns}
            rows={report.items}
            getRowId={(row) => `${row.branchId}:${row.categoryId}`}
            defaultSort={{ columnId: 'unidades', direction: 'desc' }}
            pageSize={12}
          />
          {byRecentRate ? (
            <p className="text-xs text-ap-muted">
              * Estas categorías venden más de lo que el modelo conoce; se estiman con tu ritmo de ventas de las últimas semanas.
            </p>
          ) : null}
          <p className="text-xs text-ap-muted">
            Modelo {report.algorithm} · versión {report.modelVersion}. Fuente: ventas confirmadas de tu almacén en Autopartco.
          </p>
        </>
      )}
    </section>
  )
}

function ForecastSkeleton() {
  return (
    <div className="flex flex-col gap-8">
      <ChartSkeleton kind="list" label="Cargando la proyección" />
      <ChartSkeleton kind="line" label="Cargando las ventas por semana" />
    </div>
  )
}

/**
 * Proyección (§20.6, §20.10). Pregunta principal: «¿cuánto podrías vender el próximo mes?» → cifra estimada
 * con su rango probable, rotulada como estimación. El gráfico responde «¿de dónde sale?»: ventas reales por
 * semana (rojo) y la previsión (grafito discontinuo) en una zona sombreada marcada «Estimación».
 */
function ForecastView({ forecast }: { forecast: Forecast }) {
  const points = useMemo<ChartPoint[]>(
    () =>
      forecast.weekly.map((week) => {
        const label = periodLabel(week.weekStart, 'WEEK')
        const detail =
          week.actual !== null
            ? `${label}: ${formatCOP(week.actual)} vendidos`
            : `${label}: estimación de ${formatCOP(week.forecast ?? 0)} (entre ${formatCOP(week.low ?? 0)} y ${formatCOP(week.high ?? 0)})`
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
          <Progress value={done} max={forecast.weeksRequired} aria-label="Semanas de ventas acumuladas" indicatorClassName="bg-ap-graphite" />
          <p className="ap-ref text-ap-ink" aria-hidden>
            {done} de {forecast.weeksRequired} semanas
          </p>
        </div>
      </EmptyState>
    )
  }

  const { nextMonth } = forecast
  return (
    <div className="flex flex-col gap-8">
      <section aria-labelledby="proy-mes" className="flex flex-col gap-2 border-l-2 border-ap-graphite py-1 pl-4">
        <p className="ap-index">Estimación · no es una garantía</p>
        <h2 id="proy-mes" className="text-base font-semibold text-ap-ink">
          ¿Cuánto podrías vender en {monthLabel(nextMonth.month)}?
        </h2>
        <p className="ap-price text-4xl leading-none text-ap-ink">

          {formatCOP(nextMonth.expected)}
        </p>
        <p className="text-sm text-ap-muted">
          Lo más probable es que estén entre <strong className="font-semibold text-ap-ink tabular-nums">{formatCOP(nextMonth.low)}</strong> y{' '}
          <strong className="font-semibold text-ap-ink tabular-nums">{formatCOP(nextMonth.high)}</strong>.
        </p>
        <p className="text-xs text-ap-muted">
          Calculada a partir de tus últimas {forecast.weeksRequired} semanas de ventas ({forecast.method.toLowerCase()}).
        </p>
      </section>

      <ChartSection
        title="¿Cómo se compara con las semanas recientes?"
        meta="Ventas por semana en COP · reales y estimadas"
        legend={
          <>
            <LegendItem kind="line" color={CHART_COLOR.primary}>
              Ventas reales
            </LegendItem>
            <LegendItem kind="dashed" color={CHART_COLOR.secondary}>
              Estimación
            </LegendItem>
            <LegendItem kind="area" color={CHART_COLOR.reference}>
              Rango probable
            </LegendItem>
          </>
        }
        source="Fuente: pedidos de tu almacén en Autopartco. La zona sombreada es estimada; el eje parte de 0."
      >
        {points.length > 0 ? (
          <AnalyticsChart mode="forecast" points={points} label="Ventas semanales reales y estimación con rango probable" formatAxis={(value) => compactCOP.format(value)} tableHeaders={['Semana', 'Ventas o estimación']} />
        ) : (
          <ChartEmpty>No hay semanas para mostrar todavía.</ChartEmpty>
        )}
      </ChartSection>

      <section aria-labelledby="proy-productos" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
        <h2 id="proy-productos" className="text-base font-semibold text-ap-ink">
          ¿Qué productos se estima que vendas más?
        </h2>
        {forecast.topProducts.length === 0 ? (
          <ChartEmpty>Todavía no hay productos con ventas suficientes para estimar.</ChartEmpty>
        ) : (
          <DataTable caption="Productos con más ventas estimadas el próximo mes" columns={PRODUCT_COLUMNS} rows={forecast.topProducts} getRowId={(row) => row.productId} defaultSort={{ columnId: 'ingresos', direction: 'desc' }} />
        )}
      </section>
    </div>
  )
}

/** `/portal/:storeId/inteligencia/proyeccion`: proyección de ventas del próximo mes. */
export default function IntelligenceForecastPage() {
  const [, setParams] = useSearchParams()
  const branchId = useBranchSearchParam()
  const result = useStoreQuery(`analytics:forecast:${branchId}`, async (id) => (await getAnalyticsService()).forecast(id, branchId || undefined))
  const demand = useStoreQuery(`analytics:demand-forecast:${branchId}`, async (id) => (await getAnalyticsService()).demandForecast(id, branchId || undefined))
  const { branches } = useActiveBranches()
  return (
    <IntelligencePage title="Proyección" description="Cuánto podrías vender el próximo mes y qué categorías tendrán más demanda.">
      <div className="flex flex-col gap-5">
        <form role="search" aria-label="Filtros de la proyección" className="flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={(event) => event.preventDefault()}>
          <BranchSelect
            value={branchId}
            onChange={(value) =>
              setParams(
                (current) => {
                  const next = new URLSearchParams(current)
                  next.set('sucursal', branchParamValue(value))
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
        <AnalyticsBoundary result={demand} what="la demanda por categoría" keepPrevious skeleton={<ChartSkeleton kind="list" label="Cargando la demanda por categoría" />}>
          {(report) => <DemandSection report={report} multiBranch={!branchId && branches.length > 1} />}
        </AnalyticsBoundary>
      </div>
    </IntelligencePage>
  )
}
