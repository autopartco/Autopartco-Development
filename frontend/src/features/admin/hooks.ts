import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getAdminApplicationService } from './api/admin-service'
import { getAdminOpsService } from './api/admin-ops-service'
import { getAdminPromotionService } from './api/admin-promotion-service'
import type { Page } from '@/features/portal/api/types'
import type { StoreApplicationStatus } from '@/features/seller/api/types'
import type { StoreApplication } from './api/types'

/** Prefijo de caché del servicio de administración. */
export const ADMIN_CACHE_PREFIX = 'admin:'

/** `enabled = false` (cuenta sin el permiso) no llama al servidor y devuelve una lista vacía. */
export function usePendingApplications(enabled = true) {
  return useAsync<StoreApplication[]>(`${ADMIN_CACHE_PREFIX}applications:pending${enabled ? '' : ':off'}`, async () =>
    enabled ? (await getAdminApplicationService()).pending() : [],
  )
}

/** Listado paginado de solicitudes (`status` vacío = todas). Misma familia de caché que la cola pendiente. */
export function useApplicationsList(status: StoreApplicationStatus | undefined, page: number, size: number) {
  return useAsync<Page<StoreApplication>>(`${ADMIN_CACHE_PREFIX}applications:list:${status ?? 'ALL'}:${page}:${size}`, async () =>
    (await getAdminApplicationService()).listApplications({ status, page, size }),
  )
}

/** Vuelve a pedir la lista (tras revisar una solicitud). */
export function reloadPendingApplications() {
  invalidateAsync(ADMIN_CACHE_PREFIX)
}

/** Consultas de la consola ROOT. Todas cuelgan de `ADMIN_CACHE_PREFIX` para recargar tras una mutación. */
export function useAdminPlans(enabled = true) {
  return useAsync(`${ADMIN_CACHE_PREFIX}plans${enabled ? '' : ':off'}`, async () => (enabled ? (await getAdminOpsService()).plans() : []))
}

/** Misma clave que `/admin/promociones`, así el resumen se actualiza tras crear o editar allí. */
export function useAdminPromotions(enabled = true) {
  return useAsync(`${ADMIN_CACHE_PREFIX}promotions${enabled ? '' : ':off'}`, async () => (enabled ? (await getAdminPromotionService()).list('') : []))
}

export function useAdminCategories() {
  return useAsync(`${ADMIN_CACHE_PREFIX}catalog:categories`, async () => (await getAdminOpsService()).categories())
}

export function useAdminManufacturers(enabled = true) {
  return useAsync(`${ADMIN_CACHE_PREFIX}catalog:manufacturers${enabled ? '' : ':off'}`, async () =>
    enabled ? (await getAdminOpsService()).manufacturers() : [],
  )
}

/** Recarga una parte de la consola (`plans`, `catalog:`) tras crear, editar o desactivar. */
export function reloadAdmin(part: 'plans' | 'catalog:') {
  invalidateAsync(`${ADMIN_CACHE_PREFIX}${part}`)
}
