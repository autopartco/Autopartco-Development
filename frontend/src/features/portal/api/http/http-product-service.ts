import { apiRequest } from '@/lib/api'
import type { ProductService } from '../product-service'
import type { ProductImage, ProductOptions, ProductRow } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `storesuite/products` (`StoreProductController`, `StoreProductImageController`). */
export function createHttpProductService(): ProductService {
  return {
    list: (storeId) => portalRequest<ProductRow[]>(storePath(storeId, '/products')),
    // Opciones públicas del catálogo (misma fuente que el marketplace).
    options: () => apiRequest<ProductOptions>('/marketplace/product-options'),
    create: (storeId, input) =>
      portalRequest<ProductRow>(storePath(storeId, '/products'), { method: 'POST', body: input }),
    update: (storeId, branchId, productId, input) =>
      portalRequest<ProductRow>(
        storePath(storeId, `/branches/${encodeURIComponent(branchId)}/products/${encodeURIComponent(productId)}`),
        { method: 'PATCH', body: input },
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
