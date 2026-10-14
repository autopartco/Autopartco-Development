import type { MarketplaceService } from './types'

/**
 * Selecciona el origen de datos con `VITE_MARKETPLACE_DATA_SOURCE` (`mock` por defecto).
 * Cada implementación se carga bajo demanda, así los datos simulados no viajan
 * en el bundle cuando se usa la API real.
 */
const dataSource = import.meta.env.VITE_MARKETPLACE_DATA_SOURCE === 'http' ? 'http' : 'mock'
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

let servicePromise: Promise<MarketplaceService> | undefined

export function getMarketplaceService(): Promise<MarketplaceService> {
  servicePromise ??=
    dataSource === 'http'
      ? import('./http/http-service').then((module) => module.createHttpMarketplaceService(apiBaseUrl))
      : import('./mock/mock-service').then((module) => module.createMockMarketplaceService())
  return servicePromise
}

export const MARKETPLACE_DATA_SOURCE = dataSource
