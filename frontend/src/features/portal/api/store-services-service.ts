import { DATA_SOURCE } from '@/lib/api'
import type { ServiceCategory } from '@/features/marketplace/api/types'

/** Duraciones de franja de agenda que acepta el servidor (`StoreServiceRequest.SLOT_MINUTES`). */
export const SLOT_MINUTES_OPTIONS = [30, 60, 90, 120, 180, 240] as const

/** `StoreServiceView`: servicio del almacén en el portal. */
export interface StoreServiceItem {
  id: string
  category: ServiceCategory
  name: string
  description: string | null
  /** COP; `null` = «a cotizar». */
  priceFrom: number | null
  durationMinutes: number | null
  active: boolean
  sortOrder: number
  /** Sucursales (en cualquier estado) que lo prestan. */
  branchIds: string[]
  version: number
  imageUrl: string | null
  bookingEnabled: boolean
  slotMinutes: number
}

/** `StoreServiceRequest` (alta y reemplazo completo). */
export interface StoreServiceInput {
  category: ServiceCategory
  name: string
  description?: string | null
  priceFrom: number | null
  durationMinutes: number | null
  branchIds: string[]
  sortOrder?: number
  active?: boolean
  /** Solo en el PUT: si no coincide con la del servidor, 409 `SERVICE_VERSION_CONFLICT`. */
  version?: number
  bookingEnabled: boolean
  slotMinutes: number
}

/** Servicios del almacén (`storesuite/services/StoreServiceController`). Escribir exige `STORE_UPDATE`. */
export interface StoreServicesService {
  /** `GET /stores/{id}/services?includeInactive=true`. */
  list(storeId: string): Promise<StoreServiceItem[]>
  /** `POST /stores/{id}/services` (201). */
  create(storeId: string, input: StoreServiceInput): Promise<StoreServiceItem>
  /** `PUT /stores/{id}/services/{serviceId}`. */
  update(storeId: string, serviceId: string, input: StoreServiceInput): Promise<StoreServiceItem>
  /** `DELETE /stores/{id}/services/{serviceId}` (204): desactiva. */
  deactivate(storeId: string, serviceId: string): Promise<void>
  /** `POST …/image` (multipart `file`; PNG, JPEG o WebP, ≤ 5 MB). Devuelve el servicio. */
  uploadImage(storeId: string, serviceId: string, file: File): Promise<StoreServiceItem>
  /** Quita la imagen (`DELETE …/image`). Devuelve el servicio. */
  removeImage(storeId: string, serviceId: string): Promise<StoreServiceItem>
}

export const SERVICE_IMAGE_TYPES = ['image/png', 'image/jpeg', 'image/webp']
export const SERVICE_IMAGE_MAX_BYTES = 5 * 1024 * 1024

let servicePromise: Promise<StoreServicesService> | undefined

export function getStoreServicesService(): Promise<StoreServicesService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-store-services-service').then((module) => module.createHttpStoreServicesService())
      : import('./mock/mock-store-services-service').then((module) => module.createMockStoreServicesService())
  return servicePromise
}
