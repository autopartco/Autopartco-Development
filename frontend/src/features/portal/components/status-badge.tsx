import type { ReactNode } from 'react'
import { Badge } from '@/components/ui/badge'
import type { Tone } from '../lib/labels'

/**
 * Insignia de estado del portal (§8.4): `Badge` compacto (radio 4px) con la variante semántica
 * del tono. El significado nunca depende solo del color: siempre lleva texto.
 */
export function StatusBadge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  return (
    <Badge variant={tone} className={className}>
      {children}
    </Badge>
  )
}

/** Insignia de existencias: 0 agotado, 1-3 bajo (mismos umbrales del dashboard del servidor). */
export function StockBadge({ quantity }: { quantity: number }) {
  if (quantity <= 0) return <StatusBadge tone="danger">Agotado</StatusBadge>
  if (quantity <= 3) return <StatusBadge tone="warning">Bajo · {quantity}</StatusBadge>
  return <StatusBadge tone="success">{quantity} en stock</StatusBadge>
}

/**
 * Existencias en una celda numérica de tabla (§8.5): la cantidad alineada a la derecha con cifras
 * tabulares y, si está agotado o bajo, la etiqueta con texto (no solo color).
 */
export function StockQuantity({ quantity }: { quantity: number }) {
  return (
    <span className="inline-flex items-center justify-end gap-2">
      {quantity <= 0 ? <StatusBadge tone="danger">Agotado</StatusBadge> : quantity <= 3 ? <StatusBadge tone="warning">Bajo</StatusBadge> : null}
      <span className="ap-num min-w-[3ch] font-semibold text-ap-ink">{quantity}</span>
    </span>
  )
}
