import { MARKETPLACE_DATA_SOURCE } from '@/features/marketplace/api/marketplace-service'
import type { LocationService } from './types'

/**
 * Usa el mismo origen de datos que el marketplace (`VITE_MARKETPLACE_DATA_SOURCE`).
 * Cada implementación se carga bajo demanda.
 */
let servicePromise: Promise<LocationService> | undefined

export function getLocationService(): Promise<LocationService> {
  servicePromise ??=
    MARKETPLACE_DATA_SOURCE === 'http'
      ? import('./http/http-location-service').then((module) => module.createHttpLocationService())
      : import('./mock/mock-location-service').then((module) => module.createMockLocationService())
  return servicePromise
}
