/**
 * Promociones (fase 2). Contratos de `/stores/{id}/promotions` (permisos `PROMOTION_*` y capability `PROMOTIONS`)
 * y de `/admin/promotions`. El servidor decide qué promoción aplica a cada comprador y calcula los resultados.
 */

export type PromotionAudience = 'ALL' | 'PLATFORM_FREQUENT' | 'STORE_FREQUENT' | 'STORE_AT_RISK' | 'STORE_NEW' | 'STORE_DORMANT'
export type PromotionScope = 'STORE' | 'PLATFORM'
export type PromotionTargetType = 'ALL' | 'STORE' | 'PRODUCT' | 'CATEGORY' | 'MANUFACTURER' | 'ATTRIBUTE'
export type PromotionDiscountType = 'PERCENTAGE' | 'FIXED_AMOUNT'
export type PromotionAttributeOperator = 'EQUALS' | 'CONTAINS'

/** `PromotionResponse`. */
export interface Promotion {
  id: string
  scope: PromotionScope
  storeId: string | null
  name: string
  targetType: PromotionTargetType
  targetId: string | null
  attributeKey: string | null
  attributeValue: string | null
  attributeOperator: PromotionAttributeOperator | null
  discountType: PromotionDiscountType
  discountValue: number
  /** ISO 8601 con zona. */
  startsAt: string
  endsAt: string
  enabled: boolean
  /** Vigente ahora según el servidor (habilitada y dentro de sus fechas). */
  currentlyActive: boolean
  audience: PromotionAudience
}

/** `PromotionUpsertRequest`. `audience` sin valor equivale a `ALL`. */
export interface PromotionInput {
  name: string
  targetType: PromotionTargetType
  targetId?: string | null
  attributeKey?: string | null
  attributeValue?: string | null
  attributeOperator?: PromotionAttributeOperator | null
  discountType: PromotionDiscountType
  discountValue: number
  startsAt: string
  endsAt: string
  enabled: boolean
  audience: PromotionAudience
}

export type PromotionResultStatus = 'RUNNING' | 'FINISHED' | 'INSUFFICIENT_DATA'

/** `PromotionResult`. Las conversiones y `lift` son fracciones (0,25 = 25 %) o `null` sin datos suficientes. */
export interface PromotionResults {
  exposures: number
  buyers: number
  orders: number
  revenue: number
  unitsSold: number
  baseline: { revenuePerDay: number; ordersPerDay: number }
  lift: number | null
  controlConversion: number | null
  exposedConversion: number | null
  status: PromotionResultStatus
}
