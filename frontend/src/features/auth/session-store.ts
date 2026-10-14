import type { Session } from './api/types'

/**
 * Sesión visible para la UI (nombre y correo). Los tokens NO viven aquí: los
 * guarda en memoria el servicio http y nunca llegan a React ni al almacenamiento.
 * El frontend no decide autorización: esto solo cambia lo que muestra la cabecera.
 */
let current: Session | undefined
let initialized = false
const listeners = new Set<() => void>()

export function getSession(): Session | undefined {
  return current
}

export function setSession(next: Session | undefined): void {
  current = next
  initialized = true
  for (const listener of listeners) listener()
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Recupera una sesión al cargar la app (la llama el servicio una sola vez). */
export function initSession(restore: () => Session | undefined): void {
  if (initialized) return
  initialized = true
  const restored = restore()
  if (restored) setSession(restored)
}
