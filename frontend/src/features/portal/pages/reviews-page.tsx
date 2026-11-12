import { useId, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { CornerDownRightIcon, EyeOffIcon, MessageSquareTextIcon, SearchXIcon, StarIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { AspectBars } from '@/features/marketplace/components/reviews/aspect-bars'
import { RatingSummary } from '@/features/marketplace/components/reviews/rating-summary'
import { ReviewHighlights } from '@/features/marketplace/components/reviews/review-highlights'
import { StarRating } from '@/features/marketplace/components/reviews/star-rating'
import { aspectCopy, aspectRank, formatRating, relativeDate } from '@/features/marketplace/lib/review-labels'
import { formatNumber, pluralize } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { cn } from '@/lib/utils'
import { getReviewPortalService } from '../api/review-service'
import type { PortalProductRating, PortalReview, PortalReviewType } from '../api/review-types'
import { ConfirmDialog } from '../components/confirm-dialog'
import { TextAreaField, SelectField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { SEGMENT_GROUP_CLASS, segmentClass } from '../components/segmented'
import { ServerPager } from '../components/server-pager'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { focusMain } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { formatDateTime } from '../lib/labels'
import { invalidatePortalReviews, usePortalReviewSummary } from '../lib/reviews'

const REPLY_MAX = 1000

const TYPE_FILTERS: { value: '' | PortalReviewType; label: string }[] = [
  { value: '', label: 'Todas' },
  { value: 'store', label: 'Del almacén' },
  { value: 'product', label: 'De productos' },
]

function typeOf(review: PortalReview): PortalReviewType {
  return review.type === 'STORE' ? 'store' : 'product'
}

/** Responder en línea: texto de hasta 1000 caracteres sin enlaces (el servidor valida). */
function ReplyEditor({ review, initial, onClose, onSaved }: { review: PortalReview; initial: string; onClose: () => void; onSaved: () => void }) {
  const { storeId } = usePortal()
  const fieldId = useId()
  const [text, setText] = useState(initial)
  const [error, setError] = useState<string>()
  const save = useSubmitOnce(async () => (await getReviewPortalService()).reply(storeId, typeOf(review), review.id, text.trim()))

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (text.trim().length === 0) {
      setError('Escribe la respuesta antes de publicarla.')
      return
    }
    setError(undefined)
    const result = await save.run()
    if (result.status === 'ok') {
      invalidatePortalReviews(storeId)
      onSaved()
    } else if (result.status === 'error') {
      setError(actionErrorMessage(result.error, 'No pudimos publicar tu respuesta. Inténtalo de nuevo.'))
    }
  }

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-3 rounded-md border border-ap-line bg-ap-paper p-3">
      <TextAreaField
        id={fieldId}
        label={initial ? 'Editar tu respuesta' : 'Tu respuesta'}
        rows={4}
        maxLength={REPLY_MAX}
        value={text}
        autoFocus
        disabled={save.pending}
        error={error}
        onChange={(event) => setText(event.target.value)}
        placeholder="Agradece, aclara o ofrece una solución. Sé breve y cordial."
        hint={
          <span aria-live="polite">
            {text.length} de {REPLY_MAX} caracteres. Sin enlaces; la respuesta la verá cualquier visitante.
          </span>
        }
      />
      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11 px-4" disabled={save.pending}>
          {save.pending ? 'Publicando…' : initial ? 'Guardar respuesta' : 'Publicar respuesta'}
        </Button>
        <Button type="button" variant="outline" className="h-11 px-4" disabled={save.pending} onClick={onClose}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}

function ReviewItem({ review, canReply, repliedFilter }: { review: PortalReview; canReply: boolean; repliedFilter: boolean }) {
  const { storeId } = usePortal()
  const [editing, setEditing] = useState(false)
  const [confirmingRemoval, setConfirmingRemoval] = useState(false)
  const kind = review.type === 'STORE' ? 'store' : 'product'
  const scored = Object.entries(review.aspects).toSorted(([a], [b]) => aspectRank(kind, a) - aspectRank(kind, b))

  // Con el filtro «Sin responder», la reseña sale de la lista al responderla: el foco vuelve al contenido.
  const saved = () => {
    setEditing(false)
    if (repliedFilter) focusMain()
  }

  return (
    <li className="flex flex-col gap-3 py-5">
      <div className="flex flex-col gap-1.5">
        <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <StarRating value={review.rating} />
          <span className="text-sm font-semibold text-ap-ink">{review.author}</span>
          <time dateTime={review.createdAt} title={formatDateTime(review.createdAt)} className="text-sm text-ap-muted">
            {relativeDate(review.createdAt)}
          </time>
          {review.hidden ? (
            <StatusBadge tone="warning">
              <EyeOffIcon aria-hidden className="mr-1 size-3.5" />
              Oculta por Autopartco
            </StatusBadge>
          ) : null}
        </div>
        <h3 className="text-sm font-semibold text-ap-ink">
          {review.type === 'PRODUCT' ? <span className="break-words">{review.productName ?? 'Producto'}</span> : 'Reseña del almacén'}
          <span className="sr-only"> ({formatRating(review.rating)} de 5)</span>
        </h3>
      </div>

      {review.hidden ? <p className="text-sm text-ap-muted">No se muestra al público ni cuenta en tu promedio.</p> : null}
      {review.comment ? <p className="max-w-prose text-sm break-words whitespace-pre-line text-ap-ink">{review.comment}</p> : <p className="text-sm text-ap-muted">Sin comentario, solo calificación.</p>}

      {scored.length > 0 ? (
        <ul aria-label="Categorías calificadas" className="flex flex-wrap gap-1.5">
          {scored.map(([code, value]) => (
            <li key={code} className="rounded-sm border border-ap-line bg-ap-paper px-2 py-0.5 text-xs text-ap-muted">
              {aspectCopy(kind, code).label}: <span className="font-semibold text-ap-ink tabular-nums">{value}/5</span>
            </li>
          ))}
        </ul>
      ) : null}

      {editing ? (
        <ReplyEditor review={review} initial={review.reply?.text ?? ''} onClose={() => setEditing(false)} onSaved={saved} />
      ) : review.reply ? (
        <div className="flex max-w-prose flex-col gap-2 rounded-md border border-ap-line border-l-4 border-l-ap-graphite bg-ap-paper p-3">
          <p className="flex flex-wrap items-center gap-x-2 text-xs font-semibold text-ap-ink">
            <CornerDownRightIcon aria-hidden className="size-3.5" />
            Tu respuesta
            <time dateTime={review.reply.repliedAt} className="font-normal text-ap-muted">
              {relativeDate(review.reply.repliedAt)}
            </time>
          </p>
          <p className="text-sm break-words whitespace-pre-line text-ap-ink">{review.reply.text}</p>
          {canReply ? (
            <div className="flex flex-wrap gap-2">
              <Button variant="outline" className="h-11 px-3" aria-label={`Editar tu respuesta a ${review.author}`} onClick={() => setEditing(true)}>
                Editar
              </Button>
              <Button variant="ghost" className="h-11 px-3 text-ap-danger" aria-label={`Borrar tu respuesta a ${review.author}`} onClick={() => setConfirmingRemoval(true)}>
                Borrar
              </Button>
            </div>
          ) : null}
        </div>
      ) : canReply ? (
        <div>
          <Button variant="outline" className="h-11 px-4" aria-label={`Responder a ${review.author}`} onClick={() => setEditing(true)}>
            <MessageSquareTextIcon aria-hidden />
            Responder
          </Button>
        </div>
      ) : (
        <p className="text-sm text-ap-muted">Sin respuesta todavía.</p>
      )}

      <ConfirmDialog
        open={confirmingRemoval}
        onOpenChange={setConfirmingRemoval}
        title="¿Borrar tu respuesta?"
        description="Dejará de verse en la reseña. Podrás escribir otra cuando quieras."
        confirmLabel="Borrar respuesta"
        destructive
        onConfirm={async () => {
          await (await getReviewPortalService()).removeReply(storeId, typeOf(review), review.id)
          invalidatePortalReviews(storeId)
        }}
      />
    </li>
  )
}

function ProductRatings({ title, items }: { title: string; items: PortalProductRating[] }) {
  if (items.length === 0) return null
  return (
    <section aria-label={title} className="flex min-w-0 flex-col gap-2">
      <h3 className="text-sm font-semibold text-ap-ink">{title}</h3>
      <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
        {items.map((item) => (
          <li key={item.productId} className="flex items-center justify-between gap-3 py-2 text-sm">
            <span className="min-w-0 break-words text-ap-ink">{item.name}</span>
            <span className="flex shrink-0 items-center gap-2 text-ap-muted tabular-nums">
              <StarRating value={item.average} size="sm" />
              <span className="font-semibold text-ap-ink">{formatRating(item.average)}</span>
              <span>({formatNumber(item.count)})</span>
            </span>
          </li>
        ))}
      </ul>
    </section>
  )
}

/** Promedio, distribución (filtra la lista), categorías, insignias y productos mejor y peor calificados. */
function SummarySection({ stars, onStars }: { stars: number | undefined; onStars: (stars: number | undefined) => void }) {
  const result = usePortalReviewSummary()
  return (
    <QueryBoundary result={result} what="el resumen de reseñas" keepPrevious skeleton={<ListSkeleton rows={2} label="Cargando el resumen de reseñas" />}>
      {(data) =>
        data.summary.count === 0 ? (
          <EmptyState
            icon={<StarIcon />}
            title="Aún no tienes reseñas"
            description="Las reseñas llegan cuando un comprador califica un pedido entregado. Despacha a tiempo y con la referencia correcta; así llegan las primeras estrellas."
          />
        ) : (
          <section aria-labelledby="resumen-resenas" className="flex flex-col gap-5">
            <h2 id="resumen-resenas" className="sr-only">
              Resumen de reseñas
            </h2>
            <div className="grid gap-8 lg:grid-cols-[minmax(0,1.4fr)_minmax(0,1fr)] lg:gap-12">
              <div className="flex min-w-0 flex-col gap-4">
                <RatingSummary summary={data.summary} selectedStars={stars} onSelectStars={onStars} />
                <ReviewHighlights kind="store" highlights={data.summary.highlights} />
              </div>
              <AspectBars kind="store" aspects={data.summary.aspects} className="min-w-0" />
            </div>
            {data.topProducts.length + data.bottomProducts.length > 0 ? (
              <div className="grid gap-6 md:grid-cols-2">
                <ProductRatings title="Productos mejor calificados" items={data.topProducts} />
                <ProductRatings title="Productos por mejorar" items={data.bottomProducts} />
              </div>
            ) : null}
          </section>
        )
      }
    </QueryBoundary>
  )
}

/**
 * `/portal/:storeId/resenas`: reseñas del almacén y de sus productos. Los filtros viven en la URL; con
 * `STORE_UPDATE` se responde en línea y sin él la pantalla es de solo lectura.
 */
export default function PortalReviewsPage() {
  const { storeId, can } = usePortal()
  const canReply = can('STORE_UPDATE')
  const [params, setParams] = useSearchParams()
  const starsId = useId()

  const type: '' | PortalReviewType = params.get('tipo') === 'almacen' ? 'store' : params.get('tipo') === 'productos' ? 'product' : ''
  const unanswered = params.get('respuesta') === 'sin-responder'
  const starsParam = Number(params.get('estrellas'))
  const stars = Number.isInteger(starsParam) && starsParam >= 1 && starsParam <= 5 ? starsParam : undefined
  const page = Math.max(1, Number(params.get('pagina')) || 1)

  const summary = usePortalReviewSummary()
  const list = useAsync(`portal:${storeId}:reviews:list:${type}:${unanswered}:${stars ?? ''}:${page}`, async () =>
    (await getReviewPortalService()).list(storeId, { type: type || undefined, replied: unanswered ? false : undefined, stars, page }),
  )

  function update(next: Record<string, string>, resetPage = true) {
    const merged = new URLSearchParams(params)
    for (const [name, value] of Object.entries(next)) {
      if (value) merged.set(name, value)
      else merged.delete(name)
    }
    if (resetPage) merged.delete('pagina')
    setParams(merged, { replace: true })
  }

  const filtered = type !== '' || unanswered || stars !== undefined
  const pendingReplies = summary.data?.unanswered ?? 0

  return (
    <>
      <title>Reseñas | Autopartco</title>
      <PageHeader
        title="Reseñas"
        description={canReply ? 'Lo que dicen tus compradores. Responder a tiempo construye confianza.' : 'Lo que dicen tus compradores. Tu rol puede verlas, pero no responderlas.'}
        meta={
          pendingReplies > 0 ? (
            <StatusBadge tone="warning">
              {pluralize(pendingReplies, 'reseña sin responder', 'reseñas sin responder')}
            </StatusBadge>
          ) : undefined
        }
      />

      <div className="flex flex-col gap-8">
        <SummarySection stars={stars} onStars={(next) => update({ estrellas: next ? String(next) : '' })} />

        <section aria-labelledby="lista-resenas" className="flex flex-col gap-4">
          <h2 id="lista-resenas" className="text-block-title text-ap-ink">
            Todas las reseñas
          </h2>

          <div className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-end">
            <div role="group" aria-label="Filtrar por tipo de reseña" className={SEGMENT_GROUP_CLASS}>
              {TYPE_FILTERS.map((option) => (
                <button
                  key={option.label}
                  type="button"
                  aria-pressed={type === option.value}
                  className={segmentClass(type === option.value)}
                  onClick={() => update({ tipo: option.value === 'store' ? 'almacen' : option.value === 'product' ? 'productos' : '' })}
                >
                  {option.label}
                </button>
              ))}
            </div>
            <div role="group" aria-label="Filtrar por respuesta" className={SEGMENT_GROUP_CLASS}>
              <button type="button" aria-pressed={!unanswered} className={segmentClass(!unanswered)} onClick={() => update({ respuesta: '' })}>
                Todas
              </button>
              <button type="button" aria-pressed={unanswered} className={segmentClass(unanswered)} onClick={() => update({ respuesta: 'sin-responder' })}>
                Sin responder{pendingReplies > 0 ? ` (${formatNumber(pendingReplies)})` : ''}
              </button>
            </div>
            <SelectField
              id={starsId}
              label="Calificación"
              placeholder="Todas las estrellas"
              value={stars ? String(stars) : ''}
              onChange={(event) => update({ estrellas: event.target.value })}
              options={[5, 4, 3, 2, 1].map((value) => ({ value: String(value), label: `${value} ${value === 1 ? 'estrella' : 'estrellas'}` }))}
              containerClassName="w-full sm:w-48"
            />
            {filtered ? (
              <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
                Quitar filtros
              </Button>
            ) : null}
          </div>

          <QueryBoundary result={list} what="las reseñas" keepPrevious skeleton={<ListSkeleton rows={4} label="Cargando las reseñas" />}>
            {(data) =>
              data.total === 0 ? (
                filtered ? (
                  <EmptyState icon={<SearchXIcon />} title="Ninguna reseña coincide" description="Prueba con otro tipo o calificación, o quita los filtros para ver todas.">
                    <Button variant="outline" className="h-11 px-4" onClick={() => setParams(new URLSearchParams(), { replace: true })}>
                      Quitar filtros
                    </Button>
                  </EmptyState>
                ) : (
                  <p className={cn('rounded-lg border border-ap-line px-4 py-5 text-sm text-ap-muted')}>Todavía no hay reseñas para mostrar.</p>
                )
              ) : (
                <div className="flex flex-col gap-4">
                  <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', list.isStale && 'opacity-60')} aria-busy={list.isStale || undefined}>
                    {data.items.map((review) => (
                      <ReviewItem key={`${review.type}:${review.id}`} review={review} canReply={canReply} repliedFilter={unanswered} />
                    ))}
                  </ul>
                  <ServerPager
                    page={data.page}
                    totalPages={data.totalPages}
                    total={data.total}
                    singular="reseña"
                    plural="reseñas"
                    busy={list.status === 'loading'}
                    onPageChange={(next) => update({ pagina: String(next) }, false)}
                  />
                </div>
              )
            }
          </QueryBoundary>
        </section>
      </div>
    </>
  )
}
