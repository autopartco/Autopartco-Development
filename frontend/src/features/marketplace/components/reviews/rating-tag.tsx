import { StarIcon } from 'lucide-react'
import { formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { formatRating } from '../../lib/review-labels'

interface RatingTagProps {
  rating: number | undefined
  reviewCount: number | undefined
  className?: string
}

/**
 * «★ 4,7 (128)» para tarjetas y filas. Sin reseñas no dibuja nada: nunca «0 estrellas» ni «0,0».
 */
export function RatingTag({ rating, reviewCount, className }: RatingTagProps) {
  if (rating === undefined || !reviewCount || reviewCount <= 0) return null
  return (
    <span
      className={cn('inline-flex items-center gap-1 text-sm text-ap-muted tabular-nums', className)}
      role="img"
      aria-label={`${formatRating(rating)} de 5, ${formatNumber(reviewCount)} ${reviewCount === 1 ? 'opinión' : 'opiniones'}`}
    >
      <StarIcon aria-hidden className="size-3.5 fill-ap-graphite text-ap-graphite" />
      <span aria-hidden className="font-semibold text-ap-ink">
        {formatRating(rating)}
      </span>
      <span aria-hidden>({formatNumber(reviewCount)})</span>
    </span>
  )
}
