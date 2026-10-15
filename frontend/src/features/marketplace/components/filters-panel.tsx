import { useId, type FormEvent, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group'
import { formatCOP } from '@/lib/format'
import type { FilterOptions, MarketplaceCapabilities, ProductQuery } from '../api/types'
import { RECOMMENDED_EXPLANATION } from './product-meta'
import { VehiclePicker } from './vehicle-picker'

export interface FiltersPanelProps {
  query: ProductQuery
  options: FilterOptions
  capabilities: MarketplaceCapabilities
  onChange: (patch: Partial<ProductQuery>) => void
  /** La categoría viene de la ruta y no se puede cambiar desde aquí. */
  fixedCategory?: boolean
}

function FilterSection({ title, children }: { title: string; children: ReactNode }) {
  const id = useId()
  return (
    <section aria-labelledby={id} className="flex flex-col gap-3 border-b border-border py-5 first:pt-0 last:border-b-0">
      <h3 id={id} className="label-mono text-muted-foreground">
        {title}
      </h3>
      {children}
    </section>
  )
}

function CheckRow({
  checked,
  onCheckedChange,
  children,
  description,
}: {
  checked: boolean
  onCheckedChange: (checked: boolean) => void
  children: ReactNode
  /** Explicación breve bajo la opción, enlazada con `aria-describedby`. */
  description?: string
}) {
  const id = useId()
  if (description) {
    return (
      <div className="flex flex-col">
        <label className="flex min-h-9 cursor-pointer items-center gap-3 rounded-md px-1 text-sm hover:bg-concrete pointer-coarse:min-h-11">
          <Checkbox
            checked={checked}
            onCheckedChange={(value) => onCheckedChange(value === true)}
            aria-describedby={`${id}-description`}
          />
          <span className="flex-1">{children}</span>
        </label>
        <p id={`${id}-description`} className="pb-1 pl-8 text-xs text-muted-foreground">
          {description}
        </p>
      </div>
    )
  }
  return (
    <label className="flex min-h-9 cursor-pointer items-center gap-3 rounded-md px-1 text-sm hover:bg-concrete pointer-coarse:min-h-11">
      <Checkbox checked={checked} onCheckedChange={(value) => onCheckedChange(value === true)} />
      <span className="flex-1">{children}</span>
    </label>
  )
}

function PriceFilter({ query, options, onChange }: Pick<FiltersPanelProps, 'query' | 'options' | 'onChange'>) {
  const id = useId()
  const range = options.priceRange

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
      key={`${query.minPrice ?? ''}-${query.maxPrice ?? ''}`}
      onSubmit={handleSubmit}
      className="flex flex-col gap-3"
      aria-describedby={range ? `${id}-hint` : undefined}
    >
      <div className="grid grid-cols-2 gap-2">
        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${id}-min`} className="text-xs text-muted-foreground">
            Desde (COP)
          </label>
          <Input
            id={`${id}-min`}
            name="min"
            inputMode="numeric"
            placeholder="0"
            defaultValue={query.minPrice ?? ''}
            className="h-10 tabular-nums pointer-coarse:h-11"
          />
        </div>
        <div className="flex flex-col gap-1.5">
          <label htmlFor={`${id}-max`} className="text-xs text-muted-foreground">
            Hasta (COP)
          </label>
          <Input
            id={`${id}-max`}
            name="max"
            inputMode="numeric"
            placeholder="Sin tope"
            defaultValue={query.maxPrice ?? ''}
            className="h-10 tabular-nums pointer-coarse:h-11"
          />
        </div>
      </div>
      {range ? (
        <p id={`${id}-hint`} className="text-xs text-muted-foreground tabular-nums">
          Precios entre {formatCOP(range.min)} y {formatCOP(range.max)}
        </p>
      ) : null}
      <Button type="submit" variant="outline" className="h-10 pointer-coarse:h-11">
        Aplicar precio
      </Button>
    </form>
  )
}

/** Panel de filtros: solo muestra lo que el origen de datos puede filtrar. */
export function FiltersPanel({ query, options, capabilities, onChange, fixedCategory }: FiltersPanelProps) {
  const supports = capabilities.filters

  if (supports.size === 0) {
    return (
      <p className="text-sm text-muted-foreground">
        Los filtros estarán disponibles cuando el catálogo permita buscar por categoría, marca o vehículo.
      </p>
    )
  }

  return (
    <div className="flex flex-col">
      {supports.has('vehicle') && options.vehicles.length > 0 ? (
        <FilterSection title="Tu vehículo">
          <VehiclePicker
            vehicles={options.vehicles}
            years={supports.has('year') ? options.years : undefined}
            value={{ make: query.make, model: query.model, year: query.year }}
            onChange={(vehicle) => onChange(vehicle)}
          />
        </FilterSection>
      ) : null}

      {supports.has('category') && !fixedCategory && options.categories.length > 0 ? (
        <FilterSection title="Categoría">
          <RadioGroup
            aria-label="Categoría"
            value={query.category ?? ''}
            onValueChange={(value) => onChange({ category: typeof value === 'string' && value ? value : undefined })}
            className="gap-0"
          >
            {[{ slug: '', name: 'Todas las categorías' }, ...options.categories].map((category) => (
              <label
                key={category.slug || 'all'}
                className="flex min-h-9 cursor-pointer items-center gap-3 rounded-md px-1 text-sm hover:bg-concrete pointer-coarse:min-h-11"
              >
                <RadioGroupItem value={category.slug} />
                <span>{category.name}</span>
              </label>
            ))}
          </RadioGroup>
        </FilterSection>
      ) : null}

      {supports.has('brand') && options.brands.length > 0 ? (
        <FilterSection title="Marca del repuesto">
          <div className="flex flex-col">
            {options.brands.map((brand) => {
              const checked = query.brands.includes(brand.id)
              return (
                <CheckRow
                  key={brand.id}
                  checked={checked}
                  onCheckedChange={(next) =>
                    onChange({
                      brands: next ? [...query.brands, brand.id] : query.brands.filter((id) => id !== brand.id),
                    })
                  }
                >
                  {brand.name}
                </CheckRow>
              )
            })}
          </div>
        </FilterSection>
      ) : null}

      {supports.has('price') ? (
        <FilterSection title="Precio">
          <PriceFilter query={query} options={options} onChange={onChange} />
        </FilterSection>
      ) : null}

      {supports.has('availability') || supports.has('recommendedStore') ? (
        <FilterSection title="Disponibilidad y tienda">
          <div className="flex flex-col">
            {supports.has('availability') ? (
              <CheckRow checked={query.inStockOnly} onCheckedChange={(inStockOnly) => onChange({ inStockOnly })}>
                Solo con existencias
              </CheckRow>
            ) : null}
            {supports.has('recommendedStore') ? (
              <CheckRow
                checked={query.recommendedOnly}
                onCheckedChange={(recommendedOnly) => onChange({ recommendedOnly })}
                description={RECOMMENDED_EXPLANATION}
              >
                Solo tiendas recomendadas
              </CheckRow>
            ) : null}
          </div>
        </FilterSection>
      ) : null}
    </div>
  )
}
