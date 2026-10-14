import { LocateFixedIcon, MapPinIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
} from '@/components/ui/select'
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip'
import { cn } from '@/lib/utils'
import { setSelectedCityId } from '../city-store'
import { useCities, useLocationService, useNearbyStores, useSelectedCityId } from '../hooks'

const GEOLOCATION_ITEM = '__geolocation'

/*
 * El selector vive en la cabecera carbón: texto blanco, chincheta en el rojo
 * claro para fondo oscuro y chevron en gris claro (8.8:1). Los menús que abre
 * se pintan en un portal claro, con el foco rojo del tema base.
 */
const TRIGGER_ON_DARK =
  'h-11 gap-1.5 rounded-lg border-transparent bg-transparent px-2.5 text-sm font-medium text-white transition-colors hover:bg-header-hover data-popup-open:bg-header-hover lg:h-10 [&>svg:last-child]:text-asphalt-muted'

/** Origen sin selección de ciudad (API real): se muestra como próximamente. */
function CitySoon({ className }: { className?: string }) {
  return (
    <Tooltip>
      <TooltipTrigger
        render={
          <Button
            variant="ghost"
            className={cn(TRIGGER_ON_DARK, 'text-asphalt-muted hover:text-white', className)}
            aria-disabled="true"
            aria-describedby="ciudad-proximamente"
            onClick={(event) => event.preventDefault()}
          />
        }
      >
        <MapPinIcon className="text-brand-on-asphalt" aria-hidden />
        <span className="hidden min-[400px]:inline">Colombia</span>
        <span className="sr-only min-[400px]:hidden">Ciudad</span>
      </TooltipTrigger>
      {/* Debajo del botón y con borde claro: arriba quedaría fuera de pantalla y, sobre carbón, sin contorno. */}
      <TooltipContent side="bottom" className="ring-1 ring-white/20">
        Pronto podrás elegir tu ciudad para ver almacenes cercanos.
      </TooltipContent>
      <span id="ciudad-proximamente" className="sr-only">
        Próximamente podrás elegir tu ciudad.
      </span>
    </Tooltip>
  )
}

/**
 * Selector de ciudad de la cabecera. Al cambiarla se recalculan los almacenes
 * cercanos en todas las pantallas. "Usar mi ubicación" queda deshabilitado.
 */
export function CityPicker({ className }: { className?: string }) {
  const service = useLocationService()
  const cities = useCities()
  const nearby = useNearbyStores()
  const selectedId = useSelectedCityId()

  if (service && !service.capabilities.citySelection) return <CitySoon className={className} />

  const list = cities.data ?? []
  const value = selectedId ?? nearby.data?.city?.id ?? service?.defaultCityId ?? ''
  const current = list.find((city) => city.id === value)
  const ready = list.length > 0

  return (
    <Select
      items={list.map((city) => ({ value: city.id, label: city.name }))}
      value={value}
      disabled={!ready}
      onValueChange={(next) => {
        if (typeof next === 'string' && next && next !== GEOLOCATION_ITEM) setSelectedCityId(next)
      }}
    >
      <SelectTrigger
        aria-label={current ? `Ciudad: ${current.name}. Cambiar ciudad` : 'Elegir ciudad'}
        className={cn(TRIGGER_ON_DARK, className)}
      >
        <MapPinIcon className="text-brand-on-asphalt" aria-hidden />
        {/*
         * Ancho mínimo reservado: el nombre llega después sin mover la cabecera.
         * Por debajo de 400px solo queda la chincheta (el nombre va en aria-label).
         */}
        <span className="hidden max-w-[7.5rem] min-w-[3.25rem] truncate text-left min-[400px]:inline">
          {current?.name ?? 'Ciudad'}
        </span>
      </SelectTrigger>
      <SelectContent alignItemWithTrigger={false} align="end" className="min-w-72">
        <SelectGroup>
          <SelectLabel>Ver almacenes cerca de</SelectLabel>
          {list.map((city) => (
            <SelectItem key={city.id} value={city.id}>
              {city.name}
            </SelectItem>
          ))}
        </SelectGroup>
        <SelectSeparator />
        <SelectItem value={GEOLOCATION_ITEM} disabled>
          <LocateFixedIcon aria-hidden />
          Usar mi ubicación
          <span className="ml-auto label-mono text-muted-foreground">Próximamente</span>
        </SelectItem>
      </SelectContent>
    </Select>
  )
}
