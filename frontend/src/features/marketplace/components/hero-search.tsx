import { useId, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { SearchIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useCapabilities } from '../hooks'
import { searchUrl } from '../lib/product-query'
import { PhotoSearchButton } from './photo-search'
import { VehiclePicker, type VehicleValue } from './vehicle-picker'

/**
 * Buscador principal: qué repuesto y para qué vehículo, como se pregunta en el mostrador.
 * El selector de vehículo aparece solo si el origen de datos permite filtrar por vehículo;
 * mientras se sabe, ocupa su espacio deshabilitado para no mover la página.
 */
export function HeroSearch({ className }: { className?: string }) {
  const id = useId()
  const navigate = useNavigate()
  const capabilities = useCapabilities()
  const [vehicle, setVehicle] = useState<VehicleValue>({})
  const showVehicle = capabilities === undefined || capabilities.filters.has('vehicle')

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const q = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    navigate(searchUrl({ q, makeId: vehicle.makeId, modelId: vehicle.modelId, versionId: vehicle.versionId }))
  }

  const submit = (
    <Button type="submit" className="h-11 px-6 text-base">
      Buscar
    </Button>
  )

  return (
    <form
      role="search"
      aria-label="Buscar repuestos"
      onSubmit={handleSubmit}
      className={cn(
        'flex flex-col gap-4 rounded-xl border border-ap-line-strong bg-ap-white p-4 text-ap-ink sm:p-5',
        className,
      )}
    >
      <div className="flex flex-col gap-1">
        <label htmlFor={id} className="text-sm font-semibold">
          Repuesto o referencia
        </label>
        <p id={`${id}-hint`} className="text-xs text-ap-muted">
          Escribe el nombre de la pieza o el número de parte que trae la caja.
        </p>
      </div>
      <div className={cn('flex flex-col gap-3', !showVehicle && 'sm:flex-row')}>
        <div className="relative min-w-0 flex-1">
          <SearchIcon
            className="pointer-events-none absolute top-1/2 left-3.5 size-5 -translate-y-1/2 text-ap-muted"
            aria-hidden
          />
          <input
            id={id}
            name="q"
            type="search"
            autoComplete="off"
            enterKeyHint="search"
            placeholder="Ej.: pastillas de freno o el número de parte"
            aria-describedby={`${id}-hint`}
            className="h-12 w-full min-w-0 rounded-md border border-input bg-ap-white pr-14 pl-11 text-base placeholder:text-ap-muted focus-visible:border-ap-graphite"
          />
          <PhotoSearchButton className="absolute top-0.5 right-0.5" />
        </div>
        {showVehicle ? (
          // Vehículo y botón en una fila desde sm: el formulario cabe fijo en el hero de escritorio.
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
            <fieldset className="min-w-0">
              <legend className="mb-2 text-sm font-semibold">
                Vehículo <span className="font-normal text-ap-muted">(opcional, filtra por compatibilidad)</span>
              </legend>
              <VehiclePicker
                value={vehicle}
                onChange={setVehicle}
                className="grid-cols-1 sm:grid-cols-3 [&_[data-slot=select-trigger]]:h-11 [&_label]:text-xs [&_label]:font-normal [&_label]:text-ap-muted"
              />
            </fieldset>
            {submit}
          </div>
        ) : (
          submit
        )}
      </div>
    </form>
  )
}
