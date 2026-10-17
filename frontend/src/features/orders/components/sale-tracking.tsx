import { TruckIcon } from 'lucide-react'
import { formatOrderDate } from '@/features/checkout/lib/labels'
import { StatusBadge } from '@/features/portal/components/status-badge'
import { cn } from '@/lib/utils'
import type { ShipmentDto } from '../api/types'
import { shipmentStatusLabel } from '../lib/labels'

/** Seguimiento del envío de una venta: transportadora, guía y línea de tiempo (lo más reciente primero). */
export function SaleTracking({ shipment }: { shipment: ShipmentDto | null }) {
  if (!shipment) {
    return (
      <p className="flex items-start gap-2 rounded-lg bg-concrete px-3 py-2.5 text-sm text-muted-foreground">
        <TruckIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>La tienda aún no registra el envío. Verás el seguimiento aquí cuando salga.</span>
      </p>
    )
  }
  const delivered = shipment.status === 'DELIVERED'
  const events = [...shipment.events].reverse()
  return (
    <div className="flex flex-col gap-4">
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-3">
        <div>
          <dt className="text-sm text-muted-foreground">Transportadora</dt>
          <dd className="font-semibold">{shipment.carrier}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Guía</dt>
          <dd className="font-mono font-semibold break-all">{shipment.trackingNumber}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Estado</dt>
          <dd>
            <StatusBadge tone={delivered ? 'success' : 'info'}>{shipmentStatusLabel(shipment.status)}</StatusBadge>
          </dd>
        </div>
      </dl>
      {events.length > 0 ? (
        <div>
          <h4 className="mb-3 text-sm font-semibold">Seguimiento</h4>
          <ol className="flex flex-col border-l-2 border-border pl-5">
            {events.map((event, index) => (
              <li key={`${event.status}-${event.occurredAt}-${index}`} className="relative pb-4 text-sm last:pb-0">
                <span aria-hidden className={cn('absolute top-1.5 -left-[1.65rem] size-2.5 rounded-full ring-4 ring-card', index === 0 ? 'bg-thread' : 'bg-border')} />
                <span className="font-semibold">{shipmentStatusLabel(event.status)}</span>
                <span className="text-muted-foreground"> · {formatOrderDate(event.occurredAt)}</span>
                {event.notes ? <p className="text-muted-foreground">{event.notes}</p> : null}
              </li>
            ))}
          </ol>
        </div>
      ) : null}
    </div>
  )
}
