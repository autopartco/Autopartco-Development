import { apiRequest } from '@/lib/api'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'
import type { Benefit, BenefitsService } from '../types'

/** `MyBenefitsController`: `GET /marketplace/me/benefits` (solo del titular de la sesión). */
export function createHttpBenefitsService(): BenefitsService {
  return {
    async list() {
      const token = await requireAccessToken()
      return apiRequest<Benefit[]>('/marketplace/me/benefits', { token })
    },
  }
}
