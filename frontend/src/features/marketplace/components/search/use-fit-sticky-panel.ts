import { useCallback, useRef } from 'react'

/** Alto mínimo del panel (px): por debajo de esto se deja que suba con la página. */
const MIN_HEIGHT = 160
/** Margen inferior respecto al borde de la ventana (px). */
const BOTTOM_GAP = 24

/**
 * Panel `sticky` con scroll propio que nunca queda bajo la cabecera: su alto máximo se ajusta al
 * espacio entre su posición y lo que sea menor entre el borde inferior de la ventana y el final de
 * su columna. Al llegar al pie se encoge en vez de subir.
 *
 * La columna (padre del panel) debe tener un alto que no dependa del panel (p. ej. `min-h` en la
 * cuadrícula); si no, encoger el panel encogería la columna.
 */
export function useFitStickyPanel<T extends HTMLElement>() {
  const cleanup = useRef<() => void>(undefined)

  return useCallback((panel: T | null) => {
    cleanup.current?.()
    cleanup.current = undefined
    const column = panel?.parentElement
    if (!panel || !column) return

    // Se calcula en el mismo evento (pocas lecturas de geometría): sin requestAnimationFrame, que el
    // navegador pausa en pestañas o marcos en segundo plano.
    const fit = () => {
      const stickyTop = Number.parseFloat(getComputedStyle(panel).top) || 0
      // Antes de quedar fijo, el panel empieza más abajo (bajo el título de la página).
      const top = Math.max(stickyTop, panel.getBoundingClientRect().top)
      const columnBottom = column.getBoundingClientRect().bottom
      const available = Math.min(window.innerHeight - BOTTOM_GAP, columnBottom) - top
      panel.style.maxHeight = `${Math.max(MIN_HEIGHT, Math.floor(available))}px`
    }

    fit()
    window.addEventListener('scroll', fit, { passive: true })
    window.addEventListener('resize', fit)
    const observer = new ResizeObserver(fit)
    observer.observe(column)

    cleanup.current = () => {
      window.removeEventListener('scroll', fit)
      window.removeEventListener('resize', fit)
      observer.disconnect()
      panel.style.maxHeight = ''
    }
  }, [])
}
