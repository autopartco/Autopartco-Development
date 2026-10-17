import { useId, useState, type ReactNode } from 'react'
import { CheckIcon, ChevronDownIcon, ClockIcon } from 'lucide-react'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { formatCOP } from '@/lib/format'
import type { Plan } from '../api/types'
import { limitText } from '../lib/labels'
import { annualPriceCop, annualSavingsCop, isCustomPlan, planCapabilities } from '../lib/plan-capabilities'

export type Billing = 'MONTHLY' | 'ANNUAL'

interface PlanCardProps {
  plan: Plan
  current: boolean
  /** Periodo mostrado en el precio; por defecto mensual. */
  billing?: Billing
  /** Botón de acción (o nota) bajo la lista. */
  action: ReactNode
}

function PriceBlock({ plan, billing }: { plan: Plan; billing: Billing }) {
  if (isCustomPlan(plan)) {
    return <p className="text-[clamp(1.5rem,2.2vw,1.875rem)] font-extrabold tracking-tight">A medida</p>
  }
  if (plan.monthlyPriceCop === 0) {
    return (
      <p className="flex items-baseline gap-1">
        <span className="text-[clamp(1.5rem,2.2vw,1.875rem)] font-extrabold tracking-tight tabular-nums">Gratis</span>
        <span className="text-sm text-muted-foreground">el primer mes</span>
      </p>
    )
  }
  const annual = billing === 'ANNUAL'
  return (
    <div className="flex flex-col gap-1">
      <p className="flex items-baseline gap-1 whitespace-nowrap">
        <span className="text-[clamp(1.5rem,2.2vw,1.875rem)] font-extrabold tracking-tight tabular-nums">{formatCOP(annual ? annualPriceCop(plan) : plan.monthlyPriceCop)}</span>
        <span className="text-sm text-muted-foreground">{annual ? '/ año' : '/ mes'}</span>
      </p>
      {annual ? (
        <p className="text-sm font-semibold text-foreground">2 meses gratis: ahorras {formatCOP(annualSavingsCop(plan))}</p>
      ) : null}
    </div>
  )
}

function ComingSoon({ items }: { items: { key: string; label: string }[] }) {
  const [open, setOpen] = useState(false)
  const listId = useId()
  if (items.length === 0) return null
  return (
    <div className="flex flex-col gap-2 border-t border-foreground/10 pt-3">
      <button
        type="button"
        aria-expanded={open}
        aria-controls={listId}
        onClick={() => setOpen((value) => !value)}
        className="inline-flex min-h-11 w-fit items-center gap-1.5 text-sm font-medium text-muted-foreground hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      >
        <ClockIcon className="size-4" aria-hidden />
        +{items.length} {items.length === 1 ? 'función en camino' : 'funciones en camino'}
        <ChevronDownIcon className={cn('size-4 transition-transform motion-reduce:transition-none', open && 'rotate-180')} aria-hidden />
      </button>
      <ul id={listId} hidden={!open} className="flex flex-col gap-1.5 text-sm text-muted-foreground">
        {items.map((info) => (
          <li key={info.key}>
            {info.label}{' '}
            <span className="ml-1 inline-flex h-5 items-center rounded-full bg-concrete px-2 text-xs font-semibold text-foreground">Próximamente</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

/** Tarjeta de un plan: precio en COP, cupos, funciones disponibles, funciones «En camino» y distintivos. */
export function PlanCard({ plan, current, billing = 'MONTHLY', action }: PlanCardProps) {
  const titleId = `plan-${plan.code}`
  const { available, comingSoon } = planCapabilities(plan)
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

      <PriceBlock plan={plan} billing={billing} />

      <div className="flex flex-1 flex-col gap-3">
        <ul className="flex flex-col gap-2 text-sm">
          {[
            limitText(plan.maxBranches, 'BRANCHES'),
            limitText(plan.maxProducts, 'PRODUCTS'),
            limitText(plan.maxUsers, 'USERS'),
            ...available.map((info) => info.label),
          ].map((line) => (
            <li key={line} className="flex items-start gap-2">
              <CheckIcon className="mt-0.5 size-4 shrink-0 text-stock" aria-hidden />
              <span>{line}</span>
            </li>
          ))}
        </ul>
        <ComingSoon items={comingSoon} />
      </div>

      <div className="flex flex-col gap-2">{action}</div>
    </Card>
  )
}
