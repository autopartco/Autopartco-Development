import { Link, useSearchParams } from 'react-router'
import { LightbulbIcon, TagIcon, UsersIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { getAnalyticsService } from '../api/analytics-service'
import type { CustomerReport, CustomerSegmentCode, StoreCustomer } from '../api/analytics-types'
import { AnalyticsBoundary, IntelligencePage } from '../components/intelligence-shell'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { SEGMENT_CODES, SEGMENT_INFO, dateLabel, isSegmentCode } from '../lib/analytics'
import { formatDate } from '../lib/labels'
import { SEGMENT_AUDIENCE } from '../lib/promotions'

/** «Crear promoción para este segmento»: abre el formulario de promociones con la audiencia prellenada. */
function CreatePromotionLink({ segment }: { segment: CustomerSegmentCode }) {
  const { storeId, can, subscription } = usePortal()
  const audience = SEGMENT_AUDIENCE[segment]
  if (!audience || !can('PROMOTION_CREATE') || !subscription?.capabilities.includes('PROMOTIONS')) return null
  return (
    <Button
      variant="outline"
      className="h-11 bg-card px-4"
      nativeButton={false}
      render={<Link to={`/portal/${encodeURIComponent(storeId)}/promociones?nueva=1&audiencia=${audience}`} />}
    >
      <TagIcon aria-hidden />
      Crear promoción para este segmento
    </Button>
  )
}

function daysAgo(days: number): string {
  if (days <= 0) return 'Hoy'
  return days === 1 ? 'Hace 1 día' : `Hace ${formatNumber(days)} días`
}

const COLUMNS: DataColumn<StoreCustomer>[] = [
  { id: 'cliente', header: 'Cliente', rowHeader: true, sortValue: (row) => row.name, cell: (row) => <span className="font-semibold">{row.name}</span> },
  { id: 'segmento', header: 'Segmento', sortValue: (row) => SEGMENT_INFO[row.segment].label, cell: (row) => SEGMENT_INFO[row.segment].label },
  { id: 'pedidos', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => formatNumber(row.orders) },
  { id: 'ingresos', header: 'Compras', align: 'end', sortValue: (row) => row.revenue, cell: (row) => formatCOP(row.revenue) },
  { id: 'ticket', header: 'Ticket promedio', align: 'end', sortValue: (row) => row.averageTicket, cell: (row) => formatCOP(row.averageTicket) },
  { id: 'primero', header: 'Primer pedido', sortValue: (row) => row.firstOrderAt, cell: (row) => formatDate(row.firstOrderAt) },
  {
    id: 'ultimo',
    header: 'Último pedido',
    sortValue: (row) => row.daysSinceLastOrder,
    cell: (row) => (
      <div>
        <p>{formatDate(row.lastOrderAt)}</p>
        <p className="text-xs text-muted-foreground">{daysAgo(row.daysSinceLastOrder)}</p>
      </div>
    ),
  },
]

function CustomersSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Cargando los clientes…</span>
      <div aria-hidden className="flex flex-col gap-4">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-28" />
          ))}
        </div>
        <Skeleton className="h-64" />
      </div>
    </div>
  )
}

function CustomersView({ report, selected, onSelect }: { report: CustomerReport; selected: CustomerSegmentCode | undefined; onSelect: (code: CustomerSegmentCode | undefined) => void }) {
  const total = report.segments.reduce((sum, segment) => sum + segment.customers, 0)
  if (total === 0 && !selected) {
    return (
      <EmptyState
        icon={<UsersIcon />}
        title="Aún no hay clientes para analizar"
        description={`Cuando tus clientes hagan pedidos, los verás agrupados aquí según qué tan seguido compran. Se analizan los últimos ${report.windowDays} días.`}
      />
    )
  }
  const suggestion = selected ? SEGMENT_INFO[selected].suggestion : null
  const suggested = report.segments.filter((segment) => segment.customers > 0 && SEGMENT_INFO[segment.code].suggestion)

  return (
    <div className="flex flex-col gap-6">
      <section aria-labelledby="seg-titulo" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="seg-titulo" className="text-lg font-semibold tracking-tight">
            Segmentos
          </h2>
          <p className="text-sm text-muted-foreground">
            Últimos {formatNumber(report.windowDays)} días, al {dateLabel(report.asOf)}. Toca un segmento para filtrar la tabla.
          </p>
        </div>
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {SEGMENT_CODES.map((code) => {
            const segment = report.segments.find((item) => item.code === code)
            const active = selected === code
            return (
              <li key={code}>
                <button
                  type="button"
                  aria-pressed={active}
                  onClick={() => onSelect(active ? undefined : code)}
                  className={cn(
                    'flex min-h-11 w-full flex-col items-start gap-1 rounded-xl bg-card p-4 text-left ring-1 transition-[box-shadow,background-color] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
                    active ? 'bg-concrete ring-2 ring-foreground' : 'ring-foreground/10 hover:ring-foreground/30',
                  )}
                >
                  <span className="text-sm font-semibold">{segment?.label ?? SEGMENT_INFO[code].label}</span>
                  <span className="text-3xl leading-none font-extrabold tracking-[-0.035em] tabular-nums">{formatNumber(segment?.customers ?? 0)}</span>
                  <span className="text-sm text-muted-foreground">{formatCOP(segment?.revenue ?? 0)} en compras</span>
                  <span className="text-xs text-muted-foreground">{SEGMENT_INFO[code].description}</span>
                </button>
              </li>
            )
          })}
        </ul>
      </section>

      {suggestion && selected ? (
        <aside className="flex flex-col gap-2 rounded-lg bg-concrete p-5" aria-label={`Acción sugerida para ${SEGMENT_INFO[selected].label}`}>
          <p className="flex items-center gap-2 font-semibold">
            <LightbulbIcon className="size-5" aria-hidden />
            {suggestion.title}
          </p>
          <p className="max-w-prose text-sm text-foreground/85">{suggestion.text}</p>
          <div className="mt-1">
            <CreatePromotionLink segment={selected} />
          </div>
        </aside>
      ) : !selected && suggested.length > 0 ? (
        <section aria-labelledby="acc-titulo" className="flex flex-col gap-3">
          <h2 id="acc-titulo" className="text-lg font-semibold tracking-tight">
            Acciones sugeridas
          </h2>
          <ul className="grid gap-3 md:grid-cols-2">
            {suggested.map((segment) => {
              const info = SEGMENT_INFO[segment.code]
              return (
                <li key={segment.code} className="flex flex-col items-start gap-1.5 rounded-lg bg-concrete p-4">
                  <p className="flex items-center gap-2 font-semibold">
                    <LightbulbIcon className="size-4" aria-hidden />
                    {info.suggestion?.title}
                  </p>
                  <p className="text-sm text-foreground/85">
                    {info.suggestion?.text} <span className="text-muted-foreground">({pluralize(segment.customers, 'cliente', 'clientes')} en {segment.label.toLowerCase()})</span>
                  </p>
                  <div className="flex flex-wrap items-center gap-x-2">
                    <Button variant="ghost" className="-ml-3 h-11 px-3 underline underline-offset-4" onClick={() => onSelect(segment.code)}>
                      Ver estos clientes
                    </Button>
                    <CreatePromotionLink segment={segment.code} />
                  </div>
                </li>
              )
            })}
          </ul>
        </section>
      ) : null}

      <section aria-labelledby="cli-titulo" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="cli-titulo" className="text-lg font-semibold tracking-tight">
            {selected ? SEGMENT_INFO[selected].label : 'Todos los clientes'}
          </h2>
          {selected ? (
            <Button variant="outline" className="h-11 px-4" onClick={() => onSelect(undefined)}>
              Quitar filtro
            </Button>
          ) : null}
        </div>
        {report.customers.length === 0 ? (
          <EmptyState icon={<UsersIcon />} title="Ningún cliente en este segmento" description="Cuando alguien encaje en este grupo, aparecerá aquí." />
        ) : (
          <DataTable
            caption={selected ? `Clientes del segmento ${SEGMENT_INFO[selected].label}` : 'Clientes del almacén'}
            columns={COLUMNS}
            rows={report.customers}
            getRowId={(row) => row.personId}
            defaultSort={{ columnId: 'ingresos', direction: 'desc' }}
            pageSize={15}
          />
        )}
      </section>
    </div>
  )
}

/** `/portal/:storeId/inteligencia/clientes`: segmentos de clientes y acciones sugeridas. */
export default function IntelligenceCustomersPage() {
  const [params, setParams] = useSearchParams()
  const segmentParam = params.get('segmento')
  const segment = isSegmentCode(segmentParam) ? segmentParam : undefined
  const result = useStoreQuery(`analytics:customers:${segment ?? 'all'}`, async (id) => (await getAnalyticsService()).customers(id, segment))

  function select(code: CustomerSegmentCode | undefined) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (code) next.set('segmento', code)
        else next.delete('segmento')
        return next
      },
      { replace: true },
    )
  }

  return (
    <IntelligencePage title="Clientes" description="Quiénes te compran, con qué frecuencia y a quiénes conviene escribirles ahora.">
      <AnalyticsBoundary result={result} what="los clientes" keepPrevious skeleton={<CustomersSkeleton />}>
        {(report) => <CustomersView report={report} selected={segment} onSelect={select} />}
      </AnalyticsBoundary>
    </IntelligencePage>
  )
}
