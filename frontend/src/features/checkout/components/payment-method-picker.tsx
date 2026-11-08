import { useId } from 'react'
import { BanknoteIcon, CreditCardIcon, FlaskConicalIcon, LandmarkIcon, ShieldCheckIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/states'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { CheckoutOptionsDto, PaymentMethodCode, SimulatedOutcome } from '../api/types'

/** Ícono y ayuda por código conocido; un código nuevo del servidor se muestra solo con su nombre. */
const METHOD_HINTS: Record<string, { icon: typeof CreditCardIcon; hint: string }> = {
  WOMPI: { icon: ShieldCheckIcon, hint: 'Te llevamos a la pasarela segura de Wompi para pagar con tarjeta, PSE, Nequi o Bancolombia.' },
  // Solo existen con el proveedor SIMULATED (desarrollo local).
  SIMULATED_CARD: { icon: CreditCardIcon, hint: 'Pago inmediato con una tarjeta de demostración.' },
  SIMULATED_TRANSFER: { icon: LandmarkIcon, hint: 'Transferencia bancaria de demostración.' },
  SIMULATED_CASH: { icon: BanknoteIcon, hint: 'Pago en efectivo de demostración.' },
}

const OPTION_CLASS =
  'flex min-h-14 cursor-pointer items-center gap-3 rounded-md border border-ap-line-strong bg-ap-white p-3 transition-colors hover:bg-ap-paper has-data-checked:border-ap-red has-data-checked:bg-ap-red-tint has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-(--focus)'

/** Id del contenedor, para devolver el foco aquí desde el aviso de pago rechazado. */
export const METHOD_PICKER_ID = 'checkout-metodo-pago'

/** Enfoca la opción marcada (o la primera) del selector de método. */
export function focusMethodPicker(): void {
  const root = document.getElementById(METHOD_PICKER_ID)
  const target = root?.querySelector<HTMLElement>('[role="radio"][aria-checked="true"]') ?? root?.querySelector<HTMLElement>('[role="radio"]')
  target?.focus()
}

interface PaymentMethodPickerProps {
  status: 'loading' | 'error' | 'ready'
  options?: CheckoutOptionsDto
  method?: PaymentMethodCode
  outcome: SimulatedOutcome
  disabled?: boolean
  onMethodChange: (method: PaymentMethodCode) => void
  onOutcomeChange: (outcome: SimulatedOutcome) => void
  onRetry: () => void
}

/**
 * Métodos de pago que devuelve el servidor (`GET /marketplace/checkout-options`). El aviso de
 * demostración y el selector de resultado solo existen con el proveedor SIMULATED.
 */
export function PaymentMethodPicker({ status, options, method, outcome, disabled, onMethodChange, onOutcomeChange, onRetry }: PaymentMethodPickerProps) {
  const methodLabel = useId()
  const outcomeLabel = useId()

  if (status === 'loading') {
    return (
      <div role="status" aria-busy className="flex flex-col gap-2.5">
        <span className="sr-only">Cargando los métodos de pago</span>
        <Skeleton className="h-4 w-32" />
        <Skeleton className="h-16 w-full" />
      </div>
    )
  }
  if (status === 'error' || !options) {
    return <ErrorState title="No pudimos cargar los métodos de pago" description="Revisa tu conexión e inténtalo de nuevo. Tu carrito sigue igual." onRetry={onRetry} />
  }
  if (options.methods.length === 0) {
    return (
      <p role="alert" className="rounded-md border border-ap-warning/30 bg-ap-warning-soft px-3 py-2.5 text-sm text-ap-ink">
        Los pagos no están disponibles en este momento. Inténtalo de nuevo en unos minutos; tu carrito sigue guardado.
      </p>
    )
  }

  const simulated = options.provider === 'SIMULATED'
  return (
    <div className="flex flex-col gap-6">
      {simulated ? (
        <p className="flex items-start gap-2 rounded-md border border-ap-info/25 bg-ap-info-soft px-3 py-2.5 text-sm text-ap-ink">
          <FlaskConicalIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            <strong className="font-semibold">Demostración:</strong> no se hace ningún cobro real.
          </span>
        </p>
      ) : null}

      <div id={METHOD_PICKER_ID} className="flex flex-col gap-2.5">
        <p id={methodLabel} className="text-sm font-semibold text-ap-ink">
          Método de pago
        </p>
        <RadioGroup aria-labelledby={methodLabel} value={method} disabled={disabled} onValueChange={(value) => onMethodChange(value as PaymentMethodCode)}>
          {options.methods.map((option) => {
            const known = METHOD_HINTS[option.code]
            const Icon = known?.icon ?? CreditCardIcon
            return (
              <label key={option.code} className={OPTION_CLASS}>
                <RadioGroupItem value={option.code} />
                <Icon className="size-5 shrink-0 text-ap-muted" aria-hidden />
                <span className="flex min-w-0 flex-col">
                  <span className="font-medium text-ap-ink">{option.name}</span>
                  {known ? <span className="text-sm text-ap-muted">{known.hint}</span> : null}
                </span>
              </label>
            )
          })}
        </RadioGroup>
        {!simulated ? (
          <p className="text-xs text-ap-muted">Al pulsar «Pagar» te llevamos a Wompi. Al terminar, vuelves a Autopartco para ver el estado de tu pedido.</p>
        ) : null}
      </div>

      {simulated ? (
        <div className="flex flex-col gap-2.5">
          <p id={outcomeLabel} className="text-sm font-semibold text-ap-ink">
            Resultado de la simulación
          </p>
          <RadioGroup
            aria-labelledby={outcomeLabel}
            value={outcome}
            disabled={disabled}
            onValueChange={(value) => onOutcomeChange(value as SimulatedOutcome)}
            className="grid-cols-2"
          >
            {(
              [
                ['APPROVED', 'Aprobado'],
                ['REJECTED', 'Rechazado'],
              ] as const
            ).map(([value, label]) => (
              <label key={value} className={cn(OPTION_CLASS, 'min-h-11')}>
                <RadioGroupItem value={value} />
                <span className="font-medium text-ap-ink">{label}</span>
              </label>
            ))}
          </RadioGroup>
          <p className="text-xs text-ap-muted">Sirve para ver cómo responde la tienda cuando el pago se aprueba o se rechaza.</p>
        </div>
      ) : null}
    </div>
  )
}
