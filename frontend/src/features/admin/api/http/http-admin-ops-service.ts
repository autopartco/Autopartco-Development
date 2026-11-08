import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { AdminCategory, AdminManufacturer, AdminOpsService, AdminPlan } from '../ops-types'

const id = (value: string) => encodeURIComponent(value)

/** `PlanAdminController` y `CatalogAdminController`. */
export function createHttpAdminOpsService(): AdminOpsService {
  return {
    simulated: false,
    plans: () => portalRequest<AdminPlan[]>('/admin/plans'),
    createPlan: (input) => portalRequest<AdminPlan>('/admin/plans', { method: 'POST', body: input }),
    updatePlan: (planId, input) => portalRequest<AdminPlan>(`/admin/plans/${id(planId)}`, { method: 'PUT', body: input }),
    disablePlan: (planId) => portalRequest<void>(`/admin/plans/${id(planId)}`, { method: 'DELETE' }),

    categories: () => portalRequest<AdminCategory[]>('/admin/categories'),
    createCategory: (code) => portalRequest<AdminCategory>('/admin/categories', { method: 'POST', body: { code } }),
    updateCategory: (categoryId, code) =>
      portalRequest<AdminCategory>(`/admin/categories/${id(categoryId)}`, { method: 'PATCH', body: { code } }),
    deactivateCategory: (categoryId) => portalRequest<void>(`/admin/categories/${id(categoryId)}`, { method: 'DELETE' }),

    manufacturers: () => portalRequest<AdminManufacturer[]>('/admin/manufacturers'),
    createManufacturer: (name) => portalRequest<AdminManufacturer>('/admin/manufacturers', { method: 'POST', body: { name } }),
    updateManufacturer: (manufacturerId, change) =>
      portalRequest<AdminManufacturer>(`/admin/manufacturers/${id(manufacturerId)}`, { method: 'PATCH', body: change }),
    deactivateManufacturer: (manufacturerId) => portalRequest<void>(`/admin/manufacturers/${id(manufacturerId)}`, { method: 'DELETE' }),
  }
}
