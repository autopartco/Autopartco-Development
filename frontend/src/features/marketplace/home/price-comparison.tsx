import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { ErrorState, LoadingState } from '@/components/common/states'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { useStoreDistances } from '@/features/location/hooks'
import { formatKm } from '@/features/location/lib/geo'
import { formatCOP } from '@/lib/format'
import type { Product } from '../api/types'
import { AvailabilityLabel, RecommendedMark } from '../components/product-meta'
import { productPath } from '../lib/paths'
import { useMostOfferedReference, type ReferenceOffers } from './reference-offers'

/** Tabla de ofertas de una misma referencia, del precio más bajo al más alto. */
function ComparisonTable({ reference }: { reference: ReferenceOffers }) {
  const distances = useStoreDistances()
  const offers = reference.offers.toSorted((a, b) => a.price - b.price)
  const cheapest = offers[0]
  const distanceOf = (offer: Product) => (offer.store ? distances.get(offer.store.id) : undefined)
  const nearest = offers
    .filter((offer) => distanceOf(offer) !== undefined)
    .reduce<Product | undefined>((best, offer) => (!best || (distanceOf(offer) ?? 0) < (distanceOf(best) ?? 0) ? offer : best), undefined)
  const showDistance = nearest !== undefined
  const showStock = offers.some((offer) => offer.availability)

  return (
    <div className="overflow-x-auto" tabIndex={0} role="region" aria-label="Comparación de precios por almacén">
      <Table className="min-w-[32rem]">
        <TableCaption>Precios publicados por cada tienda. Confirma existencias antes de ir.</TableCaption>
        <TableHeader>
          <TableRow>
            <TableHead>Almacén</TableHead>
            {showDistance ? <TableHead numeric>Distancia</TableHead> : null}
            {showStock ? <TableHead>Existencias</TableHead> : null}
            <TableHead numeric>Precio</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {offers.map((offer) => {
            const km = distanceOf(offer)
            return (
              <TableRow key={offer.listingKey} className="hover:bg-ap-paper">
                <TableCell>
                  <div className="flex flex-col gap-1">
                    <Link
                      to={productPath(offer.id)}
                      className="font-semibold text-ap-ink underline-offset-4 hover:text-ap-red hover:underline"
                    >
                      {offer.store?.name ?? 'Tienda'}
                    </Link>
                    <span className="flex flex-wrap gap-1">
                      {offer === cheapest ? <Badge variant="default">Precio más bajo</Badge> : null}
                      {offer === nearest ? <Badge variant="neutral" className="text-ap-ink">Más cerca</Badge> : null}
                      {offer.store?.recommended ? <RecommendedMark /> : null}
                    </span>
                  </div>
                </TableCell>
                {showDistance ? (
                  <TableCell numeric className="ap-ref">
                    {km !== undefined ? (
                      formatKm(km)
                    ) : (
                      <>
                        <span aria-hidden>—</span>
                        <span className="sr-only">Sin dato</span>
                      </>
                    )}
                  </TableCell>
                ) : null}
                {showStock ? (
                  <TableCell>{offer.availability ? <AvailabilityLabel availability={offer.availability} /> : null}</TableCell>
                ) : null}
                <TableCell numeric className="ap-price text-base">
                  {formatCOP(offer.price)}
                </TableCell>
              </TableRow>
            )
          })}
        </TableBody>
      </Table>
    </div>
  )
}

/**
 * Comparación real: la referencia que más almacenes publican, con el precio de cada uno.
 * Si ninguna referencia la publican dos o más tiendas, se explica en lugar de inventarla.
 */
export function ComparisonSection() {
  const { status, loading, reference, retry } = useMostOfferedReference()
  const enough = reference && reference.offers.length > 1

  return (
    <section aria-labelledby="comparar-titulo" className="py-14 sm:py-20">
      <Container className="grid gap-8 lg:grid-cols-[minmax(0,4fr)_minmax(0,8fr)] lg:gap-14">
        <div className="flex flex-col gap-3">
          <p className="ap-index">Comparar / Una referencia</p>
          <h2 id="comparar-titulo" className="text-section text-ap-ink">
            Mismo repuesto, distintos almacenes
          </h2>
          {enough ? (
            <dl className="mt-2 grid grid-cols-[auto_minmax(0,1fr)] gap-x-4 gap-y-2 border-t border-ap-line pt-4 text-sm">
              <dt className="text-ap-muted">Repuesto</dt>
              <dd className="font-medium text-ap-ink">{reference.name}</dd>
              {reference.brand ? (
                <>
                  <dt className="text-ap-muted">Marca</dt>
                  <dd className="font-medium text-ap-ink">{reference.brand.name}</dd>
                </>
              ) : null}
              <dt className="text-ap-muted">Referencia</dt>
              <dd className="ap-ref text-ap-ink">{reference.partNumber}</dd>
              <dt className="text-ap-muted">Almacenes</dt>
              <dd className="font-medium text-ap-ink tabular-nums">{reference.offers.length}</dd>
            </dl>
          ) : (
            <p className="max-w-md text-ap-muted">
              El precio que publica cada almacén por la misma referencia, del más bajo al más alto.
            </p>
          )}
        </div>
        <div className="min-w-0">
          {status === 'error' ? (
            <ErrorState title="No pudimos cargar la comparación de precios" onRetry={retry} />
          ) : loading ? (
            <LoadingState label="Cargando comparación de precios" rows={4} />
          ) : enough ? (
            <ComparisonTable reference={reference} />
          ) : (
            <p className="border-y border-ap-line py-6 text-ap-muted">
              Todavía ninguna referencia la publican dos o más almacenes. Cuando ocurra, aquí verás sus precios lado a
              lado.
            </p>
          )}
        </div>
      </Container>
    </section>
  )
}
