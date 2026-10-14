import { useEffect, useRef } from 'react'
import { animate, useInView, useReducedMotion } from 'motion/react'
import { formatKm } from '@/features/location/lib/geo'
import { formatCOP, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { EASE_THREAD } from './motion-presets'

const FORMATS = {
  cop: (value: number) => formatCOP(Math.round(value)),
  int: (value: number) => formatNumber(Math.round(value)),
  km: (value: number) => formatKm(value),
} as const

/**
 * Cifra que sube desde cero en 1 s al entrar en pantalla (una vez). Escribe el
 * texto directamente en el nodo, sin re-renderizar. El ancho final se reserva
 * con una copia invisible para no mover el diseño, y los lectores de pantalla
 * solo leen el valor final.
 */
export function CountUp({ value, format, className }: { value: number; format: keyof typeof FORMATS; className?: string }) {
  const rootRef = useRef<HTMLSpanElement>(null)
  const textRef = useRef<HTMLSpanElement>(null)
  const inView = useInView(rootRef, { once: true, amount: 0.6 })
  const reduced = useReducedMotion()
  const toText = FORMATS[format]
  const finalText = toText(value)

  useEffect(() => {
    const node = textRef.current
    if (!node || !inView || reduced) return
    const controls = animate(0, value, {
      duration: 1,
      ease: EASE_THREAD,
      onUpdate: (latest) => {
        node.textContent = toText(latest)
      },
    })
    return () => controls.stop()
  }, [inView, reduced, value, toText])

  return (
    <span ref={rootRef} className={cn('inline-grid tabular-nums', className)}>
      <span className="invisible col-start-1 row-start-1" aria-hidden>
        {finalText}
      </span>
      <span ref={textRef} className="col-start-1 row-start-1" aria-hidden>
        {reduced ? finalText : toText(0)}
      </span>
      <span className="sr-only">{finalText}</span>
    </span>
  )
}
