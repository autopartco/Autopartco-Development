import { ApiError } from '@/lib/api'
import type { CheckoutLineRef, StockShortage } from '../api/types'

/** Lo que la pantalla de checkout sabe mostrar (plan §5.3). */
export type CheckoutProblem =
  | { kind: 'rejected'; amount?: number }
  | { kind: 'stock'; items: StockShortage[] }
  | { kind: 'gone'; items: CheckoutLineRef[] }
  | { kind: 'address' }
  | { kind: 'changed' }
  | { kind: 'duplicate' }
  | { kind: 'invalid'; message: string }
  | { kind: 'unauthorized' }
  | { kind: 'unavailable' }

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null && !Array.isArray(value)

function itemsOf(details: unknown): Record<string, unknown>[] {
  if (!isRecord(details) || !Array.isArray(details.items)) return []
  return details.items.filter(isRecord)
}

function lineRefs(details: unknown): CheckoutLineRef[] {
  return itemsOf(details).flatMap((item) =>
    typeof item.productId === 'string' && typeof item.branchId === 'string' ? [{ productId: item.productId, branchId: item.branchId }] : [],
  )
}

function shortages(details: unknown): StockShortage[] {
  return itemsOf(details).flatMap((item) =>
    typeof item.productId === 'string' && typeof item.branchId === 'string' && typeof item.requested === 'number' && typeof item.available === 'number'
      ? [{ productId: item.productId, branchId: item.branchId, requested: item.requested, available: item.available }]
      : [],
  )
}

export function classifyCheckoutError(error: unknown): CheckoutProblem {
  if (!(error instanceof ApiError)) return { kind: 'unavailable' }
  switch (error.code) {
    case 'PAYMENT_REJECTED': {
      const amount = isRecord(error.details) && typeof error.details.amount === 'number' ? error.details.amount : undefined
      return { kind: 'rejected', amount }
    }
    case 'INSUFFICIENT_STOCK':
      return { kind: 'stock', items: shortages(error.details) }
    case 'PRODUCT_NOT_FOUND':
      return { kind: 'gone', items: lineRefs(error.details) }
    case 'ADDRESS_NOT_FOUND':
      return { kind: 'address' }
    case 'CHECKOUT_LISTING_CHANGED':
      return { kind: 'changed' }
    case 'CHECKOUT_DUPLICATE_REQUEST':
      return { kind: 'duplicate' }
    case 'VALIDATION_ERROR':
    case 'CHECKOUT_DUPLICATE_LINE':
    case 'PAYMENT_METHOD_NOT_SUPPORTED':
    case 'INVALID_REQUEST_BODY':
      return { kind: 'invalid', message: error.message }
  }
  if (error.status === 401) return { kind: 'unauthorized' }
  // Sin respuesta (red) o 5xx: no sabemos si el servidor llegó a crear el pedido.
  if (error.status === undefined || error.status >= 500) return { kind: 'unavailable' }
  return { kind: 'invalid', message: error.message }
}

/**
 * `true` si el servidor confirmó que no guardó nada (402, 404, 409, 400): se puede usar
 * una clave de idempotencia nueva. En un error de red o 5xx se conserva la misma para que
 * el reintento no cobre dos veces.
 */
export function leftNothingBehind(problem: CheckoutProblem): boolean {
  return problem.kind !== 'unavailable' && problem.kind !== 'duplicate' && problem.kind !== 'unauthorized'
}

export const lineKey = (ref: CheckoutLineRef) => `${ref.productId}:${ref.branchId}`
