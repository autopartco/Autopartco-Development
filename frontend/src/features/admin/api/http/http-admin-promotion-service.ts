import type { PromotionService } from '@/features/portal/api/promotion-service'
import type { Promotion, PromotionResults } from '@/features/portal/api/promotion-types'
import { portalRequest } from '@/features/portal/api/http/portal-http'

const BASE = '/admin/promotions'
const enc = encodeURIComponent

/** `PlatformPromotionController`. Un 401 limpia la sesión (igual que el portal); un 403 se muestra como «sin acceso». */
export function createHttpAdminPromotionService(): PromotionService {
  return {
    list: () => portalRequest<Promotion[]>(BASE),
    create: (_storeId, input) => portalRequest<Promotion>(BASE, { method: 'POST', body: input }),
    update: (_storeId, promotionId, input) => portalRequest<Promotion>(`${BASE}/${enc(promotionId)}`, { method: 'PUT', body: input }),
    remove: (_storeId, promotionId) => portalRequest<void>(`${BASE}/${enc(promotionId)}`, { method: 'DELETE' }),
    results: (_storeId, promotionId) => portalRequest<PromotionResults>(`${BASE}/${enc(promotionId)}/results`),
  }
}
