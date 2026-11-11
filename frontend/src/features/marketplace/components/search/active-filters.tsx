import { XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatCOP } from '@/lib/format'
import type { FilterOptions, SearchFacets, StoreSummary } from '../../api/types'
import { useVehicleFilterLabel } from '../../hooks'
import type { SearchState } from '../../lib/search-query'

interface Chip {
  key: string
  label: string
  patch: Partial<SearchState>
}

interface BuildContext {
  options: FilterOptions | undefined
  facets: SearchFacets | undefined
  stores: StoreSummary[] | undefined
  fixedCategory: boolean
  vehicleLabel: string | undefined
}

function buildChips(state: SearchState, { options, facets, stores, fixedCategory, vehicleLabel }: BuildContext): Chip[] {
  const chips: Chip[] = []
  if (state.makeId) {
    chips.push({
      key: 'vehicle',
      label: vehicleLabel ?? 'Vehículo',
      patch: { makeId: undefined, modelId: undefined, versionId: undefined },
    })
  }
  if (state.year !== undefined) chips.push({ key: 'year', label: `Año ${state.year}`, patch: { year: undefined } })
  if (state.category && !fixedCategory) {
    const name =
      facets?.categories.find((item) => item.id === state.category)?.name ??
      options?.categories.find((item) => item.id === state.category || item.slug === state.category)?.name ??
      'Categoría'
    chips.push({ key: 'category', label: name, patch: { category: undefined } })
  }
  for (const id of state.manufacturers) {
    const name =
      facets?.manufacturers.find((item) => item.id === id)?.name ?? options?.brands.find((item) => item.id === id)?.name ?? 'Fabricante'
    chips.push({ key: `maker-${id}`, label: name, patch: { manufacturers: state.manufacturers.filter((value) => value !== id) } })
  }
  if (state.store) {
    chips.push({ key: 'store', label: stores?.find((item) => item.id === state.store)?.name ?? 'Tienda', patch: { store: undefined } })
  }
  if (state.cityCode) {
    chips.push({
      key: 'city',
      label: facets?.cities.find((item) => item.code === state.cityCode)?.name ?? 'Ciudad',
      patch: { cityCode: undefined },
    })
  }
  if (state.near) {
    chips.push({
      key: 'near',
      label: state.radiusKm ? `A ${state.radiusKm} km de ti` : 'Cerca de ti',
      patch: { near: false, radiusKm: undefined, sort: state.sort === 'nearest' ? 'relevance' : state.sort },
    })
  }
  if (state.minPrice !== undefined || state.maxPrice !== undefined) {
    const label =
      state.minPrice !== undefined && state.maxPrice !== undefined
        ? `${formatCOP(state.minPrice)} a ${formatCOP(state.maxPrice)}`
        : state.minPrice !== undefined
          ? `Desde ${formatCOP(state.minPrice)}`
          : `Hasta ${formatCOP(state.maxPrice ?? 0)}`
    chips.push({ key: 'price', label, patch: { minPrice: undefined, maxPrice: undefined } })
  }
  if (state.inStock) chips.push({ key: 'stock', label: 'Con existencias', patch: { inStock: false } })
  if (state.onSale) chips.push({ key: 'sale', label: 'En oferta', patch: { onSale: false } })
  if (state.minRating !== undefined) {
    chips.push({ key: 'rating', label: `Tienda ${state.minRating}★ o más`, patch: { minRating: undefined } })
  }
  if (state.universal) chips.push({ key: 'universal', label: 'Universales', patch: { universal: false } })
  return chips
}

interface ActiveFiltersProps {
  state: SearchState
  options?: FilterOptions
  facets?: SearchFacets
  stores?: StoreSummary[]
  fixedCategory?: boolean
  onChange: (patch: Partial<SearchState>) => void
  onClear: () => void
}

/** Filtros aplicados como fichas removibles, con acción para limpiarlos todos. */
export function ActiveFilters({ state, options, facets, stores, fixedCategory = false, onChange, onClear }: ActiveFiltersProps) {
  const vehicleLabel = useVehicleFilterLabel(state)
  const chips = buildChips(state, { options, facets, stores, fixedCategory, vehicleLabel })
  if (chips.length === 0) return null
  return (
    <div className="flex flex-wrap items-center gap-2">
      <p className="ap-index mr-1">Filtros activos ({chips.length})</p>
      <ul className="contents">
        {chips.map((chip) => (
          <li key={chip.key}>
            <button
              type="button"
              onClick={() => onChange(chip.patch)}
              aria-label={`Quitar filtro: ${chip.label}`}
              className="inline-flex h-9 items-center gap-1.5 rounded-sm border border-ap-line-strong bg-ap-white pr-2 pl-2.5 text-sm text-ap-ink transition-colors hover:border-ap-graphite hover:bg-ap-paper pointer-coarse:h-11"
            >
              {chip.label}
              <XIcon className="size-3.5 text-ap-muted" aria-hidden />
            </button>
          </li>
        ))}
      </ul>
      <Button variant="link" className="h-9 px-1 pointer-coarse:h-11" onClick={onClear}>
        Limpiar filtros
      </Button>
    </div>
  )
}
