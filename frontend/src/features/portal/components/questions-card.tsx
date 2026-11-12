import { Link } from 'react-router'
import { pluralize } from '@/lib/format'
import { usePortal } from '../context'
import { usePortalQuestionPending } from '../lib/questions'

/**
 * Preguntas en el panel: cuántas esperan respuesta, con enlace a la pantalla de preguntas. Si la consulta falla
 * (p. ej. sin alcance) no se dibuja: la pantalla de preguntas explica el motivo.
 */
export function QuestionsCard() {
  const { storeId } = usePortal()
  const pending = usePortalQuestionPending().data
  if (pending === undefined) return null

  const link = `/portal/${encodeURIComponent(storeId)}/preguntas`
  return (
    <section aria-labelledby="preguntas-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
      <h2 id="preguntas-titulo" className="text-base font-semibold text-ap-ink">
        Preguntas
      </h2>
      <p className={pending > 0 ? 'text-sm font-semibold text-ap-warning' : 'text-sm text-ap-muted'}>
        {pending > 0 ? pluralize(pending, 'pregunta sin responder', 'preguntas sin responder') : 'No tienes preguntas por responder.'}
      </p>
      <Link to={link} className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
        {pending > 0 ? 'Responder preguntas' : 'Ver preguntas'}
      </Link>
    </section>
  )
}
