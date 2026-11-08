import { useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { DownloadIcon, LineChartIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatCOP, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { BranchBreakdown, CategoryBreakdown, Granularity, ReportTotals, SalesReport, TopProduct } from '../api/analytics-types'
import { getAnalyticsService } from '../api/analytics-service'
import { AnalyticsChart, type ChartPoint } from '../components/analytics-chart'
import { Delta } from '../components/bento'
import { ChartEmpty, ChartSection, ChartSkeleton } from '../components/chart-section'
import { SelectField } from '../components/form-controls'
import { AnalyticsBoundary, BranchSelect, IntelligencePage } from '../components/intelligence-shell'
import { useStoreQuery } from '../hooks'
import { CHART_BG } from '../lib/chart-palette'
import {
  GRANULARITY_OPTIONS,
  MAX_RANGE_DAYS,
  RANGE_PRESETS,
  csvBlob,
  dateLabel,
  fromYmd,
  isGranularity,
  isRangePreset,
  periodLabel,
  rangeLength,
  resolveRange,
  type RangePreset,
} from '../lib/analytics'
import { saveFile } from '../lib/inventory'
import { humanizeCode } from '../lib/labels'

const compactCOP = new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP', notation: 'compact', maximumFractionDigits: 1 })
const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 })

const GRANULARITY_UNIT: Record<Granularity, string> = { DAY: 'día', WEEK: 'semana', MONTH: 'mes' }

function defaultGranularity(days: number): Granularity {
  return days <= 31 ? 'DAY' : days <= 120 ? 'WEEK' : 'MONTH'
}

/** Variación porcentual con texto y flecha (no solo color). `inverse`: subir es malo (cancelaciones). */
function Variation({ current, previous, inverse = false }: { current: number; previous: number; inverse?: boolean }) {
  if (previous === 0) return <span className="text-ap-muted">{current === 0 ? 'Sin datos en ninguno' : 'Sin dato anterior'}</span>
  const change = ((current - previous) / previous) * 100
  const rounded = Math.round(Math.abs(change))
  if (rounded === 0) return <span className="text-ap-muted">Igual</span>
  const up = change > 0
  const good = inverse ? !up : up
  return (
    <span className={cn('font-semibold tabular-nums', good ? 'text-ap-success' : 'text-ap-danger')}>
      <span aria-hidden>{up ? '▲' : '▼'}</span> {up ? 'Sube' : 'Baja'} {formatNumber(rounded)} %
    </span>
  )
}

interface ComparisonRow {
  id: string
  label: string
  current: number
  previous: number
  format: (value: number) => string
  inverse?: boolean
  note?: string
}

/**
 * «¿Cómo se compara con el periodo anterior?»: tabla de valores exactos en lugar de siete tarjetas KPI
 * (§20.6). Las variaciones van en texto y flecha; cancelaciones y descuentos se leen al revés (subir es malo).
 */
function ComparisonTable({ totals, previous }: { totals: ReportTotals; previous: ReportTotals }) {
  const rows: ComparisonRow[] = [
    { id: 'ingresos', label: 'Ingresos', current: totals.revenue, previous: previous.revenue, format: formatCOP },
    { id: 'pedidos', label: 'Pedidos', current: totals.orders, previous: previous.orders, format: formatNumber },
    { id: 'ticket', label: 'Ticket promedio', current: totals.averageTicket, previous: previous.averageTicket, format: formatCOP },
    { id: 'unidades', label: 'Unidades vendidas', current: totals.units, previous: previous.units, format: formatNumber },
    { id: 'clientes', label: 'Clientes', current: totals.customers, previous: previous.customers, format: formatNumber },
    { id: 'descuentos', label: 'Descuentos otorgados', current: totals.discounts, previous: previous.discounts, format: formatCOP, inverse: true },
    {
      id: 'cancelados',
      label: 'Pedidos cancelados',
      current: totals.cancelledOrders,
      previous: previous.cancelledOrders,
      format: formatNumber,
      inverse: true,
      note: `${percent.format(totals.cancellationRate)} de los pedidos`,
    },
  ]
  return (
    <div className="overflow-x-auto rounded-md border border-ap-line" tabIndex={0} role="region" aria-label="Comparación con el periodo anterior">
      <table className="w-full min-w-[34rem] text-left text-sm">
        <caption className="sr-only">Cifras del periodo frente al periodo anterior</caption>
        <thead className="bg-ap-paper">
          <tr>
            <th scope="col" className="px-3 py-2 font-semibold text-ap-ink">
              Indicador
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">
              Este periodo
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">
              Periodo anterior
            </th>
            <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">
              Variación
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.id} className="border-t border-ap-line">
              <th scope="row" className="px-3 py-2.5 font-medium text-ap-ink">
                {row.label}
                {row.note ? <span className="block text-xs font-normal text-ap-muted">{row.note}</span> : null}
              </th>
              <td className="ap-num px-3 py-2.5 font-semibold text-ap-ink">{row.format(row.current)}</td>
              <td className="ap-num px-3 py-2.5 text-ap-muted">{row.format(row.previous)}</td>
              <td className="ap-num px-3 py-2.5">
                <Variation current={row.current} previous={row.previous} inverse={row.inverse} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

const BRANCH_COLUMNS: DataColumn<BranchBreakdown>[] = [
  { id: 'sucursal', header: 'Sucursal', rowHeader: true, sortValue: (row) => row.branchName, cell: (row) => row.branchName },
  { id: 'ingresos', header: 'Ingresos', align: 'end', sortValue: (row) => row.revenue, cell: (row) => formatCOP(row.revenue) },
  { id: 'pedidos', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => formatNumber(row.orders) },
  { id: 'unidades', header: 'Unidades', align: 'end', sortValue: (row) => row.units, cell: (row) => formatNumber(row.units) },
]

/**
 * Ventas por categoría como barras horizontales ordenadas (§20.4 B) en grafito: el rojo queda para la serie
 * principal del panel (ingresos en el tiempo). Las cifras van en texto, con su participación; la barra es apoyo.
 */
function CategoryBars({ rows, total }: { rows: CategoryBreakdown[]; total: number }) {
  const sorted = rows.toSorted((a, b) => b.revenue - a.revenue)
  const max = sorted[0]?.revenue ?? 0
  if (sorted.length === 0) return <ChartEmpty>Sin ventas por categoría en este periodo.</ChartEmpty>
  return (
    // Altura acotada con desplazamiento propio: muchas categorías no alargan la página.
    // `tabIndex` + `role="region"`: la zona desplazable se alcanza y recorre con teclado.
    <div tabIndex={0} role="region" aria-label="Ventas por categoría" className="-mr-2 max-h-80 overflow-y-auto pr-2 focus-visible:outline-offset-2">
      <ol className="flex flex-col gap-3">
        {sorted.map((row) => (
          <li key={row.categoryId} className="flex flex-col gap-1">
            <div className="flex items-baseline justify-between gap-3 text-sm">
              <span className="min-w-0 truncate font-semibold text-ap-ink">{humanizeCode(row.categoryCode)}</span>
              <span className="ap-num shrink-0 text-ap-ink">{formatCOP(row.revenue)}</span>
            </div>
            <div className="flex items-center gap-2">
              <div aria-hidden className={cn('h-2 flex-1', CHART_BG.track)}>
                <div className={cn('h-full', CHART_BG.secondary)} style={{ width: `${max > 0 ? Math.max(1, (row.revenue / max) * 100) : 0}%` }} />
              </div>
              <span className="w-28 shrink-0 text-right text-xs text-ap-muted tabular-nums">
                {total > 0 ? `${percent.format(row.revenue / total)} · ` : ''}
                {formatNumber(row.units)} u.
              </span>
            </div>
          </li>
        ))}
      </ol>
    </div>
  )
}

const PRODUCT_COLUMNS: DataColumn<TopProduct>[] = [
  {
    id: 'producto',
    header: 'Producto',
    rowHeader: true,
    sortValue: (row) => row.name,
    cell: (row) => (
      <div className="min-w-0">
        <p className="font-semibold text-ap-ink">{row.name}</p>
        <p className="ap-ref text-ap-muted">{row.sku ?? 'Sin SKU'}</p>
      </div>
    ),
  },
  { id: 'ingresos', header: 'Ingresos', align: 'end', sortValue: (row) => row.revenue, cell: (row) => formatCOP(row.revenue) },
  { id: 'unidades', header: 'Unidades', align: 'end', sortValue: (row) => row.units, cell: (row) => formatNumber(row.units) },
]

function reportCsv(report: SalesReport): (string | number)[][] {
  const { totals, previous } = report
  return [
    ['Reporte de ventas', `${report.from} a ${report.to}`],
    [],
    ['Resumen', 'Periodo', 'Periodo anterior'],
    ['Ingresos (COP)', totals.revenue, previous.revenue],
    ['Pedidos', totals.orders, previous.orders],
    ['Unidades', totals.units, previous.units],
    ['Ticket promedio (COP)', totals.averageTicket, previous.averageTicket],
    ['Clientes', totals.customers, previous.customers],
    ['Descuentos (COP)', totals.discounts, previous.discounts],
    ['Pedidos cancelados', totals.cancelledOrders, previous.cancelledOrders],
    ['Tasa de cancelación', totals.cancellationRate, previous.cancellationRate],
    [],
    ['Serie', 'Inicio del periodo', 'Ingresos (COP)', 'Pedidos', 'Unidades'],
    ...report.series.map((point) => ['', point.periodStart, point.revenue, point.orders, point.units]),
    [],
    ['Por sucursal', 'Ingresos (COP)', 'Pedidos', 'Unidades'],
    ...report.byBranch.map((row) => [row.branchName, row.revenue, row.orders, row.units]),
    [],
    ['Por categoría', 'Ingresos (COP)', 'Unidades'],
    ...report.byCategory.map((row) => [humanizeCode(row.categoryCode), row.revenue, row.units]),
    [],
    ['Productos más vendidos', 'SKU', 'Ingresos (COP)', 'Unidades'],
    ...report.topProducts.map((row) => [row.name, row.sku ?? '', row.revenue, row.units]),
  ]
}

function ReportSkeleton() {
  return (
    <div className="grid items-start gap-x-8 gap-y-8 lg:grid-cols-3">
      <ChartSkeleton kind="bars" label="Cargando el reporte" className="lg:col-span-2" />
      <ChartSkeleton kind="list" label="Cargando las categorías" />
      <ChartSkeleton kind="list" label="Cargando la comparación" className="lg:col-span-3" />
    </div>
  )
}

/**
 * Reporte (§20.6). Pregunta principal: «¿cuánto vendí en este periodo y cómo se repartió en el tiempo?» →
 * ingresos por día/semana/mes con la cifra total arriba. Le siguen «¿qué categorías lo explican?», la
 * comparación exacta con el periodo anterior y los desgloses por sucursal y producto.
 */
function ReportView({ report }: { report: SalesReport }) {
  const points = useMemo<ChartPoint[]>(
    () =>
      report.series.map((point) => ({
        label: periodLabel(point.periodStart, report.granularity),
        detail: `${periodLabel(point.periodStart, report.granularity)}: ${formatCOP(point.revenue)} · ${formatNumber(point.orders)} ${point.orders === 1 ? 'pedido' : 'pedidos'}`,
        bar: point.revenue,
      })),
    [report.series, report.granularity],
  )
  const period = `${dateLabel(report.from)} – ${dateLabel(report.to)}`
  const categoriesTotal = report.byCategory.reduce((sum, row) => sum + row.revenue, 0)

  if (report.totals.orders === 0 && report.totals.cancelledOrders === 0) {
    return (
      <EmptyState
        icon={<LineChartIcon />}
        title="Sin ventas en este periodo"
        description={`No hay pedidos entre el ${dateLabel(report.from)} y el ${dateLabel(report.to)}. Prueba con un periodo más largo o con otra sucursal.`}
      />
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <div className="grid items-start gap-x-8 gap-y-8 lg:grid-cols-3">
        <ChartSection
          className="lg:col-span-2"
          title={`¿Cuánto vendiste cada ${GRANULARITY_UNIT[report.granularity]}?`}
          meta={`${period} · ingresos en COP por ${GRANULARITY_UNIT[report.granularity]}`}
          source="Fuente: pedidos de tu almacén en Autopartco. El eje parte de 0."
          lead={
            <div className="flex flex-col gap-1">
              <p className="ap-price text-3xl leading-none text-ap-ink">{formatCOP(report.totals.revenue)}</p>
              <Delta current={report.totals.revenue} previous={report.previous.revenue} />
            </div>
          }
        >
          {points.length > 0 ? (
            <AnalyticsChart mode="bars" points={points} label="Ingresos del periodo" formatAxis={(value) => compactCOP.format(value)} tableHeaders={['Periodo', 'Ingresos y pedidos']} />
          ) : (
            <ChartEmpty>No hay puntos de la serie para este periodo.</ChartEmpty>
          )}
        </ChartSection>
        <ChartSection title="¿Qué categorías generan más ingresos?" meta={`${period} · COP y participación`}>
          <CategoryBars rows={report.byCategory} total={categoriesTotal} />
        </ChartSection>
      </div>

      <section aria-labelledby="rep-comparacion" className="flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4">
        <h2 id="rep-comparacion" className="text-base font-semibold text-ap-ink">
          ¿Cómo se compara con el periodo anterior?
        </h2>
        <ComparisonTable totals={report.totals} previous={report.previous} />
      </section>

      {/* Con una sola sucursal el desglose repite los totales: solo se muestra con dos o más. */}
      {report.byBranch.length > 1 ? (
        <section aria-labelledby="rep-sucursales" className="flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4">
          <h2 id="rep-sucursales" className="text-base font-semibold text-ap-ink">
            ¿Qué sucursal vende más?
          </h2>
          <DataTable caption="Ventas por sucursal" columns={BRANCH_COLUMNS} rows={report.byBranch} getRowId={(row) => row.branchId} defaultSort={{ columnId: 'ingresos', direction: 'desc' }} />
        </section>
      ) : null}
      <section aria-labelledby="rep-productos" className="flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4">
        <h2 id="rep-productos" className="text-base font-semibold text-ap-ink">
          Productos más vendidos
        </h2>
        {report.topProducts.length === 0 ? (
          <ChartEmpty>Ningún producto vendido en este periodo.</ChartEmpty>
        ) : (
          // Altura acotada con desplazamiento propio: todos los productos sin alargar la página.
          <div tabIndex={0} role="region" aria-label="Productos más vendidos" className="max-h-96 overflow-y-auto focus-visible:outline-offset-2">
            <DataTable caption="Productos más vendidos del periodo" columns={PRODUCT_COLUMNS} rows={report.topProducts} getRowId={(row) => row.productId} defaultSort={{ columnId: 'ingresos', direction: 'desc' }} />
          </div>
        )}
      </section>
    </div>
  )
}

/** `/portal/:storeId/inteligencia`: reporte de ventas por rango, granularidad y sucursal. */
export default function IntelligenceReportsPage() {
  const [params, setParams] = useSearchParams()
  const preset: RangePreset = isRangePreset(params.get('rango')) ? (params.get('rango') as RangePreset) : '30'
  const customFrom = params.get('desde') ?? ''
  const customTo = params.get('hasta') ?? ''
  const branchId = params.get('sucursal') ?? ''

  const range = resolveRange(preset, { from: customFrom || undefined, to: customTo || undefined })
  const days = rangeLength(range.from, range.to)
  const rangeError =
    preset !== 'personalizado'
      ? undefined
      : !fromYmd(customFrom) || !fromYmd(customTo)
        ? 'Elige la fecha inicial y la final.'
        : days < 1
          ? 'La fecha final debe ser igual o posterior a la inicial.'
          : days > MAX_RANGE_DAYS
            ? `El rango puede abarcar máximo ${MAX_RANGE_DAYS} días.`
            : undefined
  const granularity: Granularity = isGranularity(params.get('granularidad')) ? (params.get('granularidad') as Granularity) : defaultGranularity(days)

  // Con un rango inválido no se consulta nada útil: se pide el periodo por defecto y no se muestra.
  const query = rangeError ? { ...resolveRange('30', {}), granularity: 'DAY' as Granularity, branchId: undefined } : { ...range, granularity, branchId: branchId || undefined }
  const result = useStoreQuery(`analytics:report:${query.from}:${query.to}:${query.granularity}:${query.branchId ?? ''}`, async (id) =>
    (await getAnalyticsService()).report(id, query),
  )

  function setMany(values: Record<string, string>) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [name, value] of Object.entries(values)) {
          if (value) next.set(name, value)
          else next.delete(name)
        }
        return next
      },
      { replace: true },
    )
  }
  const set = (name: string, value: string) => setMany({ [name]: value })

  function exportCsv() {
    if (!result.data) return
    saveFile(csvBlob(reportCsv(result.data)), `reporte-ventas-${result.data.from}-a-${result.data.to}.csv`)
  }

  return (
    <IntelligencePage
      title="Reportes"
      description="Cómo van tus ventas frente al periodo anterior: ingresos, pedidos, ticket promedio, sucursales, categorías y productos."
      actions={
        <Button variant="outline" className="h-11 px-4" disabled={!result.data || result.status !== 'success' || Boolean(rangeError)} onClick={exportCsv}>
          <DownloadIcon aria-hidden />
          Exportar CSV
        </Button>
      }
    >
      <form role="search" aria-label="Filtros del reporte" className="mb-6 flex flex-col flex-wrap gap-3 sm:flex-row sm:items-end" onSubmit={(event) => event.preventDefault()}>
        <SelectField
          id="rep-rango"
          label="Periodo"
          value={preset}
          onChange={(event) =>
            setMany(
              event.target.value === 'personalizado'
                ? { rango: 'personalizado', desde: range.from, hasta: range.to }
                : { rango: event.target.value === '30' ? '' : event.target.value, desde: '', hasta: '' },
            )
          }
          options={RANGE_PRESETS}
          containerClassName="w-full sm:w-48"
        />
        {preset === 'personalizado' ? (
          <>
            <div className="flex w-full flex-col gap-1.5 sm:w-44">
              <label htmlFor="rep-desde" className="text-sm font-semibold text-ap-ink">
                Desde
              </label>
              <input id="rep-desde" type="date" value={customFrom} max={customTo || undefined} onChange={(event) => set('desde', event.target.value)} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} aria-describedby={rangeError ? 'rep-rango-error' : undefined} />
            </div>
            <div className="flex w-full flex-col gap-1.5 sm:w-44">
              <label htmlFor="rep-hasta" className="text-sm font-semibold text-ap-ink">
                Hasta
              </label>
              <input id="rep-hasta" type="date" value={customTo} min={customFrom || undefined} onChange={(event) => set('hasta', event.target.value)} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} aria-describedby={rangeError ? 'rep-rango-error' : undefined} />
            </div>
          </>
        ) : null}
        <SelectField
          id="rep-granularidad"
          label="Agrupar"
          value={granularity}
          onChange={(event) => set('granularidad', event.target.value)}
          options={GRANULARITY_OPTIONS}
          containerClassName="w-full sm:w-40"
        />
        <BranchSelect value={branchId} onChange={(value) => set('sucursal', value)} />
      </form>
      {rangeError ? (
        <p id="rep-rango-error" role="alert" className="rounded-md border border-ap-danger/30 bg-ap-danger-soft p-4 text-sm font-medium text-ap-danger">
          {rangeError}
        </p>
      ) : (
        <AnalyticsBoundary result={result} what="el reporte de ventas" keepPrevious skeleton={<ReportSkeleton />}>
          {(report) => <ReportView report={report} />}
        </AnalyticsBoundary>
      )}
    </IntelligencePage>
  )
}
