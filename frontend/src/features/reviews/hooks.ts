import { invalidateAsync, useAsync } from '@/lib/use-async'
import { getReviewsService } from './api/reviews-service'

/** Prefijo `portal:` para que se descarte al cerrar sesión (ver RequireSession). */
const PENDING_KEY = 'portal:buyer-reviews:pending'

/** Pedidos entregados con algo por calificar (últimos 90 días). */
export function usePendingReviews() {
  return useAsync(PENDING_KEY, () => getReviewsService().then((service) => service.pending()))
}

/** Tras crear, editar o borrar una reseña: descarta pendientes, listas, resúmenes y elegibilidad en memoria. */
export function invalidateReviews(): void {
  invalidateAsync(PENDING_KEY)
  invalidateAsync('reviews:')
  invalidateAsync('review-summary:')
  invalidateAsync('product-review-eligibility:')
  invalidateAsync('store-review-eligibility:')
  invalidateAsync('store-profile:')
}

/** Cantidad de elementos sin calificar (almacén y productos) entre los pedidos pendientes. */
export function countPending(pending: { store: { reviewed: boolean }; products: { reviewed: boolean }[] }[]): number {
  return pending.reduce((sum, item) => sum + (item.store.reviewed ? 0 : 1) + item.products.filter((product) => !product.reviewed).length, 0)
}
