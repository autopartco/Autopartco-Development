import { useId, useState } from 'react'
import { useSearchParams } from 'react-router'
import { EyeIcon, EyeOffIcon, SearchXIcon, StarIcon } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { StarRating } from '@/features/marketplace/components/reviews/star-rating'
import { SelectField, TextAreaField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ServerPager } from '@/features/portal/components/server-pager'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { ConfirmDialog } from '@/features/portal/components/confirm-dialog'
import { formatDateTime } from '@/features/portal/lib/labels'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getAdminReviewService } from '../api/admin-review-service'
import type { AdminReview } from '../api/review-types'
import { AdminBoundary, AdminPage, FormSheet } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const REASON_MIN = 3
const REASON_MAX = 200

const VISIBILITY_OPTIONS = [
  { value: 'visibles', label: 'Visibles' },
  { value: 'ocultas', label: 'Ocultas' },
]

const TYPE_OPTIONS = [
  { value: 'almacen', label: 'De almacenes' },
  { value: 'producto', label: 'De productos' },
]

const typeCode = (review: AdminReview) => (review.type === 'STORE' ? 'store' : 'product')

function reloadReviews() {
  invalidateAsync(`${ADMIN_CACHE_PREFIX}reviews`)
}

/** Panel para ocultar una reseña: el motivo (3 a 200 caracteres) queda en la reseña y en la auditoría. */
function HideSheet({ review, onClose }: { review: AdminReview | undefined; onClose: () => void }) {
  const fieldId = useId()
  return review ? <HideForm key={review.id} review={review} fieldId={fieldId} onClose={onClose} /> : null
}

function HideForm({ review, fieldId, onClose }: { review: AdminReview; fieldId: string; onClose: () => void }) {
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const trimmed = reason.trim()
  const invalid = trimmed.length < REASON_MIN || trimmed.length > REASON_MAX
  const error = touched && invalid ? `Escribe un motivo de ${REASON_MIN} a ${REASON_MAX} caracteres.` : undefined

  return (
    <FormSheet
      open
      onOpenChange={(open) => !open && onClose()}
      title="Ocultar reseña"
      description={`Dejará de verse en el sitio y no contará en los promedios de ${review.storeName}${review.productName ? ` ni de ${review.productName}` : ''}. Puedes mostrarla de nuevo cuando quieras.`}
      submitLabel="Ocultar reseña"
      what="la moderación"
      onSubmit={async () => {
        setTouched(true)
        if (invalid) return false
        await (await getAdminReviewService()).hide(typeCode(review), review.id, trimmed)
        reloadReviews()
        toast.success('La reseña quedó oculta.')
        onClose()
      }}
    >
      <blockquote className="max-w-prose rounded-md border border-ap-line border-l-4 border-l-ap-graphite bg-ap-paper p-3 text-sm break-words text-ap-ink">
        <p className="mb-1 flex items-center gap-2">
          <StarRating value={review.rating} size="sm" />
          <span className="font-semibold">{review.author}</span>
        </p>
        {review.comment ?? <span className="text-ap-muted">Sin comentario.</span>}
      </blockquote>
      <TextAreaField
        id={`${fieldId}-motivo`}
        label="Motivo"
        rows={3}
        maxLength={REASON_MAX}
        value={reason}
        error={error}
        onChange={(event) => setReason(event.target.value)}
        onBlur={() => setTouched(true)}
        placeholder="Ej.: insultos, datos personales o reseña de otra tienda."
        hint={
          <span aria-live="polite">
            {reason.length} de {REASON_MAX} caracteres. El motivo queda en la auditoría.
          </span>
        }
      />
    </FormSheet>
  )
}

/**
 * `/admin/resenas`: moderación (§7.4). Filtros en la URL y paginación en el servidor. Ocultar pide un motivo;
 * mostrar de nuevo pide confirmación. Cada acción queda en la auditoría del servidor.
 */
export default function AdminReviewsPage() {
  const [params, setParams] = useSearchParams()
  const visibility = params.get('estado') === 'ocultas' ? 'ocultas' : params.get('estado') === 'visibles' ? 'visibles' : ''
  const type = params.get('tipo') === 'almacen' ? 'almacen' : params.get('tipo') === 'producto' ? 'producto' : ''
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const [hiding, setHiding] = useState<AdminReview>()
  const [showing, setShowing] = useState<AdminReview>()

  const report = useAsync(`${ADMIN_CACHE_PREFIX}reviews:${visibility}:${type}:${page}`, async () =>
    (await getAdminReviewService()).list({
      hidden: visibility === '' ? undefined : visibility === 'ocultas',
      type: type === '' ? undefined : type === 'almacen' ? 'store' : 'product',
      page,
    }),
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

  const activeFilters = [visibility, type].filter(Boolean).length
  const clearFilters = () => setParams(new URLSearchParams(), { replace: true })

  const columns: DataColumn<AdminReview>[] = [
    {
      id: 'resena',
      header: 'Reseña',
      rowHeader: true,
      cell: (row) => (
        <div className="flex max-w-md min-w-0 flex-col gap-1">
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1">
            <StarRating value={row.rating} size="sm" />
            {row.hidden ? <StatusBadge tone="warning">Oculta</StatusBadge> : null}
          </p>
          {row.comment ? <p className="text-sm break-words whitespace-pre-line text-ap-ink">{row.comment}</p> : <p className="text-sm text-ap-muted">Sin comentario.</p>}
          {row.hidden && row.hiddenReason ? (
            <p className="text-xs text-ap-muted">
              <span className="font-semibold text-ap-ink">Motivo:</span> {row.hiddenReason}
              {row.hiddenAt ? ` · ${formatDateTime(row.hiddenAt)}` : ''}
            </p>
          ) : null}
        </div>
      ),
    },
    {
      id: 'objeto',
      header: 'Almacén y producto',
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-medium break-words text-ap-ink">{row.storeName}</p>
          <p className="text-sm break-words text-ap-muted">{row.type === 'PRODUCT' ? (row.productName ?? 'Producto') : 'Reseña del almacén'}</p>
        </div>
      ),
    },
    { id: 'autor', header: 'Autor', className: 'hidden md:table-cell', cell: (row) => <span className="text-ap-ink">{row.author}</span> },
    {
      id: 'fecha',
      header: 'Fecha',
      className: 'hidden lg:table-cell',
      cell: (row) => (
        <time dateTime={row.createdAt} className="ap-ref whitespace-nowrap text-ap-ink">
          {formatDateTime(row.createdAt)}
        </time>
      ),
    },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) =>
        row.hidden ? (
          <Button variant="outline" className="h-11 px-3" aria-label={`Mostrar de nuevo la reseña de ${row.author}`} onClick={() => setShowing(row)}>
            <EyeIcon aria-hidden />
            Mostrar
          </Button>
        ) : (
          <Button variant="outline" className="h-11 px-3" aria-label={`Ocultar la reseña de ${row.author}`} onClick={() => setHiding(row)}>
            <EyeOffIcon aria-hidden />
            Ocultar
          </Button>
        ),
    },
  ]

  return (
    <AdminPage title="Reseñas" description="Modera las reseñas de compradores. Oculta las que incumplan las reglas; el motivo queda en la auditoría.">
      <form role="search" aria-label="Filtrar reseñas" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <SelectField id="resenas-estado" label="Estado" placeholder="Visibles y ocultas" options={VISIBILITY_OPTIONS} value={visibility} onChange={(event) => update({ estado: event.target.value })} />
        <SelectField id="resenas-tipo" label="Tipo" placeholder="Almacenes y productos" options={TYPE_OPTIONS} value={type} onChange={(event) => update({ tipo: event.target.value })} />
      </form>

      {activeFilters > 0 ? (
        <div className="mt-4 flex min-h-11 items-center justify-end border-b border-ap-line pb-3">
          <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={clearFilters}>
            Quitar filtros
          </Button>
        </div>
      ) : null}

      <div className="mt-4">
        <AdminBoundary result={report} what="las reseñas" keepPrevious skeleton={<ListSkeleton rows={6} label="Cargando las reseñas" />}>
          {(data) =>
            data.total === 0 ? (
              activeFilters > 0 ? (
                <EmptyState icon={<SearchXIcon />} title="Ninguna reseña coincide" description="Prueba con otro estado o tipo, o quita los filtros.">
                  <Button variant="outline" className="h-11 px-4" onClick={clearFilters}>
                    Quitar filtros
                  </Button>
                </EmptyState>
              ) : (
                <EmptyState icon={<StarIcon />} title="Aún no hay reseñas" description="Cuando los compradores califiquen sus pedidos, aparecerán aquí para moderarlas." />
              )
            ) : (
              <div className="flex flex-col gap-4">
                <DataTable caption="Reseñas de compradores" columns={columns} rows={data.items} getRowId={(row) => `${row.type}:${row.id}`} />
                <ServerPager
                  page={data.page}
                  totalPages={Math.max(1, data.totalPages)}
                  total={data.total}
                  singular="reseña"
                  plural="reseñas"
                  busy={report.status === 'loading'}
                  onPageChange={(next) => update({ pagina: String(next) }, false)}
                />
              </div>
            )
          }
        </AdminBoundary>
      </div>

      <HideSheet review={hiding} onClose={() => setHiding(undefined)} />
      <ConfirmDialog
        open={showing !== undefined}
        onOpenChange={(open) => !open && setShowing(undefined)}
        title="¿Mostrar de nuevo esta reseña?"
        description="Volverá a verse en el sitio y contará en los promedios del almacén y del producto."
        confirmLabel="Mostrar reseña"
        onConfirm={async () => {
          if (!showing) return
          await (await getAdminReviewService()).unhide(typeCode(showing), showing.id)
          reloadReviews()
          toast.success('La reseña volvió a mostrarse.')
        }}
      />
    </AdminPage>
  )
}
