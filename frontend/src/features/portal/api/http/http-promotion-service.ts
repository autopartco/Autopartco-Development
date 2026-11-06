import type { Promotion, PromotionResults } from '../promotion-types'
import type { PromotionService } from '../promotion-service'
import { portalRequest, storePath } from './portal-http'

const base = (storeId: string, suffix = '') => storePath(storeId, `/promotions${suffix}`)
const enc = encodeURIComponent

export function createHttpPromotionService(): PromotionService {
  return {
    list: (storeId) => portalRequest<Promotion[]>(base(storeId)),
    create: (storeId, input) => portalRequest<Promotion>(base(storeId), { method: 'POST', body: input }),
    update: (storeId, promotionId, input) => portalRequest<Promotion>(base(storeId, `/${enc(promotionId)}`), { method: 'PUT', body: input }),
    remove: (storeId, promotionId) => portalRequest<void>(base(storeId, `/${enc(promotionId)}`), { method: 'DELETE' }),
    results: (storeId, promotionId) => portalRequest<PromotionResults>(base(storeId, `/${enc(promotionId)}/results`)),
  }
}
