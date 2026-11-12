import { useState, type ReactNode } from 'react'
import { StarIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import type { ReviewTarget } from '../../api/types'
import { useReviewSummary } from '../../hooks'
import { AspectBars } from './aspect-bars'
import { RatingSummary } from './rating-summary'
import { ReviewHighlights } from './review-highlights'
import { ReviewList } from './review-list'

interface ReviewsSectionProps {
  target: ReviewTarget
  /** Ancla de la sección: `opiniones` (ficha) o `resenas` (perfil de tienda). */
  id: string
  /** Etiqueta de sección sobre el título («Producto / Opiniones»). */
  index: string
  title: string
  /** Nombre del almacén para titular sus respuestas. */
  storeName?: string
  /** Texto del estado vacío, después del titular. */
  emptyText: string
  /** Acción para quien puede calificar (o el formulario actual); se muestra con y sin opiniones. */
  action?: ReactNode
  /** Contenido extra solo en el estado vacío (p. ej. invitar a ingresar). */
  emptyExtra?: ReactNode
}

/**
 * Sección completa de opiniones: resumen con barras que filtran, categorías, insignias y la lista. Con cero
 * opiniones muestra una invitación en lugar de «0,0».
 */
export function ReviewsSection({ target, id, index, title, storeName, emptyText, action, emptyExtra }: ReviewsSectionProps) {
  const summary = useReviewSummary(target)
  const [stars, setStars] = useState<number | undefined>()
  const kind = target.type
  const data = summary.data

  return (
    <section id={id} aria-labelledby={`${id}-titulo`} className="flex scroll-mt-24 flex-col gap-5">
      <div className="flex flex-col gap-1">
        <p className="ap-index">{index}</p>
        <h2 id={`${id}-titulo`} className="text-section text-ap-ink">
          {title}
        </h2>
      </div>

      {summary.status === 'error' ? (
        <ErrorState title="No pudimos cargar las opiniones" onRetry={summary.retry} />
      ) : !data ? (
        <div className="flex flex-col gap-3" aria-hidden>
          <Skeleton className="h-12 w-40" />
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-4 w-2/3" />
        </div>
      ) : data.count === 0 ? (
        <>
          <div className="flex flex-col items-start gap-3 rounded-xl border border-dashed border-ap-line-strong bg-ap-paper p-5 sm:flex-row sm:items-center sm:gap-5">
            <span aria-hidden className="grid size-12 shrink-0 place-items-center rounded-full border border-ap-line bg-ap-white">
              <StarIcon className="size-6 text-ap-steel" />
            </span>
            <div className="flex min-w-0 flex-col gap-1">
              <p className="text-base font-semibold text-ap-ink">Sé el primero en opinar después de tu compra</p>
              <p className="max-w-prose text-sm text-ap-muted">{emptyText}</p>
              {emptyExtra}
            </div>
          </div>
          {action}
        </>
      ) : (
        <>
          <div className="grid gap-8 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)] lg:gap-12">
            <div className="flex min-w-0 flex-col gap-4">
              <RatingSummary summary={data} selectedStars={stars} onSelectStars={setStars} />
              <ReviewHighlights kind={kind} highlights={data.highlights} />
            </div>
            <AspectBars kind={kind} aspects={data.aspects} className="min-w-0" />
          </div>
          {action}
          <ReviewList target={target} kind={kind} storeName={storeName} stars={stars} onStarsChange={setStars} />
        </>
      )}
    </section>
  )
}
