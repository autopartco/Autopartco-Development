import { DATA_SOURCE } from '@/lib/api'
import { initSession } from '../session-store'
import type { AuthService } from './types'

let servicePromise: Promise<AuthService> | undefined

/**
 * Servicio de cuentas según `VITE_MARKETPLACE_DATA_SOURCE` (`mock` por defecto),
 * igual que el marketplace. Cada implementación se descarga bajo demanda.
 */
export function getAuthService(): Promise<AuthService> {
  servicePromise ??= (
    DATA_SOURCE === 'http'
      ? import('./http/http-auth-service').then((module) => module.createHttpAuthService())
      : import('./mock/mock-auth-service').then((module) => module.createMockAuthService())
  ).then((service) => {
    initSession(() => service.restore())
    return service
  })
  return servicePromise
}
