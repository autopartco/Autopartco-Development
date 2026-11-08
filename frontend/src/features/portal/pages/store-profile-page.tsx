import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { ArrowUpRightIcon } from 'lucide-react'
import { toast } from 'sonner'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { FormSection } from '@/components/common/form-section'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { invalidateAsync } from '@/lib/use-async'
import { useDraftForm, type FieldErrors } from '@/lib/use-draft-form'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import { getPortalService } from '../api/portal-service'
import type { StoreContact, StoreProfile, StoreProfileUpdate } from '../api/types'
import { BrandUploader } from '../components/brand-uploader'
import { TextAreaField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { EMAIL_PATTERN, focusById } from '../lib/forms'

const CONTACT_FIELDS = ['whatsapp', 'phone', 'website', 'instagram', 'facebook', 'tiktok'] as const
type ContactField = (typeof CONTACT_FIELDS)[number]
const FIELDS = ['businessName', 'businessEmail', 'description', ...CONTACT_FIELDS] as const
type Field = (typeof FIELDS)[number]
const fieldId = (field: Field) => `tienda-${field}`
const DESCRIPTION_MAX = 1000

type Values = Record<Field, string>

/** En el formulario los teléfonos van sin indicativo (10 dígitos); el servidor los guarda como `+57…`. */
const PHONE_FIELDS: ContactField[] = ['whatsapp', 'phone']
const toLocalPhone = (value: string | null | undefined) => (value ?? '').replace(/^\+57/, '')
const cleanUser = (value: string) => value.trim().replace(/^@/, '')

/** Valor que se envía al servidor para un campo de contacto ya validado. */
function contactValue(field: ContactField, raw: string): string {
  const value = raw.trim()
  if (!value) return ''
  if (PHONE_FIELDS.includes(field)) return `+57${value.replace(/\D/g, '')}`
  if (field === 'website') return value
  return cleanUser(value)
}

function storedContact(contact: StoreContact | undefined, field: ContactField): string {
  const value = contact?.[field] ?? ''
  return PHONE_FIELDS.includes(field) ? toLocalPhone(value) : value
}

function validate(values: Values): FieldErrors {
  const errors: FieldErrors = {}
  if (!values.businessName.trim()) errors.businessName = 'El nombre del almacén no puede estar vacío.'
  else if (values.businessName.trim().length > 255) errors.businessName = 'El nombre no puede superar 255 caracteres.'
  if (!values.businessEmail.trim()) errors.businessEmail = 'El correo del almacén no puede estar vacío.'
  else if (!EMAIL_PATTERN.test(values.businessEmail.trim())) errors.businessEmail = 'El correo debe tener un formato válido, por ejemplo ventas@tualmacen.co.'
  else if (values.businessEmail.trim().length > 200) errors.businessEmail = 'El correo no puede superar 200 caracteres.'
  if (values.description.length > DESCRIPTION_MAX) errors.description = `La descripción no puede superar ${DESCRIPTION_MAX} caracteres.`
  for (const field of PHONE_FIELDS) {
    const digits = values[field].replace(/[\s-]/g, '')
    if (digits && !/^3\d{9}$|^60\d{8}$/.test(digits)) errors[field] = 'Escribe un número colombiano de 10 dígitos, por ejemplo 3001234567.'
  }
  const website = values.website.trim()
  if (website && !/^https:\/\/[^\s/$.?#][^\s]*$/.test(website)) errors.website = 'El sitio web debe empezar por https://, por ejemplo https://tualmacen.co.'
  else if (website.length > 200) errors.website = 'El sitio web no puede superar 200 caracteres.'
  for (const field of ['instagram', 'tiktok'] as const) {
    const user = cleanUser(values[field])
    if (user && !/^[A-Za-z0-9._]{1,30}$/.test(user)) errors[field] = 'Escribe solo el usuario (letras, números, punto o guion bajo), sin el enlace.'
  }
  const facebook = cleanUser(values.facebook)
  if (facebook && !/^[A-Za-z0-9.]{1,50}$/.test(facebook)) errors.facebook = 'Escribe solo el usuario de la página (lo que va después de facebook.com/).'
  return errors
}

function ProfileForm({ profile }: { profile: StoreProfile }) {
  const { storeId, can, invalidateStore } = usePortal()
  const editable = can('STORE_UPDATE')
  const draft = useDraftForm<Values>({
    initial: {
      businessName: profile.businessName,
      businessEmail: profile.businessEmail,
      description: profile.description ?? '',
      ...(Object.fromEntries(CONTACT_FIELDS.map((field) => [field, storedContact(profile.publicProfile, field)])) as Record<ContactField, string>),
    },
    validate,
  })
  const { values } = draft
  const submit = useSubmitOnce(async (input: StoreProfileUpdate) => (await getPortalService()).updateProfile(storeId, input))
  const blocker = useUnsavedChanges(draft.isDirty && !submit.pending)
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)

  const dirty = draft.isDirty

  const bind = (field: Field) => ({
    id: fieldId(field),
    value: values[field],
    error: draft.errorFor(field),
    readOnly: !editable,
    onBlur: () => draft.touch(field),
  })
  function change(field: Field, value: string) {
    draft.set(field, value)
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editable) return
    setFormError(undefined)
    const check = draft.validateAll()
    if (!check.valid) {
      focusById(check.firstInvalid ? fieldId(check.firstInvalid as Field) : undefined)
      return
    }
    const input: StoreProfileUpdate = {}
    if (values.businessName.trim() !== profile.businessName) input.businessName = values.businessName.trim()
    if (values.businessEmail.trim() !== profile.businessEmail) input.businessEmail = values.businessEmail.trim()
    if (values.description.trim() !== (profile.description ?? '')) input.description = values.description.trim()
    const contact: NonNullable<StoreProfileUpdate['publicProfile']> = {}
    for (const field of CONTACT_FIELDS) {
      const next = contactValue(field, values[field])
      if (next !== (profile.publicProfile?.[field] ?? '')) contact[field] = next
    }
    if (Object.keys(contact).length > 0) input.publicProfile = contact
    if (Object.keys(input).length === 0) {
      toast.info('No hay cambios para guardar.')
      return
    }
    // ÚNICA escritura: un PATCH al pulsar «Guardar cambios».
    const result = await submit.run(input)
    if (result.status === 'ok') {
      draft.markSaved()
      invalidateStore()
      // La lista de tiendas (selector de la barra superior) también muestra el nombre.
      invalidateAsync('portal:stores')
      toast.success('Guardamos los datos de tu tienda.')
    } else if (result.status === 'error') {
      const error = result.error
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        // En el contacto el servidor nombra los campos como `publicProfile.whatsapp`.
        const serverErrors = Object.fromEntries(Object.entries(error.fieldErrors).map(([key, value]) => [key.replace(/^publicProfile\./, ''), value]))
        draft.setServerErrors(serverErrors)
        const target = FIELDS.find((field) => serverErrors[field])
        if (target) requestAnimationFrame(() => focusById(fieldId(target)))
      }
      setFormError(actionErrorMessage(error, 'No pudimos guardar los datos de la tienda. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex max-w-2xl flex-col gap-5" aria-busy={submit.pending || undefined}>
      {!editable ? (
        <p role="note" className="border-l-2 border-ap-line-strong py-1 pl-3 text-sm text-ap-ink">
          Estás en modo de solo lectura: tu rol no puede editar los datos de la tienda.
        </p>
      ) : null}
      {formError ? <FormAlert ref={alertRef} title="No se guardaron los cambios">{formError}</FormAlert> : null}
      <FormSection
        title="Identificación"
        description="Cómo aparece tu almacén en el marketplace."
        aside={<StatusBadge tone={profile.state === 'ACTIVE' ? 'success' : 'warning'}>{profile.state === 'ACTIVE' ? 'Tienda activa' : 'Tienda pendiente'}</StatusBadge>}
        className="border-t border-ap-line-strong pt-4"
      >
        <TextField {...bind('businessName')} label="Nombre del almacén" maxLength={255} autoComplete="organization" required onChange={(event) => change('businessName', event.target.value)} />
        <TextField
          id="tienda-nit"
          label="NIT"
          value={profile.nit}
          readOnly
          className="font-mono"
          hint="El NIT no se puede cambiar desde el portal porque identifica legalmente al almacén. Si tiene un error, escríbenos."
        />
      </FormSection>
      <FormSection title="Contacto y presentación" description="Lo que leen los compradores y por dónde te escribimos." className="border-t border-ap-line-strong pt-4">
        <TextField {...bind('businessEmail')} label="Correo del almacén" type="email" inputMode="email" maxLength={200} autoComplete="off" required hint="Aquí te escribimos sobre pedidos y tu suscripción." onChange={(event) => change('businessEmail', event.target.value)} />
        <TextAreaField
          {...bind('description')}
          label="Descripción"
          optional
          maxLength={DESCRIPTION_MAX + 100}
          className="min-h-32"
          hint={`${values.description.length} de ${DESCRIPTION_MAX} caracteres. Cuéntales a los compradores qué vendes y desde cuándo.`}
          onChange={(event) => change('description', event.target.value)}
        />
      </FormSection>
      <FormSection
        title="Imagen de marca"
        description="El logo aparece en el directorio de tiendas, en tus publicaciones y en tu perfil; el banner, arriba de tu perfil."
        className="border-t border-ap-line-strong pt-4"
      >
        <BrandUploader asset="logo" url={profile.logoUrl} editable={editable} published={profile.state === 'ACTIVE'} />
        <BrandUploader asset="banner" url={profile.bannerUrl} editable={editable} published={profile.state === 'ACTIVE'} />
      </FormSection>
      <FormSection
        title="Contacto público"
        description="Se muestra en tu perfil para que los compradores te escriban. Todos son opcionales: deja vacío lo que no quieras publicar."
        className="border-t border-ap-line-strong pt-4"
      >
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField {...bind('whatsapp')} label="WhatsApp" optional type="tel" inputMode="numeric" maxLength={13} autoComplete="off" placeholder="3001234567" hint="10 dígitos, sin +57." onChange={(event) => change('whatsapp', event.target.value)} />
          <TextField {...bind('phone')} label="Teléfono" optional type="tel" inputMode="numeric" maxLength={13} autoComplete="off" placeholder="6015551234" hint="Fijo (60…) o celular, 10 dígitos." onChange={(event) => change('phone', event.target.value)} />
        </div>
        <TextField {...bind('website')} label="Sitio web" optional type="url" inputMode="url" maxLength={200} autoComplete="off" placeholder="https://tualmacen.co" onChange={(event) => change('website', event.target.value)} />
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField {...bind('instagram')} label="Instagram" optional maxLength={31} autoComplete="off" placeholder="tualmacen" hint="Solo el usuario." onChange={(event) => change('instagram', event.target.value)} />
          <TextField {...bind('facebook')} label="Facebook" optional maxLength={51} autoComplete="off" placeholder="tualmacen" hint="Lo que va tras facebook.com/." onChange={(event) => change('facebook', event.target.value)} />
          <TextField {...bind('tiktok')} label="TikTok" optional maxLength={31} autoComplete="off" placeholder="tualmacen" hint="Solo el usuario." onChange={(event) => change('tiktok', event.target.value)} />
        </div>
      </FormSection>
      {editable ? (
        <div className="sticky bottom-0 z-10 -mx-4 flex flex-wrap items-center gap-3 border-t border-ap-line bg-ap-white px-4 py-3 sm:static sm:mx-0 sm:border-0 sm:bg-transparent sm:p-0">
          <Button type="submit" className="h-11 px-6" disabled={submit.pending || !dirty}>
            {submit.pending ? 'Guardando…' : 'Guardar cambios'}
          </Button>
          <p role="status" className="text-sm text-ap-muted">
            {dirty ? 'Tienes cambios sin guardar.' : 'Sin cambios por guardar.'}
          </p>
        </div>
      ) : null}
      <UnsavedChangesDialog blocker={blocker} />
    </form>
  )
}

/** `/portal/:storeId/tienda`: perfil del almacén; el NIT es de solo lectura. */
export default function PortalStorePage() {
  const profile = useStoreQuery('profile', async (id) => (await getPortalService()).profile(id))
  return (
    <>
      <title>Tienda | Autopartco</title>
      <PageHeader
        title="Tienda"
        description="Los datos con los que te ven los compradores."
        actions={
          profile.data?.state === 'ACTIVE' ? (
            <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`/tiendas/${encodeURIComponent(profile.data.id)}`} target="_blank" rel="noopener" />}>
              Ver mi perfil público
              <ArrowUpRightIcon aria-hidden />
            </Button>
          ) : undefined
        }
      />
      {/* `keepPrevious`: al subir el logo o el banner se recarga el perfil sin desmontar el formulario. */}
      <QueryBoundary result={profile} what="los datos de tu tienda" keepPrevious skeleton={<ListSkeleton rows={4} label="Cargando los datos de tu tienda" />}>
        {(data) => <ProfileForm key={data.id} profile={data} />}
      </QueryBoundary>
    </>
  )
}
