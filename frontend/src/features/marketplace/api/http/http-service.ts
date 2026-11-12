import { apiRequest } from '@/lib/api'
import { cachedGet, type HttpResult } from '@/lib/http'
import { getAnonymousSessionId } from '@/lib/anonymous-session'
import { getAuthService } from '@/features/auth/api/auth-service'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import { PLACEHOLDER_IMAGES } from '@/lib/images'
import type {
  AdsResponseDto,
  CategoryOptionDto,
  HomeResponseDto,
  ProductImageResponseDto,
  ProductOptionsResponseDto,
  ProductResponseDto,
  SearchItemDto,
  SearchResponseDto,
  SuggestResponseDto,
  PageResponseDto,
  PublicBranchDto,
  StorePublicProfileDto,
  StoreResponseDto,
  ProductReviewEligibilityDto,
  ReviewResponseDto,
  ReviewSummaryDto,
  VehicleNameOptionDto,
  VehicleVersionOptionDto,
} from '../dto'
import { humanizeCode } from '../../lib/humanize-code'
import {
  MarketplaceError,
  type Category,
  type FilterOptions,
  type FrequentlyBoughtItem,
  type MarketplaceService,
  type Product,
  type ProductImage,
  type ProductQuery,
  type Review,
  type ReviewSummary,
  type ReviewTarget,
  type SearchSuggestions,
  type SuggestionKind,
  type OpeningHours,
  type ServiceCategory,
  type StoreBranch,
  type StoreProfile,
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
  search: '/marketplace/search',
  suggest: '/marketplace/search/suggest',
  home: '/marketplace/home',
  ads: '/marketplace/ads',
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
  storeBranches: (storeId: string) => `/marketplace/stores/${encodeURIComponent(storeId)}/branches`,
  storeReviews: (storeId: string) => `/marketplace/stores/${encodeURIComponent(storeId)}/reviews`,
} as const

const WEEK_DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const

/** Horario del servidor sin confiar en su forma: solo franjas `HH:mm` bien formadas y notas de texto. */
function toOpeningHours(raw: Record<string, unknown> | null): OpeningHours | undefined {
  if (!raw) return undefined
  const hours: OpeningHours = {}
  for (const day of WEEK_DAYS) {
    const slots = raw[day]
    if (!Array.isArray(slots)) continue
    hours[day] = slots.flatMap((slot): [string, string][] =>
      Array.isArray(slot) && typeof slot[0] === 'string' && typeof slot[1] === 'string' ? [[slot[0], slot[1]]] : [],
    )
  }
  if (typeof raw.notes === 'string' && raw.notes.trim()) hours.notes = raw.notes.trim()
  return Object.keys(hours).length > 0 ? hours : undefined
}

function toBranch(dto: PublicBranchDto): StoreBranch {
  const hasPoint = typeof dto.latitude === 'number' && typeof dto.longitude === 'number'
  return {
    id: dto.id,
    name: dto.name,
    address: dto.address ?? '',
    city: dto.city ?? undefined,
    department: dto.department ?? undefined,
    point: hasPoint ? { lat: Number(dto.latitude), lng: Number(dto.longitude) } : undefined,
    phone: dto.phone ?? undefined,
    whatsapp: dto.whatsapp ?? undefined,
    openingHours: toOpeningHours(dto.openingHours),
    openNow: dto.openNow ?? undefined,
    serviceIds: dto.serviceIds ?? [],
  }
}

function toReview(dto: ReviewResponseDto): Review {
  return {
    id: dto.id,
    rating: dto.rating,
    comment: dto.comment ?? undefined,
    aspects: dto.aspects ?? {},
    author: dto.author,
    verified: dto.verified,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt,
    reply: dto.reply ?? undefined,
  }
}

function toReviewSummary(dto: ReviewSummaryDto): ReviewSummary {
  const stars = (n: number) => dto.distribution?.[String(n)] ?? 0
  return {
    average: dto.average ?? undefined,
    count: dto.count,
    distribution: [stars(1), stars(2), stars(3), stars(4), stars(5)],
    aspects: dto.aspects ?? [],
    highlights: dto.highlights ?? [],
  }
}

/** Base de las rutas de reseñas de un producto o un almacén. */
function reviewsBase(target: ReviewTarget): string {
  return `/marketplace/${target.type === 'product' ? 'products' : 'stores'}/${encodeURIComponent(target.id)}/reviews`
}

/** `FrequentlyBoughtController`: `GET /marketplace/products/{id}/frequently-bought` (público, hasta 6). */
type FrequentlyBoughtDto = Omit<FrequentlyBoughtItem, 'image'> & { image: string | null }

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

  /**
   * GET con caché por ETag (`lib/http`): solo sin `Authorization`; home y anuncios nunca se guardan.
   * `credentials: 'include'` deja viajar la cookie `ap_area` (CORS ya permite credenciales con orígenes explícitos).
   */
  async function send(
    path: string,
    extraHeaders: Record<string, string>,
    token: string | undefined,
    signal?: AbortSignal,
  ): Promise<HttpResult<unknown> | Response> {
    try {
      return await cachedGet<unknown>(`${root}${path}`, {
        signal,
        credentials: 'include',
        headers: { Accept: 'application/json', ...extraHeaders, ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      })
    } catch (cause) {
      if (signal?.aborted) throw cause
      throw new MarketplaceError('No hay conexión con el servidor de Autopartco.', { cause })
    }
  }

  async function get<T>(
    path: string,
    extraHeaders: Record<string, string> = {},
    onHeaders?: (headers: Headers) => void,
    signal?: AbortSignal,
  ): Promise<T> {
    // Las rutas de productos son públicas, pero el precio depende de quién compra (precio para clientes frecuentes):
    // con sesión llevan el token.
    const token = ['/marketplace/products', '/marketplace/search', '/marketplace/home', '/marketplace/ads'].some((prefix) =>
      path.startsWith(prefix),
    )
      ? await bearer()
      : undefined
    let response = await send(path, extraHeaders, token, signal)
    // Un token vencido o revocado no debe impedir ver el catálogo: se reintenta como visitante.
    if (response instanceof Response && response.status === 401 && token) response = await send(path, extraHeaders, undefined, signal)
    if (!(response instanceof Response)) {
      onHeaders?.(response.headers)
      return response.data as T
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
      // Solo con reseñas: sin ellas no hay calificación que mostrar.
      rating: dto.rating != null && (dto.reviewCount ?? 0) > 0 ? dto.rating : undefined,
      reviewCount: dto.reviewCount != null && dto.reviewCount > 0 ? dto.reviewCount : undefined,
      universal: dto.universal === true,
      vehicleRules: dto.universal ? [] : (dto.vehicleRules ?? []),
      specifications: Object.entries(dto.attributes ?? {})
        .filter(([, value]) => value !== null && value !== undefined && value !== '')
        .map(([label, value]) => ({ label: humanizeCode(label), value: String(value) })),
    }
  }

  /** Ítem de búsqueda, inicio y anuncios: producto aplanado con distancia y datos del anuncio. */
  function toSearchItem(item: SearchItemDto, maps: Lookups): Product {
    return {
      ...toProduct(item, maps),
      distanceKm: item.distanceKm ?? undefined,
      sponsored: item.sponsored,
      adImpressionToken: item.adImpressionToken ?? undefined,
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
      if (query.store && query.branch) params.set('branchId', query.branch)
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

    async search(query, signal) {
      const maps = await lookups()
      const params = new URLSearchParams(vehicleQuery(query).slice(1))
      const text = query.q.trim()
      if (text) params.set('q', text)
      if (query.category) {
        const bySlug = [...maps.categories.values()].find((item) => item.slug === query.category)
        params.set('categoryId', bySlug?.id ?? query.category)
      }
      for (const id of query.manufacturers) params.append('manufacturerId', id)
      if (query.store) params.set('storeId', query.store)
      if (query.year !== undefined) params.set('year', String(query.year))
      if (query.minPrice !== undefined) params.set('priceMin', String(query.minPrice))
      if (query.maxPrice !== undefined) params.set('priceMax', String(query.maxPrice))
      if (query.inStock) params.set('inStock', 'true')
      if (query.onSale) params.set('onSale', 'true')
      if (query.cityCode) params.set('cityCode', query.cityCode)
      if (query.lat !== undefined && query.lng !== undefined) {
        params.set('lat', String(query.lat))
        params.set('lng', String(query.lng))
        if (query.radiusKm !== undefined) params.set('radiusKm', String(query.radiusKm))
      }
      if (query.minRating !== undefined) params.set('minRating', String(query.minRating))
      if (query.minProductRating !== undefined) params.set('minProductRating', String(query.minProductRating))
      if (query.universal) params.set('universal', 'true')
      params.set('sort', query.sort)
      params.set('page', String(Math.max(0, query.page - 1)))
      params.set('size', String(query.pageSize))
      // Las facetas se piden siempre: los conteos acompañan a cada cambio de filtro.
      params.set('facets', 'true')
      if (!query.interpret) params.set('interpret', 'false')
      const dto = await get<SearchResponseDto>(`${PATHS.search}?${params}`, { 'X-Session-Id': getAnonymousSessionId() }, undefined, signal)
      const toItem = (item: SearchItemDto) => toSearchItem(item, maps)
      const { interpreted, facets } = dto
      return {
        items: dto.items.map(toItem),
        sponsored: (dto.sponsored ?? []).map(toItem),
        total: dto.total,
        page: dto.page + 1,
        pageSize: dto.size,
        totalPages: Math.max(1, Math.ceil(dto.total / dto.size)),
        fuzzy: dto.fuzzy,
        interpreted: interpreted
          ? {
              text: interpreted.text,
              makeId: interpreted.makeId ?? undefined,
              makeName: interpreted.makeName ?? undefined,
              modelId: interpreted.modelId ?? undefined,
              modelName: interpreted.modelName ?? undefined,
              year: interpreted.year ?? undefined,
              cityCode: interpreted.cityCode ?? undefined,
              cityName: interpreted.cityName ?? undefined,
              reference: interpreted.reference ?? undefined,
            }
          : undefined,
        facets: facets
          ? {
              categories: facets.categories,
              manufacturers: facets.manufacturers,
              cities: facets.cities,
              price:
                facets.price && facets.price.min !== null && facets.price.max !== null
                  ? { min: facets.price.min, max: facets.price.max }
                  : undefined,
              inStock: facets.inStock,
              onSale: facets.onSale,
            }
          : undefined,
      }
    },

    async suggest(text, near, signal): Promise<SearchSuggestions> {
      const params = new URLSearchParams({ q: text.trim() })
      if (near) {
        params.set('lat', String(near.lat))
        params.set('lng', String(near.lng))
      }
      const [dto, maps] = await Promise.all([get<SuggestResponseDto>(`${PATHS.suggest}?${params}`, {}, undefined, signal), lookups()])
      return {
        suggestions: dto.suggestions.map((item) => ({ term: item.term, kind: item.kind as SuggestionKind })),
        products: dto.products.map((item) => toProduct(item, maps)),
      }
    },

    async getHome(query, signal) {
      const params = new URLSearchParams()
      if (query.lat !== undefined && query.lng !== undefined) {
        params.set('lat', String(query.lat))
        params.set('lng', String(query.lng))
      }
      if (query.cityCode) params.set('cityCode', query.cityCode)
      const qs = params.toString()
      const [dto, maps] = await Promise.all([
        get<HomeResponseDto>(`${PATHS.home}${qs ? `?${qs}` : ''}`, { 'X-Session-Id': getAnonymousSessionId() }, undefined, signal),
        lookups(),
      ])
      return {
        stats: dto.stats,
        popularSearches: dto.popularSearches ?? [],
        categories: dto.categories ?? [],
        nearby: (dto.nearby ?? []).map((item) => toSearchItem(item, maps)),
        featured: (dto.featured ?? []).map((item) => toSearchItem(item, maps)),
      }
    },

    async listAds(query, signal) {
      const params = new URLSearchParams({ placement: query.placement })
      if (query.placement === 'CATEGORY_TOP') params.set('categoryId', query.categoryId)
      else params.set('productId', query.productId)
      const [dto, maps] = await Promise.all([
        get<AdsResponseDto>(`${PATHS.ads}?${params}`, { 'X-Session-Id': getAnonymousSessionId() }, undefined, signal),
        lookups(),
      ])
      return (dto.items ?? []).map((item) => toSearchItem(item, maps))
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
        tagline: dto.tagline ?? undefined,
        verified: dto.verified ?? false,
        whatsappEnabled: dto.whatsappEnabled ?? false,
        whatsappMessage: dto.whatsappMessage ?? undefined,
        services: (dto.services ?? []).map((service) => ({
          id: service.id,
          category: service.category as ServiceCategory,
          name: service.name,
          description: service.description ?? undefined,
          priceFrom: service.priceFrom ?? undefined,
          durationMinutes: service.durationMinutes ?? undefined,
          branchIds: service.branchIds ?? [],
        })),
        featuredProductIds: dto.featuredProductIds ?? [],
      }
    },

    async listStoreBranches(storeId) {
      const branches = await get<PublicBranchDto[]>(PATHS.storeBranches(storeId))
      return branches.map(toBranch)
    },

    async getReviewSummary(target) {
      return toReviewSummary(await get<ReviewSummaryDto>(`${reviewsBase(target)}/summary`))
    },

    async listReviews(target, query) {
      const params = new URLSearchParams({ page: String(query.page), size: '10', sort: query.sort })
      if (query.stars !== undefined) params.set('stars', String(query.stars))
      if (query.withComment) params.set('withComment', 'true')
      const dto = await get<PageResponseDto<ReviewResponseDto>>(`${reviewsBase(target)}?${params}`)
      return { items: dto.items.map(toReview), page: dto.page, totalPages: dto.totalPages, total: dto.total }
    },

    async getProductReviewEligibility(productId) {
      const token = await bearer()
      if (!token) return { eligibleOrderIds: [] }
      const dto = await apiRequest<ProductReviewEligibilityDto>(`${reviewsBase({ type: 'product', id: productId })}/eligibility`, { token })
      return {
        eligibleOrderIds: dto.eligibleOrderIds ?? [],
        existingReviewId: dto.existingReviewId ?? undefined,
        editableUntil: dto.editableUntil ?? undefined,
      }
    },

    async getReviewEligibility(storeId) {
      const token = await bearer()
      if (!token) return []
      const dto = await apiRequest<{ eligibleOrderIds: string[] }>(`${PATHS.storeReviews(storeId)}/eligibility`, { token })
      return dto.eligibleOrderIds
    },
  }
}
