import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ReceiptTextIcon, RotateCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { invalidateAsync } from '@/lib/use-async'
import { getSubscriptionService } from '../api/subscription-service'
import type { SubscriptionPayment } from '../api/types'
import { PageHeader } from '../components/page-header'
import { PaymentStatusIndicator, type PaymentIndicatorState } from '../components/payment-status-indicator'
import { QueryError } from '../components/query-boundary'
import { PortalHeader } from '../layout/portal-header'
import { formatDate } from '../lib/labels'
import { clearPendingCheckout, loadPendingCheckout, type PendingCheckout } from '../lib/pending-checkout'

/** Cada 3 s al principio y luego más espaciado, durante 60 s como máximo (§6). */
const POLL_TIMEOUT_MS = 60_000
const pollDelay = (attempt: number) => (attempt < 5 ? 3_000 : Math.min(3_000 * 1.5 ** (attempt - 4), 8_000))
/** Errores seguidos de red o del servidor que se toleran antes de mostrar el error. */
const MAX_FAILURES = 3

/** Estados finales de `WompiWebhookProcessor`; cualquier otro (o ninguno) se sigue esperando. */
type FailedStatus = 'DECLINED' | 'ERROR' | 'VOIDED'
const FAILED_STATUSES: readonly string[] = ['DECLINED', 'ERROR', 'VOIDED'] satisfies FailedStatus[]

type Phase =
  | { kind: 'checking' }
  | { kind: 'approved'; payment: SubscriptionPayment }
  | { kind: 'failed'; payment: SubscriptionPayment; status: FailedStatus }
  | { kind: 'timeout' }
  | { kind: 'error'; error: unknown }

/** El `id` de Wompi solo se muestra para soporte: se acepta un formato acotado y nada más. */
function safeTransactionId(value: string | null): string | undefined {
  if (!value) return undefined
  const trimmed = value.trim()
  return /^[A-Za-z0-9-]{1,64}$/.test(trimmed) ? trimmed : undefined
}

function storePath(storeId: string, section: string): string {
  return `/portal/${encodeURIComponent(storeId)}/${section}`
}

function monthsLabel(months: number): string {
  return months === 1 ? '1 mes' : `${months} meses`
}

const isCode = (error: unknown, code: string) => error instanceof ApiError && error.code === code

/**
 * Con `id` de Wompi, sincroniza una vez por ronda (`POST …/payments/{reference}/sync`; tras un 429 ya
 * no); luego consulta `GET …/payments/{reference}` hasta un estado final o 60 s (`payments-wompi.md` §5 y §6).
 * El estado lo decide el servidor, nunca la redirección.
 */
function usePaymentStatus(checkout: PendingCheckout, transactionId: string | undefined, round: number): Phase {
  // El resultado se guarda con su ronda: al pedir "Volver a verificar" se ve "verificando" de inmediato.
  const [result, setResult] = useState<{ round: number; phase: Phase }>()
  const rateLimited = useRef(false)

  useEffect(() => {
    let active = true
    let timer: number | undefined
    const deadline = Date.now() + POLL_TIMEOUT_MS
    let attempt = 0
    let failures = 0
    const settle = (phase: Phase) => {
      if (active) setResult({ round, phase })
    }
    /** `true` si el pago ya es final y no hay que seguir consultando. */
    const apply = (payment: SubscriptionPayment): boolean => {
      if (payment.status === 'APPROVED') {
        clearPendingCheckout()
        // La suscripción y los almacenes cambiaron: las pantallas del portal recargan al volver.
        invalidateAsync(`portal:${checkout.storeId}:`)
        invalidateAsync('portal:stores')
        settle({ kind: 'approved', payment })
        return true
      }
      if (FAILED_STATUSES.includes(payment.status)) {
        settle({ kind: 'failed', payment, status: payment.status as FailedStatus })
        return true
      }
      return false
    }
    /** 403 (no es OWNER) y 404 (referencia de otro almacén) terminan el ciclo. */
    const fatal = (error: unknown): boolean => {
      if (error instanceof ApiError && (error.status === 403 || error.status === 404 || error.status === 401) && !isCode(error, 'PAYMENT_TRANSACTION_NOT_FOUND')) {
        settle({ kind: 'error', error })
        return true
      }
      return false
    }

    async function poll() {
      try {
        const payment = await (await getSubscriptionService()).payment(checkout.storeId, checkout.reference)
        if (!active) return
        failures = 0
        if (apply(payment)) return
      } catch (error) {
        if (!active || fatal(error)) return
        failures += 1
        if (failures >= MAX_FAILURES) {
          settle({ kind: 'error', error })
          return
        }
      }
      const delay = pollDelay(attempt++)
      if (Date.now() + delay > deadline) {
        settle({ kind: 'timeout' })
        return
      }
      timer = window.setTimeout(() => void poll(), delay)
    }

    async function start() {
      if (transactionId && !rateLimited.current) {
        try {
          const payment = await (await getSubscriptionService()).syncPayment(checkout.storeId, checkout.reference, transactionId)
          if (!active) return
          if (apply(payment)) return
        } catch (error) {
          if (!active) return
          // 429: se sigue solo con el GET. 404 de transacción, 409 de referencia, 400 o red: el GET dice el estado real.
          if (isCode(error, 'PAYMENT_SYNC_RATE_LIMITED')) rateLimited.current = true
          else if (fatal(error)) return
        }
      }
      void poll()
    }

    void start()
    return () => {
      active = false
      window.clearTimeout(timer)
    }
  }, [checkout, transactionId, round])

  return result && result.round === round ? result.phase : { kind: 'checking' }
}

interface ResultView {
  indicator?: PaymentIndicatorState
  /** Texto para lectores de pantalla (región `role="status"` del indicador). */
  announce: string
  title: string
  description: ReactNode
}

function viewOf(phase: Phase, checkout: PendingCheckout): ResultView {
  switch (phase.kind) {
    case 'checking':
      return {
        indicator: 'pending',
        announce: 'Verificando el pago con Wompi.',
        title: 'Estamos confirmando tu pago',
        description: 'Wompi nos está enviando la confirmación del pago. Suele tardar unos segundos; no cierres esta página.',
      }
    case 'approved':
      return {
        indicator: 'approved',
        announce: 'Pago aprobado.',
        title: 'Tu pago fue aprobado',
        description: `Tu plan ${phase.payment.planName || checkout.planName} quedó activo por ${monthsLabel(phase.payment.months || checkout.months)}. Ya puedes usar todo lo que incluye.`,
      }
    case 'failed':
      if (phase.status === 'VOIDED') {
        return {
          indicator: 'declined',
          announce: 'El pago fue anulado.',
          title: 'El pago fue anulado',
          description: 'Wompi anuló esta transacción y tu plan no cambió. Si tienes dudas sobre un cargo, escríbenos con la referencia.',
        }
      }
      return {
        indicator: 'declined',
        announce: phase.status === 'ERROR' ? 'No se pudo completar el pago.' : 'Pago rechazado.',
        title: phase.status === 'ERROR' ? 'No se pudo completar el pago' : 'Wompi rechazó el pago',
        description: 'No se hizo ningún cobro y tu plan no cambió. Puedes intentarlo de nuevo, si quieres con otro medio de pago.',
      }
    case 'timeout':
      return {
        indicator: 'waiting',
        announce: 'La confirmación del pago está tardando.',
        title: 'La confirmación está tardando',
        description:
          'Wompi puede tardar unos minutos en confirmar el pago. Cuando llegue, lo verás reflejado en tu plan; no hace falta pagar de nuevo.',
      }
    case 'error':
      return {
        announce: 'No pudimos consultar el estado del pago.',
        title: 'No pudimos consultar tu pago',
        description: 'Esto no significa que el pago haya fallado. Vuelve a intentarlo en un momento.',
      }
  }
}

function Summary({ rows }: { rows: { label: string; value: ReactNode; strong?: boolean }[] }) {
  return (
    <dl className="grid w-full grid-cols-[auto_1fr] border-t border-ap-line text-left text-sm">
      {rows.map((row) => (
        <div key={row.label} className="contents">
          <dt className="border-b border-ap-line py-2.5 pr-4 text-ap-muted">{row.label}</dt>
          <dd className={row.strong ? 'ap-price border-b border-ap-line py-2.5 text-right text-lg text-ap-ink' : 'min-w-0 border-b border-ap-line py-2.5 text-right break-all text-ap-ink tabular-nums'}>{row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

function Actions({ children }: { children: ReactNode }) {
  return <div className="flex w-full flex-col-reverse gap-2 sm:flex-row">{children}</div>
}

const BUTTON = 'h-11 w-full px-5 sm:w-auto'

function PendingResult({ checkout, transactionId }: { checkout: PendingCheckout; transactionId?: string }) {
  const [round, setRound] = useState(0)
  const phase = usePaymentStatus(checkout, transactionId, round)
  const view = viewOf(phase, checkout)
  const planPath = storePath(checkout.storeId, 'plan')

  function retry() {
    setRound((value) => value + 1)
  }


  let details: ReactNode
  let actions: ReactNode
  switch (phase.kind) {
    case 'approved': {
      const { payment } = phase
      details = (
        <Summary
          rows={[
            { label: 'Plan', value: payment.planName || checkout.planName },
            { label: 'Meses pagados', value: monthsLabel(payment.months || checkout.months) },
            ...(payment.periodEndsAt ? [{ label: 'Vigente hasta', value: formatDate(payment.periodEndsAt) }] : []),
            { label: 'Referencia', value: <span className="ap-ref">{payment.reference}</span> },
            { label: 'Total pagado', value: formatCOP(payment.amountCop), strong: true },
          ]}
        />
      )
      actions = (
        <Actions>
          <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to={storePath(checkout.storeId, 'inicio')} />}>
            Ir al panel
          </Button>
          <Button className={BUTTON} nativeButton={false} render={<Link to={planPath} />}>
            Ir a mi plan
          </Button>
        </Actions>
      )
      break
    }
    case 'failed':
      details = (
        <Summary
          rows={[
            { label: 'Plan', value: phase.payment.planName || checkout.planName },
            { label: 'Referencia', value: <span className="ap-ref">{phase.payment.reference}</span> },
          ]}
        />
      )
      actions = (
        <Actions>
          <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to={storePath(checkout.storeId, 'inicio')} />}>
            Ir al panel
          </Button>
          <Button className={BUTTON} nativeButton={false} render={<Link to={planPath} />}>
            Intentar de nuevo
          </Button>
        </Actions>
      )
      break
    case 'timeout':
      details = <CheckoutSummary checkout={checkout} />
      actions = (
        <Actions>
          <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to={planPath} />}>
            Ir a mi plan
          </Button>
          <Button className={BUTTON} onClick={retry}>
            <RotateCwIcon aria-hidden />
            Volver a verificar
          </Button>
        </Actions>
      )
      break
    case 'error':
      details = <QueryError error={phase.error} what="el estado del pago" onRetry={retry} className="w-full text-left" />
      actions = (
        <Actions>
          <Button variant="outline" className={BUTTON} nativeButton={false} render={<Link to={planPath} />}>
            Ir a mi plan
          </Button>
        </Actions>
      )
      break
    case 'checking':
      details = <CheckoutSummary checkout={checkout} />
      actions = null
      break
  }

  return (
    <ResultCard
      indicator={view.indicator ? <PaymentStatusIndicator state={view.indicator} label={view.announce} /> : null}
      title={view.title}
      description={view.description}
      busy={phase.kind === 'checking'}
    >
      {details}
      {transactionId && phase.kind !== 'approved' ? <TransactionNote id={transactionId} /> : null}
      {actions}
    </ResultCard>
  )
}

/** Lo que se envió a Wompi mientras no hay respuesta del servidor (el monto real lo confirma el servidor). */
function CheckoutSummary({ checkout }: { checkout: PendingCheckout }) {
  return (
    <Summary
      rows={[
        { label: 'Plan', value: checkout.planName },
        { label: 'Meses', value: monthsLabel(checkout.months) },
        { label: 'Referencia', value: <span className="ap-ref">{checkout.reference}</span> },
        { label: 'Total', value: formatCOP(checkout.amountInCents / 100), strong: true },
      ]}
    />
  )
}

function TransactionNote({ id }: { id: string }) {
  return (
    <p className="flex items-start gap-2 text-left text-xs text-ap-muted">
      <ReceiptTextIcon className="mt-px size-4 shrink-0" aria-hidden />
      <span>
        Transacción de Wompi: <span className="ap-ref break-all text-ap-ink">{id}</span>. Compártela con soporte si necesitas ayuda.
      </span>
    </p>
  )
}

function ResultCard({
  indicator,
  title,
  description,
  busy,
  children,
}: {
  indicator?: ReactNode
  title: string
  description: ReactNode
  busy?: boolean
  children: ReactNode
}) {
  return (
    <section aria-busy={busy || undefined} className="flex flex-col items-start gap-6 border-t-2 border-ap-graphite pt-6">
      {indicator}
      <PageHeader label="Pago del plan" title={title} description={description} className="mb-0 w-full sm:mb-0" />
      {children}
    </section>
  )
}

/**
 * `/portal/pago/resultado?id=…&env=…`: a donde Wompi devuelve tras pagar la suscripción. Lee el
 * cobro pendiente guardado antes de redirigir y consulta su estado al servidor.
 */
export default function PaymentResultPage() {
  const [params] = useSearchParams()
  const [checkout] = useState(loadPendingCheckout)
  const transactionId = safeTransactionId(params.get('id'))

  return (
    <div className="min-h-svh bg-ap-white">
      <PortalHeader innerClassName="mx-auto w-full max-w-3xl px-4 sm:px-6 lg:px-6" />
      <main id="contenido" tabIndex={-1} className="mx-auto w-full max-w-lg px-4 py-8 outline-none sm:px-6 sm:py-12">
        <title>Resultado del pago | Autopartco</title>
        {checkout ? (
          <PendingResult checkout={checkout} transactionId={transactionId} />
        ) : (
          <ResultCard
            title="No encontramos el pago en esta pestaña"
            description="Si acabas de pagar, revisa la sección Plan de tu almacén: la confirmación puede tardar unos minutos en reflejarse."
          >
            {transactionId ? <TransactionNote id={transactionId} /> : null}
            <Actions>
              <Button className={BUTTON} nativeButton={false} render={<Link to="/portal" />}>
                Ir a mis almacenes
              </Button>
            </Actions>
          </ResultCard>
        )}
      </main>
    </div>
  )
}
