import { useCallback, useRef } from 'react'

/** Alto mínimo del panel fijo (px): por debajo de esto se deja que suba con la página. */
const MIN_HEIGHT = 160
/** Margen inferior respecto al borde de la ventana (px). */
const BOTTOM_GAP = 24

/**
 * Columna de filtros junto a los resultados. Dos modos, según qué columna es más alta:
 * - Resultados más altos que los filtros: panel `sticky` con scroll propio. Su alto máximo se
 *   ajusta al espacio visible, así al llegar al pie se encoge en vez de quedar bajo la cabecera.
 * - Resultados más bajos: el panel fluye con la página sin scroll propio, y el pie aparece solo
 *   al terminar los filtros (no sube junto a unos pocos productos).
 *
 * Estructura esperada: `panel` (cabecera + contenedor con scroll) dentro de la columna; la
 * columna de resultados es el siguiente hermano de la columna y debe tener su alto natural
 * (`self-start`), si no se estira con la fila y la comparación deja de servir.
 */
export function useFitStickyPanel<T extends HTMLElement>() {
  const cleanup = useRef<() => void>(undefined)

  return useCallback((panel: T | null) => {
    cleanup.current?.()
    cleanup.current = undefined
    const column = panel?.parentElement
    const results = column?.nextElementSibling
    const scroller = panel?.lastElementChild
    if (!panel || !column || !(results instanceof HTMLElement) || !(scroller instanceof HTMLElement)) return

    // Se calcula en el mismo evento (pocas lecturas de geometría): sin requestAnimationFrame, que el
    // navegador pausa en pestañas o marcos en segundo plano.
    const fit = () => {
      const header = panel.firstElementChild
      const filtersHeight = (header instanceof HTMLElement ? header.offsetHeight : 0) + scroller.scrollHeight
      if (results.offsetHeight < filtersHeight) {
        panel.style.position = 'static'
        panel.style.maxHeight = 'none'
        return
      }
      panel.style.position = ''
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
    observer.observe(results)
    if (scroller.firstElementChild) observer.observe(scroller.firstElementChild)

    cleanup.current = () => {
      window.removeEventListener('scroll', fit)
      window.removeEventListener('resize', fit)
      observer.disconnect()
      panel.style.position = ''
      panel.style.maxHeight = ''
    }
  }, [])
}
