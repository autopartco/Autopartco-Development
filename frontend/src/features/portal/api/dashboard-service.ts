import { DATA_SOURCE } from '@/lib/api'
import type { Dashboard } from './types'

export interface DashboardService {
  /** `GET /stores/{id}/dashboard`. */
  dashboard(storeId: string): Promise<Dashboard>
}

let servicePromise: Promise<DashboardService> | undefined

export function getDashboardService(): Promise<DashboardService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-dashboard-service').then((module) => module.createHttpDashboardService())
      : import('./mock/mock-dashboard-service').then((module) => module.createMockDashboardService())
  return servicePromise
}
