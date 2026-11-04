import type { ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { PackageSearchIcon, StoreIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatOrderDate } from '@/features/checkout/lib/labels'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { getOrdersService } from '../api/orders-service'
import { SaleTracking } from '../components/sale-tracking'
import { orderStatus, ordersPath } from '../lib/labels'

const CARD = 'flex flex-col gap-4 rounded-2xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07),0_18px_40px_-28px_rgb(23_27_32/0.35)]'

export default function OrderDetailPage() {
  const { id = '' } = useParams()
  // Prefijo `portal:` para que se descarte al cerrar sesión (ver RequireSession).
  const result = useAsync(`portal:buyer-order:${id}`, () => getOrdersService().then((service) => service.get(id)))
  const order = result.data

  if (result.status === 'error') {
    const notFound = result.error instanceof ApiError && result.error.status === 404
    return (
      <Container className="py-6 sm:py-8">
        <title>{notFound ? 'Pedido no encontrado | Autopartco' : 'Mis pedidos | Autopartco'}</title>
        {notFound ? (
          <EmptyState icon={<PackageSearchIcon />} title="No encontramos este pedido" description="Puede que el enlace esté incompleto o que el pedido no sea de tu cuenta.">
            <Button className="h-11 px-5" nativeButton={false} render={<Link to={ordersPath()} />}>
              Ver mis pedidos
            </Button>
          </EmptyState>
        ) : (
          <ErrorState title="No pudimos cargar este pedido" onRetry={result.retry} />
        )}
      </Container>
    )
  }

  if (!order || result.isStale) {
    return (
      <Container className="py-6 sm:py-8">
        <div role="status" aria-busy className="flex flex-col gap-4">
          <span className="sr-only">Cargando el pedido</span>
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-9 w-2/3 max-w-xl" />
          <Skeleton className="h-64 w-full rounded-2xl" />
        </div>
      </Container>
    )
  }

  const status = orderStatus(order.status)
  return (
    <Container className="py-6 sm:py-8">
      <title>{`Pedido #${order.orderNumber} | Autopartco`}</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Mis pedidos', to: ordersPath() }, { label: `Pedido #${order.orderNumber}` }]}
        label={`Realizado el ${formatOrderDate(order.createdAt)}`}
        title={`Pedido #${order.orderNumber}`}
      >
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      </PageIntro>

      <div className="grid items-start gap-8 lg:grid-cols-[1fr_22rem]">
        <div className="flex flex-col gap-4">
          <h2 className="text-block-title">Tiendas y envíos</h2>
          <ul className="flex flex-col gap-4">
            {order.sales.map((sale) => {
              const saleStatus = orderStatus(sale.status)
              return (
                <li key={sale.saleId}>
                  <section aria-label={`${sale.storeName}, ${sale.branchName}`} className={CARD}>
                    <header className="flex flex-wrap items-start justify-between gap-2">
                      <div className="flex min-w-0 items-start gap-2.5">
                        <StoreIcon className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden />
                        <div className="min-w-0">
                          <h3 className="font-semibold">{sale.storeName}</h3>
                          <p className="text-sm text-muted-foreground">{sale.branchName}</p>
                        </div>
                      </div>
                      <StatusBadge tone={saleStatus.tone}>{saleStatus.label}</StatusBadge>
                    </header>

                    <ul className="flex flex-col divide-y divide-border">
                      {sale.items.map((item) => (
                        <li key={item.productId} className="flex justify-between gap-3 py-3 first:pt-0 last:pb-0">
                          <div className="min-w-0">
                            <p className="text-sm font-medium">{item.name}</p>
                            <p className="text-sm text-muted-foreground">
                              {item.quantity} × {formatCOP(item.unitPrice)}
                              {item.partNumber ? <span className="label-mono ml-2">Ref. {item.partNumber}</span> : null}
                            </p>
                          </div>
                          <p className="shrink-0 text-sm font-semibold tabular-nums">{formatCOP(item.lineTotal)}</p>
                        </li>
                      ))}
                    </ul>

                    <dl className="flex flex-col gap-1 border-t border-border pt-3 text-sm">
                      <Row label="Subtotal" value={formatCOP(sale.subtotal)} />
                      <Row label="Envío" value={sale.shippingCost > 0 ? formatCOP(sale.shippingCost) : 'Sin costo'} />
                      <Row label="Total de esta tienda" value={formatCOP(sale.total)} strong />
                    </dl>

                    <div className="border-t border-border pt-4">
                      <h4 className="mb-3 text-sm font-semibold">Envío</h4>
                      <SaleTracking shipment={sale.shipment} />
                    </div>
                  </section>
                </li>
              )
            })}
          </ul>
        </div>

        <aside className="flex flex-col gap-4" aria-label="Pago y entrega">
          <section className={CARD}>
            <h2 className="text-block-title">Pago</h2>
            <p className="text-3xl font-extrabold tracking-tight tabular-nums">{formatCOP(order.total)}</p>
            <dl className="flex flex-col gap-1 text-sm">
              <Row label="Subtotal" value={formatCOP(order.subtotal)} />
              <Row label="Envío" value={order.shippingTotal > 0 ? formatCOP(order.shippingTotal) : 'Sin costo'} />
              <Row label="Método" value={order.payment.methodName} />
              <Row label="Fecha" value={formatOrderDate(order.payment.paidAt ?? order.createdAt)} />
              <Row label="Referencia" value={<span className="label-mono break-all normal-case">{order.payment.reference}</span>} />
            </dl>
          </section>

          <section className={CARD}>
            <h2 className="text-block-title">Entrega en</h2>
            <address className="text-sm not-italic text-foreground/85">
              {order.deliveryAddress.addressLine}
              {order.deliveryAddress.addressComplement ? `, ${order.deliveryAddress.addressComplement}` : ''}
              <br />
              {order.deliveryAddress.city}, {order.deliveryAddress.department}
              {order.deliveryAddress.postalCode ? ` · ${order.deliveryAddress.postalCode}` : ''}
            </address>
          </section>

          <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to={ordersPath()} />}>
            Volver a mis pedidos
          </Button>
        </aside>
      </div>
    </Container>
  )
}

function Row({ label, value, strong }: { label: string; value: ReactNode; strong?: boolean }) {
  return (
    <div className={`flex justify-between gap-3 ${strong ? 'font-semibold' : ''}`}>
      <dt className={strong ? '' : 'text-muted-foreground'}>{label}</dt>
      <dd className="min-w-0 text-right tabular-nums">{value}</dd>
    </div>
  )
}
