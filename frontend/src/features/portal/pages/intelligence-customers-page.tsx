import { Link, useSearchParams } from 'react-router'
import { TagIcon, UsersIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { getAnalyticsService } from '../api/analytics-service'
import type { CustomerReport, CustomerSegmentCode, StoreCustomer } from '../api/analytics-types'
import { ChartSection, ChartSkeleton } from '../components/chart-section'
import { AnalyticsBoundary, IntelligencePage } from '../components/intelligence-shell'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { CHART_BG } from '../lib/chart-palette'
import { SEGMENT_CODES, SEGMENT_INFO, dateLabel, isSegmentCode } from '../lib/analytics'
import { formatDate } from '../lib/labels'
import { SEGMENT_AUDIENCE } from '../lib/promotions'

const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 0 })

/** «Crear promoción para este segmento»: abre el formulario de promociones con la audiencia prellenada. */
function CreatePromotionLink({ segment }: { segment: CustomerSegmentCode }) {
  const { storeId, can, subscription } = usePortal()
  const audience = SEGMENT_AUDIENCE[segment]
  if (!audience || !can('PROMOTION_CREATE') || !subscription?.capabilities.includes('PROMOTIONS')) return null
  return (
    <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`/portal/${encodeURIComponent(storeId)}/promociones?nueva=1&audiencia=${audience}`} />}>
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
  { id: 'cliente', header: 'Cliente', rowHeader: true, sortValue: (row) => row.name, cell: (row) => <span className="font-semibold text-ap-ink">{row.name}</span> },
  { id: 'segmento', header: 'Segmento', sortValue: (row) => SEGMENT_INFO[row.segment].label, cell: (row) => SEGMENT_INFO[row.segment].label },
  { id: 'pedidos', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => formatNumber(row.orders) },
  { id: 'ingresos', header: 'Compras', align: 'end', sortValue: (row) => row.revenue, cell: (row) => formatCOP(row.revenue) },
  { id: 'ticket', header: 'Ticket promedio', align: 'end', sortValue: (row) => row.averageTicket, cell: (row) => formatCOP(row.averageTicket) },
  { id: 'primero', header: 'Primer pedido', sortValue: (row) => row.firstOrderAt, cell: (row) => <span className="ap-ref">{formatDate(row.firstOrderAt)}</span> },
  {
    id: 'ultimo',
    header: 'Último pedido',
    sortValue: (row) => row.daysSinceLastOrder,
    cell: (row) => (
      <div>
        <p className="ap-ref">{formatDate(row.lastOrderAt)}</p>
        <p className="text-xs text-ap-muted">{daysAgo(row.daysSinceLastOrder)}</p>
      </div>
    ),
  },
]

function CustomersSkeleton() {
  return (
    <div className="flex flex-col gap-8">
      <ChartSkeleton kind="list" label="Cargando los segmentos" />
      <ChartSkeleton kind="list" label="Cargando los clientes" />
    </div>
  )
}

/**
 * «¿Cómo se reparten tus clientes?»: barras horizontales por segmento (§20.4 B), en grafito y con la cifra y
 * la participación en texto. Cada fila es un botón que filtra la tabla; la seleccionada lleva la línea roja.
 */
function SegmentBars({ report, selected, onSelect }: { report: CustomerReport; selected: CustomerSegmentCode | undefined; onSelect: (code: CustomerSegmentCode | undefined) => void }) {
  const total = report.segments.reduce((sum, segment) => sum + segment.customers, 0)
  const max = Math.max(1, ...report.segments.map((segment) => segment.customers))
  return (
    <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
      {SEGMENT_CODES.map((code) => {
        const segment = report.segments.find((item) => item.code === code)
        const customers = segment?.customers ?? 0
        const active = selected === code
        return (
          <li key={code}>
            <button
              type="button"
              aria-pressed={active}
              onClick={() => onSelect(active ? undefined : code)}
              className={cn(
                'grid min-h-11 w-full gap-x-4 gap-y-1 border-l-2 py-3 pr-2 pl-3 text-left transition-colors duration-150 sm:grid-cols-[12rem_minmax(0,1fr)_9rem] sm:items-center',
                active ? 'border-ap-red bg-ap-red-tint' : 'border-transparent hover:bg-ap-paper',
              )}
            >
              <span className="flex flex-col">
                <span className="text-sm font-semibold text-ap-ink">
                  {segment?.label ?? SEGMENT_INFO[code].label}
                  {active ? <span className="ml-2 text-xs font-medium text-ap-red-hover">Filtrando</span> : null}
                </span>
                <span className="text-xs text-ap-muted">{SEGMENT_INFO[code].description}</span>
              </span>
              <span className="flex items-center gap-3">
                <span aria-hidden className={cn('h-2.5 flex-1', CHART_BG.track)}>
                  <span className={cn('block h-full', CHART_BG.secondary)} style={{ width: `${customers > 0 ? Math.max(1, (customers / max) * 100) : 0}%` }} />
                </span>
                <span className="w-24 shrink-0 text-right text-sm tabular-nums">
                  <span className="ap-price text-ap-ink">{formatNumber(customers)}</span>
                  <span className="ml-1 text-xs text-ap-muted">{total > 0 ? percent.format(customers / total) : '0 %'}</span>
                </span>
              </span>
              <span className="ap-num text-sm text-ap-muted sm:text-right">{formatCOP(segment?.revenue ?? 0)} en compras</span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

/**
 * Clientes (§20.6). Pregunta principal: «¿a quiénes conviene escribirles ahora?» → reparto por segmento (filtro)
 * y la acción sugerida para cada uno; la tabla da los valores exactos por cliente.
 */
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
    <div className="flex flex-col gap-8">
      <ChartSection
        title="¿Cómo se reparten tus clientes?"
        meta={`Últimos ${formatNumber(report.windowDays)} días, al ${dateLabel(report.asOf)} · clientes por segmento`}
        source={`${pluralize(total, 'cliente', 'clientes')} en total. Elige un segmento para filtrar la tabla.`}
      >
        <SegmentBars report={report} selected={selected} onSelect={onSelect} />
      </ChartSection>

      {suggestion && selected ? (
        <aside className="flex flex-col items-start gap-2 border-l-2 border-ap-graphite py-1 pl-4" aria-label={`Acción sugerida para ${SEGMENT_INFO[selected].label}`}>
          <p className="ap-index">Acción sugerida · {SEGMENT_INFO[selected].label}</p>
          <p className="font-semibold text-ap-ink">{suggestion.title}</p>
          <p className="max-w-prose text-sm text-ap-ink">{suggestion.text}</p>
          <div className="mt-1">
            <CreatePromotionLink segment={selected} />
          </div>
        </aside>
      ) : !selected && suggested.length > 0 ? (
        <section aria-labelledby="acc-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
          <h2 id="acc-titulo" className="text-base font-semibold text-ap-ink">
            ¿A quiénes conviene escribirles ahora?
          </h2>
          <ul className="grid gap-x-8 gap-y-5 md:grid-cols-2">
            {suggested.map((segment) => {
              const info = SEGMENT_INFO[segment.code]
              return (
                <li key={segment.code} className="flex flex-col items-start gap-1.5 border-l-2 border-ap-line-strong pl-4">
                  <p className="ap-index">{segment.label}</p>
                  <p className="font-semibold text-ap-ink">{info.suggestion?.title}</p>
                  <p className="text-sm text-ap-ink">
                    {info.suggestion?.text} <span className="text-ap-muted">({pluralize(segment.customers, 'cliente', 'clientes')})</span>
                  </p>
                  <div className="flex flex-wrap items-center gap-2">
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

      <section aria-labelledby="cli-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="cli-titulo" className="text-base font-semibold text-ap-ink">
            {selected ? `Clientes: ${SEGMENT_INFO[selected].label}` : 'Todos los clientes'}
          </h2>
          {selected ? (
            <Button variant="outline" className="h-11 px-4" onClick={() => onSelect(undefined)}>
              Quitar filtro
            </Button>
          ) : null}
        </div>
        {report.customers.length === 0 ? (
          <EmptyState icon={<UsersIcon />} title="Ningún cliente en este segmento" description="Cuando alguien encaje en este grupo, aparecerá aquí. Quita el filtro para ver a todos.">
            {selected ? (
              <Button variant="outline" className="h-11 px-4" onClick={() => onSelect(undefined)}>
                Ver todos los clientes
              </Button>
            ) : null}
          </EmptyState>
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
