import { useEffect, useRef } from 'react'
import { trackAdImpression } from '../../lib/ad-events'

/** Tiempo que el anuncio debe verse (al menos 50 %) antes de contar la impresión. */
const VISIBLE_MS = 1000
const VISIBLE_RATIO = 0.5

/**
 * Registra una impresión cuando el elemento es visible al menos el 50 % durante 1 s, una vez por token.
 * Si sale de pantalla antes de cumplir el segundo, el conteo se reinicia.
 */
export function useAdImpression<T extends HTMLElement>(token: string | undefined) {
  const ref = useRef<T>(null)
  useEffect(() => {
    const element = ref.current
    if (!token || !element || typeof IntersectionObserver === 'undefined') return
    let timer: number | undefined
    const clear = () => {
      window.clearTimeout(timer)
      timer = undefined
    }
    const observer = new IntersectionObserver(
      ([entry]) => {
        if (entry?.isIntersecting && entry.intersectionRatio >= VISIBLE_RATIO) {
          timer ??= window.setTimeout(() => {
            if (document.visibilityState === 'visible') trackAdImpression(token)
            observer.disconnect()
          }, VISIBLE_MS)
        } else {
          clear()
        }
      },
      { threshold: [0, VISIBLE_RATIO, 1] },
    )
    observer.observe(element)
    return () => {
      clear()
      observer.disconnect()
    }
  }, [token])
  return ref
}
