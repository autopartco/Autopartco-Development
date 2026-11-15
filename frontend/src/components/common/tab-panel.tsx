import { useEffect, useState, type ReactNode } from 'react'
import { prefersReducedMotion } from '@/lib/reveal'
import { cn } from '@/lib/utils'

/** Última pestaña mostrada por grupo; se olvida al salir de la pantalla para que la primera entrada no se anime. */
const lastIndex = new Map<string, number>()

/**
 * Contenido de una pestaña: al cambiar entra con fundido y 8 px de desplazamiento hacia la dirección del cambio
 * (índice mayor, desde la derecha; menor, desde la izquierda), 200 ms. El llamador debe cambiar su `key` con la
 * pestaña para que se remonte. Sin cambio previo o con movimiento reducido no anima.
 */
export function TabPanel({ group, index, className, children }: { group: string; index: number; className?: string; children: ReactNode }) {
  const [direction] = useState<'left' | 'right' | null>(() => {
    const previous = lastIndex.get(group)
    if (previous === undefined || previous === index || prefersReducedMotion()) return null
    return index > previous ? 'right' : 'left'
  })
  useEffect(() => {
    lastIndex.set(group, index)
    return () => {
      lastIndex.delete(group)
    }
  }, [group, index])
  return (
    <div className={cn(direction === 'right' && 'ap-tab-from-right', direction === 'left' && 'ap-tab-from-left', className)}>
      {children}
    </div>
  )
}
