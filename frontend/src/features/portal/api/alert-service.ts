import { DATA_SOURCE } from '@/lib/api'
import type { AlertPatch, AlertPreference, AlertStatusFilter, AlertSummary, StoreAlert } from './alert-types'

/** Alertas del almacén y sus preferencias. */
export interface AlertService {
  /** `GET /alerts?status=open|unread|all`. */
  list(storeId: string, status: AlertStatusFilter): Promise<StoreAlert[]>
  /** `GET /alerts/summary`. */
  summary(storeId: string): Promise<AlertSummary>
  /** `PATCH /alerts/{id}` con `{read:true}` o `{resolved:true}`; devuelve la alerta actualizada. */
  update(storeId: string, alertId: string, patch: AlertPatch): Promise<StoreAlert>
  /** `GET /alert-preferences` (`ALERT_MANAGE`): una por tipo. */
  preferences(storeId: string): Promise<AlertPreference[]>
  /** `PUT /alert-preferences` con la lista completa. 400 `INVALID_ALERT_THRESHOLD` / `INVALID_ALERT_TYPE`. */
  savePreferences(storeId: string, preferences: AlertPreference[]): Promise<AlertPreference[]>
}

let servicePromise: Promise<AlertService> | undefined

export function getAlertService(): Promise<AlertService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-alert-service').then((module) => module.createHttpAlertService())
      : import('./mock/mock-alert-service').then((module) => module.createMockAlertService())
  return servicePromise
}
