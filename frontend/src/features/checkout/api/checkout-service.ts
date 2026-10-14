import { DATA_SOURCE } from '@/lib/api'
import type { AddressService, CheckoutService } from './types'

let checkoutPromise: Promise<CheckoutService> | undefined
let addressPromise: Promise<AddressService> | undefined

/** Misma selección `mock`/`http` que el marketplace y las cuentas; cada implementación se descarga bajo demanda. */
export function getCheckoutService(): Promise<CheckoutService> {
  checkoutPromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-checkout-service').then((module) => module.createHttpCheckoutService())
      : import('./mock/mock-checkout-service').then((module) => module.createMockCheckoutService())
  return checkoutPromise
}

export function getAddressService(): Promise<AddressService> {
  addressPromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-address-service').then((module) => module.createHttpAddressService())
      : import('./mock/mock-address-service').then((module) => module.createMockAddressService())
  return addressPromise
}
