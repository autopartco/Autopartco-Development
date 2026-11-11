import { portalRequest, storePath } from './http/portal-http'

/** Ubicaciones de anuncio (`AdPlacement`). */
export type AdPlacement = 'SEARCH_TOP' | 'HOME_FEATURED' | 'CATEGORY_TOP' | 'PRODUCT_RELATED'

export type AdCampaignStatus = 'DRAFT' | 'PENDING_PAYMENT' | 'SCHEDULED' | 'ACTIVE' | 'PAUSED' | 'FINISHED' | 'REJECTED' | 'CANCELLED'

export interface AdPlacementPrice {
  placement: AdPlacement
  dailyPrice: number
  active: boolean
}

export interface AdTargeting {
  keywords: string[]
  categoryIds: string[]
  makeIds: string[]
  modelIds: string[]
  cityCodes: string[]
}

/** `AdCampaignView`. Todos los importes y el estado los calcula el servidor. */
export interface AdCampaign {
  id: string
  storeId: string
  name: string
  status: AdCampaignStatus
  pausedBy: 'STORE' | 'ADMIN' | null
  placements: AdPlacement[]
  productIds: string[]
  /** `yyyy-MM-dd`. */
  startsOn: string
  endsOn: string
  days: number
  targeting: AdTargeting
  totalPrice: number
  paidAt: string | null
  rejectedReason: string | null
  requiresManualRefund: boolean
  createdAt: string
  updatedAt: string
}

export interface AdCampaignInput {
  name: string
  productIds: string[]
  placements: AdPlacement[]
  startsOn: string
  endsOn: string
  targeting: AdTargeting
}

export interface AdCheckout {
  reference: string
  checkoutUrl: string
  amountInCents: number
  currency: string
  campaignId: string
}

interface AdStatsCounters {
  impressions: number
  clicks: number
  whatsappClicks: number
  addToCart: number
  orders: number
  revenue: number
}

export interface AdStatsDaily extends AdStatsCounters {
  /** `yyyy-MM-dd`. */
  day: string
  reach: number
}

export interface AdStatsByPlacement extends AdStatsCounters {
  placement: AdPlacement
  /** 0..1. */
  ctr: number
}

export interface AdStatsByProduct extends AdStatsCounters {
  productId: string
  productName: string
  ctr: number
}

export interface AdStats {
  impressions: number
  reach: number
  clicks: number
  ctr: number
  whatsappClicks: number
  addToCart: number
  orders: number
  revenue: number
  costPerClick: number
  roas: number
  cost: number
  from: string
  to: string
  daily: AdStatsDaily[]
  byPlacement: AdStatsByPlacement[]
  byProduct: AdStatsByProduct[]
}

const base = (storeId: string, suffix = '') => storePath(storeId, `/ads${suffix}`)
const enc = encodeURIComponent

export const adsApi = {
  prices: (storeId: string) => portalRequest<AdPlacementPrice[]>(base(storeId, '/prices')),
  list: (storeId: string, status?: AdCampaignStatus) =>
    portalRequest<AdCampaign[]>(base(storeId, `/campaigns${status ? `?status=${enc(status)}` : ''}`)),
  get: (storeId: string, id: string) => portalRequest<AdCampaign>(base(storeId, `/campaigns/${enc(id)}`)),
  create: (storeId: string, input: AdCampaignInput) => portalRequest<AdCampaign>(base(storeId, '/campaigns'), { method: 'POST', body: input }),
  update: (storeId: string, id: string, input: AdCampaignInput) =>
    portalRequest<AdCampaign>(base(storeId, `/campaigns/${enc(id)}`), { method: 'PUT', body: input }),
  remove: (storeId: string, id: string) => portalRequest<void>(base(storeId, `/campaigns/${enc(id)}`), { method: 'DELETE' }),
  checkout: (storeId: string, id: string) => portalRequest<AdCheckout>(base(storeId, `/campaigns/${enc(id)}/checkout`), { method: 'POST' }),
  syncPayment: (storeId: string, id: string, transactionId: string) =>
    portalRequest<unknown>(base(storeId, `/campaigns/${enc(id)}/payment/sync`), { method: 'POST', body: { transactionId } }),
  pause: (storeId: string, id: string) => portalRequest<AdCampaign>(base(storeId, `/campaigns/${enc(id)}/pause`), { method: 'POST' }),
  resume: (storeId: string, id: string) => portalRequest<AdCampaign>(base(storeId, `/campaigns/${enc(id)}/resume`), { method: 'POST' }),
  cancel: (storeId: string, id: string) => portalRequest<AdCampaign>(base(storeId, `/campaigns/${enc(id)}/cancel`), { method: 'POST' }),
  stats: (storeId: string, id: string, from?: string, to?: string) => {
    const query = new URLSearchParams()
    if (from) query.set('from', from)
    if (to) query.set('to', to)
    const text = query.toString()
    return portalRequest<AdStats>(base(storeId, `/campaigns/${enc(id)}/stats${text ? `?${text}` : ''}`))
  },
}
