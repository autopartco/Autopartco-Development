import { cn } from '@/lib/utils'
import { aspectCopy, type ReviewKind } from '../../lib/review-labels'

interface ReviewHighlightsProps {
  kind: ReviewKind
  /** Códigos que el servidor destacó (ya filtrados por mínimo y umbral). */
  highlights: string[]
  /** Máximo de insignias; el servidor ya envía hasta 3. */
  max?: number
  className?: string
}

/** Insignias con ícono («Entrega rápida», «Encaja perfecto»). Sin destacadas no dibuja nada. */
export function ReviewHighlights({ kind, highlights, max = 3, className }: ReviewHighlightsProps) {
  const shown = highlights.slice(0, max)
  if (shown.length === 0) return null
  return (
    <ul aria-label="Lo que más destacan los compradores" className={cn('flex flex-wrap gap-1.5', className)}>
      {shown.map((code) => {
        const copy = aspectCopy(kind, code)
        const Icon = copy.icon
        return (
          <li
            key={code}
            className="inline-flex min-h-7 items-center gap-1.5 rounded-sm border border-ap-success/25 bg-ap-success-soft px-2 py-0.5 text-xs font-semibold text-ap-success"
          >
            <Icon aria-hidden className="size-3.5 shrink-0" />
            {copy.badge}
          </li>
        )
      })}
    </ul>
  )
}
