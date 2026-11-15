import { useId, useState } from 'react'
import { toast } from 'sonner'
import {
  AlertDialog,
  AlertDialogClose,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Button } from '@/components/ui/button'
import { FieldShell, INPUT_CLASS } from '@/components/common/form-fields'
import { ApiError } from '@/lib/api'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { cn } from '@/lib/utils'
import { getBookingService } from '../api/booking-service'
import type { Booking } from '../api/booking-types'
import { refreshBuyerBookings } from '../booking-hooks'
import { NOTE_MAX, bookingErrorMessage } from '../lib/booking'

interface CancelBookingDialogProps {
  booking: Booking
  open: boolean
  onOpenChange: (open: boolean) => void
  onCancelled?: () => void
}

/** Confirma la cancelación; el motivo es opcional y solo lo recibe el almacén. */
export function CancelBookingDialog({ booking, open, onOpenChange, onCancelled }: CancelBookingDialogProps) {
  const id = useId()
  const [reason, setReason] = useState('')
  const [error, setError] = useState<string>()
  const cancel = useSubmitOnce(async () => (await getBookingService()).cancel(booking.id, reason.trim() || undefined))

  async function confirm() {
    setError(undefined)
    const result = await cancel.run()
    if (result.status === 'ok') {
      refreshBuyerBookings()
      toast.success('Cancelamos tu cita.')
      onOpenChange(false)
      onCancelled?.()
    } else if (result.status === 'error') {
      if (result.error instanceof ApiError && result.error.code === 'BOOKING_INVALID_TRANSITION') refreshBuyerBookings()
      setError(bookingErrorMessage(result.error, 'No pudimos cancelar la cita. Inténtalo de nuevo.'))
    }
  }

  return (
    <AlertDialog
      open={open}
      onOpenChange={(next) => {
        if (cancel.pending) return
        if (!next) setError(undefined)
        onOpenChange(next)
      }}
    >
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>¿Cancelar la cita {booking.code}?</AlertDialogTitle>
          <AlertDialogDescription>
            Avisaremos a {booking.store.name}. Si después quieres otra hora, tendrás que pedir una cita nueva.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <FieldShell id={`${id}-reason`} label="Motivo para el almacén" optional hint={`${reason.length} de ${NOTE_MAX} caracteres`}>
          {(describedBy) => (
            <textarea
              id={`${id}-reason`}
              rows={3}
              maxLength={NOTE_MAX}
              aria-describedby={describedBy}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              className={cn(INPUT_CLASS, 'h-auto min-h-20 resize-y py-2')}
            />
          )}
        </FieldShell>
        {error ? (
          <p role="alert" className="rounded-md border border-ap-danger/25 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
            {error}
          </p>
        ) : null}
        <AlertDialogFooter>
          <AlertDialogClose render={<Button variant="outline" className="h-11 px-4" disabled={cancel.pending} />}>Mantener la cita</AlertDialogClose>
          <Button variant="destructive" className="h-11 px-4" loading={cancel.pending} loadingText="Cancelando…" onClick={() => void confirm()}>
            Cancelar la cita
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  )
}
