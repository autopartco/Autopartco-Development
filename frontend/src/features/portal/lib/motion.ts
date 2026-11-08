import { useEffect, useRef, useState, type RefObject } from 'react'

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
