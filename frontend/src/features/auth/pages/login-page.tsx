import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate, useSearchParams } from 'react-router'
import { FormAlert, PasswordField, TextField } from '@/components/common/form-fields'
import { focusFirstError } from '../lib/account'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { getSellerApplicationService } from '@/features/seller/api/seller-service'
import { SELLER_SETUP_PATH } from '@/features/seller/hooks'
import { getAuthService } from '../api/auth-service'
import { accountKindOf } from '../lib/account-menu-items'
import { AuthShell, SimulatedNotice } from '../components/auth-shell'
import { useSession } from '../hooks'
import { compact, emailError, type FieldErrors } from '../lib/validation'
import { safeReturnPath } from '../lib/return-path'
import { setSession } from '../session-store'

/**
 * Un cliente con solicitud aprobada pendiente de configurar va a terminarla, salvo que venga de una
 * página concreta que no sea el portal. Si la consulta falla, sigue al destino normal.
 */
async function destinationAfterLogin(roles: readonly string[], returnTo: string): Promise<string> {
  if (accountKindOf(roles) !== 'customer') return returnTo
  if (returnTo !== '/' && !returnTo.startsWith('/portal')) return returnTo
  try {
    const applications = await (await getSellerApplicationService()).myApplications()
    if (applications.some((application) => application.status === 'CONFIGURATION_PENDING')) return SELLER_SETUP_PATH
  } catch {
    // Sin solicitudes a la vista no se cambia el destino.
  }
  return returnTo
}

const FIELDS = ['email', 'password'] as const
const fieldId = (field: string) => `ingreso-${field}`

export default function LoginPage() {
  const session = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const [searchParams] = useSearchParams()
  // Destino de vuelta: `?next=` (guardas de ruta) o `state.from`; solo rutas internas.
  const returnTo = safeReturnPath(searchParams.get('next') ?? (location.state as { from?: unknown } | null)?.from)
  const hasNext = searchParams.has('next') && returnTo !== '/'
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<{ title: string; credentials?: boolean }>()
  const [submitting, setSubmitting] = useState(false)
  const alertRef = useRef<HTMLDivElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const email = String(data.get('email') ?? '')
    const password = String(data.get('password') ?? '')
    const next = compact({
      email: emailError(email),
      password: password ? undefined : 'La contraseña es obligatoria.',
    })
    setErrors(next)
    setFormError(undefined)
    if (Object.keys(next).length > 0) {
      focusFirstError(FIELDS, next, fieldId)
      return
    }
    setSubmitting(true)
    try {
      const service = await getAuthService()
      const loggedIn = await service.login({ email, password })
      setSession(loggedIn)
      navigate(await destinationAfterLogin(loggedIn.roles, returnTo), { replace: true })
    } catch (error) {
      // El backend responde lo mismo si la contraseña no coincide, si la cuenta no está
      // verificada o si está bloqueada (no revela cuál): el mensaje cubre los tres casos.
      if (error instanceof ApiError && error.code === 'UNAUTHORIZED') {
        setFormError({ title: 'No pudimos ingresar con ese correo y esa contraseña.', credentials: true })
      } else {
        setFormError({ title: error instanceof ApiError ? error.message : 'No pudimos ingresar. Inténtalo de nuevo.' })
        if (error instanceof ApiError) setErrors(error.fieldErrors)
      }
      requestAnimationFrame(() => alertRef.current?.focus())
    } finally {
      setSubmitting(false)
    }
  }

  const crumbs = [{ label: 'Inicio', to: '/' }, { label: 'Ingresar' }]

  // Con sesión activa no hay nada que hacer aquí: seguimos al destino (guarda de ruta o inicio).
  if (session) return <Navigate to={returnTo} replace />


  return (
    <AuthShell
      crumbs={crumbs}
      label="Cuenta Autopartco"
      title="Ingresar"
      description={
        <p>
          {hasNext
            ? 'Ingresa para continuar donde ibas. Usa el correo y la contraseña de tu cuenta.'
            : 'Entra con el correo y la contraseña de tu cuenta de cliente o de almacén.'}
        </p>
      }
    >
      <title>Ingresar | Autopartco</title>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-5" aria-busy={submitting || undefined}>
        {formError ? (
          <FormAlert ref={alertRef} title={formError.title}>
            {formError.credentials ? (
              <ul className="mt-1 list-disc pl-5">
                <li>Revisa que el correo y la contraseña estén bien escritos.</li>
                <li>Si acabas de crear la cuenta, primero abre el enlace que te enviamos al correo.</li>
                <li>Tras varios intentos fallidos la cuenta se bloquea un rato por seguridad.</li>
              </ul>
            ) : null}
          </FormAlert>
        ) : null}
        <TextField
          id={fieldId('email')}
          name="email"
          type="email"
          label="Correo"
          autoComplete="email"
          inputMode="email"
          maxLength={100}
          required
          error={errors.email}
        />
        <div className="flex flex-col gap-1">
          <PasswordField
            id={fieldId('password')}
            name="password"
            label="Contraseña"
            autoComplete="current-password"
            maxLength={72}
            required
            error={errors.password}
          />
          <Link
            to="/recuperar-contrasena"
            className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-brand underline-offset-4 hover:underline"
          >
            ¿Olvidaste tu contraseña?
          </Link>
        </div>
        <Button type="submit" className="h-11 text-base" disabled={submitting}>
          {submitting ? 'Ingresando…' : 'Ingresar'}
        </Button>
        <p className="text-sm text-muted-foreground">
          ¿Aún no tienes cuenta?{' '}
          <Link to="/registro" className="font-medium text-brand underline-offset-4 hover:underline">
            Crear cuenta
          </Link>
        </p>
        <SimulatedNotice />
      </form>
    </AuthShell>
  )
}
