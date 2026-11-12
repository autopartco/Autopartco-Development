import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { PackageSearchIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { getOrderService } from '../api/order-service'
import type { OrderDetail, OrderItem } from '../api/types'
import { InvoicePanel } from '../components/invoice-panel'
import { OrderStatusActions } from '../components/order-status-actions'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { ShipmentPanel } from '../components/shipment-panel'
import { StatusBadge } from '../components/status-badge'
import { ListSkeleton } from '../components/list-skeleton'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { formatDateTime, ORDER_STATUS_LABEL, ORDER_STATUS_TONE, paymentStatus } from '../lib/labels'

const ITEM_COLUMNS: DataColumn<OrderItem>[] = [
  {
    id: 'producto',
    header: 'Producto',
    rowHeader: true,
    cell: (row) => (
      <div>
        <p className="font-semibold text-ap-ink">{row.productName}</p>
        {row.partNumber ? <p className="ap-ref font-normal text-ap-muted">REF. {row.partNumber}</p> : null}
      </div>
    ),
  },
  { id: 'unidades', header: 'Unidades', align: 'end', cell: (row) => <span className="tabular-nums">{row.units}</span> },
  { id: 'precio', header: 'Precio unitario', align: 'end', cell: (row) => <span className="tabular-nums">{formatCOP(row.unitPrice)}</span> },
  { id: 'subtotal', header: 'Subtotal', align: 'end', cell: (row) => <span className="ap-price">{formatCOP(row.unitPrice * row.units)}</span> },
]

/** Bloque de un pedido: sección abierta con línea de estructura, sin tarjeta (§6.3, §8.3). */
function Section({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section aria-labelledby={id} className="flex flex-col gap-4 border-t border-ap-line-strong pt-4">
      <h2 id={id} className="text-base font-semibold text-ap-ink">
        {title}
      </h2>
      {children}
    </section>
  )
}

function OrderView({ order }: { order: OrderDetail }) {
  const payment = paymentStatus(order.paymentStatus)
  const address = order.deliveryAddress
  return (
    <div className="grid gap-8 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
      <div className="flex min-w-0 flex-col gap-6">
        <OrderStatusActions order={order} />
        <Section id="productos-pedido" title="Productos">
          <div className="flex flex-col gap-4">
            <DataTable caption={`Productos del pedido #${order.orderNumber}`} columns={ITEM_COLUMNS} rows={order.items} getRowId={(row) => row.inventoryId} />
            <dl className="ml-auto grid w-full max-w-xs grid-cols-[1fr_auto] gap-x-6 gap-y-1.5 text-sm">
              <dt className="text-ap-muted">Subtotal</dt>
              <dd className="ap-num">{formatCOP(order.subtotal)}</dd>
              <dt className="text-ap-muted">Envío</dt>
              <dd className="ap-num">{formatCOP(order.shippingCost)}</dd>
              <dt className="border-t border-ap-line pt-1.5 font-semibold text-ap-ink">Total</dt>
              <dd className="ap-price ap-num border-t border-ap-line pt-1.5 text-lg text-ap-ink">{formatCOP(order.total)}</dd>
            </dl>
          </div>
        </Section>
        <Section id="envio-pedido" title="Envío">
          <ShipmentPanel order={order} />
        </Section>
      </div>

      <div className="flex min-w-0 flex-col gap-6">
        <Section id="cliente-pedido" title="Cliente y entrega">
          <dl className="flex flex-col gap-3 text-sm">
            <div>
              <dt className="text-ap-muted">Cliente</dt>
              <dd className="font-semibold">{order.customerName ?? 'Sin nombre registrado'}</dd>
            </div>
            <div>
              <dt className="text-ap-muted">Dirección de entrega</dt>
              <dd>
                {address ? (
                  <address className="not-italic">
                    {address.addressLine}
                    {address.addressComplement ? `, ${address.addressComplement}` : ''}
                    <br />
                    {address.city}, {address.department}
                    {address.postalCode ? ` · ${address.postalCode}` : ''}
                  </address>
                ) : (
                  'Sin dirección registrada'
                )}
              </dd>
            </div>
            <div>
              <dt className="text-ap-muted">Sucursal que despacha</dt>
              <dd className="font-semibold">{order.branchName}</dd>
            </div>
          </dl>
        </Section>
        <Section id="factura-pedido" title="Factura">
          <InvoicePanel order={order} />
        </Section>
        <Section id="pago-pedido" title="Pago">
          <dl className="flex flex-col gap-3 text-sm">
            <div>
              <dt className="text-ap-muted">Estado del pago</dt>
              <dd>
                <StatusBadge tone={payment.tone}>{payment.label}</StatusBadge>
              </dd>
            </div>
            <div>
              <dt className="text-ap-muted">Medio de pago</dt>
              <dd className="font-semibold">{order.paymentMethod ?? '—'}</dd>
            </div>
          </dl>
        </Section>
      </div>
    </div>
  )
}

/** `/portal/:storeId/pedidos/:saleId`: detalle del pedido, cambio de estado y envío. */
export default function PortalOrderDetailPage() {
  const { saleId = '' } = useParams()
  const { storeId } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const order = useStoreQuery(`order:${saleId}`, async (id) => (await getOrderService()).get(id, saleId))
  const data = order.status === 'success' ? order.data : undefined

  // Un 404 con causa clara: el pedido no es de este almacén o no existe.
  if (order.status === 'error' && order.error instanceof ApiError && order.error.status === 404) {
    return (
      <EmptyState icon={<PackageSearchIcon />} title="No encontramos ese pedido" description="Puede que el enlace esté incompleto o que el pedido sea de otro almacén.">
        <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/pedidos`} />}>
          Volver a pedidos
        </Button>
      </EmptyState>
    )
  }

  return (
    <>
      <title>{`Pedido ${data ? `#${data.orderNumber}` : ''} | Autopartco`}</title>
      <PageHeader
        crumbs={[{ label: 'Pedidos', to: `${base}/pedidos` }, { label: data ? `Pedido #${data.orderNumber}` : 'Pedido' }]}
        title={data ? `Pedido #${data.orderNumber}` : 'Pedido'}
        description={data ? `Recibido el ${formatDateTime(data.createdAt)}.` : undefined}
        meta={data ? <StatusBadge tone={ORDER_STATUS_TONE[data.status]}>{ORDER_STATUS_LABEL[data.status]}</StatusBadge> : undefined}
      />
      <QueryBoundary result={order} what="el pedido" skeleton={<ListSkeleton rows={4} label="Cargando el pedido" />}>
        {(value) => <OrderView order={value} />}
      </QueryBoundary>
    </>
  )
}
