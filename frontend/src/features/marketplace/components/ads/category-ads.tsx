import { useAds } from '../../hooks'
import { ProductGrid } from '../product-card'
import { SponsoredListing } from './sponsored-listing'

const CATEGORY_AD_LIMIT = 3

/**
 * Anuncios `CATEGORY_TOP` en la cabecera de la categoría (hasta 3). Sin anuncios, con error o mientras
 * cargan no se muestra nada: no reserva espacio ni anuncia carga.
 */
export function CategoryAds({ categoryId }: { categoryId: string }) {
  const ads = useAds({ placement: 'CATEGORY_TOP', categoryId })
  const items = ads.data?.slice(0, CATEGORY_AD_LIMIT)
  if (!items || items.length === 0) return null
  return (
    <section aria-label="Anuncios de la categoría" className="mb-6 border-y border-ap-line bg-ap-paper/60 px-3 pb-4 sm:px-4">
      <ProductGrid className="grid-cols-1 sm:grid-cols-3 md:grid-cols-3">
        {items.map((product, index) => (
          <SponsoredListing key={`${product.listingKey}:${index}`} product={product} variant="card" eager={index < 3} />
        ))}
      </ProductGrid>
    </section>
  )
}
