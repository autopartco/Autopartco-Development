import type { AbcClass, CustomerSegmentCode, Granularity, ProductAction, XyzClass } from '../api/analytics-types'

// ------------------------------------------------------------------- rangos

export type RangePreset = '7' | '30' | '90' | 'mes' | 'mes-anterior' | 'personalizado'

export const RANGE_PRESETS: { value: RangePreset; label: string }[] = [
  { value: '7', label: 'Últimos 7 días' },
  { value: '30', label: 'Últimos 30 días' },
  { value: '90', label: 'Últimos 90 días' },
  { value: 'mes', label: 'Este mes' },
  { value: 'mes-anterior', label: 'Mes anterior' },
  { value: 'personalizado', label: 'Personalizado' },
]

export const GRANULARITY_OPTIONS: { value: Granularity; label: string }[] = [
  { value: 'DAY', label: 'Por día' },
  { value: 'WEEK', label: 'Por semana' },
  { value: 'MONTH', label: 'Por mes' },
]

const pad = (value: number) => String(value).padStart(2, '0')

/** `YYYY-MM-DD` en hora local. */
export function toYmd(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** Fecha local a partir de `YYYY-MM-DD`; `undefined` si no es válida. */
export function fromYmd(value: string | null | undefined): Date | undefined {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value ?? '')
  if (!match) return undefined
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return toYmd(date) === value ? date : undefined
}

export function isRangePreset(value: string | null): value is RangePreset {
  return RANGE_PRESETS.some((preset) => preset.value === value)
}

export function isGranularity(value: string | null): value is Granularity {
  return value === 'DAY' || value === 'WEEK' || value === 'MONTH'
}

/** Rango `desde`–`hasta` (ambos incluidos) de un preajuste. Con `personalizado` usa las fechas dadas. */
export function resolveRange(preset: RangePreset, custom: { from?: string; to?: string }, now = new Date()): { from: string; to: string } {
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const back = (days: number) => new Date(today.getFullYear(), today.getMonth(), today.getDate() - days)
  switch (preset) {
    case '7':
      return { from: toYmd(back(6)), to: toYmd(today) }
    case '90':
      return { from: toYmd(back(89)), to: toYmd(today) }
    case 'mes':
      return { from: toYmd(new Date(today.getFullYear(), today.getMonth(), 1)), to: toYmd(today) }
    case 'mes-anterior':
      return {
        from: toYmd(new Date(today.getFullYear(), today.getMonth() - 1, 1)),
        to: toYmd(new Date(today.getFullYear(), today.getMonth(), 0)),
      }
    case 'personalizado':
      return { from: custom.from ?? toYmd(back(29)), to: custom.to ?? toYmd(today) }
    default:
      return { from: toYmd(back(29)), to: toYmd(today) }
  }
}

/** Días del rango (ambos extremos incluidos); `NaN` si alguna fecha no es válida. */
export function rangeLength(from: string, to: string): number {
  const start = fromYmd(from)
  const end = fromYmd(to)
  if (!start || !end) return Number.NaN
  return Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1
}

export const MAX_RANGE_DAYS = 366

const dayFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' })
const dayYearFormat = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short', year: 'numeric' })
const monthFormat = new Intl.DateTimeFormat('es-CO', { month: 'short', year: '2-digit' })
const monthLongFormat = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' })

/** Etiqueta de un periodo de la serie: «5 nov», «Sem. 3 nov» o «nov. 26». */
export function periodLabel(periodStart: string, granularity: Granularity): string {
  const date = fromYmd(periodStart)
  if (!date) return periodStart
  if (granularity === 'MONTH') return monthFormat.format(date)
  return `${granularity === 'WEEK' ? 'Sem. ' : ''}${dayFormat.format(date)}`
}

export function dateLabel(value: string): string {
  const date = fromYmd(value)
  return date ? dayYearFormat.format(date) : value
}

/** «2026-12» -> «diciembre de 2026». */
export function monthLabel(month: string): string {
  const date = fromYmd(`${month}-01`)
  return date ? monthLongFormat.format(date) : month
}

// ---------------------------------------------------------------------- CSV

/** CSV para Excel en español (separador `;`, BOM UTF-8, CRLF). Neutraliza celdas que parecen fórmulas. */
export function toCsv(rows: (string | number | boolean | null | undefined)[][]): string {
  const cell = (value: string | number | boolean | null | undefined): string => {
    if (value === null || value === undefined) return ''
    let text = typeof value === 'string' ? value : String(value)
    if (typeof value === 'string' && /^[=+\-@\t\r]/.test(text)) text = `'${text}`
    return /[";\r\n]/.test(text) ? `"${text.replaceAll('"', '""')}"` : text
  }
  return `﻿${rows.map((row) => row.map(cell).join(';')).join('\r\n')}\r\n`
}

export function csvBlob(rows: (string | number | boolean | null | undefined)[][]): Blob {
  return new Blob([toCsv(rows)], { type: 'text/csv;charset=utf-8' })
}

// ----------------------------------------------------------------- clientes

export interface SegmentInfo {
  label: string
  /** Regla en lenguaje de almacén (el servidor decide la pertenencia). */
  description: string
  suggestion: { title: string; text: string } | null
}

export const SEGMENT_INFO: Record<CustomerSegmentCode, SegmentInfo> = {
  NEW: {
    label: 'Nuevos',
    description: 'Tienen una sola compra y la hicieron en los últimos 30 días.',
    suggestion: { title: 'Incentiva la segunda compra', text: 'Un descuento corto en repuestos de mantenimiento ayuda a que vuelvan.' },
  },
  FREQUENT: {
    label: 'Frecuentes',
    description: 'Compran con regularidad: 3 o más compras y la última hace 60 días o menos.',
    suggestion: { title: 'Premia su fidelidad', text: 'Ofréceles un descuento exclusivo o acceso anticipado a tus promociones.' },
  },
  AT_RISK: {
    label: 'En riesgo',
    description: 'Compraron 2 o más veces, pero su última compra fue hace entre 61 y 180 días.',
    suggestion: { title: 'Promoción de regreso', text: 'Un descuento con fecha límite en lo que suelen comprar puede recuperarlos.' },
  },
  DORMANT: {
    label: 'Dormidos',
    description: 'Llevan más de 180 días sin comprar.',
    suggestion: { title: 'Campaña de reactivación', text: 'Una oferta fuerte en tus productos más vendidos es lo que mejor funciona con este grupo.' },
  },
  OCCASIONAL: {
    label: 'Ocasionales',
    description: 'Compran de vez en cuando y no encajan en los otros grupos.',
    suggestion: null,
  },
}

export const SEGMENT_CODES: CustomerSegmentCode[] = ['NEW', 'FREQUENT', 'AT_RISK', 'DORMANT', 'OCCASIONAL']

export function isSegmentCode(value: string | null): value is CustomerSegmentCode {
  return SEGMENT_CODES.some((code) => code === value)
}

// ---------------------------------------------------------------- productos

export const ABC_INFO: Record<AbcClass, { label: string; description: string }> = {
  A: { label: 'A', description: 'Generan cerca del 80 % de tus ingresos. Cuídalos primero.' },
  B: { label: 'B', description: 'Aportan cerca del 15 % de tus ingresos. Son de rotación media.' },
  C: { label: 'C', description: 'Aportan el 5 % restante. Muchos productos, poco ingreso.' },
}

export const XYZ_INFO: Record<XyzClass, { label: string; description: string }> = {
  X: { label: 'X', description: 'Demanda estable: se vende parecido cada semana (variación baja).' },
  Y: { label: 'Y', description: 'Demanda variable: sube y baja con alguna regularidad.' },
  Z: { label: 'Z', description: 'Demanda irregular: difícil de anticipar.' },
}

export const ACTION_INFO: Record<ProductAction, { label: string; description: string }> = {
  PROTECT_STOCK: { label: 'Proteger stock', description: 'Es de clase A y las existencias alcanzan para menos de 14 días. Repón antes de quedarte sin él.' },
  NO_MOVEMENT: { label: 'Sin movimiento', description: 'Tiene existencias pero no se vendió en los últimos 90 días. Revisa si está publicado y bien descrito.' },
  LIQUIDATE: { label: 'Liquidar', description: 'No se vende hace 90 días y el producto lleva más de 180 días en tu catálogo. Una promoción libera capital.' },
  REVIEW_PRICE: { label: 'Revisar precio', description: 'Es de clase C y tus existencias duran más de 120 días. Compara tu precio con el del mercado.' },
  MAINTAIN: { label: 'Mantener', description: 'Rotación y existencias en equilibrio. No requiere acción.' },
}

export const ACTION_ORDER: ProductAction[] = ['PROTECT_STOCK', 'NO_MOVEMENT', 'LIQUIDATE', 'REVIEW_PRICE', 'MAINTAIN']
