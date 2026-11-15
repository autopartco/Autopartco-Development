import { useEffect, useRef, useState } from 'react'
import { observeOnce, shouldAnimateEntry } from '@/lib/reveal'

const DURATION_MS = 700
const formatEs = (value: number) => new Intl.NumberFormat('es-CO').format(value)

/**
 * Cifra que cuenta desde 0 hasta `value` una sola vez al entrar en pantalla (700 ms, easing out) con cifras tabulares.
 * Lectores de pantalla reciben siempre el valor final; con movimiento reducido o al volver con «atrás» se muestra directo.
 */
export function CountUp({ value, format = formatEs }: { value: number; format?: (value: number) => string }) {
  const [animate] = useState(shouldAnimateEntry)
  const [progress, setProgress] = useState(animate ? 0 : 1)
  const ref = useRef<HTMLSpanElement>(null)

  useEffect(() => {
    const element = ref.current
    if (!animate || !element) return
    let frame = 0
    const stop = observeOnce(element, () => {
      const start = performance.now()
      const tick = (now: number) => {
        const t = Math.min((now - start) / DURATION_MS, 1)
        setProgress(1 - (1 - t) ** 3)
        if (t < 1) frame = requestAnimationFrame(tick)
      }
      frame = requestAnimationFrame(tick)
    })
    return () => {
      stop()
      cancelAnimationFrame(frame)
    }
  }, [animate])

  return (
    <>
      <span ref={ref} aria-hidden className="tabular-nums">
        {format(Math.round(value * progress))}
      </span>
      <span className="sr-only">{format(value)}</span>
    </>
  )
}
