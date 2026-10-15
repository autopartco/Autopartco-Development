import { Link, Navigate } from 'react-router'
import { ArrowRightIcon, StoreIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Logo } from '@/components/common/logo'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { initials } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { getPortalService } from '../api/portal-service'
import type { MyStore, StoreApplicationStatus } from '../api/types'
import { ListSkeleton } from '../components/list-skeleton'
import { QueryError } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { PortalAccountMenu } from '../layout/portal-account-menu'
import { formatDate, SUBSCRIPTION_STATUS_LABEL, SUBSCRIPTION_STATUS_TONE, type Tone } from '../lib/labels'

const APPLICATION_LABEL: Record<StoreApplicationStatus, { label: string; tone: Tone }> = {
  ACTIVE: { label: 'Activo', tone: 'success' },
  CONFIGURATION_PENDING: { label: 'Falta configurar', tone: 'warning' },
  PENDING_REVIEW: { label: 'En revisión', tone: 'warning' },
  REJECTED: { label: 'No aprobado', tone: 'danger' },
}

function StoreCard({ store }: { store: MyStore }) {
  const openable = store.applicationStatus === 'ACTIVE'
  const application = APPLICATION_LABEL[store.applicationStatus]
  const body = (
    <>
      <Avatar className="size-12 rounded-lg text-base">
        {store.logoUrl ? <AvatarImage src={store.logoUrl} alt="" /> : null}
        <AvatarFallback aria-hidden>{initials(store.businessName) || store.businessName.slice(0, 2).toUpperCase()}</AvatarFallback>
      </Avatar>
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <p className="text-lg leading-tight font-extrabold tracking-tight">{store.businessName}</p>
        <p className="text-sm text-muted-foreground">{store.roleNames.join(', ') || (store.isOwner ? 'Propietario' : 'Colaborador')}</p>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge tone={application.tone}>{application.label}</StatusBadge>
          {openable && store.subscription ? (
            <StatusBadge tone={SUBSCRIPTION_STATUS_TONE[store.subscription.status]}>
              {store.subscription.planName} · {SUBSCRIPTION_STATUS_LABEL[store.subscription.status].toLowerCase()}
              {store.subscription.validThrough ? ` · hasta ${formatDate(store.subscription.validThrough)}` : ''}
            </StatusBadge>
          ) : null}
        </div>
      </div>
      {openable ? <ArrowRightIcon className="size-5 shrink-0 text-muted-foreground" aria-hidden /> : null}
    </>
  )
  const base = 'flex items-center gap-4 rounded-xl bg-card p-4 ring-1 ring-foreground/10 sm:p-5'
  if (!openable) {
    return (
      <div className={base}>
        {body}
      </div>
    )
  }
  return (
    <Link
      to={`/portal/${encodeURIComponent(store.storeId)}/inicio`}
      className={`${base} transition-shadow hover:ring-foreground/30`}
    >
      {body}
    </Link>
  )
}

/** `/portal`: abre el almacén si hay uno activo; si hay varios, deja elegir; si no hay, explica cómo empezar. */
export default function PortalIndexPage() {
  const stores = useAsync('portal:stores', async () => (await getPortalService()).myStores())

  let content
  if (stores.status === 'error') {
    content = <QueryError error={stores.error} what="tus almacenes" onRetry={stores.retry} />
  } else if (stores.data === undefined) {
    content = <ListSkeleton rows={2} label="Cargando tus almacenes" />
  } else if (stores.data.length === 0) {
    content = (
      <EmptyState
        icon={<StoreIcon />}
        title="Aún no tienes un almacén en Autopartco"
        description="Cuando tu solicitud sea aprobada vas a administrar aquí tus productos, pedidos y equipo. Si ya enviaste la solicitud, revisa tu correo: te escribiremos apenas haya respuesta."
      >
        <Button className="h-11 px-5" nativeButton={false} render={<Link to="/vender" />}>
          Quiero vender en Autopartco
        </Button>
      </EmptyState>
    )
  } else {
    const active = stores.data.filter((store) => store.applicationStatus === 'ACTIVE')
    const [only] = active
    if (stores.data.length === 1 && only) {
      return <Navigate to={`/portal/${encodeURIComponent(only.storeId)}/inicio`} replace />
    }
    content = (
      <ul className="flex flex-col gap-3">
        {stores.data.map((store) => (
          <li key={store.storeId}>
            <StoreCard store={store} />
          </li>
        ))}
      </ul>
    )
  }

  return (
    <div className="min-h-svh bg-background">
      <header className="border-b border-border bg-card">
        <div className="mx-auto flex h-14 w-full max-w-3xl items-center justify-between gap-3 px-4 sm:px-6">
          <Logo />
          <PortalAccountMenu />
        </div>
      </header>
      <main id="contenido" className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
        <title>Mis almacenes | Autopartco</title>
        <h1 className="mb-2 text-2xl font-extrabold tracking-tight sm:text-3xl">Mis almacenes</h1>
        <p className="mb-6 text-muted-foreground">Elige el almacén que quieres administrar.</p>
        {content}
      </main>
    </div>
  )
}
