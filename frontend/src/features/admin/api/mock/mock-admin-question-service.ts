import { ApiError, simulateLatency } from '@/lib/api'
import type { AdminQuestionService } from '../question-types'

const UNAVAILABLE = 'La moderación de preguntas solo está disponible con la API real.'

/** Sin datos simulados: las preguntas son de compradores reales. */
export function createMockAdminQuestionService(): AdminQuestionService {
  const unavailable = async (): Promise<never> => {
    throw new ApiError(UNAVAILABLE, { status: 409, code: 'QUESTION_NOT_ALLOWED' })
  }
  return {
    async list(query) {
      await simulateLatency(200)
      return { items: [], page: query.page, size: 20, total: 0, totalPages: 0 }
    },
    hide: unavailable,
    unhide: unavailable,
  }
}
