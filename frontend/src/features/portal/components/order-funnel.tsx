import { formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { SalesAnalytics, SalesFunnelStage } from '../api/types'
import { useRevealed } from '../lib/motion'
import { Reveal } from './bento'

const STAGE_LABEL: Record<SalesFunnelStage, string> = {
  RECEIVED: 'Recibidos',
  PAID: 'Pagados',
  SHIPPED: 'Enviados',
  DELIVERED: 'Entregados',
}

const STAGE_HINT: Record<SalesFunnelStage, string> = {
  RECEIVED: 'Pedidos que entraron (sin cancelados)',
  PAID: 'Con pago aprobado',
  SHIPPED: 'Despachados al comprador',
  DELIVERED: 'Recibidos por el comprador',
}

function Bar({ ratio, tone }: { ratio: number; tone: string }) {
  const revealed = useRevealed()
  return (
    <div className="h-3 overflow-hidden rounded-full bg-muted" aria-hidden>
      <div
        style={{ width: `${Math.max(ratio, ratio > 0 ? 2 : 0)}%` }}
        className={cn(
          'h-full origin-left scale-x-0 rounded-full transition-transform duration-1000 ease-out-thread motion-reduce:scale-x-100 motion-reduce:transition-none',
          revealed && 'scale-x-100',
          tone,
        )}
      />
    </div>
  )
}

/** Embudo acumulado de pedidos: cada barra se mide contra la primera etapa. */
export function OrderFunnel({ funnel, cancelled }: Pick<SalesAnalytics, 'funnel' | 'cancelled'>) {
  const first = funnel[0]?.count ?? 0
  return (
    <div className="flex flex-col gap-4">
      <ol className="flex flex-col gap-4">
        {funnel.map((step, index) => {
          const previous = index === 0 ? undefined : funnel[index - 1]?.count
          const ratio = first > 0 ? (step.count / first) * 100 : 0
          return (
            <Reveal as="li" key={step.stage} index={index} className="flex flex-col gap-1.5">
              <div className="flex items-baseline justify-between gap-2">
                <p className="text-sm font-semibold">
                  {STAGE_LABEL[step.stage]} <span className="font-normal text-muted-foreground">· {STAGE_HINT[step.stage]}</span>
                </p>
                <p className="text-sm font-bold tabular-nums">
                  {formatNumber(step.count)}
                  {previous ? <span className="ml-1.5 text-xs font-normal text-muted-foreground">{Math.round((step.count / previous) * 100)} % del paso anterior</span> : null}
                </p>
              </div>
              <Bar ratio={ratio} tone={index === funnel.length - 1 ? 'bg-stock' : 'bg-primary'} />
            </Reveal>
          )
        })}
      </ol>
      <p className="text-xs text-muted-foreground">
        {cancelled > 0 ? `${formatNumber(cancelled)} cancelados o rechazados no entran al embudo.` : 'Sin pedidos cancelados en este rango.'}
      </p>
    </div>
  )
}
