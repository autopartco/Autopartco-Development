import { useEffect, useRef } from 'react'

/** Última posición por grupo: si la barra de pestañas se remonta al cambiar de pantalla, el indicador parte de ahí. */
const lastRect = new Map<string, { x: number; w: number }>()

const ACTIVE = '[aria-current="page"], [aria-current="true"], [aria-selected="true"]'

const toTransform = ({ x, w }: { x: number; w: number }) => `translateX(${x}px) scaleX(${w})`

/**
 * Línea roja (2 px) de la pestaña activa, posicionada con `transform` y deslizada en 200 ms entre pestañas.
 * Va como hijo de la fila de pestañas, que debe ser `relative`; detecta la activa por `aria-current` o
 * `aria-selected`, así que las pestañas solo conservan un borde transparente (para el hover).
 * `group` identifica la barra para recordar la posición si se remonta.
 */
export function TabIndicator({ group }: { group: string }) {
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const indicator = ref.current
    const row = indicator?.parentElement
    if (!indicator || !row) return

    const measure = () => {
      const active = row.querySelector<HTMLElement>(ACTIVE)
      return active ? { x: active.offsetLeft, w: active.offsetWidth } : null
    }
    const place = () => {
      const rect = measure()
      if (!rect) {
        indicator.style.opacity = '0'
        return
      }
      indicator.style.transform = toTransform(rect)
      indicator.style.opacity = '1'
      lastRect.set(group, rect)
    }

    // Primera colocación sin animar: en la posición anterior (si se remontó) o ya en la actual.
    const first = measure()
    const start = lastRect.get(group) ?? first
    if (start) {
      indicator.style.transition = 'none'
      indicator.style.transform = toTransform(start)
      indicator.style.opacity = '1'
      void indicator.offsetWidth
      indicator.style.transition = ''
    }
    place()

    const mutations = new MutationObserver(place)
    mutations.observe(row, { attributes: true, attributeFilter: ['aria-current', 'aria-selected'], subtree: true, childList: true })
    const resize = new ResizeObserver(place)
    resize.observe(row)
    return () => {
      mutations.disconnect()
      resize.disconnect()
    }
  }, [group])

  return (
    <span
      ref={ref}
      aria-hidden
      className="pointer-events-none absolute bottom-0 left-0 z-10 h-0.5 w-px origin-left bg-ap-red opacity-0 transition-[transform,opacity] ap-dur-panel ease-ap-in-out"
    />
  )
}
