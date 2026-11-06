import { ApiError } from '@/lib/api'
import type { AlertService } from '../alert-service'
import { ALERT_TYPES, type AlertPreference, type AlertType, type StoreAlert } from '../alert-types'
import { datasetFor } from './mock-analytics-data'
import { getStore, newId, requirePermission, simulateNetwork, type MockStore } from './mock-state'

const HOUR = 3_600_000
const ago = (hours: number) => new Date(Date.now() - hours * HOUR).toISOString()

interface AlertState {
  alerts: StoreAlert[]
  preferences: AlertPreference[]
}

const states = new Map<string, AlertState>()

const DEFAULT_PREFERENCES: AlertPreference[] = [
  { type: 'SALES_DROP', inApp: true, email: true, threshold: { ratio: 0.7 } },
  { type: 'STOCKOUT_SOON', inApp: true, email: true, threshold: { days: 7 } },
  { type: 'STOCKOUT', inApp: true, email: true, threshold: null },
  { type: 'CUSTOMER_AT_RISK', inApp: true, email: false, threshold: null },
  { type: 'DEAD_STOCK', inApp: true, email: false, threshold: { days: 90 } },
]

function seedAlerts(store: MockStore): StoreAlert[] {
  const [first, second, third] = store.products
  const people = datasetFor(store).people
  const customer = people[2]
  const base = { readAt: null, resolvedAt: null }
  const alerts: StoreAlert[] = [
    {
      ...base,
      id: newId('alert'),
      type: 'SALES_DROP',
      severity: 'WARNING',
      title: 'Caída de ventas',
      message: 'Las ventas de los últimos 7 días están por debajo de tu promedio semanal.',
      payload: { lastSevenDaysRevenue: 1_240_000, weeklyAverageRevenue: 2_100_000, ratio: 0.59 },
      createdAt: ago(3),
    },
  ]
  if (first) {
    alerts.push({
      ...base,
      id: newId('alert'),
      type: 'STOCKOUT_SOON',
      severity: 'WARNING',
      title: 'Agotamiento próximo',
      message: `${first.name} se agotaría en unos 4 días al ritmo de ventas actual.`,
      payload: { productId: first.id, name: first.name, abcClass: 'A', stock: 6, coverageDays: 4 },
      createdAt: ago(6),
    })
  }
  if (second) {
    alerts.push({
      ...base,
      id: newId('alert'),
      type: 'STOCKOUT',
      severity: 'CRITICAL',
      title: 'Producto agotado',
      message: `${second.name} se quedó sin existencias y vendió unidades en los últimos 30 días.`,
      payload: { productId: second.id, name: second.name, unitsLast30Days: 18 },
      createdAt: ago(9),
    })
  }
  if (customer) {
    alerts.push({
      ...base,
      id: newId('alert'),
      type: 'CUSTOMER_AT_RISK',
      severity: 'INFO',
      title: 'Cliente frecuente en riesgo',
      message: `${customer.name} compraba seguido y lleva más de 60 días sin pedir.`,
      payload: { personId: customer.personId, previousSegment: 'FREQUENT', segment: 'AT_RISK' },
      createdAt: ago(27),
    })
  }
  if (third) {
    alerts.push({
      ...base,
      id: newId('alert'),
      type: 'DEAD_STOCK',
      severity: 'INFO',
      title: 'Inventario sin movimiento',
      message: 'Tienes productos con existencias que no se venden hace más de 90 días.',
      payload: { count: 2, days: 90, products: [{ productId: third.id, name: third.name, stock: third.quantity }, ...(first ? [{ productId: first.id, name: first.name, stock: first.quantity }] : [])] },
      createdAt: ago(50),
      readAt: ago(40),
    })
  }
  return alerts
}

function stateFor(store: MockStore): AlertState {
  const id = store.summary.storeId
  let state = states.get(id)
  if (!state) {
    state = { alerts: seedAlerts(store), preferences: structuredClone(DEFAULT_PREFERENCES) }
    states.set(id, state)
  }
  return state
}

const isOpen = (alert: StoreAlert) => alert.resolvedAt === null

function validatePreference(preference: AlertPreference): void {
  if (!ALERT_TYPES.includes(preference.type)) throw new ApiError('Tipo de alerta desconocido.', { status: 400, code: 'INVALID_ALERT_TYPE' })
  const t = preference.threshold
  const bad = (message: string) => new ApiError(message, { status: 400, code: 'INVALID_ALERT_THRESHOLD' })
  if (preference.type === 'SALES_DROP' && !(t && typeof t.ratio === 'number' && t.ratio >= 0.1 && t.ratio <= 0.95)) throw bad('El umbral de caída de ventas debe estar entre 10 % y 95 %.')
  if (preference.type === 'STOCKOUT_SOON' && !(t && Number.isInteger(t.days) && (t.days ?? 0) >= 1 && (t.days ?? 0) <= 30)) throw bad('Los días de cobertura deben estar entre 1 y 30.')
  if (preference.type === 'DEAD_STOCK' && !(t && Number.isInteger(t.days) && (t.days ?? 0) >= 30 && (t.days ?? 0) <= 365)) throw bad('Los días sin movimiento deben estar entre 30 y 365.')
}

export function createMockAlertService(): AlertService {
  return {
    async list(storeId, status) {
      await simulateNetwork(300)
      const store = getStore(storeId)
      requirePermission(store, 'STORE_READ')
      const alerts = stateFor(store).alerts
      const selected = status === 'all' ? alerts : status === 'unread' ? alerts.filter((alert) => isOpen(alert) && alert.readAt === null) : alerts.filter(isOpen)
      return selected.toSorted((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
    },
    async summary(storeId) {
      await simulateNetwork(150)
      const store = getStore(storeId)
      requirePermission(store, 'STORE_READ')
      const unread = stateFor(store).alerts.filter((alert) => isOpen(alert) && alert.readAt === null)
      return { unread: unread.length, critical: unread.filter((alert) => alert.severity === 'CRITICAL').length }
    },
    async update(storeId, alertId, patch) {
      await simulateNetwork(250)
      const store = getStore(storeId)
      requirePermission(store, 'STORE_READ')
      const alert = stateFor(store).alerts.find((item) => item.id === alertId)
      if (!alert) throw new ApiError('No encontramos esa alerta.', { status: 404, code: 'ALERT_NOT_FOUND' })
      const now = new Date().toISOString()
      if (patch.resolved) {
        alert.resolvedAt ??= now
        alert.readAt ??= now
      } else if (patch.read) alert.readAt ??= now
      return { ...alert }
    },
    async preferences(storeId) {
      await simulateNetwork(300)
      const store = getStore(storeId)
      requirePermission(store, 'ALERT_MANAGE')
      return structuredClone(stateFor(store).preferences)
    },
    async savePreferences(storeId, preferences) {
      await simulateNetwork(400)
      const store = getStore(storeId)
      requirePermission(store, 'ALERT_MANAGE')
      preferences.forEach(validatePreference)
      const state = stateFor(store)
      const byType = new Map<AlertType, AlertPreference>(preferences.map((preference) => [preference.type, preference]))
      state.preferences = state.preferences.map((current) => byType.get(current.type) ?? current)
      return structuredClone(state.preferences)
    },
  }
}
