import { useId, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { SearchIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { useCapabilities, useFilterOptions } from '../hooks'
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
  const options = useFilterOptions()
  const [vehicle, setVehicle] = useState<VehicleValue>({})
  const showVehicle = capabilities === undefined || capabilities.filters.has('vehicle')
  const vehicles = options.data?.vehicles ?? []

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const q = String(new FormData(event.currentTarget).get('q') ?? '').trim()
    navigate(searchUrl({ q, make: vehicle.make, model: vehicle.model }))
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
        'flex flex-col gap-3 rounded-2xl bg-card p-3 text-foreground shadow-[0_0_0_1px_rgb(0_0_0/0.06),0_1px_2px_-1px_rgb(0_0_0/0.06),0_18px_40px_-24px_rgb(23_27_32/0.45)] sm:p-4',
        className,
      )}
    >
      <label htmlFor={id} className="px-1 text-sm font-semibold">
        ¿Qué repuesto buscas?
      </label>
      <div className={cn('flex flex-col gap-3', !showVehicle && 'sm:flex-row')}>
        <div className="relative min-w-0 flex-1">
          <SearchIcon
            className="pointer-events-none absolute top-1/2 left-3.5 size-5 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <input
            id={id}
            name="q"
            type="search"
            autoComplete="off"
            enterKeyHint="search"
            placeholder="Pastillas de freno, filtro o referencia"
            className="h-12 w-full min-w-0 rounded-lg border border-input bg-paper pr-14 pl-11 text-base placeholder:text-muted-foreground"
          />
          <PhotoSearchButton className="absolute top-0.5 right-0.5" />
        </div>
        {showVehicle ? (
          // Vehículo y botón en una fila desde sm: el formulario cabe fijo en el hero de escritorio.
          <div className="grid gap-3 sm:grid-cols-[minmax(0,1fr)_auto] sm:items-end">
            <fieldset className="min-w-0">
              <legend className="sr-only">Vehículo (opcional)</legend>
              <VehiclePicker
                vehicles={vehicles}
                value={vehicle}
                onChange={setVehicle}
                disabled={vehicles.length === 0}
                className="grid-cols-2 [&_[data-slot=select-trigger]]:h-11 [&_label]:text-xs [&_label]:font-normal [&_label]:text-muted-foreground"
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
