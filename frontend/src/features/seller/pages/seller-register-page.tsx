import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ClipboardCheckIcon, StoreIcon } from 'lucide-react'
import { FieldShell, FormAlert, INPUT_CLASS, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { useSessionState } from '@/features/auth/hooks'
import { accountKindOf } from '@/features/auth/lib/account-menu-items'
import { AccountFields } from '@/features/auth/components/account-fields'
import { ACCOUNT_FIELDS, readAccount, readPasswordConfirm, validateAccount } from '@/features/auth/lib/account'
import { AuthShell, SimulatedNotice } from '@/features/auth/components/auth-shell'
import { CheckEmail } from '@/features/auth/components/check-email'
import { compact, emailError, requiredText, type FieldErrors } from '@/features/auth/lib/validation'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { invalidateAsync } from '@/lib/use-async'
import { getSellerApplicationService } from '../api/seller-service'
import { SELLER_CACHE_PREFIX } from '../hooks'
import {
  DOCUMENT_ACCEPT,
  DOCUMENT_LABELS,
  DOCUMENT_MAX_BYTES,
  OPEN_APPLICATION_STATUSES,
  REQUEST_MAX_BYTES,
  REQUIRED_DOCUMENTS,
  type CurrentUserApplicationResult,
  type LegalType,
  type MyStoreApplication,
  type SellerApplicationResult,
  type SellerDocumentKey,
  type StoreApplicationStatus,
  type StoreRegistration,
} from '../api/types'

const ACCOUNT_PREFIX = 'vendedor-'
const STORE_FIELDS = ['businessName', 'nit', 'businessEmail'] as const
const LEGAL_TYPES: { value: LegalType; label: string; hint: string }[] = [
  { value: 'NATURAL_PERSON', label: 'Persona natural', hint: 'El almacén está a tu nombre.' },
  { value: 'LEGAL_ENTITY', label: 'Empresa (persona jurídica)', hint: 'El almacén es una sociedad con NIT propio.' },
]
const LOGIN_FOR_SELLER = `/ingresar?next=${encodeURIComponent('/vender/registro')}`
const LINK_CLASS = 'font-medium text-brand underline-offset-4 hover:underline'

const CRUMBS = [
  { label: 'Inicio', to: '/' },
  { label: 'Vender', to: '/vender' },
  { label: 'Registrar almacén' },
]

const storeId = (field: string) => `almacen-${field}`
const documentId = (key: SellerDocumentKey) => `documento-${key}`

/** Errores de negocio del backend que corresponden a un campo concreto. */
const ACCOUNT_FIELD_BY_CODE: Record<string, string> = {
  EMAIL_ALREADY_EXISTS: 'email',
  IDENTIFICATION_ALREADY_EXISTS: 'identification',
}

/** `StoreApplications.requireNoOpenApplication` (409): ya hay una solicitud en curso. */
const OPEN_APPLICATION_CODE = 'SELLER_APPLICATION_ALREADY_EXISTS'

const STATUS_LABEL: Record<StoreApplicationStatus, string> = {
  PENDING_REVIEW: 'En revisión',
  CONFIGURATION_PENDING: 'Aprobada: falta configurar el almacén',
  ACTIVE: 'Almacén activo',
  REJECTED: 'No aprobada',
}

/** Qué sigue según el estado que devolvió el servidor. */
function nextStep(status: StoreApplicationStatus, email: string): ReactNode {
  switch (status) {
    case 'PENDING_REVIEW':
      return (
        <>
          Revisaremos tus documentos y te escribiremos a <strong>{email}</strong>. Si la aprobamos, te enviaremos un enlace
          para configurar tu almacén. Elegirás tu plan después de la aprobación; mientras tanto quedas con la prueba gratuita.
        </>
      )
    case 'CONFIGURATION_PENDING':
      return (
        <>
          Te enviamos a <strong>{email}</strong> un enlace para subir el logo y el banner de tu almacén. Ábrelo para terminar
          la configuración.
        </>
      )
    case 'ACTIVE':
      return 'Tu almacén ya está activo. Entra al portal para administrarlo. Si no ves el portal en tu menú, cierra sesión y vuelve a ingresar.'
    case 'REJECTED':
      return 'Esta solicitud no fue aprobada. Puedes enviar una nueva con los datos corregidos.'
  }
}

function formatDate(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleDateString('es-CO', { day: 'numeric', month: 'long', year: 'numeric' })
}

function megabytes(bytes: number): string {
  return `${(bytes / (1024 * 1024)).toLocaleString('es-CO', { maximumFractionDigits: 1 })} MB`
}

/** Reparte `details` del backend (`account.email`, `store.nit`, `legalType`) por bloque. */
function splitServerErrors(fieldErrors: Readonly<Record<string, string>>) {
  const account: FieldErrors = {}
  const store: FieldErrors = {}
  let legalType: string | undefined
  for (const [path, message] of Object.entries(fieldErrors)) {
    const [group, field] = path.split('.')
    if (group === 'account' && field) account[field] = message
    else if (group === 'store' && field) store[field] = message
    else if (group === 'legalType') legalType = message
  }
  return { account, store, legalType }
}

function Section({ title, description, children }: { title: string; description?: ReactNode; children: ReactNode }) {
  return (
    <fieldset className="flex min-w-0 flex-col gap-5 border-t border-border pt-6 first:border-t-0 first:pt-0">
      <legend className="float-left mb-1 w-full">
        <span className="block text-block-title">{title}</span>
      </legend>
      {description ? <div className="clear-both -mt-3 text-sm text-muted-foreground">{description}</div> : null}
      <div className="clear-both">{children}</div>
    </fieldset>
  )
}

/** Recuadro con el nombre del almacén, el estado devuelto por el servidor y qué sigue. */
function StatusPanel({ businessName, status, children }: { businessName: string; status: StoreApplicationStatus; children: ReactNode }) {
  return (
    <div className="flex w-full flex-col gap-3 rounded-xl bg-concrete p-4 text-sm">
      <p className="flex items-center gap-2 font-semibold">
        <ClipboardCheckIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
        <span>
          {businessName}: {STATUS_LABEL[status].toLowerCase()}
        </span>
      </p>
      <div className="text-muted-foreground">{children}</div>
    </div>
  )
}

function Loading({ message }: { message: string }) {
  return (
    <AuthShell width="wide" crumbs={CRUMBS} label="Para almacenes" title="Registra tu almacén">
      <title>Registrar almacén | Autopartco</title>
      <div role="status" aria-live="polite" className="flex flex-col gap-3">
        <span className="sr-only">{message}</span>
        <div className="h-5 w-2/3 animate-pulse rounded-md bg-muted" aria-hidden />
        <div className="h-11 w-full animate-pulse rounded-md bg-muted" aria-hidden />
        <div className="h-11 w-full animate-pulse rounded-md bg-muted" aria-hidden />
      </div>
    </AuthShell>
  )
}

// ---------------------------------------------------------------- formulario

type FormError = { title: string; body?: ReactNode }

type FormMode =
  | { kind: 'public'; onResult: (result: SellerApplicationResult) => void }
  | {
      kind: 'current-user'
      email: string
      onResult: (result: CurrentUserApplicationResult, businessName: string) => void
      /** El servidor respondió que ya hay una solicitud en curso. */
      onAlreadyOpen: () => void
    }

/**
 * Formulario del almacén. En modo `public` incluye los datos de acceso y llama a
 * `POST /store-applications`; en modo `current-user` usa la cuenta con sesión
 * (`POST /me/store-applications`) y no pide cuenta.
 */
function ApplicationForm({ mode, notice }: { mode: FormMode; notice?: ReactNode }) {
  const withAccount = mode.kind === 'public'
  const [legalType, setLegalType] = useState<LegalType>('NATURAL_PERSON')
  const [accountErrors, setAccountErrors] = useState<FieldErrors>({})
  const [storeErrors, setStoreErrors] = useState<FieldErrors>({})
  const [documentErrors, setDocumentErrors] = useState<Partial<Record<SellerDocumentKey, string>>>({})
  const [legalTypeError, setLegalTypeError] = useState<string>()
  const [formError, setFormError] = useState<FormError>()
  const [submitting, setSubmitting] = useState(false)
  const alertRef = useRef<HTMLDivElement>(null)
  const required = REQUIRED_DOCUMENTS[legalType]

  /** Enfoca el primer campo con error en el orden visual: almacén, documentos, cuenta. */
  function focusFirst(store: FieldErrors, documents: Partial<Record<SellerDocumentKey, string>>, account: FieldErrors) {
    const ids = [
      ...STORE_FIELDS.filter((field) => store[field]).map(storeId),
      ...required.filter((key) => documents[key]).map(documentId),
      ...ACCOUNT_FIELDS.filter((field) => account[field]).map((field) => `${ACCOUNT_PREFIX}${field}`),
    ]
    if (ids[0]) document.getElementById(ids[0])?.focus()
  }

  function showFormError(error: FormError) {
    setFormError(error)
    requestAnimationFrame(() => alertRef.current?.focus())
  }

  /** Errores propios de la solicitud con sesión. Devuelve `true` si lo manejó. */
  function handleCurrentUserError(error: ApiError, email: string): boolean {
    switch (error.code) {
      case 'SELLER_EMAIL_NOT_VERIFIED':
        showFormError({
          title: 'Verifica tu correo antes de solicitar el almacén.',
          body: (
            <>
              Abre el enlace que te enviamos a <strong>{email}</strong> cuando creaste la cuenta (revisa también la carpeta
              de spam). Después vuelve a esta página y envía la solicitud.
            </>
          ),
        })
        return true
      case 'SELLER_APPLICATION_CUSTOMER_ONLY':
        showFormError({
          title: 'Solo una cuenta de cliente puede solicitar un almacén.',
          body: (
            <>
              Esta cuenta ya tiene otro tipo de acceso. Si administras un almacén,{' '}
              <Link to="/portal" className={LINK_CLASS}>
                entra al portal
              </Link>
              .
            </>
          ),
        })
        return true
      case OPEN_APPLICATION_CODE:
        if (mode.kind === 'current-user') mode.onAlreadyOpen()
        return true
      default:
        return false
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const store: StoreRegistration = {
      businessName: String(data.get(storeId('businessName')) ?? '').trim(),
      nit: String(data.get(storeId('nit')) ?? '').trim(),
      businessEmail: String(data.get(storeId('businessEmail')) ?? '').trim(),
    }
    const account = withAccount ? readAccount(data, ACCOUNT_PREFIX) : undefined
    const documents: Partial<Record<SellerDocumentKey, File>> = {}
    const nextDocumentErrors: Partial<Record<SellerDocumentKey, string>> = {}
    let total = 0
    for (const key of required) {
      const file = data.get(documentId(key))
      if (!(file instanceof File) || file.size === 0) {
        nextDocumentErrors[key] = `Adjunta: ${DOCUMENT_LABELS[key]}.`
      } else if (file.size > DOCUMENT_MAX_BYTES) {
        nextDocumentErrors[key] = `El archivo pesa ${megabytes(file.size)}; el máximo es 10 MB.`
      } else {
        documents[key] = file
        total += file.size
      }
    }
    const nextStoreErrors = compact({
      businessName: requiredText(store.businessName, 'El nombre del almacén', 255),
      nit: requiredText(store.nit, 'El NIT', 20),
      businessEmail: emailError(store.businessEmail, 200, 'El correo del almacén'),
    })
    const nextAccountErrors = account ? validateAccount(account, readPasswordConfirm(data, ACCOUNT_PREFIX)) : {}

    setStoreErrors(nextStoreErrors)
    setDocumentErrors(nextDocumentErrors)
    setAccountErrors(nextAccountErrors)
    setLegalTypeError(undefined)
    setFormError(undefined)
    if (
      Object.keys(nextStoreErrors).length > 0 ||
      Object.keys(nextDocumentErrors).length > 0 ||
      Object.keys(nextAccountErrors).length > 0
    ) {
      focusFirst(nextStoreErrors, nextDocumentErrors, nextAccountErrors)
      return
    }
    if (total > REQUEST_MAX_BYTES) {
      showFormError({ title: `Los documentos suman ${megabytes(total)}; entre todos no pueden pasar de 35 MB.` })
      return
    }

    setSubmitting(true)
    try {
      const service = await getSellerApplicationService()
      if (mode.kind === 'public' && account) {
        mode.onResult(await service.submit({ account, store, legalType, documents }))
      } else if (mode.kind === 'current-user') {
        mode.onResult(await service.applyAsCurrentUser({ store, legalType, documents }), store.businessName)
      }
    } catch (error) {
      if (!(error instanceof ApiError)) {
        showFormError({ title: 'No pudimos enviar la solicitud. Revisa tu conexión e inténtalo de nuevo.' })
        return
      }
      if (mode.kind === 'current-user' && handleCurrentUserError(error, mode.email)) return
      const accountField = withAccount && error.code ? ACCOUNT_FIELD_BY_CODE[error.code] : undefined
      if (accountField) {
        const next = { [accountField]: error.message }
        setAccountErrors(next)
        focusFirst({}, {}, next)
        return
      }
      const split = splitServerErrors(error.fieldErrors)
      if (Object.keys(split.account).length > 0 || Object.keys(split.store).length > 0 || split.legalType) {
        setAccountErrors(split.account)
        setStoreErrors(split.store)
        setLegalTypeError(split.legalType)
        focusFirst(split.store, {}, split.account)
        if (split.legalType && Object.keys(split.account).length === 0 && Object.keys(split.store).length === 0) {
          document.getElementById(`tipo-${legalType}`)?.focus()
        }
        return
      }
      // SELLER_DOCUMENTS_INCOMPLETE, UNSUPPORTED_FILE_TYPE, tamaño excedido u otros: mensaje del servidor.
      showFormError({ title: error.message })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-8" aria-busy={submitting || undefined}>
      {formError ? (
        <FormAlert ref={alertRef} title={formError.title}>
          {formError.body}
        </FormAlert>
      ) : null}
      {notice}
      <p className="-mb-2 text-sm text-muted-foreground">Todos los campos son obligatorios.</p>

      <Section title="Tu almacén">
        <div className="grid gap-5 sm:grid-cols-2">
          <TextField
            id={storeId('businessName')}
            name={storeId('businessName')}
            label="Nombre del almacén"
            autoComplete="organization"
            maxLength={255}
            required
            error={storeErrors.businessName}
            containerClassName="sm:col-span-2"
          />
          <TextField
            id={storeId('nit')}
            name={storeId('nit')}
            label="NIT"
            hint="Como aparece en el RUT."
            autoComplete="off"
            maxLength={20}
            required
            error={storeErrors.nit}
          />
          <TextField
            id={storeId('businessEmail')}
            name={storeId('businessEmail')}
            type="email"
            label="Correo del almacén"
            autoComplete="email"
            inputMode="email"
            maxLength={200}
            required
            error={storeErrors.businessEmail}
          />
        </div>
      </Section>

      <Section
        title="Tipo de persona y documentos"
        description="PDF, PNG o JPG de hasta 10 MB cada uno. Los documentos los ve solo el equipo que revisa tu solicitud."
      >
        <div className="flex flex-col gap-5">
          <div role="radiogroup" aria-label="Tipo de persona" aria-describedby={legalTypeError ? 'tipo-error' : undefined} className="grid gap-2 sm:grid-cols-2">
            {LEGAL_TYPES.map((option) => (
              <label
                key={option.value}
                className={cn(
                  'flex min-h-11 cursor-pointer items-start gap-3 rounded-xl p-4 shadow-[0_0_0_1px_rgb(0_0_0/0.1)] transition-shadow has-[:checked]:shadow-[0_0_0_2px_var(--color-foreground)]',
                )}
              >
                <input
                  id={`tipo-${option.value}`}
                  type="radio"
                  name="legalType"
                  value={option.value}
                  checked={legalType === option.value}
                  onChange={() => {
                    setLegalType(option.value)
                    setDocumentErrors({})
                  }}
                  className="mt-1 size-4 shrink-0 accent-[var(--color-foreground)]"
                />
                <span className="flex flex-col gap-0.5">
                  <span className="font-semibold">{option.label}</span>
                  <span className="text-sm text-muted-foreground">{option.hint}</span>
                </span>
              </label>
            ))}
          </div>
          {legalTypeError ? (
            <p id="tipo-error" className="text-sm font-medium text-destructive">
              {legalTypeError}
            </p>
          ) : null}
          <div className="grid gap-5 sm:grid-cols-2">
            {required.map((key) => (
              <FieldShell key={`${legalType}-${key}`} id={documentId(key)} label={DOCUMENT_LABELS[key]} error={documentErrors[key]}>
                {(describedBy) => (
                  <input
                    id={documentId(key)}
                    name={documentId(key)}
                    type="file"
                    accept={DOCUMENT_ACCEPT}
                    required
                    aria-invalid={documentErrors[key] ? true : undefined}
                    aria-describedby={describedBy}
                    className={cn(
                      INPUT_CLASS,
                      'h-auto min-h-11 cursor-pointer py-1.5 file:mr-3 file:h-8 file:cursor-pointer file:rounded-md file:border-0 file:bg-concrete file:px-3 file:text-sm file:font-semibold file:text-foreground',
                    )}
                  />
                )}
              </FieldShell>
            ))}
          </div>
        </div>
      </Section>

      {withAccount ? (
        <Section title="Tus datos de acceso" description="Con este correo y esta contraseña entrarás a Autopartco. Te pediremos verificar el correo.">
          <AccountFields prefix={ACCOUNT_PREFIX} errors={accountErrors} emailLabel="Tu correo personal" />
        </Section>
      ) : null}

      <Section title="Plan">
        {/* TODO(flujo): planes y pago (Wompi) después de la aprobación; aquí no se envía `planId`. */}
        <p className="rounded-lg bg-concrete p-4 text-sm">
          Elegirás tu plan después de la aprobación. Mientras tanto, tu almacén queda con la prueba gratuita.
        </p>
      </Section>

      <div className="flex flex-col gap-4 border-t border-border pt-6">
        <Button type="submit" className="h-11 w-full px-6 text-base sm:w-fit" disabled={submitting}>
          {submitting ? 'Enviando solicitud…' : 'Enviar solicitud'}
        </Button>
        {withAccount ? (
          <p className="text-sm text-muted-foreground">
            ¿Solo buscas repuestos?{' '}
            <Link to="/registro/cliente" className={LINK_CLASS}>
              Crea una cuenta de cliente
            </Link>
          </p>
        ) : null}
        <SimulatedNotice />
      </div>
    </form>
  )
}

// ---------------------------------------------------------------- sin sesión

function PublicApplication() {
  const [result, setResult] = useState<SellerApplicationResult>()

  if (result) {
    return (
      <AuthShell width="wide" crumbs={CRUMBS} label="Solicitud enviada" title="Recibimos tu solicitud">
        <title>Solicitud enviada | Autopartco</title>
        <CheckEmail email={result.email} sent={result.verificationEmailSent}>
          <div className="mt-2 w-full">
            <StatusPanel businessName={result.businessName} status={result.status}>
              Revisaremos tus documentos. Si la aprobamos, te enviaremos un enlace para configurar tu almacén. Elegirás tu
              plan después de la aprobación; mientras tanto quedas con la prueba gratuita.
            </StatusPanel>
          </div>
          <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
            Ir al inicio
          </Button>
        </CheckEmail>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      width="wide"
      crumbs={CRUMBS}
      label="Para almacenes"
      title="Registra tu almacén"
      description={
        <>
          <p>Crea tu cuenta de vendedor y envía los documentos. Revisamos cada solicitud antes de publicar tu almacén.</p>
          <p className="mt-2 text-sm">
            ¿Ya tienes cuenta en Autopartco?{' '}
            <Link to={LOGIN_FOR_SELLER} className={LINK_CLASS}>
              Ingresa
            </Link>{' '}
            y solicita el almacén con ella.
          </p>
        </>
      }
    >
      <title>Registrar almacén | Autopartco</title>
      <ApplicationForm mode={{ kind: 'public', onResult: setResult }} />
    </AuthShell>
  )
}

// ---------------------------------------------------------------- cliente con sesión

type ApplicationsState =
  | { status: 'loading' }
  | { status: 'error' }
  | { status: 'ready'; open?: MyStoreApplication; rejected?: MyStoreApplication }

/** La más reciente primero: el backend no garantiza el orden. */
function newestFirst(list: MyStoreApplication[]): MyStoreApplication[] {
  return [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
}

async function fetchApplications(): Promise<ApplicationsState> {
  try {
    const service = await getSellerApplicationService()
    const list = newestFirst(await service.myApplications())
    return {
      status: 'ready',
      open: list.find((application) => OPEN_APPLICATION_STATUSES.includes(application.status)),
      rejected: list.find((application) => application.status === 'REJECTED'),
    }
  } catch {
    return { status: 'error' }
  }
}

function CustomerApplication({ email }: { email: string }) {
  const [state, setState] = useState<ApplicationsState>({ status: 'loading' })
  const [created, setCreated] = useState<{ result: CurrentUserApplicationResult; businessName: string }>()

  /** Recarga a petición (reintento o solicitud abierta detectada al enviar). */
  const load = useCallback(() => {
    setState({ status: 'loading' })
    void fetchApplications().then(setState)
  }, [])

  useEffect(() => {
    let active = true
    void fetchApplications().then((next) => active && setState(next))
    return () => {
      active = false
    }
  }, [])

  if (created) {
    return (
      <AuthShell width="wide" crumbs={CRUMBS} label="Solicitud enviada" title="Recibimos tu solicitud">
        <title>Solicitud enviada | Autopartco</title>
        <div className="flex flex-col items-start gap-5">
          <StatusPanel businessName={created.businessName} status={created.result.status}>
            {nextStep(created.result.status, email)}
          </StatusPanel>
          <p className="text-sm text-muted-foreground">
            La solicitud quedó asociada a tu cuenta; no necesitas verificar otro correo.
          </p>
          <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
            Ir al inicio
          </Button>
          <SimulatedNotice />
        </div>
      </AuthShell>
    )
  }

  if (state.status === 'loading') return <Loading message="Consultando tus solicitudes de almacén…" />

  if (state.status === 'error') {
    return (
      <AuthShell width="wide" crumbs={CRUMBS} label="Para almacenes" title="Registra tu almacén">
        <title>Registrar almacén | Autopartco</title>
        <div className="flex flex-col items-start gap-4">
          <FormAlert title="No pudimos consultar tus solicitudes de almacén.">
            Revisa tu conexión e inténtalo de nuevo.
          </FormAlert>
          <Button className="h-11 px-5" onClick={() => void load()}>
            Reintentar
          </Button>
        </div>
      </AuthShell>
    )
  }

  if (state.open) {
    const open = state.open
    const date = formatDate(open.createdAt)
    return (
      <AuthShell
        width="wide"
        crumbs={CRUMBS}
        label="Para almacenes"
        title="Ya tienes una solicitud en curso"
        description={<p>Solo puedes tener una solicitud de almacén abierta a la vez.</p>}
      >
        <title>Tu solicitud de almacén | Autopartco</title>
        <div className="flex flex-col items-start gap-5">
          <StatusPanel businessName={open.businessName} status={open.status}>
            {date ? <p className="mb-2">Enviada el {date}.</p> : null}
            <p>{nextStep(open.status, email)}</p>
          </StatusPanel>
          <div className="flex flex-wrap gap-2">
            {open.status === 'ACTIVE' ? (
              <Button className="h-11 px-5" nativeButton={false} render={<Link to="/portal" />}>
                Ir al portal del almacén
              </Button>
            ) : null}
            <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
              Ir al inicio
            </Button>
          </div>
        </div>
      </AuthShell>
    )
  }

  const rejected = state.rejected
  return (
    <AuthShell
      width="wide"
      crumbs={CRUMBS}
      label="Para almacenes"
      title="Registra tu almacén"
      description={
        <p>
          Solicitarás el almacén con tu cuenta <strong className="font-semibold text-foreground">{email}</strong>. Envía los
          datos del almacén y los documentos; revisamos cada solicitud antes de publicarlo.
        </p>
      }
    >
      <title>Registrar almacén | Autopartco</title>
      <ApplicationForm
        mode={{
          kind: 'current-user',
          email,
          onResult: (result, businessName) => {
            // El menú y los CTA del marketplace pasan a "Ver mi solicitud".
            invalidateAsync(SELLER_CACHE_PREFIX)
            setCreated({ result, businessName })
          },
          onAlreadyOpen: () => void load(),
        }}
        notice={
          rejected ? (
            <FormAlert tone="info" title={`Tu solicitud anterior para ${rejected.businessName} no fue aprobada.`}>
              {rejected.reviewNotes ? <p className="mb-1">Motivo: {rejected.reviewNotes}</p> : null}
              <p>Puedes enviar una nueva con los datos y documentos corregidos.</p>
            </FormAlert>
          ) : undefined
        }
      />
    </AuthShell>
  )
}

// ---------------------------------------------------------------- otras cuentas

function NotEligible({ kind }: { kind: 'store' | 'other' }) {
  return (
    <AuthShell
      width="wide"
      crumbs={CRUMBS}
      label="Para almacenes"
      title={kind === 'store' ? 'Tu cuenta ya administra un almacén' : 'Esta cuenta no puede solicitar un almacén'}
    >
      <title>Registrar almacén | Autopartco</title>
      <div className="flex flex-col items-start gap-5">
        <div className="flex items-start gap-3 rounded-xl bg-concrete p-4 text-sm">
          <StoreIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
          <p>
            {kind === 'store'
              ? 'Administra tu almacén, sus productos, pedidos y plan desde el portal. Para registrar otro almacén, escríbenos.'
              : 'Solo una cuenta de cliente puede solicitar un almacén. Ingresa con tu cuenta de cliente para enviar la solicitud.'}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          {kind === 'store' ? (
            <Button className="h-11 px-5" nativeButton={false} render={<Link to="/portal" />}>
              Ir al portal del almacén
            </Button>
          ) : null}
          <Button variant="outline" className="h-11 px-5" nativeButton={false} render={<Link to="/" />}>
            Ir al inicio
          </Button>
        </div>
      </div>
    </AuthShell>
  )
}

/**
 * `/vender/registro`. Según la sesión (los roles solo deciden qué se muestra; el servidor autoriza):
 * - Sin sesión: `POST /store-applications` (multipart, público) crea la cuenta y la solicitud.
 * - Cliente: `POST /me/store-applications` con su cuenta; si ya tiene una solicitud en curso
 *   (`GET /me/store-applications`), muestra su estado en vez del formulario.
 * - Gerente de almacén u otra cuenta: no hay formulario.
 * TODO(flujo): la elección o pago del plan queda fuera. La configuración del almacén (logo y
 * banner) se hace en `/seller/setup` con la sesión de la cuenta solicitante.
 */
export default function SellerRegisterPage() {
  const { session, ready } = useSessionState()
  if (!ready) return <Loading message="Cargando tu sesión…" />
  if (!session) return <PublicApplication />
  const kind = accountKindOf(session.roles)
  if (kind === 'customer') return <CustomerApplication key={session.user.email} email={session.user.email} />
  return <NotEligible kind={kind === 'store' ? 'store' : 'other'} />
}
