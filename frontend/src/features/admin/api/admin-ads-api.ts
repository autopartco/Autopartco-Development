import type { AdCampaign, AdCampaignStatus, AdPlacementPrice } from '@/features/portal/api/ads-api'
import { portalRequest } from '@/features/portal/api/http/portal-http'

const enc = encodeURIComponent

/** Moderación y tarifas de publicidad (`/admin/ads`, solo ROOT). */
export const adminAdsApi = {
  list: (status?: AdCampaignStatus, storeId?: string) => {
    const query = new URLSearchParams()
    if (status) query.set('status', status)
    if (storeId) query.set('storeId', storeId)
    const text = query.toString()
    return portalRequest<AdCampaign[]>(`/admin/ads/campaigns${text ? `?${text}` : ''}`)
  },
  reject: (id: string, reason: string) => portalRequest<AdCampaign>(`/admin/ads/campaigns/${enc(id)}/reject`, { method: 'POST', body: { reason } }),
  pause: (id: string) => portalRequest<AdCampaign>(`/admin/ads/campaigns/${enc(id)}/pause`, { method: 'POST' }),
  resume: (id: string) => portalRequest<AdCampaign>(`/admin/ads/campaigns/${enc(id)}/resume`, { method: 'POST' }),
  prices: () => portalRequest<AdPlacementPrice[]>('/admin/ads/prices'),
  savePrices: (prices: AdPlacementPrice[]) => portalRequest<AdPlacementPrice[]>('/admin/ads/prices', { method: 'PUT', body: { prices } }),
}
