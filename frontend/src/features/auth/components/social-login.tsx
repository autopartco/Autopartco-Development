import { useEffect, useRef, useState, type FormEvent } from 'react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError, DATA_SOURCE } from '@/lib/api'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getAuthService } from '../api/auth-service'
import type { Session, SocialPrefill, SocialProvider, SocialRegistration } from '../api/types'
import { FACEBOOK_APP_ID, GOOGLE_CLIENT_ID, facebookAccessToken, preloadFacebook, renderGoogleButton } from '../lib/social-sdk'

const SIMULATED = DATA_SOURCE !== 'http'
const showGoogle = SIMULATED || GOOGLE_CLIENT_ID !== ''
const showFacebook = SIMULATED || FACEBOOK_APP_ID !== ''

// Al menos un proveedor configurado (o datos simulados).
const socialLoginAvailable = showGoogle || showFacebook

function FacebookIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden className="size-5 fill-current">
      <path d="M24 12.07C24 5.4 18.63 0 12 0S0 5.4 0 12.07C0 18.1 4.39 23.1 10.13 24v-8.44H7.08v-3.49h3.05V9.41c0-3.02 1.79-4.69 4.53-4.69 1.31 0 2.68.24 2.68.24v2.96h-1.51c-1.49 0-1.95.93-1.95 1.88v2.26h3.33l-.53 3.49h-2.8V24C19.61 23.1 24 18.1 24 12.07Z" />
    </svg>
  )
}

function GoogleIcon() {
  return (
    <svg viewBox="0 0 24 24" aria-hidden className="size-5">
      <path fill="#EA4335" d="M12 10.2v3.9h5.5c-.24 1.4-1.7 4.1-5.5 4.1-3.3 0-6-2.7-6-6.1s2.7-6.1 6-6.1c1.9 0 3.1.8 3.8 1.5l2.6-2.5C16.8 3.4 14.6 2.4 12 2.4 6.7 2.4 2.4 6.7 2.4 12s4.3 9.6 9.6 9.6c5.5 0 9.2-3.9 9.2-9.4 0-.6-.1-1.1-.2-1.6H12Z" />
    </svg>
  )
}

/** Botón oficial de Google (o uno simulado sin credenciales). */
function GoogleButton({ mode, disabled, onToken }: { mode: 'login' | 'register'; disabled: boolean; onToken: (token: string) => void }) {
  const ref = useRef<HTMLDivElement>(null)
  const [failed, setFailed] = useState(false)
  const callback = useRef(onToken)
  useEffect(() => {
    callback.current = onToken
  })
  useEffect(() => {
    if (SIMULATED || !ref.current) return
    renderGoogleButton(ref.current, (token) => callback.current(token), mode === 'register' ? 'signup_with' : 'continue_with').catch(() => setFailed(true))
  }, [mode])

  if (SIMULATED) {
    return (
      <Button type="button" variant="outline" className="h-11 w-full gap-2" disabled={disabled} onClick={() => onToken('mock-google-token')}>
        <GoogleIcon />
        Continuar con Google
      </Button>
    )
  }
  if (failed) return <p className="text-sm text-muted-foreground">No pudimos cargar el botón de Google. Recarga la página o ingresa con tu correo.</p>
  return <div ref={ref} className={disabled ? 'pointer-events-none flex min-h-11 w-full justify-center opacity-60' : 'flex min-h-11 w-full justify-center'} />
}

interface SocialLoginProps {
  mode: 'login' | 'register'
  /** Sesión abierta: la página decide a dónde ir. */
  onSession: (session: Session) => void | Promise<void>
}

const REG_FIELDS = ['name', 'surname', 'identification', 'dateBirth', 'numberPhone'] as const
type RegField = (typeof REG_FIELDS)[number]

/** Botones "Continuar con Google/Facebook" y el formulario breve que completa una cuenta nueva. */
export function SocialLogin({ mode, onSession }: SocialLoginProps) {
  const [error, setError] = useState<string>()
  const [pendingSignup, setPendingSignup] = useState<{ provider: SocialProvider; token: string; prefill: SocialPrefill }>()
  const [regErrors, setRegErrors] = useState<Partial<Record<RegField, string>>>({})
  const [regError, setRegError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)
  const exchange = useSubmitOnce(async (provider: SocialProvider, token: string, registration?: SocialRegistration) =>
    (await getAuthService()).socialLogin(provider, token, registration),
  )
  const busy = exchange.pending

  useEffect(() => {
    if (showFacebook && !SIMULATED) preloadFacebook()
  }, [])

  async function signIn(provider: SocialProvider, token: string) {
    setError(undefined)
    const result = await exchange.run(provider, token)
    if (result.status === 'ok') {
      await onSession(result.value)
      return
    }
    if (result.status !== 'error') return
    const failure = result.error
    if (failure instanceof ApiError && failure.code === 'SOCIAL_REGISTRATION_REQUIRED') {
      setRegErrors({})
      setRegError(undefined)
      setPendingSignup({ provider, token, prefill: (failure.details as SocialPrefill | null) ?? { email: '', name: null, surname: null } })
      return
    }
    if (failure instanceof ApiError && failure.code === 'SOCIAL_LINK_REQUIRED') {
      setError('Ya tienes una cuenta con ese correo. Ingresa con tu correo y contraseña.')
    } else if (failure instanceof ApiError && failure.code === 'SOCIAL_EMAIL_REQUIRED') {
      setError('Tu cuenta no compartió el correo. Autoriza el correo al ingresar o regístrate con tu correo.')
    } else {
      setError(failure instanceof ApiError ? failure.message : 'No pudimos ingresar. Inténtalo de nuevo.')
    }
    requestAnimationFrame(() => alertRef.current?.focus())
  }

  async function withFacebook() {
    setError(undefined)
    const token = SIMULATED ? 'mock-facebook-token' : await facebookAccessToken().catch(() => undefined)
    if (token) await signIn('facebook', token)
  }

  async function completeSignup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!pendingSignup) return
    const data = new FormData(event.currentTarget)
    const value = (field: RegField) => String(data.get(field) ?? '').trim()
    const errors: Partial<Record<RegField, string>> = {}
    if (!value('name')) errors.name = 'Escribe tu nombre.'
    if (!value('surname')) errors.surname = 'Escribe tu apellido.'
    if (!value('identification')) errors.identification = 'Escribe tu número de documento.'
    if (!value('dateBirth')) errors.dateBirth = 'Elige tu fecha de nacimiento.'
    else if (value('dateBirth') >= new Date().toISOString().slice(0, 10)) errors.dateBirth = 'Debe ser una fecha pasada.'
    if (!/^\d{7,10}$/.test(value('numberPhone'))) errors.numberPhone = 'Escribe un teléfono de 7 a 10 dígitos.'
    setRegErrors(errors)
    setRegError(undefined)
    const first = REG_FIELDS.find((field) => errors[field])
    if (first) {
      document.getElementById(`social-${first}`)?.focus()
      return
    }
    const result = await exchange.run(pendingSignup.provider, pendingSignup.token, {
      name: value('name'),
      surname: value('surname'),
      identification: value('identification'),
      dateBirth: value('dateBirth'),
      numberPhone: value('numberPhone'),
    })
    if (result.status === 'ok') {
      setPendingSignup(undefined)
      await onSession(result.value)
    } else if (result.status === 'error') {
      const failure = result.error
      if (failure instanceof ApiError) {
        setRegErrors(failure.fieldErrors as Partial<Record<RegField, string>>)
        setRegError(failure.code === 'IDENTIFICATION_ALREADY_EXISTS' ? 'Ese documento ya está registrado en otra cuenta.' : failure.message)
      } else {
        setRegError('No pudimos crear tu cuenta. Inténtalo de nuevo.')
      }
    }
  }

  if (!socialLoginAvailable) return null
  const prefill = pendingSignup?.prefill

  return (
    <div className="flex flex-col gap-3">
      {error ? <FormAlert ref={alertRef} title={error} /> : null}
      {showGoogle ? <GoogleButton mode={mode} disabled={busy} onToken={(token) => void signIn('google', token)} /> : null}
      {showFacebook ? (
        <Button type="button" className="h-11 w-full gap-2 bg-[#1877F2] text-white hover:bg-[#166FE5]" disabled={busy} onClick={() => void withFacebook()}>
          <FacebookIcon />
          Continuar con Facebook
        </Button>
      ) : null}
      {busy && !pendingSignup ? <p role="status" className="text-sm text-muted-foreground">Validando tu ingreso…</p> : null}
      <div className="flex items-center gap-3 text-sm text-muted-foreground" aria-hidden>
        <span className="h-px flex-1 bg-border" />o con tu correo<span className="h-px flex-1 bg-border" />
      </div>

      <Dialog open={pendingSignup !== undefined} onOpenChange={(open) => !open && !busy && setPendingSignup(undefined)}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>Completa tu cuenta</DialogTitle>
            <DialogDescription>
              {prefill?.email ? `Crearemos tu cuenta con ${prefill.email}. ` : ''}Necesitamos estos datos para tus pedidos y facturas.
            </DialogDescription>
          </DialogHeader>
          <form noValidate onSubmit={(event) => void completeSignup(event)} className="flex flex-col gap-4" aria-busy={busy || undefined}>
            {regError ? <FormAlert title={regError} /> : null}
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField id="social-name" name="name" label="Nombre" defaultValue={prefill?.name ?? ''} maxLength={100} autoComplete="given-name" required error={regErrors.name} />
              <TextField id="social-surname" name="surname" label="Apellido" defaultValue={prefill?.surname ?? ''} maxLength={100} autoComplete="family-name" required error={regErrors.surname} />
            </div>
            <TextField id="social-identification" name="identification" label="Número de documento" inputMode="numeric" maxLength={20} autoComplete="off" required error={regErrors.identification} />
            <div className="grid gap-4 sm:grid-cols-2">
              <TextField id="social-dateBirth" name="dateBirth" type="date" label="Fecha de nacimiento" autoComplete="bday" required error={regErrors.dateBirth} />
              <TextField id="social-numberPhone" name="numberPhone" label="Celular" inputMode="tel" maxLength={10} autoComplete="tel-national" required error={regErrors.numberPhone} />
            </div>
            <Button type="submit" className="h-11" disabled={busy}>
              {busy ? 'Creando tu cuenta…' : 'Crear cuenta y continuar'}
            </Button>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
