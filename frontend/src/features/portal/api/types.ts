/**
 * Modelo del portal de almacenes. Contratos en `docs/architecture/2026-10-14-mvp-saas-vendedores-plan.md` §4.3 y §4.6.
 */

export type SubscriptionStatus = 'PENDING_SETUP' | 'TRIALING' | 'ACTIVE' | 'EXPIRED'

export interface MyStore {
  storeId: string
  businessName: string
  logoUrl: string | null
  applicationStatus: 'ACTIVE' | 'CONFIGURATION_PENDING' | 'PENDING_REVIEW' | 'REJECTED'
  isOwner: boolean
  roleNames: string[]
  subscription: { status: SubscriptionStatus; planCode: string; planName: string; validThrough: string | null } | null
}

export interface Plan {
  id: string
  code: string
  name: string
  description: string | null
  monthlyPriceCop: number
  currency: 'COP'
  features: string[]
  recommended: boolean
  enabled: boolean
  maxBranches: number | null
  maxProducts: number | null
  maxUsers: number | null
  capabilities: string[]
}

export interface Subscription {
  storeId: string
  status: SubscriptionStatus
  plan: Plan
  selectedPlan: Plan | null
  trialStartsAt: string | null
  trialEndsAt: string | null
  paidThrough: string | null
  validThrough: string | null
  daysRemaining: number | null
  usage: Record<'branches' | 'products' | 'users', { used: number; limit: number | null }>
  capabilities: string[]
  canManage: boolean
  planChangeAllowed: boolean
}

export interface SubscriptionPayment {
  id: string
  reference: string
  provider: 'SIMULATED' | 'WOMPI'
  planCode: string
  planName: string
  months: number
  amountCop: number
  currency: 'COP'
  status: string
  createdAt: string
  paidAt: string | null
  periodStartsAt: string | null
  periodEndsAt: string | null
}
