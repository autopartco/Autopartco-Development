import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ChevronRightIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface StatCardProps {
  label: string
  value: ReactNode
  hint?: ReactNode
  /** Si hay destino, la cifra entera es un enlace (un solo objetivo táctil). */
  to?: string
  /** Cifras que piden acción (pedidos pendientes, agotados): línea de acento ámbar y texto. */
  tone?: 'default' | 'attention'
  /** Versión densa para pantallas con muchas cifras, como los reportes. */
  compact?: boolean
  className?: string
}

/**
 * Cifra con su etiqueta y contexto. No es una tarjeta decorativa: bloque abierto con una línea de
 * estructura a la izquierda, sin sombra ni animación (§6.3, §13). Úsala solo si la cifra ayuda a decidir.
 */
export function StatCard({ label, value, hint, to, tone = 'default', compact = false, className }: StatCardProps) {
  const content = (
    <div
      className={cn(
        'flex h-full flex-col border-l-2 pl-3',
        compact ? 'gap-0.5 py-1' : 'gap-1 py-1.5',
        tone === 'attention' ? 'border-ap-warning' : 'border-ap-line-strong',
        className,
      )}
    >
      <p className="flex items-center gap-1 text-sm font-medium text-ap-muted">
        {label}
        {to ? <ChevronRightIcon className="size-4 text-ap-steel transition-colors group-hover/stat:text-ap-ink" aria-hidden /> : null}
      </p>
      <p className={cn('ap-price leading-tight text-ap-ink', compact ? 'text-lg' : 'text-2xl')}>{value}</p>
      {hint ? <p className={cn(tone === 'attention' ? 'font-medium text-ap-warning' : 'text-ap-muted', compact ? 'text-xs' : 'text-sm')}>{hint}</p> : null}
    </div>
  )
  return to ? (
    <Link to={to} className="group/stat block rounded-sm hover:bg-ap-paper">
      {content}
    </Link>
  ) : (
    content
  )
}
