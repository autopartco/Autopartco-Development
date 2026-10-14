import { useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { FormAlert, PasswordField, TextField } from '@/components/common/form-fields'
import { focusFirstError } from '../lib/account'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { getAuthService } from '../api/auth-service'
import { AuthShell, SimulatedNotice } from '../components/auth-shell'
import { useSession, useSignOut } from '../hooks'
import { compact, emailError, type FieldErrors } from '../lib/validation'
import { setSession } from '../session-store'

const FIELDS = ['email', 'password'] as const
const fieldId = (field: string) => `ingreso-${field}`

/** Solo rutas internas como destino de vuelta (evita redirecciones abiertas). */
function safeReturnPath(value: unknown): string {
  return typeof value === 'string' && value.startsWith('/') && !value.startsWith('//') ? value : '/'
}

export default function LoginPage() {
  const session = useSession()
  const navigate = useNavigate()
  const location = useLocation()
  const { signOut, pending: signingOut } = useSignOut()
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
      setSession(await service.login({ email, password }))
      navigate(safeReturnPath((location.state as { from?: unknown } | null)?.from), { replace: true })
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

  if (session) {
    return (
      <AuthShell crumbs={crumbs} label="Tu cuenta" title="Ya ingresaste">
        <div className="flex flex-col items-start gap-4">
          <p>
            Estás usando Autopartco como <strong className="font-semibold">{session.user.name}</strong> ({session.user.email}).
          </p>
          <div className="flex flex-wrap gap-2">
            <Button className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
              Ir al inicio
            </Button>
            <Button variant="outline" className="h-11 px-5" onClick={signOut} disabled={signingOut}>
              {signingOut ? 'Cerrando sesión…' : 'Cerrar sesión'}
            </Button>
          </div>
        </div>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      crumbs={crumbs}
      label="Cuenta Autopartco"
      title="Ingresar"
      description={<p>Entra con el correo y la contraseña de tu cuenta de cliente o de almacén.</p>}
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
