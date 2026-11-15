import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { SearchIcon, XIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { BusinessType, StoreSearchItem } from '../api/types'
import { ResultsPagination } from '../components/results-pagination'
import { errorCopy, useCityOptions } from '../components/search/store-search-support'
import { StoreResultRow, StoreRowSkeleton } from '../components/store-card'
import { useStoreSearch } from '../hooks'
import { SEARCH_PARAMS } from '../lib/search-query'

const PAGE_SIZE = 24
const ALL = 'all'

type TypeFilter = 'all' | 'parts' | 'services'
type StoreOrder = 'relevance' | 'name' | 'rating' | 'catalog'

const TYPE_PARAM = 'tipo'
const ORDER_PARAM = 'orden'

const TYPE_OPTIONS: readonly { value: TypeFilter; label: string }[] = [
  { value: 'all', label: 'Todas' },
  { value: 'parts', label: 'Repuestos' },
  { value: 'services', label: 'Servicios' },
]

const ORDER_OPTIONS: readonly { value: StoreOrder; label: string }[] = [
  { value: 'relevance', label: 'Relevancia' },
  { value: 'name', label: 'Nombre (A-Z)' },
  { value: 'rating', label: 'Mejor calificadas' },
  { value: 'catalog', label: 'Más repuestos' },
]

const TYPE_MATCH: Record<TypeFilter, readonly BusinessType[]> = {
  all: ['PARTS', 'SERVICES', 'BOTH'],
  parts: ['PARTS', 'BOTH'],
  services: ['SERVICES', 'BOTH'],
}

function oneOf<T extends string>(options: readonly { value: T }[], raw: string | null, fallback: T): T {
  return options.find((option) => option.value === raw)?.value ?? fallback
}

/** El servidor solo filtra por texto y ciudad; el tipo y el orden se aplican sobre la página recibida. */
function arrange(items: StoreSearchItem[], type: TypeFilter, order: StoreOrder): StoreSearchItem[] {
  const shown = items.filter((item) => TYPE_MATCH[type].includes(item.businessType))
  if (order === 'name') return shown.toSorted((a, b) => a.name.localeCompare(b.name, 'es'))
  if (order === 'rating') return shown.toSorted((a, b) => (b.rating.average ?? -1) - (a.rating.average ?? -1) || b.rating.count - a.rating.count)
  if (order === 'catalog') return shown.toSorted((a, b) => b.publishedProducts - a.publishedProducts)
  return shown
}

/**
 * Directorio de tiendas sobre `GET /marketplace/stores/search`: texto, ciudad, tipo de negocio y orden viven en la URL
 * (`/tiendas?q=&ciudad=&tipo=&orden=&pagina=`). El tipo y el orden se resuelven en el cliente sobre la página cargada.
 */
export default function StoresPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const q = searchParams.get(SEARCH_PARAMS.q)?.trim() ?? ''
  const cityCode = searchParams.get(SEARCH_PARAMS.cityCode)?.trim() || undefined
  const type = oneOf(TYPE_OPTIONS, searchParams.get(TYPE_PARAM), 'all')
  const order = oneOf(ORDER_OPTIONS, searchParams.get(ORDER_PARAM), 'relevance')
  const pageNumber = Number.parseInt(searchParams.get(SEARCH_PARAMS.page) ?? '', 10)
  const page = Number.isFinite(pageNumber) && pageNumber > 0 ? Math.min(pageNumber, 1001) : 1

  const [text, setText] = useState(q)
  const result = useStoreSearch({ q, cityCode, page, pageSize: PAGE_SIZE })
  const cities = useCityOptions(cityCode)
  const data = result.data
  const items = data ? arrange(data.items, type, order) : []
  const cityName = cityCode ? cities.find((city) => city.code === cityCode)?.name : undefined
  const cityItems = [{ value: ALL, label: 'Todas las ciudades' }, ...cities.map((city) => ({ value: city.code, label: city.name }))]
  const hasFilters = Boolean(q || cityCode || type !== 'all')

  const update = (patch: Record<string, string | undefined>) => {
    setSearchParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.delete(SEARCH_PARAMS.page)
        for (const [key, value] of Object.entries(patch)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        return next
      },
      { replace: true, preventScrollReset: true },
    )
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    update({ [SEARCH_PARAMS.q]: text.trim() || undefined })
  }

  const clearAll = () => {
    setText('')
    update({ [SEARCH_PARAMS.q]: undefined, [SEARCH_PARAMS.cityCode]: undefined, [TYPE_PARAM]: undefined })
  }

  let content
  if (result.status === 'error') {
    content = <ErrorState {...errorCopy(result.error)} onRetry={result.retry} />
  } else if (!data) {
    content = (
      <>
        <span className="sr-only" role="status">
          Cargando tiendas
        </span>
        <div className="flex flex-col divide-y divide-ap-line border-y border-ap-graphite">
          {Array.from({ length: 6 }, (_, index) => (
            <StoreRowSkeleton key={index} />
          ))}
        </div>
      </>
    )
  } else if (items.length === 0 && !result.isStale) {
    const onlyTypeHides = data.items.length > 0
    content = (
      <EmptyState
        title={
          onlyTypeHides
            ? 'Ninguna tienda de esta página coincide con el tipo elegido'
            : q
              ? `No encontramos tiendas para “${q}”`
              : cityCode
                ? 'Ninguna tienda coincide con esta ciudad'
                : 'Aún no hay tiendas publicadas'
        }
        description={
          onlyTypeHides
            ? 'El tipo de negocio filtra solo las tiendas de la página actual. Prueba con «Todas» o ve a otra página.'
            : hasFilters
              ? 'Revisa la ortografía, usa menos palabras o quita filtros para ver tiendas de todo el país.'
              : 'Estamos sumando almacenes de todo el país. Si tienes uno, puedes registrarlo.'
        }
      >
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          {hasFilters ? (
            <button
              type="button"
              onClick={clearAll}
              className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
            >
              Quitar filtros
            </button>
          ) : (
            <Link to="/vender" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
              Registrar mi almacén
            </Link>
          )}
          <Link to="/buscar" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
            Buscar repuestos
          </Link>
        </div>
      </EmptyState>
    )
  } else {
    content = (
      <>
        <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', result.isStale && 'opacity-60')} aria-busy={result.isStale}>
          {items.map((store) => (
            <li key={store.id}>
              <StoreResultRow store={store} />
            </li>
          ))}
        </ul>
        <ResultsPagination page={data.page} totalPages={data.totalPages} />
      </>
    )
  }

  return (
    <Container className="py-6 sm:py-8">
      <title>Tiendas de repuestos | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Tiendas' }]}
        label={data ? `Tiendas / ${pluralize(data.total, 'almacén', 'almacenes')}` : 'Tiendas / Directorio'}
        title="Tiendas de repuestos"
        description={<p>Almacenes que publican en Autopartco. Entra a una tienda para ver sus repuestos, servicios y sedes.</p>}
        className="mb-6"
      />

      <form onSubmit={submit} role="search" aria-label="Buscar tiendas" className="mb-4 flex flex-col gap-1.5">
        <Label htmlFor="buscar-tienda" className="text-sm font-semibold text-ap-ink">
          Buscar tienda por nombre o ciudad
        </Label>
        <div className="flex gap-2">
          <Input
            id="buscar-tienda"
            type="search"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="Ej. Repuestos del Caribe, Cartagena"
            autoComplete="off"
            className="flex-1"
          />
          <Button type="submit" className="h-11 gap-2 px-4">
            <SearchIcon aria-hidden />
            Buscar
          </Button>
        </div>
      </form>

      <div className="mb-4 flex flex-col gap-3 border-b border-ap-graphite pb-3 md:flex-row md:items-end md:justify-between">
        <div role="group" aria-label="Tipo de negocio" className="flex flex-wrap gap-2">
          {TYPE_OPTIONS.map((option) => {
            const active = option.value === type
            return (
              <button
                key={option.value}
                type="button"
                aria-pressed={active}
                onClick={() => update({ [TYPE_PARAM]: option.value === 'all' ? undefined : option.value })}
                className={cn(
                  'inline-flex min-h-11 items-center rounded-md border px-4 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--focus)]',
                  active ? 'border-ap-graphite bg-ap-graphite text-ap-white' : 'border-ap-line-strong bg-ap-white text-ap-ink hover:border-ap-graphite',
                )}
              >
                {option.label}
              </button>
            )
          })}
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-2">
          {cities.length > 0 ? (
            <div className="flex items-center gap-2">
              <Label htmlFor="ciudad-tiendas" className="text-sm font-normal text-ap-muted">
                Ciudad
              </Label>
              <Select
                items={cityItems}
                value={cityCode ?? ALL}
                onValueChange={(next) => next && update({ [SEARCH_PARAMS.cityCode]: next === ALL ? undefined : next })}
              >
                <SelectTrigger id="ciudad-tiendas" className="h-11 min-w-44 bg-ap-white">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent align="end">
                  {cityItems.map((item) => (
                    <SelectItem key={item.value} value={item.value}>
                      {item.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          ) : null}
          <div className="flex items-center gap-2">
            <Label htmlFor="orden-tiendas" className="text-sm font-normal text-ap-muted">
              Orden
            </Label>
            <Select
              items={ORDER_OPTIONS}
              value={order}
              onValueChange={(next) => next && update({ [ORDER_PARAM]: next === 'relevance' ? undefined : next })}
            >
              <SelectTrigger id="orden-tiendas" className="h-11 min-w-44 bg-ap-white">
                <SelectValue />
              </SelectTrigger>
              <SelectContent align="end">
                {ORDER_OPTIONS.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      </div>

      <div className="mb-4 flex flex-wrap items-center justify-between gap-x-6 gap-y-2">
        <p role="status" aria-live="polite" className="text-sm text-ap-muted tabular-nums">
          {data && !result.isStale ? (
            <>
              <strong className="font-semibold text-ap-ink">{pluralize(data.total, 'tienda', 'tiendas')}</strong>
              {data.totalPages > 1 ? ` · página ${data.page} de ${data.totalPages}` : ''}
              {type !== 'all' || order !== 'relevance' ? ` · mostrando ${items.length} de ${data.items.length} en esta página` : ''}
            </>
          ) : (
            'Buscando tiendas…'
          )}
        </p>
        <p className="text-sm text-ap-muted">
          <span className="font-semibold text-ap-ink">Tienda verificada:</span> Autopartco revisa cada almacén antes de publicarlo.
        </p>
      </div>

      {q || cityCode ? (
        <div className="mb-4 flex flex-wrap items-center gap-2">
          <p className="ap-index mr-1">Filtros</p>
          {q ? (
            <button
              type="button"
              onClick={() => {
                setText('')
                update({ [SEARCH_PARAMS.q]: undefined })
              }}
              aria-label={`Quitar filtro: texto “${q}”`}
              className="inline-flex h-9 items-center gap-1.5 rounded-sm border border-ap-graphite bg-ap-paper pr-2 pl-2.5 text-sm text-ap-ink transition-colors hover:bg-ap-white pointer-coarse:h-11"
            >
              <span className="font-medium">“{q}”</span>
              <XIcon className="size-3.5 text-ap-muted" aria-hidden />
            </button>
          ) : null}
          {cityCode ? (
            <button
              type="button"
              onClick={() => update({ [SEARCH_PARAMS.cityCode]: undefined })}
              aria-label={`Quitar filtro: ${cityName ?? 'Ciudad elegida'}`}
              className="inline-flex h-9 items-center gap-1.5 rounded-sm border border-ap-graphite bg-ap-paper pr-2 pl-2.5 text-sm text-ap-ink transition-colors hover:bg-ap-white pointer-coarse:h-11"
            >
              <span className="font-medium">{cityName ?? 'Ciudad elegida'}</span>
              <XIcon className="size-3.5 text-ap-muted" aria-hidden />
            </button>
          ) : null}
        </div>
      ) : null}

      {content}
    </Container>
  )
}
