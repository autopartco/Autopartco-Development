import { XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatCOP } from '@/lib/format'
import type { FilterOptions, ProductQuery, StoreSummary } from '../api/types'
import { useVehicleFilterLabel } from '../hooks'

interface Chip {
  key: string
  label: string
  patch: Partial<ProductQuery>
}

function buildChips(
  query: ProductQuery,
  options: FilterOptions | undefined,
  stores: StoreSummary[] | undefined,
  fixedCategory: boolean,
  vehicleLabel: string | undefined,
): Chip[] {
  const chips: Chip[] = []
  if (query.makeId) {
    chips.push({ key: 'vehicle', label: vehicleLabel ?? 'Vehículo', patch: { makeId: undefined, modelId: undefined, versionId: undefined } })
  }
  if (query.category && !fixedCategory) {
    const name = options?.categories.find((category) => category.slug === query.category)?.name ?? query.category
    chips.push({ key: 'category', label: name, patch: { category: undefined } })
  }
  for (const brandId of query.brands) {
    const name = options?.brands.find((brand) => brand.id === brandId)?.name ?? brandId
    chips.push({
      key: `brand-${brandId}`,
      label: name,
      patch: { brands: query.brands.filter((id) => id !== brandId) },
    })
  }
  if (query.store) {
    const name = stores?.find((store) => store.id === query.store)?.name ?? 'Tienda'
    chips.push({ key: 'store', label: name, patch: { store: undefined } })
  }
  if (query.minPrice !== undefined || query.maxPrice !== undefined) {
    const label =
      query.minPrice !== undefined && query.maxPrice !== undefined
        ? `${formatCOP(query.minPrice)} a ${formatCOP(query.maxPrice)}`
        : query.minPrice !== undefined
          ? `Desde ${formatCOP(query.minPrice)}`
          : `Hasta ${formatCOP(query.maxPrice ?? 0)}`
    chips.push({ key: 'price', label, patch: { minPrice: undefined, maxPrice: undefined } })
  }
  if (query.inStockOnly) chips.push({ key: 'stock', label: 'Con existencias', patch: { inStockOnly: false } })
  if (query.recommendedOnly) chips.push({ key: 'recommended', label: 'Tiendas recomendadas', patch: { recommendedOnly: false } })
  return chips
}

interface ActiveFiltersProps {
  query: ProductQuery
  options?: FilterOptions
  stores?: StoreSummary[]
  fixedCategory?: boolean
  onChange: (patch: Partial<ProductQuery>) => void
  onClear: () => void
}

/** Filtros aplicados como fichas removibles, con acción para limpiarlos todos. */
export function ActiveFilters({ query, options, stores, fixedCategory = false, onChange, onClear }: ActiveFiltersProps) {
  const vehicleLabel = useVehicleFilterLabel(query)
  const chips = buildChips(query, options, stores, fixedCategory, vehicleLabel)
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
