/**
 * Soporte de movimiento de entrada (escalonado, contadores, pestañas). Un solo `IntersectionObserver`
 * compartido por toda la aplicación: ningún elemento crea el suyo.
 */

/** Separación entre elementos que entran juntos y tope de escalones (del noveno en adelante, entran con el octavo). */
const STAGGER_MS = 45
const MAX_STEPS = 7

type Enter = (delayMs: number) => void

const handlers = new Map<Element, Enter>()
let shared: IntersectionObserver | null = null
/**
 * `true` si el observer falló o no avisa: desde entonces todo se muestra sin esperar. El contenido nunca puede quedar
 * oculto por la animación (navegadores antiguos o con errores: pantalla «en blanco» en teléfonos).
 */
let broken = false
/** Si un elemento ya visible no recibió aviso en este plazo, el observer no funciona: se muestra todo. */
const WATCHDOG_MS = 1500

function revealAll(): void {
  broken = true
  const pending = [...handlers.entries()]
  handlers.clear()
  shared?.disconnect()
  pending.forEach(([, enter]) => safeEnter(enter, 0))
}

function safeEnter(enter: Enter, delayMs: number): void {
  try {
    enter(delayMs)
  } catch {
    // Mostrar el elemento es responsabilidad del handler; un fallo aquí no debe dejar nada oculto.
  }
}

function getObserver(): IntersectionObserver {
  shared ??= new IntersectionObserver(
    (entries) => {
      try {
        // `slice().sort()` y no `toSorted()`: funciona en Safari/iOS antiguos.
        const entering = entries
          .filter((entry) => entry.isIntersecting)
          .slice()
          .sort(
            (a, b) =>
              Math.round(a.boundingClientRect.top) - Math.round(b.boundingClientRect.top) ||
              a.boundingClientRect.left - b.boundingClientRect.left,
          )
        entering.forEach((entry, order) => {
          shared?.unobserve(entry.target)
          const enter = handlers.get(entry.target)
          handlers.delete(entry.target)
          if (enter) safeEnter(enter, Math.min(order, MAX_STEPS) * STAGGER_MS)
        })
      } catch {
        revealAll()
      }
    },
    { rootMargin: '0px 0px 12% 0px', threshold: 0 },
  )
  return shared
}

function inViewport(element: Element): boolean {
  const rect = element.getBoundingClientRect()
  return rect.bottom > 0 && rect.top < window.innerHeight && rect.width > 0 && rect.height > 0
}

/** Avisa una sola vez cuando `element` entra en pantalla; devuelve la función que cancela la espera. */
export function observeOnce(element: Element, onEnter: Enter): () => void {
  if (broken) {
    safeEnter(onEnter, 0)
    return () => {}
  }
  handlers.set(element, onEnter)
  try {
    getObserver().observe(element)
  } catch {
    revealAll()
    return () => {}
  }
  // Vigilante: si ya está en pantalla y el observer no avisó, algo falla en este navegador.
  const watchdog = window.setTimeout(() => {
    if (handlers.has(element) && inViewport(element)) revealAll()
  }, WATCHDOG_MS)
  return () => {
    window.clearTimeout(watchdog)
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
  try {
    return (
      !broken &&
      typeof IntersectionObserver !== 'undefined' &&
      !prefersReducedMotion() &&
      !leftEntries.has(currentEntryKey())
    )
  } catch {
    return false
  }
}
