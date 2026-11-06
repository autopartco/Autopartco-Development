import { formatCOP, formatNumber } from '@/lib/format'
import type { AlertSeverity, AlertType, StoreAlert } from '../api/alert-types'
import type { StorePermission } from '../api/types'
import type { Tone } from './labels'

export const ALERT_TYPE_LABEL: Record<AlertType, string> = {
  SALES_DROP: 'Caída de ventas',
  STOCKOUT_SOON: 'Agotamiento próximo',
  STOCKOUT: 'Producto agotado',
  CUSTOMER_AT_RISK: 'Cliente frecuente en riesgo',
  DEAD_STOCK: 'Inventario sin movimiento',
}

export const ALERT_TYPE_DESCRIPTION: Record<AlertType, string> = {
  SALES_DROP: 'Te avisamos cuando las ventas de los últimos 7 días caen frente a tu promedio semanal.',
  STOCKOUT_SOON: 'Te avisamos cuando un producto se agotaría pronto al ritmo de ventas actual.',
  STOCKOUT: 'Te avisamos cuando un producto que se vendía se queda sin existencias.',
  CUSTOMER_AT_RISK: 'Te avisamos cuando un cliente frecuente deja de comprar.',
  DEAD_STOCK: 'Te avisamos cuando hay productos con existencias que llevan tiempo sin venderse.',
}

export const SEVERITY_LABEL: Record<AlertSeverity, { label: string; tone: Tone }> = {
  CRITICAL: { label: 'Crítica', tone: 'danger' },
  WARNING: { label: 'Atención', tone: 'warning' },
  INFO: { label: 'Informativa', tone: 'info' },
}

const text = (value: unknown): string | undefined => (typeof value === 'string' && value ? value : undefined)
const num = (value: unknown): number | undefined => (typeof value === 'number' && Number.isFinite(value) ? value : undefined)

/** Línea de detalle a partir del `payload`, leído con tolerancia: si falta un campo, solo se omite esa parte. */
export function alertDetail(alert: StoreAlert): string | undefined {
  const payload = alert.payload ?? {}
  switch (alert.type) {
    case 'SALES_DROP': {
      const last = num(payload.lastSevenDaysRevenue)
      const average = num(payload.weeklyAverageRevenue)
      const ratio = num(payload.ratio)
      if (last === undefined || average === undefined) return undefined
      const share = ratio !== undefined ? ` (${Math.round(ratio * 100)} % de lo habitual)` : ''
      return `Últimos 7 días: ${formatCOP(last)}. Promedio semanal: ${formatCOP(average)}${share}.`
    }
    case 'STOCKOUT_SOON': {
      const stock = num(payload.stock)
      const coverage = num(payload.coverageDays)
      const parts = [
        stock !== undefined ? `${formatNumber(stock)} ${stock === 1 ? 'unidad' : 'unidades'} en stock` : undefined,
        coverage !== undefined ? `alcanza para unos ${formatNumber(Math.round(coverage))} ${Math.round(coverage) === 1 ? 'día' : 'días'}` : undefined,
      ].filter(Boolean)
      return parts.length > 0 ? `${parts.join(', ')}.` : undefined
    }
    case 'STOCKOUT': {
      const units = num(payload.unitsLast30Days)
      return units !== undefined ? `Vendió ${formatNumber(units)} ${units === 1 ? 'unidad' : 'unidades'} en los últimos 30 días.` : undefined
    }
    case 'CUSTOMER_AT_RISK':
      return 'Pasó de cliente frecuente a cliente en riesgo.'
    case 'DEAD_STOCK': {
      const count = num(payload.count)
      const days = num(payload.days)
      if (count === undefined) return undefined
      return `${formatNumber(count)} ${count === 1 ? 'producto' : 'productos'} sin ventas${days !== undefined ? ` hace más de ${formatNumber(days)} días` : ''}.`
    }
  }
}

export interface AlertTarget {
  to: string
  label: string
}

/** Productos listados en una alerta de inventario sin movimiento. */
export function deadStockProducts(alert: StoreAlert): { productId: string; name: string; stock?: number }[] {
  const list = alert.payload?.products
  if (!Array.isArray(list)) return []
  return list.flatMap((item: unknown) => {
    if (!item || typeof item !== 'object') return []
    const { productId, name, stock } = item as Record<string, unknown>
    const id = text(productId)
    return id ? [{ productId: id, name: text(name) ?? 'Producto', stock: num(stock) }] : []
  })
}

/**
 * A dónde lleva la alerta (producto, clientes o ventas). Solo ofrece destinos a los que la persona tiene permiso;
 * el servidor sigue autorizando cada pantalla.
 */
export function alertTarget(alert: StoreAlert, storeId: string, can: (permission: StorePermission) => boolean): AlertTarget | undefined {
  const base = `/portal/${encodeURIComponent(storeId)}`
  const payload = alert.payload ?? {}
  switch (alert.type) {
    case 'STOCKOUT_SOON':
    case 'STOCKOUT': {
      const productId = text(payload.productId)
      return productId && can('PRODUCT_READ') ? { to: `${base}/productos/${encodeURIComponent(productId)}`, label: 'Ver el producto' } : undefined
    }
    case 'CUSTOMER_AT_RISK':
      return can('ANALYTICS_READ') ? { to: `${base}/inteligencia/clientes?segmento=${text(payload.segment) ?? 'AT_RISK'}`, label: 'Ver clientes en riesgo' } : undefined
    case 'DEAD_STOCK':
      return can('PRODUCT_READ') ? { to: `${base}/inventario`, label: 'Ver inventario' } : undefined
    case 'SALES_DROP':
      return can('ORDER_READ') ? { to: `${base}/ventas`, label: 'Ver ventas' } : undefined
  }
}
