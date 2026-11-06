import type { AlertPreference, AlertSummary, StoreAlert } from '../alert-types'
import type { AlertService } from '../alert-service'
import { portalRequest, storePath } from './portal-http'

export function createHttpAlertService(): AlertService {
  return {
    list: (storeId, status) => portalRequest<StoreAlert[]>(storePath(storeId, `/alerts?status=${status}`)),
    summary: (storeId) => portalRequest<AlertSummary>(storePath(storeId, '/alerts/summary')),
    update: (storeId, alertId, patch) =>
      portalRequest<StoreAlert>(storePath(storeId, `/alerts/${encodeURIComponent(alertId)}`), { method: 'PATCH', body: patch }),
    preferences: (storeId) => portalRequest<AlertPreference[]>(storePath(storeId, '/alert-preferences')),
    savePreferences: (storeId, preferences) =>
      portalRequest<AlertPreference[]>(storePath(storeId, '/alert-preferences'), { method: 'PUT', body: preferences }),
  }
}
