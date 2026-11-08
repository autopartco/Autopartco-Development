import { useEffect, useId, useRef, useState } from 'react'
import { Popover as PopoverPrimitive } from '@base-ui/react/popover'
import { GlobeIcon, LoaderCircleIcon, LocateFixedIcon, MapPinIcon, SearchIcon } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { searchPlaces, reverseInColombia } from '../api/photon-geocoder'
import type { GeoPoint, PlaceSuggestion } from '../api/types'
import { useLocationService, useMapArea } from '../hooks'
import { DEFAULT_MAP_AREA, setMapArea } from '../location-store'
import { isInColombia } from '../lib/map-config'

/* Mismo aspecto que el selector de ciudad: texto blanco y chincheta en rojo para fondo oscuro. */
const TRIGGER_ON_DARK =
  'inline-flex h-11 items-center gap-1.5 rounded-lg border border-transparent bg-transparent px-2.5 text-sm font-medium text-white transition-colors outline-none hover:bg-sidebar-accent focus-visible:ring-3 focus-visible:ring-ring/50 data-popup-open:bg-sidebar-accent motion-reduce:transition-none lg:h-10'

const MIN_CHARS = 3
const DEBOUNCE_MS = 300
const KIND_LABEL: Record<PlaceSuggestion['kind'], string> = {
  country: 'País',
  state: 'Departamento',
  city: 'Ciudad',
  district: 'Barrio o zona',
  locality: 'Localidad',
}

type SearchState =
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'done'; results: PlaceSuggestion[] }

function getPosition(): Promise<GeoPoint> {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
      reject,
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    )
  })
}

/** Selector de zona de la cabecera: búsqueda de lugares en Colombia, "Toda Colombia" y ubicación actual. */
export function LocationPicker({ className }: { className?: string }) {
  const area = useMapArea()
  const service = useLocationService()
  const canLocate =
    Boolean(service?.capabilities.geolocation) && typeof navigator !== 'undefined' && 'geolocation' in navigator

  const [open, setOpen] = useState(false)
  const [text, setText] = useState('')
  const [search, setSearch] = useState<SearchState>({ status: 'idle' })
  const [active, setActive] = useState(-1)
  const [locating, setLocating] = useState(false)
  const inputRef = useRef<HTMLInputElement>(null)
  const uid = useId()
  const listId = `${uid}-list`
  const optionId = (index: number) => `${uid}-opt-${index}`

  const query = text.trim()
  const tooShort = query.length < MIN_CHARS
  const results = search.status === 'done' && !tooShort ? search.results : []

  useEffect(() => {
    if (!open || query.length < MIN_CHARS) return
    const controller = new AbortController()
    const timer = window.setTimeout(() => {
      setSearch({ status: 'loading' })
      setActive(-1)
      searchPlaces(query, controller.signal).then(
        (found) => {
          if (!controller.signal.aborted) setSearch({ status: 'done', results: found })
        },
        () => {
          if (!controller.signal.aborted) setSearch({ status: 'error' })
        },
      )
    }, DEBOUNCE_MS)
    return () => {
      window.clearTimeout(timer)
      controller.abort()
    }
  }, [query, open])

  function handleOpenChange(next: boolean) {
    setOpen(next)
    if (!next) {
      setText('')
      setSearch({ status: 'idle' })
      setActive(-1)
    }
  }

  function choose(suggestion: PlaceSuggestion) {
    setMapArea(suggestion.area)
    handleOpenChange(false)
  }

  function onInputKeyDown(event: React.KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'ArrowDown' && results.length) {
      event.preventDefault()
      setActive((index) => (index + 1) % results.length)
    } else if (event.key === 'ArrowUp' && results.length) {
      event.preventDefault()
      setActive((index) => (index <= 0 ? results.length - 1 : index - 1))
    } else if (event.key === 'Enter' && active >= 0 && results[active]) {
      event.preventDefault()
      choose(results[active])
    }
  }

  async function locateMe() {
    if (locating) return
    setLocating(true)
    const outside = 'Solo mostramos almacenes en Colombia'
    try {
      const point = await getPosition().catch(() => null)
      if (!point) {
        toast.error('No pudimos obtener tu ubicación')
        return
      }
      if (!isInColombia(point)) {
        toast.error(outside)
        return
      }
      const place = await reverseInColombia(point).catch(() => ({ label: 'Tu ubicación' }))
      if (!place) {
        toast.error(outside)
        return
      }
      const dLat = 0.45
      const dLng = 0.45 / Math.cos((point.lat * Math.PI) / 180)
      setMapArea({
        label: place.label,
        precision: 'exact',
        user: point,
        center: point,
        bounds: { south: point.lat - dLat, north: point.lat + dLat, west: point.lng - dLng, east: point.lng + dLng },
      })
      handleOpenChange(false)
    } finally {
      setLocating(false)
    }
  }

  let status = ''
  if (tooShort) status = 'Escribe al menos 3 letras'
  else if (search.status === 'error') status = 'No pudimos buscar. Inténtalo de nuevo.'
  else if (search.status !== 'done') status = 'Buscando…'
  else if (!results.length) status = 'No encontramos ese lugar en Colombia'

  return (
    <PopoverPrimitive.Root open={open} onOpenChange={handleOpenChange}>
      <PopoverPrimitive.Trigger
        aria-label={`Ubicación: ${area.label}. Cambiar ubicación`}
        className={cn(TRIGGER_ON_DARK, className)}
      >
        <MapPinIcon className="size-4 shrink-0 text-ap-red-on-dark" aria-hidden />
        <span className="hidden max-w-[7.5rem] min-w-[3.25rem] truncate text-left min-[400px]:inline">{area.label}</span>
      </PopoverPrimitive.Trigger>
      <PopoverPrimitive.Portal>
        <PopoverPrimitive.Positioner side="bottom" align="end" sideOffset={6} className="isolate z-50">
          <PopoverPrimitive.Popup
            initialFocus={inputRef}
            className="w-[min(22rem,calc(100vw-1.5rem))] origin-(--transform-origin) rounded-xl border border-ap-line bg-popover p-3 text-popover-foreground shadow-overlay outline-none transition-opacity duration-150 data-ending-style:opacity-0 data-starting-style:opacity-0 motion-reduce:transition-none"
          >
            <PopoverPrimitive.Title className="mb-2 text-sm font-semibold text-ap-ink">
              Ver almacenes cerca de
            </PopoverPrimitive.Title>
            <label htmlFor={`${uid}-q`} className="sr-only">
              Buscar ciudad, barrio o departamento
            </label>
            <div className="relative">
              <SearchIcon
                className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ap-muted"
                aria-hidden
              />
              <input
                ref={inputRef}
                id={`${uid}-q`}
                type="text"
                role="combobox"
                aria-expanded={results.length > 0}
                aria-controls={listId}
                aria-autocomplete="list"
                aria-activedescendant={active >= 0 ? optionId(active) : undefined}
                autoComplete="off"
                placeholder="Ej. Chapinero, Medellín"
                value={text}
                onChange={(event) => {
                  setText(event.target.value)
                  setActive(-1)
                }}
                onKeyDown={onInputKeyDown}
                className="h-11 w-full rounded-md border border-input bg-ap-white pr-3 pl-9 text-base text-ap-ink outline-none placeholder:text-ap-muted hover:border-ap-graphite focus-visible:border-ap-graphite focus-visible:ring-3 focus-visible:ring-ring/50 md:text-sm"
              />
            </div>

            <ul id={listId} role="listbox" aria-label="Lugares encontrados" className="mt-1 max-h-64 overflow-y-auto">
              {results.map((place, index) => (
                <li
                  key={place.id}
                  id={optionId(index)}
                  role="option"
                  aria-selected={index === active}
                  onMouseMove={() => setActive(index)}
                  onClick={() => choose(place)}
                  className={cn(
                    'flex min-h-11 cursor-pointer flex-col justify-center rounded-md px-2.5 py-1.5',
                    index === active && 'bg-ap-paper outline-2 -outline-offset-2 outline-ap-graphite',
                  )}
                >
                  <span className="text-sm font-medium text-ap-ink">{place.label}</span>
                  <span className="text-xs text-ap-muted">{KIND_LABEL[place.kind]}</span>
                </li>
              ))}
            </ul>
            <p role="status" className={cn('px-2.5 text-sm text-ap-muted', status ? 'py-2' : 'sr-only')}>
              {status || (results.length ? `${results.length} lugares encontrados` : '')}
            </p>

            <div className="mt-1 border-t border-ap-line pt-2">
              <button
                type="button"
                onClick={() => {
                  setMapArea(DEFAULT_MAP_AREA)
                  handleOpenChange(false)
                }}
                className="flex min-h-11 w-full items-center gap-2 rounded-md px-2.5 text-left text-sm font-medium text-ap-ink outline-none hover:bg-ap-paper focus-visible:ring-3 focus-visible:ring-ring/50"
              >
                <GlobeIcon className="size-4 text-ap-muted" aria-hidden />
                Toda Colombia
              </button>
              {canLocate && (
                <>
                  <button
                    type="button"
                    onClick={locateMe}
                    disabled={locating}
                    aria-busy={locating}
                    className="flex min-h-11 w-full items-center gap-2 rounded-md px-2.5 text-left text-sm font-medium text-ap-ink outline-none hover:bg-ap-paper focus-visible:ring-3 focus-visible:ring-ring/50 disabled:cursor-not-allowed disabled:opacity-60"
                  >
                    {locating ? (
                      <LoaderCircleIcon className="size-4 animate-spin motion-reduce:animate-none" aria-hidden />
                    ) : (
                      <LocateFixedIcon className="size-4 text-ap-muted" aria-hidden />
                    )}
                    {locating ? 'Buscando tu ubicación…' : 'Usar mi ubicación'}
                  </button>
                  <p className="px-2.5 pb-1 text-xs text-ap-muted">
                    Usamos tu ubicación solo para mostrarte almacenes cercanos; no la guardamos.
                  </p>
                </>
              )}
            </div>
          </PopoverPrimitive.Popup>
        </PopoverPrimitive.Positioner>
      </PopoverPrimitive.Portal>
    </PopoverPrimitive.Root>
  )
}
