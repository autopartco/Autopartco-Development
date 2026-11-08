import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Container } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { NotFoundContent } from '@/features/marketplace/pages/not-found-page'

/** Errores no controlados de una ruta (incluido el fallo al descargar una ruta diferida). */
export function RouteError() {
  const error = useRouteError()
  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundContent />
  return (
    <Container className="flex flex-col items-start gap-3 py-12 sm:py-16">
      <title>Algo falló | Autopartco</title>
      <p className="ap-index">Error / Carga de página</p>
      <h1 className="text-page-title text-ap-ink">Esta página no cargó bien</h1>
      <p className="max-w-prose text-ap-muted">
        Recarga la página. Si acabamos de publicar una versión nueva, la recarga descarga la más reciente.
      </p>
      <div className="mt-2 flex flex-wrap gap-2">
        <Button onClick={() => window.location.reload()}>Recargar página</Button>
        <Button variant="outline" nativeButton={false} render={<Link to="/" />}>
          Ir al inicio
        </Button>
      </div>
    </Container>
  )
}
