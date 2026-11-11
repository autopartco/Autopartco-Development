import { apiRequest } from '@/lib/api'
import { getAnonymousSessionId } from '@/lib/anonymous-session'
import { getAuthService } from '@/features/auth/api/auth-service'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import { PLACEHOLDER_IMAGES } from '@/lib/images'
import type {
  CategoryOptionDto,
  ProductImageResponseDto,
  ProductOptionsResponseDto,
  ProductResponseDto,
  PageResponseDto,
  StorePublicProfileDto,
  StoreResponseDto,
  StoreReviewResponseDto,
  VehicleNameOptionDto,
  VehicleVersionOptionDto,
} from '../dto'
import {
  MarketplaceError,
  type Category,
  type FilterOptions,
  type FrequentlyBoughtItem,
  type MarketplaceService,
  type Product,
  type ProductImage,
  type ProductQuery,
  type StoreProfile,
  type StoreReview,
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
  frequentlyBought: (id: string) => `/marketplace/products/${encodeURIComponent(id)}/frequently-bought`,
  options: '/marketplace/product-options',
  categories: '/marketplace/product-options/categories',
  vehicleMakes: '/marketplace/vehicles/makes',
  vehicleModels: (makeId: string) => `/marketplace/vehicles/makes/${encodeURIComponent(makeId)}/models`,
  vehicleVersions: (modelId: string) => `/marketplace/vehicles/models/${encodeURIComponent(modelId)}/versions`,
  stores: '/marketplace/stores',
  storeAsset: (storeId: string, assetType: 'logo' | 'banner') =>
    `/marketplace/stores/${encodeURIComponent(storeId)}/brand/${assetType}`,
  store: (storeId: string) => `/marketplace/stores/${encodeURIComponent(storeId)}`,
  storeReviews: (storeId: string) => `/marketplace/stores/${encodeURIComponent(storeId)}/reviews`,
} as const

function toReview(dto: StoreReviewResponseDto): StoreReview {
  return { id: dto.id, rating: dto.rating, comment: dto.comment ?? undefined, authorName: dto.authorName, createdAt: dto.createdAt }
}

/** `FrequentlyBoughtController`: `GET /marketplace/products/{id}/frequently-bought` (público, hasta 6). */
type FrequentlyBoughtDto = Omit<FrequentlyBoughtItem, 'image'> & { image: string | null }

/** Palabras de códigos del catálogo que llevan tilde al mostrarse. */
const ACCENTED_WORDS: Record<string, string> = {
  carroceria: 'carrocería',
  condicion: 'condición',
  direccion: 'dirección',
  electrico: 'eléctrico',
  garantia: 'garantía',
  iluminacion: 'iluminación',
  refrigeracion: 'refrigeración',
  suspension: 'suspensión',
  transmision: 'transmisión',
}

/** "FRENOS_Y_DISCOS" -> "Frenos y discos"; "garantia_meses" -> "Garantía meses". */
function humanizeCode(code: string): string {
  const words = code
    .replace(/[_-]+/g, ' ')
    .trim()
    .toLowerCase()
    .split(' ')
    .map((word) => ACCENTED_WORDS[word] ?? word)
    .join(' ')
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

  /** Access token de la sesión, si la hay. Nunca lanza: sin sesión (o si no se pudo renovar) la petición va anónima. */
  async function bearer(): Promise<string | undefined> {
    try {
      await getAuthService()
      return await getAccessToken()
    } catch {
      return undefined
    }
  }

  async function send(path: string, extraHeaders: Record<string, string>, token: string | undefined): Promise<Response> {
    try {
      return await fetch(`${root}${path}`, {
        headers: { Accept: 'application/json', ...extraHeaders, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      })
    } catch (cause) {
      throw new MarketplaceError('No hay conexión con el servidor de Autopartco.', { cause })
    }
  }

  async function get<T>(
    path: string,
    extraHeaders: Record<string, string> = {},
    onHeaders?: (headers: Headers) => void,
  ): Promise<T> {
    // Las rutas de productos son públicas, pero el precio depende de quién compra (precio para clientes frecuentes):
    // con sesión llevan el token.
    const token = path.startsWith('/marketplace/products') ? await bearer() : undefined
    let response = await send(path, extraHeaders, token)
    // Un token vencido o revocado no debe impedir ver el catálogo: se reintenta como visitante.
    if (response.status === 401 && token) response = await send(path, extraHeaders, undefined)
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
    onHeaders?.(response.headers)
    return (await response.json()) as T
  }

  /** Las rutas de marca llegan como `/api/v1/...`; con una API en otro dominio se resuelven contra su origen. */
  function assetUrl(path: string): string {
    return /^https?:\/\//.test(root) ? new URL(path, root).toString() : path
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
      promotionAudience: dto.appliedPromotionAudience ?? undefined,
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
      // El servidor filtra por texto (q). TODO(api): no hay otros filtros ni orden. La paginación sí (page/size).
      textSearch: true,
      sorting: false,
      pagination: true,
      // `storeId` lo filtra el servidor (perfil de la tienda y «Ver más repuestos de esta tienda»).
      filters: new Set(['vehicle', 'store']),
    },

    async searchProducts(query) {
      // La petición lleva vehículo, texto (q) y categoría (categoryId) para que el servidor registre la búsqueda;
      // la paginación la hace el servidor (page desde 0, size; total en X-Total-Count).
      // TODO(api): marcas, tienda, precio, existencias y orden aún no se envían al servidor.
      const maps = await lookups()
      const params = new URLSearchParams(vehicleQuery(query).slice(1))
      const text = query.q.trim()
      if (text) params.set('q', text)
      const category = query.category ? [...maps.categories.values()].find((item) => item.slug === query.category) : undefined
      if (category) params.set('categoryId', category.id)
      if (query.store) params.set('storeId', query.store)
      params.set('page', String(Math.max(0, query.page - 1)))
      params.set('size', String(query.pageSize))
      const qs = params.toString()
      let total: number | undefined
      const listings = await get<ProductResponseDto[]>(`${PATHS.products}?${qs}`, { 'X-Session-Id': getAnonymousSessionId() }, (headers) => {
        const header = Number(headers.get('X-Total-Count'))
        if (Number.isFinite(header) && headers.has('X-Total-Count')) total = header
      })
      const items = listings.map((dto) => toProduct(dto, maps))
      const count = total ?? items.length
      return { items, total: count, page: query.page, pageSize: query.pageSize, totalPages: Math.max(1, Math.ceil(count / query.pageSize)) }
    },

    async getProduct(id) {
      const [dto, maps] = await Promise.all([get<ProductResponseDto>(PATHS.product(id)), lookups()])
      return toProduct(dto, maps)
    },

    async getRelatedProducts() {
      // TODO(api): falta un endpoint de relacionados (p. ej. GET /marketplace/products/{id}/related?limit=).
      return []
    },

    async getFrequentlyBought(productId) {
      const items = await get<FrequentlyBoughtDto[]>(PATHS.frequentlyBought(productId))
      return items.map((item) => ({
        productId: item.productId,
        name: item.name,
        price: item.price,
        image: item.image ?? undefined,
        reason: item.reason,
      }))
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

    async getStoreProfile(storeId): Promise<StoreProfile> {
      const dto = await get<StorePublicProfileDto>(PATHS.store(storeId))
      const contact = Object.fromEntries(Object.entries(dto.contact ?? {}).filter(([, value]) => Boolean(value)))
      return {
        id: dto.id,
        name: dto.name,
        description: dto.description ?? undefined,
        logoUrl: dto.logoUrl ? assetUrl(dto.logoUrl) : undefined,
        bannerUrl: dto.bannerUrl ? assetUrl(dto.bannerUrl) : undefined,
        cities: dto.cities ?? [],
        branchCount: dto.branchCount,
        memberSince: dto.memberSince ?? undefined,
        ratingAverage: dto.rating.average ?? undefined,
        ratingCount: dto.rating.count,
        publishedProducts: dto.publishedProducts,
        contact,
      }
    },

    async listStoreReviews(storeId, page) {
      const dto = await get<PageResponseDto<StoreReviewResponseDto>>(`${PATHS.storeReviews(storeId)}?page=${page}&size=10`)
      return { items: dto.items.map(toReview), page: dto.page, totalPages: dto.totalPages, total: dto.total }
    },

    async getReviewEligibility(storeId) {
      const token = await bearer()
      if (!token) return []
      const dto = await apiRequest<{ eligibleOrderIds: string[] }>(`${PATHS.storeReviews(storeId)}/eligibility`, { token })
      return dto.eligibleOrderIds
    },

    async createStoreReview(storeId, input) {
      const token = await bearer()
      if (!token) throw new MarketplaceError('Inicia sesión para calificar la tienda.', { status: 401 })
      const dto = await apiRequest<StoreReviewResponseDto>(PATHS.storeReviews(storeId), { method: 'POST', body: input, token })
      return toReview(dto)
    },
  }
}
