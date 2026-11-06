import { DATA_SOURCE } from '@/lib/api'
import type { Promotion, PromotionInput, PromotionResults } from './promotion-types'

/**
 * Promociones del almacén (`/stores/{id}/promotions`). Exige `PROMOTION_READ` y la capability `PROMOTIONS`
 * (409 `SELLER_PLAN_FEATURE_UNAVAILABLE` sin ella). Errores de escritura: 409 `SELLER_PLAN_FEATURE_UNAVAILABLE`
 * al dirigir a `PLATFORM_FREQUENT` sin `ADVANCED_REPORTS`, 400 `INVALID_PROMOTION_TARGET`.
 * La implementación de administración (`/admin/promotions`) cumple la misma interfaz e ignora `storeId`.
 */
export interface PromotionService {
  /** `GET /promotions`. */
  list(storeId: string): Promise<Promotion[]>
  /** `POST /promotions` (`PROMOTION_CREATE`). */
  create(storeId: string, input: PromotionInput): Promise<Promotion>
  /** `PUT /promotions/{id}` (`PROMOTION_UPDATE`). */
  update(storeId: string, promotionId: string, input: PromotionInput): Promise<Promotion>
  /** `DELETE /promotions/{id}` (`PROMOTION_DELETE`). */
  remove(storeId: string, promotionId: string): Promise<void>
  /** `GET /promotions/{id}/results`. */
  results(storeId: string, promotionId: string): Promise<PromotionResults>
}

let servicePromise: Promise<PromotionService> | undefined

export function getPromotionService(): Promise<PromotionService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-promotion-service').then((module) => module.createHttpPromotionService())
      : import('./mock/mock-promotion-service').then((module) => module.createMockPromotionService())
  return servicePromise
}

/** Servicio que resuelve la implementación (`mock` o `http`) en cada llamada; permite pasarlo como valor a la interfaz. */
export const lazyPromotionService: PromotionService = {
  list: async (storeId) => (await getPromotionService()).list(storeId),
  create: async (storeId, input) => (await getPromotionService()).create(storeId, input),
  update: async (storeId, promotionId, input) => (await getPromotionService()).update(storeId, promotionId, input),
  remove: async (storeId, promotionId) => (await getPromotionService()).remove(storeId, promotionId),
  results: async (storeId, promotionId) => (await getPromotionService()).results(storeId, promotionId),
}
