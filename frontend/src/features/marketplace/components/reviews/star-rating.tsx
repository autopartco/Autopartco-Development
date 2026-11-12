import { StarIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { ratingLabel } from '../../lib/review-labels'

const SIZES = { sm: 'size-3.5', md: 'size-4', lg: 'size-6' } as const

/** Redondea a la media estrella más cercana: 4,7 se pinta como 4,5 y 4,8 como 5. */
function fillOf(value: number, index: number): 0 | 0.5 | 1 {
  const rest = Math.min(1, Math.max(0, value - index))
  return rest >= 0.75 ? 1 : rest >= 0.25 ? 0.5 : 0
}

interface StarRatingProps {
  /** Promedio de 0 a 5. */
  value: number
  size?: keyof typeof SIZES
  className?: string
}

/**
 * Estrellas de solo lectura con medias estrellas. El valor va en `aria-label` («4,7 de 5»); las estrellas
 * sueltas están ocultas para lectores de pantalla.
 */
export function StarRating({ value, size = 'md', className }: StarRatingProps) {
  const sizeClass = SIZES[size]
  return (
    <span className={cn('inline-flex items-center gap-0.5', className)} role="img" aria-label={ratingLabel(value)}>
      {Array.from({ length: 5 }, (_, index) => {
        const fill = fillOf(value, index)
        return (
          <span key={index} aria-hidden className={cn('relative inline-block shrink-0', sizeClass)}>
            <StarIcon className={cn('absolute inset-0 text-ap-line-strong', sizeClass)} />
            {fill > 0 ? (
              <span className="absolute inset-y-0 left-0 overflow-hidden" style={{ width: fill === 1 ? '100%' : '50%' }}>
                <StarIcon className={cn('fill-ap-graphite text-ap-graphite', sizeClass)} />
              </span>
            ) : null}
          </span>
        )
      })}
    </span>
  )
}
