import { DATA_SOURCE } from '@/lib/api'
import type { OrdersService } from './types'

let ordersPromise: Promise<OrdersService> | undefined

/** Misma selección `mock`/`http` que el checkout; la implementación se descarga bajo demanda. */
export function getOrdersService(): Promise<OrdersService> {
  ordersPromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-orders-service').then((module) => module.createHttpOrdersService())
      : import('./mock/mock-orders-service').then((module) => module.createMockOrdersService())
  return ordersPromise
}
