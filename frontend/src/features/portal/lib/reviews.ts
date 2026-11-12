import { invalidateAsync } from '@/lib/use-async'
import { getReviewPortalService } from '../api/review-service'
import { useStoreQuery } from '../hooks'

/** Resumen de reseñas del almacén activo (promedio, sin responder, productos). Comparte caché entre pantallas. */
export function usePortalReviewSummary() {
  return useStoreQuery('reviews:summary', async (storeId) => (await getReviewPortalService()).summary(storeId))
}

/** Tras responder o borrar una respuesta: descarta lista y resumen de reseñas de este almacén. */
export function invalidatePortalReviews(storeId: string): void {
  invalidateAsync(`portal:${storeId}:reviews`)
}
