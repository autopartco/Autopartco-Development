import { DATA_SOURCE } from '@/lib/api'
import type { AdminInsightsService } from './insights-types'

let servicePromise: Promise<AdminInsightsService> | undefined

/** Auditoría y estadísticas de plataforma. Misma selección `mock`/`http` que el resto de la app. */
export function getAdminInsightsService(): Promise<AdminInsightsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-admin-insights-service').then((module) => module.createHttpAdminInsightsService())
      : import('./mock/mock-admin-insights-service').then((module) => module.createMockAdminInsightsService())
  return servicePromise
}
