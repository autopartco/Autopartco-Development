import type { Page } from '@/features/portal/api/types'

/** Moderación de reseñas (`ReviewAdminController`, solo ROOT; cada acción queda en auditoría). */

export interface AdminReview {
  id: string
  type: 'STORE' | 'PRODUCT'
  rating: number
  comment: string | null
  /** «Ana P.»: nombre + inicial del apellido (nunca el correo). */
  author: string
  createdAt: string
  reply: { text: string; repliedAt: string } | null
  storeId: string
  storeName: string
  productId: string | null
  productName: string | null
  hidden: boolean
  hiddenAt: string | null
  hiddenReason: string | null
}

export interface AdminReviewQuery {
  /** `undefined` = visibles y ocultas. */
  hidden?: boolean
  type?: 'store' | 'product'
  page: number
}

export interface AdminReviewService {
  /** `GET /admin/reviews?hidden&type&page&size`. */
  list(query: AdminReviewQuery): Promise<Page<AdminReview>>
  /** `POST /admin/reviews/{type}/{id}/hide`. Motivo de 3 a 200 caracteres; 409 `REVIEW_ALREADY_HIDDEN`. */
  hide(type: 'store' | 'product', reviewId: string, reason: string): Promise<AdminReview>
  /** `POST /admin/reviews/{type}/{id}/unhide`. 409 `REVIEW_NOT_HIDDEN`. */
  unhide(type: 'store' | 'product', reviewId: string): Promise<AdminReview>
}
