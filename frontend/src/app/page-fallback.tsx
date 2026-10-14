import { Container } from '@/components/common/page-header'
import { Skeleton } from '@/components/ui/skeleton'

/** Mientras llega el código de una ruta diferida. */
export function PageFallback() {
  return (
    <Container className="py-10">
      <span className="sr-only" role="status">
        Cargando página
      </span>
      <div className="flex flex-col gap-4" aria-hidden>
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-9 w-2/3 max-w-xl" />
        <Skeleton className="h-64 w-full" />
      </div>
    </Container>
  )
}
