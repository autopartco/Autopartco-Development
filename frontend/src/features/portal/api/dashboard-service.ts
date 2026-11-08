import { DATA_SOURCE } from '@/lib/api'
import type { Dashboard } from './types'

export interface DashboardService {
  /**
   * `GET /stores/{id}/dashboard?branchId=`. Con `branchId`, ventas e inventario son solo de esa sucursal;
   * plan, uso y suscripción siguen siendo del almacén. 404 `BRANCH_NOT_FOUND` si no es del almacén.
   */
  dashboard(storeId: string, branchId?: string): Promise<Dashboard>
}

let servicePromise: Promise<DashboardService> | undefined

export function getDashboardService(): Promise<DashboardService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-dashboard-service').then((module) => module.createHttpDashboardService())
      : import('./mock/mock-dashboard-service').then((module) => module.createMockDashboardService())
  return servicePromise
}
