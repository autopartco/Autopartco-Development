import { MinusIcon, PlusIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface QuantityStepperProps {
  /** Nombre del repuesto, para que los botones sean claros con lector de pantalla. */
  label: string
  value: number
  min?: number
  max?: number
  onChange: (value: number) => void
  className?: string
}

const BUTTON =
  'grid size-11 place-items-center text-foreground transition-colors hover:bg-concrete disabled:pointer-events-none disabled:opacity-40'

/** Control de cantidad con botones de 44px; el valor se anuncia como región en vivo. */
export function QuantityStepper({ label, value, min = 1, max = 99, onChange, className }: QuantityStepperProps) {
  return (
    <div role="group" aria-label={`Cantidad de ${label}`} className={cn('inline-flex items-center overflow-hidden rounded-lg border border-input bg-card', className)}>
      <button type="button" className={BUTTON} aria-label="Quitar una unidad" disabled={value <= min} onClick={() => onChange(value - 1)}>
        <MinusIcon className="size-4" aria-hidden />
      </button>
      <output aria-live="polite" className="min-w-10 px-1 text-center font-semibold tabular-nums">
        {value}
      </output>
      <button type="button" className={BUTTON} aria-label="Agregar una unidad" disabled={value >= max} onClick={() => onChange(value + 1)}>
        <PlusIcon className="size-4" aria-hidden />
      </button>
    </div>
  )
}
