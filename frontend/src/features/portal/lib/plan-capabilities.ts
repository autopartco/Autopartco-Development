import type { Plan } from '../api/types'
import { capabilityLabel } from './labels'

/** Escalera comercial en orden; los códigos son los de `seller_plans`. */
export const PLAN_LADDER = ['TRY', 'ENTERPRISE'] as const

/** Nombre comercial por código (el servidor manda cuando conoce el plan). */
export const PLAN_NAME: Record<string, string> = {
  TRY: 'Prueba gratuita',
  ENTERPRISE: 'Empresarial',
}

export type CapabilityStatus = 'AVAILABLE' | 'COMING_SOON'

export interface CapabilityInfo {
  key: string
  label: string
  description: string
  /** Código del plan mínimo según la escalera comercial. */
  minPlanCode: string
  status: CapabilityStatus
}

function entry(key: string, label: string, description: string, minPlanCode: string, status: CapabilityStatus = 'COMING_SOON'): CapabilityInfo {
  return { key, label, description, minPlanCode, status }
}

/**
 * Catálogo de capacidades (planes TRY y ENTERPRISE, 2026-11-05). Las marcadas `AVAILABLE` funcionan hoy;
 * el resto se muestra como «Próximamente» y no se presenta como beneficio contratado.
 * Las de `minPlanCode: 'TRY'` están en todos los planes.
 */
export const CAPABILITY_CATALOG: Record<string, CapabilityInfo> = Object.fromEntries(
  [
    entry('BASIC_ANALYTICS', 'Resumen de ventas', 'Panel con tus ventas y pedidos.', 'TRY', 'AVAILABLE'),
    entry('CSV_IMPORT', 'Importación y exportación CSV de inventario', 'Descarga tu inventario y actualiza existencias y precios desde una hoja de cálculo.', 'TRY', 'AVAILABLE'),
    entry('STOCK_ALERTS', 'Alertas de stock bajo', 'Ve qué productos están por agotarse en cada sucursal.', 'TRY', 'AVAILABLE'),
    entry('PROMOTIONS', 'Promociones', 'Descuentos y ofertas para tus productos.', 'ENTERPRISE', 'AVAILABLE'),
    entry('CUSTOM_ROLES', 'Roles y equipo personalizados', 'Define qué puede hacer cada persona de tu equipo.', 'ENTERPRISE', 'AVAILABLE'),
    entry('ADVANCED_REPORTS', 'Inteligencia comercial', 'Reportes de ventas, segmentos de clientes, clasificación de productos y proyección de ventas.', 'ENTERPRISE', 'AVAILABLE'),
    entry('WHATSAPP_NOTIFICATIONS', 'Avisos por WhatsApp', 'Notificaciones de pedidos a tu WhatsApp.', 'ENTERPRISE'),
    entry('ERP_IMPORT', 'Importar desde Alegra o Siigo', 'Trae productos y existencias de tu ERP cuando lo necesites.', 'ENTERPRISE'),
    entry('ERP_SYNC', 'Sincronización con Alegra y Siigo', 'Inventario y precios al día de forma automática.', 'ENTERPRISE'),
    entry('E_INVOICING', 'Factura electrónica', 'Emite la factura de cada pedido a través de tu ERP.', 'ENTERPRISE'),
    entry('PRICE_LISTS', 'Listas de precios por cliente', 'Precios distintos según el cliente.', 'ENTERPRISE'),
    entry('PURCHASING', 'Compras a proveedores', 'Registra y gestiona tus compras.', 'ENTERPRISE'),
    entry('API_ACCESS', 'Acceso por API', 'Conecta tus propios sistemas.', 'ENTERPRISE'),
    entry('WEBHOOKS', 'Webhooks', 'Recibe eventos de tu almacén en tus sistemas.', 'ENTERPRISE'),
    entry('CUSTOM_DOMAIN', 'Dominio propio', 'Tu tienda en tu propia dirección web.', 'ENTERPRISE'),
  ].map((info) => [info.key, info]),
)

/** Clave desconocida: se muestra tal cual, sin plan mínimo y sin prometerla. */
export function capabilityInfo(key: string): CapabilityInfo {
  return CAPABILITY_CATALOG[key] ?? { key, label: capabilityLabel(key), description: '', minPlanCode: '', status: 'COMING_SOON' }
}

export function planDisplayName(code: string, plans?: Plan[]): string {
  return plans?.find((plan) => plan.code === code)?.name ?? PLAN_NAME[code] ?? code
}

export interface PlanCapabilities {
  /** Funcionan hoy y el plan las incluye. */
  available: CapabilityInfo[]
  /** El plan las lista pero aún no existen: «En camino», no son beneficio contratado. */
  comingSoon: CapabilityInfo[]
}

/** Capacidades incluidas en todos los planes (plan mínimo TRY), las liste o no el servidor. */
const ALL_PLANS_KEYS = Object.values(CAPABILITY_CATALOG)
  .filter((info) => info.minPlanCode === 'TRY')
  .map((info) => info.key)

/** El plan incluye la capacidad: la lista el servidor o está en todos los planes. */
export function planIncludes(plan: Plan, key: string): boolean {
  return plan.capabilities.includes(key) || ALL_PLANS_KEYS.includes(key)
}

export function planCapabilities(plan: Plan): PlanCapabilities {
  const keys = [...new Set([...ALL_PLANS_KEYS, ...plan.capabilities])]
  const infos = keys.map(capabilityInfo)
  return {
    available: infos.filter((info) => info.status === 'AVAILABLE'),
    comingSoon: infos.filter((info) => info.status !== 'AVAILABLE'),
  }
}

export interface PlanLimits {
  branches: number | null
  products: number | null
  users: number | null
}

export function planLimits(plan: Plan): PlanLimits {
  return { branches: plan.maxBranches ?? null, products: plan.maxProducts ?? null, users: plan.maxUsers ?? null }
}

/** Orden de la escalera; los planes desconocidos van por precio después de los conocidos. */
export function sortPlans(plans: Plan[]): Plan[] {
  const rank = (plan: Plan) => {
    const index = PLAN_LADDER.indexOf(plan.code as (typeof PLAN_LADDER)[number])
    return index === -1 ? PLAN_LADDER.length : index
  }
  return plans.toSorted((a, b) => rank(a) - rank(b) || a.monthlyPriceCop - b.monthlyPriceCop)
}

/** Anual = 10 × mensual («2 meses gratis»). */
export function annualPriceCop(plan: Plan): number {
  return plan.monthlyPriceCop * 10
}

export function annualSavingsCop(plan: Plan): number {
  return plan.monthlyPriceCop * 2
}

/** Empresarial se contrata en línea y además ofrece «Hablar con ventas». */
export function offersSalesContact(plan: Plan): boolean {
  return plan.code === 'ENTERPRISE'
}
