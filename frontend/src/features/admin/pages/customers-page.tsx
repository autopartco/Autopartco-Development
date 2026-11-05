import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DownloadIcon, UsersIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import type { CustomerSegmentCode } from '@/features/portal/api/analytics-types'
import { QueryBoundary } from '@/features/portal/components/query-boundary'
import { ServerPager } from '@/features/portal/components/server-pager'
import { SEGMENT_CODES, SEGMENT_INFO, csvBlob, dateLabel, isSegmentCode } from '@/features/portal/lib/analytics'
import { saveFile } from '@/features/portal/lib/inventory'
import { formatDate } from '@/features/portal/lib/labels'
import { getAdminAnalyticsService } from '../api/admin-analytics-service'
import type { AdminCustomer, AdminCustomerReport } from '../api/analytics-types'
import { AuroraHeader } from '../components/aurora-header'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const PAGE_SIZE = 50
/** Tope de páginas al exportar (5.000 clientes): evita bucles si el servidor devuelve un total inconsistente. */
const MAX_EXPORT_PAGES = 100

const consent = (value: boolean) => (value ? 'Autoriza' : 'No autoriza')

const COLUMNS: DataColumn<AdminCustomer>[] = [
  {
    id: 'cliente',
    header: 'Cliente',
    rowHeader: true,
    sortValue: (row) => row.name,
    cell: (row) => (
      <div className="min-w-0">
        <p className="font-semibold">{row.name}</p>
        <p className="text-xs break-all text-muted-foreground">{row.email}</p>
      </div>
    ),
  },
  { id: 'segmento', header: 'Segmento', sortValue: (row) => SEGMENT_INFO[row.segment].label, cell: (row) => SEGMENT_INFO[row.segment].label },
  { id: 'pedidos', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => formatNumber(row.orders) },
  { id: 'almacenes', header: 'Almacenes', align: 'end', sortValue: (row) => row.stores, cell: (row) => formatNumber(row.stores) },
  { id: 'compras', header: 'Compras', align: 'end', sortValue: (row) => row.revenue, cell: (row) => formatCOP(row.revenue) },
  { id: 'ticket', header: 'Ticket promedio', align: 'end', sortValue: (row) => row.averageTicket, cell: (row) => formatCOP(row.averageTicket) },
  {
    id: 'ultimo',
    header: 'Último pedido',
    sortValue: (row) => row.daysSinceLastOrder,
    cell: (row) => (
      <div>
        <p>{formatDate(row.lastOrderAt)}</p>
        <p className="text-xs text-muted-foreground">{row.daysSinceLastOrder <= 0 ? 'Hoy' : `Hace ${formatNumber(row.daysSinceLastOrder)} ${row.daysSinceLastOrder === 1 ? 'día' : 'días'}`}</p>
      </div>
    ),
  },
  { id: 'correo', header: 'Correo comercial', sortValue: (row) => Number(row.marketingEmail), cell: (row) => consent(row.marketingEmail) },
  { id: 'whatsapp', header: 'WhatsApp comercial', sortValue: (row) => Number(row.marketingWhatsapp), cell: (row) => consent(row.marketingWhatsapp) },
]

function Skeletons() {
  return (
    <div role="status">
      <span className="sr-only">Cargando los clientes…</span>
      <div aria-hidden className="flex flex-col gap-4">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {Array.from({ length: 5 }, (_, index) => (
            <Skeleton key={index} className="h-24" />
          ))}
        </div>
        <Skeleton className="h-64" />
      </div>
    </div>
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
    <div className="flex flex-col gap-6">
      <section aria-labelledby="adm-seg" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h2 id="adm-seg" className="text-lg font-semibold tracking-tight">
            Segmentos
          </h2>
          <p className="text-sm text-muted-foreground">
            Últimos {formatNumber(report.windowDays)} días, al {dateLabel(report.asOf)}. Toca un segmento para filtrar.
          </p>
        </div>
        <ul className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
          {SEGMENT_CODES.map((code) => {
            const item = report.segments.find((candidate) => candidate.code === code)
            const active = segment === code
            return (
              <li key={code}>
                <button
                  type="button"
                  aria-pressed={active}
                  onClick={() => onSegment(active ? undefined : code)}
                  className={cn(
                    'flex min-h-11 w-full flex-col items-start gap-1 rounded-xl bg-card p-4 text-left ring-1 transition-[box-shadow,background-color] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring',
                    active ? 'bg-concrete ring-2 ring-foreground' : 'ring-foreground/10 hover:ring-foreground/30',
                  )}
                >
                  <span className="text-sm font-semibold">{item?.label ?? SEGMENT_INFO[code].label}</span>
                  <span className="text-3xl leading-none font-extrabold tracking-[-0.035em] tabular-nums">{formatNumber(item?.customers ?? 0)}</span>
                  <span className="text-sm text-muted-foreground">{formatCOP(item?.revenue ?? 0)} en compras</span>
                </button>
              </li>
            )
          })}
        </ul>
      </section>

      <section aria-labelledby="adm-cli" className="flex flex-col gap-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 id="adm-cli" className="text-lg font-semibold tracking-tight">
            {segment ? SEGMENT_INFO[segment].label : 'Todos los clientes'}
          </h2>
          <div className="flex flex-wrap gap-2">
            {segment ? (
              <Button variant="outline" className="h-11 px-4" onClick={() => onSegment(undefined)}>
                Quitar filtro
              </Button>
            ) : null}
            <Button variant="outline" className="h-11 px-4" disabled={exporting} onClick={() => void exportCsv()}>
              <DownloadIcon aria-hidden />
              {exporting ? 'Preparando archivo…' : 'Exportar CSV con autorización de correo'}
            </Button>
          </div>
        </div>
        <p className="max-w-prose text-sm text-muted-foreground">
          El archivo incluye solo a quienes autorizaron recibir correos comerciales. Úsalo únicamente para los fines que la persona aceptó (Ley 1581 de 2012).
        </p>
        {report.page.items.length === 0 ? (
          <EmptyState icon={<UsersIcon />} title="Ningún cliente en este segmento" description="Cuando alguien encaje en este grupo, aparecerá aquí." />
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
      </section>
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
    <main>
      <title>Clientes frecuentes | Administración | Autopartco</title>
      <AuroraHeader
        label="Inteligencia comercial"
        title="Clientes frecuentes"
        description="Quién compra en Autopartco y con qué frecuencia, para decidir a quién escribirle y con qué oferta."
      />
      <div className="mx-auto w-full max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <QueryBoundary result={result} what="los clientes" keepPrevious skeleton={<Skeletons />}>
          {(report) => (
            <CustomersView
              report={report}
              segment={segment}
              onSegment={(code) => update({ segmento: code ?? '', pagina: '' })}
              onPage={(next) => update({ pagina: next > 1 ? String(next) : '' })}
              busy={result.status === 'loading'}
            />
          )}
        </QueryBoundary>
      </div>
    </main>
  )
}
