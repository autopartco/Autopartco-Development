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
