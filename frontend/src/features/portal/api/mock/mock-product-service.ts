import { ApiError } from '@/lib/api'
import { PLACEHOLDER_IMAGES } from '@/lib/images'
import type { ProductService } from '../product-service'
import type {
  Branch,
  ProductBulkAction,
  ProductBulkInput,
  ProductBulkPriceMode,
  ProductBulkResult,
  ProductCreateInput,
  ProductImage,
  ProductRow,
  PublishBlocker,
  StoreManufacturer,
  StorePermission,
  VehicleRule,
  VehicleRuleInput,
} from '../types'
import {
  type MockStore,
  getStore,
  MOCK_OPTIONS,
  MOCK_VEHICLES,
  mockRuleFor,
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

const normalize = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim()

function distance(a: string, b: string): number {
  let previous = Array.from({ length: b.length + 1 }, (_, index) => index)
  for (let i = 1; i <= a.length; i += 1) {
    const current = [i]
    for (let j = 1; j <= b.length; j += 1) {
      current[j] = Math.min((previous[j] ?? 0) + 1, (current[j - 1] ?? 0) + 1, (previous[j - 1] ?? 0) + (a[i - 1] === b[j - 1] ? 0 : 1))
    }
    previous = current
  }
  return previous[b.length] ?? 0
}

/** Similitud simple 0..1: contención o distancia de edición relativa al nombre más largo. */
function similarity(query: string, name: string): number {
  const q = normalize(query)
  const n = normalize(name)
  if (!q || !n) return 0
  if (q === n) return 1
  if (n.includes(q) || q.includes(n)) return 0.8
  return Math.max(0, 1 - distance(q, n) / Math.max(q.length, n.length))
}

const manufacturersByStore = new Map<string, StoreManufacturer[]>()

function manufacturersOf(storeId: string): StoreManufacturer[] {
  let list = manufacturersByStore.get(storeId)
  if (!list) {
    list = MOCK_OPTIONS.manufacturers.map((item) => ({ ...item, approved: true }))
    manufacturersByStore.set(storeId, list)
  }
  return list
}

const byName = (a: StoreManufacturer, b: StoreManufacturer) => a.name.localeCompare(b.name, 'es')

const MAX_IMAGES = 10
const MAX_PENDING_MANUFACTURERS = 20

function imageError(message: string, code: string): ApiError {
  return new ApiError(message, { status: 400, code })
}

function checkImage(file: File): void {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
    throw imageError('Solo se aceptan imágenes JPG, PNG o WebP.', 'PRODUCT_IMAGE_INVALID')
  }
  if (file.size > 5 * 1024 * 1024) {
    throw new ApiError('Cada imagen puede pesar hasta 5 MB.', { status: 413, code: 'PAYLOAD_TOO_LARGE' })
  }
}

/** La simulación no sube el archivo: usa la imagen genérica del sistema en todas las variantes. */
function mockImage(order: number, isMain: boolean, id = newId('img')): ProductImage {
  return {
    id,
    url: PLACEHOLDER_IMAGES.product,
    thumbUrl: PLACEHOLDER_IMAGES.product,
    cardUrl: PLACEHOLDER_IMAGES.product,
    isMain,
    displayOrder: order,
    createdAt: new Date().toISOString().slice(0, 19),
  }
}

/** 400 `VALIDATION_ERROR` con `details.vehicleRules`: un producto universal no lleva reglas. */
function universalWithRules(): ApiError {
  return validation({ vehicleRules: 'Un producto universal no lleva reglas de vehículo.' })
}

function invalidRule(index: number, field: string, message: string): ApiError {
  return new ApiError('Hay reglas de vehículo por corregir.', {
    status: 400,
    code: 'VEHICLE_RULE_INVALID',
    details: [{ index, field, code: 'INCOHERENT', message }],
  })
}

/** Valida coherencia contra el catálogo y normaliza como el servidor: sin duplicados ni reglas contenidas en otra. */
function resolveRules(input: VehicleRuleInput[]): VehicleRule[] {
  if (input.length > 100) throw new ApiError('Máximo 100 reglas de vehículo.', { status: 400, code: 'VEHICLE_RULE_INVALID', details: [] })
  const rules = input.map((rule, index) => {
    const make = MOCK_VEHICLES.find((item) => item.id === rule.makeId)
    if (!make) throw invalidRule(index, 'makeId', 'Esa marca no existe.')
    if (rule.versionId && !rule.modelId) throw invalidRule(index, 'modelId', 'Para elegir una versión primero elige el modelo.')
    const model = rule.modelId ? make.models.find((item) => item.id === rule.modelId) : undefined
    if (rule.modelId && !model) throw invalidRule(index, 'modelId', 'Ese modelo no pertenece a la marca.')
    const version = rule.versionId ? model?.versions.find((item) => item.id === rule.versionId) : undefined
    if (rule.versionId && !version) throw invalidRule(index, 'versionId', 'Esa versión no pertenece al modelo.')
    return mockRuleFor(rule.versionId ?? rule.modelId ?? rule.makeId)
  })
  const covers = (outer: VehicleRule, inner: VehicleRule) =>
    outer.makeId === inner.makeId &&
    (outer.modelId === null || outer.modelId === inner.modelId) &&
    (outer.versionId === null || outer.versionId === inner.versionId)
  return rules.filter((rule, index) => !rules.some((other, otherIndex) => covers(other, rule) && (!covers(rule, other) || otherIndex < index) && otherIndex !== index))
}

interface ResolvedManufacturer extends StoreManufacturer {
  /** Fabricante nuevo (pendiente) que se guarda junto con el producto. */
  isNew: boolean
}

/** Exactamente uno de `manufacturerId` / `newManufacturerName`; no escribe nada hasta `commitManufacturer`. */
function resolveManufacturer(storeId: string, input: { manufacturerId?: string; newManufacturerName?: string; confirmNewManufacturer?: boolean }): ResolvedManufacturer {
  const list = manufacturersOf(storeId)
  if (Boolean(input.manufacturerId) === Boolean(input.newManufacturerName?.trim())) {
    throw validation({ manufacturerId: 'Elige un fabricante o escribe el nombre de uno nuevo.' })
  }
  if (input.manufacturerId) {
    const found = list.find((item) => item.id === input.manufacturerId)
    if (!found) throw validation({ manufacturerId: 'Ese fabricante no está disponible para tu almacén.' })
    return { ...found, isNew: false }
  }
  const name = (input.newManufacturerName ?? '').trim().replace(/\s+/g, ' ')
  if (name.length < 2 || name.length > 100) throw validation({ newManufacturerName: 'El nombre debe tener entre 2 y 100 caracteres.' })
  const exact = list.find((item) => normalize(item.name) === normalize(name))
  if (exact) return { ...exact, isNew: false }
  const similar = list.filter((item) => similarity(name, item.name) >= 0.6).toSorted(byName)
  if (similar.length > 0 && !input.confirmNewManufacturer) {
    throw new ApiError('Ya hay fabricantes con un nombre parecido.', {
      status: 409,
      code: 'MANUFACTURER_SIMILAR',
      details: similar.map(({ id, name: similarName }) => ({ id, name: similarName })),
    })
  }
  if (list.filter((item) => !item.approved).length >= MAX_PENDING_MANUFACTURERS) {
    throw new ApiError('Tienes demasiados fabricantes pendientes de revisión.', { status: 409, code: 'MANUFACTURER_PENDING_LIMIT' })
  }
  return { id: newId('man'), name, approved: false, isNew: true }
}

function commitManufacturer(storeId: string, manufacturer: ResolvedManufacturer): void {
  if (!manufacturer.isNew) return
  manufacturersOf(storeId).push({ id: manufacturer.id, name: manufacturer.name, approved: manufacturer.approved })
}

/** 409 `PRODUCT_NOT_PUBLISHABLE` con lo que falta; stock 0 no bloquea. */
function assertPublishable(store: MockStore, manufacturer: StoreManufacturer, imageCount: number, ruleCount: number, universal: boolean): void {
  const blockers: PublishBlocker[] = []
  if (imageCount === 0) blockers.push({ code: 'NO_IMAGES', message: 'Agrega al menos una imagen.' })
  if (!universal && ruleCount === 0) blockers.push({ code: 'NO_VEHICLES', message: 'Indica los vehículos compatibles o marca el producto como universal.' })
  if (!manufacturer.name) blockers.push({ code: 'MANUFACTURER_INVALID', message: 'El fabricante no es válido.' })
  try {
    requireActiveSubscription(store)
  } catch {
    blockers.push({ code: 'SUBSCRIPTION_INACTIVE', message: 'Tu suscripción no está activa.' })
  }
  if (blockers.length > 0) {
    throw new ApiError('Al producto le falta algo para publicarse.', { status: 409, code: 'PRODUCT_NOT_PUBLISHABLE', details: blockers })
  }
}

function activeBranch(store: MockStore, branchId: string): Branch {
  const branch = store.branches.find((candidate) => candidate.id === branchId && candidate.state === 'ACTIVE')
  if (!branch) throw new ApiError('Esa sucursal no existe o está inactiva.', { status: 404, code: 'BRANCH_NOT_FOUND' })
  return branch
}

/**
 * Sucursales del alta como en `ProductCreateRequest`: o `branchId` + `quantity`, o `listings`
 * (1 a número de sucursales, sin repetir). Sin `price` por sucursal se usa el precio general.
 */
function resolvePlacements(store: MockStore, input: ProductCreateInput): { branch: Branch; quantity: number; price: number }[] {
  const single = input.branchId !== undefined && input.quantity !== undefined
  const listings = input.listings ?? []
  if (single === listings.length > 0) {
    throw validation({ listings: 'Indica una sucursal con existencias o la lista de sucursales, no ambas.' })
  }
  const requested = single ? [{ branchId: input.branchId as string, quantity: input.quantity as number, price: undefined }] : listings
  if (requested.length > store.branches.length) throw validation({ listings: 'Hay más sucursales que las de tu almacén.' })
  const seen = new Set<string>()
  return requested.map((listing, index) => {
    if (seen.has(listing.branchId)) throw validation({ [`listings[${index}].branchId`]: 'No repitas la misma sucursal.' })
    seen.add(listing.branchId)
    if (!Number.isInteger(listing.quantity) || listing.quantity < 0) {
      throw validation({ [`listings[${index}].quantity`]: 'Las existencias no pueden ser negativas.' })
    }
    if (listing.price !== undefined && !(listing.price >= 0.01)) {
      throw validation({ [`listings[${index}].price`]: 'El precio debe ser mayor que 0.' })
    }
    return { branch: activeBranch(store, listing.branchId), quantity: listing.quantity, price: listing.price ?? input.price }
  })
}

const BULK_MAX = 500
const BULK_DELETE_MAX = 50
const BULK_PERMISSION: Record<ProductBulkAction, StorePermission> = {
  PUBLISH: 'PRODUCT_PUBLISH',
  UNPUBLISH: 'PRODUCT_PUBLISH',
  DELETE: 'PRODUCT_DELETE',
  PRICE: 'PRODUCT_UPDATE',
}

const MAX_PRICE = 9_999_999_999

/** `PERCENT`: variación con signo sobre el precio actual; `AMOUNT`: precio absoluto. Redondeado al peso. */
function adjustedPrice(price: number, mode: ProductBulkPriceMode, value: number): number {
  return Math.round(mode === 'PERCENT' ? price * (1 + value / 100) : value)
}

/** Misma semántica que `BulkProductAction`: `DELETE` y `PRICE` todo o nada; `PUBLISH` lo que se pueda. */
function applyBulk(store: MockStore, input: ProductBulkInput): ProductBulkResult {
  requirePermission(store, BULK_PERMISSION[input.action])
  const ids = [...new Set(input.productIds)]
  if (ids.length === 0 || ids.length > BULK_MAX) throw validation({ productIds: `Selecciona entre 1 y ${BULK_MAX} productos.` })
  if (input.action === 'DELETE' && ids.length > BULK_DELETE_MAX) {
    throw new ApiError(`Elimina hasta ${BULK_DELETE_MAX} productos a la vez.`, {
      status: 400,
      code: 'BULK_DELETE_LIMIT',
      details: { max: BULK_DELETE_MAX },
    })
  }
  const owned = ids.filter((id) => store.products.some((row) => row.id === id))
  const missing = ids.filter((id) => !owned.includes(id))
  const skipped: ProductBulkResult['skipped'] = []

  if (input.action === 'DELETE') {
    if (missing.length > 0) throw notFound()
    store.products = store.products.filter((row) => !owned.includes(row.id))
    return { affected: owned.length, skipped }
  }

  if (input.action === 'PRICE') {
    if (!input.branchId) throw validation({ branchId: 'Elige la sucursal donde cambia el precio.' })
    if (input.priceMode !== 'PERCENT' && input.priceMode !== 'AMOUNT') throw validation({ priceMode: 'Elige si ajustas por porcentaje o por valor.' })
    const value = input.value
    if (value === undefined || !Number.isFinite(value)) throw validation({ value: 'Escribe el valor del ajuste.' })
    activeBranch(store, input.branchId)
    if (missing.length > 0) throw notFound()
    const targets = store.products.filter((row) => owned.includes(row.id) && row.branchId === input.branchId)
    if (targets.length !== owned.length) {
      throw new ApiError('Algún producto no está listado en esa sucursal.', { status: 404, code: 'LISTING_NOT_FOUND' })
    }
    const percent = input.priceMode === 'PERCENT'
    if (percent ? value < -90 || value > 500 : value <= 0) {
      throw new ApiError(percent ? 'La variación debe estar entre -90% y +500%.' : 'El precio debe ser mayor que 0.', {
        status: 400,
        code: 'INVALID_PRICE_VALUE',
      })
    }
    const prices = targets.map((row) => adjustedPrice(row.price, input.priceMode as ProductBulkPriceMode, value))
    if (prices.some((price) => price < 1 || price > MAX_PRICE)) {
      throw new ApiError('El precio resultante debe estar entre 1 y 9.999.999.999 pesos.', { status: 400, code: 'INVALID_PRICE' })
    }
    targets.forEach((row, index) => {
      const price = prices[index] as number
      row.price = price
      row.effectivePrice = Math.max(0, price - row.discountAmount)
    })
    return { affected: targets.length, skipped }
  }

  const publish = input.action === 'PUBLISH'
  if (publish) requireActiveSubscription(store)
  for (const id of missing) skipped.push({ productId: id, code: 'PRODUCT_NOT_FOUND' })
  let affected = 0
  for (const id of owned) {
    const rows = store.products.filter((row) => row.id === id)
    const first = rows[0] as ProductRow
    if (publish) {
      try {
        assertPublishable(store, { id: first.manufacturerId, name: first.manufacturerName, approved: first.manufacturerApproved }, first.images.length, first.vehicleRules.length, first.universal)
      } catch (error) {
        const blocker = error instanceof ApiError && Array.isArray(error.details) ? (error.details[0] as PublishBlocker | undefined) : undefined
        skipped.push({ productId: id, code: blocker?.code ?? 'PRODUCT_NOT_PUBLISHABLE' })
        continue
      }
    }
    for (const row of rows) row.state = publish ? 'ACTIVE' : 'INACTIVE'
    affected += 1
  }
  return { affected, skipped }
}

/** Productos simulados: una fila por producto y sucursal, igual que el API real. */
export function createMockProductService(): ProductService {
  return {
    async listManufacturers(storeId) {
      await simulateNetwork(250)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_CREATE')
      return manufacturersOf(storeId).map((item) => ({ ...item })).toSorted(byName)
    },

    async searchManufacturers(storeId, q) {
      await simulateNetwork(200)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_CREATE')
      if (q.trim().length < 2) return []
      return manufacturersOf(storeId)
        .map((item) => ({ ...item, similarity: similarity(q, item.name) }))
        .filter((item) => item.similarity >= 0.5)
        .toSorted((a, b) => b.similarity - a.similarity)
        .slice(0, 8)
    },

    async createManufacturer(storeId, input) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_CREATE')
      const name = input.name.trim().replace(/\s+/g, ' ')
      if (name.length < 2 || name.length > 100) throw validation({ name: 'El nombre debe tener entre 2 y 100 caracteres.' })
      const list = manufacturersOf(storeId)
      const exact = list.find((item) => normalize(item.name) === normalize(name))
      if (exact) return { ...exact }
      const similar = list.filter((item) => similarity(name, item.name) >= 0.6).toSorted(byName)
      if (similar.length > 0 && !input.confirmNew) {
        throw new ApiError('Ya hay fabricantes con un nombre parecido.', {
          status: 409,
          code: 'MANUFACTURER_SIMILAR',
          details: similar.map(({ id, name: similarName }) => ({ id, name: similarName })),
        })
      }
      const created: StoreManufacturer = { id: newId('man'), name, approved: false }
      list.push(created)
      return { ...created }
    },

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

    async listMakes() {
      await simulateNetwork(200)
      return MOCK_VEHICLES.map(({ id, name }) => ({ id, name })).toSorted((a, b) => a.name.localeCompare(b.name, 'es'))
    },

    async listModels(makeId) {
      await simulateNetwork(250)
      const make = MOCK_VEHICLES.find((item) => item.id === makeId)
      if (!make) throw new ApiError('No encontramos esa marca.', { status: 404, code: 'VEHICLE_MAKE_NOT_FOUND' })
      return make.models.map(({ id, name }) => ({ id, name })).toSorted((a, b) => a.name.localeCompare(b.name, 'es'))
    },

    async listVersions(modelId) {
      await simulateNetwork(250)
      const model = MOCK_VEHICLES.flatMap((make) => make.models).find((item) => item.id === modelId)
      if (!model) throw new ApiError('No encontramos ese modelo.', { status: 404, code: 'VEHICLE_MODEL_NOT_FOUND' })
      return model.versions.map((version) => ({ ...version }))
    },

    async create(storeId, input, images = []) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_CREATE')
      requireActiveSubscription(store)
      requireCapacity(store, 'PRODUCTS')
      const placements = resolvePlacements(store, input)
      if (store.products.some((row) => row.sku && input.sku && row.sku.toLowerCase() === input.sku.toLowerCase())) {
        throw validation({ sku: 'Ya tienes un producto con este SKU.' })
      }
      if (images.length > MAX_IMAGES) throw imageError('Un producto admite hasta 10 imágenes.', 'PRODUCT_IMAGE_LIMIT')
      images.forEach(checkImage)
      // Todo o nada: se valida y resuelve todo antes de tocar el estado.
      const universal = input.universal === true
      if (universal && input.vehicleRules.length > 0) throw universalWithRules()
      const rules = universal ? [] : resolveRules(input.vehicleRules)
      const manufacturer = resolveManufacturer(storeId, input)
      const created = images.map((_, index) => mockImage(index, index === (input.mainImageIndex ?? 0)))
      if (input.publish) assertPublishable(store, manufacturer, created.length, rules.length, universal)
      const productId = newId('prod')
      const rows: ProductRow[] = placements.map(({ branch, quantity, price }) => ({
        id: productId,
        storeId,
        branchId: branch.id,
        branchName: branch.name,
        name: input.name,
        sku: input.sku || null,
        partNumber: input.partNumber || null,
        description: input.description,
        price,
        effectivePrice: price,
        discountAmount: 0,
        appliedPromotionName: null,
        quantity,
        manufacturerId: manufacturer.id,
        manufacturerName: manufacturer.name,
        manufacturerApproved: manufacturer.approved,
        categoryId: input.categoryId,
        state: input.publish ? 'ACTIVE' : 'INACTIVE',
        attributes: input.attributes ?? {},
        universal,
        vehicleRules: rules,
        images: created,
        minStock: null,
      }))
      commitManufacturer(storeId, manufacturer)
      store.products.unshift(...rows)
      return { ...(rows[0] as ProductRow) }
    },

    async addListing(storeId, productId, input) {
      await simulateNetwork(500)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_UPDATE')
      requireActiveSubscription(store)
      const source = store.products.find((row) => row.id === productId)
      if (!source) throw notFound()
      const branch = activeBranch(store, input.branchId)
      if (!(input.price >= 0.01)) throw validation({ price: 'El precio debe ser mayor que 0.' })
      if (!Number.isInteger(input.stock) || input.stock < 0) throw validation({ stock: 'Las existencias no pueden ser negativas.' })
      if (store.products.some((row) => row.id === productId && row.branchId === branch.id)) {
        throw new ApiError('El producto ya está listado en esa sucursal.', { status: 409, code: 'LISTING_ALREADY_EXISTS' })
      }
      const index = store.products.findLastIndex((row) => row.id === productId)
      store.products.splice(index + 1, 0, {
        ...source,
        branchId: branch.id,
        branchName: branch.name,
        price: input.price,
        effectivePrice: input.price,
        discountAmount: 0,
        appliedPromotionName: null,
        quantity: input.stock,
        minStock: null,
      })
    },

    async removeListing(storeId, productId, branchId) {
      await simulateNetwork(400)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_UPDATE')
      const rows = store.products.filter((row) => row.id === productId)
      if (rows.length === 0) throw notFound()
      if (!rows.some((row) => row.branchId === branchId)) {
        throw new ApiError('El producto no está listado en esa sucursal.', { status: 404, code: 'LISTING_NOT_FOUND' })
      }
      if (rows.length === 1) {
        throw new ApiError('Es el único listado del producto. Para quitarlo, borra el producto.', { status: 409, code: 'LAST_LISTING' })
      }
      store.products = store.products.filter((row) => !(row.id === productId && row.branchId === branchId))
    },

    async bulk(storeId, input) {
      await simulateNetwork(700)
      const store = getStore(storeId)
      return applyBulk(store, input)
    },

    async update(storeId, branchId, productId, input, newImages = []) {
      await simulateNetwork(600)
      const store = getStore(storeId)
      requirePermission(store, 'PRODUCT_UPDATE')
      const rows = store.products.filter((row) => row.id === productId)
      const target = rows.find((row) => row.branchId === branchId)
      if (!target) throw notFound()
      // Todo o nada: primero se valida y se calcula el resultado, después se aplica.
      newImages.forEach(checkImage)
      const removed = new Set(input.removeImageIds ?? [])
      const kept = target.images.filter((image) => !removed.has(image.id))
      const added = newImages.map((_, index) => mockImage(kept.length + index, false, `new:${index}`))
      const byRef = new Map([...kept, ...added].map((image) => [image.id, image]))
      const ordered = input.imageOrder
        ? [...input.imageOrder.flatMap((ref) => byRef.get(ref) ?? []), ...[...byRef.values()].filter((image) => !input.imageOrder?.includes(image.id))]
        : [...kept, ...added]
      if (ordered.length > MAX_IMAGES) throw imageError('Un producto admite hasta 10 imágenes.', 'PRODUCT_IMAGE_LIMIT')
      const mainId = input.mainImage ?? ordered.find((image) => image.isMain)?.id ?? ordered[0]?.id
      const images = ordered.map((image, index) => ({
        ...image,
        id: image.id.startsWith('new:') ? newId('img') : image.id,
        isMain: image.id === mainId,
        displayOrder: index,
      }))
      const universal = input.universal ?? target.universal
      if (universal && input.vehicleRules && input.vehicleRules.length > 0) throw universalWithRules()
      // Pasar a universal borra las reglas; quedarse universal las mantiene vacías.
      const rules = universal ? [] : input.vehicleRules ? resolveRules(input.vehicleRules) : target.vehicleRules
      const manufacturer =
        input.manufacturerId !== undefined || input.newManufacturerName !== undefined
          ? resolveManufacturer(storeId, input)
          : { id: target.manufacturerId, name: target.manufacturerName, approved: target.manufacturerApproved, isNew: false }
      if (input.publish) assertPublishable(store, manufacturer, images.length, rules.length, universal)
      commitManufacturer(storeId, manufacturer)
      // Datos del producto: se comparten entre sucursales. Precio y existencias: de la fila (sucursal).
      for (const row of rows) {
        if (input.name !== undefined) row.name = input.name
        if (input.sku !== undefined) row.sku = input.sku || null
        if (input.partNumber !== undefined) row.partNumber = input.partNumber || null
        if (input.description !== undefined) row.description = input.description
        row.manufacturerId = manufacturer.id
        row.manufacturerName = manufacturer.name
        row.manufacturerApproved = manufacturer.approved
        if (input.categoryId !== undefined) row.categoryId = input.categoryId
        row.universal = universal
        row.vehicleRules = rules
        row.images = images
        if (input.publish !== undefined) row.state = input.publish ? 'ACTIVE' : 'INACTIVE'
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
      const first = rows[0]
      if (!first) throw notFound()
      if (published) {
        assertPublishable(store, { id: first.manufacturerId, name: first.manufacturerName, approved: first.manufacturerApproved }, first.images.length, first.vehicleRules.length, first.universal)
      }
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
      const created = files.map((_, index) => mockImage(current.length + index, current.length === 0 && index === 0))
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
