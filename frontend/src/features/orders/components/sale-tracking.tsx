import { TruckIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { formatOrderDate } from '@/features/checkout/lib/labels'
import { cn } from '@/lib/utils'
import type { ShipmentDto } from '../api/types'
import { shipmentStatusLabel } from '../lib/labels'

/**
 * Seguimiento del envío de una venta: transportadora, guía y línea de tiempo sobria (lo más
 * reciente primero). El evento actual se marca con texto ("Último estado"), no solo con color.
 */
export function SaleTracking({ shipment }: { shipment: ShipmentDto | null }) {
  if (!shipment) {
    return (
      <p className="flex items-start gap-2 rounded-md border border-ap-line bg-ap-paper px-3 py-2.5 text-sm text-ap-muted">
        <TruckIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>La tienda aún no registra el envío. Verás el seguimiento aquí cuando salga.</span>
      </p>
    )
  }
  const delivered = shipment.status === 'DELIVERED'
  const events = [...shipment.events].reverse()
  return (
    <div className="flex flex-col gap-5">
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-3">
        <div>
          <dt className="text-sm text-ap-muted">Transportadora</dt>
          <dd className="font-semibold text-ap-ink">{shipment.carrier}</dd>
        </div>
        <div>
          <dt className="text-sm text-ap-muted">Guía</dt>
          <dd className="ap-ref break-all text-ap-ink">{shipment.trackingNumber}</dd>
        </div>
        <div>
          <dt className="text-sm text-ap-muted">Estado</dt>
          <dd>
            <Badge variant={delivered ? 'success' : 'info'}>{shipmentStatusLabel(shipment.status)}</Badge>
          </dd>
        </div>
      </dl>
      {events.length > 0 ? (
        <ol aria-label="Historial del envío" className="flex flex-col">
          {events.map((event, index) => {
            const current = index === 0
            return (
              <li
                key={`${event.status}-${event.occurredAt}-${index}`}
                className="relative grid grid-cols-[1rem_minmax(0,1fr)] gap-x-3 pb-4 text-sm last:pb-0"
              >
                {/* Marca y trazo de la línea de tiempo: cuadrado de 8px y línea fina, sin decoración. */}
                <span aria-hidden className="relative flex justify-center">
                  <span className={cn('mt-1.5 size-2 shrink-0 rounded-xs', current ? 'bg-ap-red' : 'bg-ap-line-strong')} />
                  {index < events.length - 1 ? <span className="absolute top-4 -bottom-4 w-px bg-ap-line" /> : null}
                </span>
                <div className="min-w-0">
                  <p className="flex flex-wrap items-baseline gap-x-2">
                    <span className={cn('font-semibold', current ? 'text-ap-ink' : 'text-ap-muted')}>{shipmentStatusLabel(event.status)}</span>
                    {current ? <span className="ap-index">Último estado</span> : null}
                  </p>
                  <p className="font-mono text-xs text-ap-muted tabular-nums">
                    <time dateTime={event.occurredAt}>{formatOrderDate(event.occurredAt)}</time>
                  </p>
                  {event.notes ? <p className="mt-0.5 text-ap-muted">{event.notes}</p> : null}
                </div>
              </li>
            )
          })}
        </ol>
      ) : null}
    </div>
  )
}
