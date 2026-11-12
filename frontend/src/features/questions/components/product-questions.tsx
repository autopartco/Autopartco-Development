import { useId, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation } from 'react-router'
import { CornerDownRightIcon, MessageCircleQuestionIcon, SearchIcon, SearchXIcon } from 'lucide-react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useSession } from '@/features/auth/hooks'
import { loginPathFor } from '@/features/auth/lib/return-path'
import { relativeDate } from '@/features/marketplace/lib/review-labels'
import { ConfirmDialog } from '@/features/portal/components/confirm-dialog'
import { TextAreaField } from '@/features/portal/components/form-controls'
import { ServerPager } from '@/features/portal/components/server-pager'
import { formatDateTime } from '@/features/portal/lib/labels'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { cn } from '@/lib/utils'
import { getQuestionsService } from '../api/questions-service'
import type { ProductQuestion } from '../api/types'
import { invalidateQuestions, useProductQuestions } from '../hooks'
import { questionErrorMessage } from '../lib/errors'

const QUESTION_MIN = 10
const QUESTION_MAX = 300
const QUERY_MAX = 80

/** Formulario «Pregúntale al almacén»: con sesión escribe; sin sesión inicia sesión y vuelve a la ficha. */
function AskForm({ productId, productName }: { productId: string; productName: string }) {
  const session = useSession()
  const location = useLocation()
  const fieldId = useId()
  const [text, setText] = useState('')
  const [error, setError] = useState<string>()
  const [sent, setSent] = useState(false)
  const errorRef = useRef<HTMLDivElement>(null)
  const submit = useSubmitOnce(async () => (await getQuestionsService()).ask(productId, text.trim()))

  if (!session) {
    return (
      <div className="flex flex-col items-start gap-3 rounded-xl border border-ap-line-strong bg-ap-white p-4 sm:flex-row sm:items-center sm:justify-between">
        <p className="text-sm text-ap-ink">Inicia sesión para preguntarle al almacén. Te avisamos aquí y por correo cuando responda.</p>
        <Button className="h-11 shrink-0 px-4" nativeButton={false} render={<Link to={loginPathFor(`${location.pathname}${location.search}#preguntas`)} />}>
          Iniciar sesión para preguntar
        </Button>
      </div>
    )
  }

  const length = text.trim().length
  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSent(false)
    if (length < QUESTION_MIN) {
      setError(`Escribe al menos ${QUESTION_MIN} caracteres para que el almacén pueda ayudarte.`)
      return
    }
    setError(undefined)
    const result = await submit.run()
    if (result.status === 'ok') {
      invalidateQuestions()
      setText('')
      setSent(true)
    } else if (result.status === 'error') {
      setError(questionErrorMessage(result.error))
      window.setTimeout(() => errorRef.current?.focus(), 0)
    }
  }

  return (
    <form onSubmit={onSubmit} noValidate aria-label={`Pregúntale al almacén sobre ${productName}`} className="flex max-w-xl flex-col gap-3 rounded-xl border border-ap-line-strong bg-ap-white p-4">
      <h3 className="text-block-title text-ap-ink">Pregúntale al almacén</h3>
      <div ref={errorRef} tabIndex={-1} className="outline-none" role={error ? 'alert' : undefined}>
        {error ? <FormAlert title={error} /> : null}
      </div>
      <p role="status" className={cn(sent ? 'rounded-md border border-ap-success/30 bg-ap-success-soft p-3 text-sm font-medium text-ap-ink' : 'sr-only')}>
        {sent ? 'Enviamos tu pregunta al almacén. Aparece abajo como pendiente y te avisamos cuando responda.' : ''}
      </p>
      <TextAreaField
        id={fieldId}
        label="Tu pregunta"
        rows={3}
        maxLength={QUESTION_MAX}
        value={text}
        disabled={submit.pending}
        onChange={(event) => {
          setText(event.target.value)
          setSent(false)
        }}
        placeholder="Ej.: ¿Sirve para una Mazda 3 2015 con motor 2.0?"
        hint={
          <span>
            <span aria-live="polite">
              {text.length} de {QUESTION_MAX} caracteres.
            </span>{' '}
            No incluyas teléfonos, correos ni enlaces: el almacén te responde aquí y te avisamos.
          </span>
        }
      />
      <div>
        <Button type="submit" className="h-11 px-5" disabled={submit.pending}>
          {submit.pending ? 'Enviando…' : 'Enviar pregunta'}
        </Button>
      </div>
    </form>
  )
}

function QuestionItem({ question, productId, storeName }: { question: ProductQuestion; productId: string; storeName?: string }) {
  const [confirming, setConfirming] = useState(false)
  return (
    <li className="flex flex-col gap-3 py-5">
      <div className="flex flex-col gap-1">
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
          <span className="font-semibold text-ap-ink">{question.mine ? 'Tú preguntaste' : question.author}</span>
          <time dateTime={question.createdAt} title={formatDateTime(question.createdAt)} className="text-ap-muted">
            {relativeDate(question.createdAt)}
          </time>
        </p>
        <p className="max-w-prose text-ap-ink break-words whitespace-pre-line">{question.question}</p>
      </div>

      {question.answer ? (
        <div className="flex max-w-prose flex-col gap-1.5 rounded-md border border-ap-line border-l-4 border-l-ap-graphite bg-ap-paper p-3">
          <p className="flex flex-wrap items-center gap-x-2 text-xs font-semibold text-ap-ink">
            <CornerDownRightIcon aria-hidden className="size-3.5" />
            Respuesta{storeName ? ` de ${storeName}` : ' del almacén'}
            <time dateTime={question.answer.answeredAt} className="font-normal text-ap-muted">
              {relativeDate(question.answer.answeredAt)}
            </time>
          </p>
          <p className="text-sm break-words whitespace-pre-line text-ap-ink">{question.answer.text}</p>
        </div>
      ) : (
        <div className="flex flex-wrap items-center gap-2">
          <span className="inline-flex h-6 items-center rounded-sm border border-ap-warning/40 bg-ap-warning-soft px-2 text-xs font-semibold text-ap-ink">Esperando respuesta del almacén</span>
          {question.mine ? (
            <>
              <Button variant="ghost" className="h-11 px-3 text-ap-danger" aria-label="Borrar mi pregunta pendiente" onClick={() => setConfirming(true)}>
                Borrar
              </Button>
              <ConfirmDialog
                open={confirming}
                onOpenChange={setConfirming}
                title="¿Borrar tu pregunta?"
                description="Dejará de verse y el almacén no la responderá. Puedes volver a preguntar cuando quieras."
                confirmLabel="Borrar pregunta"
                destructive
                onConfirm={async () => {
                  try {
                    await (await getQuestionsService()).remove(productId, question.id)
                  } finally {
                    // También si el servidor dice que ya no existe o ya se respondió: la lista se actualiza.
                    invalidateQuestions()
                  }
                }}
              />
            </>
          ) : null}
        </div>
      )}
    </li>
  )
}

interface ProductQuestionsProps {
  productId: string
  productName: string
  storeName?: string
}

/**
 * «Preguntas sobre este producto» (`#preguntas`): buscador, lista paginada con las respondidas primero y el
 * formulario para preguntar. Las pendientes solo las ve su autor; el servidor decide qué se muestra.
 */
export function ProductQuestions({ productId, productName, storeName }: ProductQuestionsProps) {
  const session = useSession()
  const searchId = useId()
  const [draft, setDraft] = useState('')
  const [q, setQ] = useState('')
  const [page, setPage] = useState(1)
  const list = useProductQuestions(productId, session?.user.email, { page, q })
  const searching = q.trim().length > 0

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setQ(draft.trim().slice(0, QUERY_MAX))
    setPage(1)
  }

  function clear() {
    setDraft('')
    setQ('')
    setPage(1)
  }

  const data = list.data
  const empty = data !== undefined && data.total === 0 && !searching

  return (
    <section id="preguntas" aria-labelledby="preguntas-titulo" className="flex scroll-mt-24 flex-col gap-5">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Producto / Preguntas</p>
        <h2 id="preguntas-titulo" className="text-section text-ap-ink">
          Preguntas sobre este producto
        </h2>
      </div>

      {data && !empty ? (
        <form role="search" aria-label="Buscar en las preguntas" onSubmit={search} className="flex max-w-xl items-end gap-2">
          <TextField
            id={searchId}
            label="Buscar en las preguntas"
            type="search"
            maxLength={QUERY_MAX}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="Ej.: compatibilidad, medida, garantía"
            containerClassName="min-w-0 flex-1"
          />
          <Button type="submit" variant="outline" className="h-11 shrink-0 px-4">
            <SearchIcon aria-hidden />
            Buscar
          </Button>
        </form>
      ) : null}

      {list.status === 'error' ? (
        <ErrorState title="No pudimos cargar las preguntas" description={questionErrorMessage(list.error, 'Revisa tu conexión e inténtalo de nuevo.')} onRetry={list.retry} />
      ) : !data ? (
        <div className="flex flex-col gap-3" role="status">
          <span className="sr-only">Cargando las preguntas</span>
          <Skeleton aria-hidden className="h-4 w-3/4" />
          <Skeleton aria-hidden className="h-4 w-2/3" />
          <Skeleton aria-hidden className="h-16 w-full" />
        </div>
      ) : empty ? (
        <div className="flex flex-col items-start gap-1 rounded-xl border border-dashed border-ap-line-strong bg-ap-paper p-5 sm:flex-row sm:items-center sm:gap-5">
          <span aria-hidden className="grid size-12 shrink-0 place-items-center rounded-full border border-ap-line bg-ap-white">
            <MessageCircleQuestionIcon className="size-6 text-ap-steel" />
          </span>
          <div className="min-w-0">
            <p className="text-base font-semibold text-ap-ink">¿Tienes dudas? Pregúntale al almacén antes de comprar</p>
            <p className="max-w-prose text-sm text-ap-muted">Pregunta por compatibilidad, medidas o disponibilidad. La respuesta queda visible para otros compradores.</p>
          </div>
        </div>
      ) : data.total === 0 ? (
        <div className="flex flex-col items-start gap-3 rounded-xl border border-ap-line p-5">
          <p className="flex items-center gap-2 font-semibold text-ap-ink">
            <SearchXIcon aria-hidden className="size-5 text-ap-muted" />
            Ninguna pregunta coincide con «{q}»
          </p>
          <p className="text-sm text-ap-muted">Prueba con otra palabra o pregúntale directamente al almacén.</p>
          <Button variant="outline" className="h-11 px-4" onClick={clear}>
            Quitar búsqueda
          </Button>
        </div>
      ) : (
        <div className="flex flex-col gap-4">
          {searching ? (
            <p role="status" className="text-sm text-ap-muted">
              Resultados para «{q}».{' '}
              <button type="button" onClick={clear} className="min-h-11 font-semibold text-ap-red underline underline-offset-4">
                Quitar búsqueda
              </button>
            </p>
          ) : null}
          <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', list.isStale && 'opacity-60')} aria-busy={list.isStale || undefined}>
            {data.items.map((item) => (
              <QuestionItem key={item.id} question={item} productId={productId} storeName={storeName} />
            ))}
          </ul>
          {data.totalPages > 1 ? (
            <ServerPager page={data.page} totalPages={data.totalPages} total={data.total} singular="pregunta" plural="preguntas" busy={list.status === 'loading'} onPageChange={setPage} />
          ) : null}
        </div>
      )}

      <AskForm productId={productId} productName={productName} />
    </section>
  )
}
