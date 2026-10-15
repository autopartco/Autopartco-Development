import { useId } from 'react'
import { BanknoteIcon, CreditCardIcon, FlaskConicalIcon, LandmarkIcon } from 'lucide-react'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { cn } from '@/lib/utils'
import { PAYMENT_METHODS, type PaymentMethodCode, type SimulatedOutcome } from '../api/types'

const ICONS: Record<PaymentMethodCode, typeof CreditCardIcon> = {
  SIMULATED_CARD: CreditCardIcon,
  SIMULATED_TRANSFER: LandmarkIcon,
  SIMULATED_CASH: BanknoteIcon,
}

const OPTION_CLASS =
  'flex min-h-14 cursor-pointer items-center gap-3 rounded-lg border border-border bg-card p-3 transition-colors hover:bg-concrete/60 has-data-checked:border-primary has-data-checked:bg-primary/5 has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-(--focus)'

/** Id del contenedor, para devolver el foco aquí desde el aviso de pago rechazado. */
export const METHOD_PICKER_ID = 'checkout-metodo-pago'

/** Enfoca la opción marcada (o la primera) del selector de método. */
export function focusMethodPicker(): void {
  const root = document.getElementById(METHOD_PICKER_ID)
  const target = root?.querySelector<HTMLElement>('[role="radio"][aria-checked="true"]') ?? root?.querySelector<HTMLElement>('[role="radio"]')
  target?.focus()
}

interface PaymentMethodPickerProps {
  method: PaymentMethodCode
  outcome: SimulatedOutcome
  disabled?: boolean
  onMethodChange: (method: PaymentMethodCode) => void
  onOutcomeChange: (outcome: SimulatedOutcome) => void
}

/** Método de pago simulado y resultado de la simulación (único proveedor del MVP). */
export function PaymentMethodPicker({ method, outcome, disabled, onMethodChange, onOutcomeChange }: PaymentMethodPickerProps) {
  const methodLabel = useId()
  const outcomeLabel = useId()
  return (
    <div className="flex flex-col gap-6">
      <p className="flex items-start gap-2 rounded-lg bg-concrete px-3 py-2.5 text-sm text-foreground/85">
        <FlaskConicalIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>
          <strong className="font-semibold">Demostración:</strong> no se hace ningún cobro real.
        </span>
      </p>

      <div id={METHOD_PICKER_ID} className="flex flex-col gap-2.5">
        <p id={methodLabel} className="text-sm font-semibold">
          Método de pago
        </p>
        <RadioGroup aria-labelledby={methodLabel} value={method} disabled={disabled} onValueChange={(value) => onMethodChange(value as PaymentMethodCode)}>
          {PAYMENT_METHODS.map((option) => {
            const Icon = ICONS[option.code]
            return (
              <label key={option.code} className={OPTION_CLASS}>
                <RadioGroupItem value={option.code} />
                <Icon className="size-5 shrink-0 text-muted-foreground" aria-hidden />
                <span className="flex min-w-0 flex-col">
                  <span className="font-medium">{option.name}</span>
                  <span className="text-sm text-muted-foreground">{option.hint}</span>
                </span>
              </label>
            )
          })}
        </RadioGroup>
      </div>

      <div className="flex flex-col gap-2.5">
        <p id={outcomeLabel} className="text-sm font-semibold">
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
              <span className="font-medium">{label}</span>
            </label>
          ))}
        </RadioGroup>
        <p className="text-xs text-muted-foreground">Sirve para ver cómo responde la tienda cuando el pago se aprueba o se rechaza.</p>
      </div>
    </div>
  )
}
