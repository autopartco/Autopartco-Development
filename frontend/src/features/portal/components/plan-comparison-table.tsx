import { CheckIcon, ClockIcon, MinusIcon } from 'lucide-react'
import { formatNumber } from '@/lib/format'
import type { Plan } from '../api/types'
import { capabilityInfo, CAPABILITY_CATALOG, planLimits } from '../lib/plan-capabilities'

function limitCell(value: number | null): string {
  return value === null ? 'Ilimitado' : formatNumber(value)
}

const LIMIT_ROWS = [
  { label: 'Sedes (sucursales)', pick: 'branches' },
  { label: 'Productos', pick: 'products' },
  { label: 'Usuarios', pick: 'users' },
] as const

/** Tabla comparativa de cupos y funciones. En móvil se desplaza en horizontal (con indicio de scroll). */
export function PlanComparisonTable({ plans, currentCode }: { plans: Plan[]; currentCode: string }) {
  // Catálogo conocido primero; claves desconocidas que traiga algún plan, al final.
  const unknown = [...new Set(plans.flatMap((plan) => plan.capabilities))].filter((key) => !(key in CAPABILITY_CATALOG))
  const keys = [...Object.keys(CAPABILITY_CATALOG), ...unknown]

  return (
    <div className="flex flex-col gap-2">
      <p className="text-xs text-muted-foreground sm:hidden">Desliza la tabla hacia los lados para ver todos los planes.</p>
      <div
        className="overflow-x-auto rounded-lg border border-foreground/10 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
        tabIndex={0}
        role="region"
        aria-label="Tabla comparativa de planes"
      >
        <table className="w-full min-w-[40rem] border-collapse text-left text-sm">
          <caption className="sr-only">Cupos y funciones de cada plan</caption>
          <thead>
            <tr className="bg-concrete">
              <th scope="col" className="sticky left-0 z-10 bg-concrete p-3 font-semibold">
                Característica
              </th>
              {plans.map((plan) => (
                <th key={plan.id} scope="col" className="p-3 font-semibold">
                  {plan.name}
                  {plan.code === currentCode ? <span className="block text-xs font-medium text-muted-foreground">Tu plan actual</span> : null}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {LIMIT_ROWS.map((row) => (
              <tr key={row.pick} className="border-t border-foreground/10">
                <th scope="row" className="sticky left-0 z-10 bg-card p-3 font-medium">
                  {row.label}
                </th>
                {plans.map((plan) => (
                  <td key={plan.id} className="p-3 tabular-nums">
                    {limitCell(planLimits(plan)[row.pick])}
                  </td>
                ))}
              </tr>
            ))}
            {keys.map((key) => {
              const info = capabilityInfo(key)
              return (
                <tr key={key} className="border-t border-foreground/10">
                  <th scope="row" className="sticky left-0 z-10 bg-card p-3 font-medium">
                    {info.label}
                    {info.status === 'COMING_SOON' ? <span className="block text-xs font-normal text-muted-foreground">Próximamente</span> : null}
                  </th>
                  {plans.map((plan) => {
                    const listed = plan.capabilities.includes(key)
                    return (
                      <td key={plan.id} className="p-3">
                        {!listed ? (
                          <span className="inline-flex items-center gap-1.5 text-muted-foreground">
                            <MinusIcon className="size-4" aria-hidden />
                            <span className="sr-only">No incluida</span>
                          </span>
                        ) : info.status === 'AVAILABLE' ? (
                          <span className="inline-flex items-center gap-1.5">
                            <CheckIcon className="size-4 text-stock" aria-hidden />
                            <span className="sr-only">Incluida</span>
                          </span>
                        ) : (
                          <span className="inline-flex items-center gap-1.5 text-muted-foreground">
                            <ClockIcon className="size-4" aria-hidden />
                            En camino
                          </span>
                        )}
                      </td>
                    )
                  })}
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}
