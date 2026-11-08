import { formatCOP } from '@/lib/format'
import type { Plan } from '../api/types'
import { annualPriceCop, annualSavingsCop } from '../lib/plan-capabilities'

export type Billing = 'MONTHLY' | 'ANNUAL'

/**
 * Precio de un plan en COP para la cabecera de la tabla comparativa. Ya no hay tarjetas de plan: la
 * comparación es una tabla (§8.5); este bloque conserva el cálculo del precio mensual o anual de referencia.
 */
export function PlanPrice({ plan, billing = 'MONTHLY' }: { plan: Plan; billing?: Billing }) {
  if (plan.monthlyPriceCop === 0) {
    return (
      <p className="flex flex-wrap items-baseline gap-x-1">
        <span className="ap-price text-xl text-ap-ink">Gratis</span>
        <span className="text-sm font-normal text-ap-muted">el primer mes</span>
      </p>
    )
  }
  const annual = billing === 'ANNUAL'
  return (
    <div className="flex flex-col gap-0.5">
      <p className="flex flex-wrap items-baseline gap-x-1 whitespace-nowrap">
        <span className="ap-price text-xl text-ap-ink">{formatCOP(annual ? annualPriceCop(plan) : plan.monthlyPriceCop)}</span>
        <span className="text-sm font-normal text-ap-muted">{annual ? '/ año' : '/ mes'}</span>
      </p>
      {annual ? <p className="text-xs font-semibold text-ap-ink">2 meses gratis: ahorras {formatCOP(annualSavingsCop(plan))}</p> : null}
    </div>
  )
}
