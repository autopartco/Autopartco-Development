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

/** Cifra clave del dashboard: etiqueta pequeña, valor grande y nota. */
export function StatCard({ label, value, hint, to, tone = 'default', className }: StatCardProps) {
  const content = (
    <Card
      className={cn(
        'h-full gap-2 p-4 ring-1',
        tone === 'attention' ? 'ring-stock-low/40' : 'ring-foreground/10',
        to && 'transition-shadow hover:ring-foreground/30',
        className,
      )}
    >
      <p className="text-sm font-medium text-muted-foreground">{label}</p>
      <p className="text-3xl leading-none font-extrabold tracking-tight tabular-nums">{value}</p>
      {hint ? <p className="text-sm text-muted-foreground">{hint}</p> : null}
    </Card>
  )
  return to ? (
    <Link to={to} className="block rounded-xl">
      {content}
    </Link>
  ) : (
    content
  )
}
