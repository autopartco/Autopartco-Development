import { ApiError } from '@/lib/api'
import { formatNumber } from '@/lib/format'
import type { MovementReason } from '../api/types'
import { actionErrorMessage } from './errors'

/** Mensaje en español para cada código de error de inventario del servidor. */
const ERROR_TEXT: Record<string, string> = {
  // Transferencias
  SAME_BRANCH: 'La sucursal de origen y la de destino deben ser distintas.',
  DUPLICATE_PRODUCT: 'Un producto aparece más de una vez. Déjalo una sola vez con la cantidad total.',
  BRANCH_NOT_FOUND: 'Alguna de las sucursales ya no está activa en tu almacén. Recarga la página y elige de nuevo.',
  LISTING_NOT_FOUND: 'Algún producto no está en la sucursal de origen. Recarga la página y revisa la selección.',
  TRANSFER_EMPTY: 'La sucursal de origen no tiene existencias para transferir.',
  TRANSFER_TOO_LARGE: 'La sucursal de origen tiene demasiados productos para moverlos de una vez. Transfiere por selección, en tandas más pequeñas.',
  TRANSFER_CONFLICT: 'Otra operación estaba cambiando estas existencias. No se perdió nada: vuelve a intentarlo.',
  TRANSFER_NOT_FOUND: 'No encontramos esa transferencia en tu almacén.',
  INSUFFICIENT_STOCK: 'No hay existencias suficientes para esta operación.',
  INVALID_QUANTITY: 'La cantidad no es válida. Usa números enteros mayores que 0 (en un ajuste directo, 0 o más).',
  // Ajustes
  REASON_DIRECTION: 'Ese motivo no corresponde a una entrada o a una salida de existencias. Elige otro motivo o cambia la cantidad.',
  INVALID_REASON: 'El motivo del ajuste no es válido. Elige uno de la lista.',
  NO_CHANGES: 'Las existencias ya tienen ese valor; no hay nada que cambiar.',
  // Historial
  INVALID_DATE_RANGE: 'La fecha inicial debe ser anterior o igual a la final.',
  // CSV
  FILE_REQUIRED: 'Elige un archivo CSV para continuar.',
  FILE_TOO_LARGE: 'El archivo supera el máximo de 2 MB. Divídelo en partes más pequeñas.',
  EMPTY_FILE: 'El archivo no tiene datos.',
  INVALID_CSV: 'No pudimos leer el archivo. Guárdalo como CSV separado por punto y coma (;) y vuelve a subirlo.',
  INVALID_HEADER: 'La primera fila no tiene las columnas esperadas. Descarga el inventario actual y usa esa hoja como modelo.',
  TOO_MANY_ROWS: 'El archivo supera las 5.000 filas. Divídelo en partes más pequeñas.',
  // Stock mínimo
  VALIDATION_ERROR: 'Revisa los datos: alguno no es válido.',
}

/** Códigos de las filas con error de la importación CSV. */
const ROW_ERROR_TEXT: Record<string, string> = {
  MISSING_BRANCH: 'Falta la sucursal.',
  MISSING_PRODUCT_KEY: 'Falta el SKU o la referencia.',
  INVALID_STOCK: 'El stock debe ser un número entero de 0 o más.',
  INVALID_MIN_STOCK: 'El stock mínimo debe ser un número entero de 0 o más.',
  INVALID_PRICE: 'El precio debe ser un valor en pesos mayor que 0.',
  PRODUCT_NOT_FOUND: 'No encontramos ese producto en esa sucursal.',
  AMBIGUOUS_PRODUCT: 'Hay más de un producto con ese SKU o referencia en la sucursal.',
  STOCK_CHANGED: 'El stock cambió desde la exportación y no alcanza; exporta de nuevo.',
  DUPLICATE_ROW: 'El producto ya aparece en otra fila para esa sucursal.',
}

/** Texto de una fila con error del CSV: el del código si lo conocemos, si no el del servidor. */
export function csvRowErrorText(error: { code: string; message: string }): string {
  return ROW_ERROR_TEXT[error.code] ?? error.message
}

interface StockShortfall {
  productId: string
  requested: number
  available: number
}

function shortfalls(details: unknown): StockShortfall[] {
  if (!Array.isArray(details)) return []
  return details.flatMap((item: unknown) => {
    if (!item || typeof item !== 'object') return []
    const { productId, requested, available } = item as Record<string, unknown>
    return typeof productId === 'string' && typeof requested === 'number' && typeof available === 'number'
      ? [{ productId, requested, available }]
      : []
  })
}

/** Productos sin existencias suficientes de un 409 `INSUFFICIENT_STOCK` de transferencia. */
export function insufficientStock(error: unknown): StockShortfall[] {
  return error instanceof ApiError && error.code === 'INSUFFICIENT_STOCK' ? shortfalls(error.details) : []
}

/**
 * Mensaje de un fallo de inventario. `names` traduce ids de producto a nombres en el detalle de
 * `INSUFFICIENT_STOCK`.
 */
export function inventoryErrorMessage(error: unknown, fallback: string, names?: ReadonlyMap<string, string>): string {
  if (error instanceof ApiError && error.code) {
    if (error.code === 'INSUFFICIENT_STOCK') {
      const list = shortfalls(error.details)
      if (list.length === 0) return 'Ese ajuste dejaría las existencias en negativo. Revisa la cantidad.'
      const lines = list.slice(0, 3).map((item) => `${names?.get(item.productId) ?? 'Un producto'} (pides ${formatNumber(item.requested)}, hay ${formatNumber(item.available)})`)
      const extra = list.length > 3 ? ` y ${list.length - 3} más` : ''
      return `No hay existencias suficientes en la sucursal de origen: ${lines.join('; ')}${extra}. Baja las cantidades y vuelve a intentarlo.`
    }
    const text = ERROR_TEXT[error.code]
    if (text) return text
  }
  return actionErrorMessage(error, fallback)
}

/** Fecha local `AAAA-MM-DD` de hoy. */
export function todayISO(now = new Date()): string {
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${now.getFullYear()}-${month}-${day}`
}

/** Nombre del archivo del inventario exportado: `inventario-AAAA-MM-DD.csv`. */
export function csvFilename(now = new Date()): string {
  return `inventario-${todayISO(now)}.csv`
}

/** Dispara la descarga de un archivo ya recibido. */
export function saveFile(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.append(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/** Colombia no usa horario de verano: UTC-5 todo el año. */
const CO_OFFSET = '-05:00'

/** Inicio del día `AAAA-MM-DD` en hora de Colombia, en ISO 8601 con zona. */
export const startOfDay = (date: string) => `${date}T00:00:00${CO_OFFSET}`
/** Fin del día `AAAA-MM-DD` en hora de Colombia, en ISO 8601 con zona. */
export const endOfDay = (date: string) => `${date}T23:59:59.999${CO_OFFSET}`

/** Texto del motivo para mostrarlo en la lista (con el sentido cuando limita el ajuste). */
export function reasonOptionLabel(reason: MovementReason): string {
  if (reason.direction === 'IN') return `${reason.label} (solo entradas)`
  if (reason.direction === 'OUT') return `${reason.label} (solo salidas)`
  return reason.label
}

/** Motivos de movimientos no ajustables que aparecen en el historial; el filtro los ofrece junto a los ajustables. */
export const SYSTEM_REASONS: { code: string; label: string }[] = [
  { code: 'TRANSFER_OUT', label: 'Transferencia (salida)' },
  { code: 'TRANSFER_IN', label: 'Transferencia (entrada)' },
  { code: 'CSV_IMPORT', label: 'Importación CSV' },
]

/** Lectura corta de quién hizo el movimiento. */
export function actorLabel(name: string | null): string {
  return name ?? 'Sistema'
}
