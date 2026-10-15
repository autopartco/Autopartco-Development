import { Skeleton } from '@/components/ui/skeleton'

/** Marcador de carga de listas y tablas. El texto para lectores se anuncia una vez. */
export function ListSkeleton({ rows = 5, label = 'Cargando' }: { rows?: number; label?: string }) {
  return (
    <div role="status" className="flex flex-col gap-3">
      <span className="sr-only">{label}…</span>
      <div aria-hidden className="flex flex-col gap-3">
        <Skeleton className="h-10 w-full max-w-sm" />
        {Array.from({ length: rows }, (_, index) => (
          <Skeleton key={index} className="h-14 w-full" />
        ))}
      </div>
    </div>
  )
}

/** Marcador de carga de una cuadrícula de tarjetas (dashboard, planes). */
export function CardsSkeleton({ count = 4, label = 'Cargando' }: { count?: number; label?: string }) {
  return (
    <div role="status">
      <span className="sr-only">{label}…</span>
      <div aria-hidden className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {Array.from({ length: count }, (_, index) => (
          <Skeleton key={index} className="h-32 w-full" />
        ))}
      </div>
    </div>
  )
}
