import type { ReactNode } from 'react'
import { Link, useLocation, useParams } from 'react-router'
import { m, useReducedMotion } from 'motion/react'
import { FlaskConicalIcon, PackageSearchIcon } from 'lucide-react'
import { Container } from '@/components/common/page-header'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatCOP } from '@/lib/format'
import type { BuyerOrderDto } from '../api/types'
import { OrderSalesList } from '../components/order-sales-list'
import { formatOrderDate, statusLabel } from '../lib/labels'
import { loadOrderResult, type OrderResultSummary } from '../lib/order-result-store'

const EASE = [0.22, 1, 0.36, 1] as const

function isOrderFor(value: unknown, id: string): value is BuyerOrderDto {
  return typeof value === 'object' && value !== null && (value as { orderId?: unknown }).orderId === id && Array.isArray((value as { sales?: unknown }).sales)
}

/** Palomita que se dibuja y una puntada del hilo rojo que cose el borde inferior. */
function ApprovedMark() {
  const reduced = useReducedMotion()
  return (
    <svg viewBox="0 0 64 64" className="size-16 shrink-0" fill="none" aria-hidden>
      <m.circle
        cx="32"
        cy="32"
        r="28"
        stroke="var(--color-thread-on-asphalt)"
        strokeWidth="3"
        initial={reduced ? false : { pathLength: 0, opacity: 0 }}
        animate={{ pathLength: 1, opacity: 1 }}
        transition={{ duration: 0.7, ease: EASE }}
      />
      <m.path
        d="M20 33.5 28.5 42 44.5 24"
        stroke="#fff"
        strokeWidth="4.5"
        strokeLinecap="round"
        strokeLinejoin="round"
        initial={reduced ? false : { pathLength: 0 }}
        animate={{ pathLength: 1 }}
        transition={{ duration: 0.45, delay: 0.55, ease: EASE }}
      />
    </svg>
  )
}

export default function OrderResultPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const reduced = useReducedMotion()

  const fromState = (location.state as { order?: unknown } | null)?.order
  const full = isOrderFor(fromState, id) ? fromState : undefined
  const summary: OrderResultSummary | undefined = full ?? loadOrderResult(id)

  if (!summary) {
    return (
      <Container className="py-6 sm:py-8">
        <title>Pedido no encontrado | Autopartco</title>
        <EmptyState
          icon={<PackageSearchIcon />}
          title="No encontramos el resumen de este pedido en este dispositivo"
          description="El resumen solo se guarda en el navegador donde hiciste la compra, mientras la pestaña sigue abierta."
        >
          <div className="flex flex-wrap gap-2">
            <Button className="h-11 px-5" nativeButton={false} render={<Link to="/buscar" />}>
              Buscar repuestos
            </Button>
            <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
              Ir al inicio
            </Button>
          </div>
        </EmptyState>
      </Container>
    )
  }

  return (
    <Container className="py-6 sm:py-8">
      <title>{`Pedido #${summary.orderNumber} | Autopartco`}</title>

      <m.section
        aria-labelledby="pedido-titulo"
        className="surface-asphalt relative overflow-hidden rounded-2xl px-6 py-8 sm:px-10 sm:py-10"
        initial={reduced ? false : { opacity: 0, y: 14 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.5, ease: EASE }}
      >
        <div className="flex flex-col gap-5 sm:flex-row sm:items-center sm:gap-8">
          <ApprovedMark />
          <div className="flex min-w-0 flex-col gap-2">
            <p className="label-mono text-asphalt-muted">Pedido confirmado</p>
            <h1 id="pedido-titulo" className="text-page-title">
              ¡Pago aprobado!
            </h1>
            <p className="text-lg text-asphalt-muted">
              Tu número de pedido es <strong className="font-mono font-bold text-white">#{summary.orderNumber}</strong>. Las tiendas ya recibieron tu compra.
            </p>
          </div>
        </div>
        <div className="stitch-line absolute inset-x-0 bottom-0" aria-hidden />
      </m.section>

      <div className="mt-8 grid items-start gap-8 lg:grid-cols-[1fr_22rem]">
        <div className="flex flex-col gap-4">
          <h2 className="text-block-title">Lo que compraste</h2>
          <OrderSalesList sales={summary.sales} />
        </div>

        <aside className="flex flex-col gap-4" aria-label="Detalle del pago y la entrega">
          <section className="flex flex-col gap-3 rounded-2xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07),0_18px_40px_-28px_rgb(23_27_32/0.35)]">
            <h2 className="text-block-title">Pago</h2>
            <p className="text-3xl font-extrabold tracking-tight tabular-nums">{formatCOP(summary.total)}</p>
            <dl className="flex flex-col gap-2 text-sm">
              <Row label="Estado" value={`${statusLabel(summary.payment.status)} · pedido ${statusLabel(summary.status).toLowerCase()}`} />
              <Row label="Método" value={summary.payment.methodName} />
              <Row label="Fecha" value={formatOrderDate(summary.payment.paidAt ?? summary.createdAt)} />
              <Row label="Referencia" value={<span className="label-mono break-all normal-case">{summary.payment.reference}</span>} />
            </dl>
            <p className="flex items-start gap-2 rounded-lg bg-concrete px-3 py-2.5 text-xs text-muted-foreground">
              <FlaskConicalIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
              <span>Pago de demostración: no se realizó ningún cobro real.</span>
            </p>
          </section>

          {full ? (
            <section className="flex flex-col gap-2 rounded-2xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07),0_18px_40px_-28px_rgb(23_27_32/0.35)]">
              <h2 className="text-block-title">Entrega en</h2>
              <address className="text-sm not-italic text-foreground/85">
                {full.deliveryAddress.addressLine}
                {full.deliveryAddress.addressComplement ? `, ${full.deliveryAddress.addressComplement}` : ''}
                <br />
                {full.deliveryAddress.city}, {full.deliveryAddress.department}
                {full.deliveryAddress.postalCode ? ` · ${full.deliveryAddress.postalCode}` : ''}
              </address>
            </section>
          ) : (
            <p className="text-xs text-muted-foreground">Por tu privacidad, la dirección de entrega no se guarda en este dispositivo y solo se muestra justo después de pagar.</p>
          )}

          <div className="flex flex-wrap gap-2">
            <Button className="h-11 px-5" nativeButton={false} render={<Link to="/mis-pedidos" />}>
              Ver mis pedidos
            </Button>
            <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/buscar" />}>
              Seguir comprando
            </Button>
            <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
              Ir al inicio
            </Button>
          </div>
        </aside>
      </div>
    </Container>
  )
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 sm:flex-row sm:justify-between sm:gap-3">
      <dt className="text-muted-foreground">{label}</dt>
      <dd className="min-w-0 sm:text-right">{value}</dd>
    </div>
  )
}
