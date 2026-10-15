import type { DashboardService } from '../dashboard-service'
import type { Dashboard } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/dashboard/DashboardController`. */
export function createHttpDashboardService(): DashboardService {
  return {
    dashboard: (storeId) => portalRequest<Dashboard>(storePath(storeId, '/dashboard')),
  }
}
