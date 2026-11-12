import type { Review, ReviewTarget } from '@/features/marketplace/api/types'

/**
 * Contrato de reseñas del comprador (`PendingReviewsController`, `ProductReviewController`, `StoreReviewController`).
 * Todo lo decide el servidor: elegibilidad, ventana de edición y límites.
 */

/** `GET /account/reviews/pending`: algo por calificar de un pedido entregado. */
export interface PendingReview {
  orderId: string
  orderNumber: number
  deliveredAt: string
  /** `reviewId`: reseña vigente del almacén en este pedido, para editarla. */
  store: { id: string; name: string; reviewed: boolean; reviewId?: string | null }
  products: PendingProduct[]
}

export interface PendingProduct {
  productId: string
  name: string
  /** Miniatura de la imagen principal. */
  image: string | null
  reviewed: boolean
  /** Reseña vigente del producto, para editarla. */
  reviewId?: string | null
}

/** Lo que el comprador escribe. `aspects`: código de categoría → 1–5, solo las que puntuó. */
export interface ReviewDraft {
  rating: number
  comment?: string
  aspects: Record<string, number>
}

export interface ReviewsService {
  /** `true` con la fuente `mock`. */
  readonly simulated: boolean
  pending(): Promise<PendingReview[]>
  /** 403 `REVIEW_NOT_ELIGIBLE`, 409 `REVIEW_ALREADY_EXISTS` / `REVIEW_NOT_ALLOWED`, 400 `REVIEW_*`, 429 `REVIEW_RATE_LIMITED`. */
  create(target: ReviewTarget, orderId: string, draft: ReviewDraft): Promise<Review>
  /** Solo el autor y dentro de 30 días: si no, 409 `REVIEW_EDIT_WINDOW_CLOSED`. */
  update(target: ReviewTarget, reviewId: string, draft: ReviewDraft): Promise<Review>
  /** Quita la propia reseña (sin ventana de tiempo). */
  discard(target: ReviewTarget, reviewId: string): Promise<void>
  /** Reseña propia para precargar el formulario: `GET .../reviews/mine` (en el almacén, con `?orderId=`). 404 `REVIEW_NOT_FOUND`. */
  mine(target: ReviewTarget, orderId?: string): Promise<Review>
}
