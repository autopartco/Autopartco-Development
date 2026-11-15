import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'
import type { BookingStatus } from '../api/booking-types'
import { bookingStatus } from '../lib/booking'

/** Estado de la cita en texto (el color solo refuerza). */
export function BookingStatusBadge({ status, className }: { status: BookingStatus; className?: string }) {
  const view = bookingStatus(status)
  return (
    <Badge variant={view.tone} className={cn('h-auto min-h-5 py-0.5 whitespace-normal', className)}>
      <span className="sr-only">Estado: </span>
      {view.label}
    </Badge>
  )
}
