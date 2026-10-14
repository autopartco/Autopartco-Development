/**
 * Copia en TypeScript de los DTO públicos del backend (solo campos reales).
 * Origen: backend/src/main/java/com/autopartco/backend/core/catalog/ y core/organization/.
 * UUID -> string, BigDecimal -> number, LocalDateTime -> string ISO.
 */

/** core/catalog/ProductImageResponse.java */
export interface ProductImageResponseDto {
  id: string
  /** URL pública absoluta del almacenamiento. */
  url: string
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
  categoryId: string | null
  state: string | null
  attributes: Record<string, unknown> | null
  compatibilityIds: string[] | null
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

export interface CompatibilityOptionDto {
  id: string
  make: string
  model: string
  version: string | null
}

export interface ProductOptionsResponseDto {
  manufacturers: ManufacturerOptionDto[]
  categories: CategoryOptionDto[]
  compatibilities: CompatibilityOptionDto[]
}

/** core/organization/StoreResponse.java */
export interface StoreResponseDto {
  id: string
  businessName: string
  nit: string
  businessEmail: string
  state: string | null
}
