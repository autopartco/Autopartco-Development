import { DATA_SOURCE } from '@/lib/api'
import type { MyStore, StorePermission, StoreProfile, StoreProfileUpdate } from './types'

/** Tiendas de la persona, permisos y perfil del almacén. */
export interface PortalService {
  /** `GET /me/stores`. */
  myStores(): Promise<MyStore[]>
  /** `GET /stores/{id}/my-permissions`. Solo sirve para ocultar acciones: el servidor decide. */
  permissions(storeId: string): Promise<StorePermission[]>
  /** `GET /stores/{id}/profile`. */
  profile(storeId: string): Promise<StoreProfile>
  /** `PATCH /stores/{id}`. */
  updateProfile(storeId: string, input: StoreProfileUpdate): Promise<void>
}

let servicePromise: Promise<PortalService> | undefined

/** Misma selección `mock`/`http` que el resto de la app. */
export function getPortalService(): Promise<PortalService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-portal-service').then((module) => module.createHttpPortalService())
      : import('./mock/mock-portal-service').then((module) => module.createMockPortalService())
  return servicePromise
}
