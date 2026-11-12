import type { ReviewSummary } from '@/features/marketplace/api/types'
import type { PortalProductRating, PortalReview, ReviewPortalService } from '../review-types'
import type { Page } from '../types'
import { portalRequest, storePath } from './portal-http'

/** `PortalReviewSummary` tal como lo envía el servidor. */
interface SummaryDto {
  average: number | null
  count: number
  distribution: Record<string, number>
  aspects: { aspect: string; average: number; count: number }[]
  highlights: string[]
  unanswered: number
  topProducts: PortalProductRating[]
  bottomProducts: PortalProductRating[]
}

const id = (value: string) => encodeURIComponent(value)

function toSummary(dto: SummaryDto): ReviewSummary {
  const stars = (n: number) => dto.distribution?.[String(n)] ?? 0
  return {
    average: dto.average ?? undefined,
    count: dto.count,
    distribution: [stars(1), stars(2), stars(3), stars(4), stars(5)],
    aspects: dto.aspects ?? [],
    highlights: dto.highlights ?? [],
  }
}

/** `StoreReviewsController`: `/stores/{storeId}/reviews`. */
export function createHttpReviewPortalService(): ReviewPortalService {
  return {
    list(storeId, query) {
      const params = new URLSearchParams({ page: String(query.page) })
      if (query.type) params.set('type', query.type)
      if (query.replied !== undefined) params.set('replied', String(query.replied))
      if (query.stars !== undefined) params.set('stars', String(query.stars))
      return portalRequest<Page<PortalReview>>(storePath(storeId, `/reviews?${params}`))
    },
    async summary(storeId) {
      const dto = await portalRequest<SummaryDto>(storePath(storeId, '/reviews/summary'))
      return {
        summary: toSummary(dto),
        unanswered: dto.unanswered ?? 0,
        topProducts: dto.topProducts ?? [],
        bottomProducts: dto.bottomProducts ?? [],
      }
    },
    reply: (storeId, type, reviewId, text) =>
      portalRequest<PortalReview>(storePath(storeId, `/reviews/${type}/${id(reviewId)}/reply`), { method: 'PUT', body: { text } }),
    async removeReply(storeId, type, reviewId) {
      await portalRequest<void>(storePath(storeId, `/reviews/${type}/${id(reviewId)}/reply`), { method: 'DELETE' })
    },
  }
}
