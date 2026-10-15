import { ApiError } from '@/lib/api'
import { PLACEHOLDER_IMAGES } from '@/lib/images'
import type { ProductService } from '../product-service'
import type { ProductRow } from '../types'
import {
  getStore,
  MOCK_OPTIONS,
  newId,
  requireActiveSubscription,
  requireCapacity,
  requirePermission,
  simulateNetwork,
} from './mock-state'

function notFound(): ApiError {
  return new ApiError('No encontramos ese producto en tu almacén.', { status: 404, code: 'PRODUCT_NOT_FOUND' })
}

function validation(fieldErrors: Record<string, string>): ApiError {
  return new ApiError('Hay datos por corregir.', { status: 400, code: 'VALIDATION_ERROR', fieldErrors })
}

/** Productos simulados: una fila por producto y sucursal, igual que el API real. */
export function createMockProductService(): ProductService {
  return {
    async list(storeId) {
      await simulateNetwork()
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_READ')
      return store.products.map((row) => ({ ...row }))
    },

    async options() {
      await simulateNetwork(250)
      return MOCK_OPTIONS
    },

    async create(storeId, input) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_CREATE')
      requireActiveSubscription(store)
      requireCapacity(store, 'PRODUCTS')
      const branch = store.branches.find((candidate) => candidate.id === input.branchId && candidate.state === 'ACTIVE')
      if (!branch) throw new ApiError('Esa sucursal no existe o está inactiva.', { status: 404, code: 'BRANCH_NOT_FOUND' })
      if (store.products.some((row) => row.sku && input.sku && row.sku.toLowerCase() === input.sku.toLowerCase())) {
        throw validation({ sku: 'Ya tienes un producto con este SKU.' })
      }
      const row: ProductRow = {
        id: newId('prod'),
        storeId,
        branchId: branch.id,
        branchName: branch.name,
        name: input.name,
        sku: input.sku || null,
        partNumber: input.partNumber || null,
        description: input.description,
        price: input.price,
        effectivePrice: input.price,
        discountAmount: 0,
        appliedPromotionName: null,
        quantity: input.quantity,
        manufacturerId: input.manufacturerId,
        categoryId: input.categoryId,
        state: 'INACTIVE',
        attributes: {},
        compatibilityIds: input.compatibilityIds,
        images: [],
      }
      store.products.unshift(row)
      return { ...row }
    },

    async update(storeId, branchId, productId, input) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_UPDATE')
      const rows = store.products.filter((row) => row.id === productId)
      const target = rows.find((row) => row.branchId === branchId)
      if (!target) throw notFound()
      // Datos del producto: se comparten entre sucursales. Precio y existencias: de la fila (sucursal).
      for (const row of rows) {
        if (input.name !== undefined) row.name = input.name
        if (input.sku !== undefined) row.sku = input.sku || null
        if (input.partNumber !== undefined) row.partNumber = input.partNumber || null
        if (input.description !== undefined) row.description = input.description
        if (input.manufacturerId !== undefined) row.manufacturerId = input.manufacturerId
        if (input.categoryId !== undefined) row.categoryId = input.categoryId
        if (input.compatibilityIds !== undefined) row.compatibilityIds = input.compatibilityIds
      }
      if (input.price !== undefined) {
        target.price = input.price
        target.effectivePrice = Math.max(0, input.price - target.discountAmount)
      }
      if (input.quantity !== undefined) target.quantity = input.quantity
      return { ...target }
    },

    async setPublished(storeId, productId, published) {
      await simulateNetwork(400)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_PUBLISH')
      if (published) requireActiveSubscription(store)
      const rows = store.products.filter((row) => row.id === productId)
      if (rows.length === 0) throw notFound()
      for (const row of rows) row.state = published ? 'ACTIVE' : 'INACTIVE'
    },

    async remove(storeId, productId) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_DELETE')
      if (!store.products.some((row) => row.id === productId)) throw notFound()
      store.products = store.products.filter((row) => row.id !== productId)
    },

    async uploadImages(storeId, productId, files) {
      await simulateNetwork(900)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_IMAGE_MANAGE')
      const rows = store.products.filter((row) => row.id === productId)
      if (rows.length === 0) throw notFound()
      const current = rows[0]?.images ?? []
      if (current.length + files.length > 10) {
        throw new ApiError('Un producto admite hasta 10 imágenes.', { status: 400, code: 'PRODUCT_IMAGE_LIMIT' })
      }
      for (const file of files) {
        if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
          throw new ApiError('Solo se aceptan imágenes JPG, PNG o WebP.', { status: 400, code: 'PRODUCT_IMAGE_INVALID' })
        }
        if (file.size > 5 * 1024 * 1024) {
          throw new ApiError('Cada imagen puede pesar hasta 5 MB.', { status: 413, code: 'PAYLOAD_TOO_LARGE' })
        }
      }
      const created = files.map((_, index) => ({
        id: newId('img'),
        // La simulación no sube el archivo: usa la imagen genérica del sistema.
        url: PLACEHOLDER_IMAGES.product,
        isMain: current.length === 0 && index === 0,
        displayOrder: current.length + index,
        createdAt: new Date().toISOString().slice(0, 19),
      }))
      for (const row of rows) row.images = [...current, ...created]
      return created
    },

    async setMainImage(storeId, productId, imageId) {
      await simulateNetwork(400)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_IMAGE_MANAGE')
      const rows = store.products.filter((row) => row.id === productId)
      if (rows.length === 0 || !rows[0]?.images.some((image) => image.id === imageId)) throw notFound()
      for (const row of rows) row.images = row.images.map((image) => ({ ...image, isMain: image.id === imageId }))
    },

    async removeImage(storeId, productId, imageId) {
      await simulateNetwork(400)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_IMAGE_MANAGE')
      const rows = store.products.filter((row) => row.id === productId)
      if (rows.length === 0) throw notFound()
      for (const row of rows) {
        const remaining = row.images.filter((image) => image.id !== imageId)
        const first = remaining[0]
        row.images = remaining.some((image) => image.isMain) || !first
          ? remaining
          : remaining.map((image) => ({ ...image, isMain: image.id === first.id }))
      }
    },
  }
}
