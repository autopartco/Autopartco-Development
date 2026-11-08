import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DownloadIcon, UsersIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatCOP, formatNumber, pluralize } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import type { CustomerSegmentCode } from '@/features/portal/api/analytics-types'
import { ChartSection, ChartSkeleton } from '@/features/portal/components/chart-section'
import { ServerPager } from '@/features/portal/components/server-pager'
import { CHART_BG } from '@/features/portal/lib/chart-palette'
import { SEGMENT_CODES, SEGMENT_INFO, csvBlob, dateLabel, isSegmentCode } from '@/features/portal/lib/analytics'
import { saveFile } from '@/features/portal/lib/inventory'
import { formatDate } from '@/features/portal/lib/labels'
import { getAdminAnalyticsService } from '../api/admin-analytics-service'
import type { AdminCustomer, AdminCustomerReport } from '../api/analytics-types'
import { AdminBoundary, AdminPage, AdminSection } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const PAGE_SIZE = 50
/** Tope de páginas al exportar (5.000 clientes): evita bucles si el servidor devuelve un total inconsistente. */
const MAX_EXPORT_PAGES = 100

const consent = (value: boolean) => (value ? 'Autoriza' : 'No autoriza')
const share = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 0 })

const COLUMNS: DataColumn<AdminCustomer>[] = [
  {
    id: 'cliente',
    header: 'Cliente',
    rowHeader: true,
    sortValue: (row) => row.name,
    cell: (row) => (
      <div className="min-w-0">
        <p className="font-semibold text-ap-ink">{row.name}</p>
        <p className="text-xs break-all text-ap-muted">{row.email}</p>
      </div>
    ),
  },
  { id: 'segmento', header: 'Segmento', sortValue: (row) => SEGMENT_INFO[row.segment].label, cell: (row) => SEGMENT_INFO[row.segment].label },
  { id: 'pedidos', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => <span className="ap-num">{formatNumber(row.orders)}</span> },
  { id: 'almacenes', header: 'Almacenes', align: 'end', sortValue: (row) => row.stores, cell: (row) => <span className="ap-num">{formatNumber(row.stores)}</span> },
  { id: 'compras', header: 'Compras', align: 'end', sortValue: (row) => row.revenue, cell: (row) => <span className="ap-num">{formatCOP(row.revenue)}</span> },
  { id: 'ticket', header: 'Ticket promedio', align: 'end', sortValue: (row) => row.averageTicket, cell: (row) => <span className="ap-num">{formatCOP(row.averageTicket)}</span> },
  {
    id: 'ultimo',
    header: 'Último pedido',
    sortValue: (row) => row.daysSinceLastOrder,
    cell: (row) => (
      <div>
        <p className="ap-ref text-ap-ink">{formatDate(row.lastOrderAt)}</p>
        <p className="text-xs text-ap-muted">{row.daysSinceLastOrder <= 0 ? 'Hoy' : `Hace ${formatNumber(row.daysSinceLastOrder)} ${row.daysSinceLastOrder === 1 ? 'día' : 'días'}`}</p>
      </div>
    ),
  },
  { id: 'correo', header: 'Correo comercial', sortValue: (row) => Number(row.marketingEmail), cell: (row) => consent(row.marketingEmail) },
  { id: 'whatsapp', header: 'WhatsApp comercial', sortValue: (row) => Number(row.marketingWhatsapp), cell: (row) => consent(row.marketingWhatsapp) },
]

function Skeletons() {
  return (
    <div className="flex flex-col gap-8">
      <ChartSkeleton kind="list" label="Cargando los segmentos" />
      <ChartSkeleton kind="list" label="Cargando los clientes" />
    </div>
  )
}

/**
 * «¿Cómo se reparten los clientes?»: barras horizontales por segmento (§20.4 B) en grafito, con cifra y
 * participación en texto. Cada fila filtra la tabla; la seleccionada lleva la línea roja y «Filtrando».
 */
function SegmentBars({ report, selected, onSelect }: { report: AdminCustomerReport; selected: CustomerSegmentCode | undefined; onSelect: (code: CustomerSegmentCode | undefined) => void }) {
  const total = report.segments.reduce((sum, item) => sum + item.customers, 0)
  const max = Math.max(1, ...report.segments.map((item) => item.customers))
  return (
    <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
      {SEGMENT_CODES.map((code) => {
        const item = report.segments.find((candidate) => candidate.code === code)
        const customers = item?.customers ?? 0
        const active = selected === code
        return (
          <li key={code}>
            <button
              type="button"
              aria-pressed={active}
              onClick={() => onSelect(active ? undefined : code)}
              className={cn(
                'grid min-h-11 w-full gap-x-4 gap-y-1 border-l-2 py-3 pr-2 pl-3 text-left transition-colors duration-150 sm:grid-cols-[12rem_minmax(0,1fr)_10rem] sm:items-center',
                active ? 'border-ap-red bg-ap-red-tint' : 'border-transparent hover:bg-ap-paper',
              )}
            >
              <span className="flex flex-col">
                <span className="text-sm font-semibold text-ap-ink">
                  {item?.label ?? SEGMENT_INFO[code].label}
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
                  <span className="ml-1 text-xs text-ap-muted">{total > 0 ? share.format(customers / total) : '0 %'}</span>
                </span>
              </span>
              <span className="ap-num text-sm text-ap-muted sm:text-right">{formatCOP(item?.revenue ?? 0)} en compras</span>
            </button>
          </li>
        )
      })}
    </ul>
  )
}

/** Descarga todas las páginas del segmento y deja solo a quienes autorizaron correo comercial. */
async function exportAuthorized(segment: CustomerSegmentCode | undefined): Promise<{ rows: AdminCustomer[]; truncated: boolean }> {
  const service = await getAdminAnalyticsService()
  const rows: AdminCustomer[] = []
  let truncated = false
  for (let page = 1; ; page += 1) {
    const report = await service.customers({ segment, page, size: PAGE_SIZE })
    rows.push(...report.page.items.filter((customer) => customer.marketingEmail))
    if (page * PAGE_SIZE >= report.page.total || report.page.items.length === 0) break
    if (page >= MAX_EXPORT_PAGES) {
      truncated = true
      break
    }
  }
  return { rows, truncated }
}

/**
 * Clientes de la plataforma (§20.6). Pregunta: «¿a quiénes conviene escribirles?» → reparto por segmento
 * (que filtra) y la tabla con los valores exactos. La exportación respeta la autorización de cada persona.
 */
function CustomersView({
  report,
  segment,
  onSegment,
  onPage,
  busy,
}: {
  report: AdminCustomerReport
  segment: CustomerSegmentCode | undefined
  onSegment: (code: CustomerSegmentCode | undefined) => void
  onPage: (page: number) => void
  busy: boolean
}) {
  const [exporting, setExporting] = useState(false)
  const total = report.segments.reduce((sum, item) => sum + item.customers, 0)

  async function exportCsv() {
    setExporting(true)
    try {
      const { rows, truncated } = await exportAuthorized(segment)
      if (rows.length === 0) {
        toast.info('Nadie en esta selección autorizó recibir correos comerciales, así que no hay nada que exportar.')
        return
      }
      saveFile(
        csvBlob([
          ['Nombre', 'Correo', 'Segmento', 'Pedidos', 'Compras (COP)', 'Último pedido'],
          ...rows.map((row) => [row.name, row.email, SEGMENT_INFO[row.segment].label, row.orders, row.revenue, row.lastOrderAt.slice(0, 10)]),
        ]),
        `clientes-con-autorizacion-de-correo-${report.asOf}.csv`,
      )
      toast.success(`Exportamos ${formatNumber(rows.length)} ${rows.length === 1 ? 'cliente' : 'clientes'} que autorizaron correo comercial.${truncated ? ' El archivo incluye los primeros 5.000 clientes.' : ''}`)
    } catch {
      toast.error('No pudimos generar el archivo. Inténtalo de nuevo.')
    } finally {
      setExporting(false)
    }
  }

  if (total === 0 && !segment) {
    return <EmptyState icon={<UsersIcon />} title="Aún no hay clientes con pedidos" description={`Cuando los clientes compren, aparecerán aquí agrupados por frecuencia. Se analizan los últimos ${report.windowDays} días.`} />
  }

  return (
    <div className="flex flex-col gap-8">
      <ChartSection
        title="¿Cómo se reparten los clientes?"
        meta={`Últimos ${formatNumber(report.windowDays)} días, al ${dateLabel(report.asOf)} · clientes por segmento`}
        source={`${pluralize(total, 'cliente', 'clientes')} con pedidos en toda la plataforma. Elige un segmento para filtrar la tabla.`}
      >
        <SegmentBars report={report} selected={segment} onSelect={onSegment} />
      </ChartSection>

      <AdminSection
        id="adm-cli"
        title={segment ? `Clientes: ${SEGMENT_INFO[segment].label}` : 'Todos los clientes'}
        actions={
          <>
            {segment ? (
              <Button variant="outline" className="h-11 px-4" onClick={() => onSegment(undefined)}>
                Quitar filtro
              </Button>
            ) : null}
            <Button variant="outline" className="h-11 px-4" disabled={exporting} onClick={() => void exportCsv()}>
              <DownloadIcon aria-hidden />
              {exporting ? 'Preparando archivo…' : 'Exportar CSV con autorización de correo'}
            </Button>
          </>
        }
      >
        <p className="max-w-prose border-l-2 border-ap-line-strong py-1 pl-3 text-sm text-ap-ink">
          El archivo incluye solo a quienes autorizaron recibir correos comerciales. Úsalo únicamente para los fines que la persona aceptó (Ley 1581 de 2012).
        </p>
        {report.page.items.length === 0 ? (
          <EmptyState icon={<UsersIcon />} title="Ningún cliente en este segmento" description="Cuando alguien encaje en este grupo, aparecerá aquí. Quita el filtro para ver a todos.">
            {segment ? (
              <Button variant="outline" className="h-11 px-4" onClick={() => onSegment(undefined)}>
                Ver todos los clientes
              </Button>
            ) : null}
          </EmptyState>
        ) : (
          <>
            <DataTable caption="Clientes de la plataforma" columns={COLUMNS} rows={report.page.items} getRowId={(row) => row.personId} defaultSort={{ columnId: 'compras', direction: 'desc' }} />
            <ServerPager
              page={report.page.page}
              totalPages={Math.max(1, Math.ceil(report.page.total / report.page.size))}
              total={report.page.total}
              singular="cliente"
              plural="clientes"
              onPageChange={onPage}
              busy={busy}
            />
          </>
        )}
      </AdminSection>
    </div>
  )
}

/** `/admin/clientes`: segmentos de clientes de toda la plataforma y exportación con autorización de correo. */
export default function AdminCustomersPage() {
  const [params, setParams] = useSearchParams()
  const segmentParam = params.get('segmento')
  const segment = isSegmentCode(segmentParam) ? segmentParam : undefined
  const page = Math.max(1, Number.parseInt(params.get('pagina') ?? '1', 10) || 1)
  const result = useAsync(`${ADMIN_CACHE_PREFIX}analytics:customers:${segment ?? 'all'}:${page}`, async () =>
    (await getAdminAnalyticsService()).customers({ segment, page, size: PAGE_SIZE }),
  )

  function update(changes: { segmento?: string; pagina?: string }) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [name, value] of Object.entries(changes)) {
          if (value) next.set(name, value)
          else next.delete(name)
        }
        return next
      },
      { replace: true },
    )
  }

  return (
    <AdminPage title="Clientes frecuentes" description="Quién compra en Autopartco y con qué frecuencia, para decidir a quién escribirle y con qué oferta.">
      <AdminBoundary result={result} what="los clientes" keepPrevious skeleton={<Skeletons />}>
        {(report) => (
          <CustomersView
            report={report}
            segment={segment}
            onSegment={(code) => update({ segmento: code ?? '', pagina: '' })}
            onPage={(next) => update({ pagina: next > 1 ? String(next) : '' })}
            busy={result.status === 'loading'}
          />
        )}
      </AdminBoundary>
    </AdminPage>
  )
}
