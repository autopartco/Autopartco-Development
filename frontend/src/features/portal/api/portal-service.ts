import { DATA_SOURCE } from '@/lib/api'
import type { MyStore, Subscription, SubscriptionPayment } from './types'

export interface PortalService {
  myStores(): Promise<MyStore[]>
  subscription(storeId: string): Promise<Subscription>
  payments(storeId: string): Promise<SubscriptionPayment[]>
}

let servicePromise: Promise<PortalService> | undefined

/** Misma selección `mock`/`http` que el resto de la app. */
export function getPortalService(): Promise<PortalService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-portal-service').then((module) => module.createHttpPortalService())
      : import('./mock/mock-portal-service').then((module) => module.createMockPortalService())
  return servicePromise
}
