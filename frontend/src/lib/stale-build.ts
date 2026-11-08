// Tras publicar una versión nueva, las pestañas abiertas piden archivos de la anterior
// que ya no existen. Una recarga descarga la versión vigente; solo se intenta una vez
// por sesión de pestaña para no entrar en bucle si el fallo es de red.
const RELOAD_KEY = 'autopartco:stale-build-reload'
const CHUNK_ERROR = /dynamically imported module|Importing a module script failed|Failed to fetch dynamically|Unable to preload CSS/i

export function isStaleBuildError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : typeof error === 'string' ? error : ''
  return CHUNK_ERROR.test(message)
}

/** Indica, sin efectos, si todavía se puede intentar la recarga automática. */
export function canReloadForStaleBuild(): boolean {
  try {
    return !sessionStorage.getItem(RELOAD_KEY)
  } catch {
    return false
  }
}

/** Recarga una sola vez; devuelve false si ya se intentó y hay que mostrar el error. */
export function reloadOnceForStaleBuild(): boolean {
  try {
    if (sessionStorage.getItem(RELOAD_KEY)) return false
    sessionStorage.setItem(RELOAD_KEY, String(Date.now()))
  } catch {
    return false
  }
  window.location.reload()
  return true
}

/** Llamar al arrancar: limpia la marca tras una carga correcta y escucha fallos de precarga de Vite. */
export function installStaleBuildRecovery() {
  window.addEventListener('vite:preloadError', (event) => {
    if (reloadOnceForStaleBuild()) event.preventDefault()
  })
  window.addEventListener('load', () => {
    // Si la página cargó bien durante 10 s, una próxima versión podrá recargar de nuevo.
    window.setTimeout(() => {
      try {
        sessionStorage.removeItem(RELOAD_KEY)
      } catch {
        /* sin almacenamiento: nada que limpiar */
      }
    }, 10_000)
  })
}
