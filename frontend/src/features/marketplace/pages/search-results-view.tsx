import { useState } from 'react'
import { Link } from 'react-router'
import { CarFrontIcon, InfoIcon, SearchXIcon, SlidersHorizontalIcon } from 'lucide-react'
import { Container, PageIntro, type Crumb } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
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
import { Skeleton } from '@/components/ui/skeleton'
import { pluralize } from '@/lib/format'
import type {
  FilterOptions,
  MarketplaceCapabilities,
  ProductPage,
  ProductQuery,
  SortOption,
} from '../api/types'
import { ActiveFilters } from '../components/active-filters'
import { FiltersPanel } from '../components/filters-panel'
import { ProductCard, ProductGrid, ProductGridSkeleton } from '../components/product-card'
import { ResultsPagination } from '../components/results-pagination'
import { useCapabilities, useFilterOptions, useProductQueryState, useProductSearch, useStores, useVehicleFilterLabel } from '../hooks'
import { categoryPath } from '../lib/paths'
import { countActiveFilters, SORT_LABELS } from '../lib/product-query'
import { useStoreDistances } from '@/features/location/hooks'
import { NotFoundContent } from './not-found-page'

const CLEARED_FILTERS: Partial<ProductQuery> = {
  category: undefined,
  brands: [],
  store: undefined,
  makeId: undefined,
  modelId: undefined,
  versionId: undefined,
  minPrice: undefined,
  maxPrice: undefined,
  inStockOnly: false,
  recommendedOnly: false,
}

const SORT_ITEMS = (Object.keys(SORT_LABELS) as SortOption[]).map((value) => ({ value, label: SORT_LABELS[value] }))

function SortSelect({ value, onChange }: { value: SortOption; onChange: (value: SortOption) => void }) {
  return (
    <div className="flex items-center gap-2">
      <Label htmlFor="orden-resultados" className="hidden text-sm text-muted-foreground sm:block">
        Ordenar por
      </Label>
      <Select items={SORT_ITEMS} value={value} onValueChange={(next) => next && onChange(next as SortOption)}>
        <SelectTrigger id="orden-resultados" aria-label="Ordenar por" className="h-11 min-w-40 bg-card lg:h-10">
          <SelectValue />
        </SelectTrigger>
        <SelectContent align="end">
          {SORT_ITEMS.map((item) => (
            <SelectItem key={item.value} value={item.value}>
              {item.label}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>
    </div>
  )
}

function FiltersSkeleton() {
  return (
    <div className="flex flex-col gap-3" aria-hidden>
      <Skeleton className="h-4 w-24" />
      <Skeleton className="h-10 w-full" />
      <Skeleton className="h-10 w-full" />
      <Skeleton className="mt-4 h-4 w-28" />
      {Array.from({ length: 5 }, (_, index) => (
        <Skeleton key={index} className="h-6 w-3/4" />
      ))}
    </div>
  )
}

interface FiltersSlotProps {
  query: ProductQuery
  capabilities: MarketplaceCapabilities | undefined
  options: ReturnType<typeof useFilterOptions>
  onChange: (patch: Partial<ProductQuery>) => void
  fixedCategory: boolean
}

function FiltersSlot({ query, capabilities, options, onChange, fixedCategory }: FiltersSlotProps) {
  if (options.status === 'error') {
    return <ErrorState title="No pudimos cargar los filtros" description="Puedes seguir buscando por texto." onRetry={options.retry} />
  }
  if (!options.data || !capabilities) return <FiltersSkeleton />
  return (
    <FiltersPanel
      query={query}
      options={options.data}
      capabilities={capabilities}
      onChange={onChange}
      fixedCategory={fixedCategory}
    />
  )
}

function MobileFilters({
  activeCount,
  total,
  onClear,
  ...slot
}: FiltersSlotProps & { activeCount: number; total: number | undefined; onClear: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger render={<Button variant="outline" className="h-11 gap-2 px-3 lg:hidden" />}>
        <SlidersHorizontalIcon aria-hidden />
        Filtros
        {activeCount > 0 ? (
          <span className="grid min-w-5 place-items-center rounded-full bg-brand px-1.5 text-xs font-semibold text-primary-foreground tabular-nums">
            <span className="sr-only">(</span>
            {activeCount}
            <span className="sr-only"> activos)</span>
          </span>
        ) : null}
      </SheetTrigger>
      {/* Cabecera y pie fijos; solo la lista de filtros hace scroll, así las acciones siempre se ven. */}
      <SheetContent side="right" className="gap-0 p-0 data-[side=right]:w-full">
        <SheetHeader className="shrink-0 border-b border-border px-5 py-4 pr-16">
          <SheetTitle className="text-block-title">Filtros</SheetTitle>
          <SheetDescription>Los resultados se actualizan al elegir cada filtro.</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-5">
          <FiltersSlot {...slot} />
        </div>
        <SheetFooter className="mt-0 grid shrink-0 grid-cols-2 gap-2 border-t border-border px-5 pt-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button variant="outline" className="h-11" onClick={onClear} disabled={activeCount === 0}>
            Limpiar
          </Button>
          <SheetClose render={<Button className="h-11" />}>
            {total === undefined ? 'Ver resultados' : `Ver ${pluralize(total, 'repuesto', 'repuestos')}`}
          </SheetClose>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function EmptyResults({
  query,
  options,
  activeCount,
  onClear,
}: {
  query: ProductQuery
  options: FilterOptions | undefined
  activeCount: number
  onClear: () => void
}) {
  return (
    <EmptyState
      icon={<SearchXIcon />}
      title={query.q ? `No encontramos repuestos para “${query.q}”` : 'Ningún repuesto coincide con estos filtros'}
      description={
        <ul className="mt-1 flex list-disc flex-col gap-1 pl-5">
          {query.q ? <li>Revisa la ortografía o usa menos palabras, por ejemplo “pastillas”.</li> : null}
          <li>Busca por la referencia o número de parte que aparece en la pieza.</li>
          {activeCount > 0 ? <li>Quita algunos filtros para ver más resultados.</li> : null}
        </ul>
      }
    >
      <div className="flex flex-col gap-4">
        {activeCount > 0 ? (
          <Button variant="outline" className="h-10 w-fit px-4" onClick={onClear}>
            Quitar todos los filtros
          </Button>
        ) : null}
        {options && options.categories.length > 0 ? (
          <div className="flex flex-col gap-2">
            <p className="text-sm font-medium">O explora por categoría</p>
            <ul className="flex flex-wrap gap-2">
              {options.categories.slice(0, 8).map((category) => (
                <li key={category.id}>
                  <Link
                    to={categoryPath(category.slug)}
                    className="inline-flex h-10 items-center rounded-full border border-border bg-card px-3.5 text-sm transition-colors hover:border-foreground/40 pointer-coarse:h-11"
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

function ResultCount({ page, loading, q }: { page: ProductPage | undefined; loading: boolean; q: string }) {
  let message = 'Buscando repuestos…'
  if (page && !loading) {
    const count = pluralize(page.total, 'repuesto', 'repuestos')
    const range =
      page.totalPages > 1
        ? ` (página ${page.page} de ${page.totalPages})`
        : ''
    message = `${count}${q ? ` para “${q}”` : ''}${range}`
  }
  return (
    <p role="status" aria-live="polite" className="text-sm text-muted-foreground tabular-nums">
      {message}
    </p>
  )
}

export function SearchResultsView({ fixedCategory }: { fixedCategory?: string }) {
  const [query, update] = useProductQueryState(fixedCategory)
  const capabilities = useCapabilities()
  const options = useFilterOptions()
  const stores = useStores()
  const vehicleLabel = useVehicleFilterLabel(query)
  const result = useProductSearch(query)
  const distances = useStoreDistances()

  const isCategoryRoute = Boolean(fixedCategory)
  const activeCount = countActiveFilters(query, { fixedCategory: isCategoryRoute })
  const category = options.data?.categories.find((item) => item.slug === query.category)
  const store = query.store ? stores.data?.find((item) => item.id === query.store) : undefined
  const clearFilters = () => update(CLEARED_FILTERS)

  if (isCategoryRoute && options.data && capabilities?.filters.has('category') && !category) {
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
      : query.q
        ? `Resultados para “${query.q}”`
        : 'Todos los repuestos'

  const crumbs: Crumb[] = [{ label: 'Inicio', to: '/' }]
  if (isCategoryRoute) crumbs.push({ label: 'Categorías', to: '/categorias' }, { label: category?.name ?? 'Categoría' })
  else crumbs.push({ label: 'Buscar' })

  const slot: FiltersSlotProps = {
    query,
    capabilities,
    options,
    onChange: update,
    fixedCategory: isCategoryRoute,
  }
  const page = result.data
  const showTextSearchNotice = Boolean(query.q) && capabilities && !capabilities.textSearch

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${title} | Autopartco`}</title>
      <PageIntro
        crumbs={crumbs}
        label={store ? 'Catálogo de la tienda' : isCategoryRoute ? 'Categoría' : query.q ? 'Búsqueda' : 'Catálogo'}
        title={title}
        description={isCategoryRoute && category?.description ? category.description : undefined}
        className="mb-6 sm:mb-8"
      />

      {query.makeId ? (
        <div className="mb-6 flex flex-wrap items-center gap-x-3 gap-y-1 rounded-lg border-l-4 border-brand bg-concrete px-4 py-3">
          <CarFrontIcon className="size-5 text-brand" aria-hidden />
          <p className="min-w-0 flex-1 text-sm">
            Mostrando repuestos publicados como compatibles con{' '}
            <strong className="font-semibold">{vehicleLabel ?? 'tu vehículo'}</strong>.{' '}
            <span className="text-muted-foreground">Los repuestos universales aparecen siempre.</span>
          </p>
          <Button
            variant="link"
            className="h-10 px-0"
            onClick={() => update({ makeId: undefined, modelId: undefined, versionId: undefined })}
          >
            Ver para cualquier vehículo
          </Button>
        </div>
      ) : null}

      <div className="grid gap-8 lg:grid-cols-[16.5rem_minmax(0,1fr)] lg:gap-10">
        <aside aria-labelledby="filtros-titulo" className="hidden lg:block">
          {/*
           * Columna fija con su propio scroll. Es un `div` con `overflow-y-auto` (no
           * ScrollArea): el alto queda acotado por `max-h` y el contenido nunca se
           * dibuja fuera de la columna, ni encima del pie al final de la página.
           */}
          <div className="sticky top-[calc(var(--header-h)+1.5rem)] flex max-h-[calc(100svh-var(--header-h)-3rem)] flex-col">
            <div className="mb-3 flex min-h-10 items-center justify-between gap-2 border-b border-border pb-3">
              <h2 id="filtros-titulo" className="text-block-title">
                Filtros
              </h2>
              {activeCount > 0 ? (
                <Button variant="link" className="h-10 px-0" onClick={clearFilters}>
                  Limpiar ({activeCount})
                </Button>
              ) : null}
            </div>
            <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain pt-2 pr-3 [scrollbar-width:thin]">
              <FiltersSlot {...slot} />
            </div>
          </div>
        </aside>

        <section aria-labelledby="resultados-titulo" className="flex min-w-0 flex-col gap-5">
          <h2 id="resultados-titulo" className="sr-only">
            Resultados
          </h2>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <ResultCount page={page} loading={result.isStale} q={query.q} />
            <div className="flex items-center gap-2">
              <MobileFilters {...slot} activeCount={activeCount} total={page?.total} onClear={clearFilters} />
              {capabilities?.sorting ? <SortSelect value={query.sort} onChange={(sort) => update({ sort })} /> : null}
            </div>
          </div>

          {showTextSearchNotice ? (
            <p className="flex items-start gap-2 rounded-lg bg-concrete px-4 py-3 text-sm">
              <InfoIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
              La búsqueda por texto aún no está disponible. Te mostramos todo el catálogo publicado.
            </p>
          ) : null}

          <ActiveFilters
            query={query}
            options={options.data}
            stores={stores.data}
            fixedCategory={isCategoryRoute}
            onChange={update}
            onClear={clearFilters}
          />

          {result.status === 'error' ? (
            <ErrorState
              title="No pudimos cargar los repuestos"
              description="El catálogo no respondió. Revisa tu conexión e inténtalo de nuevo; tus filtros se mantienen."
              onRetry={result.retry}
            />
          ) : !page ? (
            <ProductGridSkeleton count={6} />
          ) : page.items.length === 0 && !result.isStale ? (
            <EmptyResults query={query} options={options.data} activeCount={activeCount} onClear={clearFilters} />
          ) : (
            <>
              <ProductGrid busy={result.isStale}>
                {page.items.map((product, index) => (
                  <ProductCard
                    key={product.listingKey}
                    product={product}
                    eager={index < 3}
                    distanceKm={product.store ? distances.get(product.store.id) : undefined}
                  />
                ))}
              </ProductGrid>
              {capabilities?.pagination ? <ResultsPagination page={page.page} totalPages={page.totalPages} /> : null}
            </>
          )}
        </section>
      </div>
    </Container>
  )
}
