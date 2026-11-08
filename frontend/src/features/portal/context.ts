import { createContext, useContext } from 'react'
import type { Branch, MyStore, StorePermission, Subscription } from './api/types'

export interface PortalContextValue {
  storeId: string
  /** Tiendas de la persona (para el selector). */
  stores: MyStore[]
  store: MyStore | undefined
  /** `true` cuando ya llegaron los permisos del servidor. */
  permissionsReady: boolean
  /** Solo oculta acciones: el servidor vuelve a autorizar cada petición. */
  can: (permission: StorePermission) => boolean
  subscription: Subscription | undefined
  /** Error al cargar la suscripción (si falló); la insignia y el aviso se ocultan, la pantalla de plan lo explica. */
  subscriptionError: unknown
  /** Vuelve a pedir la suscripción (tras cambios que modifican el uso del plan). */
  reloadSubscription: () => void
  /** Reemplaza la suscripción con la que devolvió el servidor (tras un pago). */
  applySubscription: (subscription: Subscription) => void
  /**
   * Si `error` es de plan (límite, función no incluida, suscripción vencida) abre el diálogo
   * de mejora y devuelve `true`; si no, `false` para que la pantalla muestre su propio mensaje.
   */
  promptUpgrade: (error: unknown) => boolean
  /** Descarta los datos en memoria de esta tienda y recarga la suscripción. */
  invalidateStore: () => void
  /** Sucursales activas del almacén (vacío sin `BRANCH_READ` o mientras cargan). */
  activeBranches: Branch[]
  /**
   * Sucursal activa de la barra de contexto; `undefined` = todas las sucursales. Es el filtro por defecto de
   * resumen, inventario, traslados y analítica. Solo existe con dos o más sucursales activas.
   */
  branchId: string | undefined
  /** Nombre de la sucursal activa (`undefined` = todas). */
  branchName: string | undefined
  /** Cambia la sucursal activa y la recuerda para esta persona y este almacén. */
  setBranchId: (branchId: string | undefined) => void
  /** `true` cuando ya se sabe qué sucursal está activa (sucursales cargadas o sin permiso para verlas). */
  branchReady: boolean
}

export const PortalContext = createContext<PortalContextValue | undefined>(undefined)

export function usePortal(): PortalContextValue {
  const value = useContext(PortalContext)
  if (!value) throw new Error('usePortal debe usarse dentro del portal del almacén.')
  return value
}
