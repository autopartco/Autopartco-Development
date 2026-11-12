import { apiRequest } from '@/lib/api'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'
import type { ReviewResponseDto } from '@/features/marketplace/api/dto'
import type { Review, ReviewTarget } from '@/features/marketplace/api/types'
import type { PendingReview, ReviewDraft, ReviewsService } from '../types'

const id = (value: string) => encodeURIComponent(value)

function reviewsBase(target: ReviewTarget): string {
  return `/marketplace/${target.type === 'product' ? 'products' : 'stores'}/${id(target.id)}/reviews`
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

/** Sin comentario o sin categorías se omiten: el servidor las trata como «no puntuadas». */
function payload(draft: ReviewDraft) {
  const comment = draft.comment?.trim()
  return { rating: draft.rating, comment: comment || undefined, aspects: Object.keys(draft.aspects).length > 0 ? draft.aspects : undefined }
}

/** `GET /account/reviews/pending` y las rutas de reseñas de producto y de almacén. */
export function createHttpReviewsService(): ReviewsService {
  return {
    simulated: false,
    async pending() {
      return apiRequest<PendingReview[]>('/account/reviews/pending', { token: await requireAccessToken() })
    },
    async create(target, orderId, draft) {
      const token = await requireAccessToken()
      return toReview(await apiRequest<ReviewResponseDto>(reviewsBase(target), { method: 'POST', body: { orderId, ...payload(draft) }, token }))
    },
    async update(target, reviewId, draft) {
      const token = await requireAccessToken()
      return toReview(await apiRequest<ReviewResponseDto>(`${reviewsBase(target)}/${id(reviewId)}`, { method: 'PUT', body: payload(draft), token }))
    },
    async discard(target, reviewId) {
      const token = await requireAccessToken()
      await apiRequest<void>(`${reviewsBase(target)}/${id(reviewId)}`, { method: 'DELETE', token })
    },
  }
}
