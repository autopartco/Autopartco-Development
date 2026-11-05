import { DATA_SOURCE } from '@/lib/api'
import type {
  CheckoutInput,
  CheckoutResult,
  Plan,
  SimulatedPaymentInput,
  SimulatedPaymentResult,
  Subscription,
  SubscriptionPayment,
} from './types'

/** Suscripción, planes y cobro de la tienda. */
export interface SubscriptionService {
  /** `GET /stores/{id}/subscription`. */
  subscription(storeId: string): Promise<Subscription>
  /** `GET /plans` (público; solo planes habilitados). */
  plans(): Promise<Plan[]>
  /** `GET /stores/{id}/subscription/payments`. */
  payments(storeId: string): Promise<SubscriptionPayment[]>
  /** `GET /stores/{id}/subscription/payments/{reference}` (OWNER; 404 `SELLER_PAYMENT_NOT_FOUND`). */
  payment(storeId: string, reference: string): Promise<SubscriptionPayment>
  /** `POST …/payments/{reference}/sync` con el `id` de la redirección de Wompi (429 `PAYMENT_SYNC_RATE_LIMITED`). */
  syncPayment(storeId: string, reference: string, transactionId: string): Promise<SubscriptionPayment>
  /** `POST /stores/{id}/subscription/simulated-payments` (solo OWNER; 402 si se rechaza). */
  paySimulated(storeId: string, input: SimulatedPaymentInput): Promise<SimulatedPaymentResult>
  /** `POST /stores/{id}/subscription/checkout` (Wompi): devuelve la URL a la que se redirige. */
  checkout(storeId: string, input: CheckoutInput): Promise<CheckoutResult>
}

let servicePromise: Promise<SubscriptionService> | undefined

export function getSubscriptionService(): Promise<SubscriptionService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-subscription-service').then((module) => module.createHttpSubscriptionService())
      : import('./mock/mock-subscription-service').then((module) => module.createMockSubscriptionService())
  return servicePromise
}
