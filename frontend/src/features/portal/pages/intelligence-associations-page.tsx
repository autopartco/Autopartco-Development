import { MoonStarIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { formatNumber } from '@/lib/format'
import { getAnalyticsService } from '../api/analytics-service'
import type { AssociationPair, AssociationReport, AssociationSource } from '../api/analytics-types'
import { ChartSkeleton } from '../components/chart-section'
import { AnalyticsBoundary, IntelligencePage } from '../components/intelligence-shell'
import { ListSkeleton } from '../components/list-skeleton'
import { useStoreQuery } from '../hooks'
import { formatDateTime } from '../lib/labels'
import { formatFraction } from '../lib/promotions'

const SOURCE_TEXT: Record<AssociationSource, string> = {
  ORDERS: 'Calculado con los pedidos reales de tus clientes.',
  COMPATIBILITY: 'Todavía hay pocos pedidos: estas sugerencias se basan en productos compatibles con los mismos vehículos.',
  CATEGORY: 'Todavía hay pocos pedidos: estas sugerencias se basan en productos de la misma categoría.',
}

const lift = new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 })

const COLUMNS: DataColumn<AssociationPair>[] = [
  {
    id: 'pareja',
    header: 'Se compran juntos',
    rowHeader: true,
    sortValue: (row) => row.antecedent.name,
    cell: (row) => (
      <div>
        <p className="font-semibold text-ap-ink">{row.antecedent.name}</p>
        <p className="text-sm text-ap-muted">con {row.consequent.name}</p>
      </div>
    ),
  },
  { id: 'pedidos', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => formatNumber(row.orders) },
  {
    id: 'confianza',
    header: 'Quien compra el primero, lleva también el segundo',
    align: 'end',
    sortValue: (row) => row.confidence,
    cell: (row) => formatFraction(row.confidence),
  },
  { id: 'lift', header: 'Veces más probable que al azar', align: 'end', sortValue: (row) => row.lift, cell: (row) => `${lift.format(row.lift)}×` },
]

function AssociationsSkeleton() {
  return (
    <div className="flex flex-col gap-8">
      <ChartSkeleton kind="list" label="Cargando el combo sugerido" />
      <ListSkeleton rows={6} label="Cargando los productos que se compran juntos" />
    </div>
  )
}

function AssociationsView({ report }: { report: AssociationReport }) {
  if (report.computedAt === null || report.pairs.length === 0) {
    return (
      <EmptyState
        icon={<MoonStarIcon />}
        title="Aún no hay productos que se compren juntos"
        description="Esta información se calcula cada noche con tus pedidos. Cuando tus clientes empiecen a llevar varios productos en un mismo pedido, verás aquí cuáles conviene ofrecer juntos."
      />
    )
  }
  const combo = report.suggestedCombo
  return (
    <div className="flex flex-col gap-8">
      {combo ? (
        <aside className="flex flex-col items-start gap-2 border-l-2 border-ap-graphite py-1 pl-4" aria-label="Combo sugerido">
          <p className="ap-index">Combo sugerido</p>
          <ul className="flex flex-wrap items-center gap-x-2 gap-y-1 text-base font-semibold text-ap-ink">
            {combo.names.map((name, index) => (
              <li key={`${name}-${index}`} className="inline-flex items-center gap-2">
                {index > 0 ? (
                  <span aria-hidden className="font-normal text-ap-muted">
                    +
                  </span>
                ) : null}
                {name}
              </li>
            ))}
          </ul>
          <p className="max-w-prose text-sm text-ap-ink">{combo.reason}</p>
        </aside>
      ) : null}
      <section aria-labelledby="pares-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
        <div className="flex flex-col gap-1">
          <h2 id="pares-titulo" className="text-base font-semibold text-ap-ink">
            ¿Qué productos llevan juntos tus clientes?
          </h2>
          <p className="ap-ref text-ap-muted">Actualizado {formatDateTime(report.computedAt)} · se recalcula cada noche</p>
        </div>
        {report.source ? <p className="text-sm text-ap-muted">{SOURCE_TEXT[report.source]}</p> : null}
        <p className="text-sm text-ap-muted">Indica que suelen comprarse juntos, no que uno cause la compra del otro. Fíjate en la columna «Pedidos»: con pocos pedidos la relación es menos segura.</p>
        <DataTable
          caption="Productos que se compran juntos"
          columns={COLUMNS}
          rows={report.pairs}
          getRowId={(row) => `${row.antecedent.productId}:${row.consequent.productId}`}
          defaultSort={{ columnId: 'lift', direction: 'desc' }}
          pageSize={15}
        />
      </section>
      <p className="text-sm text-ap-muted">Tus clientes ven estas sugerencias como «Se compran juntos» en la ficha del producto.</p>
    </div>
  )
}

/** `/portal/:storeId/inteligencia/se-compran-juntos`: pares de productos y combo sugerido. */
export default function IntelligenceAssociationsPage() {
  const result = useStoreQuery('analytics:associations', async (id) => (await getAnalyticsService()).associations(id))
  return (
    <IntelligencePage title="Se compran juntos" description="Qué productos llevan juntos tus clientes, para ofrecerlos en combo y no perder ventas.">
      <AnalyticsBoundary result={result} what="los productos que se compran juntos" skeleton={<AssociationsSkeleton />}>
        {(report) => <AssociationsView report={report} />}
      </AnalyticsBoundary>
    </IntelligencePage>
  )
}
