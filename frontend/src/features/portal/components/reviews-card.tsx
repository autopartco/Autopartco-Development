import { Link } from 'react-router'
import { StarRating } from '@/features/marketplace/components/reviews/star-rating'
import { formatRating } from '@/features/marketplace/lib/review-labels'
import { pluralize } from '@/lib/format'
import { usePortal } from '../context'
import { usePortalReviewSummary } from '../lib/reviews'

/**
 * Reseñas en el panel: promedio del almacén y cuántas esperan respuesta, con enlace a la pantalla de reseñas.
 * Sin reseñas dice cómo llegan; si la consulta falla no se dibuja (la pantalla de reseñas explica el error).
 */
export function ReviewsCard() {
  const { storeId } = usePortal()
  const result = usePortalReviewSummary()
  const data = result.data
  if (!data) return null

  const { summary, unanswered } = data
  const link = `/portal/${encodeURIComponent(storeId)}/resenas`
  return (
    <section aria-labelledby="resenas-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
      <h2 id="resenas-titulo" className="text-base font-semibold text-ap-ink">
        Reseñas
      </h2>
      {summary.average === undefined || summary.count === 0 ? (
        <p className="text-sm text-ap-muted">Aún no tienes reseñas. Aparecen cuando un comprador califica un pedido entregado.</p>
      ) : (
        <div className="flex flex-col gap-1.5">
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <span className="ap-price text-xl text-ap-ink">{formatRating(summary.average)}</span>
            <StarRating value={summary.average} />
            <span className="text-sm text-ap-muted">{pluralize(summary.count, 'reseña', 'reseñas')}</span>
          </p>
          <p className={unanswered > 0 ? 'text-sm font-semibold text-ap-warning' : 'text-sm text-ap-muted'}>
            {unanswered > 0 ? `${pluralize(unanswered, 'reseña sin responder', 'reseñas sin responder')}` : 'Todas las reseñas tienen respuesta.'}
          </p>
        </div>
      )}
      <Link to={link} className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
        {unanswered > 0 ? 'Responder reseñas' : 'Ver reseñas'}
      </Link>
    </section>
  )
}
