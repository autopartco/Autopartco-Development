import { useState } from 'react'
import { Link } from 'react-router'
import { InfoIcon, SlidersHorizontalIcon } from 'lucide-react'
import { Container, PageIntro, type Crumb } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetTitle,
  SheetTrigger,
} from '@/components/ui/sheet'
import { pluralize } from '@/lib/format'
import { MarketplaceError, type FilterOptions, type SearchResult } from '../api/types'
import { CategoryAds } from '../components/ads/category-ads'
import { SponsoredListing } from '../components/ads/sponsored-listing'
import { ProductList, ProductListSkeleton, ProductRow } from '../components/product-card'
import { ResultsPagination } from '../components/results-pagination'
import { ActiveFilters } from '../components/search/active-filters'
import { SearchFilters, type SearchFiltersProps } from '../components/search/filters-panel'
import { InterpretedChips } from '../components/search/interpreted-chips'
import { SortSelect } from '../components/search/sort-select'
import { useNearMe } from '../components/search/use-near-me'
import { useFilterOptions, useListingSearch, useSearchState, useStores, useVehicleFilterLabel } from '../hooks'
import { categoryPath } from '../lib/paths'
import { countSearchFilters, dropInterpretedChip, toSearchQuery, type SearchState } from '../lib/search-query'
import { NotFoundContent } from './not-found-page'

const CLEARED_FILTERS: Partial<SearchState> = {
  category: undefined,
  manufacturers: [],
  store: undefined,
  makeId: undefined,
  modelId: undefined,
  versionId: undefined,
  year: undefined,
  minPrice: undefined,
  maxPrice: undefined,
  inStock: false,
  onSale: false,
  cityCode: undefined,
  near: false,
  radiusKm: undefined,
  minRating: undefined,
  minProductRating: undefined,
  universal: false,
}

/** Mensaje de error según el código del servidor; el resto cae en el genérico. */
function errorCopy(error: unknown): { title: string; description: string } {
  const code = error instanceof MarketplaceError ? error.code : undefined
  if (code === 'SEARCH_RATE_LIMITED' || (error instanceof MarketplaceError && error.status === 429)) {
    return {
      title: 'Estás buscando muy rápido',
      description: 'Espera unos segundos y vuelve a intentarlo. Tus filtros se mantienen.',
    }
  }
  if (code === 'LOCATION_REQUIRED') {
    return {
      title: 'Necesitamos tu ubicación',
      description: 'Para ordenar por cercanía activa «Cerca de mí» en los filtros, o elige otro orden.',
    }
  }
  if (code === 'VEHICLE_FILTER_INVALID') {
    return {
      title: 'El filtro de vehículo no es válido',
      description: 'Elige primero la marca y luego el modelo, o quita el filtro de vehículo.',
    }
  }
  if (code?.startsWith('SEARCH_')) {
    return {
      title: 'No pudimos interpretar la búsqueda',
      description: 'Revisa los filtros o el texto, por ejemplo quita el precio o el radio, e inténtalo de nuevo.',
    }
  }
  return {
    title: 'No pudimos cargar los repuestos',
    description: 'El catálogo no respondió. Revisa tu conexión e inténtalo de nuevo; tus filtros se mantienen.',
  }
}

/** Filtros en móvil y tablet: botón "Filtrar (n)" que abre una hoja inferior accesible. */
function MobileFilters({
  activeCount,
  total,
  onClear,
  ...filters
}: SearchFiltersProps & { activeCount: number; total: number | undefined; onClear: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={<Button variant="outline" className="gap-2 px-3 lg:hidden" />}
        aria-label={
          activeCount > 0
            ? `Filtrar: ${activeCount} ${activeCount === 1 ? 'filtro activo' : 'filtros activos'}`
            : 'Filtrar resultados'
        }
      >
        <SlidersHorizontalIcon aria-hidden />
        <span className="tabular-nums">{activeCount > 0 ? `Filtrar (${activeCount})` : 'Filtrar'}</span>
      </SheetTrigger>
      {/* Cabecera y pie fijos; solo la lista de filtros hace scroll, así las acciones siempre se ven. */}
      <SheetContent side="bottom" className="max-h-[90svh] gap-0 rounded-t-xl p-0">
        <SheetHeader className="shrink-0 border-b border-ap-line px-5 py-4 pr-16">
          <SheetTitle className="text-block-title">Filtrar resultados</SheetTitle>
          <SheetDescription>Los resultados se actualizan al elegir cada filtro.</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-5">
          <SearchFilters {...filters} />
        </div>
        <SheetFooter className="mt-0 grid shrink-0 grid-cols-2 gap-2 border-t border-ap-line px-5 pt-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button variant="outline" onClick={onClear} disabled={activeCount === 0}>
            Limpiar
          </Button>
          <SheetClose render={<Button />}>
            {total === undefined ? 'Ver resultados' : `Ver ${pluralize(total, 'resultado', 'resultados')}`}
          </SheetClose>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function EmptyResults({
  state,
  options,
  activeCount,
  onClear,
}: {
  state: SearchState
  options: FilterOptions | undefined
  activeCount: number
  onClear: () => void
}) {
  return (
    <EmptyState
      title={state.q ? `No encontramos repuestos para “${state.q}”` : 'Ningún repuesto coincide con estos filtros'}
      description={
        <ul className="mt-1 flex list-disc flex-col gap-1 pl-5">
          {state.q ? <li>Revisa la ortografía o usa menos palabras, por ejemplo “pastillas”.</li> : null}
          <li>Busca por la referencia o número de parte que aparece en la pieza.</li>
          {activeCount > 0 ? <li>Quita algunos filtros o amplía el radio para ver más resultados.</li> : null}
        </ul>
      }
    >
      <div className="flex flex-col gap-4">
        {activeCount > 0 ? (
          <Button variant="outline" className="w-fit px-4" onClick={onClear}>
            Quitar todos los filtros
          </Button>
        ) : null}
        {options && options.categories.length > 0 ? (
          <div className="flex flex-col gap-2">
            <p className="text-sm font-semibold text-ap-ink">O explora por categoría</p>
            <ul className="flex flex-wrap gap-2">
              {options.categories.slice(0, 8).map((category) => (
                <li key={category.id}>
                  <Link
                    to={categoryPath(category.slug)}
                    className="inline-flex h-10 items-center rounded-sm border border-ap-line-strong bg-ap-white px-3 text-sm text-ap-ink transition-colors hover:border-ap-graphite hover:bg-ap-paper pointer-coarse:h-11"
                  >
                    {category.name}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        ) : null}
      </div>
    </EmptyState>
  )
}

function ResultCount({ page, loading }: { page: SearchResult | undefined; loading: boolean }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-ap-muted tabular-nums">
      {page && !loading ? (
        <>
          <strong className="font-semibold text-ap-ink">{pluralize(page.total, 'resultado', 'resultados')}</strong>
          {page.totalPages > 1 ? ` · página ${page.page} de ${page.totalPages}` : ''}
        </>
      ) : (
        'Buscando repuestos…'
      )}
    </p>
  )
}

/**
 * Resultados del motor de búsqueda v2 (§7.2, §15): cabecera con la consulta visible, línea de control
 * con conteo y orden, filtros laterales en escritorio (hoja inferior en móvil), anuncios «Patrocinado»
 * arriba y resultados en filas con divisores. Todo el estado vive en la URL.
 */
export function SearchResultsView({ fixedCategory }: { fixedCategory?: string }) {
  const [state, update] = useSearchState(fixedCategory)
  const nearMe = useNearMe()
  const options = useFilterOptions()
  const stores = useStores()
  const vehicleLabel = useVehicleFilterLabel(state)
  const result = useListingSearch(toSearchQuery(state, nearMe.origin))

  const isCategoryRoute = Boolean(fixedCategory)
  const activeCount = countSearchFilters(state, { fixedCategory: isCategoryRoute })
  const category = options.data?.categories.find((item) => item.slug === state.category)
  const store = state.store ? stores.data?.find((item) => item.id === state.store) : undefined
  const clearFilters = () => update(CLEARED_FILTERS)

  const page = result.data
  const filters: SearchFiltersProps = {
    state,
    facets: page?.facets,
    options: options.data,
    nearMe,
    onChange: update,
    fixedCategory: isCategoryRoute,
  }

  if (isCategoryRoute && options.data && options.data.categories.length > 0 && !category) {
    return (
      <NotFoundContent
        title="Esta categoría no existe"
        description="Puede que el enlace esté incompleto o que la categoría ya no esté disponible."
      />
    )
  }

  const title = store
    ? `Repuestos de ${store.name}`
    : isCategoryRoute
      ? (category?.name ?? 'Categoría')
      : state.q
        ? `Resultados para “${state.q}”`
        : 'Todos los repuestos'

  const crumbs: Crumb[] = [{ label: 'Inicio', to: '/' }]
  if (isCategoryRoute) crumbs.push({ label: 'Categorías', to: '/categorias' }, { label: category?.name ?? 'Categoría' })
  else crumbs.push({ label: 'Buscar' })

  // Índice de sección: en qué parte del catálogo está la persona.
  const sectionIndex = store
    ? 'Catálogo / Tienda'
    : isCategoryRoute
      ? 'Catálogo / Categoría'
      : state.q
        ? 'Catálogo / Búsqueda'
        : 'Catálogo / Todo'

  async function changeSort(sort: SearchState['sort']) {
    if (sort !== 'nearest') {
      update({ sort })
      return
    }
    // «Más cercanos» necesita la ubicación: se activa «Cerca de mí» (sin radio) y se pide con permiso.
    update({ sort, near: true })
    await nearMe.request()
  }

  const showSponsored = Boolean(page && page.sponsored.length > 0 && page.page === 1)

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${title} | Autopartco`}</title>
      <PageIntro
        crumbs={crumbs}
        label={sectionIndex}
        title={title}
        description={isCategoryRoute && category?.description ? category.description : undefined}
        className="mb-6"
      />

      {isCategoryRoute && category ? <CategoryAds key={category.id} categoryId={category.id} /> : null}

      {state.makeId ? (
        <div className="mb-6 flex flex-wrap items-center gap-x-4 gap-y-1 border-l-2 border-ap-red bg-ap-paper py-2 pr-3 pl-4">
          <p className="min-w-0 flex-1 py-1 text-sm text-ap-ink">
            <span className="text-ap-muted">Vehículo:</span> <strong className="font-semibold">{vehicleLabel ?? 'tu vehículo'}</strong>.{' '}
            <span className="text-ap-muted">Ves los repuestos publicados como compatibles; los universales aparecen siempre.</span>
          </p>
          <Button variant="link" className="px-0" onClick={() => update({ makeId: undefined, modelId: undefined, versionId: undefined })}>
            Ver para cualquier vehículo
          </Button>
        </div>
      ) : null}

      <div className="grid gap-6 lg:min-h-[calc(100svh-var(--header-h)+10rem)] lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-10">
        <aside aria-labelledby="filtros-titulo" className="hidden lg:block">
          {/*
           * Columna fija con su propio scroll (`overflow-y-auto`, no ScrollArea): el alto queda
           * acotado por `max-h` y el contenido nunca se dibuja fuera de la columna ni sobre el pie.
           */}
          <div className="sticky top-[calc(var(--header-h)+1.5rem)] flex max-h-[calc(100svh-var(--header-h)-3rem)] flex-col">
            <div className="mb-4 flex min-h-11 items-center justify-between gap-2 border-b border-ap-graphite pb-1">
              <h2 id="filtros-titulo" className="ap-index text-ap-ink">
                Filtrar resultados
              </h2>
              {activeCount > 0 ? (
                <Button variant="link" className="px-0" onClick={clearFilters}>
                  Limpiar ({activeCount})
                </Button>
              ) : null}
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain pr-3 [scrollbar-width:thin]">
              <SearchFilters {...filters} />
            </div>
          </div>
        </aside>

        <section aria-labelledby="resultados-titulo" className="flex min-w-0 flex-col gap-4">
          <h2 id="resultados-titulo" className="sr-only">
            Resultados
          </h2>
          {/* Línea de control: conteo a la izquierda; filtro (móvil) y orden a la derecha. */}
          <div className="flex min-h-11 flex-wrap items-center justify-between gap-3 border-b border-ap-graphite pb-2">
            <ResultCount page={page} loading={result.isStale} />
            <div className="flex items-center gap-2">
              <MobileFilters {...filters} activeCount={activeCount} total={page?.total} onClear={clearFilters} />
              <SortSelect value={state.sort} onChange={(sort) => void changeSort(sort)} />
            </div>
          </div>

          {state.sort === 'nearest' && !nearMe.origin ? (
            <p role="status" className="flex items-start gap-2 border-l-2 border-ap-info bg-ap-info-soft px-4 py-3 text-sm text-ap-ink">
              <InfoIcon className="mt-0.5 size-4 shrink-0 text-ap-info" aria-hidden />
              {nearMe.locating
                ? 'Obteniendo tu ubicación para ordenar por cercanía…'
                : (nearMe.error ?? 'Para ordenar por cercanía necesitamos tu ubicación. Mientras tanto, ordenamos por relevancia.')}
            </p>
          ) : null}

          {page?.fuzzy && !result.isStale ? (
            <p role="status" className="flex items-start gap-2 border-l-2 border-ap-info bg-ap-info-soft px-4 py-3 text-sm text-ap-ink">
              <InfoIcon className="mt-0.5 size-4 shrink-0 text-ap-info" aria-hidden />
              <span>
                No encontramos coincidencias exactas. Mostrando resultados parecidos a <strong className="font-semibold">“{state.q}”</strong>.
              </span>
            </p>
          ) : null}

          <InterpretedChips
            interpreted={page?.interpreted}
            onRemove={(chip) => page?.interpreted && update(dropInterpretedChip(state, page.interpreted, chip))}
          />

          <ActiveFilters
            state={state}
            options={options.data}
            facets={page?.facets}
            stores={stores.data}
            fixedCategory={isCategoryRoute}
            onChange={update}
            onClear={clearFilters}
          />

          {result.status === 'error' ? (
            <ErrorState {...errorCopy(result.error)} onRetry={result.retry} />
          ) : !page ? (
            <ProductListSkeleton count={5} />
          ) : page.items.length === 0 && page.sponsored.length === 0 && !result.isStale ? (
            <EmptyResults state={state} options={options.data} activeCount={activeCount} onClear={clearFilters} />
          ) : (
            <>
              {showSponsored ? (
                <section aria-label="Anuncios" className="flex flex-col">
                  <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line bg-ap-paper/60">
                    {page.sponsored.map((product, index) => (
                      <li key={`${product.listingKey}:${index}`} className="px-3 sm:px-4">
                        <SponsoredListing product={product} eager={index < 2} />
                      </li>
                    ))}
                  </ul>
                </section>
              ) : null}
              <ProductList busy={result.isStale}>
                {page.items.map((product, itemIndex) => (
                  <li key={product.listingKey}>
                    {product.sponsored ? (
                      <SponsoredListing product={product} eager={itemIndex < 3} />
                    ) : (
                      <ProductRow product={product} eager={itemIndex < 3} distanceKm={product.distanceKm} />
                    )}
                  </li>
                ))}
              </ProductList>
              <ResultsPagination page={page.page} totalPages={page.totalPages} />
            </>
          )}
        </section>
      </div>
    </Container>
  )
}
