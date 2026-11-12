import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { ApiError } from '@/lib/api'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { AccountSection } from '../account-layout'
import { getProfile, updateProfile, type Profile } from '../api'

const FIELDS = ['name', 'surname', 'identification', 'dateBirth', 'numberPhone'] as const
type Field = (typeof FIELDS)[number]

function validate(values: Record<Field, string>): Partial<Record<Field, string>> {
  const errors: Partial<Record<Field, string>> = {}
  if (!values.name) errors.name = 'Escribe tu nombre.'
  if (!values.surname) errors.surname = 'Escribe tu apellido.'
  if (!values.identification) errors.identification = 'Escribe tu número de documento.'
  if (!values.dateBirth) errors.dateBirth = 'Elige tu fecha de nacimiento.'
  else if (values.dateBirth >= new Date().toISOString().slice(0, 10)) errors.dateBirth = 'Debe ser una fecha pasada.'
  if (!/^\d{7,10}$/.test(values.numberPhone)) errors.numberPhone = 'Escribe un teléfono de 7 a 10 dígitos.'
  return errors
}

function ProfileForm({ profile }: { profile: Profile }) {
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [formError, setFormError] = useState<string>()
  const save = useSubmitOnce(updateProfile)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const values = Object.fromEntries(FIELDS.map((field) => [field, String(data.get(field) ?? '').trim()])) as Record<Field, string>
    const found = validate(values)
    setErrors(found)
    setFormError(undefined)
    const first = FIELDS.find((field) => found[field])
    if (first) {
      document.getElementById(`perfil-${first}`)?.focus()
      return
    }
    const changed = Object.fromEntries(FIELDS.filter((field) => values[field] !== profile[field]).map((field) => [field, values[field]]))
    if (Object.keys(changed).length === 0) {
      toast.info('No hay cambios para guardar.')
      return
    }
    const result = await save.run(changed)
    if (result.status === 'ok') {
      invalidateAsync('portal:account:')
      toast.success('Guardamos tus datos.')
    } else if (result.status === 'error') {
      const failure = result.error
      if (failure instanceof ApiError) setErrors(failure.fieldErrors as Partial<Record<Field, string>>)
      setFormError(failure instanceof ApiError ? failure.message : 'No pudimos guardar. Inténtalo de nuevo.')
    }
  }

  return (
    <form noValidate onSubmit={(event) => void submit(event)} className="grid max-w-2xl gap-4 sm:grid-cols-2" aria-busy={save.pending || undefined}>
      {formError ? (
        <div className="sm:col-span-2">
          <FormAlert title="No se guardaron los cambios">{formError}</FormAlert>
        </div>
      ) : null}
      <TextField id="perfil-name" name="name" label="Nombre" defaultValue={profile.name} maxLength={100} autoComplete="given-name" required error={errors.name} />
      <TextField id="perfil-surname" name="surname" label="Apellido" defaultValue={profile.surname} maxLength={100} autoComplete="family-name" required error={errors.surname} />
      <TextField id="perfil-identification" name="identification" label="Número de documento" defaultValue={profile.identification} maxLength={20} inputMode="numeric" required error={errors.identification} />
      <TextField id="perfil-dateBirth" name="dateBirth" type="date" label="Fecha de nacimiento" defaultValue={profile.dateBirth} autoComplete="bday" required error={errors.dateBirth} />
      <TextField id="perfil-numberPhone" name="numberPhone" label="Teléfono" defaultValue={profile.numberPhone} maxLength={10} inputMode="tel" autoComplete="tel-national" required error={errors.numberPhone} />
      <div className="flex items-end sm:col-span-2">
        <Button type="submit" className="h-11 px-5" disabled={save.pending}>
          {save.pending ? 'Guardando…' : 'Guardar cambios'}
        </Button>
      </div>
    </form>
  )
}

/** `/cuenta/perfil`: datos de la persona titular. El correo se cambia en Seguridad. */
export default function AccountProfilePage() {
  const profile = useAsync('portal:account:profile', getProfile)
  return (
    <AccountSection id="perfil-datos" title="Datos personales" description="Los usamos en tus pedidos y facturas. El correo se cambia en Seguridad.">
      {profile.status === 'error' ? (
        <ErrorState title="No pudimos cargar tus datos" onRetry={profile.retry} />
      ) : !profile.data ? (
        <div className="grid max-w-2xl gap-4 sm:grid-cols-2" aria-hidden>
          {[0, 1, 2, 3, 4].map((key) => (
            <Skeleton key={key} className="h-16" />
          ))}
        </div>
      ) : (
        <ProfileForm key={profile.data.accountId} profile={profile.data} />
      )}
    </AccountSection>
  )
}
