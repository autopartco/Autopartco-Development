import { formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ReviewAspectSummary } from '../../api/types'
import { aspectCopy, aspectRank, formatRating, type ReviewKind } from '../../lib/review-labels'

interface AspectBarsProps {
  kind: ReviewKind
  aspects: ReviewAspectSummary[]
  className?: string
}

/**
 * Promedio por categoría («Rapidez de entrega 4,6») en barras horizontales. El valor siempre va en texto:
 * la barra solo refuerza. Sin categorías puntuadas no dibuja nada.
 */
export function AspectBars({ kind, aspects, className }: AspectBarsProps) {
  if (aspects.length === 0) return null
  const sorted = aspects.toSorted((a, b) => aspectRank(kind, a.aspect) - aspectRank(kind, b.aspect))
  return (
    <section aria-label="Calificación por categoría" className={cn('flex flex-col gap-3', className)}>
      <h3 className="text-sm font-semibold text-ap-ink">Por categoría</h3>
      <ul className="flex flex-col gap-3">
        {sorted.map((item) => {
          const copy = aspectCopy(kind, item.aspect)
          return (
            <li key={item.aspect} className="flex flex-col gap-1">
              <div className="flex items-baseline justify-between gap-3 text-sm">
                <span className="min-w-0 text-ap-ink">{copy.label}</span>
                <span className="shrink-0 font-semibold text-ap-ink tabular-nums">
                  {formatRating(item.average)}
                  <span className="sr-only"> de 5, {formatNumber(item.count)} calificaciones</span>
                </span>
              </div>
              <div aria-hidden className="h-2 overflow-hidden rounded-full bg-ap-line">
                <div className="h-full rounded-full bg-ap-graphite" style={{ width: `${Math.min(100, (item.average / 5) * 100)}%` }} />
              </div>
            </li>
          )
        })}
      </ul>
    </section>
  )
}
