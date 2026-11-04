import { getAuthService } from '@/features/auth/api/auth-service'
import type { Session } from '@/features/auth/api/types'
import { setSession } from '@/features/auth/session-store'
import { invalidateAsync } from '@/lib/use-async'

/**
 * Tras configurar el almacén la cuenta cambia de rol: el backend emite tokens nuevos y deja su
 * cookie de refresh. Se lee la sesión de nuevo (en vez de conservar la vieja) y se descartan las
 * consultas en caché de solicitudes y almacenes.
 */
export async function replaceSessionAfterSetup(): Promise<Session | undefined> {
  const auth = await getAuthService()
  const session = await auth.restore()
  if (session) setSession(session)
  invalidateAsync('seller:')
  invalidateAsync('portal:')
  return session
}
