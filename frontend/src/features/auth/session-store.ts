import { invalidateAsync } from '@/lib/use-async'
import { forgetPortalStore } from './lib/account-menu-items'
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

/**
 * Al cambiar de persona (cerrar sesión, ingresar o cambiar de cuenta) se descarta toda la caché en
 * memoria: almacenes, permisos, pedidos, precios por audiencia… pertenecen a la sesión anterior. Antes
 * solo se limpiaba el portal si su guarda seguía montada, y cerrar sesión desde el portal sale primero
 * a `/`, así que la cuenta siguiente veía el almacén de la anterior. También se olvida el último almacén
 * abierto (atajo del menú de cuenta).
 */
export function setSession(next: Session | undefined): void {
  const previous = current
  const changed = previous?.user.email !== next?.user.email
  current = next
  initialized = true
  if (changed) {
    invalidateAsync('')
    if (previous) forgetPortalStore()
  }
  for (const listener of listeners) listener()
}

export function subscribeSession(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Recupera una sesión al cargar la app (la llama el servicio una sola vez). Espera a `restore` si es asíncrono. */
export async function initSession(restore: () => Session | undefined | Promise<Session | undefined>): Promise<void> {
  if (initialized) return
  initialized = true
  const restored = await restore()
  if (restored) setSession(restored)
}
