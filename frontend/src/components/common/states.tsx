import type { ReactNode } from 'react'
import { RotateCwIcon, TriangleAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

interface ErrorStateProps {
  title?: string
  description?: string
  onRetry?: () => void
  className?: string
}

/** Error con acción clara para reintentar. */
export function ErrorState({
  title = 'No pudimos cargar esta información',
  description = 'Revisa tu conexión e inténtalo de nuevo. Si el problema sigue, vuelve en unos minutos.',
  onRetry,
  className,
}: ErrorStateProps) {
  return (
    <div role="alert" className={cn('flex flex-col items-start gap-3 rounded-lg border border-destructive/25 bg-destructive/5 p-5', className)}>
      <div className="flex items-center gap-2 font-semibold text-destructive">
        <TriangleAlertIcon className="size-5" aria-hidden />
        <p>{title}</p>
      </div>
      <p className="max-w-prose text-sm text-muted-foreground">{description}</p>
      {onRetry ? (
        <Button variant="outline" size="lg" onClick={onRetry}>
          <RotateCwIcon aria-hidden />
          Reintentar
        </Button>
      ) : null}
    </div>
  )
}

interface EmptyStateProps {
  icon?: ReactNode
  title: string
  description?: ReactNode
  children?: ReactNode
  className?: string
}

/** Estado vacío: explica qué pasó y ofrece el siguiente paso. */
export function EmptyState({ icon, title, description, children, className }: EmptyStateProps) {
  return (
    <div className={cn('flex flex-col items-start gap-4 rounded-lg bg-concrete px-6 py-10 sm:px-10', className)}>
      {icon ? <div className="text-muted-foreground [&_svg]:size-8" aria-hidden>{icon}</div> : null}
      <div className="flex flex-col gap-2">
        <h2 className="text-xl font-semibold tracking-tight">{title}</h2>
        {description ? <div className="max-w-prose text-muted-foreground">{description}</div> : null}
      </div>
      {children}
    </div>
  )
}
