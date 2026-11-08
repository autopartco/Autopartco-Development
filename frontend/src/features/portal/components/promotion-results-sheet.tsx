import { InfoIcon } from 'lucide-react'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { useAsync } from '@/lib/use-async'
import { formatCOP, formatNumber } from '@/lib/format'
import type { Promotion, PromotionResults } from '../api/promotion-types'
import type { PromotionConfig } from '../lib/promotion-config'
import { AUDIENCE_LABEL, discountText, formatFraction, MIN_EXPOSED_BUYERS } from '../lib/promotions'
import { formatDate } from '../lib/labels'
import { QueryBoundary } from './query-boundary'
import { StatCard } from './stat-card'
import { StatusBadge } from './status-badge'

function ResultsSkeleton() {
  return (
    <div role="status">
      <span className="sr-only">Cargando los resultados…</span>
      <div aria-hidden className="grid gap-3 sm:grid-cols-2">
        {Array.from({ length: 4 }, (_, index) => (
          <Skeleton key={index} className="h-28" />
        ))}
      </div>
    </div>
  )
}

function StatusLine({ results, audience }: { results: PromotionResults; audience: Promotion['audience'] }) {
  if (results.status === 'INSUFFICIENT_DATA') {
    return (
      <div role="note" className="flex items-start gap-2.5 rounded-md border border-ap-line bg-ap-paper p-4 text-sm text-ap-ink">
        <InfoIcon className="mt-0.5 size-4 shrink-0 text-ap-muted" aria-hidden />
        {audience === 'ALL' ? (
          <p>
            Esta promoción es para todos los compradores, así que no hay un grupo de control con el que comparar. Te mostramos las ventas atribuidas:
            lo que se vendió con el descuento aplicado.
          </p>
        ) : (
          <p>
            Todavía no hay datos suficientes para medir el efecto. Se necesitan al menos {MIN_EXPOSED_BUYERS} compradores expuestos al precio para
            compararlos con un grupo de control. Mientras tanto, estas son las ventas atribuidas a la promoción.
          </p>
        )}
      </div>
    )
  }
  return null
}

function ResultsView({ promotion, results }: { promotion: Promotion; results: PromotionResults }) {
  const hasEffect = results.lift !== null && results.controlConversion !== null && results.exposedConversion !== null
  const statusBadge =
    results.status === 'RUNNING' ? <StatusBadge tone="success">En curso</StatusBadge> : results.status === 'FINISHED' ? <StatusBadge>Terminada</StatusBadge> : <StatusBadge tone="warning">Datos insuficientes</StatusBadge>
  return (
    <div className="flex flex-col gap-5 overflow-y-auto px-4 pb-6">
      <div className="flex flex-wrap items-center gap-2 text-sm text-ap-muted">
        {statusBadge}
        <span>
          {discountText(promotion)} · {AUDIENCE_LABEL[promotion.audience]} · <span className="ap-ref">{formatDate(promotion.startsAt)} al {formatDate(promotion.endsAt)}</span>
        </span>
      </div>
      <StatusLine results={results} audience={promotion.audience} />
      <div className="grid gap-3 sm:grid-cols-2">
        <StatCard label="Ventas atribuidas" value={formatCOP(results.revenue)} hint={`${formatNumber(results.unitsSold)} unidades vendidas con la promoción`} />
        <StatCard label="Pedidos" value={formatNumber(results.orders)} hint={`${formatNumber(results.buyers)} compradores distintos`} />
        {promotion.audience === 'ALL' ? null : <StatCard label="Personas alcanzadas" value={formatNumber(results.exposures)} hint="Vieron el precio de la promoción" />}
        <StatCard
          label="Antes de la promoción"
          value={`${formatCOP(results.baseline.revenuePerDay)} al día`}
          hint={`${new Intl.NumberFormat('es-CO', { maximumFractionDigits: 1 }).format(results.baseline.ordersPerDay)} pedidos al día en los 28 días previos`}
        />
      </div>
      {hasEffect ? (
        <section aria-labelledby="efecto-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
          <h3 id="efecto-titulo" className="text-base font-semibold text-ap-ink">
            Efecto frente al grupo de control
          </h3>
          <p className="ap-price text-3xl leading-none text-ap-ink">
            {(results.lift ?? 0) >= 0 ? '+' : ''}
            {formatFraction(results.lift ?? 0)}
          </p>
          <p className="text-sm text-ap-muted">
            Con el precio de la promoción compró el {formatFraction(results.exposedConversion ?? 0)} de quienes lo vieron; en el grupo de control, que no lo
            vio, compró el {formatFraction(results.controlConversion ?? 0)}.
          </p>
        </section>
      ) : null}
    </div>
  )
}

interface PromotionResultsSheetProps {
  config: PromotionConfig
  promotion: Promotion | undefined
  onClose: () => void
}

/** Resultados de una promoción: alcance, ventas atribuidas y efecto frente al grupo de control. Todo lo calcula el servidor. */
export function PromotionResultsSheet({ config, promotion, onClose }: PromotionResultsSheetProps) {
  return (
    <Sheet open={promotion !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-lg">
        <SheetHeader className="p-4 pr-16">
          <SheetTitle className="text-block-title">Resultados{promotion ? `: ${promotion.name}` : ''}</SheetTitle>
          <SheetDescription>Qué logró esta promoción hasta hoy.</SheetDescription>
        </SheetHeader>
        {promotion ? <ResultsBody key={promotion.id} config={config} promotion={promotion} /> : null}
      </SheetContent>
    </Sheet>
  )
}

function ResultsBody({ config, promotion }: { config: PromotionConfig; promotion: Promotion }) {
  const result = useAsync(`${config.cacheKey}:results:${promotion.id}`, () => config.service.results(config.storeId, promotion.id))
  return (
    <div className="min-h-0 flex-1 overflow-y-auto">
      <QueryBoundary result={result} what="los resultados" skeleton={<div className="px-4"><ResultsSkeleton /></div>}>
        {(data) => <ResultsView promotion={promotion} results={data} />}
      </QueryBoundary>
    </div>
  )
}
