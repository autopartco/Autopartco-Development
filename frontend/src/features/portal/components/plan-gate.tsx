import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ArrowUpRightIcon, LockIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { usePortal } from '../context'
import { capabilityInfo, PLAN_NAME } from '../lib/plan-capabilities'

interface UpgradeNoticeProps {
  /** Clave de la capacidad bloqueada (p. ej. `PROMOTIONS`). */
  capability: string
  /** Nombre del plan que la incluye; si falta se toma del catálogo. */
  planName?: string
  className?: string
}

/** Aviso «Disponible en el plan X» con acceso a la comparativa de planes. */
export function UpgradeNotice({ capability, planName, className }: UpgradeNoticeProps) {
  const { storeId } = usePortal()
  const info = capabilityInfo(capability)
  const name = planName ?? PLAN_NAME[info.minPlanCode]
  const comingSoon = info.status === 'COMING_SOON'
  return (
    <div role="note" className={`flex flex-col gap-3 rounded-lg border border-foreground/10 bg-concrete p-4 text-sm sm:flex-row sm:items-center sm:justify-between ${className ?? ''}`}>
      <p className="flex items-start gap-2.5">
        <LockIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
        <span>
          <strong className="font-semibold">{info.label}</strong>{' '}
          {comingSoon ? 'llegará pronto.' : name ? `está disponible en el plan ${name}.` : 'no está incluida en tu plan.'}
        </span>
      </p>
      {comingSoon ? null : (
        <Button variant="outline" className="h-11 shrink-0 bg-card px-4" nativeButton={false} render={<Link to={`/portal/${encodeURIComponent(storeId)}/plan`} />}>
          Ver planes
          <ArrowUpRightIcon aria-hidden />
        </Button>
      )}
    </div>
  )
}

interface PlanGateProps {
  capability: string
  /** Capacidades del plan actual (`subscription.capabilities`). */
  granted: readonly string[]
  children: ReactNode
}

/**
 * Muestra `children` si el plan incluye la capacidad; si no, el aviso de mejora.
 * Es solo presentación: el servidor sigue siendo quien autoriza cada operación.
 */
export function PlanGate({ capability, granted, children }: PlanGateProps) {
  return granted.includes(capability) ? <>{children}</> : <UpgradeNotice capability={capability} />
}
