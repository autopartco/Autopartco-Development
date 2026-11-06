/**
 * Alertas del almacén (fase 2). Contratos de `/stores/{id}/alerts` (permiso `STORE_READ`) y de
 * `/stores/{id}/alert-preferences` (permiso `ALERT_MANAGE`).
 */

export type AlertType = 'SALES_DROP' | 'STOCKOUT_SOON' | 'STOCKOUT' | 'CUSTOMER_AT_RISK' | 'DEAD_STOCK'
export type AlertSeverity = 'INFO' | 'WARNING' | 'CRITICAL'
export type AlertStatusFilter = 'open' | 'unread' | 'all'

export const ALERT_TYPES: AlertType[] = ['SALES_DROP', 'STOCKOUT_SOON', 'STOCKOUT', 'CUSTOMER_AT_RISK', 'DEAD_STOCK']

/** El `payload` depende del tipo; todos los campos se leen con tolerancia (el servidor puede añadir más). */
export interface StoreAlert {
  id: string
  type: AlertType
  severity: AlertSeverity
  title: string
  message: string
  payload: Record<string, unknown>
  createdAt: string
  readAt: string | null
  resolvedAt: string | null
}

export interface AlertSummary {
  unread: number
  critical: number
}

export interface AlertPatch {
  read?: true
  resolved?: true
}

/** Umbral por tipo: `SALES_DROP {ratio}`, `STOCKOUT_SOON {days}`, `DEAD_STOCK {days}`; el resto, `null`. */
export interface AlertPreference {
  type: AlertType
  inApp: boolean
  email: boolean
  threshold: Record<string, number> | null
}
