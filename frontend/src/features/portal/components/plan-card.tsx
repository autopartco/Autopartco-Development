import type { ReactNode } from 'react'
import { CheckIcon } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { formatCOP } from '@/lib/format'
import type { Plan } from '../api/types'
import { capabilityLabel, limitText } from '../lib/labels'

interface PlanCardProps {
  plan: Plan
  current: boolean
  /** Botón de acción (o nota) bajo la lista. */
  action: ReactNode
}

/** Tarjeta de un plan: precio mensual en COP, límites, funciones incluidas y distintivos. */
export function PlanCard({ plan, current, action }: PlanCardProps) {
  const free = plan.monthlyPriceCop === 0
  const titleId = `plan-${plan.code}`
  return (
    <Card
      role="group"
      aria-labelledby={titleId}
      className={cn('h-full gap-4 p-5 ring-1', plan.recommended ? 'ring-2 ring-recommended' : 'ring-foreground/10')}
    >
      <div className="flex flex-col gap-1.5">
        <div className="flex flex-wrap items-center gap-2">
          <h3 id={titleId} className="text-xl font-extrabold tracking-tight">
            {plan.name}
          </h3>
          {plan.recommended ? (
            <span className="inline-flex h-6 items-center rounded-full bg-recommended-soft px-2.5 text-xs font-semibold text-recommended">
              Recomendado
            </span>
          ) : null}
          {current ? (
            <span className="inline-flex h-6 items-center rounded-full bg-asphalt px-2.5 text-xs font-semibold text-white">
              Tu plan actual
            </span>
          ) : null}
        </div>
        {plan.description ? <p className="text-sm text-muted-foreground">{plan.description}</p> : null}
      </div>

      <p className="flex items-baseline gap-1">
        <span className="text-3xl font-extrabold tracking-tight tabular-nums">{free ? 'Gratis' : formatCOP(plan.monthlyPriceCop)}</span>
        {free ? null : <span className="text-sm text-muted-foreground">/ mes</span>}
      </p>

      <ul className="flex flex-1 flex-col gap-2 text-sm">
        {[
          limitText(plan.maxBranches, 'BRANCHES'),
          limitText(plan.maxProducts, 'PRODUCTS'),
          limitText(plan.maxUsers, 'USERS'),
          ...plan.capabilities.map(capabilityLabel),
        ].map((line) => (
          <li key={line} className="flex items-start gap-2">
            <CheckIcon className="mt-0.5 size-4 shrink-0 text-stock" aria-hidden />
            <span>{line}</span>
          </li>
        ))}
      </ul>

      <div className="flex flex-col gap-2">{action}</div>
    </Card>
  )
}
