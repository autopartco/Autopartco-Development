import type { MouseEvent } from 'react'
import { MegaphoneIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import type { Product } from '../../api/types'
import { trackAdClick } from '../../lib/ad-events'
import { ProductCard, ProductRow } from '../product-card'
import { useAdImpression } from './use-ad-impression'

/** Etiqueta obligatoria de todo anuncio pagado. */
export function SponsoredLabel({ className }: { className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-1 text-xs font-semibold text-ap-muted', className)}>
      <MegaphoneIcon className="size-3.5" aria-hidden />
      Patrocinado
    </span>
  )
}

interface SponsoredListingProps {
  product: Product
  /** `row`: fila de resultados; `card`: ficha compacta para cuadrículas (inicio, categoría, producto). */
  variant?: 'row' | 'card'
  eager?: boolean
  className?: string
}

/**
 * Anuncio como listado con la etiqueta «Patrocinado». Cuenta la impresión al verse (50 % durante 1 s, una vez
 * por token) y el clic al abrir el producto. Es el componente que reutilizan búsqueda, categoría, inicio y producto.
 */
export function SponsoredListing({ product, variant = 'row', eager, className }: SponsoredListingProps) {
  const token = product.adImpressionToken
  const ref = useAdImpression<HTMLDivElement>(token)

  function handleClick(event: MouseEvent<HTMLDivElement>) {
    if (!token || !(event.target instanceof Element)) return
    if (event.target.closest('a')) trackAdClick(token)
  }

  return (
    // El clic se captura en el contenedor: cualquier enlace del listado cuenta como clic del anuncio.
    <div ref={ref} onClickCapture={handleClick} className={cn('relative', className)}>
      <div className="pt-3">
        <SponsoredLabel />
      </div>
      {variant === 'card' ? (
        <ProductCard product={product} eager={eager} distanceKm={product.distanceKm} />
      ) : (
        <ProductRow product={product} eager={eager} distanceKm={product.distanceKm} />
      )}
    </div>
  )
}
