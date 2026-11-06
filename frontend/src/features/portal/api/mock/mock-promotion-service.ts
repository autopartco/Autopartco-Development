import { ApiError } from '@/lib/api'
import type { PromotionService } from '../promotion-service'
import type { Promotion, PromotionInput, PromotionResults } from '../promotion-types'
import { hashOf } from './mock-analytics-data'
import { getStore, newId, requireCapability, requirePermission, simulateNetwork, type MockStore } from './mock-state'

const DAY = 86_400_000
const iso = (days: number) => new Date(Date.now() + days * DAY).toISOString()

const byStore = new Map<string, Promotion[]>()

function withPhase(promotion: Promotion): Promotion {
  const now = Date.now()
  const active = promotion.enabled && Date.parse(promotion.startsAt) <= now && Date.parse(promotion.endsAt) > now
  return { ...promotion, currentlyActive: active }
}

function seed(store: MockStore): Promotion[] {
  const storeId = store.summary.storeId
  const row = store.products[0]
  const base = { scope: 'STORE' as const, storeId, targetType: 'STORE' as const, targetId: null, attributeKey: null, attributeValue: null, attributeOperator: null, enabled: true, currentlyActive: false }
  const items: Promotion[] = [
    { ...base, id: newId('promo'), name: 'Semana del mecánico', discountType: 'PERCENTAGE', discountValue: 10, startsAt: iso(-10), endsAt: iso(20), audience: 'ALL' },
    { ...base, id: newId('promo'), name: 'Gracias por volver a comprar', discountType: 'PERCENTAGE', discountValue: 8, startsAt: iso(-45), endsAt: iso(15), audience: 'STORE_FREQUENT' },
    { ...base, id: newId('promo'), name: 'Te extrañamos', targetType: row ? 'PRODUCT' : 'STORE', targetId: row?.id ?? null, discountType: 'FIXED_AMOUNT', discountValue: 5000, startsAt: iso(-5), endsAt: iso(25), audience: 'STORE_AT_RISK' },
    { ...base, id: newId('promo'), name: 'Liquidación de fin de año', discountType: 'PERCENTAGE', discountValue: 20, startsAt: iso(-90), endsAt: iso(-30), audience: 'ALL' },
    { ...base, id: newId('promo'), name: 'Estreno de temporada', discountType: 'PERCENTAGE', discountValue: 12, startsAt: iso(7), endsAt: iso(37), audience: 'ALL' },
  ]
  return items.map(withPhase)
}

function listFor(store: MockStore): Promotion[] {
  const id = store.summary.storeId
  let list = byStore.get(id)
  if (!list) {
    list = seed(store)
    byStore.set(id, list)
  }
  return list
}

function invalidTarget(message: string): ApiError {
  return new ApiError(message, { status: 400, code: 'INVALID_PROMOTION_TARGET' })
}

function validate(store: MockStore, input: PromotionInput): void {
  if (input.audience === 'PLATFORM_FREQUENT') requireCapability(store, 'ADVANCED_REPORTS')
  if (input.targetType === 'ALL') throw invalidTarget('Solo la plataforma puede crear una promoción global.')
  if (input.targetType === 'ATTRIBUTE' && (!input.attributeKey?.trim() || !input.attributeValue?.trim())) {
    throw invalidTarget('Las promociones por atributo requieren una clave y un valor, sin ID.')
  }
  if (['PRODUCT', 'CATEGORY', 'MANUFACTURER'].includes(input.targetType) && !input.targetId) {
    throw invalidTarget('Este tipo de promoción requiere un ID de destino válido.')
  }
}

function fromInput(storeId: string, id: string, input: PromotionInput): Promotion {
  return withPhase({
    id,
    scope: 'STORE',
    storeId,
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
    currentlyActive: false,
    audience: input.audience,
  })
}

/** Resultados simulados y deterministas por promoción (mismas reglas de estado que el servidor). */
export function mockPromotionResults(promotion: Promotion): PromotionResults {
  const seedValue = hashOf(promotion.id + promotion.name)
  const finished = Date.parse(promotion.endsAt) <= Date.now()
  const started = Date.parse(promotion.startsAt) <= Date.now()
  const orders = started ? 8 + (seedValue % 40) : 0
  const revenue = orders * (48_000 + (seedValue % 30) * 1000)
  const baseline = { revenuePerDay: 310_000 + (seedValue % 90) * 1000, ordersPerDay: 3.2 + (seedValue % 8) / 10 }
  const base = { orders, revenue, unitsSold: Math.round(orders * 1.7), baseline }
  if (promotion.audience === 'ALL') {
    return { ...base, exposures: 0, buyers: Math.round(orders * 0.85), lift: null, controlConversion: null, exposedConversion: null, status: 'INSUFFICIENT_DATA' }
  }
  const insufficient = promotion.audience === 'STORE_AT_RISK' || !started
  if (insufficient) {
    return { ...base, exposures: started ? 14 : 0, buyers: started ? 3 : 0, lift: null, controlConversion: null, exposedConversion: null, status: 'INSUFFICIENT_DATA' }
  }
  const control = 0.04 + (seedValue % 30) / 1000
  const exposed = control * (1.2 + (seedValue % 40) / 100)
  return {
    ...base,
    exposures: 180 + (seedValue % 200),
    buyers: 42 + (seedValue % 30),
    lift: Math.round(((exposed - control) / control) * 100) / 100,
    controlConversion: Math.round(control * 1000) / 1000,
    exposedConversion: Math.round(exposed * 1000) / 1000,
    status: finished ? 'FINISHED' : 'RUNNING',
  }
}

export function createMockPromotionService(): PromotionService {
  return {
    async list(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PROMOTION_READ')
      requireCapability(store, 'PROMOTIONS')
      return listFor(store).map(withPhase)
    },
    async create(storeId, input) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PROMOTION_CREATE')
      requireCapability(store, 'PROMOTIONS')
      validate(store, input)
      const created = fromInput(storeId, newId('promo'), input)
      listFor(store).unshift(created)
      return created
    },
    async update(storeId, promotionId, input) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PROMOTION_UPDATE')
      requireCapability(store, 'PROMOTIONS')
      validate(store, input)
      const list = listFor(store)
      const index = list.findIndex((item) => item.id === promotionId)
      if (index < 0) throw new ApiError('No encontramos esa promoción.', { status: 404, code: 'PROMOTION_NOT_FOUND' })
      const updated = fromInput(storeId, promotionId, input)
      list[index] = updated
      return updated
    },
    async remove(storeId, promotionId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PROMOTION_DELETE')
      requireCapability(store, 'PROMOTIONS')
      const list = listFor(store)
      const index = list.findIndex((item) => item.id === promotionId)
      if (index < 0) throw new ApiError('No encontramos esa promoción.', { status: 404, code: 'PROMOTION_NOT_FOUND' })
      list.splice(index, 1)
    },
    async results(storeId, promotionId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PROMOTION_READ')
      requireCapability(store, 'PROMOTIONS')
      const promotion = listFor(store).find((item) => item.id === promotionId)
      if (!promotion) throw new ApiError('No encontramos esa promoción.', { status: 404, code: 'PROMOTION_NOT_FOUND' })
      return mockPromotionResults(promotion)
    },
  }
}
