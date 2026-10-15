import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import type { Tone } from '../lib/labels'

const TONE_CLASS: Record<Tone, { box: string; dot: string }> = {
  neutral: { box: 'bg-concrete text-foreground', dot: 'bg-muted-foreground' },
  success: { box: 'bg-stock/10 text-stock', dot: 'bg-stock' },
  warning: { box: 'bg-stock-low/10 text-stock-low', dot: 'bg-stock-low' },
  danger: { box: 'bg-destructive/10 text-destructive', dot: 'bg-destructive' },
  info: { box: 'bg-recommended-soft text-recommended', dot: 'bg-recommended' },
}

/**
 * Insignia de estado. El significado nunca depende solo del color: siempre lleva texto
 * (y un punto decorativo).
 */
export function StatusBadge({ tone = 'neutral', children, className }: { tone?: Tone; children: ReactNode; className?: string }) {
  const classes = TONE_CLASS[tone]
  return (
    <span
      className={cn(
        'inline-flex h-6 w-fit items-center gap-1.5 rounded-full px-2.5 text-xs font-semibold whitespace-nowrap',
        classes.box,
        className,
      )}
    >
      <span aria-hidden className={cn('size-1.5 rounded-full', classes.dot)} />
      {children}
    </span>
  )
}

/** Insignia de existencias: 0 agotado, 1-3 bajo (mismos umbrales del dashboard del servidor). */
export function StockBadge({ quantity }: { quantity: number }) {
  if (quantity <= 0) return <StatusBadge tone="danger">Agotado</StatusBadge>
  if (quantity <= 3) return <StatusBadge tone="warning">Bajo · {quantity}</StatusBadge>
  return <StatusBadge tone="success">{quantity} en stock</StatusBadge>
}
