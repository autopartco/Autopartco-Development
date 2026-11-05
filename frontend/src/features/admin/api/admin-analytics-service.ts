import { DATA_SOURCE } from '@/lib/api'
import type { AdminAnalyticsService } from './analytics-types'

let servicePromise: Promise<AdminAnalyticsService> | undefined

/** Misma selección `mock`/`http` que el resto de la app. */
export function getAdminAnalyticsService(): Promise<AdminAnalyticsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-admin-analytics-service').then((module) => module.createHttpAdminAnalyticsService())
      : import('./mock/mock-admin-analytics-service').then((module) => module.createMockAdminAnalyticsService())
  return servicePromise
}
