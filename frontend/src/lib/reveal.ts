/**
 * Soporte de movimiento de entrada (escalonado, contadores, pestañas). Un solo `IntersectionObserver`
 * compartido por toda la aplicación: ningún elemento crea el suyo.
 */

/** Separación entre elementos que entran juntos y tope de escalones (del noveno en adelante, entran con el octavo). */
const STAGGER_MS = 45
const MAX_STEPS = 7

type Enter = (delayMs: number) => void

const handlers = new WeakMap<Element, Enter>()
let shared: IntersectionObserver | null = null

function getObserver(): IntersectionObserver {
  shared ??= new IntersectionObserver(
    (entries) => {
      const entering = entries
        .filter((entry) => entry.isIntersecting)
        .toSorted(
          (a, b) =>
            Math.round(a.boundingClientRect.top) - Math.round(b.boundingClientRect.top) ||
            a.boundingClientRect.left - b.boundingClientRect.left,
        )
      entering.forEach((entry, order) => {
        shared?.unobserve(entry.target)
        handlers.get(entry.target)?.(Math.min(order, MAX_STEPS) * STAGGER_MS)
        handlers.delete(entry.target)
      })
    },
    { rootMargin: '0px 0px -6% 0px', threshold: 0.05 },
  )
  return shared
}

/** Avisa una sola vez cuando `element` entra en pantalla; devuelve la función que cancela la espera. */
export function observeOnce(element: Element, onEnter: Enter): () => void {
  handlers.set(element, onEnter)
  getObserver().observe(element)
  return () => {
    handlers.delete(element)
    shared?.unobserve(element)
  }
}

export function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Claves de entradas del historial que el usuario ya dejó atrás: al volver a ellas no se repite la entrada. */
const leftEntries = new Set<string>()

export function markEntryLeft(key: string): void {
  leftEntries.add(key)
}

function currentEntryKey(): string {
  const state = window.history.state as { key?: string } | null
  return state?.key ?? 'default'
}

/** `true` si la entrada debe animarse: hay observer, no se pidió reducir movimiento y no es un regreso con «atrás». */
export function shouldAnimateEntry(): boolean {
  return (
    typeof IntersectionObserver !== 'undefined' && !prefersReducedMotion() && !leftEntries.has(currentEntryKey())
  )
}
