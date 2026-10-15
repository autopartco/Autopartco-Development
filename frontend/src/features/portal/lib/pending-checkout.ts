/**
 * Cobro con Wompi en curso. Wompi devuelve al usuario a `/portal/pago/resultado?id=…&env=…` sin
 * nuestra referencia ni el almacén, así que antes de redirigir se guarda aquí lo necesario para
 * consultar el estado al servidor. No es fuente de verdad: el estado del pago siempre lo da
 * `GET /stores/{id}/subscription/payments`. Se borra al confirmar el pago y al cerrar sesión.
 */
export interface PendingCheckout {
  storeId: string
  reference: string
  planName: string
  months: number
  amountInCents: number
  /** ISO 8601. */
  startedAt: string
}

const PENDING_CHECKOUT_KEY = 'autopartco.pendingCheckout'

function isPendingCheckout(value: unknown): value is PendingCheckout {
  if (typeof value !== 'object' || value === null) return false
  const record = value as Record<string, unknown>
  return (
    typeof record.storeId === 'string' &&
    record.storeId.length > 0 &&
    typeof record.reference === 'string' &&
    record.reference.length > 0 &&
    typeof record.planName === 'string' &&
    typeof record.months === 'number' &&
    typeof record.amountInCents === 'number' &&
    typeof record.startedAt === 'string'
  )
}

export function savePendingCheckout(checkout: PendingCheckout): void {
  try {
    window.sessionStorage.setItem(PENDING_CHECKOUT_KEY, JSON.stringify(checkout))
  } catch {
    // Sin almacenamiento: la página de resultado mostrará el estado neutro con enlace al plan.
  }
}

export function loadPendingCheckout(): PendingCheckout | undefined {
  try {
    const raw = window.sessionStorage.getItem(PENDING_CHECKOUT_KEY)
    if (!raw) return undefined
    const parsed: unknown = JSON.parse(raw)
    return isPendingCheckout(parsed) ? parsed : undefined
  } catch {
    return undefined
  }
}

export function clearPendingCheckout(): void {
  try {
    window.sessionStorage.removeItem(PENDING_CHECKOUT_KEY)
  } catch {
    // Nada que limpiar.
  }
}
