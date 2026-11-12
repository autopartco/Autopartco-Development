import { useId, useState } from 'react'
import { BadgeCheckIcon, CornerDownRightIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import type { Review, ReviewSort, ReviewTarget } from '../../api/types'
import { useReviews } from '../../hooks'
import { aspectCopy, aspectRank, relativeDate, type ReviewKind } from '../../lib/review-labels'
import { StarRating } from './star-rating'

const dateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' })

const SORT_OPTIONS: { value: ReviewSort; label: string }[] = [
  { value: 'recent', label: 'Más recientes' },
  { value: 'highest', label: 'Mejor calificadas' },
  { value: 'lowest', label: 'Peor calificadas' },
]

const chip = (pressed: boolean) =>
  cn(
    'inline-flex min-h-11 items-center rounded-md border px-3 text-sm font-medium transition-colors',
    pressed ? 'border-ap-graphite bg-ap-graphite text-ap-white' : 'border-input bg-ap-white text-ap-ink hover:border-ap-graphite hover:bg-ap-paper',
  )

function ReviewItem({ review, kind, storeName }: { review: Review; kind: ReviewKind; storeName?: string }) {
  const scored = Object.entries(review.aspects).toSorted(([a], [b]) => aspectRank(kind, a) - aspectRank(kind, b))
  return (
    <li className="flex flex-col gap-2.5 py-5">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <StarRating value={review.rating} />
        <span className="text-sm font-semibold text-ap-ink">{review.author}</span>
        {review.verified ? (
          <span className="inline-flex items-center gap-1 text-xs font-semibold text-ap-success">
            <BadgeCheckIcon aria-hidden className="size-3.5" />
            Compra verificada
          </span>
        ) : null}
        <time dateTime={review.createdAt} title={dateFormatter.format(new Date(review.createdAt))} className="text-sm text-ap-muted">
          {relativeDate(review.createdAt)}
        </time>
      </div>

      {review.comment ? <p className="max-w-prose text-sm break-words whitespace-pre-line text-ap-ink">{review.comment}</p> : null}

      {scored.length > 0 ? (
        <ul aria-label="Categorías calificadas" className="flex flex-wrap gap-1.5">
          {scored.map(([code, value]) => (
            <li key={code} className="rounded-sm border border-ap-line bg-ap-paper px-2 py-0.5 text-xs text-ap-muted">
              {aspectCopy(kind, code).label}: <span className="font-semibold text-ap-ink tabular-nums">{value}/5</span>
            </li>
          ))}
        </ul>
      ) : null}

      {review.reply ? (
        <div className="mt-1 flex max-w-prose flex-col gap-1 rounded-md border border-ap-line border-l-4 border-l-ap-graphite bg-ap-paper p-3">
          <p className="flex flex-wrap items-center gap-x-2 text-xs font-semibold text-ap-ink">
            <CornerDownRightIcon aria-hidden className="size-3.5" />
            Respuesta {storeName ? `de ${storeName}` : 'de la tienda'}
            <time dateTime={review.reply.repliedAt} className="font-normal text-ap-muted">
              {relativeDate(review.reply.repliedAt)}
            </time>
          </p>
          <p className="text-sm break-words whitespace-pre-line text-ap-ink">{review.reply.text}</p>
        </div>
      ) : null}
    </li>
  )
}

interface ReviewListProps {
  target: ReviewTarget
  /** Define las etiquetas de categoría; normalmente sigue a `target.type`. */
  kind: ReviewKind
  /** Nombre del almacén para titular su respuesta. */
  storeName?: string
  /** Filtro de estrellas controlado (compartido con las barras del resumen). */
  stars?: number
  onStarsChange: (stars: number | undefined) => void
}

/**
 * Lista de opiniones con orden, filtro por estrellas y por «con comentario», y paginación. Cada cambio de filtro
 * vuelve a la primera página. Estados: carga, error recuperable y «sin resultados con estos filtros».
 */
export function ReviewList({ target, kind, storeName, stars, onStarsChange }: ReviewListProps) {
  const sortId = useId()
  const [sort, setSort] = useState<ReviewSort>('recent')
  const [withComment, setWithComment] = useState(false)
  const [page, setPage] = useState(1)
  const reviews = useReviews(target, { page, sort, stars, withComment })

  // Al cambiar de filtro se vuelve a la primera página; las estrellas llegan desde fuera.
  const changeStars = (next: number | undefined) => {
    setPage(1)
    onStarsChange(next)
  }
  const filtered = stars !== undefined || withComment

  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between">
        <div role="group" aria-label="Filtrar opiniones por estrellas" className="flex flex-wrap gap-2">
          <button type="button" aria-pressed={stars === undefined} className={chip(stars === undefined)} onClick={() => changeStars(undefined)}>
            Todas
          </button>
          {[5, 4, 3, 2, 1].map((value) => (
            <button
              key={value}
              type="button"
              aria-pressed={stars === value}
              aria-label={`${value} ${value === 1 ? 'estrella' : 'estrellas'}`}
              className={chip(stars === value)}
              onClick={() => changeStars(stars === value ? undefined : value)}
            >
              {value} ★
            </button>
          ))}
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <button
            type="button"
            aria-pressed={withComment}
            className={chip(withComment)}
            onClick={() => {
              setWithComment((current) => !current)
              setPage(1)
            }}
          >
            Con comentario
          </button>
          <div className="flex flex-col gap-1">
            <label htmlFor={sortId} className="text-xs text-ap-muted">
              Ordenar por
            </label>
            <select
              id={sortId}
              value={sort}
              onChange={(event) => {
                setSort(event.target.value as ReviewSort)
                setPage(1)
              }}
              className="h-11 rounded-md border border-input bg-ap-white px-3 text-base text-ap-ink sm:text-sm"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </div>

      {reviews.status === 'error' ? (
        <ErrorState title="No pudimos cargar las opiniones" onRetry={reviews.retry} />
      ) : !reviews.data ? (
        <div className="flex flex-col gap-3 py-2" aria-hidden>
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      ) : reviews.data.items.length === 0 ? (
        <div className="flex flex-col items-start gap-2 border-y border-ap-line py-5 text-sm text-ap-muted" role="status">
          <p className="text-base font-semibold text-ap-ink">No hay opiniones con estos filtros</p>
          <p>Prueba con otra calificación o quita el filtro para ver todas.</p>
          {filtered ? (
            <Button
              variant="outline"
              onClick={() => {
                setWithComment(false)
                changeStars(undefined)
              }}
            >
              Quitar filtros
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', reviews.isStale && 'opacity-60')} aria-busy={reviews.isStale || undefined}>
            {reviews.data.items.map((review) => (
              <ReviewItem key={review.id} review={review} kind={kind} storeName={storeName} />
            ))}
          </ul>
          {reviews.data.totalPages > 1 ? (
            <nav aria-label="Páginas de opiniones" className="flex flex-wrap items-center gap-2">
              <Button variant="outline" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>
                Anteriores
              </Button>
              <span className="text-sm text-ap-muted tabular-nums" aria-live="polite">
                Página {reviews.data.page} de {reviews.data.totalPages}
              </span>
              <Button variant="outline" disabled={page >= reviews.data.totalPages} onClick={() => setPage((current) => current + 1)}>
                Siguientes
              </Button>
            </nav>
          ) : null}
        </>
      )}
    </div>
  )
}
