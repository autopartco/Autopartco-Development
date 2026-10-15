/**
 * Validaciones de cliente que replican las del backend (mismos límites y mensajes
 * que `RegisterRequest`, `LoginRequest` y `PasswordResetRequest`). Solo ayudan a
 * corregir antes de enviar: el servidor vuelve a validar todo.
 */

export type FieldErrors = Record<string, string>

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
/** Mismo patrón que el backend: minúscula, mayúscula, número y carácter especial. */
const STRONG_PASSWORD = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).+$/

export const PASSWORD_HINT = 'Entre 8 y 72 caracteres, con mayúscula, minúscula, número y un carácter especial.'

export function requiredText(value: string, label: string, max: number): string | undefined {
  if (!value.trim()) return `${label} es obligatorio.`
  if (value.trim().length > max) return `${label} no puede superar ${max} caracteres.`
  return undefined
}

export function emailError(value: string, max = 100, label = 'El correo'): string | undefined {
  if (!value.trim()) return `${label} es obligatorio.`
  if (value.trim().length > max) return `${label} no puede superar ${max} caracteres.`
  if (!EMAIL.test(value.trim())) return `${label} debe tener un formato válido, por ejemplo nombre@correo.com.`
  return undefined
}

export function newPasswordError(value: string): string | undefined {
  if (!value) return 'La contraseña es obligatoria.'
  if (value.length < 8 || value.length > 72) return 'La contraseña debe tener entre 8 y 72 caracteres.'
  if (!STRONG_PASSWORD.test(value)) return 'La contraseña debe incluir mayúscula, minúscula, número y carácter especial.'
  return undefined
}

export function birthDateError(value: string): string | undefined {
  if (!value) return 'La fecha de nacimiento es obligatoria.'
  const date = new Date(`${value}T00:00:00`)
  if (Number.isNaN(date.getTime())) return 'Escribe una fecha válida.'
  if (date.getTime() >= Date.now()) return 'La fecha de nacimiento debe estar en el pasado.'
  return undefined
}

/** Quita los campos sin error. */
export function compact(errors: Record<string, string | undefined>): FieldErrors {
  return Object.fromEntries(Object.entries(errors).filter((entry): entry is [string, string] => Boolean(entry[1])))
}
