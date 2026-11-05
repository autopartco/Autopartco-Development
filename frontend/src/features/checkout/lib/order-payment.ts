import { ApiError } from '@/lib/api'
import type { CartLine, OrderPaymentDto } from '../api/types'

/**
 * Pedido enviado a Wompi. Se guarda en `sessionStorage` antes de redirigir para mostrar el número
 * del pedido al volver y, si el pago vence, devolver los repuestos al carrito. No es fuente de
 * verdad: el estado del pago siempre lo da `GET /me/orders/{orderId}/payment`.
 */
export interface PendingOrderPayment {
  orderId: string
  orderNumber: number
  reference: string
  /** Instantánea del carrito al pagar (solo para restaurarlo; el servidor recalcula todo). */
  lines: CartLine[]
}

const key = (orderId: string) => `autopartco.order-payment.${orderId}`

export function savePendingOrderPayment(pending: PendingOrderPayment): void {
  try {
    window.sessionStorage.setItem(key(pending.orderId), JSON.stringify(pending))
  } catch {
    // Sin almacenamiento: la página de resultado funciona igual con lo que diga el servidor.
  }
}

export function loadPendingOrderPayment(orderId: string): PendingOrderPayment | undefined {
  try {
    const raw = window.sessionStorage.getItem(key(orderId))
    if (!raw) return undefined
    const parsed = JSON.parse(raw) as Partial<PendingOrderPayment> | null
    if (!parsed || parsed.orderId !== orderId || typeof parsed.orderNumber !== 'number' || typeof parsed.reference !== 'string') return undefined
    return { orderId, orderNumber: parsed.orderNumber, reference: parsed.reference, lines: Array.isArray(parsed.lines) ? parsed.lines : [] }
  } catch {
    return undefined
  }
}

export function clearPendingOrderPayment(orderId: string): void {
  try {
    window.sessionStorage.removeItem(key(orderId))
  } catch {
    // Nada que limpiar.
  }
}

/** Solo se redirige a URLs https (la pasarela); nunca a esquemas arbitrarios. La URL no se modifica. */
export function isSafeCheckoutUrl(value: string | null | undefined): value is string {
  if (!value) return false
  try {
    return new URL(value).protocol === 'https:'
  } catch {
    return false
  }
}

/** El `id` de la redirección de Wompi (§4): se envía a `sync` solo si cumple el formato del contrato. */
export function safeTransactionId(value: string | null): string | undefined {
  if (!value) return undefined
  const trimmed = value.trim()
  return /^[A-Za-z0-9-]{1,64}$/.test(trimmed) ? trimmed : undefined
}

/** Hora de un `LocalDateTime` de Colombia (sin zona); si no es hoy, también la fecha. */
export function formatPaymentDeadline(value: string | null): string | undefined {
  if (!value) return undefined
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return undefined
  const today = new Date().toDateString() === date.toDateString()
  return new Intl.DateTimeFormat('es-CO', today ? { timeStyle: 'short' } : { day: 'numeric', month: 'long', hour: 'numeric', minute: '2-digit' }).format(date)
}

/**
 * Estado final para el polling (§6): `APPROVED`, `EXPIRED`, o `REJECTED`/`CANCELLED` sin enlace de pago.
 * Cualquier otro estado puede cambiar todavía.
 */
export function isFinalPayment(payment: OrderPaymentDto): boolean {
  if (payment.status === 'APPROVED' || payment.status === 'EXPIRED') return true
  return (payment.status === 'REJECTED' || payment.status === 'CANCELLED') && payment.checkoutUrl === null
}

export type OrderPaymentKind = 'pending' | 'approved' | 'late' | 'rejected' | 'voided' | 'expired' | 'closed'

/** Qué mostrar según `status` y `orderStatus` del servidor (§7). */
export function orderPaymentKind(payment: OrderPaymentDto): OrderPaymentKind {
  switch (payment.status) {
    case 'APPROVED':
      return payment.orderStatus === 'CANCELLED' ? 'late' : 'approved'
    case 'EXPIRED':
      return 'expired'
    case 'REJECTED':
      return payment.orderStatus === 'CANCELLED' ? 'closed' : 'rejected'
    case 'CANCELLED':
      return payment.orderStatus === 'CANCELLED' ? 'closed' : 'voided'
    default:
      return payment.orderStatus === 'CANCELLED' ? 'closed' : 'pending'
  }
}

export const isApiCode = (error: unknown, code: string) => error instanceof ApiError && error.code === code
