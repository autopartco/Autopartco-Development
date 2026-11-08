import { DATA_SOURCE } from '@/lib/api'
import type { AdminOpsService } from './ops-types'

let servicePromise: Promise<AdminOpsService> | undefined

/** Planes y catálogo maestro. Misma selección `mock`/`http` que el resto de la app. */
export function getAdminOpsService(): Promise<AdminOpsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-admin-ops-service').then((module) => module.createHttpAdminOpsService())
      : import('./mock/mock-admin-ops-service').then((module) => module.createMockAdminOpsService())
  return servicePromise
}
