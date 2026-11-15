import { useId } from 'react'
import { FieldShell, INPUT_CLASS, TextField } from '@/components/common/form-fields'
import { OptionSelect } from '@/components/common/option-select'
import { useVehicleMakes, useVehicleModels } from '@/features/marketplace/hooks'
import { cn } from '@/lib/utils'
import { NOTE_MAX } from '../../lib/booking'
import type { BookingDraft, DraftErrors } from '../../lib/booking-draft'

interface DetailsStepProps {
  draft: BookingDraft
  onChange: (patch: Partial<BookingDraft>) => void
  /** Teléfono efectivo (el editado o el del perfil). */
  phone: string
  phoneLoading: boolean
  errors: DraftErrors
}

/** Paso «Vehículo y contacto»: marca y modelo del catálogo, año y placa opcionales, teléfono y nota. */
export function DetailsStep({ draft, onChange, phone, phoneLoading, errors }: DetailsStepProps) {
  const id = useId()
  const makes = useVehicleMakes()
  const models = useVehicleModels(draft.makeId || undefined)
  const modelsLoading = Boolean(draft.makeId) && (models.status === 'loading' || models.isStale)
  const modelOptions = draft.makeId && !models.isStale ? (models.data ?? []) : []
  const failed = makes.status === 'error' || (Boolean(draft.makeId) && models.status === 'error')

  return (
    <div className="flex flex-col gap-4">
      <fieldset className="flex min-w-0 flex-col gap-3">
        <legend className="mb-1 text-sm font-semibold text-ap-ink">
          Tu vehículo <span className="font-normal text-ap-muted">(opcional, ayuda al taller a prepararse)</span>
        </legend>
        <div className="grid gap-3 sm:grid-cols-2">
          <OptionSelect
            id={`${id}-make`}
            label="Marca"
            emptyLabel={makes.status === 'loading' ? 'Cargando marcas…' : 'Elige la marca'}
            value={draft.makeId}
            disabled={makes.status !== 'success'}
            options={(makes.data ?? []).map((make) => ({ value: make.id, label: make.name }))}
            onValueChange={(makeId) => onChange({ makeId, modelId: '' })}
          />
          <OptionSelect
            id={`${id}-model`}
            label="Modelo"
            emptyLabel={!draft.makeId ? 'Primero la marca' : modelsLoading ? 'Cargando modelos…' : 'Elige el modelo'}
            value={draft.modelId}
            disabled={!draft.makeId || modelsLoading}
            options={modelOptions.map((model) => ({ value: model.id, label: model.name }))}
            onValueChange={(modelId) => onChange({ modelId })}
          />
        </div>
        {errors.vehicle ? (
          <p role="alert" className="text-sm font-medium text-ap-danger">
            {errors.vehicle}
          </p>
        ) : null}
        {failed ? (
          <p role="alert" className="text-xs text-ap-danger">
            No pudimos cargar los vehículos. Puedes continuar sin indicarlo.
          </p>
        ) : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <TextField
            id={`${id}-year`}
            label="Año"
            optional
            inputMode="numeric"
            maxLength={4}
            autoComplete="off"
            value={draft.year}
            error={errors.year}
            onChange={(event) => onChange({ year: event.target.value.replace(/\D/g, '') })}
          />
          <TextField
            id={`${id}-plate`}
            label="Placa"
            optional
            maxLength={7}
            autoComplete="off"
            autoCapitalize="characters"
            hint="De 5 a 7 letras o números, sin guiones."
            value={draft.plate}
            error={errors.plate}
            className="uppercase"
            onChange={(event) => onChange({ plate: event.target.value.replace(/[^A-Za-z0-9]/g, '').toUpperCase() })}
          />
        </div>
      </fieldset>

      <TextField
        id={`${id}-phone`}
        label="Teléfono de contacto"
        type="tel"
        inputMode="tel"
        autoComplete="tel"
        maxLength={20}
        hint={phoneLoading ? 'Buscando el teléfono de tu perfil…' : 'El taller lo usa solo para esta cita.'}
        value={phone}
        error={errors.phone}
        onChange={(event) => onChange({ phone: event.target.value })}
      />

      <FieldShell id={`${id}-note`} label="Nota para el taller" optional error={errors.note} hint={`${draft.note.length} de ${NOTE_MAX} caracteres`}>
        {(describedBy) => (
          <textarea
            id={`${id}-note`}
            rows={3}
            maxLength={NOTE_MAX}
            aria-invalid={errors.note ? true : undefined}
            aria-describedby={describedBy}
            value={draft.note}
            onChange={(event) => onChange({ note: event.target.value })}
            className={cn(INPUT_CLASS, 'h-auto min-h-24 resize-y py-2')}
          />
        )}
      </FieldShell>
    </div>
  )
}
