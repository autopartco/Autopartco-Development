import { apiRequest } from '@/lib/api'
import type { ProductService } from '../product-service'
import type {
  ManufacturerMatch,
  ProductImage,
  ProductOptions,
  ProductRow,
  StoreManufacturer,
  VehicleMake,
  VehicleModel,
  VehicleVersion,
} from '../types'
import { portalRequest, storePath } from './portal-http'

/** Multipart del backend: `data` es un Blob `application/json`; los archivos van en `field`. */
function productForm(data: unknown, field: 'images' | 'newImages', files: File[] = []): FormData {
  const form = new FormData()
  form.append('data', new Blob([JSON.stringify(data)], { type: 'application/json' }))
  for (const file of files) form.append(field, file, file.name)
  return form
}

/** `storesuite/products` (`StoreProductController`, `StoreProductImageController`). */
export function createHttpProductService(): ProductService {
  return {
    list: (storeId) => portalRequest<ProductRow[]>(storePath(storeId, '/products')),
    // Opciones públicas del catálogo (misma fuente que el marketplace).
    options: () => apiRequest<ProductOptions>('/marketplace/product-options'),
    listMakes: () => apiRequest<VehicleMake[]>('/marketplace/vehicles/makes'),
    listModels: (makeId) => apiRequest<VehicleModel[]>(`/marketplace/vehicles/makes/${encodeURIComponent(makeId)}/models`),
    listVersions: (modelId) => apiRequest<VehicleVersion[]>(`/marketplace/vehicles/models/${encodeURIComponent(modelId)}/versions`),
    listManufacturers: (storeId) => portalRequest<StoreManufacturer[]>(storePath(storeId, '/manufacturers')),
    searchManufacturers: (storeId, q) =>
      q.trim().length < 2
        ? Promise.resolve([])
        : portalRequest<ManufacturerMatch[]>(storePath(storeId, `/manufacturers/search?q=${encodeURIComponent(q.trim())}`)),
    createManufacturer: (storeId, input) =>
      portalRequest<StoreManufacturer>(storePath(storeId, '/manufacturers'), { method: 'POST', body: input }),
    create: (storeId, input, images) =>
      portalRequest<ProductRow>(storePath(storeId, '/products'), { method: 'POST', body: productForm(input, 'images', images) }),
    update: (storeId, branchId, productId, input, newImages) =>
      portalRequest<ProductRow>(
        storePath(storeId, `/branches/${encodeURIComponent(branchId)}/products/${encodeURIComponent(productId)}`),
        { method: 'PATCH', body: productForm(input, 'newImages', newImages) },
      ),
    async setPublished(storeId, productId, published) {
      await portalRequest(storePath(storeId, `/products/${encodeURIComponent(productId)}/publication`), {
        method: 'PATCH',
        body: { published },
      })
    },
    async remove(storeId, productId) {
      await portalRequest(storePath(storeId, `/products/${encodeURIComponent(productId)}`), { method: 'DELETE' })
    },
    uploadImages(storeId, productId, files) {
      const form = new FormData()
      for (const file of files) form.append('files', file, file.name)
      return portalRequest<ProductImage[]>(storePath(storeId, `/products/${encodeURIComponent(productId)}/images`), {
        method: 'POST',
        body: form,
      })
    },
    async setMainImage(storeId, productId, imageId) {
      await portalRequest(
        storePath(storeId, `/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}/main`),
        { method: 'PATCH' },
      )
    },
    async removeImage(storeId, productId, imageId) {
      await portalRequest(
        storePath(storeId, `/products/${encodeURIComponent(productId)}/images/${encodeURIComponent(imageId)}`),
        { method: 'DELETE' },
      )
    },
  }
}
