import { memo, type ReactNode } from 'react'
import { Link } from 'react-router'
import { SmartImage } from '@/components/common/smart-image'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import { formatKm } from '@/features/location/lib/geo'
import type { Product } from '../api/types'
import { productPath } from '../lib/paths'
import { AvailabilityLabel, BrandName, FitmentLine, Price, StoreLine } from './product-meta'

/**
 * Mini ruta del cliente a la tienda: aparece al pasar el cursor o al enfocar la
 * tarjeta, y el hilo se dibuja con `stroke-dashoffset`. Decorativa: la distancia
 * ya se anuncia en la etiqueta.
 */
function MiniRoute({ distanceKm }: { distanceKm: number }) {
  return (
    <span
      aria-hidden
      className="pointer-events-none absolute inset-x-2 bottom-2 flex translate-y-1 items-center gap-2 rounded-md bg-card/95 px-2 py-1.5 opacity-0 shadow-[0_0_0_1px_rgb(0_0_0/0.06),0_4px_12px_-4px_rgb(0_0_0/0.25)] transition-[opacity,transform] duration-200 ease-out-thread group-hover:translate-y-0 group-hover:opacity-100 group-has-[a:focus-visible]:translate-y-0 group-has-[a:focus-visible]:opacity-100"
    >
      <span className="label-mono text-muted-foreground">Tú</span>
      <svg viewBox="0 0 120 24" className="h-5 min-w-0 flex-1" preserveAspectRatio="none" fill="none">
        <path d="M4 18 H58 V6 H112" stroke="var(--color-map-dot)" strokeWidth="2" strokeDasharray="2 4" />
        <path
          d="M4 18 H58 V6 H112"
          pathLength={1}
          stroke="var(--color-thread)"
          strokeWidth="2"
          strokeLinecap="round"
          strokeLinejoin="round"
          className="transition-[stroke-dashoffset] delay-75 duration-500 ease-out-thread [stroke-dasharray:1] [stroke-dashoffset:1] group-hover:[stroke-dashoffset:0] group-has-[a:focus-visible]:[stroke-dashoffset:0]"
        />
        <circle cx="4" cy="18" r="3" fill="var(--color-asphalt)" />
        <circle cx="112" cy="6" r="4" fill="var(--color-thread)" />
      </svg>
      <span className="label-mono font-semibold text-foreground tabular-nums">{formatKm(distanceKm)}</span>
    </span>
  )
}

interface ProductCardProps {
  product: Product
  eager?: boolean
  /** Distancia a la sede más cercana de la tienda; sin valor no se muestra nada. */
  distanceKm?: number
}

/**
 * Ficha de catálogo. Toda la tarjeta es clicable mediante el enlace del título
 * (un solo destino de tabulación por producto).
 */
export const ProductCard = memo(function ProductCard({ product, eager = false, distanceKm }: ProductCardProps) {
  return (
    <article className="group relative flex flex-col gap-3 rounded-xl p-2 transition-[background-color,box-shadow] duration-200 hover:bg-card hover:shadow-[0_0_0_1px_rgb(0_0_0/0.05),0_8px_24px_-12px_rgb(0_0_0/0.18)] has-[a:focus-visible]:bg-card">
      <div className="relative">
        <SmartImage
          src={product.images[0]?.cardUrl}
          alt=""
          width={400}
          height={300}
          loading={eager ? 'eager' : 'lazy'}
          frameClassName="aspect-[4/3] rounded-md outline outline-1 -outline-offset-1 outline-black/10"
          className="transition-transform duration-300 ease-out-snap group-hover:scale-[1.03]"
        />
        {product.discountAmount || distanceKm !== undefined ? (
          // Las dos etiquetas comparten fila; si la tarjeta es estrecha, la segunda baja en vez de taparse.
          <div className="absolute inset-x-2 top-2 flex flex-wrap items-start justify-between gap-1">
            {product.discountAmount ? (
              <span className="rounded-sm bg-brand px-1.5 py-1 label-mono text-primary-foreground tabular-nums">
                Ahorra {formatCOP(product.discountAmount)}
              </span>
            ) : null}
            {distanceKm !== undefined ? (
              <span className="ml-auto rounded-sm bg-asphalt px-1.5 py-1 label-mono text-white tabular-nums">
                Sede a {formatKm(distanceKm)}
                <span className="sr-only"> de tu punto de referencia</span>
              </span>
            ) : null}
          </div>
        ) : null}
        {distanceKm !== undefined ? <MiniRoute distanceKm={distanceKm} /> : null}
      </div>

      <div className="flex flex-1 flex-col gap-2 px-1 pb-1">
        <p className="flex flex-wrap gap-x-2 text-xs text-muted-foreground">
          {product.brand ? <BrandName brand={product.brand} /> : null}
          {product.partNumber ? <span className="tabular-nums">Ref. {product.partNumber}</span> : null}
        </p>
        <h3 className="line-clamp-2 text-[0.95rem] leading-snug font-medium">
          <Link
            to={productPath(product.id)}
            data-stretched-link
            className="outline-none after:absolute after:inset-0 after:rounded-lg focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[var(--focus)] group-hover:text-brand"
          >
            {product.name}
          </Link>
        </h3>
        <FitmentLine product={product} />
        <div className="mt-auto flex flex-col gap-1 pt-1">
          <Price product={product} />
          {product.availability ? <AvailabilityLabel availability={product.availability} /> : null}
          {product.store ? <StoreLine store={product.store} className="text-xs" /> : null}
        </div>
      </div>
    </article>
  )
})

export function ProductCardSkeleton() {
  return (
    <div className="flex flex-col gap-3 p-2" aria-hidden>
      <Skeleton className="aspect-[4/3] w-full rounded-md" />
      <div className="flex flex-col gap-2 px-1">
        <Skeleton className="h-3 w-1/3" />
        <Skeleton className="h-4 w-11/12" />
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="mt-2 h-5 w-1/2" />
        <Skeleton className="h-3 w-2/5" />
      </div>
    </div>
  )
}

export function ProductGrid({
  children,
  className,
  busy,
}: {
  children: ReactNode
  className?: string
  busy?: boolean
}) {
  return (
    <div
      aria-busy={busy || undefined}
      className={cn(
        'grid grid-cols-1 gap-x-3 gap-y-6 min-[420px]:grid-cols-2 md:grid-cols-3 md:gap-x-4 transition-opacity duration-200',
        busy && 'opacity-60',
        className,
      )}
    >
      {children}
    </div>
  )
}

export function ProductGridSkeleton({ count, className }: { count: number; className?: string }) {
  return (
    <ProductGrid className={className}>
      {Array.from({ length: count }, (_, index) => (
        <ProductCardSkeleton key={index} />
      ))}
    </ProductGrid>
  )
}
