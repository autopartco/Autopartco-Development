import { Link } from 'react-router'
import { PackageCheckIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { getAnalyticsService } from '../api/analytics-service'
import type { StockoutLevel, StockoutRiskItem, StockoutRiskReport } from '../api/analytics-types'
import { AnalyticsBoundary, IntelligencePage } from '../components/intelligence-shell'
import { ListSkeleton } from '../components/list-skeleton'
import { usePortal } from '../context'
import { useActiveBranches, useStoreQuery } from '../hooks'
import { formatDateTime } from '../lib/labels'
import { formatFraction } from '../lib/promotions'

const LEVEL: Record<StockoutLevel, { label: string; marker: string }> = {
  HIGH: { label: 'Alto', marker: 'bg-ap-danger' },
  MEDIUM: { label: 'Medio', marker: 'bg-ap-warning' },
  LOW: { label: 'Bajo', marker: 'bg-ap-line-strong' },
}

/** 9999 = el producto no vende: la cobertura no se puede expresar en días. */
function coverText(days: number): string {
  if (days >= 9999) return 'Sin ventas recientes'
  if (days < 1) return 'Menos de 1 día'
  return `${formatNumber(Math.round(days))} ${Math.round(days) === 1 ? 'día' : 'días'}`
}

function columns(inventoryHref: (row: StockoutRiskItem) => string, multiBranch: boolean): DataColumn<StockoutRiskItem>[] {
  const all: DataColumn<StockoutRiskItem>[] = [
    {
      id: 'producto',
      header: 'Producto',
      rowHeader: true,
      sortValue: (row) => row.productName,
      cell: (row) => (
        <div className="min-w-0">
          <Link to={inventoryHref(row)} className="font-semibold text-ap-ink underline-offset-4 hover:underline focus-visible:underline">
            {row.productName}
          </Link>
          {row.sku ? <p className="ap-ref text-ap-muted">{row.sku}</p> : null}
        </div>
      ),
    },
    { id: 'sucursal', header: 'Sucursal', sortValue: (row) => row.branchName, cell: (row) => row.branchName },
    {
      id: 'riesgo',
      header: 'Probabilidad de agotarse',
      align: 'end',
      sortValue: (row) => row.probability,
      cell: (row) => (
        <span className="inline-flex items-center justify-end gap-2">
          <span aria-hidden className={cn('size-2 shrink-0 rounded-[2px]', LEVEL[row.level].marker)} />
          <span className="font-semibold text-ap-ink tabular-nums">{formatFraction(row.probability)}</span>
          <span className="text-sm text-ap-muted">{LEVEL[row.level].label}</span>
        </span>
      ),
    },
    { id: 'stock', header: 'Stock', align: 'end', sortValue: (row) => row.stock, cell: (row) => <span className="tabular-nums">{formatNumber(row.stock)}</span> },
    { id: 'cobertura', header: 'Alcanza para', align: 'end', sortValue: (row) => row.coverDays, cell: (row) => coverText(row.coverDays) },
  ]
  return multiBranch ? all : all.filter((column) => column.id !== 'sucursal')
}

/**
 * Riesgo de agotamiento (Random Forest calibrado, cálculo nocturno). Pregunta: «¿qué debo reponer o revisar antes de
 * quedarme sin stock?». Una tabla ordenada por probabilidad; el nombre lleva al inventario filtrado por la referencia.
 */
function StockoutView({ report }: { report: StockoutRiskReport }) {
  const { storeId } = usePortal()
  const { branches } = useActiveBranches()
  const base = `/portal/${encodeURIComponent(storeId)}/inventario`
  const inventoryHref = (row: StockoutRiskItem) => `${base}?q=${encodeURIComponent(row.sku ?? row.productName)}`

  if (!report.generatedAt) {
    return (
      <EmptyState
        icon={<PackageCheckIcon />}
        title="El riesgo de agotamiento aún no se ha calculado"
        description="Se calcula cada noche con los movimientos de tu inventario. Vuelve mañana para ver qué productos podrían agotarse."
      />
    )
  }
  if (report.items.length === 0) {
    return (
      <EmptyState
        icon={<PackageCheckIcon />}
        title="Ningún producto tiene riesgo de agotarse"
        description={`Con el ritmo de venta actual, ningún inventario tiene al menos un ${formatFraction(report.minProbability)} de probabilidad de llegar a 0 en los próximos ${report.horizonDays} días. Actualizado ${formatDateTime(report.generatedAt)}.`}
      />
    )
  }

  const high = report.items.filter((row) => row.level === 'HIGH').length
  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="riesgo-resumen" className="flex flex-col gap-2 border-l-2 border-ap-graphite py-1 pl-4">
        <p className="ap-index">Estimación · próximos {report.horizonDays} días</p>
        <h2 id="riesgo-resumen" className="text-base font-semibold text-ap-ink">
          {report.items.length === 1 ? '1 producto podría agotarse' : `${formatNumber(report.items.length)} productos podrían agotarse`}
          {high > 0 ? `, ${formatNumber(high)} con riesgo alto` : ''}
        </h2>
        <p className="max-w-prose text-sm text-ap-muted">
          Revisa primero los de riesgo alto: repón el stock o verifica que la cantidad registrada sea la real. Se muestran los productos con al menos un{' '}
          {formatFraction(report.minProbability)} de probabilidad
          {report.baseRate !== null ? `; en promedio, un producto tiene un ${formatFraction(report.baseRate)} de agotarse en ${report.horizonDays} días` : ''}.
        </p>
      </section>
      <section aria-labelledby="riesgo-tabla" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
        <div className="flex flex-col gap-1">
          <h2 id="riesgo-tabla" className="text-base font-semibold text-ap-ink">
            ¿Qué productos se pueden agotar?
          </h2>
          <p className="ap-ref text-ap-muted">Actualizado {formatDateTime(report.generatedAt)} · se recalcula cada noche</p>
        </div>
        <DataTable
          caption={`Productos con riesgo de agotarse en los próximos ${report.horizonDays} días`}
          columns={columns(inventoryHref, branches.length > 1)}
          rows={report.items}
          getRowId={(row) => row.inventoryId}
          defaultSort={{ columnId: 'riesgo', direction: 'desc' }}
          pageSize={15}
        />
        <p className="text-xs text-ap-muted">
          Modelo {report.algorithm} · versión {report.modelVersion}. Considera las ventas, los ajustes de inventario y el stock de cada sucursal. «Alcanza
          para» usa el ritmo de venta de los últimos 3 meses.
        </p>
      </section>
    </div>
  )
}

/** `/portal/:storeId/inteligencia/riesgo-agotamiento`: inventarios con riesgo de agotarse. */
export default function IntelligenceStockoutPage() {
  const result = useStoreQuery('analytics:stockout-risk', async (id) => (await getAnalyticsService()).stockoutRisk(id))
  return (
    <IntelligencePage title="Riesgo de agotamiento" description="Qué productos podrían quedarse sin stock en las próximas dos semanas.">
      <AnalyticsBoundary result={result} what="el riesgo de agotamiento" skeleton={<ListSkeleton rows={8} label="Cargando el riesgo de agotamiento" />}>
        {(report) => <StockoutView report={report} />}
      </AnalyticsBoundary>
    </IntelligencePage>
  )
}
