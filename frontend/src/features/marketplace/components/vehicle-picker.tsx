import { useId } from 'react'
import { OptionSelect } from '@/components/common/option-select'
import { cn } from '@/lib/utils'
import { useVehicleMakes, useVehicleModels, useVehicleVersions } from '../hooks'

export interface VehicleValue {
  makeId?: string
  modelId?: string
  versionId?: string
}

interface VehiclePickerProps {
  value: VehicleValue
  onChange: (value: VehicleValue) => void
  className?: string
  disabled?: boolean
}

/**
 * Marca → Modelo → Versión en cascada. Cada nivel consulta el catálogo al elegir el anterior (con caché
 * en memoria) y se bloquea mientras no hay nivel superior, así que no hay combinaciones incoherentes.
 * El filtrado lo hace el servidor: aquí solo se arman `makeId`/`modelId`/`versionId`.
 */
export function VehiclePicker({ value, onChange, className, disabled }: VehiclePickerProps) {
  const id = useId()
  const makes = useVehicleMakes()
  const models = useVehicleModels(value.makeId)
  const versions = useVehicleVersions(value.modelId)

  const modelsLoading = Boolean(value.makeId) && (models.status === 'loading' || models.isStale)
  const versionsLoading = Boolean(value.modelId) && (versions.status === 'loading' || versions.isStale)
  const modelOptions = value.makeId && !models.isStale ? (models.data ?? []) : []
  const versionOptions = value.modelId && !versions.isStale ? (versions.data ?? []) : []
  const failed = makes.status === 'error' || (Boolean(value.makeId) && models.status === 'error') || (Boolean(value.modelId) && versions.status === 'error')

  return (
    <div className={cn('grid gap-3', className)}>
      <OptionSelect
        id={`${id}-make`}
        label="Marca del vehículo"
        emptyLabel={makes.status === 'loading' ? 'Cargando marcas…' : 'Todas las marcas'}
        value={value.makeId ?? ''}
        disabled={disabled || makes.status !== 'success'}
        options={(makes.data ?? []).map((make) => ({ value: make.id, label: make.name }))}
        onValueChange={(makeId) => onChange({ makeId: makeId || undefined, modelId: undefined, versionId: undefined })}
      />
      <OptionSelect
        id={`${id}-model`}
        label="Modelo"
        emptyLabel={!value.makeId ? 'Elige la marca' : modelsLoading ? 'Cargando modelos…' : 'Todos los modelos'}
        value={value.modelId ?? ''}
        disabled={disabled || !value.makeId || modelsLoading}
        options={modelOptions.map((model) => ({ value: model.id, label: model.name }))}
        onValueChange={(modelId) => onChange({ makeId: value.makeId, modelId: modelId || undefined, versionId: undefined })}
      />
      <OptionSelect
        id={`${id}-version`}
        label="Versión"
        emptyLabel={!value.modelId ? 'Elige el modelo' : versionsLoading ? 'Cargando versiones…' : 'Todas las versiones'}
        value={value.versionId ?? ''}
        disabled={disabled || !value.modelId || versionsLoading}
        options={versionOptions.map((version) => ({ value: version.id, label: version.name }))}
        onValueChange={(versionId) => onChange({ ...value, versionId: versionId || undefined })}
      />
      {failed ? (
        <p role="alert" className="text-xs text-destructive">
          No pudimos cargar los vehículos. Revisa tu conexión e inténtalo de nuevo.
        </p>
      ) : null}
    </div>
  )
}
