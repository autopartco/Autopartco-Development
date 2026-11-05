import { apiRequest } from '@/lib/api'
import type { SubscriptionService } from '../subscription-service'
import type { CheckoutResult, Plan, SimulatedPaymentResult, Subscription, SubscriptionPayment } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/subscription` (`SubscriptionController`, `PlanCatalogController`). */
export function createHttpSubscriptionService(): SubscriptionService {
  return {
    subscription: (storeId) => portalRequest<Subscription>(storePath(storeId, '/subscription')),
    // Catálogo público: no necesita token.
    plans: () => apiRequest<Plan[]>('/plans'),
    payments: (storeId) => portalRequest<SubscriptionPayment[]>(storePath(storeId, '/subscription/payments')),
    payment: (storeId, reference) =>
      portalRequest<SubscriptionPayment>(storePath(storeId, `/subscription/payments/${encodeURIComponent(reference)}`)),
    syncPayment: (storeId, reference, transactionId) =>
      portalRequest<SubscriptionPayment>(storePath(storeId, `/subscription/payments/${encodeURIComponent(reference)}/sync`), {
        method: 'POST',
        body: { transactionId },
      }),
    paySimulated: (storeId, input) =>
      portalRequest<SimulatedPaymentResult>(storePath(storeId, '/subscription/simulated-payments'), {
        method: 'POST',
        body: input,
      }),
    checkout: (storeId, input) =>
      portalRequest<CheckoutResult>(storePath(storeId, '/subscription/checkout'), { method: 'POST', body: input }),
  }
}
