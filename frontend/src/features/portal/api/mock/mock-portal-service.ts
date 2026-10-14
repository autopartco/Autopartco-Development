import { simulateLatency } from '@/lib/api'
import type { PortalService } from '../portal-service'
import type { Plan, Subscription, SubscriptionPayment } from '../types'

const STORE_ID = '6f1c2a3e-0000-4000-8000-000000000001'

const trialPlan: Plan = {
  id: 'p-try',
  code: 'TRY',
  name: 'Prueba gratuita',
  description: '1 mes para probar la plataforma',
  monthlyPriceCop: 0,
  currency: 'COP',
  features: ['1 sucursal', '100 productos'],
  recommended: false,
  enabled: true,
  maxBranches: 1,
  maxProducts: 100,
  maxUsers: 2,
  capabilities: ['BASIC_ANALYTICS', 'CSV_IMPORT'],
}

const subscription: Subscription = {
  storeId: STORE_ID,
  status: 'TRIALING',
  plan: trialPlan,
  selectedPlan: null,
  trialStartsAt: '2026-10-01T10:00:00',
  trialEndsAt: '2026-11-01T10:00:00',
  paidThrough: null,
  validThrough: '2026-11-01T10:00:00',
  daysRemaining: 18,
  usage: {
    branches: { used: 1, limit: 1 },
    products: { used: 40, limit: 100 },
    users: { used: 1, limit: 2 },
  },
  capabilities: trialPlan.capabilities,
  canManage: true,
  planChangeAllowed: true,
}

/** Datos simulados para desarrollo sin backend. No llama a ningún servidor. */
export function createMockPortalService(): PortalService {
  return {
    async myStores() {
      await simulateLatency()
      return [
        {
          storeId: STORE_ID,
          businessName: 'Repuestos La 30',
          logoUrl: null,
          applicationStatus: 'ACTIVE',
          isOwner: true,
          roleNames: ['Propietario'],
          subscription: { status: subscription.status, planCode: trialPlan.code, planName: trialPlan.name, validThrough: subscription.validThrough },
        },
      ]
    },
    async subscription() {
      await simulateLatency()
      return subscription
    },
    async payments() {
      await simulateLatency()
      return [] as SubscriptionPayment[]
    },
  }
}
