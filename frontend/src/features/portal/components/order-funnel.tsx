import { formatNumber } from '@/lib/format'
import type { SalesAnalytics, SalesFunnelStage } from '../api/types'
import { CHART_BG } from '../lib/chart-palette'

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

const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 0 })

/**
 * Embudo acumulado de pedidos (§20.4 G): una sola serie (rojo), cada barra medida contra la primera
 * etapa y con la cifra y el porcentaje en texto (la barra es solo apoyo). La etapa con la mayor caída
 * se señala con texto para responder «¿en qué paso se quedan?».
 */
export function OrderFunnel({ funnel, cancelled }: Pick<SalesAnalytics, 'funnel' | 'cancelled'>) {
  const first = funnel[0]?.count ?? 0
  // Mayor pérdida entre dos etapas seguidas (en pedidos), si la hay.
  const drops = funnel.slice(1).map((step, index) => ({ stage: step.stage, lost: (funnel[index]?.count ?? 0) - step.count }))
  const worst = drops.reduce<{ stage: SalesFunnelStage; lost: number } | undefined>((found, item) => (item.lost > 0 && item.lost > (found?.lost ?? 0) ? item : found), undefined)

  return (
    <div className="flex flex-col gap-4">
      {worst ? (
        <p className="text-sm text-ap-ink">
          La mayor caída está en el paso a <strong className="font-semibold">{STAGE_LABEL[worst.stage].toLowerCase()}</strong>: {formatNumber(worst.lost)}{' '}
          {worst.lost === 1 ? 'pedido aún no llega a esa etapa' : 'pedidos aún no llegan a esa etapa'}.
        </p>
      ) : null}
      <ol className="flex flex-col gap-3">
        {funnel.map((step, index) => {
          const previous = index === 0 ? undefined : funnel[index - 1]?.count
          const ratio = first > 0 ? (step.count / first) * 100 : 0
          return (
            <li key={step.stage} className="flex flex-col gap-1.5">
              <div className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-0.5">
                <p className="text-sm font-semibold text-ap-ink">
                  {STAGE_LABEL[step.stage]} <span className="font-normal text-ap-muted">· {STAGE_HINT[step.stage]}</span>
                </p>
                <p className="text-sm">
                  <span className="ap-price text-ap-ink">{formatNumber(step.count)}</span>
                  <span className="ml-2 text-xs text-ap-muted tabular-nums">
                    {index === 0 ? '100 %' : `${percent.format(first > 0 ? step.count / first : 0)} del total`}
                    {previous ? ` · ${percent.format(step.count / previous)} del paso anterior` : ''}
                  </span>
                </p>
              </div>
              <div className={`h-2.5 ${CHART_BG.track}`} aria-hidden>
                <div style={{ width: `${Math.max(ratio, ratio > 0 ? 1 : 0)}%` }} className={`h-full ${CHART_BG.primary}`} />
              </div>
            </li>
          )
        })}
      </ol>
      <p className="text-xs text-ap-muted">
        {cancelled > 0 ? `${formatNumber(cancelled)} cancelados o rechazados no entran al embudo.` : 'Sin pedidos cancelados en este rango.'}
      </p>
    </div>
  )
}
