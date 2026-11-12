import { useId, useState } from 'react'
import { useSearchParams } from 'react-router'
import { EyeIcon, EyeOffIcon, MessageCircleQuestionIcon, SearchXIcon } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { ConfirmDialog } from '@/features/portal/components/confirm-dialog'
import { SelectField, TextAreaField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ServerPager } from '@/features/portal/components/server-pager'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { formatDateTime } from '@/features/portal/lib/labels'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getAdminQuestionService } from '../api/admin-question-service'
import type { AdminQuestion } from '../api/question-types'
import { AdminBoundary, AdminPage, FormSheet } from '../components/admin-kit'
import { ADMIN_CACHE_PREFIX } from '../hooks'

const REASON_MIN = 3
const REASON_MAX = 200

const VISIBILITY_OPTIONS = [
  { value: 'visibles', label: 'Visibles' },
  { value: 'ocultas', label: 'Ocultas' },
]

function reloadQuestions() {
  invalidateAsync(`${ADMIN_CACHE_PREFIX}questions`)
}

/** Panel para ocultar una pregunta: el motivo (3 a 200 caracteres) queda en la pregunta y en la auditoría. */
function HideSheet({ question, onClose }: { question: AdminQuestion | undefined; onClose: () => void }) {
  const fieldId = useId()
  return question ? <HideForm key={question.id} question={question} fieldId={fieldId} onClose={onClose} /> : null
}

function HideForm({ question, fieldId, onClose }: { question: AdminQuestion; fieldId: string; onClose: () => void }) {
  const [reason, setReason] = useState('')
  const [touched, setTouched] = useState(false)
  const trimmed = reason.trim()
  const invalid = trimmed.length < REASON_MIN || trimmed.length > REASON_MAX
  const error = touched && invalid ? `Escribe un motivo de ${REASON_MIN} a ${REASON_MAX} caracteres.` : undefined

  return (
    <FormSheet
      open
      onOpenChange={(open) => !open && onClose()}
      title="Ocultar pregunta"
      description={`Dejará de verse en la ficha de ${question.productName}. Puedes mostrarla de nuevo cuando quieras.`}
      submitLabel="Ocultar pregunta"
      what="la moderación"
      onSubmit={async () => {
        setTouched(true)
        if (invalid) return false
        await (await getAdminQuestionService()).hide(question.id, trimmed)
        reloadQuestions()
        toast.success('La pregunta quedó oculta.')
        onClose()
      }}
    >
      <blockquote className="max-w-prose rounded-md border border-ap-line border-l-4 border-l-ap-graphite bg-ap-paper p-3 text-sm break-words text-ap-ink">
        <p className="mb-1 font-semibold">{question.author}</p>
        {question.question}
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
        placeholder="Ej.: datos de contacto, insultos o publicidad."
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
 * `/admin/preguntas`: moderación (§7.4). Filtro en la URL y paginación en el servidor. Ocultar pide un motivo;
 * mostrar de nuevo pide confirmación. Cada acción queda en la auditoría del servidor.
 */
export default function AdminQuestionsPage() {
  const [params, setParams] = useSearchParams()
  const visibility = params.get('estado') === 'ocultas' ? 'ocultas' : params.get('estado') === 'visibles' ? 'visibles' : ''
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const [hiding, setHiding] = useState<AdminQuestion>()
  const [showing, setShowing] = useState<AdminQuestion>()

  const report = useAsync(`${ADMIN_CACHE_PREFIX}questions:${visibility}:${page}`, async () =>
    (await getAdminQuestionService()).list({ hidden: visibility === '' ? undefined : visibility === 'ocultas', page }),
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

  const filtered = visibility !== ''
  const clearFilters = () => setParams(new URLSearchParams(), { replace: true })

  const columns: DataColumn<AdminQuestion>[] = [
    {
      id: 'pregunta',
      header: 'Pregunta',
      rowHeader: true,
      cell: (row) => (
        <div className="flex max-w-md min-w-0 flex-col gap-1">
          {row.hidden ? (
            <p>
              <StatusBadge tone="warning">Oculta</StatusBadge>
            </p>
          ) : null}
          <p className="text-sm break-words whitespace-pre-line text-ap-ink">{row.question}</p>
          {row.answer ? <p className="text-xs break-words text-ap-muted">Respuesta: {row.answer.text}</p> : <p className="text-xs text-ap-muted">Sin responder.</p>}
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
          <p className="text-sm break-words text-ap-muted">{row.productName}</p>
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
          <Button variant="outline" className="h-11 px-3" aria-label={`Mostrar de nuevo la pregunta de ${row.author}`} onClick={() => setShowing(row)}>
            <EyeIcon aria-hidden />
            Mostrar
          </Button>
        ) : (
          <Button variant="outline" className="h-11 px-3" aria-label={`Ocultar la pregunta de ${row.author}`} onClick={() => setHiding(row)}>
            <EyeOffIcon aria-hidden />
            Ocultar
          </Button>
        ),
    },
  ]

  return (
    <AdminPage title="Preguntas" description="Modera las preguntas de compradores sobre productos. Oculta las que incumplan las reglas; el motivo queda en la auditoría.">
      <form role="search" aria-label="Filtrar preguntas" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <SelectField id="preguntas-estado" label="Estado" placeholder="Visibles y ocultas" options={VISIBILITY_OPTIONS} value={visibility} onChange={(event) => update({ estado: event.target.value })} />
      </form>

      {filtered ? (
        <div className="mt-4 flex min-h-11 items-center justify-end border-b border-ap-line pb-3">
          <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={clearFilters}>
            Quitar filtros
          </Button>
        </div>
      ) : null}

      <div className="mt-4">
        <AdminBoundary result={report} what="las preguntas" keepPrevious skeleton={<ListSkeleton rows={6} label="Cargando las preguntas" />}>
          {(data) =>
            data.total === 0 ? (
              filtered ? (
                <EmptyState icon={<SearchXIcon />} title="Ninguna pregunta coincide" description="Prueba con otro estado o quita los filtros.">
                  <Button variant="outline" className="h-11 px-4" onClick={clearFilters}>
                    Quitar filtros
                  </Button>
                </EmptyState>
              ) : (
                <EmptyState icon={<MessageCircleQuestionIcon />} title="Aún no hay preguntas" description="Cuando los compradores pregunten por productos, aparecerán aquí para moderarlas." />
              )
            ) : (
              <div className="flex flex-col gap-4">
                <DataTable caption="Preguntas de compradores" columns={columns} rows={data.items} getRowId={(row) => row.id} />
                <ServerPager
                  page={data.page}
                  totalPages={Math.max(1, data.totalPages)}
                  total={data.total}
                  singular="pregunta"
                  plural="preguntas"
                  busy={report.status === 'loading'}
                  onPageChange={(next) => update({ pagina: String(next) }, false)}
                />
              </div>
            )
          }
        </AdminBoundary>
      </div>

      <HideSheet question={hiding} onClose={() => setHiding(undefined)} />
      <ConfirmDialog
        open={showing !== undefined}
        onOpenChange={(open) => !open && setShowing(undefined)}
        title="¿Mostrar de nuevo esta pregunta?"
        description="Volverá a verse en la ficha del producto."
        confirmLabel="Mostrar pregunta"
        onConfirm={async () => {
          if (!showing) return
          await (await getAdminQuestionService()).unhide(showing.id)
          reloadQuestions()
          toast.success('La pregunta volvió a mostrarse.')
        }}
      />
    </AdminPage>
  )
}
