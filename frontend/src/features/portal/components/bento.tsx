import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import { formatNumber } from '@/lib/format'

interface BentoTileProps {
  /** Conservado por compatibilidad: ya no hay escalonado de entrada (§10). */
  index?: number
  title: ReactNode
  /** Contexto breve a la derecha del título (periodo, unidad). */
  aside?: ReactNode
  className?: string
  children: ReactNode
}

/**
 * Bloque de contenido analítico: sección abierta con título y línea de estructura superior, sin
 * tarjeta, sombra ni animación de entrada (§6.3, §10, §20.14).
 */
export function BentoTile({ title, aside, className, children }: BentoTileProps) {
  return (
    <section className={cn('flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4', className)}>
      <header className="flex flex-wrap items-baseline justify-between gap-x-3 gap-y-1">
        <h2 className="text-base font-semibold text-ap-ink">{title}</h2>
        {aside ? <div className="text-sm text-ap-muted">{aside}</div> : null}
      </header>
      {children}
    </section>
  )
}

/** Variación frente al periodo anterior de igual duración, con texto (no solo color). */
export function Delta({ current, previous }: { current: number; previous: number }) {
  if (previous === 0) return <span className="text-sm text-ap-muted">Sin periodo anterior para comparar</span>
  const change = ((current - previous) / previous) * 100
  const rounded = Math.round(Math.abs(change))
  if (rounded === 0) return <span className="text-sm text-ap-muted">Igual que el periodo anterior</span>
  const up = change > 0
  return (
    <span className={cn('text-sm font-semibold tabular-nums', up ? 'text-ap-success' : 'text-ap-danger')}>
      {up ? 'Sube' : 'Baja'} {formatNumber(rounded)} % frente al periodo anterior
    </span>
  )
}
