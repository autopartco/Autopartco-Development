import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { CircleCheckIcon, InfoIcon, RefreshCwIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatCOP } from '@/lib/format'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getSubscriptionService } from '../api/subscription-service'
import type { Plan, SimulatedPaymentResult, Subscription } from '../api/types'
import { CardsSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { PaymentsTable } from '../components/payments-table'
import { PlanCard } from '../components/plan-card'
import { QueryBoundary, QueryError } from '../components/query-boundary'
import { CheckoutDialog } from '../components/simulated-checkout-dialog'
import { StatusBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { Reveal, RevealGroup } from '../components/bento'
import { capabilityLabel, formatDate, formatDays, SUBSCRIPTION_STATUS_LABEL, SUBSCRIPTION_STATUS_TONE } from '../lib/labels'

function SummaryCard({ subscription }: { subscription: Subscription }) {
  const days = subscription.daysRemaining
  return (
    <Card className="gap-5 p-5">
      <CardHeader className="gap-1.5 px-0">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <CardTitle className="text-xl font-extrabold tracking-tight">Plan {subscription.plan.name}</CardTitle>
          <StatusBadge tone={SUBSCRIPTION_STATUS_TONE[subscription.status]}>{SUBSCRIPTION_STATUS_LABEL[subscription.status]}</StatusBadge>
        </div>
        <CardDescription>
          {subscription.status === 'EXPIRED'
            ? `Tu suscripción venció${subscription.validThrough ? ` el ${formatDate(subscription.validThrough)}` : ''}.`
            : subscription.validThrough
              ? `${subscription.status === 'TRIALING' ? 'Tu prueba termina' : 'Pagado hasta'} el ${formatDate(subscription.validThrough)}${days !== null ? ` (${days === 0 ? 'vence hoy' : `quedan ${formatDays(days)}`})` : ''}.`
              : 'Aún no tienes una vigencia asignada.'}
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-5 px-0 md:grid-cols-3">
        <UsageMeter capacity="PRODUCTS" used={subscription.usage.products.used} limit={subscription.usage.products.limit} />
        <UsageMeter capacity="BRANCHES" used={subscription.usage.branches.used} limit={subscription.usage.branches.limit} />
        <UsageMeter capacity="USERS" used={subscription.usage.users.used} limit={subscription.usage.users.limit} />
      </CardContent>
      {subscription.capabilities.length > 0 ? (
        <div className="flex flex-col gap-2">
          <h3 className="text-sm font-semibold">Funciones incluidas</h3>
          <ul className="flex flex-wrap gap-2">
            {subscription.capabilities.map((capability) => (
              <li key={capability} className="rounded-full bg-concrete px-3 py-1 text-xs font-medium">
                {capabilityLabel(capability)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </Card>
  )
}

function PlanAction({
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

  if (!subscription.canManage) return <p className="text-sm text-muted-foreground">Solo el propietario puede cambiar el plan.</p>
  if (free) return <p className="text-sm text-muted-foreground">Es la prueba inicial: no se paga.</p>
  if (subscription.paymentMode === 'NONE') {
    return <p className="text-sm text-muted-foreground">El pago en línea no está disponible por ahora. Escríbenos y te ayudamos.</p>
  }
  if (!isCurrent && !subscription.planChangeAllowed) {
    return (
      <p className="text-sm text-muted-foreground">
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

function PlanView({ subscription }: { subscription: Subscription }) {
  const { storeId, applySubscription, invalidateStore } = usePortal()
  const plans = useAsync('portal:plans', async () => (await getSubscriptionService()).plans())
  const payments = useStoreQuery('payments', async (id) => (await getSubscriptionService()).payments(id))
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
    <div className="flex flex-col gap-8">
      {receipt ? (
        <div role="status" className="flex items-start gap-3 rounded-lg border border-stock/30 bg-stock/10 p-4 text-sm">
          <CircleCheckIcon className="mt-0.5 size-5 shrink-0 text-stock" aria-hidden />
          <div>
            <p className="font-semibold">
              Pago de prueba aprobado: plan {receipt.payment.planName} por {receipt.payment.months} {receipt.payment.months === 1 ? 'mes' : 'meses'}.
            </p>
            <p className="text-foreground/85">
              Total registrado {formatCOP(receipt.payment.amountCop)}. No se realizó ningún cobro real. Tu plan está vigente hasta el{' '}
              {formatDate(receipt.subscription.paidThrough)}. Referencia <span className="font-mono">{receipt.payment.reference}</span>.
            </p>
          </div>
        </div>
      ) : null}

      <SummaryCard subscription={subscription} />

      <section aria-labelledby="planes-titulo" className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <h2 id="planes-titulo" className="text-xl font-extrabold tracking-tight">
            Compara los planes
          </h2>
          <p className="text-muted-foreground">
            Pagas por adelantado de 1 a 12 meses; no hay renovación automática. Si bajas de plan no se borra nada: solo se bloquean las altas que excedan el cupo.
          </p>
        </div>
        {subscription.canManage && subscription.paymentMode === 'SIMULATED' ? (
          <p className="flex items-start gap-2 text-sm font-medium text-recommended">
            <InfoIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
            Pago de prueba: no se realiza ningún cobro.
          </p>
        ) : null}
        {!subscription.canManage ? (
          <p role="note" className="flex items-start gap-2 rounded-lg bg-concrete p-3 text-sm">
            <InfoIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
            Estás en modo de solo lectura: el plan solo lo gestiona el propietario del almacén.
          </p>
        ) : null}
        {plans.status === 'error' ? (
          <QueryError error={plans.error} what="los planes disponibles" onRetry={plans.retry} />
        ) : plans.data === undefined ? (
          <CardsSkeleton count={3} label="Cargando planes" />
        ) : (
          <RevealGroup>
            <ul className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
              {plans.data
                .toSorted((a, b) => a.monthlyPriceCop - b.monthlyPriceCop)
                .map((plan, index) => (
                  <Reveal as="li" index={index} key={plan.id}>
                    <PlanCard
                      plan={plan}
                      current={plan.code === subscription.plan.code}
                      action={<PlanAction plan={plan} subscription={subscription} onChoose={choose} />}
                    />
                  </Reveal>
                ))}
            </ul>
          </RevealGroup>
        )}
      </section>

      <section aria-labelledby="pagos-titulo" className="flex flex-col gap-4">
        <h2 id="pagos-titulo" className="text-xl font-extrabold tracking-tight">
          Historial de pagos
        </h2>
        <QueryBoundary result={payments} what="el historial de pagos">
          {(data) => <PaymentsTable payments={data} />}
        </QueryBoundary>
      </section>

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
    <div role="status" className="flex flex-col gap-3 rounded-lg border border-recommended/30 bg-recommended-soft p-4 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p>
        <strong className="font-semibold">Estamos confirmando tu pago con Wompi.</strong> Tu plan cambia cuando el servidor lo confirma, no al volver a esta página. Puede tardar unos minutos.
      </p>
      <Button variant="outline" className="h-11 shrink-0 bg-card px-4" onClick={onRefresh}>
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
      {returnedFromGateway ? <div className="mb-6"><WompiReturnNotice onRefresh={invalidateStore} /></div> : null}
      {subscription ? (
        <PlanView subscription={subscription} />
      ) : subscriptionError !== undefined ? (
        <QueryError error={subscriptionError} what="tu plan" onRetry={reloadSubscription} />
      ) : (
        <CardsSkeleton count={3} label="Cargando tu plan" />
      )}
    </>
  )
}
