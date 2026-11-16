import { useSession } from '@/features/auth/hooks'
import type { PlatformPermission } from '../api/console-types'

/** `ROOT` exige ser root; el resto, el permiso de plataforma que lleva el token del miembro del equipo. */
export type SectionAccess = PlatformPermission | 'ROOT'

export interface AdminAccess {
  root: boolean
  staff: boolean
  /** Puede entrar a la consola (root o miembro del equipo). */
  allowed: boolean
  can: (access: SectionAccess) => boolean
}

/**
 * Acceso a la consola según el claim `roles` del token: `ROLE_ROOT` puede todo; `ROLE_STAFF` solo lo que traen sus
 * códigos `PLATFORM_*`. Solo decide qué se muestra: el servidor comprueba cada petición.
 */
export function useAdminAccess(): AdminAccess {
  const roles = useSession()?.roles ?? []
  return adminAccessOf(roles)
}

export function adminAccessOf(roles: readonly string[]): AdminAccess {
  const root = roles.includes('ROLE_ROOT')
  const staff = roles.includes('ROLE_STAFF')
  const granted = new Set(roles)
  return {
    root,
    staff,
    allowed: root || staff,
    can: (access) => root || (access !== 'ROOT' && staff && granted.has(access)),
  }
}
