import { Link, useSearchParams } from 'react-router'
import { XIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { useStoreSearch, useStoreState } from '../../hooks'
import { searchTabHref } from '../../lib/search-query'
import { toStoreQuery } from '../../lib/store-search-query'
import { ResultsPagination } from '../results-pagination'
import { StoreResultRow, StoreRowSkeleton } from '../store-card'
import { SearchTabs } from './search-tabs'
import { errorCopy, useCityOptions } from './store-search-support'

const ALL_CITIES = 'all'

/** Pestaña «Tiendas» de `/buscar`: texto y ciudad en la URL, filas con separadores y paginación con enlaces. */
export function StoresSearchView() {
  const [state, update] = useStoreState()
  const [searchParams] = useSearchParams()
  const result = useStoreSearch(toStoreQuery(state))
  const cities = useCityOptions(state.cityCode)
  const page = result.data
  const cityName = state.cityCode ? cities.find((city) => city.code === state.cityCode)?.name : undefined
  const title = state.q ? `Tiendas para “${state.q}”` : 'Todas las tiendas'
  const items = [{ value: ALL_CITIES, label: 'Todas las ciudades' }, ...cities.map((city) => ({ value: city.code, label: city.name }))]

  let content
  if (result.status === 'error') {
    content = <ErrorState {...errorCopy(result.error)} onRetry={result.retry} />
  } else if (!page) {
    content = (
      <>
        <span className="sr-only" role="status">
          Cargando tiendas
        </span>
        <div className="flex flex-col divide-y divide-ap-line border-b border-ap-line">
          {Array.from({ length: 5 }, (_, index) => (
            <StoreRowSkeleton key={index} />
          ))}
        </div>
      </>
    )
  } else if (page.items.length === 0 && !result.isStale) {
    content = (
      <EmptyState
        title={state.q ? `No encontramos tiendas para “${state.q}”` : 'Ninguna tienda coincide con esta ciudad'}
        description={
          <ul className="mt-1 flex list-disc flex-col gap-1 pl-5">
            {state.q ? <li>Revisa la ortografía o usa menos palabras.</li> : null}
            {state.cityCode ? <li>Quita el filtro de ciudad para ver tiendas de todo el país.</li> : null}
            <li>Si buscas una pieza, mira los repuestos publicados.</li>
          </ul>
        }
      >
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          {state.cityCode ? (
            <button
              type="button"
              onClick={() => update({ cityCode: undefined })}
              className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
            >
              Ver todas las ciudades
            </button>
          ) : null}
          <Link
            to={searchTabHref(searchParams, 'stores', 'all')}
            className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
          >
            Ver repuestos
          </Link>
        </div>
      </EmptyState>
    )
  } else {
    content = (
      <>
        <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', result.isStale && 'opacity-60')} aria-busy={result.isStale}>
          {page.items.map((store) => (
            <li key={store.id}>
              <StoreResultRow store={store} />
            </li>
          ))}
        </ul>
        <ResultsPagination page={page.page} totalPages={page.totalPages} />
      </>
    )
  }

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${title} | Autopartco`}</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Buscar' }]}
        label="Catálogo / Tiendas"
        title={title}
        description="Almacenes publicados en Autopartco. Entra a una tienda para ver sus repuestos, servicios y sedes."
        className="mb-6"
      />
      <SearchTabs active="stores" />
      <section aria-labelledby="resultados-tiendas-titulo" className="flex min-w-0 flex-col gap-4">
        <h2 id="resultados-tiendas-titulo" className="sr-only">
          Tiendas
        </h2>
        <div className="flex min-h-11 flex-wrap items-center justify-between gap-3 border-b border-ap-graphite pb-2">
          <p role="status" aria-live="polite" className="text-sm text-ap-muted tabular-nums">
            {page && !result.isStale ? (
              <>
                <strong className="font-semibold text-ap-ink">{pluralize(page.total, 'tienda', 'tiendas')}</strong>
                {page.totalPages > 1 ? ` · página ${page.page} de ${page.totalPages}` : ''}
              </>
            ) : (
              'Buscando tiendas…'
            )}
          </p>
          {cities.length > 0 ? (
            <div className="flex items-center gap-2">
              <Label htmlFor="ciudad-tiendas" className="text-sm font-normal text-ap-muted">
                Ciudad
              </Label>
              <Select
                items={items}
                value={state.cityCode ?? ALL_CITIES}
                onValueChange={(next) => next && update({ cityCode: next === ALL_CITIES ? undefined : next })}
              >
                <SelectTrigger id="ciudad-tiendas" className="h-11 min-w-44 bg-ap-white">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent align="end">
                  {items.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}
        </div>
        {state.cityCode ? (
          <div className="flex flex-wrap items-center gap-2">
            <p className="ap-index mr-1">Filtros</p>
            <button
              type="button"
              onClick={() => update({ cityCode: undefined })}
              aria-label={`Quitar filtro: ${cityName ?? 'Ciudad elegida'}`}
              className="inline-flex h-9 items-center gap-1.5 rounded-sm border border-ap-graphite bg-ap-paper pr-2 pl-2.5 text-sm text-ap-ink transition-colors hover:bg-ap-white pointer-coarse:h-11"
            >
              <span className="font-medium">{cityName ?? 'Ciudad elegida'}</span>
              <XIcon className="size-3.5 text-ap-muted" aria-hidden />
            </button>
          </div>
        ) : null}
        {content}
      </section>
    </Container>
  )
}
