import { AlertCircleIcon, CheckIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

export type StepStatus = 'idle' | 'complete' | 'error'

export interface StepperStep {
  id: string
  label: string
  status?: StepStatus
  /** Un paso bloqueado no se puede elegir (p. ej. aún no se llegó a él). */
  locked?: boolean
}

interface StepperProps {
  steps: StepperStep[]
  /** `id` del paso actual. */
  current: string
  onSelect: (id: string) => void
  /** Nombre accesible de la navegación. */
  label?: string
  className?: string
}

const STATUS_TEXT: Record<StepStatus, string> = { idle: '', complete: 'completo', error: 'con errores' }

/**
 * Indicador de pasos para formularios largos. Es una lista de botones nativos: se recorre con Tab,
 * se activa con Enter o Espacio y el paso actual lleva `aria-current="step"`. En móvil muestra los
 * círculos y el nombre del paso actual; desde `sm` muestra todos los nombres.
 */
export function Stepper({ steps, current, onSelect, label = 'Pasos del formulario', className }: StepperProps) {
  const index = Math.max(0, steps.findIndex((step) => step.id === current))
  const active = steps[index]
  return (
    <nav aria-label={label} className={cn('flex flex-col gap-2', className)}>
      <p className="text-sm font-semibold sm:sr-only" aria-hidden>
        Paso {index + 1} de {steps.length}
        {active ? <span className="font-normal text-muted-foreground"> · {active.label}</span> : null}
      </p>
      <ol className="flex items-center gap-1 sm:gap-2">
        {steps.map((step, position) => {
          const isCurrent = step.id === current
          const status = step.status ?? 'idle'
          return (
            <li key={step.id} className={cn('flex items-center gap-1 sm:gap-2', position < steps.length - 1 && 'sm:flex-1')}>
              <button
                type="button"
                onClick={() => onSelect(step.id)}
                disabled={step.locked && !isCurrent}
                aria-current={isCurrent ? 'step' : undefined}
                className={cn(
                  'flex min-h-11 items-center gap-2 rounded-full pr-1 text-sm outline-none transition-colors focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-50 sm:pr-3',
                  isCurrent ? 'font-semibold text-foreground' : 'text-muted-foreground hover:text-foreground',
                )}
              >
                <span
                  aria-hidden
                  className={cn(
                    'grid size-8 shrink-0 place-items-center rounded-full border text-xs font-bold',
                    isCurrent && 'border-primary bg-primary text-primary-foreground',
                    !isCurrent && status === 'complete' && 'border-primary/40 bg-primary/10 text-primary',
                    !isCurrent && status === 'error' && 'border-destructive bg-destructive/10 text-destructive',
                  )}
                >
                  {status === 'complete' && !isCurrent ? (
                    <CheckIcon className="size-4" />
                  ) : status === 'error' ? (
                    <AlertCircleIcon className="size-4" />
                  ) : (
                    position + 1
                  )}
                </span>
                <span className="sr-only sm:not-sr-only">{step.label}</span>
                {STATUS_TEXT[status] ? <span className="sr-only">({STATUS_TEXT[status]})</span> : null}
              </button>
              {position < steps.length - 1 ? <span aria-hidden className="hidden h-px flex-1 bg-border sm:block" /> : null}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
