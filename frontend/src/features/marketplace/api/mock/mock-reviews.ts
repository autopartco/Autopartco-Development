/**
 * Reseñas de ejemplo para el origen simulado. Solo dos repuestos tienen opiniones; el resto
 * (y todos los almacenes) quedan sin reseñas, para ver también el estado vacío.
 * Resumen y tarjetas salen de la misma lista, así que siempre coinciden.
 */
import type { Review, ReviewListQuery, ReviewPage, ReviewSummary } from '../types'

const PAGE_SIZE = 10

function review(
  id: string,
  rating: number,
  author: string,
  createdAt: string,
  extra: Partial<Pick<Review, 'comment' | 'aspects' | 'reply'>> = {},
): Review {
  return { id, rating, author, verified: true, createdAt, updatedAt: createdAt, aspects: {}, ...extra }
}

const SAMPLE_REVIEWS: Review[] = [
  review('mock-rev-1', 5, 'Carlos M.', '2026-10-28T15:20:00Z', {
    comment: 'Llegaron en dos días y encajaron perfecto en mi Spark. Frenan mucho mejor que las anteriores.',
    aspects: { QUALITY: 5, FIT: 5, AS_DESCRIBED: 5, VALUE: 4, INSTALL: 5 },
    reply: { text: 'Gracias por tu compra, Carlos. Recuerda revisar el nivel del líquido de frenos en el próximo cambio.', repliedAt: '2026-10-29T13:05:00Z' },
  }),
  review('mock-rev-2', 5, 'Andrea P.', '2026-10-19T10:02:00Z', {
    comment: 'Exactamente la referencia que necesitaba. El mecánico las instaló en una hora.',
    aspects: { QUALITY: 5, FIT: 5, AS_DESCRIBED: 5, INSTALL: 5 },
  }),
  review('mock-rev-3', 4, 'Julián R.', '2026-10-02T18:44:00Z', {
    comment: 'Buen producto y buen precio. Las pastillas hacen un poco de ruido los primeros días.',
    aspects: { QUALITY: 4, FIT: 5, VALUE: 5 },
  }),
  review('mock-rev-4', 5, 'Marcela G.', '2026-09-21T09:30:00Z', { aspects: { QUALITY: 5, FIT: 4, AS_DESCRIBED: 5 } }),
  review('mock-rev-5', 4, 'Sebastián L.', '2026-09-08T21:10:00Z', {
    comment: 'Cumplen lo prometido.',
    aspects: { FIT: 4, VALUE: 4 },
  }),
  review('mock-rev-6', 3, 'Diana T.', '2026-08-30T12:00:00Z', {
    comment: 'Sirven, pero esperaba una caja más cuidada. El producto en sí está bien.',
    aspects: { QUALITY: 3, AS_DESCRIBED: 4 },
  }),
]

/** Repuestos de ejemplo con opiniones: la primera pastilla y las cerámicas. */
const REVIEWED_PRODUCT_IDS = new Set(['rep-001', 'rep-002'])

function ratingOf(reviews: Review[]): number | undefined {
  if (reviews.length === 0) return undefined
  return Math.round((reviews.reduce((sum, item) => sum + item.rating, 0) / reviews.length) * 10) / 10
}

function reviewsOf(type: 'product' | 'store', id: string): Review[] {
  return type === 'product' && REVIEWED_PRODUCT_IDS.has(id) ? SAMPLE_REVIEWS : []
}

/** Calificación para mostrar en la tarjeta; `undefined` sin reseñas. */
export function mockProductRating(productId: string): { rating: number; reviewCount: number } | undefined {
  const reviews = reviewsOf('product', productId)
  const rating = ratingOf(reviews)
  return rating === undefined ? undefined : { rating, reviewCount: reviews.length }
}

export function mockReviewSummary(type: 'product' | 'store', id: string): ReviewSummary {
  const reviews = reviewsOf(type, id)
  const distribution: ReviewSummary['distribution'] = [0, 0, 0, 0, 0]
  const perAspect = new Map<string, number[]>()
  for (const item of reviews) {
    distribution[item.rating - 1] = (distribution[item.rating - 1] ?? 0) + 1
    for (const [aspect, value] of Object.entries(item.aspects)) perAspect.set(aspect, [...(perAspect.get(aspect) ?? []), value])
  }
  const aspects = [...perAspect.entries()].map(([aspect, values]) => ({
    aspect,
    average: Math.round((values.reduce((a, b) => a + b, 0) / values.length) * 10) / 10,
    count: values.length,
  }))
  // Mismas reglas que el servidor: al menos 3 calificaciones y promedio de 4,3 o más; máximo 3, de mayor a menor.
  const highlights = aspects
    .filter((item) => item.count >= 3 && item.average >= 4.3)
    .toSorted((a, b) => b.average - a.average)
    .slice(0, 3)
    .map((item) => item.aspect)
  return { average: ratingOf(reviews), count: reviews.length, distribution, aspects, highlights }
}

export function mockReviewPage(type: 'product' | 'store', id: string, query: ReviewListQuery): ReviewPage {
  let items = reviewsOf(type, id).filter(
    (item) => (query.stars === undefined || item.rating === query.stars) && (!query.withComment || Boolean(item.comment)),
  )
  items = items.toSorted((a, b) =>
    query.sort === 'highest'
      ? b.rating - a.rating || b.createdAt.localeCompare(a.createdAt)
      : query.sort === 'lowest'
        ? a.rating - b.rating || b.createdAt.localeCompare(a.createdAt)
        : b.createdAt.localeCompare(a.createdAt),
  )
  const totalPages = Math.ceil(items.length / PAGE_SIZE)
  const start = (query.page - 1) * PAGE_SIZE
  return { items: items.slice(start, start + PAGE_SIZE), page: query.page, totalPages, total: items.length }
}
