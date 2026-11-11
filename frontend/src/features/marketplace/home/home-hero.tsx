import { lazy, Suspense, useEffect, useState } from 'react'
import { Link } from 'react-router'
import { StoreIcon } from 'lucide-react'
import { Container } from '@/components/common/page-header'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { pluralize } from '@/lib/format'
import type { AsyncResult } from '@/lib/use-async'
import type { HomeData } from '../api/types'
import { SearchBox } from '../components/search/search-box'
import { VehiclePicker, type VehicleValue } from '../components/vehicle-picker'
import { useCapabilities } from '../hooks'
import { SEARCH_PARAMS } from '../lib/search-query'

/** El mapa (y MapLibre) se descarga aparte y después del primer pintado: no compite con el LCP. */
const CoverageMap = lazy(() => import('@/features/location/components/coverage-map').then((module) => ({ default: module.CoverageMap })))

/** Verdadero cuando el navegador queda libre (o tras un breve plazo en los que no tienen `requestIdleCallback`). */
function useAfterIdle(): boolean {
  const [ready, setReady] = useState(false)
  useEffect(() => {
    const idle = (window as Partial<Window>).requestIdleCallback
    if (idle) {
      const handle = idle.call(window, () => setReady(true), { timeout: 1500 })
      return () => window.cancelIdleCallback(handle)
    }
    const timer = globalThis.setTimeout(() => setReady(true), 300)
    return () => globalThis.clearTimeout(timer)
  }, [])
  return ready
}

function MapPlaceholder() {
  return (
    <div aria-hidden className="flex min-w-0 flex-col gap-4 self-start rounded-xl border border-ap-line bg-ap-white p-5 sm:p-6">
      <Skeleton className="h-3 w-32" />
      <Skeleton className="h-6 w-2/3" />
      <Skeleton className="aspect-[4/3] min-h-60 w-full rounded-lg" />
    </div>
  )
}

function DeferredCoverageMap() {
  const ready = useAfterIdle()
  if (!ready) return <MapPlaceholder />
  return (
    <Suspense fallback={<MapPlaceholder />}>
      <CoverageMap />
    </Suspense>
  )
}

function searchTermUrl(term: string): string {
  return `/buscar?${new URLSearchParams({ [SEARCH_PARAMS.q]: term })}`
}

/** Conteo real de almacenes y repuestos; sin datos no se muestra nada (nunca cifras inventadas). */
function HeroStats({ home }: { home: AsyncResult<HomeData> }) {
  if (home.status === 'error') return null
  if (!home.data) return <Skeleton className="h-5 w-64" aria-hidden />
  const { stores, products } = home.data.stats
  return (
    <p className="text-sm font-medium text-ap-ink tabular-nums">
      {pluralize(stores, 'almacén', 'almacenes')} <span className="text-ap-steel" aria-hidden>·</span>{' '}
      {pluralize(products, 'repuesto publicado', 'repuestos publicados')}
    </p>
  )
}

/**
 * Inicio, parte 1 (§3): propuesta, buscador grande, vehículo en línea, búsquedas populares y,
 * al lado (debajo en móvil), la cobertura real. La invitación a almacenes queda arriba, a la vista.
 */
export function HomeHero({ home }: { home: AsyncResult<HomeData> }) {
  const capabilities = useCapabilities()
  const [vehicle, setVehicle] = useState<VehicleValue>({})
  const showVehicle = capabilities === undefined || capabilities.filters.has('vehicle')
  const popular = home.data?.popularSearches ?? []

  return (
    <section aria-labelledby="hero-title" className="border-b border-ap-line bg-ap-paper">
      <Container className="flex flex-col gap-8 py-6 sm:py-8 lg:gap-10 lg:py-10">
        <div className="flex flex-wrap items-center justify-between gap-x-6 gap-y-2 border-b border-ap-line pb-4">
          <p className="text-sm text-ap-muted">¿Vendes repuestos? Muestra tu inventario a quienes lo buscan cerca.</p>
          <Button variant="outline" nativeButton={false} render={<Link to="/vender" />}>
            <StoreIcon aria-hidden />
            Registrar mi almacén
          </Button>
        </div>

        <div className="grid gap-10 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:gap-14">
          <div className="flex min-w-0 flex-col gap-5">
            <h1 id="hero-title" className="max-w-2xl text-display text-ap-graphite">
              Encuentra el repuesto exacto para tu carro, en almacenes cerca de ti.
            </h1>
            <HeroStats home={home} />
            <SearchBox size="lg" vehicle={vehicle} className="max-w-2xl" />

            {showVehicle ? (
              <fieldset className="max-w-2xl min-w-0">
                <legend className="mb-2 text-sm font-semibold text-ap-ink">
                  ¿Qué carro tienes? <span className="font-normal text-ap-muted">(opcional, filtra por compatibilidad)</span>
                </legend>
                <VehiclePicker
                  value={vehicle}
                  onChange={setVehicle}
                  className="grid-cols-1 sm:grid-cols-3 [&_[data-slot=select-trigger]]:h-11 [&_label]:text-xs [&_label]:font-normal [&_label]:text-ap-muted"
                />
              </fieldset>
            ) : null}

            {popular.length > 0 ? (
              <div className="flex max-w-2xl flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                <span className="text-ap-muted">Lo más buscado:</span>
                <ul className="flex flex-wrap gap-2">
                  {popular.map((term) => (
                    <li key={term}>
                      <Link
                        to={searchTermUrl(term)}
                        className="inline-flex min-h-11 items-center rounded-sm border border-ap-line-strong bg-ap-white px-3 text-ap-ink transition-colors hover:border-ap-graphite hover:bg-ap-paper"
                      >
                        {term}
                      </Link>
                    </li>
                  ))}
                </ul>
              </div>
            ) : null}
          </div>
          <DeferredCoverageMap />
        </div>
      </Container>
    </section>
  )
}
