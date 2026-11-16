import { humanizeCode } from '@/features/portal/lib/labels'

export type Tone = 'neutral' | 'success' | 'warning' | 'danger' | 'info'

interface LabelTone {
  label: string
  tone: Tone
}

const STATE: Record<string, LabelTone> = {
  ACTIVE: { label: 'Activa', tone: 'success' },
  INACTIVE: { label: 'Desactivada', tone: 'neutral' },
  LOCKED: { label: 'Bloqueada por intentos', tone: 'danger' },
  PENDING: { label: 'Pendiente', tone: 'warning' },
  AWAITING_PAYMENT: { label: 'Esperando pago', tone: 'warning' },
  CONFIRMED: { label: 'Confirmado', tone: 'info' },
  PREPARING: { label: 'Preparando', tone: 'info' },
  SHIPPED: { label: 'Enviado', tone: 'info' },
  DELIVERED: { label: 'Entregado', tone: 'success' },
  CANCELLED: { label: 'Cancelado', tone: 'neutral' },
  REJECTED: { label: 'Rechazado', tone: 'danger' },
}

/** Estado de cuenta, almacén, pedido o venta (`states.code`). */
export function stateLabel(code: string | null | undefined): LabelTone {
  if (!code) return { label: 'Sin estado', tone: 'neutral' }
  return STATE[code] ?? { label: humanizeCode(code), tone: 'neutral' }
}

const PAYMENT: Record<string, LabelTone> = {
  APPROVED: { label: 'Aprobado', tone: 'success' },
  DECLINED: { label: 'Rechazado', tone: 'danger' },
  PENDING: { label: 'Pendiente', tone: 'warning' },
  VOIDED: { label: 'Anulado', tone: 'neutral' },
  ERROR: { label: 'Error', tone: 'danger' },
  REFUNDED: { label: 'Reembolsado', tone: 'neutral' },
}

export function paymentStatusLabel(code: string | null | undefined): LabelTone {
  if (!code) return { label: 'Sin pago', tone: 'neutral' }
  return PAYMENT[code] ?? { label: humanizeCode(code), tone: 'neutral' }
}

export const ACCOUNT_TYPE_LABEL: Record<string, string> = {
  CUSTOMER: 'Cliente',
  STORE_MANAGER: 'Almacén',
  STAFF: 'Equipo',
  ROOT: 'Root',
}

export const DECISION: Record<string, LabelTone> = {
  ALLOW: { label: 'Permitido', tone: 'success' },
  REVIEW: { label: 'Para revisar', tone: 'warning' },
  DENY: { label: 'Bloqueado', tone: 'danger' },
}

export const OUTCOME: Record<string, LabelTone> = {
  LEGIT: { label: 'Legítimo', tone: 'success' },
  FRAUD_CONFIRMED: { label: 'Fraude confirmado', tone: 'danger' },
  CHARGEBACK: { label: 'Contracargo', tone: 'danger' },
  FRIENDLY_FRAUD: { label: 'Fraude amistoso', tone: 'danger' },
  UNKNOWN: { label: 'Sin determinar', tone: 'neutral' },
}

export const PROVIDER_LABEL: Record<string, string> = {
  WOMPI: 'Wompi',
  SIMULATED: 'Simulado',
}

export const STAGE_LABEL: Record<string, string> = {
  PRE_AUTHORIZATION: 'Antes de autorizar',
  POST_AUTHORIZATION: 'Después de autorizar',
  MANUAL_REVIEW: 'Revisión manual',
}

/** Nombre legible de un permiso de plataforma (la descripción viene del servidor; esto es el respaldo corto). */
export const PERMISSION_LABEL: Record<string, string> = {
  PLATFORM_DASHBOARD_READ: 'Resumen',
  PLATFORM_APPLICATIONS_REVIEW: 'Solicitudes',
  PLATFORM_STORES_READ: 'Almacenes',
  PLATFORM_ACCOUNTS_READ: 'Cuentas (ver)',
  PLATFORM_ACCOUNTS_MANAGE: 'Cuentas (gestionar)',
  PLATFORM_ORDERS_READ: 'Pedidos y pagos',
  PLATFORM_PAYMENT_RISK_REVIEW: 'Pagos atípicos',
  PLATFORM_CATALOG_MANAGE: 'Catálogo',
  PLATFORM_PLANS_MANAGE: 'Planes',
  PLATFORM_PROMOTIONS_MANAGE: 'Promociones',
  PLATFORM_ADS_MANAGE: 'Publicidad',
  PLATFORM_MODERATION: 'Moderación',
  PLATFORM_ANALYTICS_READ: 'Analítica y modelos',
  PLATFORM_AUDIT_READ: 'Auditoría',
}
