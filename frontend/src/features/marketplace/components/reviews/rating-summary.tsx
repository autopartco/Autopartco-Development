import { formatNumber, pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ReviewSummary } from '../../api/types'
import { formatRating } from '../../lib/review-labels'
import { StarRating } from './star-rating'

interface RatingSummaryProps {
  summary: ReviewSummary
  /** Estrellas elegidas como filtro; `undefined` = todas. */
  selectedStars?: number
  /** Si se omite, las barras son solo informativas. */
  onSelectStars?: (stars: number | undefined) => void
  className?: string
}

/**
 * Promedio grande, conteo y una barra por estrella. Cuando hay `onSelectStars`, cada barra es un botón
 * (`aria-pressed`) que filtra la lista de opiniones; pulsarla otra vez quita el filtro.
 */
export function RatingSummary({ summary, selectedStars, onSelectStars, className }: RatingSummaryProps) {
  if (summary.average === undefined || summary.count <= 0) return null
  const interactive = Boolean(onSelectStars)
  return (
    <div className={cn('grid gap-5 sm:grid-cols-[auto_minmax(0,1fr)] sm:items-center sm:gap-8', className)}>
      <div className="flex flex-col gap-1.5">
        <p className="text-5xl leading-none font-semibold text-ap-ink tabular-nums">
          {formatRating(summary.average)}
          <span className="sr-only"> de 5</span>
        </p>
        <StarRating value={summary.average} size="lg" />
        <p className="text-sm text-ap-muted tabular-nums">{pluralize(summary.count, 'opinión', 'opiniones')}</p>
      </div>

      <div role={interactive ? 'group' : undefined} aria-label={interactive ? 'Filtrar opiniones por estrellas' : undefined}>
        <ul className="flex flex-col gap-1">
          {[5, 4, 3, 2, 1].map((stars) => {
            const count = summary.distribution[stars - 1] ?? 0
            const percent = summary.count > 0 ? (count / summary.count) * 100 : 0
            const pressed = selectedStars === stars
            const row = (
              <>
                <span className="w-9 shrink-0 text-left text-sm text-ap-ink tabular-nums">{stars} ★</span>
                <span aria-hidden className="h-2.5 min-w-0 flex-1 overflow-hidden rounded-full bg-ap-line">
                  <span className="block h-full rounded-full bg-ap-graphite" style={{ width: `${percent}%` }} />
                </span>
                <span className="w-10 shrink-0 text-right text-sm text-ap-muted tabular-nums">{formatNumber(count)}</span>
              </>
            )
            return (
              <li key={stars}>
                {interactive ? (
                  <button
                    type="button"
                    aria-pressed={pressed}
                    disabled={count === 0 && !pressed}
                    aria-label={`${stars} ${stars === 1 ? 'estrella' : 'estrellas'}, ${pluralize(count, 'opinión', 'opiniones')}`}
                    onClick={() => onSelectStars?.(pressed ? undefined : stars)}
                    className={cn(
                      'flex min-h-9 w-full items-center gap-3 rounded-md px-2 transition-colors pointer-coarse:min-h-11',
                      pressed ? 'bg-ap-paper ring-1 ring-ap-graphite' : 'hover:bg-ap-paper',
                      'disabled:cursor-not-allowed disabled:opacity-60 disabled:hover:bg-transparent',
                    )}
                  >
                    {row}
                  </button>
                ) : (
                  <div className="flex min-h-9 items-center gap-3 px-2">{row}</div>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </div>
  )
}
