import { useState, type FormEvent } from 'react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { useSubmitOnce } from '@/lib/use-submit-once'
import type { PersonAddressDto, PersonAddressRequestDto } from '../api/types'
import { getAddressService } from '../api/checkout-service'

const FIELDS = ['label', 'addressLine', 'addressComplement', 'city', 'department', 'postalCode'] as const
type Field = (typeof FIELDS)[number]
type Errors = Partial<Record<Field, string>>

const REQUIRED: Record<'label' | 'addressLine' | 'city' | 'department', string> = {
  label: 'Ponle un nombre a la dirección (por ejemplo, Casa).',
  addressLine: 'Escribe la dirección de entrega.',
  city: 'Escribe la ciudad.',
  department: 'Escribe el departamento.',
}
const fieldId = (field: Field) => `direccion-${field}`

/** El backend nombra los campos anidados `location.<campo>`; se aceptan ambas formas. */
function pickServerErrors(fieldErrors: Readonly<Record<string, string>>): Errors {
  const errors: Errors = {}
  for (const field of FIELDS) {
    const message = fieldErrors[`location.${field}`] ?? fieldErrors[field]
    if (message) errors[field] = message
  }
  return errors
}

function focusFirst(errors: Errors) {
  const first = FIELDS.find((field) => errors[field])
  if (first) document.getElementById(fieldId(first))?.focus()
}

interface AddressFormProps {
  /** Si no hay direcciones, la nueva queda como predeterminada. */
  makeDefault: boolean
  onCreated: (address: PersonAddressDto) => void
  onCancel: () => void
}

export function AddressForm({ makeDefault, onCreated, onCancel }: AddressFormProps) {
  const [errors, setErrors] = useState<Errors>({})
  const [formError, setFormError] = useState<string>()
  const submit = useSubmitOnce((input: PersonAddressRequestDto) => getAddressService().then((service) => service.create(input)))
  const submitting = submit.pending

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const read = (field: Field) => String(data.get(field) ?? '').trim()
    const next: Errors = {}
    for (const field of Object.keys(REQUIRED) as Array<keyof typeof REQUIRED>) {
      if (!read(field)) next[field] = REQUIRED[field]
    }
    setErrors(next)
    setFormError(undefined)
    if (Object.keys(next).length > 0) {
      focusFirst(next)
      return
    }
    const input: PersonAddressRequestDto = {
      label: read('label'),
      isDefault: makeDefault,
      location: {
        addressLine: read('addressLine'),
        addressComplement: read('addressComplement') || undefined,
        city: read('city'),
        department: read('department'),
        postalCode: read('postalCode') || undefined,
      },
    }
    // ÚNICA escritura de este formulario: la dirección se crea al pulsar «Guardar dirección».
    const result = await submit.run(input)
    if (result.status === 'ok') {
      onCreated(result.value)
    } else if (result.status === 'error') {
      const error = result.error
      const serverErrors = error instanceof ApiError ? pickServerErrors(error.fieldErrors) : {}
      setErrors(serverErrors)
      if (Object.keys(serverErrors).length > 0) focusFirst(serverErrors)
      else setFormError(error instanceof ApiError ? error.message : 'No pudimos guardar la dirección. Inténtalo de nuevo.')
    }
  }

  return (
    <form noValidate onSubmit={handleSubmit} aria-busy={submitting || undefined} className="flex flex-col gap-4 rounded-lg border border-border bg-concrete/50 p-4">
      <h3 className="text-base font-semibold">Agregar dirección</h3>
      {formError ? <FormAlert title={formError} /> : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id={fieldId('label')} name="label" label="Nombre de la dirección" placeholder="Casa, taller…" maxLength={100} required error={errors.label} containerClassName="sm:col-span-2" />
        <TextField
          id={fieldId('addressLine')}
          name="addressLine"
          label="Dirección"
          placeholder="Calle 10 # 20-30"
          autoComplete="street-address"
          maxLength={255}
          required
          error={errors.addressLine}
          containerClassName="sm:col-span-2"
        />
        <TextField id={fieldId('addressComplement')} name="addressComplement" label="Complemento" placeholder="Apto, torre, referencia" maxLength={255} optional error={errors.addressComplement} containerClassName="sm:col-span-2" />
        <TextField id={fieldId('city')} name="city" label="Ciudad" autoComplete="address-level2" maxLength={150} required error={errors.city} />
        <TextField id={fieldId('department')} name="department" label="Departamento" autoComplete="address-level1" maxLength={150} required error={errors.department} />
        <TextField id={fieldId('postalCode')} name="postalCode" label="Código postal" inputMode="numeric" autoComplete="postal-code" maxLength={20} optional error={errors.postalCode} />
      </div>
      <div className="flex flex-wrap gap-2">
        <Button type="submit" className="h-11 px-5" disabled={submitting}>
          {submitting ? 'Guardando…' : 'Guardar dirección'}
        </Button>
        <Button type="button" variant="outline" className="h-11 px-5" onClick={onCancel} disabled={submitting}>
          Cancelar
        </Button>
      </div>
    </form>
  )
}
