import { useId, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { toast } from 'sonner'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { TextAreaField, SelectField } from '@/features/portal/components/form-controls'
import type { ReviewTarget } from '@/features/marketplace/api/types'
import { aspectCodes, aspectCopy } from '@/features/marketplace/lib/review-labels'
import { Skeleton } from '@/components/ui/skeleton'
import { useMediaQuery } from '@/lib/use-media-query'
import { useAsync } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getReviewsService } from '../api/reviews-service'
import type { ReviewDraft } from '../api/types'
import { invalidateReviews } from '../hooks'
import { editWindowClosed, reviewErrorMessage } from '../lib/errors'
import { StarInput } from './star-input'

export type ReviewOutcome = 'created' | 'updated' | 'discarded'

export interface ReviewOrderOption {
  id: string
  label: string
}

export interface ExistingReview {
  id: string
  /** Fin de la ventana de 30 días para editar (`ProductReviewEligibility.editableUntil`). */
  editableUntil?: string
  /** Lo que ya escribió, si se conoce; si no, el diálogo lo carga con `GET …/reviews/mine`. */
  initial?: ReviewDraft
}

interface ReviewDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  target: ReviewTarget
  /** Nombre del almacén o del repuesto que se califica. */
  targetName: string
  /** Pedidos con los que se puede calificar (al crear). Con uno solo no se pregunta. */
  orders?: ReviewOrderOption[]
  /** Reseña propia ya publicada: el diálogo edita (o borra) en vez de crear. */
  existing?: ExistingReview
  onDone?: (outcome: ReviewOutcome) => void
}

const COMMENT_MAX = 1000

const COPY = {
  store: {
    create: 'Califica tu compra en',
    edit: 'Edita tu reseña de',
    question: '¿Cómo fue tu experiencia con este almacén?',
    aspectsHint: 'Puntúa lo que quieras destacar. Ayuda a otros compradores a elegir mejor.',
    placeholder: 'Ej.: entregaron a tiempo y la referencia era la correcta.',
    thanks: 'Gracias por calificar el almacén.',
  },
  product: {
    create: 'Califica este repuesto:',
    edit: 'Edita tu reseña de',
    question: '¿Qué tal te pareció el repuesto?',
    aspectsHint: 'Puntúa lo que quieras destacar. Ayuda a otros compradores a elegir mejor.',
    placeholder: 'Ej.: encajó perfecto en mi vehículo y se instala fácil.',
    thanks: 'Gracias por calificar el repuesto.',
  },
} as const

/**
 * Hoja inferior en móvil y diálogo desde `sm`. El contenido se monta al abrir, así que el formulario siempre
 * empieza limpio (o con la reseña existente).
 */
export function ReviewDialog(props: ReviewDialogProps) {
  const wide = useMediaQuery('(min-width: 40rem)')
  const { open, onOpenChange, target, targetName, existing } = props
  const copy = COPY[target.type]
  const title = existing ? `${copy.edit} ${targetName}` : `${copy.create} ${targetName}`
  const description = 'Solo califican quienes recibieron su pedido. Tu nombre se muestra como «Ana P.»; nunca tu correo.'

  const body = <ReviewBody key={`${target.type}:${target.id}:${existing?.id ?? 'nueva'}`} {...props} onClose={() => onOpenChange(false)} />

  return wide ? (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle className="text-block-title text-ap-ink">{title}</DialogTitle>
          <DialogDescription>{description}</DialogDescription>
        </DialogHeader>
        {body}
      </DialogContent>
    </Dialog>
  ) : (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent side="bottom" className="max-h-[92svh] gap-4 overflow-y-auto rounded-t-2xl p-5">
        <SheetHeader className="p-0 pr-10">
          <SheetTitle className="text-block-title text-ap-ink">{title}</SheetTitle>
          <SheetDescription>{description}</SheetDescription>
        </SheetHeader>
        {body}
      </SheetContent>
    </Sheet>
  )
}

const EDIT_WINDOW_MS = 30 * 24 * 60 * 60 * 1000

/**
 * Al editar, precarga la reseña propia desde el servidor (`GET …/reviews/mine`; en el almacén, la del pedido).
 * Si el llamador ya trae `existing.initial`, lo usa tal cual.
 */
function ReviewBody(props: ReviewDialogProps & { onClose: () => void }) {
  const { existing, target, orders, onClose } = props
  const orderId = target.type === 'store' ? orders?.[0]?.id : undefined
  const needsLoad = Boolean(existing) && !existing?.initial
  const mine = useAsync(`review-mine:${needsLoad ? '' : 'none:'}${target.type}:${target.id}:${orderId ?? ''}`, async () =>
    needsLoad ? (await getReviewsService()).mine(target, orderId) : undefined,
  )

  if (!existing || !needsLoad) return <ReviewForm {...props} />
  if (mine.status === 'error') {
    return (
      <div className="flex flex-col gap-4">
        <FormAlert title={reviewErrorMessage(mine.error, 'No pudimos cargar tu reseña. Inténtalo de nuevo.')} />
        <div className="flex flex-wrap gap-2">
          <Button type="button" className="h-11 px-4" onClick={mine.retry}>
            Reintentar
          </Button>
          <Button type="button" variant="outline" className="h-11 px-4" onClick={onClose}>
            Cerrar
          </Button>
        </div>
      </div>
    )
  }
  if (!mine.data || mine.isStale) {
    // Sin datos todavía (o llegando los de otra clave): se muestra la carga.
    return (
      <div className="flex flex-col gap-3" role="status">
        <span className="sr-only">Cargando tu reseña</span>
        <Skeleton aria-hidden className="h-10 w-48" />
        <Skeleton aria-hidden className="h-32 w-full" />
      </div>
    )
  }
  const review = mine.data
  const created = new Date(review.createdAt).getTime()
  const editableUntil = existing.editableUntil ?? (Number.isNaN(created) ? undefined : new Date(created + EDIT_WINDOW_MS).toISOString())
  return (
    <ReviewForm
      {...props}
      existing={{ ...existing, editableUntil, initial: { rating: review.rating, comment: review.comment, aspects: review.aspects } }}
    />
  )
}

function ReviewForm({ target, targetName, orders = [], existing, onDone, onClose }: ReviewDialogProps & { onClose: () => void }) {
  const formId = useId()
  const copy = COPY[target.type]
  const kind = target.type
  const closed = existing ? editWindowClosed(existing.editableUntil) : false

  const [orderId, setOrderId] = useState(orders[0]?.id ?? '')
  const [rating, setRating] = useState(existing?.initial?.rating ?? 0)
  const [aspects, setAspects] = useState<Record<string, number>>(existing?.initial?.aspects ?? {})
  const [comment, setComment] = useState(existing?.initial?.comment ?? '')
  const [error, setError] = useState<string>()
  const [confirmingDiscard, setConfirmingDiscard] = useState(false)
  const alertRef = useRef<HTMLDivElement>(null)

  const save = useSubmitOnce(async () => {
    const draft: ReviewDraft = { rating, comment: comment.trim() || undefined, aspects }
    const service = await getReviewsService()
    if (existing) await service.update(target, existing.id, draft)
    else await service.create(target, orderId, draft)
  })
  const discard = useSubmitOnce(async () => {
    if (!existing) return
    await (await getReviewsService()).discard(target, existing.id)
  })
  const pending = save.pending || discard.pending

  function finish(outcome: ReviewOutcome, message: string) {
    invalidateReviews()
    toast.success(message)
    onDone?.(outcome)
    onClose()
  }

  function showError(message: string) {
    setError(message)
    // El aviso aparece arriba del formulario: se lleva ahí el foco para que se lea.
    window.setTimeout(() => alertRef.current?.focus(), 0)
  }

  const fail = (cause: unknown) => showError(reviewErrorMessage(cause))

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (rating < 1) {
      showError('Elige de 1 a 5 estrellas para publicar tu reseña.')
      return
    }
    if (!existing && !orderId) {
      showError('Elige el pedido con el que recibiste este producto.')
      return
    }
    setError(undefined)
    const result = await save.run()
    if (result.status === 'ok') finish(existing ? 'updated' : 'created', existing ? 'Actualizamos tu reseña.' : copy.thanks)
    else if (result.status === 'error') fail(result.error)
  }

  async function confirmDiscard() {
    setError(undefined)
    const result = await discard.run()
    if (result.status === 'ok') finish('discarded', 'Borramos tu reseña.')
    else if (result.status === 'error') {
      setConfirmingDiscard(false)
      fail(result.error)
    }
  }

  const readOnly = closed
  return (
    <form onSubmit={submit} noValidate className="flex min-w-0 flex-col gap-5" aria-label={`Reseña de ${targetName}`}>
      <div ref={alertRef} tabIndex={-1} className="outline-none" role={error ? 'alert' : undefined}>
        {error ? <FormAlert title={error} /> : null}
      </div>

      {readOnly ? (
        <FormAlert tone="info" title="Ya no puedes editar esta reseña">
          Pasaron los 30 días para modificarla. Si quieres, puedes borrarla.
        </FormAlert>
      ) : null}

      {!existing && orders.length > 1 ? (
        <SelectField
          id={`${formId}-pedido`}
          label="Pedido"
          hint="Elige el pedido en el que lo recibiste."
          value={orderId}
          onChange={(event) => setOrderId(event.target.value)}
          options={orders.map((order) => ({ value: order.id, label: order.label }))}
          containerClassName="max-w-xs"
        />
      ) : null}

      {!readOnly ? (
        <>
          <section className="flex flex-col gap-1.5" aria-labelledby={`${formId}-general`}>
            <h3 id={`${formId}-general`} className="text-sm font-semibold text-ap-ink">
              {copy.question}
            </h3>
            <StarInput name={`${formId}-rating`} value={rating} onChange={setRating} legend="Calificación general, de 1 a 5 estrellas" showWord disabled={pending} />
          </section>

          <section className="flex flex-col gap-2" aria-labelledby={`${formId}-categorias`}>
            <div className="flex flex-col gap-0.5">
              <h3 id={`${formId}-categorias`} className="text-sm font-semibold text-ap-ink">
                Detalles <span className="font-normal text-ap-muted">(opcional)</span>
              </h3>
              <p className="text-xs text-ap-muted">{copy.aspectsHint}</p>
            </div>
            <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
              {aspectCodes(kind).map((code) => {
                const value = aspects[code] ?? 0
                const label = aspectCopy(kind, code).label
                return (
                  <li key={code} className="flex flex-col gap-0.5 py-1.5 sm:flex-row sm:items-center sm:justify-between sm:gap-3">
                    <p className="text-sm text-ap-ink">{label}</p>
                    <div className="flex items-center gap-1">
                      <StarInput
                        name={`${formId}-${code}`}
                        value={value}
                        onChange={(next) => setAspects((current) => ({ ...current, [code]: next }))}
                        legend={`${label}, de 1 a 5 estrellas`}
                        size="sm"
                        disabled={pending}
                      />
                      <Button
                        type="button"
                        variant="ghost"
                        className="h-11 px-2 text-xs"
                        disabled={pending || value === 0}
                        aria-label={`Quitar la calificación de ${label}`}
                        onClick={() =>
                          setAspects((current) => {
                            const next = { ...current }
                            delete next[code]
                            return next
                          })
                        }
                      >
                        Quitar
                      </Button>
                    </div>
                  </li>
                )
              })}
            </ul>
          </section>

          <TextAreaField
            id={`${formId}-comentario`}
            label="Tu comentario"
            optional
            rows={4}
            maxLength={COMMENT_MAX}
            value={comment}
            disabled={pending}
            onChange={(event) => setComment(event.target.value)}
            placeholder={copy.placeholder}
            hint={
              <span aria-live="polite">
                {comment.length} de {COMMENT_MAX} caracteres. No incluyas enlaces ni datos personales.
              </span>
            }
          />
        </>
      ) : null}

      {confirmingDiscard ? (
        <div role="group" aria-label="Confirmar borrado" className="flex flex-col gap-3 rounded-md border border-ap-danger/30 bg-ap-danger-soft p-3">
          <p className="text-sm font-medium text-ap-ink">¿Borrar tu reseña? Dejará de verse y no se puede deshacer.</p>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="destructive" className="h-11 px-4" disabled={pending} onClick={() => void confirmDiscard()}>
              {discard.pending ? 'Borrando…' : 'Sí, borrar mi reseña'}
            </Button>
            <Button type="button" variant="outline" className="h-11 px-4" disabled={pending} onClick={() => setConfirmingDiscard(false)}>
              Mejor no
            </Button>
          </div>
        </div>
      ) : (
        <Actions
          pending={pending}
          saving={save.pending}
          editing={Boolean(existing)}
          readOnly={readOnly}
          onClose={onClose}
          onDiscard={existing ? () => setConfirmingDiscard(true) : undefined}
        />
      )}
    </form>
  )
}

function Actions({
  pending,
  saving,
  editing,
  readOnly,
  onClose,
  onDiscard,
}: {
  pending: boolean
  saving: boolean
  editing: boolean
  readOnly: boolean
  onClose: () => void
  onDiscard?: () => void
}): ReactNode {
  return (
    <div className="flex flex-col-reverse gap-2 sm:flex-row sm:flex-wrap sm:items-center">
      {onDiscard ? (
        <Button type="button" variant="ghost" className="h-11 px-4 text-ap-danger sm:mr-auto" disabled={pending} onClick={onDiscard}>
          Borrar mi reseña
        </Button>
      ) : null}
      <Button type="button" variant="outline" className="h-11 px-4" disabled={pending} onClick={onClose}>
        {readOnly ? 'Cerrar' : 'Ahora no'}
      </Button>
      {readOnly ? null : (
        <Button type="submit" className="h-11 px-5" disabled={pending}>
          {saving ? 'Guardando…' : editing ? 'Guardar cambios' : 'Publicar reseña'}
        </Button>
      )}
    </div>
  )
}
