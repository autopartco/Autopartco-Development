import { DATA_SOURCE } from '@/lib/api'
import type { AdminReviewService } from './review-types'

let servicePromise: Promise<AdminReviewService> | undefined

/** Moderación de reseñas. Misma selección `mock`/`http` que el resto de la consola. */
export function getAdminReviewService(): Promise<AdminReviewService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-admin-review-service').then((module) => module.createHttpAdminReviewService())
      : import('./mock/mock-admin-review-service').then((module) => module.createMockAdminReviewService())
  return servicePromise
}
