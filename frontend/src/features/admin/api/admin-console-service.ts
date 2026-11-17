import { DATA_SOURCE } from '@/lib/api'
import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { Page } from '@/features/portal/api/types'
import type { AdminConsoleService } from './console-types'

const id = (value: string) => encodeURIComponent(value)

function query(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams()
  for (const [name, value] of Object.entries(params)) if (value !== undefined && value !== '') search.set(name, String(value))
  const text = search.toString()
  return text ? `?${text}` : ''
}

/** Controladores `AdminMeController`, `AnomaliesAdminController`, `SupportAdminController`, `OrdersAdminController` y `StaffAdminController`. */
function createHttpService(): AdminConsoleService {
  return {
    me: () => portalRequest('/admin/me'),
    paymentRisk: ({ decision, status, q, page, size }) => portalRequest(`/admin/payment-risk${query({ decision, status, q, page, size })}`),
    paymentRiskSummary: () => portalRequest('/admin/payment-risk/summary'),
    markRiskOutcome: (assessmentId, outcome) => portalRequest(`/admin/payment-risk/${assessmentId}/outcome`, { method: 'POST', body: { outcome } }),
    models: () => portalRequest('/admin/ml/models'),
    rescoreModel: (code) => portalRequest(`/admin/ml/models/${id(code)}/score`, { method: 'POST' }),
    stores: ({ q, state, page, size }) => portalRequest(`/admin/stores${query({ q, state, page, size })}`),
    store: (storeId) => portalRequest(`/admin/stores/${id(storeId)}`),
    accounts: ({ q, type, state, page, size }) => portalRequest(`/admin/accounts${query({ q, type, state, page, size })}`),
    account: (accountId) => portalRequest(`/admin/accounts/${id(accountId)}`),
    lockAccount: (accountId) => portalRequest(`/admin/accounts/${id(accountId)}/lock`, { method: 'POST' }),
    unlockAccount: (accountId) => portalRequest(`/admin/accounts/${id(accountId)}/unlock`, { method: 'POST' }),
    revokeSessions: (accountId) => portalRequest(`/admin/accounts/${id(accountId)}/revoke-sessions`, { method: 'POST' }),
    orders: ({ q, status, page, size }) => portalRequest(`/admin/orders${query({ q, status, page, size })}`),
    order: (orderId) => portalRequest(`/admin/orders/${id(orderId)}`),
    payments: ({ q, status, provider, page, size }) => portalRequest(`/admin/payments${query({ q, status, provider, page, size })}`),
    staffMembers: () => portalRequest('/admin/staff'),
    addStaffMember: (input) => portalRequest('/admin/staff', { method: 'POST', body: input }),
    updateStaffMember: (accountId, change) => portalRequest(`/admin/staff/${id(accountId)}`, { method: 'PATCH', body: change }),
    removeStaffMember: (accountId) => portalRequest(`/admin/staff/${id(accountId)}`, { method: 'DELETE' }),
    staffRoles: () => portalRequest('/admin/staff/roles'),
    createStaffRole: (input) => portalRequest('/admin/staff/roles', { method: 'POST', body: input }),
    updateStaffRole: (roleId, input) => portalRequest(`/admin/staff/roles/${id(roleId)}`, { method: 'PUT', body: input }),
    deleteStaffRole: (roleId) => portalRequest(`/admin/staff/roles/${id(roleId)}`, { method: 'DELETE' }),
    staffPermissions: () => portalRequest('/admin/staff/permissions'),
  }
}

const emptyPage = <T,>(page: number, size: number): Page<T> => ({ items: [], page, size, total: 0, totalPages: 0 })
const unavailable = () => Promise.reject(new Error('Disponible solo con el servidor real.'))

/**
 * Modo mock: estas secciones leen datos reales de la plataforma; sin servidor no se inventan cifras, se muestran
 * los estados vacíos.
 */
function createMockService(): AdminConsoleService {
  return {
    me: async () => ({ email: 'root@autopartco.test', root: true, roleName: 'Root', permissions: [] }),
    paymentRisk: async ({ page, size }) => emptyPage(page, size),
    paymentRiskSummary: async () => ({ days: 30, total: 0, review: 0, pendingReview: 0, legit: 0, fraudConfirmed: 0, averageScore: null, reviewAmount: 0 }),
    markRiskOutcome: unavailable,
    models: async () => [],
    rescoreModel: unavailable,
    stores: async ({ page, size }) => emptyPage(page, size),
    store: unavailable,
    accounts: async ({ page, size }) => emptyPage(page, size),
    account: unavailable,
    lockAccount: unavailable,
    unlockAccount: unavailable,
    revokeSessions: unavailable,
    orders: async ({ page, size }) => emptyPage(page, size),
    order: unavailable,
    payments: async ({ page, size }) => emptyPage(page, size),
    staffMembers: async () => [],
    addStaffMember: unavailable,
    updateStaffMember: unavailable,
    removeStaffMember: unavailable,
    staffRoles: async () => [],
    createStaffRole: unavailable,
    updateStaffRole: unavailable,
    deleteStaffRole: unavailable,
    staffPermissions: async () => [],
  }
}

let service: AdminConsoleService | undefined

export function getAdminConsoleService(): AdminConsoleService {
  service ??= DATA_SOURCE === 'http' ? createHttpService() : createMockService()
  return service
}
