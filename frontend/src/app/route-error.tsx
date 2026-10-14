import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Container } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { NotFoundContent } from '@/features/marketplace/pages/not-found-page'

/** Errores no controlados de una ruta (incluido el fallo al descargar una ruta diferida). */
export function RouteError() {
  const error = useRouteError()
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundContent />
  return (
    <Container className="flex flex-col items-start gap-4 py-16">
      <title>Algo falló | Autopartco</title>
      <h1 className="text-page-title">Esta página no cargó bien</h1>
      <p className="max-w-prose text-muted-foreground">
        Recarga la página. Si acabamos de publicar una versión nueva, la recarga descarga la más reciente.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button size="lg" className="h-11 px-5" onClick={() => window.location.reload()}>
          Recargar página
        </Button>
        <Button variant="outline" size="lg" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
          Ir al inicio
        </Button>
      </div>
    </Container>
  )
}
