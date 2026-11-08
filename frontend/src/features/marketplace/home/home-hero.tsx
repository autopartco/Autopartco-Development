import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import type { NearbyStores } from '@/features/location/api/types'
import { useNearbyStores } from '@/features/location/hooks'
import { formatKm } from '@/features/location/lib/geo'
import { pluralize } from '@/lib/format'
import type { AsyncResult } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { HeroSearch } from '../components/hero-search'
import { RecommendedMark } from '../components/product-meta'
import { storeProductsPath } from '../lib/paths'
import { searchUrl } from '../lib/product-query'

/** Ejemplos de búsqueda: atajos al buscador, no un ranking. */
const SEARCH_EXAMPLES = ['Pastillas de freno', 'Filtro de aceite', 'Kit de embrague', 'Bujías', 'Amortiguador']
const NEARBY_LIMIT = 5

/**
 * Almacenes con sede en la ciudad elegida, del más cercano al más lejano. Es el contexto
 * real de cobertura junto al buscador: nombre, zona, distancia y acceso a sus repuestos.
 */
function NearbyStoresPanel({ result }: { result: AsyncResult<NearbyStores> }) {
  const data = result.data
  const stops = data?.stores.slice(0, NEARBY_LIMIT) ?? []
  const place = data?.city ? `${data.origin ? `${data.origin.label}, ` : ''}${data.city.name}` : undefined
  const hasDistances = stops.some((stop) => stop.distanceKm !== undefined)

  let body: ReactNode
  if (result.status === 'error') {
    body = (
      <ErrorState
        title="No pudimos cargar los almacenes cercanos"
        description="Revisa tu conexión e inténtalo de nuevo. Mientras tanto, puedes buscar tu repuesto."
        onRetry={result.retry}
      />
    )
  } else if (!data) {
    body = (
      <div className="flex flex-col divide-y divide-ap-line" aria-hidden>
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="flex items-center justify-between gap-4 py-3">
            <Skeleton className="h-4 w-1/2" />
            <Skeleton className="h-4 w-14" />
          </div>
        ))}
      </div>
    )
  } else if (stops.length === 0) {
    body = (
      <div className="flex flex-col items-start gap-2 py-2">
        <p className="font-semibold text-ap-ink">Aún no hay almacenes con sede en {data.city?.name ?? 'esta ciudad'}</p>
        <p className="text-sm text-ap-muted">Elige otra ciudad en la cabecera o mira el directorio completo.</p>
      </div>
    )
  } else {
    body = (
      <ol className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
        {stops.map((stop) => (
          <li key={stop.key} className="group relative flex items-start justify-between gap-4 py-3">
            <div className="flex min-w-0 flex-col gap-1">
              <Link
                to={storeProductsPath(stop.store.id)}
                data-stretched-link
                className="font-semibold text-ap-ink after:absolute after:inset-0 focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-[var(--focus)] group-hover:text-ap-red"
              >
                {stop.store.name}
              </Link>
              <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ap-muted">
                {(stop.area ?? stop.branchName) ? <span>{stop.area ?? stop.branchName}</span> : null}
                {stop.store.productCount !== undefined ? (
                  <span className="tabular-nums">{pluralize(stop.store.productCount, 'repuesto', 'repuestos')}</span>
                ) : null}
                {stop.store.recommended ? <RecommendedMark /> : null}
              </p>
            </div>
            {stop.distanceKm !== undefined ? (
              <span className="ap-ref shrink-0 pt-0.5 text-ap-ink">
                {formatKm(stop.distanceKm)}
                <span className="sr-only"> en línea recta</span>
              </span>
            ) : null}
          </li>
        ))}
      </ol>
    )
  }

  return (
    <section
      aria-labelledby="cerca-titulo"
      aria-busy={result.isStale || undefined}
      className={cn(
        'flex min-w-0 flex-col gap-4 self-start rounded-xl border border-ap-line bg-ap-white p-5 transition-opacity duration-150 sm:p-6',
        result.isStale && data && 'opacity-60',
      )}
    >
      <div className="flex flex-col gap-1">
        <p className="ap-index">Cobertura{data?.city ? ` / ${data.city.name}` : ''}</p>
        <h2 id="cerca-titulo" className="text-block-title text-ap-ink">
          {place ? `Almacenes cerca de ${place}` : 'Almacenes en Autopartco'}
        </h2>
        {data && data.stores.length > 0 ? (
          <p className="text-sm text-ap-muted tabular-nums">
            {pluralize(data.stores.length, 'almacén con sede', 'almacenes con sede')} en {data.city?.name ?? 'la ciudad'}
            {data.stores.length > stops.length ? `; los ${stops.length} más cercanos` : ''}
          </p>
        ) : null}
      </div>
      {body}
      {data && stops.length > 0 && hasDistances && place ? (
        <p className="text-xs text-ap-muted">
          Distancia en línea recta desde {place}, un punto de referencia mientras habilitamos tu ubicación.
        </p>
      ) : null}
      <Link
        to="/tiendas"
        className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
      >
        Ver el directorio de tiendas
      </Link>
    </section>
  )
}

/**
 * Inicio (§7.1): titular a la izquierda, el buscador como herramienta principal con
 * ejemplos, y al lado la cobertura real (almacenes con sede en la ciudad elegida).
 * En móvil se apila: titular, buscador, ejemplos y almacenes.
 */
export function HomeHero() {
  const nearby = useNearbyStores()
  const city = nearby.data?.city?.name

  return (
    <section aria-labelledby="hero-title" className="border-b border-ap-line bg-ap-paper">
      <Container className="grid gap-10 py-10 sm:py-12 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:gap-14 lg:py-16">
        <div className="flex min-w-0 flex-col gap-5">
          <p className="ap-index">Catálogo / Repuestos en {city ?? 'Colombia'}</p>
          <h1 id="hero-title" className="max-w-2xl text-display text-ap-graphite">
            Tu repuesto, en el almacén más cercano.
          </h1>
          <p className="max-w-xl text-base text-ap-muted sm:text-lg">
            Para conductores y talleres: busca por nombre o referencia y compara precio, existencias y distancia entre
            los almacenes de {city ?? 'tu ciudad'}.
          </p>
          <HeroSearch className="max-w-2xl" />
          <div className="flex max-w-2xl flex-wrap items-center gap-x-1 gap-y-1 text-sm">
            <span className="mr-2 text-ap-muted">Prueba con:</span>
            <ul className="contents">
              {SEARCH_EXAMPLES.map((term, index) => (
                <li key={term} className="flex items-center">
                  {index > 0 ? (
                    <span className="px-1 text-ap-steel" aria-hidden>
                      ·
                    </span>
                  ) : null}
                  <Link
                    to={searchUrl({ q: term })}
                    className="inline-flex min-h-11 items-center px-1 font-medium text-ap-ink underline decoration-ap-line-strong underline-offset-4 hover:text-ap-red hover:decoration-ap-red"
                  >
                    {term}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>
        <NearbyStoresPanel result={nearby} />
      </Container>
    </section>
  )
}
