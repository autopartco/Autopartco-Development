import { useId } from 'react'
import { OptionSelect } from '@/components/common/option-select'
import { cn } from '@/lib/utils'
import type { VehicleMakeOption } from '../api/types'

export interface VehicleValue {
  make?: string
  model?: string
  year?: number
}

interface VehiclePickerProps {
  vehicles: VehicleMakeOption[]
  /** Años disponibles; si el servicio no los informa, no se muestra el selector de año. */
  years?: number[]
  value: VehicleValue
  onChange: (value: VehicleValue) => void
  className?: string
  disabled?: boolean
}

/** Marca, modelo y (si hay datos) año del vehículo. El modelo depende de la marca. */
export function VehiclePicker({ vehicles, years, value, onChange, className, disabled }: VehiclePickerProps) {
  const id = useId()
  const models = vehicles.find((option) => option.make === value.make)?.models ?? []
  const showYear = Boolean(years && years.length > 0)

  return (
    <div className={cn('grid gap-3', className)}>
      <OptionSelect
        id={`${id}-make`}
        label="Marca del vehículo"
        emptyLabel="Todas las marcas"
        value={value.make ?? ''}
        disabled={disabled}
        options={vehicles.map((option) => ({ value: option.make, label: option.make }))}
        onValueChange={(make) => onChange({ make: make || undefined, model: undefined, year: value.year })}
      />
      <OptionSelect
        id={`${id}-model`}
        label="Modelo"
        emptyLabel={value.make ? 'Todos los modelos' : 'Elige la marca'}
        value={value.model ?? ''}
        disabled={disabled || !value.make}
        options={models.map((model) => ({ value: model, label: model }))}
        onValueChange={(model) => onChange({ ...value, model: model || undefined })}
      />
      {showYear ? (
        <OptionSelect
          id={`${id}-year`}
          label="Año"
          emptyLabel="Cualquier año"
          value={value.year ? String(value.year) : ''}
          disabled={disabled}
          options={(years ?? []).map((year) => ({ value: String(year), label: String(year) }))}
          onValueChange={(year) => onChange({ ...value, year: year ? Number(year) : undefined })}
        />
      ) : null}
    </div>
  )
}
