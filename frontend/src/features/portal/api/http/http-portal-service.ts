import { apiRequest } from '@/lib/api'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import type { PortalService } from '../portal-service'
import type { MyStore, Subscription, SubscriptionPayment } from '../types'

export function createHttpPortalService(): PortalService {
  async function get<T>(path: string): Promise<T> {
    return apiRequest<T>(path, { token: await getAccessToken() })
  }

  return {
    myStores: () => get<MyStore[]>('/me/stores'),
    subscription: (storeId) => get<Subscription>(`/stores/${storeId}/subscription`),
    payments: (storeId) => get<SubscriptionPayment[]>(`/stores/${storeId}/subscription/payments`),
  }
}
