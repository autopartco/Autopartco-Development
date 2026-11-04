import type { VehicleRule } from '../api/types'
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

/** «Toyota · todos los modelos», «Renault · Duster · todas las versiones», «Renault · Duster · 2.0 4x4». */
export function ruleLabel(rule: VehicleRule): string {
  const model = rule.modelName ?? 'todos los modelos'
  const version = rule.modelName ? (rule.versionLabel ?? 'todas las versiones') : undefined
  return [rule.makeName, model, version].filter(Boolean).join(' · ')
}
