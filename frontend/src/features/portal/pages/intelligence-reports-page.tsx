import { useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { DownloadIcon, LineChartIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { BranchBreakdown, CategoryBreakdown, Granularity, ReportTotals, SalesReport, TopProduct } from '../api/analytics-types'
import { getAnalyticsService } from '../api/analytics-service'
import { AnalyticsChart, type ChartPoint } from '../components/analytics-chart'
import { BentoTile } from '../components/bento'
import { SelectField } from '../components/form-controls'
import { AnalyticsBoundary, BranchSelect, IntelligencePage } from '../components/intelligence-shell'
import { StatCard } from '../components/stat-card'
import { useStoreQuery } from '../hooks'
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

function defaultGranularity(days: number): Granularity {
  return days <= 31 ? 'DAY' : days <= 120 ? 'WEEK' : 'MONTH'
}

/** Variación porcentual con texto y flecha (no solo color). `inverse`: subir es malo (cancelaciones). */
function Variation({ current, previous, inverse = false }: { current: number; previous: number; inverse?: boolean }) {
  if (previous === 0) return <span>{current === 0 ? 'Sin datos en el periodo anterior' : 'Sin periodo anterior para comparar'}</span>
  const change = ((current - previous) / previous) * 100
  const rounded = Math.round(Math.abs(change))
  if (rounded === 0) return <span>Igual que el periodo anterior</span>
  const up = change > 0
  const good = inverse ? !up : up
  return (
    <span className={cn('font-semibold', good ? 'text-stock' : 'text-destructive')}>
      {up ? '▲' : '▼'} {formatNumber(rounded)} % {up ? 'más' : 'menos'} que el periodo anterior
    </span>
  )
}

/** Cuatro cifras principales en una fila y tres secundarias en otra: siete tarjetas sin huecos en la cuadrícula. */
function Kpis({ totals, previous }: { totals: ReportTotals; previous: ReportTotals }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard label="Ingresos" value={formatCOP(totals.revenue)} hint={<Variation current={totals.revenue} previous={previous.revenue} />} />
        <StatCard label="Pedidos" value={formatNumber(totals.orders)} hint={<Variation current={totals.orders} previous={previous.orders} />} />
        <StatCard label="Ticket promedio" value={formatCOP(totals.averageTicket)} hint={<Variation current={totals.averageTicket} previous={previous.averageTicket} />} />
        <StatCard label="Unidades vendidas" value={formatNumber(totals.units)} hint={<Variation current={totals.units} previous={previous.units} />} />
      </div>
      <div className="grid gap-3 sm:grid-cols-3">
        <SecondaryKpis totals={totals} previous={previous} />
      </div>
    </div>
  )
}

function SecondaryKpis({ totals, previous }: { totals: ReportTotals; previous: ReportTotals }) {
  return (
    <>
      <StatCard label="Clientes" value={formatNumber(totals.customers)} hint={<Variation current={totals.customers} previous={previous.customers} />} />
      <StatCard label="Descuentos otorgados" value={formatCOP(totals.discounts)} hint={<Variation current={totals.discounts} previous={previous.discounts} inverse />} />
      <StatCard
        label="Pedidos cancelados"
        value={formatNumber(totals.cancelledOrders)}
        hint={
          <>
            {new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 1 }).format(totals.cancellationRate)} de los pedidos ·{' '}
            <Variation current={totals.cancelledOrders} previous={previous.cancelledOrders} inverse />
          </>
        }
      />
    </>
  )
}

const BRANCH_COLUMNS: DataColumn<BranchBreakdown>[] = [
  { id: 'sucursal', header: 'Sucursal', rowHeader: true, sortValue: (row) => row.branchName, cell: (row) => row.branchName },
  { id: 'ingresos', header: 'Ingresos', align: 'end', sortValue: (row) => row.revenue, cell: (row) => formatCOP(row.revenue) },
  { id: 'pedidos', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => formatNumber(row.orders) },
  { id: 'unidades', header: 'Unidades', align: 'end', sortValue: (row) => row.units, cell: (row) => formatNumber(row.units) },
]

/**
 * Ventas por categoría como lista con barra proporcional a los ingresos (cabe en un tercio del ancho sin pasar a
 * tarjetas). Ordenada por ingresos; las cifras van en texto, la barra es solo apoyo visual.
 */
function CategoryBars({ rows }: { rows: CategoryBreakdown[] }) {
  const sorted = rows.toSorted((a, b) => b.revenue - a.revenue)
  const max = sorted[0]?.revenue ?? 0
  if (sorted.length === 0) return <p className="text-sm text-muted-foreground">Sin ventas por categoría en este periodo.</p>
  return (
    <ol className="flex flex-col gap-3">
      {sorted.map((row) => (
        <li key={row.categoryId} className="flex flex-col gap-1">
          <div className="flex items-baseline justify-between gap-3 text-sm">
            <span className="min-w-0 truncate font-semibold">{humanizeCode(row.categoryCode)}</span>
            <span className="shrink-0 tabular-nums">{formatCOP(row.revenue)}</span>
          </div>
          <div className="flex items-center gap-2">
            <div aria-hidden className="h-1.5 flex-1 overflow-hidden rounded-full bg-concrete">
              <div className="h-full rounded-full bg-primary/70" style={{ width: `${max > 0 ? Math.max(2, (row.revenue / max) * 100) : 0}%` }} />
            </div>
            <span className="w-20 shrink-0 text-right text-xs text-muted-foreground tabular-nums">
              {formatNumber(row.units)} {row.units === 1 ? 'unidad' : 'unidades'}
            </span>
          </div>
        </li>
      ))}
    </ol>
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
        <p className="font-semibold">{row.name}</p>
        <p className="text-xs text-muted-foreground">{row.sku ?? 'Sin SKU'}</p>
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
    <div role="status">
      <span className="sr-only">Cargando el reporte…</span>
      <div aria-hidden className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton key={index} className="h-28" />
        ))}
        <Skeleton className="h-72 sm:col-span-2 xl:col-span-4" />
      </div>
    </div>
  )
}

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
    <div className="flex flex-col gap-6">
      <Kpis totals={report.totals} previous={report.previous} />
      <div className="grid items-start gap-6 lg:grid-cols-3">
        <BentoTile title="Ingresos" aside={`${dateLabel(report.from)} – ${dateLabel(report.to)}`} className="lg:col-span-2">
          <AnalyticsChart mode="bars" points={points} label="Ingresos del periodo" formatAxis={(value) => compactCOP.format(value)} />
        </BentoTile>
        <BentoTile index={1} title="Por categoría">
          <CategoryBars rows={report.byCategory} />
        </BentoTile>
      </div>
      {/* Con una sola sucursal el desglose repite los totales: solo se muestra con dos o más. */}
      {report.byBranch.length > 1 ? (
        <section aria-labelledby="rep-sucursales" className="flex min-w-0 flex-col gap-3">
          <h2 id="rep-sucursales" className="text-lg font-semibold tracking-tight">
            Por sucursal
          </h2>
          <DataTable caption="Ventas por sucursal" columns={BRANCH_COLUMNS} rows={report.byBranch} getRowId={(row) => row.branchId} defaultSort={{ columnId: 'ingresos', direction: 'desc' }} />
        </section>
      ) : null}
      <section aria-labelledby="rep-productos" className="flex min-w-0 flex-col gap-3">
        <h2 id="rep-productos" className="text-lg font-semibold tracking-tight">
          Productos más vendidos
        </h2>
        <DataTable caption="Productos más vendidos del periodo" columns={PRODUCT_COLUMNS} rows={report.topProducts} getRowId={(row) => row.productId} defaultSort={{ columnId: 'ingresos', direction: 'desc' }} pageSize={5} />
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
              <label htmlFor="rep-desde" className="text-sm font-semibold">
                Desde
              </label>
              <input id="rep-desde" type="date" value={customFrom} max={customTo || undefined} onChange={(event) => set('desde', event.target.value)} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} />
            </div>
            <div className="flex w-full flex-col gap-1.5 sm:w-44">
              <label htmlFor="rep-hasta" className="text-sm font-semibold">
                Hasta
              </label>
              <input id="rep-hasta" type="date" value={customTo} min={customFrom || undefined} onChange={(event) => set('hasta', event.target.value)} className={INPUT_CLASS} aria-invalid={rangeError ? true : undefined} />
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
        <p role="alert" className="rounded-lg border border-destructive/25 bg-destructive/5 p-4 text-sm font-medium text-destructive">
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
