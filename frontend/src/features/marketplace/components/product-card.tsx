import { memo, type ReactNode } from 'react'
import { Link } from 'react-router'
import { SmartImage } from '@/components/common/smart-image'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatKm } from '@/features/location/lib/geo'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Product } from '../api/types'
import { productPath } from '../lib/paths'
import { AvailabilityLabel, BrandName, FitmentLine, Price, StoreLine } from './product-meta'

interface ProductItemProps {
  product: Product
  eager?: boolean
  /** Distancia a la sede más cercana de la tienda; sin valor no se muestra nada. */
  distanceKm?: number
}

/* ------------------------------------------------------------------ */
/* Fila de catálogo (resultados, §7.2 y §15)                          */
/* ------------------------------------------------------------------ */

/**
 * Resultado como fila de catálogo: imagen, referencia, nombre, compatibilidad y tienda;
 * a la derecha, en columna estable, precio, existencias y acción. El nombre es el único
 * destino de tabulación; el botón "Ver producto" repite el enlace para el puntero.
 * En móvil se apila: imagen y datos arriba, precio y acción debajo.
 */
export const ProductRow = memo(function ProductRow({ product, eager = false, distanceKm }: ProductItemProps) {
  const href = productPath(product.id)
  return (
    <article className="grid grid-cols-[5rem_minmax(0,1fr)] gap-x-4 py-5 sm:grid-cols-[7rem_minmax(0,1fr)_11.5rem] sm:gap-x-5">
      <Link to={href} tabIndex={-1} aria-hidden className="row-span-1 self-start">
        <SmartImage
          src={product.images[0]?.thumbUrl ?? product.images[0]?.cardUrl}
          alt=""
          width={112}
          height={112}
          loading={eager ? 'eager' : 'lazy'}
          frameClassName="aspect-square w-full rounded-md border border-ap-line"
          className="p-1.5"
        />
      </Link>

      <div className="flex min-w-0 flex-col gap-1.5">
        <p className="flex flex-wrap items-baseline gap-x-3 gap-y-0.5 text-xs">
          {product.partNumber ? <span className="ap-ref text-ap-muted">REF. {product.partNumber}</span> : null}
          {product.brand ? <BrandName brand={product.brand} className="text-xs" /> : null}
        </p>
        <h3 className="text-base leading-snug font-semibold text-ap-ink">
          <Link to={href} className="underline-offset-4 hover:text-ap-red hover:underline">
            {product.name}
          </Link>
        </h3>
        <FitmentLine product={product} showMissing />
        {product.store ? (
          <StoreLine store={product.store} distanceKm={distanceKm !== undefined ? formatKm(distanceKm) : undefined} />
        ) : null}
      </div>

      <div className="col-span-2 mt-4 flex items-end justify-between gap-3 border-t border-ap-line pt-3 sm:col-span-1 sm:mt-0 sm:flex-col sm:items-end sm:justify-start sm:border-t-0 sm:border-l sm:pt-0 sm:pl-5">
        <div className="flex min-w-0 flex-col gap-1 sm:items-end sm:text-right">
          <Price product={product} className="sm:justify-end" />
          {product.discountAmount ? (
            <span className="text-xs font-medium text-ap-red tabular-nums">Ahorra {formatCOP(product.discountAmount)}</span>
          ) : null}
          {product.availability ? <AvailabilityLabel availability={product.availability} /> : null}
        </div>
        <Link
          to={href}
          tabIndex={-1}
          aria-hidden
          className={cn(buttonVariants({ variant: 'outline' }), 'shrink-0 sm:mt-auto sm:w-full')}
        >
          Ver producto
        </Link>
      </div>
    </article>
  )
})

export function ProductRowSkeleton() {
  return (
    <div className="grid grid-cols-[5rem_minmax(0,1fr)] gap-x-4 py-5 sm:grid-cols-[7rem_minmax(0,1fr)_11.5rem] sm:gap-x-5" aria-hidden>
      <Skeleton className="aspect-square w-full rounded-md" />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-3 w-28" />
        <Skeleton className="h-5 w-4/5" />
        <Skeleton className="h-4 w-3/5" />
        <Skeleton className="h-4 w-2/5" />
      </div>
      <div className="col-span-2 mt-4 flex justify-between gap-3 sm:col-span-1 sm:mt-0 sm:flex-col sm:items-end">
        <Skeleton className="h-6 w-28" />
        <Skeleton className="h-11 w-32 sm:w-full" />
      </div>
    </div>
  )
}

/** Lista de resultados con divisores; `busy` atenúa mientras llega la página siguiente. */
export function ProductList({ children, className, busy }: { children: ReactNode; className?: string; busy?: boolean }) {
  return (
    <ol
      aria-busy={busy || undefined}
      className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line transition-opacity duration-150', busy && 'opacity-60', className)}
    >
      {children}
    </ol>
  )
}

export function ProductListSkeleton({ count }: { count: number }) {
  return (
    <div className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
      <span className="sr-only" role="status">
        Cargando repuestos
      </span>
      {Array.from({ length: count }, (_, index) => (
        <ProductRowSkeleton key={index} />
      ))}
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Ficha compacta en cuadrícula (portada y relacionados)              */
/* ------------------------------------------------------------------ */

/**
 * Ficha compacta para vitrinas cortas, donde la foto ayuda a reconocer la pieza.
 * Toda la ficha es clicable mediante el enlace del título (un solo destino de tabulación).
 */
export const ProductCard = memo(function ProductCard({ product, eager = false, distanceKm }: ProductItemProps) {
  return (
    <article className="group relative flex h-full flex-col gap-3 border-t border-ap-line pt-4">
      <SmartImage
        src={product.images[0]?.cardUrl}
        alt=""
        width={400}
        height={300}
        loading={eager ? 'eager' : 'lazy'}
        frameClassName="aspect-[4/3] rounded-md border border-ap-line"
        className="p-2"
      />
      <div className="flex flex-1 flex-col gap-1.5">
        {product.partNumber ? <p className="ap-ref text-xs text-ap-muted">REF. {product.partNumber}</p> : null}
        <h3 className="line-clamp-2 text-[0.9375rem] leading-snug font-semibold text-ap-ink">
          <Link
            to={productPath(product.id)}
            data-stretched-link
            className="outline-none after:absolute after:inset-0 after:rounded-md focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[var(--focus)] group-hover:text-ap-red"
          >
            {product.name}
          </Link>
        </h3>
        {product.brand ? <BrandName brand={product.brand} className="text-xs" /> : null}
        <div className="mt-auto flex flex-col gap-1 pt-2">
          <div className="flex flex-wrap items-center gap-2">
            <Price product={product} />
            {product.discountAmount ? <Badge variant="default">Ahorra {formatCOP(product.discountAmount)}</Badge> : null}
          </div>
          {product.availability ? <AvailabilityLabel availability={product.availability} className="text-xs" /> : null}
          {product.store ? (
            <StoreLine
              store={product.store}
              distanceKm={distanceKm !== undefined ? formatKm(distanceKm) : undefined}
              className="text-xs"
            />
          ) : null}
        </div>
      </div>
    </article>
  )
})

export function ProductCardSkeleton() {
  return (
    <div className="flex flex-col gap-3 border-t border-ap-line pt-4" aria-hidden>
      <Skeleton className="aspect-[4/3] w-full rounded-md" />
      <Skeleton className="h-3 w-1/3" />
      <Skeleton className="h-4 w-11/12" />
      <Skeleton className="mt-2 h-5 w-1/2" />
      <Skeleton className="h-3 w-2/5" />
    </div>
  )
}

export function ProductGrid({ children, className, busy }: { children: ReactNode; className?: string; busy?: boolean }) {
  return (
    <div
      aria-busy={busy || undefined}
      className={cn(
        'grid grid-cols-2 gap-x-4 gap-y-8 md:grid-cols-3 md:gap-x-6 transition-opacity duration-150',
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
