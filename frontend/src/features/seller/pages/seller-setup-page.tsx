import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router'
import { FieldShell, FormAlert, INPUT_CLASS } from '@/components/common/form-fields'
import { LoadingState } from '@/components/common/states'
import { Stepper } from '@/components/common/stepper'
import { Button } from '@/components/ui/button'
import { useSessionState, useSignOut } from '@/features/auth/hooks'
import { loginPathFor } from '@/features/auth/lib/return-path'
import { UnsavedChangesDialog } from '@/components/common/unsaved-changes-dialog'
import { ApiError } from '@/lib/api'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { useUnsavedChanges } from '@/lib/use-unsaved-changes'
import { getSellerApplicationService } from '../api/seller-service'
import { DOCUMENT_MAX_BYTES, type SetupDetails } from '../api/types'
import { SELLER_SETUP_PATH, useSellerCta } from '../hooks'
import { replaceSessionAfterSetup } from '../lib/replace-session'

const IMAGE_ACCEPT = 'image/png,image/jpeg,image/webp,.png,.jpg,.jpeg,.webp'
const STEPS = ['Marca', 'Descripción', 'Revisar y activar'] as const
const LAST_STEP = STEPS.length - 1
const SHELL = 'mx-auto w-full max-w-2xl px-4 py-8 sm:px-6 sm:py-10'

/** Mensaje para el usuario según el código de error del backend al configurar el almacén. */
function setupErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
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
      <p className="ap-index mb-2">Almacén / Configuración</p>
      <h1 className="text-page-title text-ap-ink">{title}</h1>
      {children}
    </div>
  )
}

// ------------------------------------------------------------------ asistente por pasos

interface WizardProps {
  /** Envía la configuración. Si falla, lanza el error y el asistente lo muestra. Llama a `onSaved` al guardar, antes de navegar. */
  submit: (details: SetupDetails, onSaved: () => void) => Promise<void>
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
  const saved = useRef(false)
  const send = useSubmitOnce((details: SetupDetails) =>
    submit(details, () => {
      saved.current = true
    }),
  )
  const submitting = send.pending
  // Guardia de cambios sin guardar; `saved` lo apaga justo tras la escritura, antes de navegar al portal.
  const blocker = useUnsavedChanges(() => (logo !== undefined || banner !== undefined || description.trim() !== '') && !saved.current)
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
    // ÚNICA escritura: logo, banner y descripción viajan juntos al pulsar «Activar mi almacén».
    // Los archivos se eligieron y validaron en local; no se subió nada al elegirlos.
    const result = await send.run({ businessDescription: description, logo, banner })
    if (result.status === 'error') {
      setFormError(setupErrorMessage(result.error))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <>
      <p className="mt-2 text-ap-muted">Tu solicitud fue aprobada. Completa los datos para empezar a vender.</p>

      {/* Pasos ya hechos se pueden revisar; los siguientes se desbloquean al avanzar con «Siguiente». */}
      <Stepper
        className="mt-6 border-b border-ap-line pb-4"
        label="Pasos de la configuración"
        current={String(step)}
        onSelect={(id) => setStep(Number(id))}
        steps={STEPS.map((label, index) => ({
          id: String(index),
          label,
          status: index < step ? 'complete' : 'idle',
          locked: index > step,
        }))}
      />
      <p className="sr-only" aria-live="polite">
        Paso {step + 1} de {STEPS.length}: {STEPS[step]}
      </p>

      {formError ? (
        <div className="mt-6">
          <FormAlert ref={alertRef} title="No pudimos completar la configuración">
            {formError}
          </FormAlert>
        </div>
      ) : null}

      <form className="mt-6 flex flex-col gap-5" onSubmit={handleSubmit} noValidate aria-busy={submitting || undefined}>
        <section hidden={step !== 0} className="flex flex-col gap-5" aria-labelledby="setup-paso-0">
          <h2 id="setup-paso-0" ref={step === 0 ? headingRef : undefined} tabIndex={-1} className="text-block-title text-ap-ink outline-none">
            Marca de tu almacén
          </h2>
          <p className="-mt-3 text-sm text-ap-muted">Súbelos ahora o más adelante: ambos son opcionales.</p>
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
          <h2 id="setup-paso-1" ref={step === 1 ? headingRef : undefined} tabIndex={-1} className="text-block-title text-ap-ink outline-none">
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
          <h2 id="setup-paso-2" ref={step === 2 ? headingRef : undefined} tabIndex={-1} className="text-block-title text-ap-ink outline-none">
            Revisar y activar
          </h2>
          <dl className="flex flex-col divide-y divide-ap-line border-y border-ap-line text-sm">
            <div className="grid gap-1 py-3 sm:grid-cols-[8rem_minmax(0,1fr)] sm:gap-4">
              <dt className="font-semibold text-ap-ink">Logo</dt>
              <dd className="break-all text-ap-muted">{logo ? logo.name : 'Sin logo por ahora'}</dd>
            </div>
            <div className="grid gap-1 py-3 sm:grid-cols-[8rem_minmax(0,1fr)] sm:gap-4">
              <dt className="font-semibold text-ap-ink">Banner</dt>
              <dd className="break-all text-ap-muted">{banner ? banner.name : 'Sin banner por ahora'}</dd>
            </div>
            <div className="grid gap-1 py-3 sm:grid-cols-[8rem_minmax(0,1fr)] sm:gap-4">
              <dt className="font-semibold text-ap-ink">Descripción</dt>
              <dd className="break-words text-ap-muted">{description.trim() || 'Sin descripción por ahora'}</dd>
            </div>
          </dl>
          <p className="text-sm text-ap-muted">Al activar, tu almacén queda abierto y empieza tu prueba gratuita.</p>
        </section>

        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-ap-line pt-5">
          {step > 0 ? (
            <Button type="button" variant="outline" disabled={submitting} onClick={() => setStep(step - 1)}>
              Atrás
            </Button>
          ) : (
            <span />
          )}
          {step < LAST_STEP ? (
            <Button type="button" onClick={goNext}>
              Siguiente
            </Button>
          ) : (
            <Button type="submit" disabled={submitting}>
              {submitting ? 'Activando…' : 'Activar mi almacén'}
            </Button>
          )}
        </div>
        <UnsavedChangesDialog blocker={blocker} />
      </form>
    </>
  )
}

// ------------------------------------------------------------------ flujo con sesión

/** Al terminar, reemplaza la sesión con los tokens nuevos (rol de gerente) y entra al portal. */
function useFinish() {
  const navigate = useNavigate()
  return async function finish() {
    const session = await replaceSessionAfterSetup()
    navigate(session ? '/portal' : '/ingresar', { replace: true, state: { from: '/portal' } })
  }
}

/** Cuenta con sesión que no es la solicitante: ofrece cambiar de cuenta y volver aquí. */
function WrongAccount() {
  const navigate = useNavigate()
  const { signOut, pending } = useSignOut()
  const headingRef = useRef<HTMLHeadingElement>(null)
  useEffect(() => headingRef.current?.focus(), [])

  async function switchAccount() {
    await signOut()
    navigate(loginPathFor(SELLER_SETUP_PATH), { replace: true })
  }

  return (
    <div className={SHELL}>
      <title>Configura tu almacén | Autopartco</title>
      <p className="ap-index mb-2">Almacén / Configuración</p>
      <h1 ref={headingRef} tabIndex={-1} className="text-page-title text-ap-ink outline-none">
        Esta cuenta no puede configurar el almacén
      </h1>
      <p className="mt-2 text-ap-muted" role="status">
        Esta cuenta no tiene una configuración de almacén pendiente. Si el enlace era de otra cuenta, cámbiala.
      </p>
      <div className="mt-6 flex flex-col gap-3 sm:flex-row">
        <Button disabled={pending} onClick={() => void switchAccount()}>
          {pending ? 'Cerrando sesión…' : 'Cambiar de cuenta'}
        </Button>
        <Button variant="outline" nativeButton={false} render={<Link to="/" />}>
          Ir al inicio
        </Button>
      </div>
    </div>
  )
}

/**
 * `/seller/setup`: exige sesión. La cuenta solicitante, con su solicitud `CONFIGURATION_PENDING`, completa
 * marca y descripción, se activa y la sesión pasa a gerente del almacén. Un `?token=` de enlaces viejos
 * se ignora y se borra de la URL: la identidad sale siempre de la sesión (ADR 0003).
 */
export default function SellerSetupPage() {
  const { session, ready } = useSessionState()
  const seller = useSellerCta()
  const finish = useFinish()

  // Saca un `?token=` heredado de la barra y del historial. Se conserva history.state para no romper el router.
  useEffect(() => {
    if (new URLSearchParams(window.location.search).has('token')) {
      window.history.replaceState(window.history.state, '', SELLER_SETUP_PATH)
    }
  }, [])

  if (!ready) {
    return (
      <Shell title="Configura tu almacén">
        <LoadingState label="Cargando…" rows={2} className="mt-6" />
      </Shell>
    )
  }
  if (!session) return <Navigate to={loginPathFor(SELLER_SETUP_PATH)} replace />
  if (seller.state === 'loading') {
    return (
      <Shell title="Configura tu almacén">
        <LoadingState label="Buscando tu solicitud…" rows={2} className="mt-6" />
      </Shell>
    )
  }
  // Almacén ya activo (solicitud ACTIVE o rol de almacén): nada que configurar.
  if (seller.state === 'manage') return <Navigate to="/portal" replace />

  const application = seller.application
  if (seller.state === 'review' && application?.status === 'CONFIGURATION_PENDING') {
    return (
      <Shell title="Configura tu almacén">
        <SetupWizard
          submit={async (details, onSaved) => {
            await (await getSellerApplicationService()).completeSetupAuthenticated(application.applicationId, details)
            onSaved()
            await finish()
          }}
        />
      </Shell>
    )
  }
  return <WrongAccount />
}
