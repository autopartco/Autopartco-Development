import { DATA_SOURCE } from '@/lib/api'
import type { QuestionsService } from './types'

let servicePromise: Promise<QuestionsService> | undefined

/** Misma selección `mock`/`http` que el resto de la app; la implementación se descarga bajo demanda. */
export function getQuestionsService(): Promise<QuestionsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-questions-service').then((module) => module.createHttpQuestionsService())
      : import('./mock/mock-questions-service').then((module) => module.createMockQuestionsService())
  return servicePromise
}
