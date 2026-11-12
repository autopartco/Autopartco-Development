import { DATA_SOURCE } from '@/lib/api'
import type { QuestionPortalService } from './question-types'

let servicePromise: Promise<QuestionPortalService> | undefined

/** Preguntas del portal. Misma selección `mock`/`http` que el resto de servicios. */
export function getQuestionPortalService(): Promise<QuestionPortalService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-question-service').then((module) => module.createHttpQuestionPortalService())
      : import('./mock/mock-question-service').then((module) => module.createMockQuestionPortalService())
  return servicePromise
}
