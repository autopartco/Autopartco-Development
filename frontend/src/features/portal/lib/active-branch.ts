/**
 * Sucursal activa del portal, recordada por persona y almacén en `localStorage`. Solo es una preferencia de
 * vista: el servidor vuelve a validar que la sucursal pertenezca al almacén en cada consulta. Si el navegador
 * bloquea el almacenamiento (modo privado, cuota), se trabaja sin recordar la elección.
 */
const PREFIX = 'autopartco:portal:sucursal'

const storageKey = (userKey: string, storeId: string) => `${PREFIX}:${userKey}:${storeId}`

export function readActiveBranch(userKey: string | undefined, storeId: string): string | undefined {
  if (!userKey) return undefined
  try {
    return window.localStorage.getItem(storageKey(userKey, storeId)) ?? undefined
  } catch {
    return undefined
  }
}

export function writeActiveBranch(userKey: string | undefined, storeId: string, branchId: string | undefined): void {
  if (!userKey) return
  try {
    if (branchId) window.localStorage.setItem(storageKey(userKey, storeId), branchId)
    else window.localStorage.removeItem(storageKey(userKey, storeId))
  } catch {
    // Sin almacenamiento disponible: la elección vale solo para esta visita.
  }
}
