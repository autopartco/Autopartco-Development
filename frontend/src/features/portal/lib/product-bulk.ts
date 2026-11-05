import { DEFAULT_MIN_STOCK, type ProductBulkAction, type ProductRow } from '../api/types'
import { humanizeCode } from './labels'

/** Máximo de productos por acción masiva (igual que el servidor). */
export const BULK_MAX = 500

/** Máximo de productos por solicitud al eliminar (`BULK_DELETE_LIMIT`, 400). */
export const BULK_DELETE_MAX = 50

export const BULK_DELETE_LIMIT_TEXT = `Elimina hasta ${BULK_DELETE_MAX} productos a la vez.`

/** Umbral de stock bajo del listado: el del servidor si lo trae, si no `DEFAULT_MIN_STOCK`. */
export function minStockOf(row: ProductRow): number {
  return row.minStock ?? DEFAULT_MIN_STOCK
}

/** Stock en o por debajo del mínimo (incluye agotados), como `GET /inventory/low-stock`. */
export function isLowStock(row: ProductRow): boolean {
  return row.quantity <= minStockOf(row)
}

/** Motivo legible de un producto omitido (`skipped[].code`). */
const SKIP_REASON: Record<string, string> = {
  NO_IMAGES: 'le falta al menos una imagen',
  NO_VEHICLES: 'le faltan los vehículos compatibles o marcarlo como universal',
  MANUFACTURER_INVALID: 'hay que revisar el fabricante',
  SUBSCRIPTION_INACTIVE: 'tu suscripción no está activa',
  PRODUCT_NOT_PUBLISHABLE: 'le falta información para publicarse',
  PRODUCT_NOT_FOUND: 'ya no existe en tu almacén',
  LISTING_NOT_FOUND: 'no está en esa sucursal',
  ALREADY_PUBLISHED: 'ya estaba publicado',
  ALREADY_UNPUBLISHED: 'ya estaba retirado',
}

export function skipReason(code: string): string {
  return SKIP_REASON[code] ?? humanizeCode(code).toLowerCase()
}

/** Verbo en pasado para el resumen de la acción. */
export const BULK_DONE: Record<ProductBulkAction, { one: string; many: string }> = {
  PUBLISH: { one: 'producto publicado', many: 'productos publicados' },
  UNPUBLISH: { one: 'producto retirado del marketplace', many: 'productos retirados del marketplace' },
  DELETE: { one: 'producto eliminado', many: 'productos eliminados' },
  PRICE: { one: 'precio actualizado', many: 'precios actualizados' },
}
