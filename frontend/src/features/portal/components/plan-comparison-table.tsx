import type { ReactNode } from 'react'
import { CheckIcon, MinusIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { formatNumber } from '@/lib/format'
import type { Plan } from '../api/types'
import { capabilityInfo, CAPABILITY_CATALOG, planIncludes, planLimits } from '../lib/plan-capabilities'
import { PlanPrice, type Billing } from './plan-card'

function limitCell(value: number | null): string {
  return value === null ? 'Ilimitado' : formatNumber(value)
}

const LIMIT_ROWS = [
  { label: 'Sedes (sucursales)', pick: 'branches' },
  { label: 'Productos', pick: 'products' },
  { label: 'Usuarios', pick: 'users' },
] as const

interface PlanComparisonTableProps {
  plans: Plan[]
  currentCode: string
  billing?: Billing
  /** Acción por plan (botón o nota), en la última fila. */
  renderAction?: (plan: Plan) => ReactNode
}

/**
 * Comparación de planes como tabla (§8.5): una columna por plan con precio, estado («Tu plan actual»,
 * «Recomendado») en texto, cupos, funciones y la acción al pie. Las funciones disponibles van antes que las
 * «En camino», que se rotulan para no presentarlas como contratadas. En móvil se desplaza en horizontal con la
 * primera columna fija. El plan actual lleva la línea de acento superior.
 */
export function PlanComparisonTable({ plans, currentCode, billing = 'MONTHLY', renderAction }: PlanComparisonTableProps) {
  // Catálogo conocido primero (disponibles y luego «en camino»); claves desconocidas que traiga algún plan, al final.
  const unknown = [...new Set(plans.flatMap((plan) => plan.capabilities))].filter((key) => !(key in CAPABILITY_CATALOG))
  const known = Object.keys(CAPABILITY_CATALOG)
  const available = known.filter((key) => capabilityInfo(key).status === 'AVAILABLE')
  const coming = [...known.filter((key) => capabilityInfo(key).status !== 'AVAILABLE'), ...unknown]
  const isCurrent = (plan: Plan) => plan.code === currentCode
  const cellClass = (plan: Plan) => cn('p-3 align-top', isCurrent(plan) && 'bg-ap-paper/60')

  const featureRows = (keys: string[]) =>
    keys.map((key) => {
      const info = capabilityInfo(key)
      return (
        <tr key={key} className="border-t border-ap-line">
          <th scope="row" className="sticky left-0 z-10 bg-ap-white p-3 text-left font-medium text-ap-ink">
            {info.label}
          </th>
          {plans.map((plan) => {
            const listed = planIncludes(plan, key)
            return (
              <td key={plan.id} className={cellClass(plan)}>
                {!listed ? (
                  <span className="inline-flex items-center gap-1.5 text-ap-muted">
                    <MinusIcon className="size-4" aria-hidden />
                    No incluida
                  </span>
                ) : info.status === 'AVAILABLE' ? (
                  <span className="inline-flex items-center gap-1.5 font-medium text-ap-ink">
                    <CheckIcon className="size-4 text-ap-success" aria-hidden />
                    Incluida
                  </span>
                ) : (
                  <span className="text-ap-muted">En camino</span>
                )}
              </td>
            )
          })}
        </tr>
      )
    })

  const groupRow = (label: string, hint?: string) => (
    <tr className="border-t border-ap-line-strong bg-ap-paper">
      <th scope="colgroup" colSpan={plans.length + 1} className="sticky left-0 px-3 py-2 text-left">
        <span className="ap-index">{label}</span>
        {hint ? <span className="ml-2 text-xs font-normal text-ap-muted">{hint}</span> : null}
      </th>
    </tr>
  )

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs text-ap-muted sm:hidden">Desliza la tabla hacia los lados para ver todos los planes.</p>
      <div className="overflow-x-auto rounded-md border border-ap-line-strong" tabIndex={0} role="region" aria-label="Comparación de planes">
        <table className="w-full min-w-[36rem] border-collapse text-left text-sm">
          <caption className="sr-only">Precio, cupos y funciones de cada plan</caption>
          <thead>
            <tr>
              <th scope="col" className="sticky left-0 z-10 w-[38%] bg-ap-white p-3 align-bottom font-semibold text-ap-muted">
                Plan
              </th>
              {plans.map((plan) => (
                <th
                  key={plan.id}
                  scope="col"
                  className={cn('border-t-2 p-3 align-top font-normal', isCurrent(plan) ? 'border-t-ap-red bg-ap-paper/60' : 'border-t-transparent')}
                >
                  <span className="flex flex-col gap-1.5">
                    <span className="text-base font-semibold text-ap-ink">{plan.name}</span>
                    {isCurrent(plan) || plan.recommended ? (
                      <span className="ap-index text-ap-ink">
                        {[isCurrent(plan) ? 'Tu plan actual' : null, plan.recommended ? 'Recomendado' : null].filter(Boolean).join(' · ')}
                      </span>
                    ) : null}
                    {plan.description ? <span className="text-sm text-ap-muted">{plan.description}</span> : null}
                    <PlanPrice plan={plan} billing={billing} />
                  </span>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {groupRow('Cupos')}
            {LIMIT_ROWS.map((row) => (
              <tr key={row.pick} className="border-t border-ap-line">
                <th scope="row" className="sticky left-0 z-10 bg-ap-white p-3 text-left font-medium text-ap-ink">
                  {row.label}
                </th>
                {plans.map((plan) => (
                  <td key={plan.id} className={cn(cellClass(plan), 'ap-ref text-sm text-ap-ink')}>
                    {limitCell(planLimits(plan)[row.pick])}
                  </td>
                ))}
              </tr>
            ))}
            {groupRow('Funciones')}
            {featureRows(available)}
            {coming.length > 0 ? groupRow('En camino', 'Aún no están activas y no cuentan como funciones contratadas.') : null}
            {featureRows(coming)}
          </tbody>
          {renderAction ? (
            <tfoot>
              <tr className="border-t border-ap-line-strong">
                <th scope="row" className="sticky left-0 z-10 bg-ap-white p-3 text-left font-medium text-ap-muted">
                  <span className="sr-only">Acción</span>
                </th>
                {plans.map((plan) => (
                  <td key={plan.id} className={cellClass(plan)}>
                    <div className="flex flex-col gap-2">{renderAction(plan)}</div>
                  </td>
                ))}
              </tr>
            </tfoot>
          ) : null}
        </table>
      </div>
    </div>
  )
}
