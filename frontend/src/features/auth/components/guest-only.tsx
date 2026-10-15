import type { ReactNode } from 'react'
import { Navigate } from 'react-router'
import { useSessionState } from '../hooks'

/**
 * Para pantallas solo de invitados (registro, recuperar contraseña): con sesión activa lleva al
 * inicio, porque crear otra cuenta o recuperar una contraseña ya no aplica.
 */
export function GuestOnly({ children, fallback = null }: { children: ReactNode; fallback?: ReactNode }) {
  const { session, ready } = useSessionState()

  if (!ready) return <>{fallback}</>
  if (session) return <Navigate to="/" replace />
  return <>{children}</>
}
