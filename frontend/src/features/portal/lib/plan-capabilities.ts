import type { Plan } from '../api/types'
import { capabilityLabel } from './labels'

/** Escalera comercial en orden; los códigos son los de `seller_plans`. */
export const PLAN_LADDER = ['TRY', 'BASIC', 'BUSINESS', 'PRO', 'ENTERPRISE'] as const

/** Nombre comercial por código (el servidor manda cuando conoce el plan). */
export const PLAN_NAME: Record<string, string> = {
  TRY: 'Prueba',
  BASIC: 'Esencial',
  BUSINESS: 'Negocio',
  PRO: 'Pro',
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
 * Catálogo de capacidades (plan comercial 2026-10-17). Solo `PROMOTIONS` y `CUSTOM_ROLES` funcionan hoy;
 * el resto se muestra como «Próximamente» y no se presenta como beneficio contratado.
 */
export const CAPABILITY_CATALOG: Record<string, CapabilityInfo> = Object.fromEntries(
  [
    entry('BASIC_ANALYTICS', 'Resumen de ventas', 'Panel con tus ventas y pedidos.', 'TRY'),
    entry('CSV_IMPORT', 'Importación de productos por CSV', 'Sube tu catálogo desde una hoja de cálculo.', 'BASIC'),
    entry('PROMOTIONS', 'Promociones', 'Descuentos y ofertas para tus productos.', 'BUSINESS', 'AVAILABLE'),
    entry('CUSTOM_ROLES', 'Roles y equipo personalizados', 'Define qué puede hacer cada persona de tu equipo.', 'BUSINESS', 'AVAILABLE'),
    entry('ADVANCED_REPORTS', 'Reportes avanzados', 'Informes detallados de ventas e inventario.', 'BUSINESS'),
    entry('WHATSAPP_NOTIFICATIONS', 'Avisos por WhatsApp', 'Notificaciones de pedidos a tu WhatsApp.', 'BUSINESS'),
    entry('ERP_IMPORT', 'Importar desde Alegra o Siigo', 'Trae productos y existencias de tu ERP cuando lo necesites.', 'BUSINESS'),
    entry('ERP_SYNC', 'Sincronización con Alegra y Siigo', 'Inventario y precios al día de forma automática.', 'PRO'),
    entry('E_INVOICING', 'Factura electrónica', 'Emite la factura de cada pedido a través de tu ERP.', 'PRO'),
    entry('PRICE_LISTS', 'Listas de precios por cliente', 'Precios distintos según el cliente.', 'PRO'),
    entry('PURCHASING', 'Compras a proveedores', 'Registra y gestiona tus compras.', 'PRO'),
    entry('STOCK_ALERTS', 'Alertas de stock', 'Aviso cuando un producto se agota.', 'PRO'),
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

export function planCapabilities(plan: Plan): PlanCapabilities {
  const infos = plan.capabilities.map(capabilityInfo)
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

/**
 * Empresarial sintético: solo para mostrar tarjeta y columna. No existe en la BD, nunca se contrata
 * (no hay checkout) y no tiene ningún efecto en permisos ni cupos reales.
 */
const SYNTHETIC_ENTERPRISE: Plan = {
  id: 'synthetic-enterprise',
  code: 'ENTERPRISE',
  name: 'Empresarial',
  description: 'Sin límites, con integraciones y acompañamiento.',
  monthlyPriceCop: 0,
  currency: 'COP',
  features: [],
  recommended: false,
  enabled: false,
  maxBranches: null,
  maxProducts: null,
  maxUsers: null,
  capabilities: Object.values(CAPABILITY_CATALOG).map((info) => info.key),
}

/** Ordena los planes de la API y añade Empresarial (informativo) si el servidor no lo devuelve. */
export function plansForDisplay(plans: Plan[]): Plan[] {
  return sortPlans(plans.some((plan) => plan.code === 'ENTERPRISE') ? plans : [...plans, SYNTHETIC_ENTERPRISE])
}

/** Anual = 10 × mensual («2 meses gratis»). */
export function annualPriceCop(plan: Plan): number {
  return plan.monthlyPriceCop * 10
}

export function annualSavingsCop(plan: Plan): number {
  return plan.monthlyPriceCop * 2
}

/** Empresarial se cotiza a medida. */
export function isCustomPlan(plan: Plan): boolean {
  return plan.code === 'ENTERPRISE'
}
