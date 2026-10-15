/**
 * Utilidades de formularios del portal: lectura de números en formato colombiano y manejo de
 * errores por campo (cliente + servidor). Las reglas replican las anotaciones de los records
 * Java solo para avisar a tiempo: el servidor valida todo otra vez.
 */

export type FieldErrors = Record<string, string>

/**
 * Convierte "85000", "85.000", "85.000,50" o "85000.50" en número. Devuelve `undefined` si no es
 * un número válido. Un punto seguido de exactamente tres dígitos se lee como separador de miles.
 */
export function parseAmount(value: string): number | undefined {
  const clean = value.trim().replace(/[\s$]/g, '')
  if (!clean) return undefined
  let normalized = clean
  if (clean.includes(',')) {
    normalized = clean.replace(/\./g, '').replace(',', '.')
  } else if (/^\d{1,3}(\.\d{3})+$/.test(clean)) {
    normalized = clean.replace(/\./g, '')
  }
  if (!/^\d+(\.\d+)?$/.test(normalized)) return undefined
  const number = Number(normalized)
  return Number.isFinite(number) ? number : undefined
}

/** Entero no negativo o `undefined`. */
export function parseCount(value: string): number | undefined {
  const clean = value.trim()
  if (!/^\d+$/.test(clean)) return undefined
  const number = Number(clean)
  return Number.isSafeInteger(number) ? number : undefined
}

export function firstErrorId(order: readonly string[], errors: FieldErrors, idFor: (field: string) => string): string | undefined {
  const first = order.find((field) => errors[field])
  return first ? idFor(first) : undefined
}

export function focusById(id: string | undefined): void {
  if (id) document.getElementById(id)?.focus()
}

/** Teléfono colombiano fijo o celular: 7 a 10 dígitos (regla de `BranchCreateRequest`). */
export const PHONE_PATTERN = /^[0-9]{7,10}$/

export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
