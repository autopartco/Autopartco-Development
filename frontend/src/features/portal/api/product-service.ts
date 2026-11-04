import { DATA_SOURCE } from '@/lib/api'
import type {
  ManufacturerCreateInput,
  ManufacturerMatch,
  ProductCreateInput,
  ProductImage,
  ProductOptions,
  ProductRow,
  ProductUpdateInput,
  StoreManufacturer,
  VehicleMake,
  VehicleModel,
  VehicleVersion,
} from './types'

/** Productos del almacén (una fila por producto y sucursal) e imágenes. */
export interface ProductService {
  /** `GET /stores/{id}/products`. */
  list(storeId: string): Promise<ProductRow[]>
  /** `GET /marketplace/product-options` (público): fabricantes y categorías. */
  options(): Promise<ProductOptions>
  /** `GET /marketplace/vehicles/makes` (público, cacheable). */
  listMakes(): Promise<VehicleMake[]>
  /** `GET /marketplace/vehicles/makes/{makeId}/models` (público, cacheable). */
  listModels(makeId: string): Promise<VehicleModel[]>
  /** `GET /marketplace/vehicles/models/{modelId}/versions` (público, cacheable). */
  listVersions(modelId: string): Promise<VehicleVersion[]>
  /** `GET /stores/{id}/manufacturers`: aprobados y pendientes propios, por nombre. */
  listManufacturers(storeId: string): Promise<StoreManufacturer[]>
  /** `GET /stores/{id}/manufacturers/search?q=`: máx. 8, tolera errores ortográficos; con menos de 2 caracteres devuelve `[]`. */
  searchManufacturers(storeId: string, q: string): Promise<ManufacturerMatch[]>
  /**
   * `POST /stores/{id}/manufacturers`. Reutiliza el existente (200) o crea uno pendiente (201).
   * Lanza `ApiError` 409 `MANUFACTURER_SIMILAR` (con `details: [{id,name}]`) si hay parecidos y no se envió `confirmNew`.
   */
  createManufacturer(storeId: string, input: ManufacturerCreateInput): Promise<StoreManufacturer>
  /**
   * `POST /stores/{id}/products` (multipart atómico: `data` + `images`). Todo o nada.
   * Errores: 409 `PRODUCT_NOT_PUBLISHABLE` (`details: PublishBlocker[]`), 409 `MANUFACTURER_SIMILAR`
   * (`details: [{id,name}]`), 400 `VEHICLE_RULE_INVALID` (`details: VehicleRuleErrorDetail[]`).
   */
  create(storeId: string, input: ProductCreateInput, images?: File[]): Promise<ProductRow>
  /** `PATCH /stores/{id}/branches/{branchId}/products/{productId}` (multipart atómico: `data` + `newImages`). */
  update(storeId: string, branchId: string, productId: string, input: ProductUpdateInput, newImages?: File[]): Promise<ProductRow>
  /** `PATCH /stores/{id}/products/{productId}/publication`. */
  setPublished(storeId: string, productId: string, published: boolean): Promise<void>
  /** `DELETE /stores/{id}/products/{productId}`. */
  remove(storeId: string, productId: string): Promise<void>
  /** `POST …/products/{productId}/images` (multipart, campo `file`). */
  uploadImages(storeId: string, productId: string, files: File[]): Promise<ProductImage[]>
  /** `PATCH …/images/{imageId}/main`. */
  setMainImage(storeId: string, productId: string, imageId: string): Promise<void>
  /** `DELETE …/images/{imageId}`. */
  removeImage(storeId: string, productId: string, imageId: string): Promise<void>
}

let servicePromise: Promise<ProductService> | undefined

export function getProductService(): Promise<ProductService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-product-service').then((module) => module.createHttpProductService())
      : import('./mock/mock-product-service').then((module) => module.createMockProductService())
  return servicePromise
}
