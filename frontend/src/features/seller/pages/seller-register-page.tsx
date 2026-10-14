import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ClipboardCheckIcon } from 'lucide-react'
import { FieldShell, FormAlert, INPUT_CLASS, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { AccountFields } from '@/features/auth/components/account-fields'
import { ACCOUNT_FIELDS, readAccount, readPasswordConfirm, validateAccount } from '@/features/auth/lib/account'
import { AuthShell, SimulatedNotice } from '@/features/auth/components/auth-shell'
import { CheckEmail } from '@/features/auth/components/check-email'
import { compact, emailError, requiredText, type FieldErrors } from '@/features/auth/lib/validation'
import { ApiError } from '@/lib/api'
import { cn } from '@/lib/utils'
import { getSellerApplicationService } from '../api/seller-service'
import {
  DOCUMENT_ACCEPT,
  DOCUMENT_LABELS,
  DOCUMENT_MAX_BYTES,
  REQUEST_MAX_BYTES,
  REQUIRED_DOCUMENTS,
  type LegalType,
  type SellerApplicationResult,
  type SellerDocumentKey,
  type StoreRegistration,
} from '../api/types'

const ACCOUNT_PREFIX = 'vendedor-'
const STORE_FIELDS = ['businessName', 'nit', 'businessEmail'] as const
const LEGAL_TYPES: { value: LegalType; label: string; hint: string }[] = [
  { value: 'NATURAL_PERSON', label: 'Persona natural', hint: 'El almacén está a tu nombre.' },
  { value: 'LEGAL_ENTITY', label: 'Empresa (persona jurídica)', hint: 'El almacén es una sociedad con NIT propio.' },
]

const storeId = (field: string) => `almacen-${field}`
const documentId = (key: SellerDocumentKey) => `documento-${key}`

/** Errores de negocio del backend que corresponden a un campo concreto. */
const ACCOUNT_FIELD_BY_CODE: Record<string, string> = {
  EMAIL_ALREADY_EXISTS: 'email',
  IDENTIFICATION_ALREADY_EXISTS: 'identification',
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

/**
 * `/vender/registro`: `POST /store-applications` (multipart, público). Crea la cuenta
 * del vendedor y la solicitud del almacén en revisión.
 * TODO(flujo): `/store-applications/setup` (logo y banner con el token del correo de
 * aprobación), el seguimiento de la solicitud y la elección o pago del plan quedan fuera.
 */
export default function SellerRegisterPage() {
  const [legalType, setLegalType] = useState<LegalType>('NATURAL_PERSON')
  const [accountErrors, setAccountErrors] = useState<FieldErrors>({})
  const [storeErrors, setStoreErrors] = useState<FieldErrors>({})
  const [documentErrors, setDocumentErrors] = useState<Partial<Record<SellerDocumentKey, string>>>({})
  const [legalTypeError, setLegalTypeError] = useState<string>()
  const [formError, setFormError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<SellerApplicationResult>()
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

  function showFormError(message: string) {
    setFormError(message)
    requestAnimationFrame(() => alertRef.current?.focus())
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const store: StoreRegistration = {
      businessName: String(data.get(storeId('businessName')) ?? '').trim(),
      nit: String(data.get(storeId('nit')) ?? '').trim(),
      businessEmail: String(data.get(storeId('businessEmail')) ?? '').trim(),
    }
    const account = readAccount(data, ACCOUNT_PREFIX)
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
    const nextAccountErrors = validateAccount(account, readPasswordConfirm(data, ACCOUNT_PREFIX))

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
      showFormError(`Los documentos suman ${megabytes(total)}; entre todos no pueden pasar de 35 MB.`)
      return
    }

    setSubmitting(true)
    try {
      const service = await getSellerApplicationService()
      setResult(await service.submit({ account, store, legalType, documents }))
    } catch (error) {
      if (!(error instanceof ApiError)) {
        showFormError('No pudimos enviar la solicitud. Inténtalo de nuevo.')
        return
      }
      const accountField = error.code ? ACCOUNT_FIELD_BY_CODE[error.code] : undefined
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
      showFormError(error.message)
    } finally {
      setSubmitting(false)
    }
  }

  const crumbs = [
    { label: 'Inicio', to: '/' },
    { label: 'Vender', to: '/vender' },
    { label: 'Registrar almacén' },
  ]

  if (result) {
    return (
      <AuthShell width="wide" crumbs={crumbs} label="Solicitud enviada" title="Recibimos tu solicitud">
        <title>Solicitud enviada | Autopartco</title>
        <CheckEmail email={result.email} sent={result.verificationEmailSent}>
          <div className="mt-2 flex w-full flex-col gap-3 rounded-xl bg-concrete p-4 text-sm">
            <p className="flex items-center gap-2 font-semibold">
              <ClipboardCheckIcon className="size-4 text-muted-foreground" aria-hidden />
              {result.businessName}: {result.status === 'PENDING_REVIEW' ? 'en revisión' : 'solicitud recibida'}
            </p>
            <p className="text-muted-foreground">
              Revisaremos tus documentos. Si la aprobamos, te enviaremos un enlace para configurar tu almacén. Elegirás
              tu plan después de la aprobación; mientras tanto quedas con la prueba gratuita.
            </p>
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
      crumbs={crumbs}
      label="Para almacenes"
      title="Registra tu almacén"
      description={
        <p>
          Crea tu cuenta de vendedor y envía los documentos. Revisamos cada solicitud antes de publicar tu almacén.
        </p>
      }
    >
      <title>Registrar almacén | Autopartco</title>
      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-8" aria-busy={submitting || undefined}>
        {formError ? <FormAlert ref={alertRef} title={formError} /> : null}
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

        <Section title="Tus datos de acceso" description="Con este correo y esta contraseña entrarás a Autopartco. Te pediremos verificar el correo.">
          <AccountFields prefix={ACCOUNT_PREFIX} errors={accountErrors} emailLabel="Tu correo personal" />
        </Section>

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
          <p className="text-sm text-muted-foreground">
            ¿Solo buscas repuestos?{' '}
            <Link to="/registro/cliente" className="font-medium text-brand underline-offset-4 hover:underline">
              Crea una cuenta de cliente
            </Link>
          </p>
          <SimulatedNotice />
        </div>
      </form>
    </AuthShell>
  )
}
