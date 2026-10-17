import { PLACEHOLDER_IMAGES } from '@/lib/images'
import type {
  CategoryOptionDto,
  ProductImageResponseDto,
  ProductOptionsResponseDto,
  ProductResponseDto,
  StoreResponseDto,
  VehicleNameOptionDto,
  VehicleVersionOptionDto,
} from '../dto'
import {
  MarketplaceError,
  type Category,
  type FilterOptions,
  type MarketplaceService,
  type Product,
  type ProductImage,
  type ProductQuery,
  type StoreSummary,
} from '../types'

/**
 * Implementación contra el backend real. Usa SOLO los endpoints públicos existentes
 * (marketplace/catalog/ProductBrowseController y marketplace/stores/StoreBrowseController).
 * `baseUrl` incluye el prefijo `ApiPaths.BASE` (`/api/v1`). Todas estas rutas son públicas
 * (`WebSecurityConfig.PUBLIC_ROUTES`).
 */
const PATHS = {
  products: '/marketplace/products',
  product: (id: string) => `/marketplace/products/${encodeURIComponent(id)}`,
  options: '/marketplace/product-options',
  categories: '/marketplace/product-options/categories',
  vehicleMakes: '/marketplace/vehicles/makes',
  vehicleModels: (makeId: string) => `/marketplace/vehicles/makes/${encodeURIComponent(makeId)}/models`,
  vehicleVersions: (modelId: string) => `/marketplace/vehicles/models/${encodeURIComponent(modelId)}/versions`,
  stores: '/marketplace/stores',
  storeAsset: (storeId: string, assetType: 'logo' | 'banner') =>
    `/marketplace/stores/${encodeURIComponent(storeId)}/brand/${assetType}`,
} as const

/** "FRENOS_Y_DISCOS" -> "Frenos y discos". */
function humanizeCode(code: string): string {
  const words = code.replace(/[_-]+/g, ' ').trim().toLowerCase()
  return words.charAt(0).toUpperCase() + words.slice(1)
}

function toCategory(dto: CategoryOptionDto): Category {
  // TODO(api): CategoryOption solo trae `code`. Falta nombre visible, descripción y conteo de productos publicados.
  return { id: dto.id, slug: dto.code.toLowerCase(), name: humanizeCode(dto.code) }
}

/** Listados usan `cardUrl`/`thumbUrl`; el detalle usa `url`. Si falta una variante se cae a la siguiente. */
function toImages(images: ProductImageResponseDto[] | null): ProductImage[] {
  if (!images || images.length === 0) {
    return [{ url: PLACEHOLDER_IMAGES.product, thumbUrl: PLACEHOLDER_IMAGES.product, cardUrl: PLACEHOLDER_IMAGES.product }]
  }
  const isMain = (image: ProductImageResponseDto) => (image.isMain ?? image.main ? 1 : 0)
  return images
    .toSorted((a, b) => isMain(b) - isMain(a) || a.displayOrder - b.displayOrder)
    .map((image) => ({
      url: image.url,
      thumbUrl: image.thumbUrl || image.cardUrl || image.url,
      cardUrl: image.cardUrl || image.thumbUrl || image.url,
    }))
}

/** Query string del filtro por vehículo; el servidor valida la jerarquía (400 `VEHICLE_FILTER_INVALID`). */
function vehicleQuery(query?: Pick<ProductQuery, 'makeId' | 'modelId' | 'versionId'>): string {
  const params = new URLSearchParams()
  if (query?.makeId) params.set('makeId', query.makeId)
  if (query?.modelId) params.set('modelId', query.modelId)
  if (query?.versionId) params.set('versionId', query.versionId)
  const qs = params.toString()
  return qs ? `?${qs}` : ''
}

interface Lookups {
  categories: Map<string, Category>
  stores: Map<string, StoreSummary>
}

export function createHttpMarketplaceService(baseUrl: string): MarketplaceService {
  const root = baseUrl.replace(/\/+$/, '')

  async function get<T>(path: string): Promise<T> {
    let response: Response
    try {
      response = await fetch(`${root}${path}`, { headers: { Accept: 'application/json' } })
    } catch (cause) {
      throw new MarketplaceError('No hay conexión con el servidor de Autopartco.', { cause })
    }
    if (!response.ok) {
      const body = (await response.json().catch(() => null)) as {
        message?: string
        code?: string
        error?: { message?: string; code?: string }
      } | null
      throw new MarketplaceError(body?.error?.message ?? body?.message ?? `El servidor respondió ${response.status}.`, {
        status: response.status,
        code: body?.error?.code ?? body?.code,
      })
    }
    return (await response.json()) as T
  }

  function toStore(dto: StoreResponseDto): StoreSummary {
    // TODO(api): StoreResponse no informa ciudad ni cuántos productos publica. Tampoco el distintivo
    // "Recomendado" (`recommended`): sin él no se muestra el badge ni el filtro de tiendas recomendadas.
    return { id: dto.id, name: dto.businessName, logoUrl: `${root}${PATHS.storeAsset(dto.id, 'logo')}` }
  }

  let lookupsPromise: Promise<Lookups> | undefined
  function lookups(): Promise<Lookups> {
    lookupsPromise ??= Promise.all([
      get<ProductOptionsResponseDto>(PATHS.options),
      get<StoreResponseDto[]>(PATHS.stores),
    ]).then(([options, stores]) => ({
      categories: new Map(options.categories.map((item) => [item.id, toCategory(item)])),
      stores: new Map(stores.map((item) => [item.id, toStore(item)])),
    }))
    lookupsPromise.catch(() => {
      lookupsPromise = undefined
    })
    return lookupsPromise
  }

  function toProduct(dto: ProductResponseDto, maps: Lookups): Product {
    const discount = dto.discountAmount && dto.discountAmount > 0 ? dto.discountAmount : undefined
    return {
      id: dto.id,
      listingKey: `${dto.id}:${dto.branchId ?? dto.storeId}`,
      branchId: dto.branchId ?? undefined,
      name: dto.name,
      partNumber: dto.partNumber ?? undefined,
      description: dto.description ?? undefined,
      price: dto.effectivePrice ?? dto.price,
      listPrice: discount ? dto.price : undefined,
      discountAmount: discount,
      promotionName: dto.appliedPromotionName ?? undefined,
      images: toImages(dto.images),
      category: dto.categoryId ? maps.categories.get(dto.categoryId) : undefined,
      // La marca sale del propio producto: se ve aunque el fabricante esté pendiente de aprobación.
      brand: dto.manufacturerName
        ? { id: dto.manufacturerId ?? dto.manufacturerName, name: dto.manufacturerName, approved: dto.manufacturerApproved ?? undefined }
        : undefined,
      store: maps.stores.get(dto.storeId) ?? { id: dto.storeId, name: 'Tienda Autopartco' },
      availability:
        dto.quantity === null
          ? undefined
          : {
              status: dto.quantity > 0 ? 'in_stock' : 'out_of_stock',
              quantity: dto.quantity,
              branchName: dto.branchName ?? undefined,
            },
      universal: dto.universal === true,
      vehicleRules: dto.universal ? [] : (dto.vehicleRules ?? []),
      specifications: Object.entries(dto.attributes ?? {})
        .filter(([, value]) => value !== null && value !== undefined && value !== '')
        .map(([label, value]) => ({ label: humanizeCode(label), value: String(value) })),
    }
  }

  return {
    capabilities: {
      // El servidor solo filtra por vehículo (makeId/modelId/versionId); los universales aparecen siempre.
      // TODO(api): no hay búsqueda por texto, otros filtros, orden ni paginación en el servidor.
      textSearch: false,
      sorting: false,
      pagination: false,
      filters: new Set(['vehicle']),
    },

    async searchProducts(query) {
      // TODO(api): solo se envía el filtro por vehículo; el endpoint devuelve el resto del catálogo publicado completo.
      const [listings, maps] = await Promise.all([get<ProductResponseDto[]>(`${PATHS.products}${vehicleQuery(query)}`), lookups()])
      const items = listings.map((dto) => toProduct(dto, maps))
      return { items, total: items.length, page: 1, pageSize: Math.max(items.length, 1), totalPages: 1 }
    },

    async getProduct(id) {
      const [dto, maps] = await Promise.all([get<ProductResponseDto>(PATHS.product(id)), lookups()])
      return toProduct(dto, maps)
    },

    async getRelatedProducts() {
      // TODO(api): falta un endpoint de relacionados (p. ej. GET /marketplace/products/{id}/related?limit=).
      return []
    },

    async getFilterOptions(): Promise<FilterOptions> {
      const options = await get<ProductOptionsResponseDto>(PATHS.options)
      return {
        categories: options.categories.map(toCategory),
        brands: options.manufacturers,
        // TODO(api): falta el rango de precios publicados.
      }
    },

    async listVehicleMakes() {
      return get<VehicleNameOptionDto[]>(PATHS.vehicleMakes)
    },

    async listVehicleModels(makeId) {
      return get<VehicleNameOptionDto[]>(PATHS.vehicleModels(makeId))
    },

    async listVehicleVersions(modelId) {
      const versions = await get<VehicleVersionOptionDto[]>(PATHS.vehicleVersions(modelId))
      return versions.map((version) => ({ id: version.id, name: version.label }))
    },

    async getCategories() {
      const categories = await get<CategoryOptionDto[]>(PATHS.categories)
      return categories.map(toCategory)
    },

    async listStores() {
      const stores = await get<StoreResponseDto[]>(PATHS.stores)
      return stores.map(toStore)
    },
  }
}
