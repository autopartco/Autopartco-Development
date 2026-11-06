import { DATA_SOURCE } from '@/lib/api'
import type { BenefitsService } from './types'

let servicePromise: Promise<BenefitsService> | undefined

/** Misma selección `mock`/`http` que el resto de la app; la implementación se descarga bajo demanda. */
export function getBenefitsService(): Promise<BenefitsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-benefits-service').then((module) => module.createHttpBenefitsService())
      : import('./mock/mock-benefits-service').then((module) => module.createMockBenefitsService())
  return servicePromise
}
