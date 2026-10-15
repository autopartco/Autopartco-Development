import { Navigate, useLocation } from 'react-router'

/** Redirige una ruta antigua a su destino canónico conservando la query (p. ej. `?token=`). */
export function LegacyRedirect({ to }: { to: string }) {
  const { search, hash } = useLocation()
  return <Navigate to={`${to}${search}${hash}`} replace />
}
