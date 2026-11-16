import { ReceiptTextIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { formatCOP, formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { SelectField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ServerPager } from '@/features/portal/components/server-pager'
import { formatDateTime, humanizeCode } from '@/features/portal/lib/labels'
import { getAdminConsoleService } from '../api/admin-console-service'
import type { AdminOrderRow } from '../api/console-types'
import { AdminBoundary, AdminPage } from '../components/admin-kit'
import { KeyValues, ListMeta, Pill, QuerySearch, SheetBlock } from '../components/console-kit'
import { useUrlFilters } from '../lib/use-url-filters'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { DECISION, PROVIDER_LABEL, paymentStatusLabel, stateLabel } from '../lib/console-labels'

const PAGE_SIZE = 25
const STATUS_OPTIONS = ['AWAITING_PAYMENT', 'PENDING', 'CONFIRMED', 'PREPARING', 'SHIPPED', 'DELIVERED', 'CANCELLED'].map((value) => ({ value, label: stateLabel(value).label }))

function OrderSheet({ orderId, onClose }: { orderId: string; onClose: () => void }) {
  const detail = useAsync(`${ADMIN_CACHE_PREFIX}order:${orderId || 'none'}`, async () => (orderId ? getAdminConsoleService().order(orderId) : undefined))
  const order = detail.data?.order
  return (
    <Sheet open={orderId !== ''} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-2xl">
        <SheetHeader className="border-b border-ap-line p-4 pr-16">
          <p className="ap-index">Pedido</p>
          <SheetTitle className="text-block-title text-ap-ink">{order ? `Pedido ${order.orderNumber}` : 'Cargando…'}</SheetTitle>
          <SheetDescription>{order ? `${order.customerEmail ?? 'Sin cuenta'} · ${formatDateTime(order.placedAt)}` : 'Detalle del pedido'}</SheetDescription>
        </SheetHeader>
        <div className="flex flex-col gap-5 overflow-y-auto p-4">
          <AdminBoundary result={detail} what="el pedido" skeleton={<ListSkeleton rows={6} label="Cargando el pedido" />}>
            {(data) =>
              data ? (
                <>
                  <KeyValues
                    items={[
                      ['Estado', <Pill key="s" value={stateLabel(data.order.status)} />],
                      ['Total', <span key="t" className="ap-price">{formatCOP(data.order.total)}</span>],
                      ['Subtotal', formatCOP(data.subtotal)],
                      ['Descuentos', formatCOP(data.discountTotal)],
                      ['Envío', formatCOP(data.shippingTotal)],
                      ['Canal', data.order.channel ?? '—'],
                      ...(data.cancelledAt ? ([['Cancelado', `${formatDateTime(data.cancelledAt)} · ${humanizeCode(data.cancellationReason ?? 'sin motivo')}`]] as [string, string][]) : []),
                      ['ID', data.order.id, true],
                    ]}
                  />
                  <SheetBlock title={`Productos (${data.lines.length})`}>
                    <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                      {data.lines.map((line, index) => (
                        <li key={`${line.productId}-${index}`} className="flex items-start justify-between gap-3 py-2">
                          <span className="min-w-0">
                            <span className="block font-semibold text-ap-ink">{line.name}</span>
                            <span className="ap-ref text-ap-muted">
                              {line.sku ?? 'Sin SKU'} · {line.storeName ?? '—'}
                            </span>
                          </span>
                          <span className="ap-num shrink-0 text-right text-ap-ink">
                            {formatNumber(line.units)} × {formatCOP(line.unitPrice)}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </SheetBlock>
                  <SheetBlock title="Ventas por almacén">
                    <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                      {data.sales.map((sale) => (
                        <li key={sale.id} className="flex items-center justify-between gap-3 py-2">
                          <span className="min-w-0">
                            <span className="block font-semibold text-ap-ink">{sale.storeName}</span>
                            <span className="text-ap-muted">{sale.branchName}</span>
                          </span>
                          <span className="flex shrink-0 items-center gap-2">
                            <span className="ap-num text-ap-ink">{formatCOP(sale.total)}</span>
                            <Pill value={stateLabel(sale.status)} />
                          </span>
                        </li>
                      ))}
                    </ul>
                  </SheetBlock>
                  <SheetBlock title="Pagos">
                    {data.payments.length === 0 ? (
                      <p className="text-sm text-ap-muted">El pedido no tiene pagos.</p>
                    ) : (
                      <ul className="divide-y divide-ap-line border-y border-ap-line text-sm">
                        {data.payments.map((payment) => (
                          <li key={payment.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                            <span className="min-w-0">
                              <span className="block text-ap-ink">
                                {PROVIDER_LABEL[payment.provider] ?? payment.provider} · intento {payment.attempt}
                              </span>
                              <span className="ap-ref text-ap-muted">{formatDateTime(payment.createdAt)}</span>
                            </span>
                            <span className="flex items-center gap-2">
                              <span className="ap-num text-ap-ink">{formatCOP(payment.amount)}</span>
                              <Pill value={paymentStatusLabel(payment.status)} />
                              {payment.riskDecision === 'REVIEW' ? <Pill value={DECISION.REVIEW} /> : null}
                            </span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </SheetBlock>
                  {data.history.length > 0 ? (
                    <SheetBlock title="Historial">
                      <ol className="flex flex-col gap-2 text-sm">
                        {data.history.map((change, index) => (
                          <li key={`${change.changedAt}-${index}`} className="flex items-center justify-between gap-3 border-b border-ap-line pb-2">
                            <span className="text-ap-ink">
                              {change.fromStatus ? `${stateLabel(change.fromStatus).label} → ` : ''}
                              {stateLabel(change.toStatus).label}
                            </span>
                            <span className="ap-ref text-ap-muted">{formatDateTime(change.changedAt)}</span>
                          </li>
                        ))}
                      </ol>
                    </SheetBlock>
                  ) : null}
                </>
              ) : null
            }
          </AdminBoundary>
        </div>
      </SheetContent>
    </Sheet>
  )
}

/** `/admin/pedidos`: pedidos de todos los almacenes; el detalle muestra productos, ventas, pagos e historial. */
export default function AdminOrdersPage() {
  const { get, page, update } = useUrlFilters()
  const q = get('q')
  const status = get('estado')
  const selected = get('pedido')
  const list = useAsync(`${ADMIN_CACHE_PREFIX}orders:${q}:${status}:${page}`, () => getAdminConsoleService().orders({ q, status, page, size: PAGE_SIZE }))
  const activeFilters = [q, status].filter(Boolean).length
  const open = (row: AdminOrderRow) => update({ pedido: row.id }, false)

  const columns: DataColumn<AdminOrderRow>[] = [
    {
      id: 'pedido',
      header: 'Pedido',
      rowHeader: true,
      cell: (row) => (
        <div className="min-w-0">
          <p className="ap-ref font-semibold text-ap-ink">{row.orderNumber}</p>
          <p className="ap-ref text-ap-muted">{formatDateTime(row.placedAt)}</p>
        </div>
      ),
    },
    {
      id: 'cliente',
      header: 'Cliente',
      className: 'hidden md:table-cell',
      cell: (row) => (
        <div className="min-w-0">
          <p className="text-ap-ink">{row.customerName ?? '—'}</p>
          <p className="break-all text-sm text-ap-muted">{row.customerEmail ?? ''}</p>
        </div>
      ),
    },
    { id: 'estado', header: 'Estado', cell: (row) => <Pill value={stateLabel(row.status)} /> },
    { id: 'pago', header: 'Pago', className: 'hidden lg:table-cell', cell: (row) => <Pill value={paymentStatusLabel(row.paymentStatus)} /> },
    { id: 'total', header: 'Total', align: 'end', cell: (row) => <span className="ap-price">{formatCOP(row.total)}</span> },
    {
      id: 'ver',
      header: 'Detalle',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-11 px-3" aria-label={`Ver el pedido ${row.orderNumber}`} onClick={() => open(row)}>
          Ver
        </Button>
      ),
    },
  ]

  return (
    <AdminPage title="Pedidos" description="Pedidos de todos los almacenes, con sus productos, pagos y cambios de estado.">
      <form role="search" aria-label="Filtrar pedidos" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <QuerySearch label="Número, cliente o correo" placeholder="7001234 o correo" value={q} onApply={(value) => update({ q: value })} />
        <SelectField id="pedidos-estado" label="Estado" placeholder="Todos" options={STATUS_OPTIONS} value={status} onChange={(event) => update({ estado: event.target.value })} />
      </form>
      <ListMeta total={list.data?.total} singular="pedido" plural="pedidos" activeFilters={activeFilters} onClear={() => update({ q: '', estado: '' })} />
      <AdminBoundary result={list} what="los pedidos" keepPrevious skeleton={<ListSkeleton rows={8} label="Cargando los pedidos" />}>
        {(data) =>
          data.total === 0 ? (
            activeFilters > 0 ? (
              <EmptyState icon={<SearchXIcon />} title="Ningún pedido coincide" description="Prueba con otro número, correo o estado." />
            ) : (
              <EmptyState icon={<ReceiptTextIcon />} title="Aún no hay pedidos" description="Los pedidos del marketplace aparecerán aquí." />
            )
          ) : (
            <div className="flex flex-col gap-4">
              <DataTable caption="Pedidos de la plataforma" columns={columns} rows={data.items} getRowId={(row) => row.id} onRowClick={open} />
              <ServerPager page={data.page} totalPages={Math.max(1, data.totalPages)} total={data.total} singular="pedido" plural="pedidos" busy={list.status === 'loading'} onPageChange={(next) => update({ pagina: String(next) }, false)} />
            </div>
          )
        }
      </AdminBoundary>
      <OrderSheet orderId={selected} onClose={() => update({ pedido: '' }, false)} />
    </AdminPage>
  )
}
