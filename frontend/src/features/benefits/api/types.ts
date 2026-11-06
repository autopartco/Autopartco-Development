/** `GET /marketplace/me/benefits` (autenticado): promociones dirigidas al segmento del comprador. */

export type BenefitAudience = 'ALL' | 'PLATFORM_FREQUENT' | 'STORE_FREQUENT' | 'STORE_AT_RISK' | 'STORE_NEW' | 'STORE_DORMANT'

export interface Benefit {
  promotionId: string
  name: string
  audience: BenefitAudience
  /** `null` en promociones de plataforma. */
  storeId: string | null
  storeName: string | null
  discountType: 'PERCENTAGE' | 'FIXED_AMOUNT'
  discountValue: number
  targetType: 'ALL' | 'STORE' | 'PRODUCT' | 'CATEGORY' | 'MANUFACTURER' | 'ATTRIBUTE'
  targetId: string | null
  /** ISO 8601. */
  endsAt: string
}

export interface BenefitsService {
  list(): Promise<Benefit[]>
}
