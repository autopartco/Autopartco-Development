import { useId, type FormEvent } from 'react'
import { LoaderCircleIcon, LocateFixedIcon } from 'lucide-react'
import { OptionSelect } from '@/components/common/option-select'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { isServiceCategory } from '../lib/categories'
import { SingleFacetList, type FacetItem } from '@/features/marketplace/components/search/facet-list'
import { CheckRow, FilterSection } from '@/features/marketplace/components/search/filters-panel'
import type { NearMe } from '@/features/marketplace/components/search/use-near-me'
import type { ServiceCategoryCount, ServiceSearchResult } from '../api/types'
import { SERVICE_DEFAULT_RADIUS_KM, SERVICE_RADIUS_OPTIONS_KM, type ServiceState } from '../lib/service-query'

export interface ServiceFiltersProps {
  state: ServiceState
  /** Conteos del servidor para la consulta actual (ciudades). */
  facets: ServiceSearchResult['facets']
  categories: ServiceCategoryCount[] | undefined
  nearMe: NearMe
  onChange: (patch: Partial<ServiceState>) => void
  /** La categoría se elige en otro control (rejilla de categorías de la página). */
  hideCategory?: boolean
}

function NearFilter({ state, nearMe, onChange }: Pick<ServiceFiltersProps, 'state' | 'nearMe' | 'onChange'>) {
  const id = useId()
  const { origin, locating, error, request } = nearMe

  async function enable() {
    onChange({ near: true, radiusKm: state.radiusKm ?? SERVICE_DEFAULT_RADIUS_KM })
    await request()
  }

  function disable() {
    onChange({ near: false, radiusKm: undefined, sort: state.sort === 'nearest' ? 'relevance' : state.sort })
  }

  return (
    <div className="flex flex-col gap-3">
      <Button
        type="button"
        variant={state.near ? 'default' : 'outline'}
        aria-pressed={state.near}
        disabled={locating}
        onClick={state.near ? disable : enable}
        className="h-10 justify-start gap-2 pointer-coarse:h-11"
      >
        {locating ? <LoaderCircleIcon className="animate-spin" aria-hidden /> : <LocateFixedIcon aria-hidden />}
        Cerca de mí
      </Button>
      {state.near ? (
        <OptionSelect
          id={`${id}-radius`}
          label="Radio de búsqueda"
          emptyLabel="Sin límite (solo ordena)"
          value={state.radiusKm ? String(state.radiusKm) : ''}
          options={SERVICE_RADIUS_OPTIONS_KM.map((km) => ({ value: String(km), label: `${km} km` }))}
          onValueChange={(value) => onChange({ radiusKm: value ? Number(value) : undefined })}
        />
      ) : null}
      <p role="status" className="text-xs text-ap-muted">
        {locating
          ? 'Obteniendo tu ubicación…'
          : error
            ? error
            : state.near && origin
              ? 'Mostramos la distancia a la sede más cercana de cada servicio.'
              : state.near
                ? 'Usamos la ciudad que elegiste arriba. Para mayor precisión, comparte tu ubicación.'
                : 'Te pediremos permiso para usar tu ubicación.'}
      </p>
      {state.near && !origin && !locating ? (
        <Button type="button" variant="outline" className="h-10 pointer-coarse:h-11" onClick={() => void request()}>
          Usar mi ubicación
        </Button>
      ) : null}
    </div>
  )
}

function PriceMaxFilter({ value, onChange }: { value: number | undefined; onChange: ServiceFiltersProps['onChange'] }) {
  const id = useId()

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const digits = String(new FormData(event.currentTarget).get('max') ?? '').replace(/\D/g, '')
    onChange({ priceMax: digits ? Number(digits) : undefined })
  }

  return (
    <form key={value ?? 'none'} onSubmit={handleSubmit} className="flex flex-col gap-3">
      <div className="flex min-w-0 flex-col gap-1.5">
        <label htmlFor={id} className="text-xs text-ap-muted">
          Precio «desde» hasta (COP)
        </label>
        <Input
          id={id}
          name="max"
          inputMode="numeric"
          placeholder="Sin tope"
          defaultValue={value ?? ''}
          className="h-10 tabular-nums pointer-coarse:h-11"
        />
      </div>
      <Button type="submit" variant="outline" className="h-10 pointer-coarse:h-11">
        Aplicar precio
      </Button>
    </form>
  )
}

/** Filtros de servicios: cercanía, categoría, ciudad, precio máximo, promoción, agenda en línea y abierto ahora. */
export function ServiceFilters({ state, facets, categories, nearMe, onChange, hideCategory }: ServiceFiltersProps) {
  const categoryItems: FacetItem[] = (categories ?? []).map((item) => ({ id: item.category, name: item.label, count: item.count }))
  const cities: FacetItem[] = facets?.cities.map((city) => ({ id: city.code, name: city.name, count: city.count })) ?? []

  return (
    <div className="flex flex-col">
      <FilterSection title="Cerca de ti">
        <NearFilter state={state} nearMe={nearMe} onChange={onChange} />
      </FilterSection>

      {!hideCategory && categoryItems.length > 0 ? (
        <FilterSection title="Categoría">
          <SingleFacetList
            label="Categoría"
            allLabel="Todas las categorías"
            items={categoryItems}
            selected={state.category}
            onChange={(category) => onChange({ category: isServiceCategory(category) ? category : undefined })}
          />
        </FilterSection>
      ) : null}

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

      <FilterSection title="Precio">
        <PriceMaxFilter value={state.priceMax} onChange={onChange} />
      </FilterSection>

      <FilterSection title="Promoción y agenda">
        <div className="flex flex-col">
          <CheckRow checked={state.onSale} onCheckedChange={(onSale) => onChange({ onSale })}>
            En promoción
          </CheckRow>
          <CheckRow checked={state.bookable} onCheckedChange={(bookable) => onChange({ bookable })}>
            Con agenda en línea
          </CheckRow>
          <CheckRow checked={state.openNow} onCheckedChange={(openNow) => onChange({ openNow })}>
            Abierto ahora
          </CheckRow>
        </div>
      </FilterSection>
    </div>
  )
}
