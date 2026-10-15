import { DATA_SOURCE } from '@/lib/api'
import type { AdminApplicationService } from './types'

let servicePromise: Promise<AdminApplicationService> | undefined

/** Misma selección `mock`/`http` que el resto de la app. */
export function getAdminApplicationService(): Promise<AdminApplicationService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-admin-service').then((module) => module.createHttpAdminApplicationService())
      : import('./mock/mock-admin-service').then((module) => module.createMockAdminApplicationService())
  return servicePromise
}
