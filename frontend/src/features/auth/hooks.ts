import { useCallback, useEffect, useState, useSyncExternalStore } from 'react'
import { getAuthService } from './api/auth-service'
import type { Session } from './api/types'
import { getSession, setSession, subscribeSession } from './session-store'

/** Sesión actual. Al montar, carga el servicio para recuperar la sesión simulada si la hay. */
export function useSession(): Session | undefined {
  useEffect(() => {
    void getAuthService()
  }, [])
  return useSyncExternalStore(subscribeSession, getSession, () => undefined)
}

/** Cierra la sesión (en el servidor si es real) y limpia la UI. */
export function useSignOut() {
  const [pending, setPending] = useState(false)
  const signOut = useCallback(async () => {
    setPending(true)
    try {
      const service = await getAuthService()
      await service.logout()
    } finally {
      setSession(undefined)
      setPending(false)
    }
  }, [])
  return { signOut, pending }
}
