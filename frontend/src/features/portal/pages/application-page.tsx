import { useState } from 'react'
import { Link } from 'react-router'
import { ArrowLeftIcon, FileClockIcon, MailIcon } from 'lucide-react'
import { ApiError } from '@/lib/api'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/lib/use-async'
import { getSellerApplicationService } from '@/features/seller/api/seller-service'
import type { MyStoreApplication, StoreApplicationStatus } from '@/features/seller/api/types'
import { SELLER_CACHE_PREFIX, SELLER_SETUP_PATH } from '@/features/seller/hooks'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryError } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { PortalHeader } from '../layout/portal-header'
import { formatDate, type Tone } from '../lib/labels'

const STATUS: Record<StoreApplicationStatus, { label: string; tone: Tone; next: string }> = {
  PENDING_REVIEW: {
    label: 'En revisión',
    tone: 'warning',
    next: 'Estamos revisando tus documentos. Te escribiremos al correo de tu cuenta apenas haya respuesta; no necesitas hacer nada más.',
  },
  CONFIGURATION_PENDING: {
    label: 'Falta configurar',
    tone: 'warning',
    next: 'Aprobamos tu solicitud. Falta subir el logo y el banner y activar tu almacén; hazlo con la cuenta con la que solicitaste (el correo de aprobación solo te lleva aquí).',
  },
  ACTIVE: {
    label: 'Activo',
    tone: 'success',
    next: 'Tu almacén ya está activo. Si no lo ves en tu menú, cierra sesión y vuelve a ingresar.',
  },
  REJECTED: {
    label: 'No aprobada',
    tone: 'danger',
    next: 'Esta solicitud no fue aprobada. Puedes enviar una nueva con los datos y documentos corregidos.',
  },
}

/** Reenvía el correo de configuración con la sesión; respuesta neutra y espera si el servidor responde 429. */
function ResendSetupEmail({ applicationId }: { applicationId: string }) {
  const [sending, setSending] = useState(false)
  const [notice, setNotice] = useState<{ error: boolean; text: string }>()

  async function resend() {
    setSending(true)
    setNotice(undefined)
    try {
      await (await getSellerApplicationService()).requestSetupLink(applicationId)
      setNotice({ error: false, text: 'Si tu solicitud sigue pendiente de configurar, te enviamos el correo. Puede tardar unos minutos; revisa también tu carpeta de spam.' })
    } catch (error) {
      const cooldown = error instanceof ApiError && error.code === 'SELLER_SETUP_LINK_COOLDOWN'
      setNotice({
        error: true,
        text: cooldown
          ? 'Ya te enviamos el correo hace poco. Espera 5 minutos antes de pedir otro y revisa tu carpeta de spam.'
          : 'No pudimos reenviar el correo. Inténtalo de nuevo en unos minutos.',
      })
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="flex flex-col items-start gap-2">
      <Button variant="outline" className="h-11 gap-2 px-5" disabled={sending} onClick={() => void resend()}>
        <MailIcon className="size-4" aria-hidden />
        {sending ? 'Enviando…' : 'Reenviarme el correo de configuración'}
      </Button>
      <div aria-live="polite">
        {notice ? <p className={notice.error ? 'text-sm font-medium text-destructive' : 'text-sm text-muted-foreground'}>{notice.text}</p> : null}
      </div>
    </div>
  )
}

function ApplicationCard({ application }: { application: MyStoreApplication }) {
  const status = STATUS[application.status]
  return (
    <article className="flex flex-col gap-3 rounded-xl bg-card p-5 ring-1 ring-foreground/10">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h2 className="text-lg leading-tight font-extrabold tracking-tight">{application.businessName}</h2>
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      </div>
      <p className="text-sm text-muted-foreground">Enviada el {formatDate(application.createdAt)}</p>
      <p className="text-sm">{status.next}</p>
      {application.reviewNotes ? (
        <div className="rounded-lg bg-concrete p-3 text-sm">
          <p className="label-mono text-muted-foreground">Comentarios de la revisión</p>
          <p className="mt-1">{application.reviewNotes}</p>
        </div>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {application.status === 'CONFIGURATION_PENDING' ? (
          <>
            <Button className="h-11 px-5" nativeButton={false} render={<Link to={SELLER_SETUP_PATH} />}>
              Continuar configuración
            </Button>
            <ResendSetupEmail applicationId={application.applicationId} />
          </>
        ) : null}
        {application.status === 'ACTIVE' ? (
          <Button className="h-11 px-5" nativeButton={false} render={<Link to="/portal" />}>
            Ir a mi almacén
          </Button>
        ) : null}
        {application.status === 'REJECTED' ? (
          <Button className="h-11 px-5" nativeButton={false} render={<Link to="/vender/registro" />}>
            Enviar una nueva solicitud
          </Button>
        ) : null}
      </div>
    </article>
  )
}

/** `/portal/solicitud`: estado de las solicitudes de almacén de la cuenta (`GET /me/store-applications`). */
export default function ApplicationPage() {
  const result = useAsync<MyStoreApplication[]>(`${SELLER_CACHE_PREFIX}applications:portal`, async () =>
    (await getSellerApplicationService()).myApplications(),
  )

  let content
  if (result.status === 'error') {
    content = <QueryError error={result.error} what="tu solicitud de almacén" onRetry={result.retry} />
  } else if (result.data === undefined) {
    content = <ListSkeleton rows={1} label="Cargando tu solicitud" />
  } else if (result.data.length === 0) {
    content = (
      <EmptyState
        icon={<FileClockIcon />}
        title="Aún no has enviado una solicitud de almacén"
        description="Registra tu almacén con tus datos y documentos; revisamos cada solicitud antes de publicarlo."
      >
        <Button className="h-11 px-5" nativeButton={false} render={<Link to="/vender/registro" />}>
          Registra tu almacén
        </Button>
      </EmptyState>
    )
  } else {
    const list = [...result.data].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
    content = (
      <ul className="flex flex-col gap-3">
        {list.map((application) => (
          <li key={application.applicationId}>
            <ApplicationCard application={application} />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div className="min-h-svh bg-background">
      <PortalHeader innerClassName="mx-auto w-full max-w-3xl px-4 sm:px-6 lg:px-6" />
      <main id="contenido" className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
        <title>Mi solicitud de almacén | Autopartco</title>
        <Link to="/portal" className="mb-4 inline-flex min-h-11 items-center gap-1.5 text-sm font-medium text-brand underline-offset-4 hover:underline">
          <ArrowLeftIcon className="size-4" aria-hidden />
          Mis almacenes
        </Link>
        <PageHeader label="Portal de almacenes" title="Mi solicitud de almacén" description="Estado de tu registro y lo que sigue." />
        {content}
      </main>
    </div>
  )
}
