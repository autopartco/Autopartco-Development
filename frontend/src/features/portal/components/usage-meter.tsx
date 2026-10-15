import { Link } from 'react-router'
import { TriangleAlertIcon } from 'lucide-react'
import { Progress } from '@/components/ui/progress'
import { cn } from '@/lib/utils'
import { formatNumber } from '@/lib/format'
import type { Capacity } from '../api/types'
import { CAPACITY_LABEL, usagePercent } from '../lib/labels'

interface UsageMeterProps {
  capacity: Capacity
  used: number
  limit: number | null
  /** Enlace "Mejorar plan" cuando el cupo está cerca o en el límite. */
  upgradeTo?: string
  className?: string
}

/** Uso de un cupo del plan: barra y texto ("40 de 100 productos"); aviso desde el 80 %. */
export function UsageMeter({ capacity, used, limit, upgradeTo, className }: UsageMeterProps) {
  const names = CAPACITY_LABEL[capacity]
  const percent = usagePercent(used, limit)
  const atLimit = limit !== null && used >= limit
  const nearLimit = percent !== null && percent >= 80
  const label = names.plural.charAt(0).toUpperCase() + names.plural.slice(1)
  const text =
    limit === null
      ? `${formatNumber(used)} ${used === 1 ? names.singular : names.plural} · sin límite`
      : `${formatNumber(used)} de ${formatNumber(limit)} ${limit === 1 ? names.singular : names.plural}`

  return (
    <div className={cn('flex flex-col gap-2', className)}>
      <div className="flex items-baseline justify-between gap-3">
        <span className="text-sm font-semibold">{label}</span>
        <span className="text-sm text-muted-foreground tabular-nums">{text}</span>
      </div>
      {percent !== null ? (
        <Progress
          value={percent}
          aria-label={`Uso de ${names.plural}`}
          getAriaValueText={() => text}
          indicatorClassName={cn(atLimit ? 'bg-destructive' : nearLimit ? 'bg-stock-low' : 'bg-foreground')}
        />
      ) : (
        <p className="text-xs text-muted-foreground">Tu plan no limita este recurso.</p>
      )}
      {nearLimit ? (
        <p className={cn('flex items-start gap-1.5 text-sm font-medium', atLimit ? 'text-destructive' : 'text-stock-low')}>
          <TriangleAlertIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
          <span>
            {atLimit ? `Llegaste al límite de ${names.plural}.` : `Estás cerca del límite de ${names.plural}.`}{' '}
            {upgradeTo ? (
              <Link to={upgradeTo} className="underline underline-offset-4">
                Mejorar plan
              </Link>
            ) : null}
          </span>
        </p>
      ) : null}
    </div>
  )
}
