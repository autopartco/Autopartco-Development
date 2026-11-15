import { useEffect, useRef, useState, type ComponentPropsWithoutRef, type ElementType } from 'react'
import { observeOnce, shouldAnimateEntry } from '@/lib/reveal'
import { cn } from '@/lib/utils'

type RevealProps<T extends ElementType> = { as?: T } & Omit<ComponentPropsWithoutRef<T>, 'as'>

/**
 * Entrada de un elemento al aparecer en pantalla: opacidad 0 a 1 y 10 px a 0 en 280 ms (`.ap-reveal`, index.css),
 * escalonada 45 ms entre los que entran juntos (hasta 8 escalones). Ocurre una sola vez por montaje, usa el observer
 * compartido de `lib/reveal` y no se repite al volver con «atrás» ni con movimiento reducido.
 */
export function Reveal<T extends ElementType = 'div'>({ as, className, ...props }: RevealProps<T>) {
  const Tag: ElementType = as ?? 'div'
  const [animate] = useState(shouldAnimateEntry)
  const ref = useRef<HTMLElement>(null)

  useEffect(() => {
    const element = ref.current
    if (!animate || !element) return
    return observeOnce(element, (delay) => {
      element.style.setProperty('--ap-delay', `${delay}ms`)
      element.setAttribute('data-in', '')
    })
  }, [animate])

  return <Tag ref={ref} className={cn(animate && 'ap-reveal', className)} {...props} />
}
