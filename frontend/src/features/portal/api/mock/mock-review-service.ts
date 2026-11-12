import { ApiError, simulateLatency } from '@/lib/api'
import type { ReviewPortalService } from '../review-types'

/** Sin datos simulados: las reseñas las escriben compradores reales, así que la vista simulada está vacía. */
export function createMockReviewPortalService(): ReviewPortalService {
  return {
    async list(_storeId, query) {
      await simulateLatency(200)
      return { items: [], page: query.page, size: 10, total: 0, totalPages: 0 }
    },
    async summary() {
      await simulateLatency(200)
      return {
        summary: { count: 0, distribution: [0, 0, 0, 0, 0], aspects: [], highlights: [] },
        unanswered: 0,
        topProducts: [],
        bottomProducts: [],
      }
    },
    async reply() {
      throw new ApiError('Las reseñas solo están disponibles con la API real.', { status: 409, code: 'REVIEW_NOT_ALLOWED' })
    },
    async removeReply() {
      throw new ApiError('Las reseñas solo están disponibles con la API real.', { status: 409, code: 'REVIEW_NOT_ALLOWED' })
    },
  }
}
