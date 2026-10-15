import { useRef, useState, type FormEvent } from 'react'
import { FlaskConicalIcon } from 'lucide-react'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogClose, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError, detailNumber, detailText } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { getSubscriptionService } from '../api/subscription-service'
import type { Plan, SimulatedPaymentResult, Subscription } from '../api/types'
import { actionErrorMessage } from '../lib/errors'
import { formatDate } from '../lib/labels'
import { SelectField } from './form-controls'

interface CheckoutDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  storeId: string
  plan: Plan | undefined
  subscription: Subscription
  /** Pago simulado aprobado: el servidor ya devolvió la suscripción nueva. */
  onPaid: (result: SimulatedPaymentResult) => void
}

const MONTH_OPTIONS = Array.from({ length: 12 }, (_, index) => ({
  value: String(index + 1),
  label: index === 0 ? '1 mes' : `${index + 1} meses`,
}))

/** Solo se redirige a URLs https (la pasarela real); nunca a esquemas arbitrarios. */
function isSafeCheckoutUrl(value: string): boolean {
  try {
    const url = new URL(value)
    return url.protocol === 'https:'
  } catch {
    return false
  }
}

/** Formulario del cobro. Se monta al abrir el diálogo: `clientRequestId` nace una vez por apertura. */
function CheckoutForm({ storeId, plan, subscription, onPaid, onClose }: Omit<CheckoutDialogProps, 'open' | 'onOpenChange' | 'plan'> & { plan: Plan; onClose: () => void }) {
  const [clientRequestId] = useState(() => crypto.randomUUID())
  const [months, setMonths] = useState('1')
  const [simulateRejection, setSimulateRejection] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState<{ title: string; detail?: string }>()
  const alertRef = useRef<HTMLDivElement>(null)

  const simulated = subscription.paymentMode === 'SIMULATED'
  const wompi = subscription.paymentMode === 'WOMPI'
  const count = Number(months)
  // Estimado para mostrar: el importe real lo calcula y confirma el servidor.
  const estimate = plan.monthlyPriceCop * count

  function fail(title: string, detail?: string) {
    setError({ title, detail })
    requestAnimationFrame(() => alertRef.current?.focus())
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    setPending(true)
    setError(undefined)
    try {
      const service = await getSubscriptionService()
      if (simulated) {
        const result = await service.paySimulated(storeId, {
          planId: plan.id,
          months: count,
          simulatedOutcome: simulateRejection ? 'REJECTED' : 'APPROVED',
          clientRequestId,
        })
        onPaid(result)
        onClose()
      } else if (wompi) {
        const result = await service.checkout(storeId, { planId: plan.id, months: count })
        if (!isSafeCheckoutUrl(result.checkoutUrl)) {
          fail('No pudimos abrir la pasarela de pago', 'La dirección de pago no es válida. Inténtalo de nuevo o escríbenos.')
          return
        }
        window.location.assign(result.checkoutUrl)
      }
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 402) {
        const amount = detailNumber(cause, 'amountCop')
        fail(
          'El pago fue rechazado',
          `No se hizo ningún cobro${amount !== undefined ? ` de ${formatCOP(amount)}` : ''} y tu plan no cambió. Puedes intentarlo de nuevo.`,
        )
      } else if (cause instanceof ApiError && cause.code === 'SUBSCRIPTION_PLAN_CHANGE_LOCKED') {
        const paidThrough = detailText(cause, 'paidThrough')
        const currentCode = detailText(cause, 'currentPlanCode')
        fail(
          'Todavía no puedes cambiar de plan',
          `Ya pagaste${currentCode ? ` el plan ${currentCode}` : ' otro plan'}${paidThrough ? ` hasta el ${formatDate(paidThrough)}` : ''}. Podrás elegir otro cuando termine ese periodo; mientras tanto puedes extender el actual.`,
        )
      } else if (cause instanceof ApiError && cause.code === 'SUBSCRIPTION_DUPLICATE_REQUEST') {
        fail('Este pago ya se había enviado', 'Cierra este cuadro y vuelve a abrirlo para intentarlo con una solicitud nueva.')
      } else {
        fail('No se pudo procesar el pago', actionErrorMessage(cause, 'Inténtalo de nuevo en unos minutos.'))
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <form onSubmit={(event) => void handleSubmit(event)} noValidate className="flex flex-col gap-4" aria-busy={pending || undefined}>
      <DialogHeader>
        <DialogTitle>Pagar el plan {plan.name}</DialogTitle>
        <DialogDescription>
          {simulated
            ? 'Elige cuántos meses quieres pagar por adelantado. No hay renovación automática.'
            : 'Elige cuántos meses pagar por adelantado. Te llevaremos a la pasarela segura de Wompi.'}
        </DialogDescription>
      </DialogHeader>

      {simulated ? (
        <p className="flex items-start gap-2.5 rounded-lg border border-recommended/30 bg-recommended-soft p-3 text-sm font-semibold text-recommended">
          <FlaskConicalIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
          Pago de prueba: no se realiza ningún cobro.
        </p>
      ) : null}

      {error ? (
        <FormAlert ref={alertRef} title={error.title}>
          {error.detail}
        </FormAlert>
      ) : null}

      <SelectField id="pago-meses" label="Meses a pagar" options={MONTH_OPTIONS} value={months} onChange={(event) => setMonths(event.target.value)} disabled={pending} />

      <dl className="grid grid-cols-[1fr_auto] gap-x-4 gap-y-1.5 rounded-lg bg-concrete p-3 text-sm">
        <dt className="text-muted-foreground">Precio mensual</dt>
        <dd className="text-right tabular-nums">{formatCOP(plan.monthlyPriceCop)}</dd>
        <dt className="text-muted-foreground">Meses</dt>
        <dd className="text-right tabular-nums">{count}</dd>
        <dt className="font-semibold">Total estimado</dt>
        <dd className="text-right text-lg font-extrabold tabular-nums">{formatCOP(estimate)}</dd>
      </dl>
      <p className="-mt-2 text-xs text-muted-foreground">El valor final lo confirma el servidor al procesar el pago.</p>

      {simulated ? (
        <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
          <Checkbox checked={simulateRejection} onCheckedChange={(checked) => setSimulateRejection(checked === true)} disabled={pending} />
          <span>Simular que el pago es rechazado (solo para probar)</span>
        </label>
      ) : null}

      <DialogFooter>
        <DialogClose render={<Button type="button" variant="outline" className="h-11 px-4" disabled={pending} />}>Cancelar</DialogClose>
        <Button type="submit" className="h-11 px-4" disabled={pending || (!simulated && !wompi)}>
          {pending ? 'Procesando…' : simulated ? `Pagar ${formatCOP(estimate)} (prueba)` : 'Continuar a Wompi'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Cobro del plan: pago de prueba (`SIMULATED`) o redirección a Wompi (`WOMPI`). */
export function CheckoutDialog({ open, onOpenChange, plan, ...rest }: CheckoutDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>{plan ? <CheckoutForm plan={plan} onClose={() => onOpenChange(false)} {...rest} /> : null}</DialogContent>
    </Dialog>
  )
}
