/**
 * Ciudad elegida por el usuario, compartida entre la cabecera y las pantallas.
 * Se guarda en localStorage con clave versionada; si no hay valor, el servicio
 * usa su ciudad por defecto.
 */
const STORAGE_KEY = 'autopartco:city:v1'

function readStored(): string | undefined {
  try {
    return window.localStorage.getItem(STORAGE_KEY) ?? undefined
  } catch {
    return undefined
  }
}

let selectedCityId: string | undefined = typeof window === 'undefined' ? undefined : readStored()
const listeners = new Set<() => void>()

export function subscribeSelectedCity(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function getSelectedCityId(): string | undefined {
  return selectedCityId
}

export function setSelectedCityId(cityId: string): void {
  if (cityId === selectedCityId) return
  selectedCityId = cityId
  try {
    window.localStorage.setItem(STORAGE_KEY, cityId)
  } catch {
    // Almacenamiento no disponible (modo privado): la elección dura la sesión.
  }
  for (const listener of listeners) listener()
}
