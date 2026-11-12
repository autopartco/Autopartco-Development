import { ApiError, simulateLatency } from '@/lib/api'
import type { QuestionPortalService } from '../question-types'

const UNAVAILABLE = 'Las preguntas solo están disponibles con la API real.'

/** Sin datos simulados: las preguntas las escriben compradores reales, así que la vista simulada está vacía. */
export function createMockQuestionPortalService(): QuestionPortalService {
  const unavailable = async (): Promise<never> => {
    throw new ApiError(UNAVAILABLE, { status: 409, code: 'QUESTION_NOT_ALLOWED' })
  }
  return {
    async list(_storeId, query) {
      await simulateLatency(200)
      return { items: [], page: query.page, size: 10, total: 0, totalPages: 0 }
    },
    async pendingCount() {
      await simulateLatency(200)
      return 0
    },
    answer: unavailable,
    removeAnswer: unavailable,
  }
}
