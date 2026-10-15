import { useContext } from 'react'
import { HeaderAccount } from '@/features/auth/components/account-menu'
import type { StorePermission } from '../api/types'
import { PortalContext } from '../context'

/**
 * Menú de cuenta del portal: el mismo de la cabecera del marketplace (opciones según el tipo
 * de cuenta, en `features/auth/lib/account-menu-items.ts`), con las secciones del almacén abierto.
 * En `/portal` (sin almacén elegido) no hay contexto y solo ofrece "Mis almacenes".
 */
export function PortalAccountMenu() {
  const portal = useContext(PortalContext)
  return (
    <HeaderAccount
      area="portal"
      storeId={portal?.storeId}
      can={portal?.permissionsReady ? (permission) => portal.can(permission as StorePermission) : undefined}
    />
  )
}
