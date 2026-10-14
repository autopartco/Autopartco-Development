import { Link, useParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { useAsync } from '@/lib/use-async'
import { getPortalService } from '../api/portal-service'
import { formatDate, SUBSCRIPTION_STATUS_LABEL, usageText } from '../lib/labels'

/** `/portal/:storeId/inicio`: resumen de la suscripción y de los cupos. */
export default function PortalOverviewPage() {
  const { storeId = '' } = useParams()
  const subscription = useAsync(`portal:subscription:${storeId}`, async () =>
    (await getPortalService()).subscription(storeId),
  )

  if (subscription.status === 'loading') {
    return <p className="text-sm text-muted-foreground">Cargando tu resumen…</p>
  }
  if (subscription.status === 'error' || !subscription.data) {
    return (
      <div role="alert" className="flex flex-col gap-3">
        <p className="text-sm">No pudimos cargar el resumen del almacén.</p>
        <Button variant="outline" onClick={subscription.retry}>Reintentar</Button>
      </div>
    )
  }
  const data = subscription.data

  return (
    <section aria-labelledby="resumen-titulo" className="flex flex-col gap-4">
      <h1 id="resumen-titulo" className="text-lg font-semibold">Resumen</h1>
      <dl className="grid gap-4 sm:grid-cols-3">
        <div>
          <dt className="text-sm text-muted-foreground">Estado</dt>
          <dd className="font-medium">{SUBSCRIPTION_STATUS_LABEL[data.status]}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Plan</dt>
          <dd className="font-medium">{data.plan.name}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Vigente hasta</dt>
          <dd className="font-medium">
            {formatDate(data.validThrough)}
            {data.daysRemaining !== null ? ` (${data.daysRemaining} días)` : ''}
          </dd>
        </div>
      </dl>
      <dl className="grid gap-4 sm:grid-cols-3">
        <div>
          <dt className="text-sm text-muted-foreground">Sucursales</dt>
          <dd>{usageText(data.usage.branches.used, data.usage.branches.limit)}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Productos</dt>
          <dd>{usageText(data.usage.products.used, data.usage.products.limit)}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Usuarios</dt>
          <dd>{usageText(data.usage.users.used, data.usage.users.limit)}</dd>
        </div>
      </dl>
      <Button variant="outline" className="w-fit" nativeButton={false} render={<Link to={`/portal/${storeId}/plan`} />}>
        Ver plan y suscripción
      </Button>
    </section>
  )
}
