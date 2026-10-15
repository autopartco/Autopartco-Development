import { useCallback, useEffect, useState, useSyncExternalStore } from 'react'
import { getAuthService } from './api/auth-service'
import type { Session } from './api/types'
import { invalidateAsync } from '@/lib/use-async'
import { clearPendingCheckout } from '@/features/portal/lib/pending-checkout'
import { forgetPortalStore } from './lib/account-menu-items'
import { getSession, setSession, subscribeSession } from './session-store'

/** Sesión actual. Al montar, carga el servicio para recuperar la sesión simulada si la hay. */
export function useSession(): Session | undefined {
  useEffect(() => {
    void getAuthService()
  }, [])
  return useSyncExternalStore(subscribeSession, getSession, () => undefined)
}

/**
 * Sesión más un indicador `ready`: `false` hasta que el servicio de cuentas cargó y pudo
 * recuperar la sesión. Las guardas de ruta esperan a `ready` para no mandar a login por error.
 */
export function useSessionState(): { session: Session | undefined; ready: boolean } {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    let active = true
    getAuthService().then(
      () => active && setReady(true),
      () => active && setReady(true),
    )
    return () => {
      active = false
    }
  }, [])
  const session = useSyncExternalStore(subscribeSession, getSession, () => undefined)
  return { session, ready }
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
      forgetPortalStore()
      invalidateAsync('seller:')
      clearPendingCheckout()
      setSession(undefined)
      setPending(false)
    }
  }, [])
  return { signOut, pending }
}
