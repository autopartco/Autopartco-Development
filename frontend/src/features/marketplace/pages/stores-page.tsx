import { Link } from 'react-router'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { useDistanceOrigin, useStoreDistances } from '@/features/location/hooks'
import { pluralize } from '@/lib/format'
import { RECOMMENDED_EXPLANATION, RecommendedMark } from '../components/product-meta'
import { StoreRow, StoreRowSkeleton } from '../components/store-card'
import { useStores } from '../hooks'

/** Directorio de tiendas: filas con nombre, ciudad, distintivo, repuestos y distancia. */
export default function StoresPage() {
  const stores = useStores()
  const distances = useStoreDistances()
  const hasRecommended = stores.data?.some((store) => store.recommended) ?? false
  const hasCounts = stores.data?.some((store) => store.productCount !== undefined) ?? false
  const hasDistances = distances.size > 0
  const reference = useDistanceOrigin()

  return (
    <Container className="py-6 sm:py-8">
      <title>Tiendas de repuestos | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Tiendas' }]}
        label={stores.data ? `Tiendas / ${pluralize(stores.data.length, 'almacén', 'almacenes')}` : 'Tiendas / Directorio'}
        title="Tiendas de repuestos"
        description={
          <p>
            Almacenes que publican en Autopartco. Entra a una tienda para ver sus repuestos, precios y existencias.
            {hasDistances && reference
              ? ` Las distancias se miden en línea recta desde ${reference} hasta la sede más cercana de cada tienda.`
              : null}
          </p>
        }
      />

      {/* Verificación (aplica a todas, por eso no se marca tienda por tienda) y significado del distintivo. */}
      <dl className="mb-6 flex flex-col gap-2 text-sm sm:flex-row sm:flex-wrap sm:gap-x-8">
        <div className="flex flex-wrap items-center gap-2">
          <dt className="font-semibold text-ap-ink">Tienda verificada:</dt>
          <dd className="text-ap-muted">Autopartco revisa cada almacén antes de publicarlo.</dd>
        </div>
        {hasRecommended ? (
          <div className="flex flex-wrap items-center gap-2">
            <dt>
              <RecommendedMark />
            </dt>
            <dd className="text-ap-muted">{RECOMMENDED_EXPLANATION}</dd>
          </div>
        ) : null}
      </dl>

      {stores.status === 'error' ? (
        <ErrorState title="No pudimos cargar las tiendas" onRetry={stores.retry} />
      ) : !stores.data ? (
        <div className="flex flex-col divide-y divide-ap-line border-y border-ap-graphite">
          <span className="sr-only" role="status">
            Cargando tiendas
          </span>
          {Array.from({ length: 6 }, (_, index) => (
            <StoreRowSkeleton key={index} />
          ))}
        </div>
      ) : stores.data.length === 0 ? (
        <EmptyState
          title="Aún no hay tiendas publicadas"
          description="Estamos sumando almacenes de todo el país. Si tienes uno, puedes registrarlo."
        >
          <Link to="/vender" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
            Registrar mi almacén
          </Link>
        </EmptyState>
      ) : (
        <div>
          {/* Encabezado de columnas (escritorio): solo las que tienen datos. */}
          <div
            className="hidden grid-cols-[2.75rem_minmax(0,1fr)_9rem_7rem_1.25rem] gap-x-4 border-b border-ap-graphite px-3 pb-2 sm:grid"
            aria-hidden
          >
            <span />
            <span className="ap-index">Tienda</span>
            <span className="ap-index text-right">{hasCounts ? 'Repuestos' : ''}</span>
            <span className="ap-index text-right">{hasDistances ? 'Distancia' : ''}</span>
            <span />
          </div>
          <ul className="flex flex-col divide-y divide-ap-line border-b border-ap-line max-sm:border-t max-sm:border-t-ap-graphite">
            {stores.data.map((store) => (
              <li key={store.id}>
                <StoreRow store={store} distanceKm={distances.get(store.id)} />
              </li>
            ))}
          </ul>
        </div>
      )}
    </Container>
  )
}
