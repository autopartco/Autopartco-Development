import { Link } from 'react-router'
import { m } from 'motion/react'
import { Container } from '@/components/common/page-header'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { useStoreDistances } from '@/features/location/hooks'
import { formatKm } from '@/features/location/lib/geo'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Product } from '../api/types'
import { RecommendedMark } from '../components/product-meta'
import { productPath } from '../lib/paths'
import { EASE_THREAD } from './motion-presets'
import { useMostOfferedReference, type ReferenceOffers } from './reference-offers'

/** Las barras crecen con 120 ms de escalonado (solo `transform: scaleX`). */
const ROWS = { hidden: {}, show: { transition: { staggerChildren: 0.12 } } }
const ROW = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { duration: 0.3 } },
}
const BAR = {
  hidden: { scaleX: 0 },
  show: (ratio: number) => ({ scaleX: ratio, transition: { duration: 0.7, ease: EASE_THREAD } }),
}

function Tag({ children, tone }: { children: string; tone: 'brand' | 'ink' }) {
  return (
    <span
      className={cn(
        'rounded-sm px-1.5 py-0.5 label-mono text-white',
        tone === 'brand' ? 'bg-brand' : 'bg-asphalt',
      )}
    >
      {children}
    </span>
  )
}

function ComparisonRows({ reference }: { reference: ReferenceOffers }) {
  const distances = useStoreDistances()
  const offers = reference.offers.toSorted((a, b) => a.price - b.price)
  const maxPrice = Math.max(...offers.map((offer) => offer.price))
  const cheapest = offers[0]
  const distanceOf = (offer: Product) => (offer.store ? distances.get(offer.store.id) : undefined)
  const nearest = offers
    .filter((offer) => distanceOf(offer) !== undefined)
    .reduce<Product | undefined>((best, offer) => (!best || (distanceOf(offer) ?? 0) < (distanceOf(best) ?? 0) ? offer : best), undefined)

  return (
    <m.ol
      className="flex flex-col gap-1"
      initial="hidden"
      whileInView="show"
      viewport={{ once: true, amount: 0.3 }}
      variants={ROWS}
      aria-label={`Precio de ${reference.name}, referencia ${reference.partNumber}, por almacén`}
    >
      {offers.map((offer) => {
        const isCheapest = offer === cheapest
        const isNearest = offer === nearest
        const km = distanceOf(offer)
        const outOfStock = offer.availability?.status === 'out_of_stock'
        return (
          <m.li key={offer.listingKey} variants={ROW}>
            <Link
              to={productPath(offer.id)}
              className="grid grid-cols-[minmax(0,1fr)_auto] items-center gap-x-4 gap-y-2 rounded-lg px-3 py-3 transition-colors hover:bg-card md:grid-cols-[minmax(0,15rem)_minmax(0,1fr)_7.5rem]"
            >
              <span className="flex min-w-0 flex-col">
                <span className="truncate font-semibold">{offer.store?.name ?? 'Tienda'}</span>
                <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-muted-foreground">
                  {km !== undefined ? <span className="tabular-nums">a {formatKm(km)}</span> : null}
                  {outOfStock ? <span>Agotado</span> : null}
                  {isCheapest ? <Tag tone="brand">Más barato</Tag> : null}
                  {isNearest ? <Tag tone="ink">Más cerca</Tag> : null}
                  {offer.store?.recommended ? <RecommendedMark /> : null}
                </span>
              </span>
              <span className="text-right font-mono text-base font-semibold tabular-nums md:order-last">
                {formatCOP(offer.price)}
              </span>
              <span className="relative col-span-2 h-2.5 overflow-hidden rounded-full bg-concrete md:col-span-1" aria-hidden>
                <m.span
                  custom={offer.price / maxPrice}
                  variants={BAR}
                  className={cn(
                    'absolute inset-0 origin-left rounded-full',
                    isCheapest ? 'bg-thread' : isNearest ? 'bg-asphalt' : 'bg-map-water',
                  )}
                />
              </span>
            </Link>
          </m.li>
        )
      })}
    </m.ol>
  )
}

export function ComparisonSection() {
  const { status, loading, reference, retry } = useMostOfferedReference()
  const enough = reference && reference.offers.length > 1

  return (
    <section aria-labelledby="comparar-titulo" className="py-20 sm:py-28">
      <Container className="grid gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-16">
        <div className="flex flex-col gap-5">
          <p className="label-mono text-muted-foreground tabular-nums">
            {enough ? `${reference.offers.length} almacenes, una referencia` : 'Comparar precios'}
          </p>
          <h2 id="comparar-titulo" className="text-section">
            Mismo repuesto, distintos almacenes
          </h2>
          <p className="max-w-md text-base text-muted-foreground sm:text-lg">
            {enough
              ? `${reference.name}${reference.brand ? ` ${reference.brand.name}` : ''}, referencia ${reference.partNumber}: el precio que publica cada almacén, del más barato al más caro.`
              : 'El precio que publica cada almacén por la misma referencia, del más barato al más caro.'}
          </p>
          <p className="text-sm text-muted-foreground">Cada tienda define su precio. Confirma existencias antes de ir.</p>
        </div>
        <div className="min-w-0">
          {status === 'error' ? (
            <ErrorState title="No pudimos cargar la comparación de precios" onRetry={retry} />
          ) : loading ? (
            <div className="flex flex-col gap-4" aria-hidden>
              {Array.from({ length: 5 }, (_, index) => (
                <Skeleton key={index} className="h-12 rounded-lg" />
              ))}
            </div>
          ) : enough ? (
            <ComparisonRows reference={reference} />
          ) : (
            <p className="rounded-xl border border-border bg-card p-6 text-muted-foreground">
              Cuando varios almacenes publiquen la misma referencia, aquí verás sus precios lado a lado.
            </p>
          )}
        </div>
      </Container>
    </section>
  )
}
