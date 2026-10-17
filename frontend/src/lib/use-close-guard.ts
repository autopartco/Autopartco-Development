import { useCallback, useEffect, useRef, useState } from 'react'

export interface CloseGuard {
  /** El formulario avisa si tiene cambios sin enviar (`false` al desmontarse o mientras envía). */
  setDirty: (dirty: boolean) => void
  /** Para la X, Esc, el fondo y «Cancelar»: cierra, o pregunta si hay cambios. */
  requestClose: () => void
  /** `true` mientras se muestra la pregunta «¿Descartar?». */
  asking: boolean
  keepEditing: () => void
  discard: () => void
}

/**
 * Guardia de cierre para paneles y diálogos (Sheet/Dialog) con formulario: no son navegación de
 * router, así que `useUnsavedChanges` no los ve. Se muestra con `DiscardChangesDialog`. Tras un
 * envío exitoso el formulario cierra con `onClose` directo (sin pasar por la pregunta).
 */
export function useCloseGuard(onClose: () => void): CloseGuard {
  const dirty = useRef(false)
  const close = useRef(onClose)
  useEffect(() => {
    close.current = onClose
  })
  const [asking, setAsking] = useState(false)

  const setDirty = useCallback((next: boolean) => {
    dirty.current = next
  }, [])
  const requestClose = useCallback(() => {
    if (dirty.current) setAsking(true)
    else close.current()
  }, [])
  const keepEditing = useCallback(() => setAsking(false), [])
  const discard = useCallback(() => {
    dirty.current = false
    setAsking(false)
    close.current()
  }, [])

  return { setDirty, requestClose, asking, keepEditing, discard }
}
