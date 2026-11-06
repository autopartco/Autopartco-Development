import { LightbulbIcon, MoonStarIcon, ShoppingBasketIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { formatNumber } from '@/lib/format'
import { getAnalyticsService } from '../api/analytics-service'
import type { AssociationPair, AssociationReport, AssociationSource } from '../api/analytics-types'
import { AnalyticsBoundary, IntelligencePage } from '../components/intelligence-shell'
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
        <p className="font-semibold">{row.antecedent.name}</p>
        <p className="text-sm text-muted-foreground">con {row.consequent.name}</p>
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
    <div role="status">
      <span className="sr-only">Cargando los productos que se compran juntos…</span>
      <div aria-hidden className="flex flex-col gap-4">
        <Skeleton className="h-28" />
        <Skeleton className="h-64" />
      </div>
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
    <div className="flex flex-col gap-6">
      {combo ? (
        <aside className="flex flex-col gap-2 rounded-lg bg-concrete p-5" aria-label="Combo sugerido">
          <p className="flex items-center gap-2 font-semibold">
            <LightbulbIcon className="size-5" aria-hidden />
            Combo sugerido
          </p>
          <ul className="flex flex-wrap gap-2">
            {combo.names.map((name, index) => (
              <li key={`${name}-${index}`} className="rounded-full bg-card px-3 py-1 text-sm font-medium ring-1 ring-foreground/10">
                {name}
              </li>
            ))}
          </ul>
          <p className="max-w-prose text-sm text-foreground/85">{combo.reason}</p>
        </aside>
      ) : null}
      <section aria-labelledby="pares-titulo" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="pares-titulo" className="text-lg font-semibold tracking-tight">
            Productos que se compran juntos
          </h2>
          <p className="text-sm text-muted-foreground">Actualizado {formatDateTime(report.computedAt)}. Se recalcula cada noche.</p>
        </div>
        {report.source ? <p className="text-sm text-muted-foreground">{SOURCE_TEXT[report.source]}</p> : null}
        <DataTable
          caption="Productos que se compran juntos"
          columns={COLUMNS}
          rows={report.pairs}
          getRowId={(row) => `${row.antecedent.productId}:${row.consequent.productId}`}
          defaultSort={{ columnId: 'lift', direction: 'desc' }}
          pageSize={15}
        />
      </section>
      <p className="flex items-start gap-2 text-sm text-muted-foreground">
        <ShoppingBasketIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>Tus clientes ven estas sugerencias como «Se compran juntos» en la ficha del producto.</span>
      </p>
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
