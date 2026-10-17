import { AwardIcon, CarFrontIcon, GlobeIcon } from 'lucide-react'
import { formatCOP, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Availability, Brand, Product, StoreSummary } from '../api/types'
import { ruleLabel } from '../lib/paths'

/** Precio que devuelve el servicio; si hay promoción, muestra el precio anterior. */
export function Price({ product, size = 'md' }: { product: Product; size?: 'md' | 'lg' }) {
  return (
    <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
      <span
        className={cn(
          'tabular-nums',
          size === 'lg'
            ? 'text-[2.25rem] leading-none font-extrabold tracking-[-0.035em] sm:text-[2.75rem]'
            : 'text-lg font-semibold tracking-tight',
        )}
      >
        {formatCOP(product.price)}
      </span>
      {product.listPrice ? (
        <span className="text-sm text-muted-foreground tabular-nums line-through">
          <span className="sr-only">Antes </span>
          {formatCOP(product.listPrice)}
        </span>
      ) : null}
    </div>
  )
}

const AVAILABILITY_COPY: Record<Availability['status'], { label: string; tone: string; dot: string }> = {
  in_stock: { label: 'Disponible', tone: 'text-stock', dot: 'bg-stock' },
  low_stock: { label: 'Pocas unidades', tone: 'text-stock-low', dot: 'bg-stock-low' },
  out_of_stock: { label: 'Agotado', tone: 'text-muted-foreground', dot: 'bg-muted-foreground' },
}

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
      <span className={cn('size-2 rounded-full', copy.dot)} aria-hidden />
      {copy.label}
      {quantity}
    </span>
  )
}

/** Etiqueta «Universal»: el repuesto no depende del vehículo y sale en cualquier búsqueda por vehículo. */
export function UniversalTag({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex w-fit shrink-0 items-center gap-1 rounded-full bg-concrete px-2 py-0.5 text-xs leading-tight font-semibold text-foreground',
        className,
      )}
    >
      <GlobeIcon className="size-3.5" aria-hidden />
      Universal
    </span>
  )
}

/** Marca del fabricante; si aún no está aprobado se muestra igual, con un aviso discreto. */
export function BrandName({ brand, className }: { brand: Brand; className?: string }) {
  return (
    <span className={cn('inline-flex flex-wrap items-baseline gap-x-1.5', className)}>
      <span className="font-semibold text-foreground">{brand.name}</span>
      {brand.approved === false ? <span className="text-xs font-normal text-muted-foreground">(fabricante en revisión)</span> : null}
    </span>
  )
}

/** «Universal: sirve para cualquier vehículo» o «Aplica para Renault · Duster · todas las versiones y 1 regla más». */
export function FitmentLine({ product, className }: { product: Pick<Product, 'universal' | 'vehicleRules'>; className?: string }) {
  const [first, ...rest] = product.vehicleRules
  if (!product.universal && !first) return null
  return (
    <p className={cn('flex items-start gap-1.5 text-sm text-foreground/80', className)}>
      <CarFrontIcon className="mt-0.5 size-4 shrink-0 text-brand" aria-hidden />
      {product.universal || !first ? (
        <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <UniversalTag />
          <span className="text-muted-foreground">Sirve para cualquier vehículo</span>
        </span>
      ) : (
        <span>
          Aplica para {ruleLabel(first)}
          {rest.length > 0 ? (
            <span className="text-muted-foreground">
              {' '}
              y {rest.length} {rest.length === 1 ? 'regla más' : 'reglas más'}
            </span>
          ) : null}
        </span>
      )}
    </p>
  )
}

/** Qué significa el distintivo. Se muestra junto al filtro, en Tiendas y en la ficha del repuesto. */
export const RECOMMENDED_EXPLANATION = 'Tiendas con buen servicio y respuesta en Autopartco.'

/**
 * Distintivo "Recomendado": azul acero con un sello (no rojo, que es el color de
 * las acciones, ni verde, que es el de existencias). Solo se pinta si el servidor
 * lo informa (`store.recommended`).
 * TODO(flujo): cómo se gana o se pierde el distintivo no está definido; el frontend no lo calcula.
 */
export function RecommendedMark({ className, tone = 'light' }: { className?: string; tone?: 'light' | 'dark' }) {
  return (
    <span
      className={cn(
        'inline-flex w-fit shrink-0 items-center gap-1 rounded-full py-0.5 pr-2 pl-1.5 text-xs leading-tight font-semibold',
        tone === 'dark' ? 'bg-white text-recommended' : 'bg-recommended-soft text-recommended',
        className,
      )}
    >
      <AwardIcon className="size-3.5" aria-hidden />
      Recomendado
    </span>
  )
}

export function StoreLine({ store, className }: { store: StoreSummary; className?: string }) {
  return (
    <p className={cn('flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-muted-foreground', className)}>
      <span className="font-medium text-foreground">{store.name}</span>
      {store.city ? <span>{store.city}</span> : null}
      {store.recommended ? <RecommendedMark /> : null}
    </p>
  )
}
