/**
 * Identificador anónimo y estable por navegador para medir búsquedas (cabecera `X-Session-Id`).
 * Es un UUID aleatorio: no contiene ni se deriva de datos personales. Si el almacenamiento no está
 * disponible (modo privado, bloqueo de cookies), se usa uno en memoria que dura mientras la pestaña.
 */
const STORAGE_KEY = 'autopartco.session-id'
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

let memoryId: string | undefined

function newId(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6]! & 0x0f) | 0x40
  bytes[8] = (bytes[8]! & 0x3f) | 0x80
  const hex = [...bytes].map((byte) => byte.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export function getAnonymousSessionId(): string {
  try {
    const stored = window.localStorage.getItem(STORAGE_KEY)
    if (stored && UUID.test(stored)) return stored
    const created = newId()
    window.localStorage.setItem(STORAGE_KEY, created)
    return created
  } catch {
    memoryId ??= newId()
    return memoryId
  }
}
