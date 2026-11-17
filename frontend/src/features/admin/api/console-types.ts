import type { Page } from '@/features/portal/api/types'

/**
 * Secciones de la consola ampliada (`/api/v1/admin`): acceso, pagos atípicos, modelos, almacenes, cuentas, pedidos,
 * pagos y equipo de plataforma. Fechas ISO; importes en COP.
 */

export type PlatformPermission =
  | 'PLATFORM_DASHBOARD_READ'
  | 'PLATFORM_APPLICATIONS_REVIEW'
  | 'PLATFORM_STORES_READ'
  | 'PLATFORM_ACCOUNTS_READ'
  | 'PLATFORM_ACCOUNTS_MANAGE'
  | 'PLATFORM_ORDERS_READ'
  | 'PLATFORM_PAYMENT_RISK_REVIEW'
  | 'PLATFORM_CATALOG_MANAGE'
  | 'PLATFORM_PLANS_MANAGE'
  | 'PLATFORM_PROMOTIONS_MANAGE'
  | 'PLATFORM_ADS_MANAGE'
  | 'PLATFORM_MODERATION'
  | 'PLATFORM_ANALYTICS_READ'
  | 'PLATFORM_AUDIT_READ'

/** `GET /admin/me`. */
export interface AdminMe {
  email: string
  root: boolean
  roleName: string | null
  permissions: PlatformPermission[]
}

// ---- Pagos atípicos ----------------------------------------------------------------------------------------------

export type RiskDecision = 'ALLOW' | 'REVIEW' | 'DENY'
export type RiskOutcome = 'LEGIT' | 'FRAUD_CONFIRMED' | 'CHARGEBACK' | 'FRIENDLY_FRAUD' | 'UNKNOWN'

export interface RiskAssessment {
  id: number
  assessedAt: string
  stage: string
  decision: RiskDecision
  /** 0..1. En `payment_anomaly`, puntuación de anomalía de Isolation Forest. */
  riskScore: number | null
  threshold: number | null
  model: string | null
  modelVersion: string | null
  outcome: RiskOutcome | null
  outcomeAt: string | null
  reviewedBy: string | null
  paymentId: string
  amount: number
  provider: string
  paymentStatus: string | null
  paymentCreatedAt: string
  attemptNumber: number | null
  orderId: string
  orderNumber: number | null
  customerEmail: string | null
}

export interface RiskSummary {
  days: number
  total: number
  review: number
  pendingReview: number
  legit: number
  fraudConfirmed: number
  averageScore: number | null
  reviewAmount: number
}

export interface RiskQuery {
  decision?: string
  status?: 'PENDING' | 'REVIEWED' | ''
  q?: string
  page: number
  size: number
}

// ---- Modelos -----------------------------------------------------------------------------------------------------

export interface MlModel {
  code: string
  task: string
  targetEntity: string
  description: string
  version: string
  algorithm: string
  status: string
  role: string
  dataOrigin: string
  trainedAt: string | null
  deployedAt: string | null
  trainFrom: string | null
  trainTo: string | null
  validationFrom: string | null
  validationTo: string | null
  metrics: Record<string, unknown>
  baselineMetrics: Record<string, unknown>
  hyperparameters: Record<string, unknown>
  knownLimitations: string | null
  lastRunAt: string | null
  lastRunStatus: string | null
  lastRunRows: number | null
  runsLast7Days: number
  failedRunsLast7Days: number
}

/** `POST /admin/ml/models/{code}/score` (solo root): recálculo inmediato de un modelo. */
export interface MlRescore {
  model: string
  status: 'SUCCEEDED' | 'FAILED'
  rows: number
}

// ---- Almacenes ---------------------------------------------------------------------------------------------------

export interface AdminStoreRow {
  id: string
  businessName: string
  nit: string | null
  businessEmail: string | null
  state: string | null
  businessType: string | null
  createdAt: string
  planName: string | null
  applicationStatus: string | null
  trialEndsAt: string | null
  paidThrough: string | null
  branches: number
  products: number
  orders30d: number
  revenue30d: number
}

export interface AdminStoreDetail {
  store: AdminStoreRow
  ratingAverage: number | null
  ratingCount: number
  openQuestions: number
  publishedProducts: number
  stockouts: number
  branches: { id: string; name: string; municipality: string | null; active: boolean }[]
  team: { name: string; email: string | null; roles: string | null; state: string | null }[]
}

// ---- Cuentas -----------------------------------------------------------------------------------------------------

export type AccountTypeCode = 'CUSTOMER' | 'STORE_MANAGER' | 'STAFF' | 'ROOT'

export interface AdminAccountRow {
  id: string
  email: string
  name: string | null
  type: AccountTypeCode
  state: string
  createdAt: string
  lastLoginAt: string | null
  activeSessions: number
  orders: number
}

export interface AdminAccountDetail {
  account: AdminAccountRow
  phone: string | null
  twoFactor: boolean
  sessions: { id: string; ipAddress: string | null; userAgent: string | null; createdAt: string; expiresAt: string }[]
  logins: { attemptedAt: string; successful: boolean; ipAddress: string | null }[]
  stores: { storeId: string; storeName: string; roles: string | null }[]
  failedLogins7d: number
}

// ---- Pedidos y pagos ---------------------------------------------------------------------------------------------

export interface AdminOrderRow {
  id: string
  orderNumber: number
  placedAt: string
  status: string
  channel: string | null
  total: number
  customerEmail: string | null
  customerName: string | null
  stores: number
  paymentStatus: string | null
  paymentProvider: string | null
}

export interface AdminOrderDetail {
  order: AdminOrderRow
  subtotal: number
  discountTotal: number
  shippingTotal: number
  cancelledAt: string | null
  cancellationReason: string | null
  lines: { productId: string | null; name: string; sku: string | null; units: number; unitPrice: number; discount: number; storeName: string | null; branchName: string | null }[]
  sales: { id: string; storeName: string; branchName: string; status: string; total: number; fulfillmentType: string | null; confirmedAt: string | null; cancelledAt: string | null; cancellationReason: string | null }[]
  payments: { id: string; status: string; provider: string; amount: number; refunded: number; attempt: number; createdAt: string; paidAt: string | null; failureCode: string | null; riskDecision: RiskDecision | null; riskScore: number | null }[]
  history: { changedAt: string; fromStatus: string | null; toStatus: string; actorType: string | null; reasonCode: string | null }[]
}

export interface AdminPaymentRow {
  id: string
  createdAt: string
  status: string
  provider: string
  amount: number
  refunded: number
  attempt: number
  failureCode: string | null
  orderId: string
  orderNumber: number
  customerEmail: string | null
  riskDecision: RiskDecision | null
}

// ---- Equipo ------------------------------------------------------------------------------------------------------

export interface StaffPermission {
  code: PlatformPermission
  description: string
}

export interface StaffRole {
  id: string
  code: string
  name: string
  description: string | null
  system: boolean
  permissions: PlatformPermission[]
  members: number
}

export interface StaffMember {
  accountId: string
  email: string
  name: string | null
  roleId: string
  roleName: string
  active: boolean
  accountState: string
  createdAt: string
  lastLoginAt: string | null
}

export interface NewStaffMember {
  email: string
  name: string
  surname: string
  identification: string
  dateBirth: string
  numberPhone: string
  roleId: string
}

export interface StaffRoleInput {
  name: string
  description?: string
  permissions: PlatformPermission[]
}

export interface ListQuery {
  q?: string
  page: number
  size: number
}

export interface AdminConsoleService {
  me(): Promise<AdminMe>
  paymentRisk(query: RiskQuery): Promise<Page<RiskAssessment>>
  paymentRiskSummary(): Promise<RiskSummary>
  markRiskOutcome(id: number, outcome: RiskOutcome): Promise<void>
  models(): Promise<MlModel[]>
  rescoreModel(code: string): Promise<MlRescore>
  stores(query: ListQuery & { state?: string }): Promise<Page<AdminStoreRow>>
  store(storeId: string): Promise<AdminStoreDetail>
  accounts(query: ListQuery & { type?: string; state?: string }): Promise<Page<AdminAccountRow>>
  account(accountId: string): Promise<AdminAccountDetail>
  lockAccount(accountId: string): Promise<void>
  unlockAccount(accountId: string): Promise<void>
  revokeSessions(accountId: string): Promise<void>
  orders(query: ListQuery & { status?: string }): Promise<Page<AdminOrderRow>>
  order(orderId: string): Promise<AdminOrderDetail>
  payments(query: ListQuery & { status?: string; provider?: string }): Promise<Page<AdminPaymentRow>>
  staffMembers(): Promise<StaffMember[]>
  addStaffMember(input: NewStaffMember): Promise<StaffMember>
  updateStaffMember(accountId: string, change: { roleId?: string; active?: boolean }): Promise<StaffMember>
  removeStaffMember(accountId: string): Promise<void>
  staffRoles(): Promise<StaffRole[]>
  createStaffRole(input: StaffRoleInput): Promise<StaffRole>
  updateStaffRole(roleId: string, input: StaffRoleInput): Promise<StaffRole>
  deleteStaffRole(roleId: string): Promise<void>
  staffPermissions(): Promise<StaffPermission[]>
}
