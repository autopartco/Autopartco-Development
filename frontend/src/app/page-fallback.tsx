import { Container } from '@/components/common/page-header'
import { Skeleton } from '@/components/ui/skeleton'

/** Mientras llega el código de una ruta diferida: esqueleto sobrio con la forma de `PageIntro`. */
export function PageFallback() {
  return (
    <Container className="py-8 sm:py-10">
      <span className="sr-only" role="status">
        Cargando página
      </span>
      <div className="flex flex-col gap-3" aria-hidden>
        <Skeleton className="h-3 w-40" />
        <Skeleton className="h-8 w-2/3 max-w-lg" />
        <Skeleton className="h-4 w-1/2 max-w-md" />
        <div className="mt-5 flex flex-col gap-2">
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-full" />
          <Skeleton className="h-12 w-4/5" />
        </div>
      </div>
    </Container>
  )
}
