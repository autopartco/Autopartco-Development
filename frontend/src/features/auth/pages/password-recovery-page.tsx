import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { CheckCircle2Icon } from 'lucide-react'
import { FormAlert, PasswordField, TextField } from '@/components/common/form-fields'
import { focusFirstError } from '../lib/account'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getAuthService } from '../api/auth-service'
import { AuthShell, SimulatedNotice } from '../components/auth-shell'
import { compact, emailError, newPasswordError, PASSWORD_HINT, type FieldErrors } from '../lib/validation'

type Step = 'request' | 'reset' | 'done'

function useFocusOnChange<T extends HTMLElement>(key: unknown) {
  const ref = useRef<T>(null)
  const first = useRef(true)
  useEffect(() => {
    if (first.current) {
      first.current = false
      return
    }
    ref.current?.focus()
  }, [key])
  return ref
}

/**
 * `/recuperar-contrasena`. El correo de recuperación trae un código (token) en texto,
 * no un enlace (`EmailService.sendPasswordRecovery`), así que el paso 2 lo pide a mano.
 * Si llega `?token=` en la URL, se precarga.
 */
export default function PasswordRecoveryPage() {
  const [searchParams] = useSearchParams()
  const urlToken = searchParams.get('token') ?? ''
  const [step, setStep] = useState<Step>(urlToken ? 'reset' : 'request')
  const [notice, setNotice] = useState<string>()
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const request = useSubmitOnce(async (email: string) => (await getAuthService()).requestPasswordRecovery(email))
  const reset = useSubmitOnce(async (token: string, password: string) => (await getAuthService()).resetPassword(token, password))
  const submitting = request.pending || reset.pending
  const alertRef = useRef<HTMLDivElement>(null)
  const headingRef = useFocusOnChange<HTMLHeadingElement>(step)

  function fail(error: unknown, fallback: string) {
    if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
      setErrors(error.fieldErrors)
      return
    }
    setFormError(error instanceof ApiError ? error.message : fallback)
    requestAnimationFrame(() => alertRef.current?.focus())
  }

  async function handleRequest(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const email = String(new FormData(event.currentTarget).get('email') ?? '')
    const next = compact({ email: emailError(email) })
    setErrors(next)
    setFormError(undefined)
    if (next.email) {
      document.getElementById('recuperar-email')?.focus()
      return
    }
    // ÚNICA escritura de este paso: el correo se pide al pulsar «Enviar código».
    const result = await request.run(email)
    if (result.status === 'ok') {
      setNotice(result.value)
      setStep('reset')
    } else if (result.status === 'error') {
      fail(result.error, 'No pudimos enviar el correo. Inténtalo de nuevo.')
    }
  }

  async function handleReset(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const token = String(data.get('token') ?? '').trim()
    const password = String(data.get('password') ?? '')
    const confirm = String(data.get('confirm') ?? '')
    const next = compact({
      token: token ? undefined : 'Pega el código que te enviamos por correo.',
      password: newPasswordError(password),
      confirm: !newPasswordError(password) && confirm !== password ? 'Las contraseñas no coinciden.' : undefined,
    })
    setErrors(next)
    setFormError(undefined)
    if (Object.keys(next).length > 0) {
      focusFirstError(['token', 'password', 'confirm'], next, (field) => `recuperar-${field}`)
      return
    }
    // ÚNICA escritura de este paso: la contraseña cambia al pulsar «Cambiar contraseña».
    const result = await reset.run(token, password)
    if (result.status === 'ok') {
      setNotice(result.value)
      setStep('done')
    } else if (result.status === 'error') {
      fail(result.error, 'No pudimos cambiar la contraseña. Inténtalo de nuevo.')
    }
  }

  const headings: Record<Step, string> = {
    request: 'Te enviaremos un código a tu correo',
    reset: 'Escribe el código y tu nueva contraseña',
    done: 'Listo, ya puedes ingresar',
  }

  return (
    <AuthShell
      crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Ingresar', to: '/ingresar' }, { label: 'Recuperar contraseña' }]}
      label={step === 'done' ? 'Contraseña actualizada' : `Paso ${step === 'request' ? 1 : 2} de 2`}
      title="Recuperar contraseña"
    >
      <title>Recuperar contraseña | Autopartco</title>
      <div className="flex flex-col gap-5">
        <h2 ref={headingRef} tabIndex={-1} className="text-block-title outline-none">
          {headings[step]}
        </h2>
        {formError ? <FormAlert ref={alertRef} title={formError} /> : null}

        {step === 'request' ? (
          <form noValidate onSubmit={handleRequest} className="flex flex-col gap-5" aria-busy={submitting || undefined}>
            <TextField
              id="recuperar-email"
              name="email"
              type="email"
              label="Correo de tu cuenta"
              autoComplete="email"
              inputMode="email"
              maxLength={100}
              required
              error={errors.email}
            />
            <Button type="submit" className="h-11 text-base" disabled={submitting}>
              {submitting ? 'Enviando…' : 'Enviar código'}
            </Button>
            <button
              type="button"
              onClick={() => {
                setErrors({})
                setStep('reset')
              }}
              className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-brand underline-offset-4 hover:underline"
            >
              Ya tengo un código
            </button>
          </form>
        ) : null}

        {step === 'reset' ? (
          <form noValidate onSubmit={handleReset} className="flex flex-col gap-5" aria-busy={submitting || undefined}>
            {notice ? <FormAlert tone="info" title={notice} /> : null}
            <TextField
              id="recuperar-token"
              name="token"
              label="Código del correo"
              hint="Cópialo del correo de recuperación. Solo sirve una vez y vence pronto."
              defaultValue={urlToken}
              autoComplete="one-time-code"
              autoCapitalize="none"
              spellCheck={false}
              required
              error={errors.token}
            />
            <PasswordField
              id="recuperar-password"
              name="password"
              label="Nueva contraseña"
              hint={PASSWORD_HINT}
              autoComplete="new-password"
              maxLength={72}
              required
              error={errors.password}
            />
            <PasswordField
              id="recuperar-confirm"
              name="confirm"
              label="Repite la nueva contraseña"
              autoComplete="new-password"
              maxLength={72}
              required
              error={errors.confirm}
            />
            <Button type="submit" className="h-11 text-base" disabled={submitting}>
              {submitting ? 'Guardando…' : 'Cambiar contraseña'}
            </Button>
            <button
              type="button"
              onClick={() => {
                setErrors({})
                setNotice(undefined)
                setStep('request')
              }}
              className="inline-flex min-h-11 w-fit items-center text-sm font-medium text-brand underline-offset-4 hover:underline"
            >
              Pedir otro código
            </button>
          </form>
        ) : null}

        {step === 'done' ? (
          <div className="flex flex-col items-start gap-4">
            <p className="flex items-start gap-2">
              <CheckCircle2Icon className="mt-0.5 size-5 shrink-0 text-stock" aria-hidden />
              {notice ?? 'Tu contraseña fue actualizada.'}
            </p>
            <Button className="h-11 px-5" nativeButton={false} render={<Link to="/ingresar" />}>
              Ingresar
            </Button>
          </div>
        ) : null}
        <SimulatedNotice />
      </div>
    </AuthShell>
  )
}
