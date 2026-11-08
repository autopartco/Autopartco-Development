import { Link, Navigate } from 'react-router'
import { ArrowRightIcon, StoreIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar'
import { initials } from '@/lib/format'
import { SELLER_APPLICATION_PATH, SELLER_SETUP_PATH, useSellerCta } from '@/features/seller/hooks'
import { useAsync } from '@/lib/use-async'
import { getPortalService } from '../api/portal-service'
import type { MyStore, StoreApplicationStatus } from '../api/types'
import { ListSkeleton } from '../components/list-skeleton'
import { QueryError } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { PageHeader } from '../components/page-header'
import { PortalHeader } from '../layout/portal-header'
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
      <Avatar className="size-11 rounded-md text-sm">
        {store.logoUrl ? <AvatarImage src={store.logoUrl} alt="" /> : null}
        <AvatarFallback aria-hidden>{initials(store.businessName) || store.businessName.slice(0, 2).toUpperCase()}</AvatarFallback>
      </Avatar>
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <p className="text-base leading-tight font-semibold text-ap-ink">{store.businessName}</p>
        <p className="text-sm text-ap-muted">{store.roleNames.join(', ') || (store.isOwner ? 'Propietario' : 'Colaborador')}</p>
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge tone={application.tone}>{application.label}</StatusBadge>
          {openable && store.subscription ? (
            <StatusBadge tone={SUBSCRIPTION_STATUS_TONE[store.subscription.status]}>
              {store.subscription.planName}
              {SUBSCRIPTION_STATUS_LABEL[store.subscription.status].toLowerCase() === store.subscription.planName.toLowerCase()
                ? ''
                : ` · ${SUBSCRIPTION_STATUS_LABEL[store.subscription.status].toLowerCase()}`}
              {store.subscription.validThrough ? ` · hasta ${formatDate(store.subscription.validThrough)}` : ''}
            </StatusBadge>
          ) : null}
        </div>
      </div>
      {openable ? (
        <ArrowRightIcon
          className="size-5 shrink-0 text-ap-muted transition-colors duration-150 group-hover:text-ap-ink"
          aria-hidden
        />
      ) : null}
    </>
  )
  const base = 'flex items-center gap-4 px-4 py-4 sm:px-5'
  if (!openable) {
    // Sin portal todavía: la tarjeta lleva al estado de la solicitud.
    return (
      <Link
        to={store.applicationStatus === 'CONFIGURATION_PENDING' ? SELLER_SETUP_PATH : SELLER_APPLICATION_PATH}
        className={`${base} group transition-colors hover:bg-ap-paper`}
      >
        {body}
        <span className="shrink-0 text-sm font-semibold text-ap-red group-hover:underline">
          {store.applicationStatus === 'CONFIGURATION_PENDING' ? 'Terminar configuración' : 'Ver solicitud'}
        </span>
      </Link>
    )
  }
  return (
    <Link
      to={`/portal/${encodeURIComponent(store.storeId)}/inicio`}
      className={`${base} group transition-colors hover:bg-ap-paper`}
    >
      {body}
    </Link>
  )
}

/** `/portal`: abre el almacén si hay uno activo; si hay varios, deja elegir; si no hay, explica cómo empezar. */
export default function PortalIndexPage() {
  const stores = useAsync('portal:stores', async () => (await getPortalService()).myStores())
  // Respaldo: si el servidor no lista el almacén de una solicitud en curso, se muestra la solicitud.
  const hasNoStores = stores.data?.length === 0
  const seller = useSellerCta()

  let content
  if (stores.status === 'error') {
    content = <QueryError error={stores.error} what="tus almacenes" onRetry={stores.retry} />
  } else if (stores.data === undefined) {
    content = <ListSkeleton rows={2} label="Cargando tus almacenes" />
  } else if (hasNoStores && seller.state === 'loading') {
    content = <ListSkeleton rows={1} label="Consultando tu solicitud" />
  } else if (hasNoStores && seller.state === 'review' && seller.application) {
    content = (
      <Link
        to={seller.application.status === 'CONFIGURATION_PENDING' ? SELLER_SETUP_PATH : seller.to}
        className="group flex items-center gap-4 rounded-lg border border-ap-line px-4 py-4 transition-colors hover:bg-ap-paper sm:px-5"
      >
        <div className="flex min-w-0 flex-1 flex-col gap-1.5">
          <p className="text-base leading-tight font-semibold text-ap-ink">{seller.application.businessName}</p>
          <StatusBadge tone="warning">
            {APPLICATION_LABEL[seller.application.status].label}
          </StatusBadge>
        </div>
        <span className="shrink-0 text-sm font-semibold text-ap-red group-hover:underline">
          {seller.application.status === 'CONFIGURATION_PENDING' ? 'Terminar configuración' : 'Ver solicitud'}
        </span>
      </Link>
    )
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
      <ul className="divide-y divide-ap-line rounded-lg border border-ap-line">
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
      <PortalHeader innerClassName="mx-auto w-full max-w-3xl px-4 sm:px-6 lg:px-6" />
      <main id="contenido" className="mx-auto w-full max-w-3xl px-4 py-8 sm:px-6 sm:py-12">
        <title>Mis almacenes | Autopartco</title>
        <PageHeader label="Portal de almacenes" title="Mis almacenes" description="Elige el almacén que quieres administrar." />
        {content}
      </main>
    </div>
  )
}
