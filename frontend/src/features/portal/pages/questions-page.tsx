import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CornerDownRightIcon, EyeOffIcon, MessageCircleQuestionIcon, MessageSquareTextIcon, SearchXIcon } from 'lucide-react'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { productPath } from '@/features/marketplace/lib/paths'
import { relativeDate } from '@/features/marketplace/lib/review-labels'
import { ApiError } from '@/lib/api'
import { formatNumber, pluralize } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { cn } from '@/lib/utils'
import { getQuestionPortalService } from '../api/question-service'
import type { PortalQuestion } from '../api/question-types'
import { ConfirmDialog } from '../components/confirm-dialog'
import { SelectField, TextAreaField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { SEGMENT_GROUP_CLASS, segmentClass } from '../components/segmented'
import { ServerPager } from '../components/server-pager'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { focusMain, useActiveBranches } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { formatDateTime } from '../lib/labels'
import { invalidatePortalQuestions, usePortalQuestionPending } from '../lib/questions'

const ANSWER_MAX = 1000

type Status = 'sin-responder' | 'respondidas' | 'todas'

const STATUS_FILTERS: { value: Status; label: string }[] = [
  { value: 'sin-responder', label: 'Sin responder' },
  { value: 'respondidas', label: 'Respondidas' },
  { value: 'todas', label: 'Todas' },
]

/** Responder en línea: texto de hasta 1000 caracteres sin datos de contacto (el servidor valida). */
function AnswerEditor({ question, initial, onClose, onSaved }: { question: PortalQuestion; initial: string; onClose: () => void; onSaved: () => void }) {
  const { storeId } = usePortal()
  const fieldId = useId()
  const [text, setText] = useState(initial)
  const [error, setError] = useState<string>()
  const save = useSubmitOnce(async () => (await getQuestionPortalService()).answer(storeId, question.id, text.trim()))

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (text.trim().length === 0) {
      setError('Escribe la respuesta antes de publicarla.')
      return
    }
    setError(undefined)
    const result = await save.run()
    if (result.status === 'ok') {
      invalidatePortalQuestions(storeId)
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
        maxLength={ANSWER_MAX}
        value={text}
        autoFocus
        disabled={save.pending}
        error={error}
        onChange={(event) => setText(event.target.value)}
        placeholder="Responde con datos concretos: compatibilidad, medidas, disponibilidad."
        hint={
          <span aria-live="polite">
            {text.length} de {ANSWER_MAX} caracteres. Sin teléfonos, correos ni enlaces; la respuesta la verá cualquier visitante.
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

function QuestionItem({ question, highlighted, statusFilter }: { question: PortalQuestion; highlighted: boolean; statusFilter: Status }) {
  const { storeId } = usePortal()
  // El enlace de la notificación (`?pregunta=`) abre el editor si la pregunta sigue sin responder.
  const [editing, setEditing] = useState(highlighted && !question.answer)
  const [confirmingRemoval, setConfirmingRemoval] = useState(false)
  const ref = useRef<HTMLLIElement>(null)

  // ...y la enfoca y la deja a la vista. Con el editor abierto el foco pasa al campo de respuesta.
  useEffect(() => {
    if (!highlighted) return
    ref.current?.scrollIntoView({ block: 'center', behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' })
    if (!editing) ref.current?.focus({ preventScroll: true })
    // Solo al llegar a la pregunta: abrir o cerrar el editor después no debe volver a moverla.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [highlighted])

  // Con el filtro «Sin responder» la pregunta sale de la lista al responderla: el foco vuelve al contenido.
  const saved = () => {
    setEditing(false)
    if (statusFilter === 'sin-responder') focusMain()
  }

  return (
    <li
      ref={ref}
      tabIndex={-1}
      aria-label={`Pregunta de ${question.author} sobre ${question.productName}`}
      className={cn('flex flex-col gap-3 py-5 outline-none', highlighted && 'rounded-md bg-ap-paper px-3 ring-2 ring-ap-graphite focus-visible:ring-2')}
    >
      <div className="flex flex-col gap-1.5">
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <Link to={productPath(question.productId)} className="font-semibold break-words text-ap-ink underline-offset-4 hover:text-ap-red hover:underline">
            {question.productName}
          </Link>
          {question.hidden ? (
            <StatusBadge tone="warning">
              <EyeOffIcon aria-hidden className="mr-1 size-3.5" />
              Oculta por Autopartco
            </StatusBadge>
          ) : null}
        </p>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ap-muted">
          <span className="font-semibold text-ap-ink">{question.author}</span>
          <time dateTime={question.createdAt} title={formatDateTime(question.createdAt)}>
            {relativeDate(question.createdAt)}
          </time>
        </p>
        <p className="max-w-prose break-words whitespace-pre-line text-ap-ink">{question.question}</p>
      </div>

      {editing ? (
        <AnswerEditor question={question} initial={question.answer?.text ?? ''} onClose={() => setEditing(false)} onSaved={saved} />
      ) : question.answer ? (
        <div className="flex max-w-prose flex-col gap-2 rounded-md border border-ap-line border-l-4 border-l-ap-graphite bg-ap-paper p-3">
          <p className="flex flex-wrap items-center gap-x-2 text-xs font-semibold text-ap-ink">
            <CornerDownRightIcon aria-hidden className="size-3.5" />
            Tu respuesta
            <time dateTime={question.answer.answeredAt} className="font-normal text-ap-muted">
              {relativeDate(question.answer.answeredAt)}
            </time>
          </p>
          <p className="text-sm break-words whitespace-pre-line text-ap-ink">{question.answer.text}</p>
          <div className="flex flex-wrap gap-2">
            <Button variant="outline" className="h-11 px-3" aria-label={`Editar tu respuesta a ${question.author}`} onClick={() => setEditing(true)}>
              Editar
            </Button>
            <Button variant="ghost" className="h-11 px-3 text-ap-danger" aria-label={`Borrar tu respuesta a ${question.author}`} onClick={() => setConfirmingRemoval(true)}>
              Borrar
            </Button>
          </div>
        </div>
      ) : (
        <div>
          <Button className="h-11 px-4" aria-label={`Responder a ${question.author}`} onClick={() => setEditing(true)}>
            <MessageSquareTextIcon aria-hidden />
            Responder
          </Button>
        </div>
      )}

      <ConfirmDialog
        open={confirmingRemoval}
        onOpenChange={setConfirmingRemoval}
        title="¿Borrar tu respuesta?"
        description="La pregunta volverá a quedar sin responder. Podrás escribir otra respuesta cuando quieras."
        confirmLabel="Borrar respuesta"
        destructive
        onConfirm={async () => {
          try {
            await (await getQuestionPortalService()).removeAnswer(storeId, question.id)
          } finally {
            invalidatePortalQuestions(storeId)
          }
        }}
      />
    </li>
  )
}

/**
 * `/portal/:storeId/preguntas`: preguntas de compradores sobre los productos del almacén. Los filtros viven en la
 * URL. `?pregunta={id}` abre y enfoca esa pregunta (enlace de la notificación): si no está en la página actual,
 * recorre las páginas hasta encontrarla.
 */
export default function PortalQuestionsPage() {
  const { storeId } = usePortal()
  const [params, setParams] = useSearchParams()
  const branchFieldId = useId()
  const { branches } = useActiveBranches()

  const target = params.get('pregunta') ?? ''
  const rawStatus = params.get('estado')
  const status: Status = rawStatus === 'respondidas' || rawStatus === 'todas' || rawStatus === 'sin-responder' ? rawStatus : target ? 'todas' : 'sin-responder'
  const branchId = params.get('sucursal') ?? ''
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const answered = status === 'respondidas' ? true : status === 'sin-responder' ? false : undefined

  const pending = usePortalQuestionPending()
  const list = useAsync(`portal:${storeId}:questions:list:${status}:${branchId}:${page}`, async () =>
    (await getQuestionPortalService()).list(storeId, { answered, branchId: branchId || undefined, page }),
  )

  function update(next: Record<string, string>, resetPage = true) {
    const merged = new URLSearchParams(params)
    for (const [name, value] of Object.entries(next)) {
      if (value) merged.set(name, value)
      else merged.delete(name)
    }
    if (resetPage) merged.delete('pagina')
    // Al elegir filtros a mano el enlace de la notificación ya no aplica.
    if (resetPage && !('pregunta' in next)) merged.delete('pregunta')
    setParams(merged, { replace: true })
  }

  const data = list.status === 'success' ? list.data : undefined
  const found = data?.items.some((item) => item.id === target) ?? false
  const lastPage = data ? data.page >= data.totalPages : false

  // Busca la pregunta del enlace en las páginas siguientes.
  useEffect(() => {
    if (target && data && !found && !lastPage) {
      const merged = new URLSearchParams(params)
      merged.set('pagina', String(data.page + 1))
      setParams(merged, { replace: true })
    }
  }, [target, data, found, lastPage, params, setParams])

  const forbidden = list.status === 'error' && list.error instanceof ApiError && list.error.status === 403
  const pendingCount = pending.data ?? 0
  const filtered = status !== 'sin-responder' || branchId !== ''
  const reset = () => setParams(new URLSearchParams(), { replace: true })

  return (
    <>
      <title>Preguntas | Autopartco</title>
      <PageHeader
        title="Preguntas"
        description="Lo que los compradores quieren saber antes de comprar. Una respuesta a tiempo cierra ventas."
        meta={pendingCount > 0 ? <StatusBadge tone="warning">{pluralize(pendingCount, 'pregunta sin responder', 'preguntas sin responder')}</StatusBadge> : undefined}
      />

      {forbidden ? (
        <ErrorState title="No tienes acceso a las preguntas de esta tienda" description="Pídele al propietario del almacén que revise tus permisos o cambia de almacén desde el selector." />
      ) : (
        <section aria-labelledby="lista-preguntas" className="flex flex-col gap-4">
          <h2 id="lista-preguntas" className="sr-only">
            Lista de preguntas
          </h2>
          <div className="flex flex-col gap-3 lg:flex-row lg:flex-wrap lg:items-end">
            <div role="group" aria-label="Filtrar por respuesta" className={SEGMENT_GROUP_CLASS}>
              {STATUS_FILTERS.map((option) => (
                <button key={option.value} type="button" aria-pressed={status === option.value} className={segmentClass(status === option.value)} onClick={() => update({ estado: option.value })}>
                  {option.label}
                  {option.value === 'sin-responder' && pendingCount > 0 ? ` (${formatNumber(pendingCount)})` : ''}
                </button>
              ))}
            </div>
            {branches.length > 1 ? (
              <SelectField
                id={branchFieldId}
                label="Sucursal"
                placeholder="Todas las sucursales"
                value={branchId}
                onChange={(event) => update({ sucursal: event.target.value })}
                options={branches.map((branch) => ({ value: branch.id, label: branch.name }))}
                containerClassName="w-full sm:w-56"
              />
            ) : null}
            {filtered ? (
              <Button variant="ghost" className="h-11 px-3 underline underline-offset-4" onClick={reset}>
                Quitar filtros
              </Button>
            ) : null}
          </div>

          {target && data && !found && lastPage ? (
            <p role="status" className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-ink">
              No encontramos la pregunta del aviso. Puede que ya la hayan respondido o que la hayan quitado.
            </p>
          ) : null}

          <QueryBoundary result={list} what="las preguntas" keepPrevious skeleton={<ListSkeleton rows={4} label="Cargando las preguntas" />}>
            {(result) =>
              result.total === 0 ? (
                filtered ? (
                  <EmptyState icon={<SearchXIcon />} title="Ninguna pregunta coincide" description="Prueba con otro estado o sucursal, o quita los filtros para ver todas.">
                    <Button variant="outline" className="h-11 px-4" onClick={reset}>
                      Quitar filtros
                    </Button>
                  </EmptyState>
                ) : (
                  <EmptyState
                    icon={<MessageCircleQuestionIcon />}
                    title="No tienes preguntas sin responder"
                    description="Cuando un comprador pregunte por uno de tus productos, aparecerá aquí y te avisaremos."
                  />
                )
              ) : (
                <div className="flex flex-col gap-4">
                  <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', list.isStale && 'opacity-60')} aria-busy={list.isStale || undefined}>
                    {result.items.map((question) => (
                      <QuestionItem key={question.id} question={question} highlighted={question.id === target} statusFilter={status} />
                    ))}
                  </ul>
                  <ServerPager
                    page={result.page}
                    totalPages={result.totalPages}
                    total={result.total}
                    singular="pregunta"
                    plural="preguntas"
                    busy={list.status === 'loading'}
                    onPageChange={(next) => update({ pagina: String(next) }, false)}
                  />
                </div>
              )
            }
          </QueryBoundary>
        </section>
      )}
    </>
  )
}
