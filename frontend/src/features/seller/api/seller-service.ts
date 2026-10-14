import { DATA_SOURCE } from '@/lib/api'
import type { SellerApplicationService } from './types'

let servicePromise: Promise<SellerApplicationService> | undefined

/** Misma selección `mock`/`http` que el marketplace y las cuentas. */
export function getSellerApplicationService(): Promise<SellerApplicationService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-seller-service').then((module) => module.createHttpSellerApplicationService())
      : import('./mock/mock-seller-service').then((module) => module.createMockSellerApplicationService())
  return servicePromise
}
