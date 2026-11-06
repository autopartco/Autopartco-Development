import { ApiError, simulateLatency } from '@/lib/api'
import { getSession } from '@/features/auth/session-store'
import type { Benefit, BenefitsService } from '../types'

const DAY = 86_400_000
const inDays = (days: number) => new Date(Date.now() + days * DAY).toISOString()

/** Beneficios simulados: uno de plataforma y dos de almacenes, con distinto destino y vencimiento. */
function seed(): Benefit[] {
  return [
    {
      promotionId: 'benefit-plataforma',
      name: 'Precio de temporada',
      audience: 'PLATFORM_FREQUENT',
      storeId: null,
      storeName: null,
      discountType: 'PERCENTAGE',
      discountValue: 10,
      targetType: 'ALL',
      targetId: null,
      endsAt: inDays(21),
    },
    {
      promotionId: 'benefit-la13',
      name: 'Gracias por comprar seguido',
      audience: 'STORE_FREQUENT',
      storeId: 'store-la13',
      storeName: 'Repuestos La 13',
      discountType: 'PERCENTAGE',
      discountValue: 8,
      targetType: 'STORE',
      targetId: null,
      endsAt: inDays(9),
    },
    {
      promotionId: 'benefit-valle',
      name: 'Te extrañamos',
      audience: 'STORE_AT_RISK',
      storeId: 'store-valle',
      storeName: 'Frenos y Embragues del Valle',
      discountType: 'FIXED_AMOUNT',
      discountValue: 15000,
      targetType: 'CATEGORY',
      targetId: null,
      endsAt: inDays(30),
    },
  ]
}

export function createMockBenefitsService(): BenefitsService {
  return {
    async list() {
      await simulateLatency(450)
      if (!getSession()) throw new ApiError('Debes iniciar sesión.', { status: 401, code: 'UNAUTHORIZED' })
      // `sinbeneficios...` simula una cuenta sin promociones dirigidas para ver el estado vacío.
      if (getSession()?.user.email.startsWith('sinbeneficios')) return []
      return seed()
    },
  }
}
