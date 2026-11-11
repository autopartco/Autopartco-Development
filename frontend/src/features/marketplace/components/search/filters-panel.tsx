import { useId, type FormEvent, type ReactNode } from 'react'
import { OptionSelect } from '@/components/common/option-select'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { formatCOP, formatNumber } from '@/lib/format'
import type { FilterOptions, SearchFacets } from '../../api/types'
import type { SearchState } from '../../lib/search-query'
import { VehiclePicker } from '../vehicle-picker'
import { MultiFacetList, SingleFacetList, type FacetItem } from './facet-list'
import { NearMeFilter } from './radius-filter'
import type { NearMe } from './use-near-me'

export interface SearchFiltersProps {
  state: SearchState
  /** Conteos del servidor para la consulta actual; sin ellos se listan las opciones del catálogo sin conteo. */
  facets: SearchFacets | undefined
  options: FilterOptions | undefined
  nearMe: NearMe
  onChange: (patch: Partial<SearchState>) => void
  /** La categoría viene de la ruta y no se puede cambiar desde aquí. */
  fixedCategory?: boolean
}

function FilterSection({ title, children }: { title: string; children: ReactNode }) {
  const id = useId()
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3 border-b border-ap-line py-5 first:pt-0 last:border-b-0">
      <h3 id={id} className="text-sm font-semibold text-ap-ink">
        {title}
      </h3>
      {children}
    </section>
  )
}

function CheckRow({
  checked,
  onCheckedChange,
  count,
  children,
}: {
  checked: boolean
  onCheckedChange: (checked: boolean) => void
  count?: number
  children: ReactNode
}) {
  return (
    <label className="flex min-h-9 cursor-pointer items-center gap-3 rounded-md px-1 text-sm hover:bg-ap-paper pointer-coarse:min-h-11">
      <Checkbox checked={checked} onCheckedChange={(value) => onCheckedChange(value === true)} />
      <span className="min-w-0 flex-1">{children}</span>
      {count !== undefined ? (
        <span className="pl-2 text-xs text-ap-muted tabular-nums">
          <span className="sr-only">, </span>
          {formatNumber(count)}
          <span className="sr-only"> resultados</span>
        </span>
      ) : null}
    </label>
  )
}

function PriceFilter({ state, range, onChange }: { state: SearchState; range: SearchFacets['price']; onChange: SearchFiltersProps['onChange'] }) {
  const id = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const read = (name: string) => {
      const digits = String(data.get(name) ?? '').replace(/\D/g, '')
      return digits ? Number(digits) : undefined
    }
    let minPrice = read('min')
    let maxPrice = read('max')
    if (minPrice !== undefined && maxPrice !== undefined && minPrice > maxPrice) {
      ;[minPrice, maxPrice] = [maxPrice, minPrice]
    }
    onChange({ minPrice, maxPrice })
  }

  return (
    <form
      key={`${state.minPrice ?? ''}-${state.maxPrice ?? ''}`}
      onSubmit={handleSubmit}
      className="flex flex-col gap-3"
      aria-describedby={range ? `${id}-hint` : undefined}
    >
      <div className="grid grid-cols-2 gap-2">
        <div className="flex min-w-0 flex-col gap-1.5">
          <label htmlFor={`${id}-min`} className="text-xs text-ap-muted">
            Desde (COP)
          </label>
          <Input
            id={`${id}-min`}
            name="min"
            inputMode="numeric"
            placeholder="0"
            defaultValue={state.minPrice ?? ''}
            className="h-10 tabular-nums pointer-coarse:h-11"
          />
        </div>
        <div className="flex min-w-0 flex-col gap-1.5">
          <label htmlFor={`${id}-max`} className="text-xs text-ap-muted">
            Hasta (COP)
          </label>
          <Input
            id={`${id}-max`}
            name="max"
            inputMode="numeric"
            placeholder="Sin tope"
            defaultValue={state.maxPrice ?? ''}
            className="h-10 tabular-nums pointer-coarse:h-11"
          />
        </div>
      </div>
      {range ? (
        <p id={`${id}-hint`} className="text-xs text-ap-muted tabular-nums">
          Precios entre {formatCOP(range.min)} y {formatCOP(range.max)}
        </p>
      ) : null}
      <Button type="submit" variant="outline" className="h-10 pointer-coarse:h-11">
        Aplicar precio
      </Button>
    </form>
  )
}

const MAX_YEAR = new Date().getFullYear() + 1
const MIN_YEAR = 1950

function YearFilter({ year, onChange }: { year: number | undefined; onChange: SearchFiltersProps['onChange'] }) {
  const id = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const digits = String(new FormData(event.currentTarget).get('year') ?? '').replace(/\D/g, '')
    const value = digits ? Number(digits) : undefined
    onChange({ year: value !== undefined && value >= MIN_YEAR && value <= MAX_YEAR ? value : undefined })
  }

  return (
    <form key={year ?? 'none'} onSubmit={handleSubmit} className="flex items-end gap-2">
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <label htmlFor={id} className="text-sm font-medium">
          Año del vehículo
        </label>
        <Input
          id={id}
          name="year"
          inputMode="numeric"
          maxLength={4}
          placeholder="Ej. 2015"
          defaultValue={year ?? ''}
          className="h-10 tabular-nums pointer-coarse:h-11"
        />
      </div>
      <Button type="submit" variant="outline" className="h-10 pointer-coarse:h-11">
        Aplicar
      </Button>
    </form>
  )
}

const RATING_OPTIONS = [
  { value: '4', label: '4 estrellas o más' },
  { value: '3', label: '3 estrellas o más' },
  { value: '2', label: '2 estrellas o más' },
]

/** Panel de filtros del motor v2: facetas con conteo, cercanía y radio, vehículo, precio y disponibilidad. */
export function SearchFilters({ state, facets, options, nearMe, onChange, fixedCategory }: SearchFiltersProps) {
  const ratingId = useId()

  const categories: FacetItem[] =
    facets?.categories ?? options?.categories.map((category) => ({ id: category.id, name: category.name })) ?? []
  const manufacturers: FacetItem[] =
    facets?.manufacturers ?? options?.brands.map((brand) => ({ id: brand.id, name: brand.name })) ?? []
  const cities: FacetItem[] = facets?.cities.map((city) => ({ id: city.code, name: city.name, count: city.count })) ?? []
  // La categoría puede venir como slug (enlaces antiguos): se busca la opción por id o slug.
  const selectedCategory = options?.categories.find((item) => item.id === state.category || item.slug === state.category)?.id ?? state.category

  return (
    <div className="flex flex-col">
      <FilterSection title="Cerca de ti">
        <NearMeFilter state={state} nearMe={nearMe} onChange={onChange} />
      </FilterSection>

      <FilterSection title="Tu vehículo">
        <VehiclePicker
          value={{ makeId: state.makeId, modelId: state.modelId, versionId: state.versionId }}
          onChange={(vehicle) => onChange(vehicle)}
        />
        <YearFilter year={state.year} onChange={onChange} />
        <CheckRow checked={state.universal} onCheckedChange={(universal) => onChange({ universal })}>
          Solo repuestos universales
        </CheckRow>
      </FilterSection>

      {!fixedCategory && categories.length > 0 ? (
        <FilterSection title="Categoría">
          <SingleFacetList
            label="Categoría"
            allLabel="Todas las categorías"
            items={categories}
            selected={selectedCategory}
            onChange={(category) => onChange({ category })}
          />
        </FilterSection>
      ) : null}

      {manufacturers.length > 0 ? (
        <FilterSection title="Fabricante">
          <MultiFacetList
            label="Fabricante"
            items={manufacturers}
            selected={state.manufacturers}
            onChange={(next) => onChange({ manufacturers: next })}
          />
        </FilterSection>
      ) : null}

      <FilterSection title="Precio">
        <PriceFilter state={state} range={facets?.price} onChange={onChange} />
      </FilterSection>

      {cities.length > 0 || state.cityCode ? (
        <FilterSection title="Ciudad">
          <SingleFacetList
            label="Ciudad"
            allLabel="Todas las ciudades"
            items={cities}
            selected={state.cityCode}
            onChange={(cityCode) => onChange({ cityCode })}
          />
        </FilterSection>
      ) : null}

      <FilterSection title="Disponibilidad y tienda">
        <div className="flex flex-col">
          <CheckRow checked={state.inStock} onCheckedChange={(inStock) => onChange({ inStock })} count={facets?.inStock}>
            Solo con existencias
          </CheckRow>
          <CheckRow checked={state.onSale} onCheckedChange={(onSale) => onChange({ onSale })} count={facets?.onSale}>
            En oferta
          </CheckRow>
        </div>
        <OptionSelect
          id={ratingId}
          label="Calificación de la tienda"
          emptyLabel="Cualquiera"
          value={state.minRating ? String(state.minRating) : ''}
          options={RATING_OPTIONS}
          onValueChange={(value) => onChange({ minRating: value ? Number(value) : undefined })}
        />
      </FilterSection>
    </div>
  )
}
