import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate, useSearchParams } from 'react-router'
import { CheckIcon, MailIcon } from 'lucide-react'
import { FieldShell, FormAlert, INPUT_CLASS } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { useSessionState } from '@/features/auth/hooks'
import { loginPathFor } from '@/features/auth/lib/return-path'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { getSellerApplicationService } from '../api/seller-service'
import { DOCUMENT_MAX_BYTES, type SetupDetails } from '../api/types'
import { SELLER_APPLICATION_PATH, SELLER_SETUP_PATH, useSellerCta } from '../hooks'
import { replaceSessionAfterSetup } from '../lib/replace-session'

const IMAGE_ACCEPT = 'image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp'
const STEPS = ['Marca', 'Descripción', 'Revisar y activar'] as const
const LAST_STEP = STEPS.length - 1
const SHELL = 'mx-auto w-full max-w-xl px-4 py-10'

/** Mensaje para el usuario según el código de error del backend al configurar el almacén. */
function setupErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'SELLER_SETUP_TOKEN_INVALID') return 'El enlace venció o ya fue utilizado.'
    if (error.code === 'SELLER_EMAIL_NOT_VERIFIED') return 'Verifica tu correo antes de terminar la configuración del almacén.'
    if (error.code === 'SELLER_SETUP_NOT_PENDING') return 'Esta solicitud ya no está pendiente de configuración. Revisa su estado en tu portal.'
    if (error.code === 'SELLER_APPLICATION_NOT_FOUND') return 'No encontramos tu solicitud. Vuelve a iniciar sesión e inténtalo de nuevo.'
    return error.message
  }
  return 'No pudimos guardar la configuración. Inténtalo de nuevo.'
}

/** Imagen válida (tipo y tamaño) o mensaje de error. */
function imageError(file: File | undefined): string | undefined {
  if (!file) return undefined
  if (!file.type.startsWith('image/')) return 'Sube una imagen PNG, JPG o WebP.'
  if (file.size > DOCUMENT_MAX_BYTES) return 'La imagen supera 10 MB.'
  return undefined
}

function Shell({ title, children }: { title: string; children: ReactNode }) {
  return (
    <div className={SHELL}>
      <title>Configura tu almacén | Autopartco</title>
      <h1 className="text-2xl font-semibold">{title}</h1>
      {children}
    </div>
  )
}

// ------------------------------------------------------------------ asistente por pasos

interface WizardProps {
  /** Envía la configuración. Si falla, lanza el error y el asistente lo muestra. */
  submit: (details: SetupDetails) => Promise<void>
}

/**
 * Marca (logo, banner) -> Descripción -> Revisar y activar. Los tres paneles siguen montados
 * (los inactivos van ocultos) para no perder los archivos elegidos al volver atrás.
 */
function SetupWizard({ submit }: WizardProps) {
  const [step, setStep] = useState(0)
  const [description, setDescription] = useState('')
  const [logo, setLogo] = useState<File>()
  const [banner, setBanner] = useState<File>()
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [formError, setFormError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const alertRef = useRef<HTMLDivElement>(null)
  const moved = useRef(false)

  // Al cambiar de paso el foco pasa al título del paso (no en la primera carga).
  useEffect(() => {
    if (moved.current) headingRef.current?.focus()
    moved.current = true
  }, [step])

  function validateBrand(): boolean {
    const next: Record<string, string> = {}
    const logoProblem = imageError(logo)
    const bannerProblem = imageError(banner)
    if (logoProblem) next.logo = logoProblem
    if (bannerProblem) next.banner = bannerProblem
    setErrors(next)
    if (next.logo) document.getElementById('setup-logo')?.focus()
    else if (next.banner) document.getElementById('setup-banner')?.focus()
    return Object.keys(next).length === 0
  }

  function goNext() {
    if (step === 0 && !validateBrand()) return
    setStep((current) => Math.min(LAST_STEP, current + 1))
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (step !== LAST_STEP) {
      goNext()
      return
    }
    if (!validateBrand()) {
      setStep(0)
      return
    }
    setFormError(undefined)
    setSubmitting(true)
    try {
      await submit({ businessDescription: description, logo, banner })
    } catch (error) {
      setFormError(setupErrorMessage(error))
      requestAnimationFrame(() => alertRef.current?.focus())
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <p className="mt-2 text-muted-foreground">Tu solicitud fue aprobada. Completa los datos para empezar a vender.</p>

      <nav aria-label="Pasos de la configuración" className="mt-6">
        <ol className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
          {STEPS.map((label, index) => {
            const done = index < step
            const current = index === step
            return (
              <li key={label} className="flex items-center gap-2">
                {done ? (
                  <button
                    type="button"
                    onClick={() => setStep(index)}
                    className="inline-flex min-h-11 items-center gap-1.5 rounded-lg px-2 font-medium text-brand underline-offset-4 hover:underline"
                  >
                    <CheckIcon className="size-4" aria-hidden />
                    <span className="sr-only">Volver al paso {index + 1}: </span>
                    {label}
                  </button>
                ) : (
                  <span
                    aria-current={current ? 'step' : undefined}
                    className={cn('inline-flex min-h-11 items-center gap-1.5 px-2', current ? 'font-semibold text-foreground' : 'text-muted-foreground')}
                  >
                    <span
                      className={cn(
                        'inline-flex size-6 items-center justify-center rounded-full text-xs font-semibold',
                        current ? 'bg-brand text-white' : 'bg-concrete text-muted-foreground',
                      )}
                      aria-hidden
                    >
                      {index + 1}
                    </span>
                    {label}
                    <span className="sr-only">{current ? ' (paso actual)' : ''}</span>
                  </span>
                )}
                {index < LAST_STEP ? <span className="text-muted-foreground" aria-hidden>›</span> : null}
              </li>
            )
          })}
        </ol>
        <p className="sr-only" aria-live="polite">
          Paso {step + 1} de {STEPS.length}: {STEPS[step]}
        </p>
      </nav>

      {formError ? (
        <FormAlert ref={alertRef} title="No pudimos completar la configuración">
          {formError}
        </FormAlert>
      ) : null}

      <form className="mt-4 flex flex-col gap-5" onSubmit={handleSubmit} noValidate aria-busy={submitting || undefined}>
        <section hidden={step !== 0} className="flex flex-col gap-5" aria-labelledby="setup-paso-0">
          <h2 id="setup-paso-0" ref={step === 0 ? headingRef : undefined} tabIndex={-1} className="text-lg font-semibold outline-none">
            Marca de tu almacén
          </h2>
          <p className="-mt-3 text-sm text-muted-foreground">Súbelos ahora o más adelante: ambos son opcionales.</p>
          <FieldShell id="setup-logo" label="Logo" optional error={errors.logo} hint="PNG, JPG o WebP, hasta 10 MB.">
            {(describedBy) => (
              <input
                id="setup-logo"
                type="file"
                accept={IMAGE_ACCEPT}
                aria-describedby={describedBy}
                aria-invalid={errors.logo ? true : undefined}
                className={`${INPUT_CLASS} py-2`}
                onChange={(event) => setLogo(event.target.files?.[0])}
              />
            )}
          </FieldShell>
          <FieldShell id="setup-banner" label="Banner" optional error={errors.banner} hint="PNG, JPG o WebP, hasta 10 MB.">
            {(describedBy) => (
              <input
                id="setup-banner"
                type="file"
                accept={IMAGE_ACCEPT}
                aria-describedby={describedBy}
                aria-invalid={errors.banner ? true : undefined}
                className={`${INPUT_CLASS} py-2`}
                onChange={(event) => setBanner(event.target.files?.[0])}
              />
            )}
          </FieldShell>
        </section>

        <section hidden={step !== 1} className="flex flex-col gap-5" aria-labelledby="setup-paso-1">
          <h2 id="setup-paso-1" ref={step === 1 ? headingRef : undefined} tabIndex={-1} className="text-lg font-semibold outline-none">
            Descripción
          </h2>
          <FieldShell id="setup-descripcion" label="Descripción del almacén" optional hint="Cuéntale a tus clientes qué vendes.">
            {(describedBy) => (
              <textarea
                id="setup-descripcion"
                aria-describedby={describedBy}
                className={`${INPUT_CLASS} min-h-24 py-2`}
                maxLength={500}
                value={description}
                onChange={(event) => setDescription(event.target.value)}
              />
            )}
          </FieldShell>
        </section>

        <section hidden={step !== 2} className="flex flex-col gap-4" aria-labelledby="setup-paso-2">
          <h2 id="setup-paso-2" ref={step === 2 ? headingRef : undefined} tabIndex={-1} className="text-lg font-semibold outline-none">
            Revisar y activar
          </h2>
          <dl className="flex flex-col gap-3 rounded-xl bg-card p-4 text-sm ring-1 ring-foreground/10">
            <div>
              <dt className="font-semibold">Logo</dt>
              <dd className="text-muted-foreground">{logo ? logo.name : 'Sin logo por ahora'}</dd>
            </div>
            <div>
              <dt className="font-semibold">Banner</dt>
              <dd className="text-muted-foreground">{banner ? banner.name : 'Sin banner por ahora'}</dd>
            </div>
            <div>
              <dt className="font-semibold">Descripción</dt>
              <dd className="break-words text-muted-foreground">{description.trim() || 'Sin descripción por ahora'}</dd>
            </div>
          </dl>
          <p className="text-sm text-muted-foreground">Al activar, tu almacén queda abierto y empieza tu prueba gratuita.</p>
        </section>

        <div className="flex flex-wrap items-center justify-between gap-3">
          {step > 0 ? (
            <Button type="button" variant="outline" className="h-11 px-5" disabled={submitting} onClick={() => setStep(step - 1)}>
              Atrás
            </Button>
          ) : (
            <span />
          )}
          {step < LAST_STEP ? (
            <Button type="button" className="h-11 px-5" onClick={goNext}>
              Siguiente
            </Button>
          ) : (
            <Button type="submit" className="h-11 px-5" disabled={submitting}>
              {submitting ? 'Activando…' : 'Activar mi almacén'}
            </Button>
          )}
        </div>
      </form>
    </>
  )
}

// ------------------------------------------------------------------ enlace vencido

/** Mensaje de espera/neutro tras pedir un enlace nuevo. */
function linkErrorMessage(error: unknown): string {
  if (error instanceof ApiError && error.code === 'SELLER_SETUP_LINK_COOLDOWN') {
    return 'Ya te enviamos un enlace hace poco. Espera 5 minutos antes de pedir otro y revisa también tu carpeta de spam.'
  }
  return error instanceof ApiError ? error.message : 'No pudimos enviar el enlace. Inténtalo de nuevo.'
}

function ExpiredLink({ token, hasSession }: { token: string; hasSession: boolean }) {
  const [sending, setSending] = useState(false)
  const [notice, setNotice] = useState<{ tone: 'info' | 'error'; text: string }>()

  async function resend() {
    setSending(true)
    setNotice(undefined)
    try {
      await (await getSellerApplicationService()).requestSetupLinkByToken(token)
      // Respuesta neutra: no revela si el enlace correspondía a una solicitud.
      setNotice({ tone: 'info', text: 'Si tu solicitud está pendiente de configurar, te enviamos un enlace nuevo al correo de tu cuenta. Puede tardar unos minutos.' })
    } catch (error) {
      setNotice({ tone: 'error', text: linkErrorMessage(error) })
    } finally {
      setSending(false)
    }
  }

  return (
    <Shell title="Este enlace ya no sirve">
      <p className="mt-2 text-muted-foreground">Venció o ya se usó. Puedes terminar la configuración de dos maneras.</p>
      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <Button className="h-11 px-5" nativeButton={false} render={<Link to={hasSession ? SELLER_SETUP_PATH : loginPathFor(SELLER_SETUP_PATH)} replace />}>
          Continuar con mi cuenta
        </Button>
        <Button variant="outline" className="h-11 gap-2 px-5" disabled={sending} onClick={() => void resend()}>
          <MailIcon className="size-4" aria-hidden />
          {sending ? 'Enviando…' : 'Enviarme un nuevo enlace'}
        </Button>
      </div>
      <div aria-live="polite">
        {notice ? (
          <p className={cn('mt-4 text-sm', notice.tone === 'error' ? 'font-medium text-destructive' : 'text-muted-foreground')}>{notice.text}</p>
        ) : null}
      </div>
      {!hasSession ? <p className="mt-4 text-sm text-muted-foreground">Con tu cuenta no necesitas el enlace del correo.</p> : null}
    </Shell>
  )
}

// ------------------------------------------------------------------ flujos

function useFinish() {
  const navigate = useNavigate()
  return async function finish() {
    const session = await replaceSessionAfterSetup()
    navigate(session ? '/portal' : '/ingresar', { replace: true, state: { from: '/portal' } })
  }
}

/** `?token=`: el enlace del correo de aprobación. */
function TokenSetup({ token }: { token: string }) {
  const { session } = useSessionState()
  const finish = useFinish()
  const [expired, setExpired] = useState(false)

  if (expired) return <ExpiredLink token={token} hasSession={Boolean(session)} />

  return (
    <Shell title="Configura tu almacén">
      <SetupWizard
        submit={async (details) => {
          try {
            await (await getSellerApplicationService()).completeSetup({ token, ...details })
          } catch (error) {
            if (error instanceof ApiError && error.code === 'SELLER_SETUP_TOKEN_INVALID') {
              setExpired(true)
              return
            }
            throw error
          }
          await finish()
        }}
      />
    </Shell>
  )
}

/** Sin token: la cuenta con sesión configura la solicitud `CONFIGURATION_PENDING` que le aprobaron. */
function SessionSetup() {
  const { session, ready } = useSessionState()
  const seller = useSellerCta()
  const finish = useFinish()

  if (!ready) return <Shell title="Configura tu almacén"><p className="mt-4 text-muted-foreground" role="status">Cargando…</p></Shell>
  if (!session) return <Navigate to={loginPathFor(SELLER_SETUP_PATH)} replace />
  if (seller.state === 'loading') {
    return <Shell title="Configura tu almacén"><p className="mt-4 text-muted-foreground" role="status">Buscando tu solicitud…</p></Shell>
  }

  const application = seller.application
  if (seller.state === 'review' && application?.status === 'CONFIGURATION_PENDING') {
    return (
      <Shell title="Configura tu almacén">
        <SetupWizard
          submit={async (details) => {
            await (await getSellerApplicationService()).completeSetupAuthenticated(application.applicationId, details)
            await finish()
          }}
        />
      </Shell>
    )
  }

  if (seller.state === 'review') {
    return (
      <Shell title="Tu solicitud sigue en revisión">
        <p className="mt-2 text-muted-foreground">Cuando la aprobemos podrás terminar la configuración aquí.</p>
        <Button className="mt-6 h-11 px-5" nativeButton={false} render={<Link to={SELLER_APPLICATION_PATH} />}>
          Ver mi solicitud
        </Button>
      </Shell>
    )
  }
  if (seller.state === 'manage') {
    return (
      <Shell title="Tu almacén ya está activo">
        <p className="mt-2 text-muted-foreground">No hay nada más que configurar.</p>
        <Button className="mt-6 h-11 px-5" nativeButton={false} render={<Link to="/portal" />}>
          Ir a mi almacén
        </Button>
      </Shell>
    )
  }
  return (
    <Shell title="No hay configuración pendiente">
      <p className="mt-2 text-muted-foreground">Tu cuenta no tiene un almacén aprobado por configurar.</p>
      <Button className="mt-6 h-11 px-5" nativeButton={false} render={<Link to="/vender" />}>
        Vende en Autopartco
      </Button>
    </Shell>
  )
}

/**
 * `/seller/setup`: con `?token=` (enlace del correo) o sin token (exige sesión). En ambos casos el
 * almacén aprobado completa su marca y descripción, se activa y la sesión pasa a gerente del almacén.
 */
export default function SellerSetupPage() {
  const [params] = useSearchParams()
  // El token se captura una sola vez en memoria: el estado sobrevive a re-renders aunque la URL cambie.
  const [token] = useState(() => params.get('token')?.trim() ?? '')

  // Saca el token de la barra y del historial. Se conserva history.state para no romper el índice del router.
  useEffect(() => {
    if (token && new URLSearchParams(window.location.search).has('token')) {
      window.history.replaceState(window.history.state, '', SELLER_SETUP_PATH)
    }
  }, [token])

  return token ? <TokenSetup token={token} /> : <SessionSetup />
}
