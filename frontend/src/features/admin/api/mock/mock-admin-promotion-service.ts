import { ApiError, simulateLatency } from '@/lib/api'
import type { PromotionService } from '@/features/portal/api/promotion-service'
import type { Promotion, PromotionInput } from '@/features/portal/api/promotion-types'
import { mockPromotionResults } from '@/features/portal/api/mock/mock-promotion-service'

const DAY = 86_400_000
const iso = (days: number) => new Date(Date.now() + days * DAY).toISOString()

function withPhase(promotion: Promotion): Promotion {
  const now = Date.now()
  return { ...promotion, currentlyActive: promotion.enabled && Date.parse(promotion.startsAt) <= now && Date.parse(promotion.endsAt) > now }
}

const base = { scope: 'PLATFORM' as const, storeId: null, targetId: null, attributeKey: null, attributeValue: null, attributeOperator: null, enabled: true, currentlyActive: false }

const seeded: Promotion[] = [
  { ...base, id: 'admin-promo-1', name: 'Precio de temporada', targetType: 'ALL', discountType: 'PERCENTAGE', discountValue: 10, startsAt: iso(-12), endsAt: iso(18), audience: 'PLATFORM_FREQUENT' },
  { ...base, id: 'admin-promo-2', name: 'Semana de lanzamiento', targetType: 'ALL', discountType: 'PERCENTAGE', discountValue: 5, startsAt: iso(-60), endsAt: iso(-30), audience: 'ALL' },
]
const promotions: Promotion[] = seeded.map(withPhase)

function validate(input: PromotionInput): void {
  if (input.audience !== 'ALL' && input.audience !== 'PLATFORM_FREQUENT') {
    throw new ApiError('Las promociones de plataforma solo pueden dirigirse a todos los compradores o a los clientes frecuentes de Autopartco.', {
      status: 400,
      code: 'INVALID_PROMOTION_AUDIENCE',
    })
  }
}

function build(id: string, input: PromotionInput): Promotion {
  return withPhase({
    ...base,
    id,
    name: input.name,
    targetType: input.targetType,
    targetId: input.targetId ?? null,
    attributeKey: input.attributeKey ?? null,
    attributeValue: input.attributeValue ?? null,
    attributeOperator: input.attributeOperator ?? null,
    discountType: input.discountType,
    discountValue: input.discountValue,
    startsAt: input.startsAt,
    endsAt: input.endsAt,
    enabled: input.enabled,
    audience: input.audience,
  })
}

const notFound = () => new ApiError('No encontramos esa promoción.', { status: 404, code: 'PROMOTION_NOT_FOUND' })

export function createMockAdminPromotionService(): PromotionService {
  return {
    async list() {
      await simulateLatency(400)
      return promotions.map(withPhase)
    },
    async create(_storeId, input) {
      await simulateLatency(400)
      validate(input)
      const created = build(`admin-promo-${crypto.randomUUID()}`, input)
      promotions.unshift(created)
      return created
    },
    async update(_storeId, promotionId, input) {
      await simulateLatency(400)
      validate(input)
      const index = promotions.findIndex((item) => item.id === promotionId)
      if (index < 0) throw notFound()
      const updated = build(promotionId, input)
      promotions[index] = updated
      return updated
    },
    async remove(_storeId, promotionId) {
      await simulateLatency(400)
      const index = promotions.findIndex((item) => item.id === promotionId)
      if (index < 0) throw notFound()
      promotions.splice(index, 1)
    },
    async results(_storeId, promotionId) {
      await simulateLatency(400)
      const promotion = promotions.find((item) => item.id === promotionId)
      if (!promotion) throw notFound()
      return mockPromotionResults(promotion)
    },
  }
}
