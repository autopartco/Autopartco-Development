import { useParams } from 'react-router'
import { Button } from '@/components/ui/button'
import { formatCOP } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { getPortalService } from '../api/portal-service'
import { formatDate, SUBSCRIPTION_STATUS_LABEL, usageText } from '../lib/labels'

/** `/portal/:storeId/plan`: estado, uso frente a límites y pagos. El cobro llega con SA-B3 y SA-F10. */
export default function PortalPlanPage() {
  const { storeId = '' } = useParams()
  const subscription = useAsync(`portal:subscription:${storeId}`, async () =>
    (await getPortalService()).subscription(storeId),
  )
  const payments = useAsync(`portal:payments:${storeId}`, async () => (await getPortalService()).payments(storeId))

  if (subscription.status === 'loading') {
    return <p className="text-sm text-muted-foreground">Cargando tu plan…</p>
  }
  if (subscription.status === 'error' || !subscription.data) {
    return (
      <div role="alert" className="flex flex-col gap-3">
        <p className="text-sm">No pudimos cargar tu plan.</p>
        <Button variant="outline" onClick={subscription.retry}>Reintentar</Button>
      </div>
    )
  }
  const data = subscription.data
  const nearLimit = (used: number, limit: number | null) => limit !== null && limit > 0 && used / limit >= 0.8

  return (
    <section aria-labelledby="plan-titulo" className="flex flex-col gap-6">
      <div>
        <h1 id="plan-titulo" className="text-lg font-semibold">Plan y suscripción</h1>
        <p className="text-sm text-muted-foreground">
          {SUBSCRIPTION_STATUS_LABEL[data.status]} · {data.plan.name} · vigente hasta {formatDate(data.validThrough)}
        </p>
      </div>

      {data.status === 'EXPIRED' && (
        <p role="status" className="rounded-md border border-destructive/30 p-3 text-sm text-destructive">
          Tu prueba o suscripción venció. Puedes consultar tus productos, pero no publicar ni crear operaciones nuevas.
        </p>
      )}

      <div className="grid gap-4 sm:grid-cols-3">
        {(
          [
            ['Sucursales', data.usage.branches],
            ['Productos', data.usage.products],
            ['Usuarios', data.usage.users],
          ] as const
        ).map(([label, item]) => (
          <div key={label} className="flex flex-col gap-1">
            <span className="text-sm text-muted-foreground">{label}</span>
            <span className="font-medium">{usageText(item.used, item.limit)}</span>
            {nearLimit(item.used, item.limit) && (
              <span className="text-xs text-amber-700">Cerca del límite de tu plan.</span>
            )}
          </div>
        ))}
      </div>

      <div className="flex flex-col gap-2">
        <h2 className="text-base font-semibold">Pagos</h2>
        {payments.status === 'loading' && <p className="text-sm text-muted-foreground">Cargando pagos…</p>}
        {payments.status === 'error' && <p className="text-sm">No pudimos cargar el historial de pagos.</p>}
        {payments.data && payments.data.length === 0 && <p className="text-sm text-muted-foreground">Aún no tienes pagos.</p>}
        {payments.data && payments.data.length > 0 && (
          <ul className="flex flex-col divide-y">
            {payments.data.map((payment) => (
              <li key={payment.id} className="flex flex-wrap justify-between gap-2 py-2 text-sm">
                <span>{payment.planName} · {payment.months} mes(es)</span>
                <span>{formatCOP(payment.amountCop)} · {payment.status}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  )
}
