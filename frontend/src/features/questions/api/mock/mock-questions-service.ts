import { ApiError, simulateLatency } from '@/lib/api'
import type { QuestionsService } from '../types'

const UNAVAILABLE = 'Las preguntas solo están disponibles con la API real.'

/** Sin datos simulados: las preguntas las escriben compradores reales. */
export function createMockQuestionsService(): QuestionsService {
  const unavailable = async (): Promise<never> => {
    await simulateLatency(200)
    throw new ApiError(UNAVAILABLE, { status: 409, code: 'QUESTION_NOT_ALLOWED' })
  }
  return {
    async list(_productId, query) {
      await simulateLatency(200)
      return { items: [], page: query.page, size: query.size ?? 10, total: 0, totalPages: 0 }
    },
    ask: unavailable,
    remove: unavailable,
  }
}
