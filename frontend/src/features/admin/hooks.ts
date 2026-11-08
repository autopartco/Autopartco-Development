import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getAdminApplicationService } from './api/admin-service'
import { getAdminOpsService } from './api/admin-ops-service'
import { getAdminPromotionService } from './api/admin-promotion-service'
import type { StoreApplication } from './api/types'

/** Prefijo de caché del servicio de administración. */
export const ADMIN_CACHE_PREFIX = 'admin:'

export function usePendingApplications() {
  return useAsync<StoreApplication[]>(`${ADMIN_CACHE_PREFIX}applications:pending`, async () =>
    (await getAdminApplicationService()).pending(),
  )
}

/** Vuelve a pedir la lista (tras revisar una solicitud). */
export function reloadPendingApplications() {
  invalidateAsync(ADMIN_CACHE_PREFIX)
}

/** Consultas de la consola ROOT. Todas cuelgan de `ADMIN_CACHE_PREFIX` para recargar tras una mutación. */
export function useAdminPlans() {
  return useAsync(`${ADMIN_CACHE_PREFIX}plans`, async () => (await getAdminOpsService()).plans())
}

/** Misma clave que `/admin/promociones`, así el resumen se actualiza tras crear o editar allí. */
export function useAdminPromotions() {
  return useAsync(`${ADMIN_CACHE_PREFIX}promotions`, async () => (await getAdminPromotionService()).list(''))
}

export function useAdminCategories() {
  return useAsync(`${ADMIN_CACHE_PREFIX}catalog:categories`, async () => (await getAdminOpsService()).categories())
}

export function useAdminManufacturers() {
  return useAsync(`${ADMIN_CACHE_PREFIX}catalog:manufacturers`, async () => (await getAdminOpsService()).manufacturers())
}

/** Recarga una parte de la consola (`plans`, `catalog:`) tras crear, editar o desactivar. */
export function reloadAdmin(part: 'plans' | 'catalog:') {
  invalidateAsync(`${ADMIN_CACHE_PREFIX}${part}`)
}
