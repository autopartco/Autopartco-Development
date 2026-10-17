import { useEffect, useRef } from 'react'
import { useBlocker, type Blocker } from 'react-router'

/**
 * Guardia de cambios sin guardar: bloquea la navegación interna (react-router) y avisa al cerrar o
 * recargar la pestaña (`beforeunload`). Con `when = false` no interfiere. Después de un envío
 * exitoso, llama a `markSaved()` del borrador *antes* de navegar para que `when` ya sea `false`.
 * Si navegas en el mismo instante del guardado (el estado aún no se repintó), pasa una función en
 * lugar de un booleano: se evalúa justo al navegar, así que puede leer una referencia («ya guardé»).
 * Muestra el diálogo con `UnsavedChangesDialog` usando el `blocker` devuelto.
 */
export function useUnsavedChanges(when: boolean | (() => boolean)): Blocker {
  const check = useRef<() => boolean>(() => false)
  useEffect(() => {
    check.current = typeof when === 'function' ? when : () => when
  })
  const active = typeof when === 'function' ? true : when

  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      (typeof when === 'function' ? when() : when) && (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search),
  )

  useEffect(() => {
    if (!active) return
    function warn(event: BeforeUnloadEvent) {
      // El texto del aviso lo decide el navegador; basta con cancelar el evento.
      if (check.current()) event.preventDefault()
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [active])

  return blocker
}
