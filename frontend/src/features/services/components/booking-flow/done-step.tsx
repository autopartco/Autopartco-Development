import { Link } from 'react-router'
import { CircleCheckIcon } from 'lucide-react'
import { buttonVariants } from '@/components/ui/button'
import type { Booking } from '../../api/booking-types'
import { formatBookingMoment } from '../../lib/booking'
import { bookingPath } from '../../lib/paths'

/** Paso final: código de la cita y enlace a su detalle. El estado real lo muestra «Mis citas». */
export function DoneStep({ booking }: { booking: Booking }) {
  return (
    <div className="flex flex-col gap-4" role="status">
      <div className="flex items-start gap-3">
        <CircleCheckIcon className="mt-0.5 size-6 shrink-0 text-ap-success" aria-hidden />
        <div className="flex flex-col gap-1">
          <p className="text-base font-semibold text-ap-ink">Enviamos tu solicitud de cita</p>
          <p className="text-sm text-ap-muted">
            {booking.store.name} la revisará y te avisará si la confirma o si propone otra hora. Puedes seguirla en «Mis citas».
          </p>
        </div>
      </div>
      <dl className="grid gap-3 border-y border-ap-line py-3 sm:grid-cols-2">
        <div>
          <dt className="text-sm text-ap-muted">Código de la cita</dt>
          <dd className="font-mono text-lg font-semibold text-ap-ink tabular-nums">{booking.code}</dd>
        </div>
        <div>
          <dt className="text-sm text-ap-muted">Fecha solicitada</dt>
          <dd className="text-sm font-medium text-ap-ink">{formatBookingMoment(booking.startsAt)}</dd>
        </div>
      </dl>
      <Link to={bookingPath(booking.id)} className={buttonVariants({ className: 'w-full sm:w-fit' })}>
        Ver mi cita
      </Link>
    </div>
  )
}
