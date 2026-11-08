import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { MessageSquareIcon, RefreshCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP } from '@/lib/format'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getSubscriptionService } from '../api/subscription-service'
import type { Plan, SimulatedPaymentResult, Subscription } from '../api/types'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { PaymentsTable } from '../components/payments-table'
import { type Billing } from '../components/plan-card'
import { PlanComparisonTable } from '../components/plan-comparison-table'
import { QueryBoundary, QueryError } from '../components/query-boundary'
import { QuoteRequestDialog } from '../components/quote-request-dialog'
import { SEGMENT_GROUP_CLASS, segmentClass } from '../components/segmented'
import type { QuoteContext } from '../lib/quote-request'
import { CheckoutDialog } from '../components/simulated-checkout-dialog'
import { StatusBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { offersSalesContact, planCapabilities, sortPlans } from '../lib/plan-capabilities'
import { formatDate, formatDays, SUBSCRIPTION_STATUS_LABEL, SUBSCRIPTION_STATUS_TONE } from '../lib/labels'

/** Estado del plan, vigencia y uso de cupos: sección abierta (sin tarjeta), lo primero que se revisa. */
function PlanSummary({ subscription }: { subscription: Subscription }) {
  const days = subscription.daysRemaining
  const capabilities = planCapabilities(subscription.plan)
  return (
    <section aria-labelledby="plan-actual" className="flex flex-col gap-5 border-l-2 border-ap-graphite pl-4">
      <div className="flex flex-col gap-1.5">
        <p className="ap-index">Tu plan</p>
        <div className="flex flex-wrap items-center gap-2">
          <h2 id="plan-actual" className="text-block-title text-ap-ink">
            Plan {subscription.plan.name}
          </h2>
          {SUBSCRIPTION_STATUS_LABEL[subscription.status].toLowerCase() !== subscription.plan.name.toLowerCase() ? (
            <StatusBadge tone={SUBSCRIPTION_STATUS_TONE[subscription.status]}>{SUBSCRIPTION_STATUS_LABEL[subscription.status]}</StatusBadge>
          ) : null}
        </div>
        <p className="text-sm text-ap-muted">
          {subscription.status === 'EXPIRED'
            ? `Tu suscripción venció${subscription.validThrough ? ` el ${formatDate(subscription.validThrough)}` : ''}.`
            : subscription.validThrough
              ? `${subscription.status === 'TRIALING' ? 'Tu prueba termina' : 'Pagado hasta'} el ${formatDate(subscription.validThrough)}${days !== null ? ` (${days === 0 ? 'vence hoy' : `quedan ${formatDays(days)}`})` : ''}.`
              : 'Aún no tienes una vigencia asignada.'}
        </p>
      </div>
      <div className="grid gap-5 md:grid-cols-3">
        <UsageMeter capacity="PRODUCTS" used={subscription.usage.products.used} limit={subscription.usage.products.limit} />
        <UsageMeter capacity="BRANCHES" used={subscription.usage.branches.used} limit={subscription.usage.branches.limit} />
        <UsageMeter capacity="USERS" used={subscription.usage.users.used} limit={subscription.usage.users.limit} />
      </div>
      {capabilities.available.length > 0 ? (
        <div className="flex flex-col gap-1.5">
          <h3 className="text-sm font-semibold text-ap-ink">Funciones incluidas</h3>
          <p className="text-sm text-ap-ink">{capabilities.available.map((info) => info.label).join(' · ')}</p>
        </div>
      ) : null}
      {capabilities.comingSoon.length > 0 ? (
        <div className="flex flex-col gap-1.5">
          <h3 className="text-sm font-semibold text-ap-ink">En camino</h3>
          <p className="text-sm text-ap-muted">
            {capabilities.comingSoon.map((info) => info.label).join(' · ')}. Aún no están activas y no cuentan como funciones contratadas.
          </p>
        </div>
      ) : null}
    </section>
  )
}

function SalesContactAction({ onRequest }: { onRequest: () => void }) {
  return (
    <Button variant="ghost" className="h-11 w-full justify-start px-3" onClick={onRequest}>
      <MessageSquareIcon aria-hidden />
      Hablar con ventas
    </Button>
  )
}

function BillingToggle({ value, onChange }: { value: Billing; onChange: (value: Billing) => void }) {
  const options: { value: Billing; label: string }[] = [
    { value: 'MONTHLY', label: 'Mensual' },
    { value: 'ANNUAL', label: 'Anual · 2 meses gratis' },
  ]
  return (
    <div role="group" aria-label="Periodo de facturación" className={SEGMENT_GROUP_CLASS}>
      {options.map((option) => (
        <button key={option.value} type="button" aria-pressed={value === option.value} onClick={() => onChange(option.value)} className={segmentClass(value === option.value)}>
          {option.label}
        </button>
      ))}
    </div>
  )
}

function PlanAction(props: { plan: Plan; subscription: Subscription; onChoose: (plan: Plan) => void; onRequestQuote: () => void }) {
  return (
    <>
      <PurchaseAction {...props} />
      {offersSalesContact(props.plan) ? <SalesContactAction onRequest={props.onRequestQuote} /> : null}
    </>
  )
}

function PurchaseAction({
  plan,
  subscription,
  onChoose,
}: {
  plan: Plan
  subscription: Subscription
  onChoose: (plan: Plan) => void
}) {
  const isCurrent = plan.id === subscription.plan.id || plan.code === subscription.plan.code
  const free = plan.monthlyPriceCop === 0

  if (!subscription.canManage) return <p className="text-sm text-ap-muted">Solo el propietario puede cambiar el plan.</p>
  if (free) {
    return (
      <p className="text-sm text-ap-muted">
        {plan.code === 'TRY' ? 'Es el mes de prueba gratuita con el que empieza tu almacén: no se paga.' : 'Este plan no se contrata en línea. Escríbenos y te ayudamos.'}
      </p>
    )
  }
  if (subscription.paymentMode === 'NONE') {
    return <p className="text-sm text-ap-muted">El pago en línea no está disponible por ahora. Escríbenos y te ayudamos.</p>
  }
  if (!isCurrent && !subscription.planChangeAllowed) {
    return (
      <p className="text-sm text-ap-muted">
        Ya pagaste otro plan{subscription.paidThrough ? ` hasta el ${formatDate(subscription.paidThrough)}` : ''}. Podrás cambiar cuando termine ese periodo.
      </p>
    )
  }
  const label = isCurrent && subscription.status === 'ACTIVE' ? 'Extender periodo' : subscription.status === 'EXPIRED' ? 'Renovar con este plan' : 'Elegir este plan'
  return (
    <Button variant={plan.recommended ? 'default' : 'outline'} className="h-11 w-full px-4" onClick={() => onChoose(plan)}>
      {label}
    </Button>
  )
}

function PlansSkeleton() {
  return (
    <div role="status" className="flex flex-col gap-2">
      <span className="sr-only">Cargando los planes…</span>
      <div aria-hidden className="flex flex-col gap-px overflow-hidden rounded-md border border-ap-line">
        <Skeleton className="h-28 rounded-none" />
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-11 rounded-none" />
        ))}
      </div>
    </div>
  )
}

function PlanView({ subscription }: { subscription: Subscription }) {
  const { storeId, store, applySubscription, invalidateStore } = usePortal()
  const [quoteOpen, setQuoteOpen] = useState(false)
  const quoteContext: QuoteContext = {
    storeName: store?.businessName ?? `Almacén ${storeId}`,
    storeId,
    planName: subscription.plan.name,
    usage: [
      { label: 'Sedes', ...subscription.usage.branches },
      { label: 'Productos', ...subscription.usage.products },
      { label: 'Usuarios', ...subscription.usage.users },
    ],
  }
  const plans = useAsync('portal:plans', async () => (await getSubscriptionService()).plans())
  const payments = useStoreQuery('payments', async (id) => (await getSubscriptionService()).payments(id))
  const [billing, setBilling] = useState<Billing>('MONTHLY')
  const [chosen, setChosen] = useState<Plan>()
  const [open, setOpen] = useState(false)
  const [receipt, setReceipt] = useState<SimulatedPaymentResult>()

  function choose(plan: Plan) {
    setReceipt(undefined)
    setChosen(plan)
    setOpen(true)
  }

  function handlePaid(result: SimulatedPaymentResult) {
    applySubscription(result.subscription)
    invalidateStore()
    setReceipt(result)
  }

  return (
    <div className="flex flex-col gap-10">
      {receipt ? (
        <div role="status" className="flex flex-col gap-1 rounded-md border border-ap-success/30 bg-ap-success-soft p-4 text-sm">
          <p className="font-semibold text-ap-success">
            Pago de prueba aprobado: plan {receipt.payment.planName} por {receipt.payment.months} {receipt.payment.months === 1 ? 'mes' : 'meses'}.
          </p>
          <p className="text-ap-ink">
            Total registrado <span className="ap-price">{formatCOP(receipt.payment.amountCop)}</span>. No se realizó ningún cobro real. Tu plan está vigente hasta el{' '}
            {formatDate(receipt.subscription.paidThrough)}. Referencia <span className="ap-ref">{receipt.payment.reference}</span>.
          </p>
        </div>
      ) : null}

      <PlanSummary subscription={subscription} />

      <section aria-labelledby="planes-titulo" className="flex flex-col gap-4 border-t border-ap-line-strong pt-4">
        <div className="flex flex-col gap-1">
          <h2 id="planes-titulo" className="text-block-title text-ap-ink">
            Compara los planes
          </h2>
          <p className="max-w-prose text-ap-muted">
            Pagas por adelantado de 1 a 12 meses; no hay renovación automática. Si bajas de plan no se borra nada: solo se bloquean las altas que excedan el cupo.
          </p>
        </div>
        {subscription.canManage && subscription.paymentMode === 'SIMULATED' ? (
          <p className="border-l-2 border-ap-info pl-3 text-sm font-medium text-ap-info">Pago de prueba: no se realiza ningún cobro.</p>
        ) : null}
        {!subscription.canManage ? (
          <p role="note" className="border-l-2 border-ap-line-strong pl-3 text-sm text-ap-ink">
            Estás en modo de solo lectura: el plan solo lo gestiona el propietario del almacén.
          </p>
        ) : null}
        <BillingToggle value={billing} onChange={setBilling} />
        {billing === 'ANNUAL' ? (
          <p role="note" className="max-w-prose text-sm text-ap-muted">
            El pago anual con descuento aún no está disponible en el cobro: por ahora pagas por meses al precio mensual. El precio anual es de referencia.
          </p>
        ) : null}
        {plans.status === 'error' ? (
          <QueryError error={plans.error} what="los planes disponibles" onRetry={plans.retry} />
        ) : plans.data === undefined ? (
          <PlansSkeleton />
        ) : plans.data.length === 0 ? (
          <p className="border-l-2 border-ap-line-strong pl-3 text-sm text-ap-muted">No hay planes disponibles para contratar ahora. Escríbenos y te ayudamos a elegir.</p>
        ) : (
          <PlanComparisonTable
            plans={sortPlans(plans.data)}
            currentCode={subscription.plan.code}
            billing={billing}
            renderAction={(plan) => <PlanAction plan={plan} subscription={subscription} onChoose={choose} onRequestQuote={() => setQuoteOpen(true)} />}
          />
        )}
      </section>

      <section aria-labelledby="pagos-titulo" className="flex flex-col gap-4 border-t border-ap-line-strong pt-4">
        <h2 id="pagos-titulo" className="text-block-title text-ap-ink">
          Historial de pagos
        </h2>
        <QueryBoundary result={payments} what="el historial de pagos" skeleton={<ListSkeleton rows={3} label="Cargando el historial de pagos" />}>
          {(data) => <PaymentsTable payments={data} />}
        </QueryBoundary>
      </section>

      <QuoteRequestDialog open={quoteOpen} onOpenChange={setQuoteOpen} context={quoteContext} />
      <CheckoutDialog open={open} onOpenChange={setOpen} storeId={storeId} plan={chosen} subscription={subscription} onPaid={handlePaid} />
    </div>
  )
}

/**
 * Al volver de Wompi (`?pago=…` o `?id=…`) el estado NO se da por bueno por la redirección:
 * se vuelve a pedir al servidor y el plan solo cambia cuando él lo confirma.
 */
function WompiReturnNotice({ onRefresh }: { onRefresh: () => void }) {
  return (
    <div role="status" className="flex flex-col gap-3 rounded-md border border-ap-info/30 bg-ap-info-soft p-4 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p className="text-ap-ink">
        <strong className="font-semibold">Estamos confirmando tu pago con Wompi.</strong> Tu plan cambia cuando el servidor lo confirma, no al volver a esta página. Puede tardar unos minutos.
      </p>
      <Button variant="outline" className="h-11 shrink-0 px-4" onClick={onRefresh}>
        <RefreshCwIcon aria-hidden />
        Actualizar estado
      </Button>
    </div>
  )
}

/** `/portal/:storeId/plan`: estado, vigencia, uso, comparativa de planes, cobro e historial de pagos. */
export default function PortalPlanPage() {
  const { storeId, subscription, subscriptionError, reloadSubscription, invalidateStore } = usePortal()
  const [params] = useSearchParams()
  const returnedFromGateway = params.has('pago') || params.has('id')

  // Al volver de la pasarela se pide el estado real una vez.
  useEffect(() => {
    if (returnedFromGateway) invalidateAsync(`portal:${storeId}:`)
  }, [returnedFromGateway, storeId])

  return (
    <>
      <title>Plan y suscripción | Autopartco</title>
      <PageHeader title="Plan y suscripción" description="Tu plan, lo que ya usas, y cómo mejorarlo o renovarlo." />
      {returnedFromGateway ? (
        <div className="mb-6">
          <WompiReturnNotice onRefresh={invalidateStore} />
        </div>
      ) : null}
      {subscription ? (
        <PlanView subscription={subscription} />
      ) : subscriptionError !== undefined ? (
        <QueryError error={subscriptionError} what="tu plan" onRetry={reloadSubscription} />
      ) : (
        <ListSkeleton rows={3} label="Cargando tu plan" />
      )}
    </>
  )
}
