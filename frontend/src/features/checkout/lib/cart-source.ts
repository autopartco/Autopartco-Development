import { useSyncExternalStore } from 'react'
import type { CartLine, CheckoutLineRef, StoredCart } from '../api/types'

/**
 * Lee y escribe el carrito de `localStorage` (formato de `docs/architecture/2026-10-14-mvp-checkout-plan.md` §2.6).
 * Es tolerante: un JSON corrupto, de otra versión o con líneas inválidas nunca lanza; esas líneas se descartan.
 * Toda escritura emite `autopartco:cart-changed` (el indicador del header y las otras vistas lo escuchan).
 */
export const CART_KEY = 'autopartco.cart.v1'
export const CART_EVENT = 'autopartco:cart-changed'

const EMPTY: CartLine[] = []

function isLine(value: unknown): value is CartLine {
  if (!value || typeof value !== 'object') return false
  const line = value as Record<string, unknown>
  return (
    typeof line.productId === 'string' && line.productId !== '' &&
    typeof line.branchId === 'string' && line.branchId !== '' &&
    typeof line.quantity === 'number' && Number.isInteger(line.quantity) && line.quantity >= 1 && line.quantity <= 99 &&
    typeof line.name === 'string' &&
    typeof line.storeId === 'string' &&
    typeof line.unitPrice === 'number' && Number.isFinite(line.unitPrice) && line.unitPrice >= 0 &&
    typeof line.addedAt === 'string' &&
    (line.storeName === undefined || typeof line.storeName === 'string') &&
    (line.branchName === undefined || typeof line.branchName === 'string') &&
    (line.imageUrl === undefined || typeof line.imageUrl === 'string')
  )
}

function parse(raw: string | null): CartLine[] {
  if (!raw) return EMPTY
  try {
    const data = JSON.parse(raw) as Partial<StoredCart> | null
    if (!data || data.version !== 1 || !Array.isArray(data.lines)) return EMPTY
    const seen = new Set<string>()
    const lines = data.lines.filter((line): line is CartLine => {
      if (!isLine(line)) return false
      const id = `${line.productId}:${line.branchId}`
      if (seen.has(id)) return false
      seen.add(id)
      return true
    })
    return lines.length > 0 ? lines : EMPTY
  } catch {
    return EMPTY
  }
}

function readRaw(): string | null {
  try {
    return window.localStorage.getItem(CART_KEY)
  } catch {
    return null
  }
}

export function readCartLines(): CartLine[] {
  return parse(readRaw())
}

function write(lines: CartLine[]): void {
  try {
    const cart: StoredCart = { version: 1, updatedAt: new Date().toISOString(), lines }
    window.localStorage.setItem(CART_KEY, JSON.stringify(cart))
  } catch {
    // Sin almacenamiento disponible no hay carrito persistente; la UI refleja lo que se pudo leer.
  }
  window.dispatchEvent(new Event(CART_EVENT))
}

const sameLine = (line: CartLine, ref: CheckoutLineRef) => line.productId === ref.productId && line.branchId === ref.branchId

export function removeCartLines(refs: CheckoutLineRef[]): void {
  write(readCartLines().filter((line) => !refs.some((ref) => sameLine(line, ref))))
}

/** Fija la cantidad de una línea (1–99); con 0 o menos la quita. */
export function setCartLineQuantity(ref: CheckoutLineRef, quantity: number): void {
  if (quantity < 1) return removeCartLines([ref])
  const next = Math.min(99, Math.floor(quantity))
  write(readCartLines().map((line) => (sameLine(line, ref) ? { ...line, quantity: next } : line)))
}

export function clearCart(): void {
  write([])
}

// useSyncExternalStore exige una instantánea estable: se reutiliza la lista mientras el texto crudo no cambie.
let cachedRaw: string | null | undefined
let cachedLines: CartLine[] = EMPTY

function getSnapshot(): CartLine[] {
  const raw = readRaw()
  if (raw !== cachedRaw) {
    cachedRaw = raw
    cachedLines = parse(raw)
  }
  return cachedLines
}

function subscribe(listener: () => void): () => void {
  window.addEventListener(CART_EVENT, listener)
  window.addEventListener('storage', listener)
  return () => {
    window.removeEventListener(CART_EVENT, listener)
    window.removeEventListener('storage', listener)
  }
}

/** Líneas del carrito, reactivas a cambios locales y de otras pestañas. */
export function useCartLines(): CartLine[] {
  return useSyncExternalStore(subscribe, getSnapshot, () => EMPTY)
}

/**
 * Agrega unidades a una línea (la crea si no existe) y devuelve la cantidad resultante, tope 99.
 * La instantánea de nombre y precio solo se muestra: el servidor recalcula al pagar.
 */
export function addCartLine(line: Omit<CartLine, 'quantity' | 'addedAt'>, quantity = 1): number {
  const current = readCartLines()
  const existing = current.find((item) => sameLine(item, line))
  const total = Math.min(99, (existing?.quantity ?? 0) + Math.max(1, Math.floor(quantity)))
  if (existing) {
    write(current.map((item) => (sameLine(item, line) ? { ...item, ...line, quantity: total } : item)))
  } else {
    write([...current, { ...line, quantity: total, addedAt: new Date().toISOString() }])
  }
  return total
}
