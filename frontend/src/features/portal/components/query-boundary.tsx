import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { RotateCwIcon, ShieldAlertIcon, TriangleAlertIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import type { AsyncResult } from '@/lib/use-async'
import { describeError } from '../lib/errors'
import { ListSkeleton } from './list-skeleton'

/** Error de carga con causa y siguiente paso: reintentar, volver a login o cambiar de almacén. */
export function QueryError({ error, what, onRetry, className }: { error: unknown; what?: string; onRetry?: () => void; className?: string }) {
  const view = describeError(error, what)
  const Icon = view.kind === 'forbidden' ? ShieldAlertIcon : TriangleAlertIcon
  return (
    <div
      role="alert"
      className={cn('flex flex-col items-start gap-3 rounded-lg border border-destructive/25 bg-destructive/5 p-5', className)}
    >
      <div className="flex items-center gap-2 font-semibold text-destructive">
        <Icon className="size-5" aria-hidden />
        <p>{view.title}</p>
      </div>
      <p className="max-w-prose text-sm text-foreground/85">{view.description}</p>
      <div className="flex flex-wrap gap-2">
        {view.retryable && onRetry ? (
          <Button variant="outline" className="h-11 px-4" onClick={onRetry}>
            <RotateCwIcon aria-hidden />
            Reintentar
          </Button>
        ) : null}
        {view.kind === 'forbidden' ? (
          <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to="/portal" />}>
            Ver mis almacenes
          </Button>
        ) : null}
        {view.kind === 'unauthorized' ? (
          <Button className="h-11 px-4" nativeButton={false} render={<Link to="/ingresar" />}>
            Ingresar
          </Button>
        ) : null}
      </div>
    </div>
  )
}

interface QueryBoundaryProps<T> {
  result: AsyncResult<T>
  /** Qué se estaba cargando, para el mensaje de error ("tus productos"). */
  what: string
  skeleton?: ReactNode
  /** Mantiene los datos anteriores mientras se recarga la misma consulta (tras una mutación). */
  keepPrevious?: boolean
  children: (data: T) => ReactNode
}

/** Estados de carga, error y éxito de una consulta de `useAsync`, de forma uniforme. */
export function QueryBoundary<T>({ result, what, skeleton, keepPrevious, children }: QueryBoundaryProps<T>) {
  if (result.status === 'error') return <QueryError error={result.error} what={what} onRetry={result.retry} />
  if (result.data !== undefined && (result.status === 'success' || keepPrevious)) {
    return <div aria-busy={result.status === 'loading' || undefined}>{children(result.data)}</div>
  }
  return <>{skeleton ?? <ListSkeleton label={`Cargando ${what}`} />}</>
}
