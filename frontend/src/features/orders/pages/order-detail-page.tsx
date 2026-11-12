import { useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { Link, useParams } from 'react-router'
import { ClockIcon, FileDownIcon, InfoIcon, PackageSearchIcon, ReceiptTextIcon, StoreIcon } from 'lucide-react'
import { getCheckoutService } from '@/features/checkout/api/checkout-service'
import { formatPaymentDeadline, isSafeCheckoutUrl } from '@/features/checkout/lib/order-payment'
import { orderResultPath } from '@/features/checkout/lib/paths'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatOrderDate } from '@/features/checkout/lib/labels'
import { ProductReviewAction, StoreReviewAction } from '@/features/reviews/components/order-review-actions'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { openHtmlInNewTab, saveBlob } from '@/lib/download'
import { getOrdersService } from '../api/orders-service'
import { SaleTracking } from '../components/sale-tracking'
import { buyerOrderStatus, isUnpaidOrder, orderStatus, ordersPath } from '../lib/labels'

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/** Factura en PDF que adjuntó el almacén. */
function InvoiceDownload({ orderId, saleId, fileName }: { orderId: string; saleId: string; fileName: string }) {
  const [busy, setBusy] = useState(false)
  async function download() {
    setBusy(true)
    try {
      const service = await getOrdersService()
      saveBlob(await service.invoice(orderId, saleId), fileName)
    } catch (error) {
      toast.error(errorMessage(error, 'No pudimos descargar la factura. Inténtalo de nuevo.'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <Button variant="outline" size="sm" className="h-11 w-fit sm:h-9" disabled={busy} onClick={() => void download()}>
      <FileDownIcon aria-hidden />
      {busy ? 'Descargando…' : 'Descargar factura'}
      <span className="sr-only"> ({fileName})</span>
    </Button>
  )
}

/** Comprobante de compra imprimible (no es la factura). */
function ReceiptButton({ orderId }: { orderId: string }) {
  const [busy, setBusy] = useState(false)
  async function open() {
    setBusy(true)
    try {
      const service = await getOrdersService()
      await openHtmlInNewTab(() => service.receipt(orderId))
    } catch (error) {
      toast.error(errorMessage(error, 'No pudimos abrir el comprobante. Inténtalo de nuevo.'))
    } finally {
      setBusy(false)
    }
  }
  return (
    <Button variant="outline" disabled={busy} onClick={() => void open()}>
      <ReceiptTextIcon aria-hidden />
      {busy ? 'Abriendo…' : 'Ver comprobante de compra'}
    </Button>
  )
}

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
            <Button nativeButton={false} render={<Link to={ordersPath()} />}>
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
        <span className="sr-only" role="status">
          Cargando el pedido
        </span>
        <div className="flex flex-col gap-4" aria-hidden>
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-9 w-2/3 max-w-xl" />
          <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
            <Skeleton className="h-64 w-full" />
            <Skeleton className="h-64 w-full rounded-xl" />
          </div>
        </div>
      </Container>
    )
  }

  const status = buyerOrderStatus(order.status)
  const unpaid = isUnpaidOrder(order.status)
  return (
    <Container className="py-6 sm:py-8">
      <title>{`Pedido #${order.orderNumber} | Autopartco`}</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Mis pedidos', to: ordersPath() }, { label: `Pedido #${order.orderNumber}` }]}
        label={
          <>
            Realizado el <time dateTime={order.createdAt}>{formatOrderDate(order.createdAt)}</time>
          </>
        }
        title={`Pedido #${order.orderNumber}`}
      >
        <Badge variant={status.tone} className="h-auto min-h-6 px-2 py-0.5 text-sm whitespace-normal">
          <span className="sr-only">Estado: </span>
          {status.label}
        </Badge>
      </PageIntro>

      {unpaid ? <UnpaidOrderNotice orderId={order.orderId} orderStatus={order.status} /> : null}

      <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex flex-col gap-4">
          <h2 className="text-block-title text-ap-ink">Tiendas y envíos</h2>
          <ul className="flex flex-col gap-10">
            {order.sales.map((sale) => {
              const saleStatus = orderStatus(sale.status)
              return (
                <li key={sale.saleId}>
                  <section aria-label={`${sale.storeName}, ${sale.branchName}`} className="flex flex-col">
                    <header className="flex flex-wrap items-start justify-between gap-2 border-b border-ap-graphite pb-2">
                      <div className="flex min-w-0 items-start gap-2.5">
                        <StoreIcon className="mt-0.5 size-5 shrink-0 text-ap-muted" aria-hidden />
                        <div className="min-w-0">
                          <h3 className="font-semibold text-ap-ink">{sale.storeName}</h3>
                          <p className="text-sm text-ap-muted">{/^sucursal\b/i.test(sale.branchName) ? sale.branchName : `Sucursal ${sale.branchName}`}</p>
                        </div>
                      </div>
                      {unpaid ? null : (
                        <Badge variant={saleStatus.tone}>
                          <span className="sr-only">Estado de la venta: </span>
                          {saleStatus.label}
                        </Badge>
                      )}
                    </header>

                    <ul className="flex flex-col divide-y divide-ap-line">
                      {sale.items.map((item) => (
                        <li key={item.productId} className="flex justify-between gap-3 py-3">
                          <div className="min-w-0">
                            {item.partNumber ? <p className="ap-ref text-xs text-ap-muted">REF. {item.partNumber}</p> : null}
                            <p className="text-sm font-medium text-ap-ink">{item.name}</p>
                            <p className="font-mono text-xs text-ap-muted tabular-nums">
                              {item.quantity} × {formatCOP(item.unitPrice)}
                            </p>
                            {sale.status === 'DELIVERED' ? (
                              <div className="mt-2 empty:hidden">
                                <ProductReviewAction orderId={order.orderId} orderNumber={order.orderNumber} productId={item.productId} productName={item.name} />
                              </div>
                            ) : null}
                          </div>
                          <p className="ap-price ap-num shrink-0 text-sm text-ap-ink">{formatCOP(item.lineTotal)}</p>
                        </li>
                      ))}
                    </ul>

                    <dl className="flex flex-col gap-1 border-t border-ap-line pt-3 text-sm">
                      <Row label="Subtotal" value={formatCOP(sale.subtotal)} />
                      <Row label="Envío" value={sale.shippingCost > 0 ? formatCOP(sale.shippingCost) : 'Sin costo'} />
                      <Row label="Total de esta tienda" value={formatCOP(sale.total)} strong />
                    </dl>

                    {sale.invoice ? (
                      <div className="mt-3 flex flex-col gap-1">
                        <InvoiceDownload orderId={order.orderId} saleId={sale.saleId} fileName={sale.invoice.fileName} />
                      </div>
                    ) : unpaid ? null : (
                      <p className="mt-3 text-xs text-ap-muted">La factura la emite el almacén; cuando la adjunte podrás descargarla aquí.</p>
                    )}

                    {sale.status === 'DELIVERED' ? (
                      <div className="mt-4 flex flex-col gap-1.5 border-t border-ap-line pt-4 empty:hidden">
                        <StoreReviewAction orderId={order.orderId} orderNumber={order.orderNumber} storeId={sale.storeId} storeName={sale.storeName} />
                      </div>
                    ) : null}

                    {unpaid ? null : (
                      <div className="mt-5 border-t border-ap-line pt-4">
                        <h4 className="ap-index mb-3">Seguimiento del envío</h4>
                        <SaleTracking shipment={sale.shipment} />
                      </div>
                    )}
                  </section>
                </li>
              )
            })}
          </ul>
        </div>

        <aside className="flex flex-col gap-5 rounded-xl border border-ap-line bg-ap-white p-5" aria-label="Pago y entrega">
          <section className="flex flex-col gap-3">
            <h2 className="text-block-title text-ap-ink">Pago</h2>
            <p className="ap-price text-2xl text-ap-ink">{formatCOP(order.total)}</p>
            <dl className="flex flex-col divide-y divide-ap-line border-y border-ap-line text-sm">
              <Row label="Subtotal" value={formatCOP(order.subtotal)} spaced />
              <Row label="Envío" value={order.shippingTotal > 0 ? formatCOP(order.shippingTotal) : 'Sin costo'} spaced />
              <Row label="Método" value={order.payment.methodName} spaced />
              <Row label="Fecha" value={formatOrderDate(order.payment.paidAt ?? order.createdAt)} spaced />
              <Row label="Referencia" value={<span className="ap-ref break-all">{order.payment.reference}</span>} spaced />
            </dl>
          </section>

          <section className="flex flex-col gap-2 border-t border-ap-line pt-5">
            <h2 className="text-block-title text-ap-ink">Entrega en</h2>
            <address className="text-sm not-italic text-ap-ink">
              {order.deliveryAddress.addressLine}
              {order.deliveryAddress.addressComplement ? `, ${order.deliveryAddress.addressComplement}` : ''}
              <br />
              {order.deliveryAddress.city}, {order.deliveryAddress.department}
              {order.deliveryAddress.postalCode ? ` · ${order.deliveryAddress.postalCode}` : ''}
            </address>
          </section>

          {unpaid ? null : <ReceiptButton orderId={order.orderId} />}
          <Button variant="outline" nativeButton={false} render={<Link to={ordersPath()} />}>
            Volver a mis pedidos
          </Button>
        </aside>
      </div>
    </Container>
  )
}

/**
 * Aviso de un pedido sin pago aprobado (§7). El texto de un `CANCELLED` depende del pago
 * (`GET /me/orders/{id}/payment`): vencido o aprobado tarde. Si la consulta falla, se usa el del vencimiento.
 */
function UnpaidOrderNotice({ orderId, orderStatus: code }: { orderId: string; orderStatus: string }) {
  const payment = useAsync(`portal:buyer-order-payment:${orderId}`, async () => (await getCheckoutService()).paymentStatus(orderId))
  const data = payment.status === 'success' ? payment.data : undefined
  const checkoutUrl = isSafeCheckoutUrl(data?.checkoutUrl) ? data.checkoutUrl : undefined

  if (code === 'AWAITING_PAYMENT') {
    const deadline = formatPaymentDeadline(data?.expiresAt ?? null)
    return (
      <div
        role="status"
        aria-busy={payment.status === 'loading' || undefined}
        className="mb-8 flex flex-col gap-3 rounded-xl border border-ap-warning/30 bg-ap-warning-soft p-4 text-sm text-ap-ink sm:flex-row sm:items-center sm:justify-between"
      >
        <div className="flex items-start gap-2.5">
          <ClockIcon className="mt-0.5 size-5 shrink-0 text-ap-warning" aria-hidden />
          <p>
            <strong className="font-semibold">Pago pendiente.</strong>{' '}
            {deadline ? `Completa el pago antes de las ${deadline} para confirmar tu pedido.` : 'Completa el pago para confirmar tu pedido.'}
          </p>
        </div>
        <div className="flex shrink-0 flex-col gap-2 sm:flex-row">
          <Button variant="outline" className="bg-ap-white" nativeButton={false} render={<Link to={orderResultPath(orderId)} />}>
            Ver estado del pago
          </Button>
          {checkoutUrl ? <Button onClick={() => window.location.assign(checkoutUrl)}>Ir a pagar</Button> : null}
        </div>
      </div>
    )
  }

  const late = data?.status === 'APPROVED'
  return (
    <div role="status" className="mb-8 flex items-start gap-2.5 rounded-xl border border-ap-line bg-ap-paper p-4 text-sm text-ap-ink">
      <InfoIcon className="mt-0.5 size-5 shrink-0 text-ap-muted" aria-hidden />
      <p>
        <strong className="font-semibold">Cancelado: pago no completado.</strong>{' '}
        {late
          ? 'Recibimos tu pago después de cancelar el pedido. Te contactaremos para devolverte el dinero.'
          : 'No recibimos el pago a tiempo y liberamos los repuestos. No se hizo ningún cobro.'}
      </p>
    </div>
  )
}

function Row({ label, value, strong, spaced }: { label: string; value: ReactNode; strong?: boolean; spaced?: boolean }) {
  return (
    <div className={`flex items-baseline justify-between gap-3 ${spaced ? 'py-2.5' : ''} ${strong ? 'font-semibold text-ap-ink' : ''}`}>
      <dt className={strong ? '' : 'shrink-0 text-ap-muted'}>{label}</dt>
      <dd className={`ap-num min-w-0 text-ap-ink ${strong ? 'ap-price' : ''}`}>{value}</dd>
    </div>
  )
}
