import { Badge } from '@/components/ui/badge'
import { formatCOP, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Availability, Brand, Product, StoreSummary } from '../api/types'
import { ruleLabel } from '../lib/paths'

/**
 * Datos comerciales y técnicos de un listado (§7.2, §8.4). Cada etiqueta dice lo que
 * informa el servidor: "Recomendado" (tienda), "Disponible" (existencias) y "Universal"
 * (compatibilidad) son datos distintos y nunca se intercambian.
 */

/** Precio que devuelve el servicio; si hay promoción, muestra el precio anterior. */
export function Price({ product, size = 'md', className }: { product: Product; size?: 'md' | 'lg'; className?: string }) {
  return (
    <div className={cn('flex flex-wrap items-baseline gap-x-2 gap-y-1', className)}>
      <span className={cn('ap-price text-ap-ink', size === 'lg' ? 'text-[1.75rem] leading-none sm:text-[2rem]' : 'text-lg leading-tight')}>
        {formatCOP(product.price)}
      </span>
      {product.listPrice ? (
        <span className="text-sm text-ap-muted tabular-nums line-through">
          <span className="sr-only">Antes </span>
          {formatCOP(product.listPrice)}
        </span>
      ) : null}
      {isAudiencePrice(product) ? <FrequentPriceTag /> : null}
    </div>
  )
}

/** El servidor aplicó una promoción dirigida a un segmento de compradores (no a todos). */
function isAudiencePrice(product: Pick<Product, 'promotionAudience'>): boolean {
  return product.promotionAudience !== undefined && product.promotionAudience !== 'ALL'
}

/** Etiqueta junto al precio cuando la promoción es para clientes frecuentes. */
export function FrequentPriceTag({ className }: { className?: string }) {
  return (
    <Badge variant="secondary" className={className}>
      Precio para clientes frecuentes
    </Badge>
  )
}

const AVAILABILITY_COPY: Record<Availability['status'], { label: string; tone: string; dot: string }> = {
  in_stock: { label: 'Disponible', tone: 'text-ap-success', dot: 'bg-ap-success' },
  low_stock: { label: 'Pocas unidades', tone: 'text-ap-warning', dot: 'bg-ap-warning' },
  out_of_stock: { label: 'Agotado', tone: 'text-ap-muted', dot: 'bg-ap-steel' },
}

/** Existencias informadas por el servidor; el texto siempre acompaña al color. */
export function AvailabilityLabel({
  availability,
  showQuantity = false,
  className,
}: {
  availability: Availability
  showQuantity?: boolean
  className?: string
}) {
  const copy = AVAILABILITY_COPY[availability.status]
  const quantity =
    showQuantity && availability.quantity !== undefined && availability.status !== 'out_of_stock'
      ? ` (${formatNumber(availability.quantity)} ${availability.quantity === 1 ? 'unidad' : 'unidades'})`
      : ''
  return (
    <span className={cn('inline-flex items-center gap-1.5 text-sm font-medium', copy.tone, className)}>
      <span className={cn('size-2 shrink-0 rounded-full', copy.dot)} aria-hidden />
      {copy.label}
      {quantity}
    </span>
  )
}

/** Etiqueta «Universal»: el repuesto no depende del vehículo y sale en cualquier búsqueda por vehículo. */
export function UniversalTag({ className }: { className?: string }) {
  return (
    <Badge variant="neutral" className={cn('text-ap-ink', className)}>
      Universal
    </Badge>
  )
}

/** Marca del fabricante; si aún no está aprobado se muestra igual, con un aviso discreto. */
export function BrandName({ brand, className }: { brand: Brand; className?: string }) {
  return (
    <span className={cn('inline-flex flex-wrap items-baseline gap-x-1.5', className)}>
      <span className="font-semibold text-ap-ink">{brand.name}</span>
      {brand.approved === false ? <span className="text-xs font-normal text-ap-muted">(fabricante en revisión)</span> : null}
    </span>
  )
}

/**
 * Compatibilidad como dato de primer nivel. El catálogo informa reglas por vehículo o
 * que la pieza es universal; no informa si cada regla está confirmada, así que no se
 * distingue confirmada/parcial. Sin reglas, `showMissing` lo dice en vez de ocultarlo.
 */
export function FitmentLine({
  product,
  className,
  showMissing = false,
}: {
  product: Pick<Product, 'universal' | 'vehicleRules'>
  className?: string
  showMissing?: boolean
}) {
  const [first, ...rest] = product.vehicleRules
  if (product.universal) {
    return (
      <p className={cn('flex flex-wrap items-center gap-x-2 gap-y-1 text-sm', className)}>
        <UniversalTag />
        <span className="text-ap-muted">Sirve para cualquier vehículo</span>
      </p>
    )
  }
  if (!first) {
    return showMissing ? (
      <p className={cn('text-sm text-ap-muted', className)}>Sin compatibilidad publicada: confirma con la tienda</p>
    ) : null
  }
  return (
    <p className={cn('text-sm text-ap-ink', className)}>
      <span className="text-ap-muted">Aplica para </span>
      {ruleLabel(first)}
      {rest.length > 0 ? (
        <span className="text-ap-muted">
          {' '}
          y {rest.length} {rest.length === 1 ? 'vehículo más' : 'vehículos más'}
        </span>
      ) : null}
    </p>
  )
}

/** Qué significa el distintivo. Se muestra junto al filtro, en Tiendas y en la ficha del repuesto. */
export const RECOMMENDED_EXPLANATION = 'Tiendas con buen servicio y respuesta en Autopartco.'

/**
 * Distintivo "Recomendado" en azul informativo (no rojo, que es acción, ni verde, que es
 * existencias). Solo se pinta si el servidor lo informa (`store.recommended`).
 * TODO(flujo): cómo se gana o se pierde el distintivo no está definido; el frontend no lo calcula.
 */
export function RecommendedMark({ className }: { className?: string }) {
  return (
    <Badge variant="info" className={className}>
      Recomendado
    </Badge>
  )
}

export function StoreLine({ store, className, distanceKm }: { store: StoreSummary; className?: string; distanceKm?: string }) {
  return (
    <p className={cn('flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ap-muted', className)}>
      <span className="font-medium text-ap-ink">{store.name}</span>
      {store.city ? <span>· {store.city}</span> : null}
      {distanceKm ? <span className="tabular-nums">· sede a {distanceKm}</span> : null}
      {store.recommended ? <RecommendedMark /> : null}
    </p>
  )
}
