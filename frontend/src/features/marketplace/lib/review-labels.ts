import {
  AwardIcon,
  ClipboardCheckIcon,
  GemIcon,
  HeadsetIcon,
  PackageCheckIcon,
  RulerIcon,
  ScaleIcon,
  TagIcon,
  TruckIcon,
  WrenchIcon,
  type LucideIcon,
} from 'lucide-react'
import { humanizeCode } from './humanize-code'

/**
 * Textos de las categorías de reseña. El servidor envía solo el código (`StoreAspect` / `ProductAspect`);
 * la etiqueta, la insignia y el ícono viven aquí. El orden de cada mapa es el de las barras.
 */
export type ReviewKind = 'product' | 'store'

export interface AspectCopy {
  /** Nombre de la categoría en las barras y en cada opinión. */
  label: string
  /** Texto de la insignia cuando la categoría destaca. */
  badge: string
  icon: LucideIcon
}

const STORE_ASPECTS: Record<string, AspectCopy> = {
  SERVICE: { label: 'Atención', badge: 'Excelente atención', icon: HeadsetIcon },
  SPEED: { label: 'Rapidez de entrega', badge: 'Entrega rápida', icon: TruckIcon },
  QUALITY: { label: 'Calidad de los productos', badge: 'Productos de calidad', icon: AwardIcon },
  PRICE: { label: 'Precio justo', badge: 'Buenos precios', icon: TagIcon },
  ACCURACY: { label: 'Pedido completo y correcto', badge: 'Pedidos exactos', icon: PackageCheckIcon },
}

const PRODUCT_ASPECTS: Record<string, AspectCopy> = {
  QUALITY: { label: 'Calidad', badge: 'Muy buena calidad', icon: AwardIcon },
  FIT: { label: 'Encajó en mi vehículo', badge: 'Encaja perfecto', icon: RulerIcon },
  AS_DESCRIBED: { label: 'Igual a la descripción', badge: 'Tal como se describe', icon: ClipboardCheckIcon },
  VALUE: { label: 'Vale lo que cuesta', badge: 'Gran relación precio-calidad', icon: ScaleIcon },
  INSTALL: { label: 'Fácil de instalar', badge: 'Fácil de instalar', icon: WrenchIcon },
}

const COPY: Record<ReviewKind, Record<string, AspectCopy>> = { product: PRODUCT_ASPECTS, store: STORE_ASPECTS }

/** Texto de un código; un código nuevo del servidor se muestra legible en vez de romper la pantalla. */
export function aspectCopy(kind: ReviewKind, code: string): AspectCopy {
  return COPY[kind][code] ?? { label: humanizeCode(code), badge: humanizeCode(code), icon: GemIcon }
}

/** Posición de la categoría en el orden de la tabla; las desconocidas van al final. */
export function aspectRank(kind: ReviewKind, code: string): number {
  const index = Object.keys(COPY[kind]).indexOf(code)
  return index === -1 ? Number.MAX_SAFE_INTEGER : index
}

const ratingFormatter = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** «4,7». */
export function formatRating(value: number): string {
  return ratingFormatter.format(value)
}

/** «4,7 de 5»: el texto que leen los lectores de pantalla. */
export function ratingLabel(value: number): string {
  return `${formatRating(value)} de 5`
}

const relativeFormatter = new Intl.RelativeTimeFormat('es-CO', { numeric: 'auto' })
const DAY_MS = 86_400_000

/** «hoy», «hace 3 días», «hace 2 meses», «hace 1 año». */
export function relativeDate(iso: string, now = Date.now()): string {
  const time = new Date(iso).getTime()
  if (Number.isNaN(time)) return ''
  const days = Math.max(0, Math.floor((now - time) / DAY_MS))
  if (days < 1) return 'hoy'
  if (days < 30) return relativeFormatter.format(-days, 'day')
  if (days < 365) return relativeFormatter.format(-Math.floor(days / 30), 'month')
  return relativeFormatter.format(-Math.floor(days / 365), 'year')
}
