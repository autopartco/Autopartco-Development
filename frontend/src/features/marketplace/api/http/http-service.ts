import { PLACEHOLDER_IMAGES } from '@/lib/images'
import type {
  CategoryOptionDto,
  CompatibilityOptionDto,
  ProductImageResponseDto,
  ProductOptionsResponseDto,
  ProductResponseDto,
  StoreResponseDto,
} from '../dto'
import {
  MarketplaceError,
  type Category,
  type FilterOptions,
  type MarketplaceService,
  type Product,
  type StoreSummary,
  type VehicleFit,
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

function toVehicleFit(dto: CompatibilityOptionDto): VehicleFit {
  // TODO(api): CompatibilityOption no tiene rango de años; sin él no hay filtro por año.
  return { id: dto.id, make: dto.make, model: dto.model, version: dto.version ?? undefined }
}

function imageUrls(images: ProductImageResponseDto[] | null): string[] {
  if (!images || images.length === 0) return [PLACEHOLDER_IMAGES.product]
  const isMain = (image: ProductImageResponseDto) => (image.isMain ?? image.main ? 1 : 0)
  return images
    .toSorted((a, b) => isMain(b) - isMain(a) || a.displayOrder - b.displayOrder)
    .map((image) => image.url)
}

interface Lookups {
  manufacturers: Map<string, { id: string; name: string }>
  categories: Map<string, Category>
  compatibilities: Map<string, VehicleFit>
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
      const body = (await response.json().catch(() => null)) as { message?: string; code?: string } | null
      throw new MarketplaceError(body?.message ?? `El servidor respondió ${response.status}.`, {
        status: response.status,
        code: body?.code,
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
      manufacturers: new Map(options.manufacturers.map((item) => [item.id, item])),
      categories: new Map(options.categories.map((item) => [item.id, toCategory(item)])),
      compatibilities: new Map(options.compatibilities.map((item) => [item.id, toVehicleFit(item)])),
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
      name: dto.name,
      partNumber: dto.partNumber ?? undefined,
      description: dto.description ?? undefined,
      price: dto.effectivePrice ?? dto.price,
      listPrice: discount ? dto.price : undefined,
      discountAmount: discount,
      promotionName: dto.appliedPromotionName ?? undefined,
      images: imageUrls(dto.images),
      category: dto.categoryId ? maps.categories.get(dto.categoryId) : undefined,
      brand: dto.manufacturerId ? maps.manufacturers.get(dto.manufacturerId) : undefined,
      store: maps.stores.get(dto.storeId) ?? { id: dto.storeId, name: 'Tienda Autopartco' },
      availability:
        dto.quantity === null
          ? undefined
          : {
              status: dto.quantity > 0 ? 'in_stock' : 'out_of_stock',
              quantity: dto.quantity,
              branchName: dto.branchName ?? undefined,
            },
      compatibilities: (dto.compatibilityIds ?? []).flatMap((id) => {
        const fit = maps.compatibilities.get(id)
        return fit ? [fit] : []
      }),
      specifications: Object.entries(dto.attributes ?? {})
        .filter(([, value]) => value !== null && value !== undefined && value !== '')
        .map(([label, value]) => ({ label: humanizeCode(label), value: String(value) })),
    }
  }

  return {
    capabilities: {
      // TODO(api): no hay búsqueda por texto, filtros, orden ni paginación en el servidor.
      // Contrato sugerido: GET /marketplace/products?q=&categoryId=&manufacturerId=&compatibilityId=
      //   &year=&minPrice=&maxPrice=&inStock=&recommendedStore=&storeId=&sort=&page=&size=
      //   -> { items: ProductResponse[], total, page, size }.
      textSearch: false,
      sorting: false,
      pagination: false,
      filters: new Set(),
    },

    async searchProducts() {
      // TODO(api): se ignoran los parámetros de búsqueda; el endpoint devuelve el catálogo publicado completo.
      const [listings, maps] = await Promise.all([get<ProductResponseDto[]>(PATHS.products), lookups()])
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
      const makes = new Map<string, Set<string>>()
      for (const fit of options.compatibilities) {
        const models = makes.get(fit.make) ?? new Set<string>()
        models.add(fit.model)
        makes.set(fit.make, models)
      }
      return {
        categories: options.categories.map(toCategory),
        brands: options.manufacturers,
        vehicles: [...makes.entries()].map(([make, models]) => ({ make, models: [...models].toSorted() })),
        // TODO(api): faltan años de compatibilidad y rango de precios publicados.
      }
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
