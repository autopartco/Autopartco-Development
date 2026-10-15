import { createContext, useContext, useEffect, useRef, useState, type RefObject } from 'react'

/** `true` si la persona pidió reducir movimiento: todo queda en su estado final. */
export function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Curva de las entradas del portal (igual a `--ease-out-thread`). */
export const EASE = 'cubic-bezier(0.22, 1, 0.36, 1)'

/** Paso del escalonado entre mosaicos (60–90 ms). */
export const STAGGER_MS = 75

/**
 * Marca `true` una sola vez cuando el elemento entra en pantalla (IntersectionObserver).
 * Con movimiento reducido o sin IntersectionObserver arranca en `true`.
 */
export function useRevealOnce<T extends Element>(): [RefObject<T | null>, boolean] {
  const ref = useRef<T | null>(null)
  const [revealed, setRevealed] = useState(() => prefersReducedMotion() || typeof IntersectionObserver === 'undefined')

  useEffect(() => {
    const element = ref.current
    if (revealed || !element) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setRevealed(true)
          observer.disconnect()
        }
      },
      { threshold: 0.15 },
    )
    observer.observe(element)
    return () => observer.disconnect()
  }, [revealed])

  return [ref, revealed]
}

/** Visibilidad continua del elemento: sirve para pausar temporizadores fuera de pantalla. */
export function useInView<T extends Element>(): [RefObject<T | null>, boolean] {
  const ref = useRef<T | null>(null)
  const [inView, setInView] = useState(true)

  useEffect(() => {
    const element = ref.current
    if (!element || typeof IntersectionObserver === 'undefined') return
    const observer = new IntersectionObserver((entries) => {
      const last = entries.at(-1)
      if (last) setInView(last.isIntersecting)
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])

  return [ref, inView]
}

/** Cuando el mosaico padre ya entró; los hijos lo usan para escalonar sus propias entradas. */
export const RevealContext = createContext(true)

export function useRevealed(): boolean {
  return useContext(RevealContext)
}

/**
 * Número que rueda de 0 (o del valor anterior) al objetivo en `duration` ms al activarse.
 * Con movimiento reducido devuelve el valor final directamente.
 */
export function useCountUp(target: number, active: boolean, duration = 1200): number {
  const reduced = prefersReducedMotion()
  const [value, setValue] = useState(0)
  const current = useRef(0)

  useEffect(() => {
    if (reduced || !active) return
    const from = current.current
    const startedAt = performance.now()
    let frame = 0
    const tick = (now: number) => {
      // `now` (marca del frame) puede ser anterior a `startedAt`: sin acotar daba progreso negativo.
      const progress = Math.min(1, Math.max(0, (now - startedAt) / duration))
      const eased = 1 - Math.pow(1 - progress, 3)
      const value = from + (target - from) * eased
      current.current = Math.min(Math.max(value, Math.min(from, target)), Math.max(from, target))
      setValue(current.current)
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, active, duration, reduced])

  return reduced ? target : active ? value : 0
}
