import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getQuestionsService } from './api/questions-service'
import type { QuestionsQuery } from './api/types'

/** Tamaño de página de la ficha (el mismo que usa el conteo, para compartir la consulta). */
export const QUESTIONS_PAGE_SIZE = 10

/** La clave incluye la sesión: con ella el servidor añade las preguntas pendientes propias. */
export function useProductQuestions(productId: string, sessionKey: string | undefined, query: QuestionsQuery) {
  const q = query.q?.trim() ?? ''
  return useAsync(`questions:${productId}:${sessionKey ?? 'anon'}:${q}:${query.page}`, async () =>
    (await getQuestionsService()).list(productId, { page: query.page, q: q || undefined, size: QUESTIONS_PAGE_SIZE }),
  )
}

/**
 * Preguntas respondidas del producto, para el enlace junto al título. Comparte la consulta de la primera página:
 * las respondidas van primero, así que si hay más de una página todas las de la primera están respondidas.
 */
export function useAnsweredQuestionCount(productId: string, sessionKey: string | undefined): number | undefined {
  const first = useProductQuestions(productId, sessionKey, { page: 1 }).data
  if (!first) return undefined
  const answeredOnPage = first.items.filter((item) => item.answer).length
  return first.items.some((item) => !item.answer) || first.totalPages <= 1 ? answeredOnPage : first.total
}

/** Tras preguntar o borrar una pregunta: descarta las listas en memoria. */
export function invalidateQuestions(): void {
  invalidateAsync('questions:')
}
