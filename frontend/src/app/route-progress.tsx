import { useEffect, useState, useSyncExternalStore } from 'react'
import { useNavigation } from 'react-router'

/** Páginas diferidas que se están descargando (cada `PageFallback` montado cuenta una). */
let loadingPages = 0
const listeners = new Set<() => void>()
const emit = () => listeners.forEach((listener) => listener())

/** Se llama desde el fallback de `Suspense`: mientras está montado, la barra sabe que hay una carga. */
export function useReportPageLoading() {
  useEffect(() => {
    loadingPages += 1
    emit()
    return () => {
      loadingPages -= 1
      emit()
    }
  }, [])
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** Retardo antes de mostrar la barra: en cargas rápidas no aparece (evita parpadeo). */
const SHOW_DELAY_MS = 150
const FINISH_MS = 220

type Phase = 'idle' | 'running' | 'finishing'

/**
 * Línea de 2 px en rojo de acción al pie de la cabecera mientras hay navegación pendiente o se descarga
 * una página diferida. Decorativa (`aria-hidden`): el estado de carga lo anuncia el fallback de la página.
 */
export function RouteProgress() {
  const navigation = useNavigation()
  const pagesLoading = useSyncExternalStore(subscribe, () => loadingPages > 0, () => false)
  const busy = navigation.state !== 'idle' || pagesLoading
  const [phase, setPhase] = useState<Phase>('idle')

  useEffect(() => {
    if (busy) {
      const timer = window.setTimeout(() => setPhase('running'), SHOW_DELAY_MS)
      return () => window.clearTimeout(timer)
    }
    // Termina: si la barra era visible, se cierra; si nunca llegó a mostrarse, no hace nada.
    const finish = window.setTimeout(() => setPhase((current) => (current === 'running' ? 'finishing' : 'idle')), 0)
    const reset = window.setTimeout(() => setPhase('idle'), FINISH_MS + 40)
    return () => {
      window.clearTimeout(finish)
      window.clearTimeout(reset)
    }
  }, [busy])

  if (phase === 'idle') return null
  return (
    <div aria-hidden className="pointer-events-none absolute inset-x-0 bottom-0 h-0.5 overflow-hidden">
      <div className={phase === 'running' ? 'ap-progress-run size-full bg-ap-red' : 'ap-progress-finish size-full bg-ap-red'} />
    </div>
  )
}
