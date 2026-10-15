import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getAdminApplicationService } from './api/admin-service'
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
