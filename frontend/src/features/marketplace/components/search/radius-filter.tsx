import { useId } from 'react'
import { LoaderCircleIcon, LocateFixedIcon } from 'lucide-react'
import { OptionSelect } from '@/components/common/option-select'
import { Button } from '@/components/ui/button'
import { DEFAULT_RADIUS_KM, RADIUS_OPTIONS_KM, type SearchState } from '../../lib/search-query'
import type { NearMe } from './use-near-me'

interface NearMeFilterProps {
  state: SearchState
  nearMe: NearMe
  onChange: (patch: Partial<SearchState>) => void
}

/** «Cerca de mí» con filtro de radio. Sin radio, la ubicación solo ordena por cercanía. */
export function NearMeFilter({ state, nearMe, onChange }: NearMeFilterProps) {
  const id = useId()
  const { origin, locating, error, request } = nearMe

  async function enable() {
    onChange({ near: true, radiusKm: state.radiusKm ?? DEFAULT_RADIUS_KM })
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
          options={RADIUS_OPTIONS_KM.map((km) => ({ value: String(km), label: `${km} km` }))}
          onValueChange={(value) => onChange({ radiusKm: value ? Number(value) : undefined })}
        />
      ) : null}
      <p role="status" className="text-xs text-ap-muted">
        {locating
          ? 'Obteniendo tu ubicación…'
          : error
            ? error
            : state.near && !origin
              ? 'Necesitamos tu ubicación para buscar cerca. Se usa solo en este dispositivo.'
              : state.near && origin
                ? 'Mostramos la distancia a la sede más cercana de cada tienda.'
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
