import { useState, type FormEvent } from 'react'
import { MonitorSmartphoneIcon } from 'lucide-react'
import { toast } from 'sonner'
import { FormAlert, PasswordField, TextField } from '@/components/common/form-fields'
import { ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Switch } from '@/components/ui/switch'
import { getAuthService } from '@/features/auth/api/auth-service'
import { useSignOut } from '@/features/auth/hooks'
import { ApiError } from '@/lib/api'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { AccountSection } from '../account-layout'
import {
  changePassword,
  confirmEmailChange,
  confirmTwoFactor,
  disableTwoFactorWithPassword,
  getProfile,
  getSettings,
  listIdentities,
  listSessions,
  patchSettings,
  requestEmailChange,
  requestTwoFactor,
  revokeSession,
  unlinkIdentity,
} from '../api'

const PASSWORD_RULE = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,72}$/
const PROVIDER_LABEL: Record<string, string> = { google: 'Google', facebook: 'Facebook' }

function message(error: unknown, fallback: string): string {
  return error instanceof ApiError ? error.message : fallback
}

function refresh() {
  invalidateAsync('portal:account:')
}

function formatDate(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : new Intl.DateTimeFormat('es-CO', { dateStyle: 'medium', timeStyle: 'short' }).format(date)
}

// ------------------------------------------------------------------ contraseña

/** Cuenta creada con Google sin contraseña: se crea con el enlace de recuperación, que prueba el control del correo. */
function CreatePasswordNotice({ email, children }: { email: string; children: string }) {
  const [state, setState] = useState<'idle' | 'busy' | 'sent'>('idle')

  async function send() {
    setState('busy')
    try {
      await (await getAuthService()).requestPasswordRecovery(email)
      setState('sent')
    } catch (error) {
      setState('idle')
      toast.error(message(error, 'No pudimos enviar el enlace.'))
    }
  }

  return (
    <div className="flex max-w-md flex-col gap-3">
      <FormAlert tone="info" title="Aún no tienes contraseña">
        {state === 'sent' ? <>Te enviamos un enlace a <strong className="break-all">{email}</strong> para crearla.</> : children}
      </FormAlert>
      {state === 'sent' ? null : (
        <Button className="h-11 w-fit px-5" disabled={state === 'busy'} onClick={() => void send()}>
          {state === 'busy' ? 'Enviando…' : 'Enviarme el enlace para crearla'}
        </Button>
      )}
    </div>
  )
}

function PasswordSection({ email, passwordSet }: { email: string; passwordSet: boolean }) {
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const current = String(data.get('current') ?? '')
    const next = String(data.get('next') ?? '')
    const repeat = String(data.get('repeat') ?? '')
    const found: Record<string, string> = {}
    if (!current) found.current = 'Escribe tu contraseña actual.'
    if (!PASSWORD_RULE.test(next)) found.next = 'De 8 a 72 caracteres, con mayúscula, minúscula, número y carácter especial.'
    else if (next === current) found.next = 'Debe ser distinta de la actual.'
    if (repeat !== next) found.repeat = 'Las contraseñas no coinciden.'
    setErrors(found)
    if (Object.keys(found).length > 0) {
      document.getElementById(`clave-${Object.keys(found)[0]}`)?.focus()
      return
    }
    setBusy(true)
    try {
      await changePassword(current, next)
      form.reset()
      toast.success('Cambiamos tu contraseña y cerramos las demás sesiones.')
      refresh()
    } catch (error) {
      if (error instanceof ApiError && error.code === 'INVALID_PASSWORD') setErrors({ current: error.message })
      else toast.error(message(error, 'No pudimos cambiar la contraseña.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AccountSection
      id="seg-clave"
      title="Contraseña"
      description={passwordSet ? 'Al cambiarla cerramos las demás sesiones abiertas.' : undefined}
    >
      {!passwordSet ? (
        <CreatePasswordNotice email={email}>Ingresas con Google. Crea una contraseña para poder ingresar también con tu correo.</CreatePasswordNotice>
      ) : (
      <form noValidate onSubmit={(event) => void submit(event)} className="grid max-w-md gap-4">
        <PasswordField id="clave-current" name="current" label="Contraseña actual" autoComplete="current-password" error={errors.current} />
        <PasswordField id="clave-next" name="next" label="Nueva contraseña" autoComplete="new-password" hint="Mínimo 8 caracteres con mayúscula, minúscula, número y carácter especial." error={errors.next} />
        <PasswordField id="clave-repeat" name="repeat" label="Repite la nueva contraseña" autoComplete="new-password" error={errors.repeat} />
        <Button type="submit" className="h-11 w-fit px-5" disabled={busy}>
          {busy ? 'Guardando…' : 'Cambiar contraseña'}
        </Button>
      </form>
      )}
    </AccountSection>
  )
}

// ------------------------------------------------------------------ correo

function EmailSection({ email, passwordSet }: { email: string; passwordSet: boolean }) {
  const { signOut } = useSignOut()
  const [step, setStep] = useState<{ newEmail: string } | null>(null)
  const [error, setError] = useState<string>()
  const [busy, setBusy] = useState(false)

  async function request(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const newEmail = String(data.get('newEmail') ?? '').trim()
    const password = String(data.get('password') ?? '')
    setError(undefined)
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(newEmail)) {
      setError('Escribe un correo válido.')
      return
    }
    if (!password) {
      setError('Confirma tu contraseña.')
      return
    }
    setBusy(true)
    try {
      const result = await requestEmailChange(newEmail, password)
      if (!result.sent) toast.info(`Espera ${result.retryAfterSeconds} segundos para pedir otro código.`)
      setStep({ newEmail })
    } catch (failure) {
      setError(message(failure, 'No pudimos enviar el código.'))
    } finally {
      setBusy(false)
    }
  }

  async function confirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!step) return
    const code = String(new FormData(event.currentTarget).get('code') ?? '').trim()
    setBusy(true)
    setError(undefined)
    try {
      await confirmEmailChange(step.newEmail, code)
      toast.success('Cambiamos tu correo. Ingresa de nuevo con el correo nuevo.')
      await signOut()
    } catch (failure) {
      setError(message(failure, 'No pudimos confirmar el código.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AccountSection id="seg-correo" title="Correo" description={<>Ingresas con <strong className="break-all text-ap-ink">{email}</strong>. Te enviaremos un código al correo nuevo; al confirmarlo deberás ingresar otra vez.</>}>
      {error ? <FormAlert title="No se completó el cambio">{error}</FormAlert> : null}
      {!passwordSet ? (
        <CreatePasswordNotice email={email}>Para cambiar el correo debes confirmar tu contraseña. Créala primero con un enlace a tu correo actual.</CreatePasswordNotice>
      ) : step ? (
        <form noValidate onSubmit={(event) => void confirm(event)} className="grid max-w-md gap-4">
          <p className="text-sm text-ap-ink">
            Enviamos un código a <strong className="break-all">{step.newEmail}</strong>. Vence en 15 minutos.
          </p>
          <TextField id="correo-code" name="code" label="Código" inputMode="numeric" autoComplete="one-time-code" maxLength={12} required />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" className="h-11 px-5" disabled={busy}>
              {busy ? 'Confirmando…' : 'Confirmar correo'}
            </Button>
            <Button type="button" variant="ghost" className="h-11" onClick={() => setStep(null)}>
              Usar otro correo
            </Button>
          </div>
        </form>
      ) : (
        <form noValidate onSubmit={(event) => void request(event)} className="grid max-w-md gap-4">
          <TextField id="correo-new" name="newEmail" type="email" label="Correo nuevo" autoComplete="email" maxLength={100} required />
          <PasswordField id="correo-password" name="password" label="Tu contraseña" autoComplete="current-password" />
          <Button type="submit" className="h-11 w-fit px-5" disabled={busy}>
            {busy ? 'Enviando…' : 'Enviar código'}
          </Button>
        </form>
      )}
    </AccountSection>
  )
}

// ------------------------------------------------------------------ verificación en dos pasos

function TwoFactorSection({ enabled, social }: { enabled: boolean; social: boolean }) {
  const [pending, setPending] = useState<'ENABLE' | 'DISABLE' | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string>()

  async function start(action: 'ENABLE' | 'DISABLE') {
    setError(undefined)
    setBusy(true)
    try {
      const result = await requestTwoFactor(action)
      if (!result.sent) toast.info(`Espera ${result.retryAfterSeconds} segundos para pedir otro código.`)
      setPending(action)
    } catch (failure) {
      setError(message(failure, 'No pudimos enviar el código.'))
    } finally {
      setBusy(false)
    }
  }

  async function confirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!pending) return
    const code = String(new FormData(event.currentTarget).get('code') ?? '').trim()
    setBusy(true)
    setError(undefined)
    try {
      await confirmTwoFactor(code, pending)
      toast.success(pending === 'ENABLE' ? 'Activamos la verificación en dos pasos.' : 'Desactivamos la verificación en dos pasos.')
      setPending(null)
      refresh()
    } catch (failure) {
      setError(message(failure, 'El código no es válido.'))
    } finally {
      setBusy(false)
    }
  }

  async function disableWithPassword(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const password = String(new FormData(event.currentTarget).get('password') ?? '')
    setBusy(true)
    setError(undefined)
    try {
      await disableTwoFactorWithPassword(password)
      toast.success('Desactivamos la verificación en dos pasos.')
      refresh()
    } catch (failure) {
      setError(message(failure, 'No pudimos desactivarla.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <AccountSection
      id="seg-2fa"
      title="Verificación en dos pasos"
      description="Al ingresar te pediremos además un código que enviamos a tu correo."
      aside={<span className={enabled ? 'text-sm font-semibold text-ap-success' : 'text-sm text-ap-muted'}>{enabled ? 'Activa' : 'Desactivada'}</span>}
    >
      {error ? <FormAlert title="No se completó">{error}</FormAlert> : null}
      {pending ? (
        <form noValidate onSubmit={(event) => void confirm(event)} className="grid max-w-md gap-4">
          <TextField id="dospasos-code" name="code" label="Código enviado a tu correo" inputMode="numeric" autoComplete="one-time-code" maxLength={12} required />
          <div className="flex flex-wrap gap-2">
            <Button type="submit" className="h-11 px-5" disabled={busy}>
              {pending === 'ENABLE' ? 'Activar' : 'Desactivar'}
            </Button>
            <Button type="button" variant="ghost" className="h-11" onClick={() => setPending(null)}>
              Cancelar
            </Button>
          </div>
        </form>
      ) : !enabled ? (
        <Button className="h-11 w-fit px-5" disabled={busy} onClick={() => void start('ENABLE')}>
          Activar verificación en dos pasos
        </Button>
      ) : social ? (
        <Button variant="outline" className="h-11 w-fit px-5" disabled={busy} onClick={() => void start('DISABLE')}>
          Desactivar con un código
        </Button>
      ) : (
        <form noValidate onSubmit={(event) => void disableWithPassword(event)} className="grid max-w-md gap-4">
          <PasswordField id="dospasos-password" name="password" label="Confirma tu contraseña para desactivarla" autoComplete="current-password" required />
          <Button type="submit" variant="outline" className="h-11 w-fit px-5" disabled={busy}>
            Desactivar
          </Button>
        </form>
      )}
    </AccountSection>
  )
}

// ------------------------------------------------------------------ avisos de ingreso

function LoginAlertsSection({ enabled }: { enabled: boolean }) {
  const [value, setValue] = useState(enabled)
  async function toggle(next: boolean) {
    setValue(next)
    try {
      await patchSettings({ security: { loginAlerts: next } })
      refresh()
    } catch (error) {
      setValue(!next)
      toast.error(message(error, 'No pudimos guardar el cambio.'))
    }
  }
  return (
    <AccountSection id="seg-alertas" title="Avisos de inicio de sesión" description="Te escribimos cuando alguien ingresa a tu cuenta desde un dispositivo nuevo.">
      <label className="flex min-h-11 w-fit items-center gap-3 text-sm text-ap-ink">
        <Switch checked={value} onCheckedChange={(next) => void toggle(next)} />
        {value ? 'Activados' : 'Desactivados'}
      </label>
    </AccountSection>
  )
}

// ------------------------------------------------------------------ sesiones

function SessionsSection() {
  const sessions = useAsync('portal:account:sessions', listSessions)
  async function close(sessionId: string) {
    try {
      await revokeSession(sessionId)
      toast.success('Cerramos esa sesión.')
      refresh()
    } catch (error) {
      toast.error(message(error, 'No pudimos cerrar la sesión.'))
    }
  }
  return (
    <AccountSection id="seg-sesiones" title="Sesiones abiertas" description="Dispositivos con tu cuenta abierta. Cierra las que no reconozcas.">
      {sessions.status === 'error' ? (
        <ErrorState title="No pudimos cargar tus sesiones" onRetry={sessions.retry} />
      ) : !sessions.data ? (
        <Skeleton className="h-16 max-w-2xl" />
      ) : (
        <ul className="max-w-2xl divide-y divide-ap-line border-y border-ap-line">
          {sessions.data.map((session) => (
            <li key={session.sessionId} className="flex items-center gap-3 py-3">
              <MonitorSmartphoneIcon className="size-5 shrink-0 text-ap-muted" aria-hidden />
              <div className="min-w-0 flex-1 text-sm">
                <p className="truncate font-medium text-ap-ink">
                  {session.userAgent || 'Dispositivo desconocido'}
                  {session.current ? <span className="ml-2 text-xs font-semibold text-ap-success">Esta sesión</span> : null}
                </p>
                <p className="font-mono text-xs text-ap-muted tabular-nums">
                  {session.ipAddress ?? 'IP desconocida'} · desde {formatDate(session.createdAt)}
                </p>
              </div>
              {session.current ? null : (
                <Button variant="outline" size="sm" className="h-11 sm:h-9" onClick={() => void close(session.sessionId)}>
                  Cerrar
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </AccountSection>
  )
}

// ------------------------------------------------------------------ ingreso con Google

function IdentitiesSection({ passwordSet }: { passwordSet: boolean }) {
  const identities = useAsync('portal:account:identities', listIdentities)
  const [unlinking, setUnlinking] = useState<string | null>(null)
  const [error, setError] = useState<string>()

  async function unlink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!unlinking) return
    const password = String(new FormData(event.currentTarget).get('password') ?? '')
    setError(undefined)
    try {
      await unlinkIdentity(unlinking, password)
      toast.success(`Desvinculamos ${PROVIDER_LABEL[unlinking] ?? unlinking}.`)
      setUnlinking(null)
      refresh()
    } catch (failure) {
      setError(message(failure, 'No pudimos desvincularlo.'))
    }
  }

  return (
    <AccountSection
      id="seg-social"
      title="Ingreso con Google"
      description={passwordSet ? 'Métodos de ingreso vinculados a tu cuenta.' : 'Métodos de ingreso vinculados a tu cuenta. Para desvincular Google primero crea una contraseña: así conservas una forma de ingresar.'}
    >
      {identities.status === 'error' ? (
        <ErrorState title="No pudimos cargar los métodos de ingreso" onRetry={identities.retry} />
      ) : !identities.data ? (
        <Skeleton className="h-12 max-w-2xl" />
      ) : identities.data.length === 0 ? (
        <p className="text-sm text-ap-muted">No tienes cuentas externas vinculadas. Puedes vincular Google ingresando con «Continuar con Google» con este mismo correo.</p>
      ) : (
        <ul className="max-w-2xl divide-y divide-ap-line border-y border-ap-line">
          {identities.data.map((identity) => (
            <li key={identity.provider} className="flex flex-col gap-3 py-3">
              <div className="flex items-center gap-3">
                <div className="min-w-0 flex-1 text-sm">
                  <p className="font-medium text-ap-ink">{PROVIDER_LABEL[identity.provider] ?? identity.provider}</p>
                  <p className="break-all text-xs text-ap-muted">
                    {identity.email ?? ''} · vinculado el {formatDate(identity.linkedAt)}
                  </p>
                </div>
                {unlinking === identity.provider || !passwordSet ? null : (
                  <Button variant="outline" size="sm" className="h-11 sm:h-9" onClick={() => setUnlinking(identity.provider)}>
                    Desvincular
                  </Button>
                )}
              </div>
              {unlinking === identity.provider ? (
                <form noValidate onSubmit={(event) => void unlink(event)} className="grid max-w-md gap-3">
                  {error ? <FormAlert title="No se desvinculó">{error}</FormAlert> : null}
                  <PasswordField id="social-password" name="password" label="Confirma tu contraseña" hint="Así conservas una forma de ingresar." autoComplete="current-password" required />
                  <div className="flex flex-wrap gap-2">
                    <Button type="submit" variant="outline" className="h-11">
                      Desvincular
                    </Button>
                    <Button type="button" variant="ghost" className="h-11" onClick={() => setUnlinking(null)}>
                      Cancelar
                    </Button>
                  </div>
                </form>
              ) : null}
            </li>
          ))}
        </ul>
      )}
    </AccountSection>
  )
}

/** `/cuenta/seguridad`: contraseña, correo, 2FA, avisos de ingreso, sesiones y Google. */
export default function AccountSecurityPage() {
  const profile = useAsync('portal:account:profile', getProfile)
  const settings = useAsync('portal:account:settings', getSettings)
  const identities = useAsync('portal:account:identities', listIdentities)
  if (profile.status === 'error' || settings.status === 'error') {
    return <ErrorState title="No pudimos cargar tu seguridad" onRetry={() => (profile.retry(), settings.retry())} />
  }
  if (!profile.data || !settings.data) {
    return (
      <div className="flex flex-col gap-4" aria-hidden>
        <Skeleton className="h-40 max-w-md" />
        <Skeleton className="h-32 max-w-md" />
      </div>
    )
  }
  const social = (identities.data?.length ?? 0) > 0
  return (
    <div className="flex flex-col">
      <PasswordSection email={profile.data.email} passwordSet={profile.data.passwordSet !== false} />
      <EmailSection email={profile.data.email} passwordSet={profile.data.passwordSet !== false} />
      <TwoFactorSection key={String(settings.data.security.twoFactor.enabled)} enabled={settings.data.security.twoFactor.enabled} social={social} />
      <LoginAlertsSection key={`alertas-${String(settings.data.security.loginAlerts)}`} enabled={settings.data.security.loginAlerts} />
      <SessionsSection />
      <IdentitiesSection passwordSet={profile.data.passwordSet !== false} />
    </div>
  )
}
