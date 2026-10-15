import type { VehicleFit } from '../api/types'
import { searchUrl } from './product-query'

export function productPath(id: string): string {
  return `/producto/${encodeURIComponent(id)}`
}

export function categoryPath(slug: string): string {
  return `/categoria/${encodeURIComponent(slug)}`
}

export function storeProductsPath(storeId: string): string {
  return searchUrl({ store: storeId })
}

/** "Chevrolet Spark GT 2011–2018". */
export function vehicleLabel(fit: VehicleFit, withYears = true): string {
  const years = withYears && fit.yearFrom && fit.yearTo ? ` ${fit.yearFrom}–${fit.yearTo}` : ''
  return `${fit.make} ${fit.model}${years}`
}
