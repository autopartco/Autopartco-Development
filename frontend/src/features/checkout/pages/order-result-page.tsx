import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { CircleCheckIcon, FlaskConicalIcon, PackageSearchIcon, ReceiptTextIcon, RotateCwIcon } from 'lucide-react'
import { Container } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { setSession } from '@/features/auth/session-store'
import { PaymentStatusIndicator, type PaymentIndicatorState } from '@/features/portal/components/payment-status-indicator'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { invalidateAsync } from '@/lib/use-async'
import { getCheckoutService } from '../api/checkout-service'
import type { BuyerOrderDto, OrderPaymentDto } from '../api/types'
import { OrderSalesList } from '../components/order-sales-list'
import { restoreCartLines } from '../lib/cart-source'
import { formatOrderDate, statusLabel } from '../lib/labels'
import {
  clearPendingOrderPayment,
  formatPaymentDeadline,
  isApiCode,
  isFinalPayment,
  isSafeCheckoutUrl,
  loadPendingOrderPayment,
  orderPaymentKind,
  safeTransactionId,
  type OrderPaymentKind,
} from '../lib/order-payment'
import { loadOrderResult, type OrderResultSummary } from '../lib/order-result-store'

/** Polling del estado (§6): cada 3 s al principio, luego más espaciado, durante 60 s como máximo. */
const POLL_TIMEOUT_MS = 60_000
const pollDelay = (attempt: number) => (attempt < 5 ? 3_000 : Math.min(3_000 * 1.5 ** (attempt - 4), 8_000))
/** Errores seguidos de red o del servidor que se toleran antes de mostrar el error. */
const MAX_FAILURES = 3

/** Correo público de soporte (`VITE_SUPPORT_EMAIL`); sin él no se inventa ningún destino. */
const SUPPORT_EMAIL = (import.meta.env.VITE_SUPPORT_EMAIL as string | undefined)?.trim()

function isOrderFor(value: unknown, id: string): value is BuyerOrderDto {
  return typeof value === 'object' && value !== null && (value as { orderId?: unknown }).orderId === id && Array.isArray((value as { sales?: unknown }).sales)
}

/**
 * `/pedido/:id/resultado`. Con el recibo de un pedido SIMULATED recién creado muestra la confirmación;
 * en cualquier otro caso (vuelta de Wompi, recarga) consulta el estado del pago al servidor. Nunca
 * decide el estado por los parámetros de la redirección: el `id` solo se envía a `sync`.
 */
export default function OrderResultPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const [params] = useSearchParams()
  const transactionId = safeTransactionId(params.get('id'))

  const fromState = (location.state as { order?: unknown } | null)?.order
  const full = isOrderFor(fromState, id) ? fromState : undefined
  const summary: OrderResultSummary | undefined = full ?? (transactionId ? undefined : loadOrderResult(id))

  if (summary) return <ReceiptView summary={summary} full={full} />
  return <PaymentStatusView key={id} orderId={id} transactionId={transactionId} />
}

// ------------------------------------------------------------------ estado del pago (WOMPI)

type Phase = 'checking' | 'settled' | 'timeout' | 'error' | 'notFound'

interface PaymentState {
  round: number
  phase: Phase
  payment?: OrderPaymentDto
  error?: unknown
}

/** Sincroniza una vez por ronda (si hay `id` y no hubo 429) y luego consulta el `GET` hasta un estado final o 60 s. */
function useOrderPayment(orderId: string, transactionId: string | undefined, round: number): PaymentState {
  const [state, setState] = useState<PaymentState>({ round, phase: 'checking' })
  const rateLimited = useRef(false)

  useEffect(() => {
    let active = true
    let timer: number | undefined
    const deadline = Date.now() + POLL_TIMEOUT_MS
    let attempt = 0
    let failures = 0

    const update = (patch: Partial<PaymentState>) => {
      if (!active) return
      setState((current) => ({ ...(current.round === round ? current : { phase: 'checking', payment: current.payment }), ...patch, round }))
    }
    /** `true` si el error termina el ciclo (pedido ajeno o sesión vencida). */
    const fatal = (error: unknown): boolean => {
      if (error instanceof ApiError && error.status === 401) {
        // RequireSession lleva a /ingresar y vuelve a esta misma ruta con sus parámetros.
        setSession(undefined)
        return true
      }
      if (isApiCode(error, 'ORDER_NOT_FOUND') || (error instanceof ApiError && error.status === 404 && error.code === undefined)) {
        update({ phase: 'notFound' })
        return true
      }
      return false
    }
    const received = (payment: OrderPaymentDto): boolean => {
      update({ payment, error: undefined })
      if (isFinalPayment(payment)) {
        update({ phase: 'settled' })
        return true
      }
      return false
    }

    async function poll() {
      try {
        const payment = await (await getCheckoutService()).paymentStatus(orderId)
        if (!active) return
        failures = 0
        if (received(payment)) return
      } catch (error) {
        if (!active || fatal(error)) return
        failures += 1
        if (failures >= MAX_FAILURES) {
          update({ phase: 'error', error })
          return
        }
      }
      const delay = pollDelay(attempt++)
      if (Date.now() + delay > deadline) {
        update({ phase: 'timeout' })
        return
      }
      timer = window.setTimeout(() => void poll(), delay)
    }

    async function start() {
      if (transactionId && !rateLimited.current) {
        try {
          const payment = await (await getCheckoutService()).syncPayment(orderId, transactionId)
          if (!active) return
          if (received(payment)) return
        } catch (error) {
          if (!active || fatal(error)) return
          // 429: se sigue solo con el GET. 404 de transacción, 409 de referencia, 400 o red: el GET dice el estado real.
          if (isApiCode(error, 'PAYMENT_SYNC_RATE_LIMITED')) rateLimited.current = true
        }
      }
      void poll()
    }

    void start()
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [orderId, transactionId, round])

  return state.round === round ? state : { round, phase: 'checking', payment: state.payment }
}

const INDICATOR: Record<OrderPaymentKind, PaymentIndicatorState> = {
  pending: 'pending',
  approved: 'approved',
  late: 'waiting',
  rejected: 'declined',
  voided: 'declined',
  expired: 'declined',
  closed: 'declined',
}

interface StatusCopy {
  title: string
  description: string
  announce: string
}

function copyOf(kind: OrderPaymentKind, payment: OrderPaymentDto, orderNumber: number | undefined, timedOut: boolean): StatusCopy {
  const deadline = formatPaymentDeadline(payment.expiresAt)
  switch (kind) {
    case 'approved':
      return {
        title: '¡Pago aprobado!',
        description: `Tu pedido${orderNumber ? ` #${orderNumber}` : ''} está confirmado. Los almacenes ya lo están preparando.`,
        announce: 'Pago aprobado.',
      }
    case 'late':
      return {
        title: 'Recibimos tu pago tarde',
        description: 'El pedido ya se había cancelado. Te contactaremos para devolverte el dinero.',
        announce: 'Pago recibido después de cancelar el pedido.',
      }
    case 'rejected':
      return {
        title: 'El pago fue rechazado',
        description: `No se hizo ningún cobro.${deadline ? ` Reservamos tus repuestos hasta las ${deadline}.` : ''}`,
        announce: 'Pago rechazado.',
      }
    case 'voided':
      return {
        title: 'El pago fue anulado',
        description: `No se hizo ningún cobro.${deadline ? ` Puedes intentarlo de nuevo antes de las ${deadline}.` : ''}`,
        announce: 'Pago anulado.',
      }
    case 'expired':
      return {
        title: 'Se venció el tiempo para pagar',
        description: 'No recibimos el pago a tiempo y liberamos los repuestos. Si ya pagaste, te contactaremos para devolverte el dinero.',
        announce: 'Se venció el tiempo para pagar.',
      }
    case 'closed':
      return {
        title: 'Este pedido se canceló',
        description: 'No recibimos el pago a tiempo y liberamos los repuestos. No se hizo ningún cobro.',
        announce: 'Pedido cancelado.',
      }
    case 'pending':
      return {
        title: 'Estamos confirmando tu pago',
        description: timedOut
          ? 'Wompi aún no confirma el pago. Con PSE o Nequi puede tardar unos minutos; no hace falta pagar de nuevo.'
          : 'Wompi aún no confirma el pago. Con PSE o Nequi puede tardar unos minutos.',
        announce: timedOut ? 'La confirmación del pago está tardando.' : 'Verificando el pago.',
      }
  }
}

const BUTTON = 'w-full sm:w-auto'

function PaymentStatusView({ orderId, transactionId }: { orderId: string; transactionId?: string }) {
  const navigate = useNavigate()
  const [round, setRound] = useState(0)
  const state = useOrderPayment(orderId, transactionId, round)
  const [pending] = useState(() => loadPendingOrderPayment(orderId))
  const { payment, phase } = state
  const kind = payment ? orderPaymentKind(payment) : undefined

  // Un estado final cambia el pedido: "Mis pedidos" recarga al entrar.
  useEffect(() => {
    if (phase !== 'settled' || !kind) return
    invalidateAsync('portal:buyer-order')
    if (kind === 'approved' || kind === 'late') clearPendingOrderPayment(orderId)
  }, [kind, orderId, phase])

  const recheck = () => setRound((value) => value + 1)

  function backToCart() {
    if (pending) restoreCartLines(pending.lines)
    clearPendingOrderPayment(orderId)
    navigate('/carrito')
  }

  function payAgain(url: string) {
    window.location.assign(url)
  }

  if (phase === 'notFound') {
    return (
      <Container className="py-6 sm:py-8">
        <title>Pedido no encontrado | Autopartco</title>
        <EmptyState icon={<PackageSearchIcon />} title="No encontramos este pedido" description="Puede que el enlace esté incompleto o que el pedido no sea de tu cuenta.">
          <Button nativeButton={false} render={<Link to="/mis-pedidos" />}>
            Ver mis pedidos
          </Button>
        </EmptyState>
      </Container>
    )
  }

  if (!payment) {
    if (phase === 'error' || phase === 'timeout') {
      return (
        <Container className="max-w-lg py-8 sm:py-12">
          <title>Estado del pago | Autopartco</title>
          <ErrorState
            title="No pudimos consultar tu pago"
            description="Esto no significa que el pago haya fallado. Revisa tu conexión y vuelve a verificar en un momento."
            onRetry={recheck}
          />
          {transactionId ? <TransactionNote id={transactionId} /> : null}
        </Container>
      )
    }
    return (
      <ResultShell indicator="pending" announce="Verificando el pago." title="Estamos confirmando tu pago" description="Consultando el estado de tu pago; no cierres esta página." busy>
        {transactionId ? <TransactionNote id={transactionId} /> : null}
      </ResultShell>
    )
  }

  const resolvedKind = kind ?? 'pending'
  const checking = phase === 'checking' && resolvedKind === 'pending'
  const copy = copyOf(resolvedKind, payment, pending?.orderNumber, phase === 'timeout' || phase === 'error')
  const checkoutUrl = isSafeCheckoutUrl(payment.checkoutUrl) ? payment.checkoutUrl : undefined

  let actions: ReactNode
  switch (resolvedKind) {
    case 'approved':
      actions = (
        <>
          <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to="/buscar" />}>
            Seguir comprando
          </Button>
          <Button className={BUTTON} nativeButton={false} render={<Link to={`/mis-pedidos/${encodeURIComponent(orderId)}`} />}>
            Ver mi pedido
          </Button>
        </>
      )
      break
    case 'late':
      actions = (
        <>
          <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to={`/mis-pedidos/${encodeURIComponent(orderId)}`} />}>
            Ver mi pedido
          </Button>
          {SUPPORT_EMAIL ? (
            <Button
              className={BUTTON}
              nativeButton={false}
              render={<a href={`mailto:${SUPPORT_EMAIL}?subject=${encodeURIComponent(`Devolución del pago ${payment.reference}`)}`} />}
            >
              Contactar soporte
            </Button>
          ) : null}
        </>
      )
      break
    case 'rejected':
    case 'voided':
      actions = (
        <>
          <Button variant="outline" className={BUTTON} onClick={backToCart}>
            Volver al carrito
          </Button>
          {checkoutUrl ? (
            <Button className={BUTTON} onClick={() => payAgain(checkoutUrl)}>
              Intentar de nuevo
            </Button>
          ) : null}
        </>
      )
      break
    case 'expired':
    case 'closed':
      actions = (
        <>
          <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to="/mis-pedidos" />}>
            Ver mis pedidos
          </Button>
          <Button className={BUTTON} onClick={backToCart}>
            Volver al carrito
          </Button>
        </>
      )
      break
    case 'pending':
      actions = (
        <>
          {/* Con `id` en la URL ya hay una transacción en curso: pagar otra vez podría duplicar el cobro. */}
          {checkoutUrl && !transactionId ? (
            <Button variant={checking ? 'default' : 'outline'} className={BUTTON} onClick={() => payAgain(checkoutUrl)}>
              Ir a pagar
            </Button>
          ) : null}
          {checking ? null : (
            <Button className={BUTTON} onClick={recheck}>
              <RotateCwIcon aria-hidden />
              Volver a verificar
            </Button>
          )}
        </>
      )
      break
  }

  return (
    <ResultShell indicator={INDICATOR[resolvedKind]} announce={copy.announce} title={copy.title} description={copy.description} busy={checking}>
      <PaymentSummary payment={payment} orderNumber={pending?.orderNumber} />
      {phase === 'error' && state.error ? (
        <p role="alert" className="text-sm font-medium text-ap-danger">
          No pudimos actualizar el estado. Lo que ves es lo último que confirmó el servidor.
        </p>
      ) : null}
      {transactionId && resolvedKind !== 'approved' ? <TransactionNote id={transactionId} /> : null}
      {actions ? <div className="flex w-full flex-col-reverse gap-2 border-t border-ap-line pt-5 sm:flex-row">{actions}</div> : null}
    </ResultShell>
  )
}

function ResultShell({
  indicator,
  announce,
  title,
  description,
  busy,
  children,
}: {
  indicator: PaymentIndicatorState
  announce: string
  title: string
  description: string
  busy?: boolean
  children?: ReactNode
}) {
  return (
    <Container className="max-w-2xl py-8 sm:py-12">
      <title>{`${title} | Autopartco`}</title>
      <section
        aria-labelledby="pago-titulo"
        aria-busy={busy || undefined}
        className="flex flex-col items-start gap-6 rounded-xl border border-ap-line bg-ap-white p-5 sm:p-8"
      >
        <div className="flex w-full flex-col items-start gap-5 sm:flex-row sm:items-center sm:gap-6">
          <PaymentStatusIndicator state={indicator} label={announce} className="shrink-0" />
          <header className="flex min-w-0 flex-col gap-2">
            <p className="ap-index">Pago del pedido</p>
            <h1 id="pago-titulo" className="text-page-title text-ap-ink">
              {title}
            </h1>
            <p className="text-ap-muted">{description}</p>
          </header>
        </div>
        {children}
      </section>
    </Container>
  )
}

function PaymentSummary({ payment, orderNumber }: { payment: OrderPaymentDto; orderNumber?: number }) {
  const rows: { label: string; value: ReactNode; strong?: boolean }[] = [
    ...(orderNumber ? [{ label: 'Pedido', value: <span className="font-mono">#{orderNumber}</span> }] : []),
    { label: 'Referencia', value: <span className="font-mono text-xs">{payment.reference}</span> },
    ...(payment.paidAt ? [{ label: 'Pagado el', value: formatOrderDate(payment.paidAt) }] : []),
    { label: 'Total', value: formatCOP(payment.amountInCents / 100), strong: true },
  ]
  return (
    <dl className="flex w-full flex-col divide-y divide-ap-line border-y border-ap-line text-sm">
      {rows.map((row) => (
        <div key={row.label} className="flex items-baseline justify-between gap-4 py-2.5">
          <dt className="shrink-0 text-ap-muted">{row.label}</dt>
          <dd className={row.strong ? 'ap-price ap-num text-lg text-ap-ink' : 'ap-num min-w-0 break-all text-ap-ink'}>{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

function TransactionNote({ id }: { id: string }) {
  return (
    <p className="mt-4 flex items-start gap-2 text-left text-xs text-ap-muted">
      <ReceiptTextIcon className="mt-px size-4 shrink-0" aria-hidden />
      <span>
        Transacción de Wompi: <span className="font-mono break-all text-ap-ink">{id}</span>. Compártela con soporte si necesitas ayuda.
      </span>
    </p>
  )
}

// ------------------------------------------------------------------ recibo (SIMULATED)

/** Recibo de un pedido SIMULATED recién creado (desarrollo local). */
function ReceiptView({ summary, full }: { summary: OrderResultSummary; full?: BuyerOrderDto }) {
  const simulatedPayment = summary.payment.method.startsWith('SIMULATED')

  return (
    <Container className="py-6 sm:py-8">
      <title>{`Pedido #${summary.orderNumber} | Autopartco`}</title>

      <section
        aria-labelledby="pedido-titulo"
        className="flex flex-col gap-4 rounded-xl border border-ap-success/30 bg-ap-success-soft p-5 sm:flex-row sm:items-start sm:gap-5 sm:p-6"
      >
        <CircleCheckIcon className="size-8 shrink-0 text-ap-success" aria-hidden />
        <div className="flex min-w-0 flex-col gap-2">
          <p className="ap-index">Pedido confirmado</p>
          <h1 id="pedido-titulo" className="text-page-title text-ap-ink">
            ¡Pago aprobado!
          </h1>
          <p className="text-ap-ink">
            Tu número de pedido es <strong className="font-mono font-semibold">#{summary.orderNumber}</strong>. Las tiendas ya recibieron tu compra.
          </p>
        </div>
      </section>

      <div className="mt-8 grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex flex-col gap-4">
          <h2 className="text-block-title text-ap-ink">Lo que compraste</h2>
          <OrderSalesList sales={summary.sales} />
        </div>

        <aside className="flex flex-col gap-5 rounded-xl border border-ap-line bg-ap-white p-5" aria-label="Detalle del pago y la entrega">
          <section className="flex flex-col gap-3">
            <h2 className="text-block-title text-ap-ink">Pago</h2>
            <p className="ap-price text-2xl text-ap-ink">{formatCOP(summary.total)}</p>
            <dl className="flex flex-col divide-y divide-ap-line border-y border-ap-line text-sm">
              <Row label="Estado" value={`${statusLabel(summary.payment.status)} · pedido ${statusLabel(summary.status).toLowerCase()}`} />
              <Row label="Método" value={summary.payment.methodName} />
              <Row label="Fecha" value={<span className="tabular-nums">{formatOrderDate(summary.payment.paidAt ?? summary.createdAt)}</span>} />
              <Row label="Referencia" value={<span className="ap-ref break-all">{summary.payment.reference}</span>} />
            </dl>
            {simulatedPayment ? (
              <p className="flex items-start gap-2 rounded-md border border-ap-info/25 bg-ap-info-soft px-3 py-2.5 text-xs text-ap-ink">
                <FlaskConicalIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
                <span>Pago de demostración: no se realizó ningún cobro real.</span>
              </p>
            ) : null}
          </section>

          {full ? (
            <section className="flex flex-col gap-2 border-t border-ap-line pt-5">
              <h2 className="text-block-title text-ap-ink">Entrega en</h2>
              <address className="text-sm not-italic text-ap-ink">
                {full.deliveryAddress.addressLine}
                {full.deliveryAddress.addressComplement ? `, ${full.deliveryAddress.addressComplement}` : ''}
                <br />
                {full.deliveryAddress.city}, {full.deliveryAddress.department}
                {full.deliveryAddress.postalCode ? ` · ${full.deliveryAddress.postalCode}` : ''}
              </address>
            </section>
          ) : (
            <p className="border-t border-ap-line pt-5 text-xs text-ap-muted">
              Por tu privacidad, la dirección de entrega no se guarda en este dispositivo y solo se muestra justo después de pagar.
            </p>
          )}

          <div className="flex flex-col gap-2 border-t border-ap-line pt-5">
            <Button nativeButton={false} render={<Link to="/mis-pedidos" />}>
              Ver mis pedidos
            </Button>
            <Button variant="outline" nativeButton={false} render={<Link to="/buscar" />}>
              Seguir comprando
            </Button>
            <Button variant="ghost" nativeButton={false} render={<Link to="/" />}>
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
    <div className="flex flex-col gap-0.5 py-2.5 sm:flex-row sm:items-baseline sm:justify-between sm:gap-3">
      <dt className="shrink-0 text-ap-muted">{label}</dt>
      <dd className="min-w-0 text-ap-ink sm:text-right">{value}</dd>
    </div>
  )
}
