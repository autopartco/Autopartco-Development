import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { FormAlert } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { getAuthService } from '../api/auth-service'
import type { RegistrationResult } from '../api/types'
import { AccountFields } from '../components/account-fields'
import { ACCOUNT_FIELDS, focusFirstError, readAccount, readPasswordConfirm, validateAccount } from '../lib/account'
import { AuthShell, SimulatedNotice } from '../components/auth-shell'
import { CheckEmail } from '../components/check-email'
import type { FieldErrors } from '../lib/validation'

const PREFIX = 'cliente-'

/** Errores de negocio de `RegistrationService` que corresponden a un campo. */
const FIELD_BY_CODE: Record<string, string> = {
  EMAIL_ALREADY_EXISTS: 'email',
  IDENTIFICATION_ALREADY_EXISTS: 'identification',
}

/** `/registro/cliente`: `POST /auth/register` con los campos de `RegisterRequest`. */
export default function CustomerRegisterPage() {
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<RegistrationResult>()
  const alertRef = useRef<HTMLDivElement>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const account = readAccount(data, PREFIX)
    const next = validateAccount(account, readPasswordConfirm(data, PREFIX))
    setErrors(next)
    setFormError(undefined)
    if (Object.keys(next).length > 0) {
      focusFirstError(ACCOUNT_FIELDS, next, (field) => `${PREFIX}${field}`)
      return
    }
    setSubmitting(true)
    try {
      const service = await getAuthService()
      setResult(await service.register(account))
    } catch (error) {
      if (error instanceof ApiError) {
        const field = error.code ? FIELD_BY_CODE[error.code] : undefined
        const serverErrors = field ? { [field]: error.message } : error.fieldErrors
        setErrors(serverErrors)
        if (Object.keys(serverErrors).length > 0) {
          focusFirstError(ACCOUNT_FIELDS, serverErrors, (name) => `${PREFIX}${name}`)
          return
        }
        setFormError(error.message)
      } else {
        setFormError('No pudimos crear la cuenta. Inténtalo de nuevo.')
      }
      requestAnimationFrame(() => alertRef.current?.focus())
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      width="wide"
      crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Crear cuenta', to: '/registro' }, { label: 'Cliente' }]}
      label="Cuenta de cliente"
      title={result ? 'Ya casi' : 'Crea tu cuenta'}
      description={
        result ? undefined : <p>Para conductores y talleres. Te pediremos verificar tu correo antes de ingresar.</p>
      }
    >
      <title>Crear cuenta de cliente | Autopartco</title>
      {result ? (
        <CheckEmail email={result.email} sent={result.verificationEmailSent} expiresAt={result.verificationExpiresAt}>
          <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/ingresar" />}>
            Ir a ingresar
          </Button>
        </CheckEmail>
      ) : (
        <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-6" aria-busy={submitting || undefined}>
          {formError ? <FormAlert ref={alertRef} title={formError} /> : null}
          <p className="text-sm text-muted-foreground">Todos los campos son obligatorios.</p>
          <AccountFields prefix={PREFIX} errors={errors} />
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <Button type="submit" className="h-11 px-6 text-base" disabled={submitting}>
              {submitting ? 'Creando cuenta…' : 'Crear cuenta'}
            </Button>
            <p className="text-sm text-muted-foreground">
              ¿Tienes un almacén?{' '}
              <Link to="/vender/registro" className="font-medium text-brand underline-offset-4 hover:underline">
                Regístralo aquí
              </Link>
            </p>
          </div>
          <SimulatedNotice />
        </form>
      )}
    </AuthShell>
  )
}
