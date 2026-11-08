import type { ComponentType } from 'react'
import { CircleCheckIcon, CircleXIcon, ClockIcon, HourglassIcon, type LucideProps } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

export type PaymentIndicatorState = 'pending' | 'approved' | 'declined' | 'waiting'

const VIEW: Record<PaymentIndicatorState, { text: string; variant: 'info' | 'success' | 'danger' | 'warning'; icon: ComponentType<LucideProps> }> = {
  pending: { text: 'Verificando el pago', variant: 'info', icon: HourglassIcon },
  approved: { text: 'Pago aprobado', variant: 'success', icon: CircleCheckIcon },
  declined: { text: 'Pago no aprobado', variant: 'danger', icon: CircleXIcon },
  waiting: { text: 'Confirmación pendiente', variant: 'warning', icon: ClockIcon },
}

interface PaymentStatusIndicatorProps {
  state: PaymentIndicatorState
  /** Estado en texto para lectores de pantalla (región `role="status"`). */
  label: string
  className?: string
}

/**
 * Estado del resultado de un pago como etiqueta (§8.4): icono estático y texto explícito, sin
 * animaciones continuas. Solo refleja lo que el servidor ya confirmó; el detalle se anuncia en `label`.
 */
export function PaymentStatusIndicator({ state, label, className }: PaymentStatusIndicatorProps) {
  const view = VIEW[state]
  const Icon = view.icon
  return (
    <div className={cn('flex flex-col items-start', className)}>
      <Badge variant={view.variant} className="h-7 gap-1.5 px-2.5 text-sm [&>svg]:size-4!">
        <Icon aria-hidden />
        {view.text}
      </Badge>
      <p role="status" aria-live="polite" className="sr-only">
        {label}
      </p>
    </div>
  )
}
