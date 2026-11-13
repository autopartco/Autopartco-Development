import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CheckCircle2Icon } from 'lucide-react'
import { FormAlert } from '@/components/common/form-fields'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { Button } from '@/components/ui/button'
import { AccountFields } from '@/features/auth/components/account-fields'
import { AuthShell } from '@/features/auth/components/auth-shell'
import { useSession } from '@/features/auth/hooks'
import { ACCOUNT_FIELDS, focusFirstError, readAccount, readPasswordConfirm, validateAccount } from '@/features/auth/lib/account'
import type { FieldErrors } from '@/features/auth/lib/validation'
import { getStaffService } from '@/features/portal/api/staff-service'
import { invalidateAsync } from '@/lib/use-async'
import { SELLER_CACHE_PREFIX } from '../hooks'
import type { StaffInvitationAccepted, StaffInvitationAccount, StaffInvitationPreview } from '@/features/portal/api/types'
import { RoleAccessSummary } from '@/features/portal/components/role-picker'
import { formatDate } from '@/features/portal/lib/labels'
import { ApiError } from '@/lib/api'
import { useFormTouched } from '@/lib/use-form-touched'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'

const PREFIX = 'invitacion-'

type Load = { status: 'loading' } | { status: 'ready'; preview: StaffInvitationPreview } | { status: 'error'; message: string }

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

/**
 * `/seller/invitation?token=`: enlace del correo de invitación al equipo de un almacén (`StaffInvitationMailer`).
 * Muestra la vista previa y acepta con `POST /staff-invitations/accept`; si el correo no tiene cuenta, la crea con
 * los datos del formulario.
 */
export default function StaffInvitationPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const session = useSession()
  const [load, setLoad] = useState<Load>({ status: 'loading' })
  const [accepted, setAccepted] = useState<StaffInvitationAccepted>()
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)
  const accept = useSubmitOnce(async (account: StaffInvitationAccount | undefined) => (await getStaffService()).acceptInvitation(token, account))
  const form = useFormTouched()
  const blocker = useUnsavedChanges(form.touched && !accept.pending && !accepted)

  useEffect(() => {
    if (!token) return
    let active = true
    getStaffService()
      .then((service) => service.previewInvitation(token))
      .then(
        (preview) => active && setLoad({ status: 'ready', preview }),
        (error: unknown) => active && setLoad({ status: 'error', message: errorMessage(error, 'No pudimos abrir la invitación. Inténtalo de nuevo.') }),
      )
    return () => {
      active = false
    }
  }, [token])

  const current: Load = token ? load : { status: 'error', message: 'El enlace no trae el código de la invitación.' }

  async function handleSubmit(event: FormEvent<HTMLFormElement>, preview: StaffInvitationPreview) {
    event.preventDefault()
    setFormError(undefined)
    let account: StaffInvitationAccount | undefined
    if (preview.accountCreationRequired) {
      const data = new FormData(event.currentTarget)
      const read = { ...readAccount(data, PREFIX), email: preview.email }
      const next = validateAccount(read, readPasswordConfirm(data, PREFIX))
      setErrors(next)
      if (Object.keys(next).length > 0) {
        focusFirstError(ACCOUNT_FIELDS, next, (field) => `${PREFIX}${field}`)
        return
      }
      account = {
        name: read.name,
        surname: read.surname,
        identification: read.identification,
        dateBirth: read.dateBirth,
        numberPhone: read.numberPhone,
        password: read.password,
      }
    }
    // ÚNICA escritura: el token es de un solo uso y se consume al pulsar «Aceptar».
    const result = await accept.run(account)
    if (result.status === 'ok') {
      // El menú y `/portal` deben ver el almacén nuevo sin recargar la página.
      invalidateAsync(SELLER_CACHE_PREFIX)
      invalidateAsync('portal:stores')
      setAccepted(result.value)
    } else if (result.status === 'error') {
      const error = result.error
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        const serverErrors = Object.fromEntries(Object.entries(error.fieldErrors).map(([key, value]) => [key.replace(/^account\./, ''), value]))
        setErrors(serverErrors)
        focusFirstError(ACCOUNT_FIELDS, serverErrors, (field) => `${PREFIX}${field}`)
      }
      setFormError(errorMessage(error, 'No pudimos aceptar la invitación. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  const portalPath = accepted ? `/portal/${encodeURIComponent(accepted.storeId)}` : '/portal'
  const signedInAs = session?.user.email.toLowerCase()

  return (
    <AuthShell
      width={current.status === 'ready' && current.preview.accountCreationRequired && !accepted ? 'wide' : 'narrow'}
      crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Invitación al equipo' }]}
      label="Equipo de almacén"
      title={accepted ? 'Ya eres parte del equipo' : current.status === 'ready' ? `Únete a ${current.preview.storeName}` : 'Invitación al equipo'}
    >
      <title>Invitación al equipo | Autopartco</title>
      {accepted ? (
        <div role="status" className="flex flex-col items-start gap-4">
          <p className="flex items-start gap-2 text-lg font-semibold">
            <CheckCircle2Icon className="mt-1 size-5 shrink-0 text-ap-success" aria-hidden />
            Entraste a {accepted.storeName} como {accepted.roleName}.
          </p>
          {signedInAs === accepted.email.toLowerCase() ? (
            <Button nativeButton={false} render={<Link to={portalPath} />}>
              Ir al portal del almacén
            </Button>
          ) : (
            <>
              <p className="text-ap-muted">Ingresa con {accepted.email} para empezar a trabajar en el portal.</p>
              <Button nativeButton={false} render={<Link to={`/ingresar?next=${encodeURIComponent(portalPath)}`} />}>
                Ingresar
              </Button>
            </>
          )}
        </div>
      ) : current.status === 'loading' ? (
        <p role="status" className="text-ap-muted">
          Abriendo la invitación…
        </p>
      ) : current.status === 'error' ? (
        <div className="flex flex-col items-start gap-4">
          <FormAlert title={current.message}>
            <p>Si ya la aceptaste, solo ingresa. Si venció, pide al administrador del almacén que te la reenvíe.</p>
          </FormAlert>
          <Button variant="outline" nativeButton={false} render={<Link to="/ingresar" />}>
            Ir a ingresar
          </Button>
        </div>
      ) : (
        <form noValidate onSubmit={(event) => void handleSubmit(event, current.preview)} {...form.formProps} className="flex flex-col gap-6" aria-busy={accept.pending || undefined}>
          {formError ? <FormAlert ref={alertRef} title={formError} /> : null}
          <div className="flex flex-col gap-1">
            <p>
              Te invitaron con el correo <span className="font-semibold">{current.preview.email}</span> y el rol{' '}
              <span className="font-semibold">{current.preview.roleName}</span>.
            </p>
            <p className="text-sm text-ap-muted">La invitación vence el {formatDate(current.preview.expiresAt)}.</p>
          </div>
          <section aria-label="Qué podrás hacer" className="flex flex-col gap-1.5">
            <p className="text-sm font-semibold">Qué podrás hacer</p>
            <RoleAccessSummary permissionCodes={current.preview.permissionCodes} />
          </section>
          {current.preview.accountCreationRequired ? (
            <>
              <p className="text-sm text-ap-muted">Aún no tienes cuenta en Autopartco. Completa tus datos para crearla; todos los campos son obligatorios.</p>
              <AccountFields prefix={PREFIX} errors={errors} fixedEmail={current.preview.email} />
            </>
          ) : signedInAs && signedInAs !== current.preview.email.toLowerCase() ? (
            <p role="note" className="text-sm text-ap-muted">
              Tienes la sesión abierta con {signedInAs}. La invitación se aplicará a {current.preview.email}.
            </p>
          ) : null}
          <div>
            <Button type="submit" disabled={accept.pending}>
              {accept.pending ? 'Aceptando…' : current.preview.accountCreationRequired ? 'Crear cuenta y aceptar' : 'Aceptar invitación'}
            </Button>
          </div>
          <UnsavedChangesDialog blocker={blocker} />
        </form>
      )}
    </AuthShell>
  )
}
