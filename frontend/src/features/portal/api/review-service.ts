import { DATA_SOURCE } from '@/lib/api'
import type { ReviewPortalService } from './review-types'

let servicePromise: Promise<ReviewPortalService> | undefined

/** Reseñas del portal. Misma selección `mock`/`http` que el resto de servicios. */
export function getReviewPortalService(): Promise<ReviewPortalService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-review-service').then((module) => module.createHttpReviewPortalService())
      : import('./mock/mock-review-service').then((module) => module.createMockReviewPortalService())
  return servicePromise
}
