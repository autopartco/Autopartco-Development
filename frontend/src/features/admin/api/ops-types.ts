/**
 * Contratos de la consola ROOT (`/api/v1/admin/**`, solo `ROLE_ROOT`):
 * `PlanAdminController` y `CatalogAdminController` (las promociones van por `admin-promotion-service`).
 * La interfaz solo muestra; el servidor valida y autoriza cada petición.
 */

/** `core.billing.PlanResponse`. */
export interface AdminPlan {
  id: string
  code: string
  name: string
  description: string | null
  monthlyPriceCop: number
  currency: string
  features: string[]
  recommended: boolean
  enabled: boolean
  /** `null` = ilimitado. */
  maxBranches: number | null
  maxProducts: number | null
  maxUsers: number | null
  capabilities: string[]
}

/** `core.billing.PlanRequest` (POST crea, PUT reemplaza). */
export type PlanInput = Omit<AdminPlan, 'id'>

/** `core.catalog.CategoryResponse`. */
export interface AdminCategory {
  id: string
  code: string
}

/** `core.catalog.ManufacturerResponse`. `suggestedByStoreId` = lo propuso un almacén. */
export interface AdminManufacturer {
  id: string
  name: string
  approved: boolean
  suggestedByStoreId: string | null
}

export interface AdminOpsService {
  readonly simulated: boolean
  plans(): Promise<AdminPlan[]>
  createPlan(input: PlanInput): Promise<AdminPlan>
  updatePlan(id: string, input: PlanInput): Promise<AdminPlan>
  /** `DELETE /admin/plans/{id}`: lo deshabilita (no lo borra). */
  disablePlan(id: string): Promise<void>

  categories(): Promise<AdminCategory[]>
  createCategory(code: string): Promise<AdminCategory>
  updateCategory(id: string, code: string): Promise<AdminCategory>
  deactivateCategory(id: string): Promise<void>

  manufacturers(): Promise<AdminManufacturer[]>
  createManufacturer(name: string): Promise<AdminManufacturer>
  updateManufacturer(id: string, change: { name?: string; approved?: true }): Promise<AdminManufacturer>
  deactivateManufacturer(id: string): Promise<void>
}
