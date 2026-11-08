import { useEffect, useState } from 'react'
import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Container } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { NotFoundContent } from '@/features/marketplace/pages/not-found-page'
import { canReloadForStaleBuild, isStaleBuildError, reloadOnceForStaleBuild } from '@/lib/stale-build'

type ErrorCopy = { index: string; title: string; body: string }

function describe(error: unknown): ErrorCopy {
  if (isStaleBuildError(error)) {
    return {
      index: 'Error / Versión nueva',
      title: 'Publicamos una versión nueva',
      body: 'Esta pestaña tenía la versión anterior. Recarga la página para descargar la más reciente.',
    }
  }
  if (isRouteErrorResponse(error) && (error.status === 401 || error.status === 403)) {
    return {
      index: `Error / ${error.status}`,
      title: error.status === 401 ? 'Tu sesión terminó' : 'No tienes acceso a esta página',
      body:
        error.status === 401
          ? 'Ingresa de nuevo para continuar.'
          : 'Si crees que deberías verla, ingresa con la cuenta correcta o pide acceso al administrador del almacén.',
    }
  }
  if (isRouteErrorResponse(error) && error.status >= 500) {
    return {
      index: `Error / ${error.status}`,
      title: 'El servicio no respondió',
      body: 'Es un problema de nuestro lado. Espera un momento y recarga la página.',
    }
  }
  return {
    index: 'Error / Carga de página',
    title: 'Esta página no cargó bien',
    body: 'Recarga la página. Si el problema sigue, vuelve al inicio e inténtalo de nuevo.',
  }
}

/**
 * Errores no controlados de una ruta: 404, permisos, fallos del servidor, código roto
 * y archivos de una versión ya reemplazada (en ese caso recarga sola una vez).
 */
export function RouteError() {
  const error = useRouteError()
  const stale = isStaleBuildError(error)
  // Durante la recarga automática no se muestra nada para evitar un parpadeo del error.
  const [reloading] = useState(() => stale && canReloadForStaleBuild())

  useEffect(() => {
    if (reloading) reloadOnceForStaleBuild()
  }, [reloading])

  if (isRouteErrorResponse(error) && error.status === 404) return <NotFoundContent />
  if (reloading) return null

  const copy = describe(error)
  const needsLogin = isRouteErrorResponse(error) && error.status === 401
  return (
    <Container className="flex flex-col items-start gap-3 py-12 sm:py-16">
      <title>{`${copy.title} | Autopartco`}</title>
      <p className="ap-index">{copy.index}</p>
      <h1 className="text-page-title text-ap-ink">{copy.title}</h1>
      <p className="max-w-prose text-ap-muted">{copy.body}</p>
      <div className="mt-2 flex flex-wrap gap-2">
        {needsLogin ? (
          <Button nativeButton={false} render={<Link to="/ingresar" />}>
            Ingresar
          </Button>
        ) : (
          <Button onClick={() => window.location.reload()}>Recargar página</Button>
        )}
        <Button variant="outline" nativeButton={false} render={<a href="/" />}>
          Ir al inicio
        </Button>
      </div>
    </Container>
  )
}
