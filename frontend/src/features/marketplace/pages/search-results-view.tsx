import { useState } from 'react'
import { Link } from 'react-router'
import { InfoIcon, SlidersHorizontalIcon } from 'lucide-react'
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
import { ProductList, ProductListSkeleton, ProductRow } from '../components/product-card'
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
      <Label htmlFor="orden-resultados" className="hidden text-sm font-normal text-ap-muted sm:block">
        Orden
      </Label>
      <Select items={SORT_ITEMS} value={value} onValueChange={(next) => next && onChange(next as SortOption)}>
        <SelectTrigger id="orden-resultados" aria-label="Ordenar por" className="h-11 min-w-40 bg-ap-white">
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

/** Filtros en móvil y tablet: botón "Filtrar (n)" que abre un panel lateral accesible. */
function MobileFilters({
  activeCount,
  total,
  onClear,
  ...slot
}: FiltersSlotProps & { activeCount: number; total: number | undefined; onClear: () => void }) {
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
      <SheetContent side="right" className="gap-0 p-0 data-[side=right]:w-full">
        <SheetHeader className="shrink-0 border-b border-ap-line px-5 py-4 pr-16">
          <SheetTitle className="text-block-title">Filtrar resultados</SheetTitle>
          <SheetDescription>Los resultados se actualizan al elegir cada filtro.</SheetDescription>
        </SheetHeader>
        <div className="min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-5">
          <FiltersSlot {...slot} />
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

function ResultCount({ page, loading }: { page: ProductPage | undefined; loading: boolean }) {
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
 * Resultados de catálogo (§7.2, §15): cabecera con la consulta visible, línea de control
 * con conteo y orden, filtros laterales en escritorio (panel en móvil) y resultados en
 * filas con divisores.
 */
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

  // Índice de sección: en qué parte del catálogo está la persona.
  const sectionIndex = store
    ? 'Catálogo / Tienda'
    : isCategoryRoute
      ? 'Catálogo / Categoría'
      : query.q
        ? 'Catálogo / Búsqueda'
        : 'Catálogo / Todo'

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
        label={sectionIndex}
        title={title}
        description={isCategoryRoute && category?.description ? category.description : undefined}
        className="mb-6"
      />

      {query.makeId ? (
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

      <div className="grid gap-6 lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-10">
        <aside aria-labelledby="filtros-titulo" className="hidden lg:block">
          {/*
           * Columna fija con su propio scroll. Es un `div` con `overflow-y-auto` (no
           * ScrollArea): el alto queda acotado por `max-h` y el contenido nunca se
           * dibuja fuera de la columna, ni encima del pie al final de la página.
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
              <FiltersSlot {...slot} />
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
              <MobileFilters {...slot} activeCount={activeCount} total={page?.total} onClear={clearFilters} />
              {capabilities?.sorting ? <SortSelect value={query.sort} onChange={(sort) => update({ sort })} /> : null}
            </div>
          </div>

          {showTextSearchNotice ? (
            <p className="flex items-start gap-2 border-l-2 border-ap-info bg-ap-info-soft px-4 py-3 text-sm text-ap-ink">
              <InfoIcon className="mt-0.5 size-4 shrink-0 text-ap-info" aria-hidden />
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
            <ProductListSkeleton count={5} />
          ) : page.items.length === 0 && !result.isStale ? (
            <EmptyResults query={query} options={options.data} activeCount={activeCount} onClear={clearFilters} />
          ) : (
            <>
              <ProductList busy={result.isStale}>
                {page.items.map((product, itemIndex) => (
                  <li key={product.listingKey}>
                    <ProductRow
                      product={product}
                      eager={itemIndex < 3}
                      distanceKm={product.store ? distances.get(product.store.id) : undefined}
                    />
                  </li>
                ))}
              </ProductList>
              {capabilities?.pagination ? <ResultsPagination page={page.page} totalPages={page.totalPages} /> : null}
            </>
          )}
        </section>
      </div>
    </Container>
  )
}
