import type { ReviewSummary } from '@/features/marketplace/api/types'
import type { Page } from './types'

/**
 * Reseñas del almacén y de sus productos en el portal (`StoreReviewsController`): lectura con `STORE_READ`,
 * respuesta con `STORE_UPDATE`.
 */

/** En las rutas va en minúsculas; en las respuestas, el nombre del enum. */
export type PortalReviewType = 'store' | 'product'

export interface PortalReviewReply {
  text: string
  repliedAt: string
}

export interface PortalReview {
  id: string
  type: 'STORE' | 'PRODUCT'
  rating: number
  comment: string | null
  /** Código de categoría → 1–5. */
  aspects: Record<string, number>
  /** «Ana P.»: nombre + inicial del apellido. */
  author: string
  createdAt: string
  updatedAt: string
  reply: PortalReviewReply | null
  /** La ocultó Autopartco: no cuenta en promedios ni se muestra al público. El motivo no se expone. */
  hidden: boolean
  productId: string | null
  productName: string | null
}

export interface PortalProductRating {
  productId: string
  name: string
  average: number
  count: number
}

/** `PortalReviewSummary`: el resumen del almacén más lo que pide acción. */
export interface PortalReviewSummary {
  summary: ReviewSummary
  /** Reseñas visibles sin respuesta (de almacén y de productos). */
  unanswered: number
  topProducts: PortalProductRating[]
  bottomProducts: PortalProductRating[]
}

export interface PortalReviewQuery {
  type?: PortalReviewType
  /** `false` = solo las que no tienen respuesta. */
  replied?: boolean
  stars?: number
  page: number
}

export interface ReviewPortalService {
  /** `GET /stores/{id}/reviews?type&replied&stars&page`. 400 `REVIEW_TYPE_INVALID` / `REVIEW_STARS_INVALID`. */
  list(storeId: string, query: PortalReviewQuery): Promise<Page<PortalReview>>
  /** `GET /stores/{id}/reviews/summary`. */
  summary(storeId: string): Promise<PortalReviewSummary>
  /** `PUT /stores/{id}/reviews/{type}/{reviewId}/reply`. 400 `REVIEW_LINKS_NOT_ALLOWED`, 404 `REVIEW_NOT_FOUND`. */
  reply(storeId: string, type: PortalReviewType, reviewId: string, text: string): Promise<PortalReview>
  /** `DELETE /stores/{id}/reviews/{type}/{reviewId}/reply`. */
  removeReply(storeId: string, type: PortalReviewType, reviewId: string): Promise<void>
}
