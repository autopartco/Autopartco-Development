import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Card } from '@/components/ui/card'
import { cn } from '@/lib/utils'

interface StatCardProps {
  label: string
  value: ReactNode
  hint?: ReactNode
  /** Si hay destino, toda la tarjeta es un enlace (un solo objetivo táctil). */
  to?: string
  /** Resalta cifras que piden acción (pedidos pendientes, agotados). */
  tone?: 'default' | 'attention'
  className?: string
}

/**
 * Cifra clave del dashboard: etiqueta pequeña, valor grande y nota. Entra con un fundido corto
 * y, si es enlace, se eleva 2px al pasar el puntero (como las tarjetas del marketplace).
 * Ambos movimientos se anulan con `prefers-reduced-motion`.
 */
export function StatCard({ label, value, hint, to, tone = 'default', className }: StatCardProps) {
  const content = (
    <Card
      className={cn(
        'h-full gap-2 p-4 ring-1 motion-safe:animate-in motion-safe:fade-in-0 motion-safe:slide-in-from-bottom-1 motion-safe:duration-300 motion-safe:ease-out-snap',
        tone === 'attention' ? 'ring-stock-low/40' : 'ring-foreground/10',
        to &&
          'transition-[box-shadow,transform] duration-200 ease-out-snap group-hover/stat:ring-foreground/30 group-hover/stat:shadow-[0_8px_24px_-12px_rgb(0_0_0/0.18)] motion-safe:group-hover/stat:-translate-y-0.5',
        className,
      )}
    >
      <p className="text-sm font-medium text-muted-foreground">{label}</p>
      <p className="text-3xl leading-none font-extrabold tracking-[-0.035em] tabular-nums">{value}</p>
      {hint ? <p className="text-sm text-muted-foreground">{hint}</p> : null}
    </Card>
  )
  return to ? (
    <Link to={to} className="group/stat block rounded-xl">
      {content}
    </Link>
  ) : (
    content
  )
}
