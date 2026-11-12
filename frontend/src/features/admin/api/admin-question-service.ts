import { DATA_SOURCE } from '@/lib/api'
import type { AdminQuestionService } from './question-types'

let servicePromise: Promise<AdminQuestionService> | undefined

/** Moderación de preguntas. Misma selección `mock`/`http` que el resto de la consola. */
export function getAdminQuestionService(): Promise<AdminQuestionService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-admin-question-service').then((module) => module.createHttpAdminQuestionService())
      : import('./mock/mock-admin-question-service').then((module) => module.createMockAdminQuestionService())
  return servicePromise
}
