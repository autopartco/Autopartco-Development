import { ApiError, simulateLatency } from '@/lib/api'
import type { ReviewsService } from '../types'

const UNAVAILABLE = 'Las reseñas solo están disponibles con la API real.'

/** Sin datos simulados: no hay pedidos entregados que calificar. */
export function createMockReviewsService(): ReviewsService {
  const unavailable = async (): Promise<never> => {
    await simulateLatency(200)
    throw new ApiError(UNAVAILABLE, { status: 409, code: 'REVIEW_NOT_ALLOWED' })
  }
  return {
    simulated: true,
    async pending() {
      await simulateLatency(200)
      return []
    },
    create: unavailable,
    update: unavailable,
    discard: unavailable,
  }
}
