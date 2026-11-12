import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { Page } from '@/features/portal/api/types'
import type { AdminReview, AdminReviewService } from '../review-types'

const id = (value: string) => encodeURIComponent(value)

/** `ReviewAdminController`: `/admin/reviews`. */
export function createHttpAdminReviewService(): AdminReviewService {
  return {
    list(query) {
      const params = new URLSearchParams({ page: String(query.page), size: '20' })
      if (query.hidden !== undefined) params.set('hidden', String(query.hidden))
      if (query.type) params.set('type', query.type)
      return portalRequest<Page<AdminReview>>(`/admin/reviews?${params}`)
    },
    hide: (type, reviewId, reason) => portalRequest<AdminReview>(`/admin/reviews/${type}/${id(reviewId)}/hide`, { method: 'POST', body: { reason } }),
    unhide: (type, reviewId) => portalRequest<AdminReview>(`/admin/reviews/${type}/${id(reviewId)}/unhide`, { method: 'POST' }),
  }
}
