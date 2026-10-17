/**
 * Copia en TypeScript de los DTO públicos del backend (solo campos reales).
 * Origen: backend/src/main/java/com/autopartco/backend/core/catalog/ y core/organization/.
 * UUID -> string, BigDecimal -> number, LocalDateTime -> string ISO.
 */

/** core/catalog/ProductImageResponse.java */
export interface ProductImageResponseDto {
  id: string
  /** Original (detalle). */
  url: string
  /** Variante pequeña (400 px). Puede faltar en servidores antiguos. */
  thumbUrl?: string | null
  /** Variante de tarjeta (800 px). */
  cardUrl?: string | null
  /** Componente `isMain` del record; algunas configuraciones de Jackson lo serializan como `main`. */
  isMain?: boolean
  main?: boolean
  displayOrder: number
  createdAt: string
}

/** core/catalog/ProductResponse.java. Representa un listado: producto en una sucursal. */
export interface ProductResponseDto {
  id: string
  storeId: string
  branchId: string | null
  branchName: string | null
  name: string
  partNumber: string | null
  description: string | null
  price: number
  effectivePrice: number | null
  discountAmount: number | null
  appliedPromotionName: string | null
  quantity: number | null
  manufacturerId: string | null
  /** Visible aunque el fabricante esté pendiente de aprobación. */
  manufacturerName: string | null
  manufacturerApproved: boolean | null
  categoryId: string | null
  state: string | null
  attributes: Record<string, unknown> | null
  /** Producto que no depende del vehículo (sin reglas). */
  universal: boolean | null
  vehicleRules: VehicleRuleResponseDto[] | null
  images: ProductImageResponseDto[] | null
}

/** core/catalog/ProductOptionsResponse.java */
export interface ManufacturerOptionDto {
  id: string
  name: string
}

export interface CategoryOptionDto {
  id: string
  code: string
}

export interface ProductOptionsResponseDto {
  manufacturers: ManufacturerOptionDto[]
  categories: CategoryOptionDto[]
}

/** Regla de compatibilidad con nombres; los niveles «todos» vienen en `null`. */
export interface VehicleRuleResponseDto {
  makeId: string
  makeName: string
  modelId: string | null
  modelName: string | null
  versionId: string | null
  versionLabel: string | null
}

/** GET /marketplace/vehicles/makes, /makes/{id}/models */
export interface VehicleNameOptionDto {
  id: string
  name: string
}

/** GET /marketplace/vehicles/models/{id}/versions */
export interface VehicleVersionOptionDto {
  id: string
  label: string
}

/** core/organization/StoreResponse.java */
export interface StoreResponseDto {
  id: string
  businessName: string
  nit: string
  businessEmail: string
  state: string | null
}
