import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { InfoIcon, SlidersHorizontalIcon, XIcon } from 'lucide-react'
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
import { MarketplaceError } from '@/features/marketplace/api/types'
import { ResultsPagination } from '@/features/marketplace/components/results-pagination'
import { useFitStickyPanel } from '@/features/marketplace/components/search/use-fit-sticky-panel'
import { useNearMe } from '@/features/marketplace/components/search/use-near-me'
import { formatCOP, pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ServiceSearchResult, ServiceSort } from '../api/types'
import { useServiceCategories, useServiceSearch } from '../hooks'
import { serviceCategoryLabel } from '../lib/categories'
import { SERVICES_PATH } from '../lib/paths'
import {
  countServiceFilters,
  SERVICE_SORT_LABELS,
  toServiceQuery,
  type ServiceState,
} from '../lib/service-query'
import { ServiceCard, ServiceRowSkeleton } from './service-card'
import { ServiceFilters, type ServiceFiltersProps } from './service-filters'
import { ServiceMap } from './service-map'

const SORT_ITEMS = (Object.keys(SERVICE_SORT_LABELS) as ServiceSort[]).map((value) => ({ value, label: SERVICE_SORT_LABELS[value] }))

/** Mensaje de error según el código del servidor; el resto cae en el genérico. */
function errorCopy(error: unknown): { title: string; description: string } {
  const code = error instanceof MarketplaceError ? error.code : undefined
  if (code === 'SEARCH_RATE_LIMITED' || (error instanceof MarketplaceError && error.status === 429)) {
    return { title: 'Estás buscando muy rápido', description: 'Espera unos segundos y vuelve a intentarlo. Tus filtros se mantienen.' }
  }
  if (code === 'LOCATION_REQUIRED') {
    return {
      title: 'Necesitamos tu ubicación',
      description: 'Para buscar cerca activa «Cerca de mí» en los filtros y comparte tu ubicación, o elige tu ciudad arriba.',
    }
  }
  if (code?.startsWith('SEARCH_') || code === 'SERVICE_CATEGORY_INVALID') {
    return {
      title: 'No pudimos interpretar la búsqueda',
      description: 'Revisa los filtros, por ejemplo quita el precio o el radio, e inténtalo de nuevo.',
    }
  }
  return {
    title: 'No pudimos cargar los servicios',
    description: 'El listado no respondió. Revisa tu conexión e inténtalo de nuevo; tus filtros se mantienen.',
  }
}

interface ChipSpec {
  key: string
  label: string
  remove: () => void
}

function ActiveFilters({
  chips,
  onClear,
}: {
  chips: ChipSpec[]
  onClear: () => void
}) {
  if (chips.length === 0) return null
  return (
    <div className="flex flex-wrap items-center gap-2">
      <p className="ap-index mr-1">Filtros</p>
      <ul className="contents">
        {chips.map((chip) => (
          <li key={chip.key}>
            <button
              type="button"
              onClick={chip.remove}
              aria-label={`Quitar filtro: ${chip.label}`}
              className="inline-flex h-9 items-center gap-1.5 rounded-sm border border-ap-graphite bg-ap-paper pr-2 pl-2.5 text-sm text-ap-ink transition-colors hover:bg-ap-white pointer-coarse:h-11"
            >
              <span className="font-medium">{chip.label}</span>
              <XIcon className="size-3.5 text-ap-muted" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
      {chips.length > 1 ? (
        <Button variant="link" className="px-1" onClick={onClear}>
          Quitar todos
        </Button>
      ) : null}
    </div>
  )
}

function MobileFilters({
  activeCount,
  total,
  onClear,
  ...filters
}: ServiceFiltersProps & { activeCount: number; total: number | undefined; onClear: () => void }) {
  const [open, setOpen] = useState(false)
  return (
    <Sheet open={open} onOpenChange={setOpen}>
      <SheetTrigger
        render={<Button variant="outline" className="gap-2 px-3 lg:hidden" />}
        aria-label={
          activeCount > 0 ? `Filtrar: ${activeCount} ${activeCount === 1 ? 'filtro activo' : 'filtros activos'}` : 'Filtrar resultados'
        }
      >
        <SlidersHorizontalIcon aria-hidden />
        <span className="tabular-nums">{activeCount > 0 ? `Filtrar (${activeCount})` : 'Filtrar'}</span>
      </SheetTrigger>
      <SheetContent side="bottom" className="max-h-[90svh] gap-0 rounded-t-xl p-0">
        <SheetHeader className="shrink-0 border-b border-ap-line px-5 py-4 pr-16">
          <SheetTitle className="text-block-title">Filtrar servicios</SheetTitle>
          <SheetDescription>Los resultados se actualizan al elegir cada filtro.</SheetDescription>
        </SheetHeader>
        <div className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain px-5 py-5">
          <ServiceFilters {...filters} />
        </div>
        <SheetFooter className="mt-0 grid shrink-0 grid-cols-2 gap-2 border-t border-ap-line px-5 pt-4 pb-[max(1rem,env(safe-area-inset-bottom))]">
          <Button variant="outline" onClick={onClear} disabled={activeCount === 0}>
            Limpiar
          </Button>
          <SheetClose render={<Button />}>
            {total === undefined ? 'Ver resultados' : `Ver ${pluralize(total, 'servicio', 'servicios')}`}
          </SheetClose>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  )
}

function ServiceSortSelect({ value, onChange }: { value: ServiceSort; onChange: (value: ServiceSort) => void }) {
  return (
    <div className="flex items-center gap-2">
      <Label htmlFor="orden-servicios" className="hidden text-sm font-normal text-ap-muted sm:block">
        Orden
      </Label>
      <Select items={SORT_ITEMS} value={value} onValueChange={(next) => next && onChange(next as ServiceSort)}>
        <SelectTrigger id="orden-servicios" aria-label="Ordenar por" className="h-11 min-w-40 bg-ap-white">
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

function ViewToggle({ value, onChange }: { value: 'list' | 'map'; onChange: (value: 'list' | 'map') => void }) {
  const option = (view: 'list' | 'map', label: string) => (
    <button
      type="button"
      aria-pressed={value === view}
      onClick={() => onChange(view)}
      className={cn(
        'inline-flex min-h-11 items-center px-3 text-sm font-medium transition-colors first:rounded-l-md last:rounded-r-md',
        value === view ? 'bg-ap-graphite text-ap-white' : 'bg-ap-white text-ap-ink hover:bg-ap-paper',
      )}
    >
      {label}
    </button>
  )
  return (
    <div role="group" aria-label="Vista de resultados" className="flex divide-x divide-input overflow-hidden rounded-md border border-input">
      {option('list', 'Lista')}
      {option('map', 'Con mapa')}
    </div>
  )
}

function ResultCount({ page, loading }: { page: ServiceSearchResult | undefined; loading: boolean }) {
  return (
    <p role="status" aria-live="polite" className="text-sm text-ap-muted tabular-nums">
      {page && !loading ? (
        <>
          <strong className="font-semibold text-ap-ink">{pluralize(page.total, 'servicio', 'servicios')}</strong>
          {page.totalPages > 1 ? ` · página ${page.page} de ${page.totalPages}` : ''}
        </>
      ) : (
        'Buscando servicios…'
      )}
    </p>
  )
}

interface ServiceResultsProps {
  state: ServiceState
  update: (patch: Partial<ServiceState>, options?: { keepPage?: boolean }) => void
  /** `page`: `/servicios` (la categoría se elige en la rejilla). `search`: pestaña Servicios de `/buscar`. */
  context: 'page' | 'search'
  /** Contenido sobre la línea de control (p. ej. la nota de precios). */
  intro?: ReactNode
}

/**
 * Resultados de servicios (§7.2): filtros laterales en escritorio y hoja en móvil, línea de control con conteo, vista
 * lista o con mapa y orden, resumen de filtros activos y filas con divisores. Todo el estado vive en la URL.
 */
export function ServiceResults({ state, update, context, intro }: ServiceResultsProps) {
  const nearMe = useNearMe()
  const filtersPanelRef = useFitStickyPanel<HTMLDivElement>()
  const query = toServiceQuery(state, nearMe.origin)
  const result = useServiceSearch(query)
  const categories = useServiceCategories(state.cityCode)
  const [view, setView] = useState<'list' | 'map'>('list')
  const [activeBranch, setActiveBranch] = useState<string>()

  const page = result.data
  const hideCategory = context === 'page'
  const activeCount = countServiceFilters(state, { skipCategory: hideCategory })
  const cityName = state.cityCode ? page?.facets?.cities.find((city) => city.code === state.cityCode)?.name : undefined

  const clearFilters = () =>
    update({
      category: hideCategory ? state.category : undefined,
      cityCode: undefined,
      near: false,
      radiusKm: undefined,
      priceMax: undefined,
      onSale: false,
      bookable: false,
      openNow: false,
      sort: state.sort === 'nearest' ? 'relevance' : state.sort,
    })

  const filters: ServiceFiltersProps = {
    state,
    facets: page?.facets,
    categories: categories.data,
    nearMe,
    onChange: update,
    hideCategory,
  }

  const chips: ChipSpec[] = []
  if (!hideCategory && state.category) chips.push({ key: 'category', label: serviceCategoryLabel(state.category), remove: () => update({ category: undefined }) })
  if (state.cityCode) chips.push({ key: 'city', label: cityName ?? 'Ciudad elegida', remove: () => update({ cityCode: undefined }) })
  if (state.near) {
    chips.push({
      key: 'near',
      label: state.radiusKm ? `Cerca de mí · ${state.radiusKm} km` : 'Cerca de mí',
      remove: () => update({ near: false, radiusKm: undefined, sort: state.sort === 'nearest' ? 'relevance' : state.sort }),
    })
  }
  if (state.priceMax !== undefined) chips.push({ key: 'price', label: `Hasta ${formatCOP(state.priceMax)}`, remove: () => update({ priceMax: undefined }) })
  if (state.onSale) chips.push({ key: 'sale', label: 'En promoción', remove: () => update({ onSale: false }) })
  if (state.bookable) chips.push({ key: 'bookable', label: 'Con agenda en línea', remove: () => update({ bookable: false }) })
  if (state.openNow) chips.push({ key: 'open', label: 'Abierto ahora', remove: () => update({ openNow: false }) })

  async function changeSort(sort: ServiceSort) {
    if (sort !== 'nearest') {
      update({ sort })
      return
    }
    // «Más cercanos» necesita la ubicación: se activa «Cerca de mí» (sin radio) y se pide con permiso.
    update({ sort, near: true })
    await nearMe.request()
  }

  /** Desde el marcador del mapa: resalta el servicio de esa sede y lo acerca en la lista. */
  function selectBranch(branchId: string) {
    setActiveBranch(branchId)
    const row = document.querySelector(`[data-branch-id="${CSS.escape(branchId)}"]`)
    const reduce = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    row?.scrollIntoView({ block: 'nearest', behavior: reduce ? 'auto' : 'smooth' })
  }

  const downgraded = state.sort === 'nearest' && query.sort !== 'nearest'

  let content: ReactNode
  if (result.status === 'error') {
    content = <ErrorState {...errorCopy(result.error)} onRetry={result.retry} />
  } else if (!page) {
    content = (
      <>
        <span className="sr-only" role="status">
          Cargando servicios
        </span>
        <div className="flex flex-col divide-y divide-ap-line border-b border-ap-line">
          {Array.from({ length: 5 }, (_, index) => (
            <ServiceRowSkeleton key={index} />
          ))}
        </div>
      </>
    )
  } else if (page.items.length === 0 && !result.isStale) {
    content = (
      <EmptyState
        title={state.q ? `No encontramos servicios para “${state.q}”` : 'Ningún servicio coincide con estos filtros'}
        description={
          <ul className="mt-1 flex list-disc flex-col gap-1 pl-5">
            {state.q ? <li>Revisa la ortografía o usa menos palabras, por ejemplo “alineación”.</li> : null}
            {activeCount > 0 ? <li>Quita algunos filtros o amplía el radio para ver más talleres.</li> : null}
            <li>Si buscas una pieza, mira los repuestos publicados.</li>
          </ul>
        }
      >
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          {activeCount > 0 ? (
            <Button variant="outline" className="w-fit px-4" onClick={clearFilters}>
              Quitar todos los filtros
            </Button>
          ) : null}
          {state.category || state.q ? (
            <Link to={SERVICES_PATH} className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
              Ver todos los servicios
            </Link>
          ) : null}
        </div>
      </EmptyState>
    )
  } else {
    content = (
      <>
        {view === 'map' ? <ServiceMap items={page.items} activeBranchId={activeBranch} onSelect={selectBranch} className="aspect-[4/3] max-h-[28rem]" /> : null}
        <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', result.isStale && 'opacity-60')} aria-busy={result.isStale}>
          {page.items.map((service, index) => (
            <li key={service.id}>
              <ServiceCard service={service} layout="row" eager={index < 3} active={view === 'map' && service.branch?.id === activeBranch} />
            </li>
          ))}
        </ul>
        <ResultsPagination page={page.page} totalPages={page.totalPages} />
      </>
    )
  }

  return (
    <div className="grid gap-6 lg:min-h-[calc(100svh-var(--header-h)-1.5rem)] lg:grid-cols-[16rem_minmax(0,1fr)] lg:gap-10">
      <aside aria-labelledby="filtros-servicios-titulo" className="hidden lg:block">
        <div ref={filtersPanelRef} className="sticky top-[calc(var(--header-h)+1.5rem)] flex max-h-[calc(100svh-var(--header-h)-3rem)] flex-col">
          <div className="mb-4 flex min-h-11 items-center justify-between gap-2 border-b border-ap-graphite pb-1">
            <h2 id="filtros-servicios-titulo" className="ap-index text-ap-ink">
              Filtrar servicios
            </h2>
            {activeCount > 0 ? (
              <Button variant="link" className="px-0" onClick={clearFilters}>
                Limpiar ({activeCount})
              </Button>
            ) : null}
          </div>
          <div className="relative min-h-0 flex-1 overflow-y-auto overscroll-contain pr-3 [scrollbar-width:thin]">
            <ServiceFilters {...filters} />
          </div>
        </div>
      </aside>

      <section aria-labelledby="resultados-servicios-titulo" className="flex min-w-0 flex-col gap-4">
        <h2 id="resultados-servicios-titulo" className="sr-only">
          Servicios
        </h2>
        {intro}
        <div className="flex min-h-11 flex-wrap items-center justify-between gap-3 border-b border-ap-graphite pb-2">
          <ResultCount page={page} loading={result.isStale} />
          <div className="flex flex-wrap items-center gap-2">
            <ViewToggle value={view} onChange={setView} />
            <MobileFilters {...filters} activeCount={activeCount} total={page?.total} onClear={clearFilters} />
            <ServiceSortSelect value={state.sort} onChange={(sort) => void changeSort(sort)} />
          </div>
        </div>

        {state.sort === 'nearest' && (downgraded || (state.near && !nearMe.origin && nearMe.locating)) ? (
          <p role="status" className="flex items-start gap-2 border-l-2 border-ap-info bg-ap-info-soft px-4 py-3 text-sm text-ap-ink">
            <InfoIcon className="mt-0.5 size-4 shrink-0 text-ap-info" aria-hidden />
            {nearMe.locating
              ? 'Obteniendo tu ubicación para ordenar por cercanía…'
              : (nearMe.error ?? 'Para ordenar por cercanía elige tu ciudad arriba o comparte tu ubicación. Mientras tanto, ordenamos por relevancia.')}
          </p>
        ) : null}

        <ActiveFilters chips={chips} onClear={clearFilters} />
        {content}
      </section>
    </div>
  )
}
