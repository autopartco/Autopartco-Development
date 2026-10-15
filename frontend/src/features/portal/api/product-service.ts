import { DATA_SOURCE } from '@/lib/api'
import type { ProductCreateInput, ProductImage, ProductOptions, ProductRow, ProductUpdateInput } from './types'

/** Productos del almacén (una fila por producto y sucursal) e imágenes. */
export interface ProductService {
  /** `GET /stores/{id}/products`. */
  list(storeId: string): Promise<ProductRow[]>
  /** `GET /marketplace/product-options` (público): fabricantes, categorías y vehículos compatibles. */
  options(): Promise<ProductOptions>
  /** `POST /stores/{id}/products`. */
  create(storeId: string, input: ProductCreateInput): Promise<ProductRow>
  /** `PATCH /stores/{id}/branches/{branchId}/products/{productId}`. */
  update(storeId: string, branchId: string, productId: string, input: ProductUpdateInput): Promise<ProductRow>
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
