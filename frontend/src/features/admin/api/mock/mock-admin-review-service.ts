import { ApiError, simulateLatency } from '@/lib/api'
import type { AdminReviewService } from '../review-types'

const UNAVAILABLE = 'La moderación de reseñas solo está disponible con la API real.'

/** Sin datos simulados: las reseñas son de compradores reales. */
export function createMockAdminReviewService(): AdminReviewService {
  const unavailable = async (): Promise<never> => {
    throw new ApiError(UNAVAILABLE, { status: 409, code: 'REVIEW_NOT_ALLOWED' })
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
