import { useEffect, type ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { invalidateAsync } from '@/lib/use-async'
import { useSessionState } from '../hooks'
import { loginPathFor } from '../lib/return-path'

/**
 * Guarda de ruta: sin sesión manda a `/ingresar` conservando el destino (`next`).
 * Solo oculta pantallas: cada petición la autoriza el servidor.
 */
export function RequireSession({ children, fallback }: { children: ReactNode; fallback: ReactNode }) {
  const { session, ready } = useSessionState()
  const location = useLocation()
  const signedIn = session !== undefined

  // Al perder la sesión se descartan los datos en memoria del portal (pueden ser de otra cuenta).
  useEffect(() => {
    if (ready && !signedIn) invalidateAsync('portal:')
  }, [ready, signedIn])

  if (!ready) return <>{fallback}</>
  if (!session) return <Navigate to={loginPathFor(`${location.pathname}${location.search}${location.hash}`)} replace />
  return <>{children}</>
}
