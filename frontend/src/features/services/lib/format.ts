import type { StoreBranch, StoreProfile, StoreService } from '@/features/marketplace/api/types'
import type { ServiceSummary } from '../api/types'

/** «45 min», «1 h», «1 h 30 min», «2 d». */
export function formatDuration(minutes: number): string {
  if (minutes < 60) return `${minutes} min`
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours >= 24) return `${Math.round(hours / 24)} d`
  return rest === 0 ? `${hours} h` : `${hours} h ${rest} min`
}

const dayFormatter = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', timeZone: 'America/Bogota' })

/** «30 nov» a partir del ISO de vencimiento de la promoción; `undefined` si la fecha no es válida. */
export function formatPromotionEnd(iso: string): string | undefined {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? undefined : dayFormatter.format(date).replace('.', '')
}

/** Servicio del perfil de la tienda llevado a la misma forma que una tarjeta del servidor (la tienda ya se conoce). */
export function storeServiceToSummary(service: StoreService, store: StoreProfile, branches: StoreBranch[]): ServiceSummary {
  const branch = branches.find((item) => service.branchIds.includes(item.id))
  return {
    id: service.id,
    name: service.name,
    category: service.category,
    imageUrl: service.imageUrl,
    priceFrom: service.priceFrom,
    basePriceFrom: service.basePriceFrom,
    promotion: service.promotion,
    durationMinutes: service.durationMinutes,
    bookingEnabled: service.bookingEnabled,
    store: { id: store.id, name: store.name, logoUrl: store.logoUrl, rating: store.ratingAverage, businessType: store.businessType },
    branch: branch
      ? { id: branch.id, name: branch.name, city: branch.city, address: branch.address, point: branch.point, openNow: branch.openNow }
      : undefined,
  }
}
