import { DATA_SOURCE } from '@/lib/api'
import type { PromotionService } from '@/features/portal/api/promotion-service'

/**
 * Promociones de plataforma (`/admin/promotions`, solo ROOT): misma interfaz que las del almacén (`storeId` se ignora).
 * La audiencia solo puede ser `ALL` o `PLATFORM_FREQUENT` (400 `INVALID_PROMOTION_AUDIENCE` con otra).
 */
let servicePromise: Promise<PromotionService> | undefined

export function getAdminPromotionService(): Promise<PromotionService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-admin-promotion-service').then((module) => module.createHttpAdminPromotionService())
      : import('./mock/mock-admin-promotion-service').then((module) => module.createMockAdminPromotionService())
  return servicePromise
}

/** Resuelve la implementación en cada llamada; se pasa como valor a la configuración compartida de promociones. */
export const lazyAdminPromotionService: PromotionService = {
  list: async (storeId) => (await getAdminPromotionService()).list(storeId),
  create: async (storeId, input) => (await getAdminPromotionService()).create(storeId, input),
  update: async (storeId, promotionId, input) => (await getAdminPromotionService()).update(storeId, promotionId, input),
  remove: async (storeId, promotionId) => (await getAdminPromotionService()).remove(storeId, promotionId),
  results: async (storeId, promotionId) => (await getAdminPromotionService()).results(storeId, promotionId),
}
