import { DATA_SOURCE } from '@/lib/api'
import type { ReviewsService } from './types'

let servicePromise: Promise<ReviewsService> | undefined

/** Misma selección `mock`/`http` que el resto de la app; la implementación se descarga bajo demanda. */
export function getReviewsService(): Promise<ReviewsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-reviews-service').then((module) => module.createHttpReviewsService())
      : import('./mock/mock-reviews-service').then((module) => module.createMockReviewsService())
  return servicePromise
}
