import { useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { invalidateAsync } from '@/lib/use-async'
import { getPortalService } from '../api/portal-service'
import type { StoreProfile, StoreProfileUpdate } from '../api/types'
import { TextAreaField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { EMAIL_PATTERN, focusById, type FieldErrors } from '../lib/forms'

const FIELDS = ['businessName', 'businessEmail', 'description'] as const
type Field = (typeof FIELDS)[number]
const fieldId = (field: Field) => `tienda-${field}`
const DESCRIPTION_MAX = 1000

function ProfileForm({ profile }: { profile: StoreProfile }) {
  const { storeId, can, invalidateStore } = usePortal()
  const editable = can('STORE_UPDATE')
  const [values, setValues] = useState({
    businessName: profile.businessName,
    businessEmail: profile.businessEmail,
    description: profile.description ?? '',
  })
  const [touched, setTouched] = useState<Partial<Record<Field, boolean>>>({})
  const [submitted, setSubmitted] = useState(false)
  const [serverErrors, setServerErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [pending, setPending] = useState(false)
  const alertRef = useRef<HTMLDivElement>(null)

  const errors: FieldErrors = {}
  if (!values.businessName.trim()) errors.businessName = 'El nombre del almacén no puede estar vacío.'
  else if (values.businessName.trim().length > 255) errors.businessName = 'El nombre no puede superar 255 caracteres.'
  if (!values.businessEmail.trim()) errors.businessEmail = 'El correo del almacén no puede estar vacío.'
  else if (!EMAIL_PATTERN.test(values.businessEmail.trim())) errors.businessEmail = 'El correo debe tener un formato válido, por ejemplo ventas@tualmacen.co.'
  else if (values.businessEmail.trim().length > 200) errors.businessEmail = 'El correo no puede superar 200 caracteres.'
  if (values.description.length > DESCRIPTION_MAX) errors.description = `La descripción no puede superar ${DESCRIPTION_MAX} caracteres.`

  const shown = (field: Field) => serverErrors[field] ?? (submitted || touched[field] ? errors[field] : undefined)
  const bind = (field: Field) => ({
    id: fieldId(field),
    value: values[field],
    error: shown(field),
    readOnly: !editable,
    onBlur: () => setTouched((current) => ({ ...current, [field]: true })),
  })
  function change(field: Field, value: string) {
    setValues((current) => ({ ...current, [field]: value }))
    if (serverErrors[field]) setServerErrors(({ [field]: _removed, ...rest }) => rest)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending || !editable) return
    setSubmitted(true)
    setFormError(undefined)
    const first = FIELDS.find((field) => errors[field])
    if (first) {
      focusById(fieldId(first))
      return
    }
    const input: StoreProfileUpdate = {}
    if (values.businessName.trim() !== profile.businessName) input.businessName = values.businessName.trim()
    if (values.businessEmail.trim() !== profile.businessEmail) input.businessEmail = values.businessEmail.trim()
    if (values.description.trim() !== (profile.description ?? '')) input.description = values.description.trim()
    if (Object.keys(input).length === 0) {
      toast.info('No hay cambios para guardar.')
      return
    }
    setPending(true)
    try {
      await (await getPortalService()).updateProfile(storeId, input)
      invalidateStore()
      // La lista de tiendas (selector de la barra superior) también muestra el nombre.
      invalidateAsync('portal:stores')
      toast.success('Guardamos los datos de tu tienda.')
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        setServerErrors(error.fieldErrors)
        const target = FIELDS.find((field) => error.fieldErrors[field])
        if (target) requestAnimationFrame(() => focusById(fieldId(target)))
      }
      setFormError(actionErrorMessage(error, 'No pudimos guardar los datos de la tienda. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    } finally {
      setPending(false)
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex max-w-2xl flex-col gap-5" aria-busy={pending || undefined}>
      {!editable ? (
        <p role="note" className="rounded-lg bg-concrete p-3 text-sm">
          Estás en modo de solo lectura: tu rol no puede editar los datos de la tienda.
        </p>
      ) : null}
      {formError ? <FormAlert ref={alertRef} title="No se guardaron los cambios">{formError}</FormAlert> : null}
      <TextField {...bind('businessName')} label="Nombre del almacén" maxLength={255} autoComplete="organization" required onChange={(event) => change('businessName', event.target.value)} />
      <TextField {...bind('businessEmail')} label="Correo del almacén" type="email" inputMode="email" maxLength={200} autoComplete="off" required hint="Aquí te escribimos sobre pedidos y tu suscripción." onChange={(event) => change('businessEmail', event.target.value)} />
      <TextField
        id="tienda-nit"
        label="NIT"
        value={profile.nit}
        readOnly
        hint="El NIT no se puede cambiar desde el portal porque identifica legalmente al almacén. Si tiene un error, escríbenos."
      />
      <TextAreaField
        {...bind('description')}
        label="Descripción"
        optional
        maxLength={DESCRIPTION_MAX + 100}
        className="min-h-32"
        hint={`${values.description.length} de ${DESCRIPTION_MAX} caracteres. Cuéntales a los compradores qué vendes y desde cuándo.`}
        onChange={(event) => change('description', event.target.value)}
      />
      <div className="flex items-center gap-3">
        <span className="text-sm text-muted-foreground">Estado:</span>
        <StatusBadge tone={profile.state === 'ACTIVE' ? 'success' : 'warning'}>{profile.state === 'ACTIVE' ? 'Activa' : 'Pendiente'}</StatusBadge>
      </div>
      {editable ? (
        <Button type="submit" className="h-11 w-fit px-6" disabled={pending}>
          {pending ? 'Guardando…' : 'Guardar cambios'}
        </Button>
      ) : null}
    </form>
  )
}

/** `/portal/:storeId/tienda`: perfil del almacén; el NIT es de solo lectura. */
export default function PortalStorePage() {
  const profile = useStoreQuery('profile', async (id) => (await getPortalService()).profile(id))
  return (
    <>
      <title>Tienda | Autopartco</title>
      <PageHeader title="Tienda" description="Los datos con los que te ven los compradores." />
      <QueryBoundary result={profile} what="los datos de tu tienda" skeleton={<ListSkeleton rows={4} label="Cargando los datos de tu tienda" />}>
        {(data) => <ProfileForm key={data.id} profile={data} />}
      </QueryBoundary>
    </>
  )
}
