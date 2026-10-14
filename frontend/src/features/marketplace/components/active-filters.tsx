import { AnimatePresence, m } from 'motion/react'
import { XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatCOP } from '@/lib/format'
import type { FilterOptions, ProductQuery, StoreSummary } from '../api/types'

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
): Chip[] {
  const chips: Chip[] = []
  if (query.make) {
    const vehicle = [query.make, query.model, query.year].filter(Boolean).join(' ')
    chips.push({ key: 'vehicle', label: vehicle, patch: { make: undefined, model: undefined, year: undefined } })
  } else if (query.year) {
    chips.push({ key: 'year', label: `Año ${query.year}`, patch: { year: undefined } })
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
  const chips = buildChips(query, options, stores, fixedCategory)
  if (chips.length === 0) return null
  return (
    <div className="flex flex-wrap items-center gap-2">
      <span className="sr-only">Filtros aplicados:</span>
      <ul className="contents">
        <AnimatePresence initial={false}>
          {chips.map((chip) => (
            <m.li
              key={chip.key}
              initial={{ opacity: 0, scale: 0.95 }}
              animate={{ opacity: 1, scale: 1 }}
              exit={{ opacity: 0, scale: 0.95, transition: { duration: 0.1 } }}
              transition={{ duration: 0.16, ease: [0.23, 1, 0.32, 1] }}
            >
              <button
                type="button"
                onClick={() => onChange(chip.patch)}
                className="inline-flex h-9 items-center gap-1.5 rounded-full border border-border bg-card pr-2.5 pl-3.5 text-sm pointer-coarse:h-11 transition-colors hover:border-foreground/40 active:scale-[0.97]"
              >
                {chip.label}
                <XIcon className="size-3.5 text-muted-foreground" aria-hidden />
                <span className="sr-only">(quitar filtro)</span>
              </button>
            </m.li>
          ))}
        </AnimatePresence>
      </ul>
      <Button variant="link" className="h-9 px-1 pointer-coarse:h-11" onClick={onClear}>
        Limpiar filtros
      </Button>
    </div>
  )
}
