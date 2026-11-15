import { useId, useMemo, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { FieldShell, INPUT_CLASS } from '@/components/common/form-fields'
import { getBookingService } from '@/features/services/api/booking-service'
import { formatDayLong, formatSlotTime, NOTE_MAX, todayInBogota } from '@/features/services/lib/booking'
import { useAsync } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { cn } from '@/lib/utils'
import type { StoreBooking } from '../api/store-bookings-service'
import { SelectField } from './form-controls'
import { QueryError } from './query-boundary'

interface BaseProps {
  booking: StoreBooking
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Envía la acción; si rechaza, el diálogo muestra el motivo y sigue abierto. */
  onSubmit: (body: { note?: string; startsAt?: string }) => Promise<string | undefined>
}

/** Rechazar o cancelar: la nota para el comprador es obligatoria (se valida aquí y otra vez en el servidor). */
export function NoteDecisionDialog({ booking, open, onOpenChange, onSubmit, kind }: BaseProps & { kind: 'reject' | 'cancel' }) {
  const id = useId()
  const [note, setNote] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const [error, setError] = useState<string>()
  const send = useSubmitOnce(async (text: string) => onSubmit({ note: text }))
  const reject = kind === 'reject'

  async function confirm() {
    const text = note.trim()
    if (!text) {
      setFieldError(reject ? 'Explica al comprador por qué rechazas la solicitud.' : 'Explica al comprador por qué cancelas la cita.')
      document.getElementById(`${id}-note`)?.focus()
      return
    }
    setFieldError(undefined)
    setError(undefined)
    const result = await send.run(text)
    if (result.status === 'ok') {
      if (result.value) setError(result.value)
      else {
        setNote('')
        onOpenChange(false)
      }
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (send.pending ? undefined : onOpenChange(next))}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{reject ? `Rechazar la solicitud ${booking.code}` : `Cancelar la cita ${booking.code}`}</DialogTitle>
          <DialogDescription>El comprador recibe tu nota por correo. Sé claro y ofrece una alternativa si puedes.</DialogDescription>
        </DialogHeader>
        <FieldShell id={`${id}-note`} label="Nota para el comprador" error={fieldError} hint={`${note.length} de ${NOTE_MAX} caracteres`}>
          {(describedBy) => (
            <textarea
              id={`${id}-note`}
              rows={4}
              maxLength={NOTE_MAX}
              aria-invalid={fieldError ? true : undefined}
              aria-describedby={describedBy}
              value={note}
              onChange={(event) => {
                setNote(event.target.value)
                if (fieldError) setFieldError(undefined)
              }}
              className={cn(INPUT_CLASS, 'h-auto min-h-24 resize-y py-2')}
            />
          )}
        </FieldShell>
        {error ? (
          <p role="alert" className="rounded-md border border-ap-danger/25 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
            {error}
          </p>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" className="h-11 px-4" disabled={send.pending} onClick={() => onOpenChange(false)}>
            Volver
          </Button>
          <Button type="button" variant="destructive" className="h-11 px-4" loading={send.pending} loadingText="Enviando…" onClick={() => void confirm()}>
            {reject ? 'Rechazar solicitud' : 'Cancelar cita'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

/** Proponer otra hora: solo se ofrecen horas de `availability` (el servidor vuelve a validar la franja). */
export function ProposeDialog({ booking, open, onOpenChange, onSubmit }: BaseProps) {
  const id = useId()
  const from = todayInBogota()
  const availability = useAsync(open ? `portal:availability:${booking.service.id}:${booking.branch.id}:${from}` : `portal:availability:idle`, async () =>
    open
      ? (await getBookingService()).availability({ serviceId: booking.service.id, branchId: booking.branch.id, from, days: 14 })
      : { slotMinutes: 0, days: [] },
  )
  const [day, setDay] = useState('')
  const [time, setTime] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const [error, setError] = useState<string>()
  const send = useSubmitOnce(async (startsAt: string) => onSubmit({ startsAt }))

  const days = useMemo(() => (availability.data?.days ?? []).filter((entry) => entry.slots.length > 0), [availability.data])
  const activeDay = days.find((entry) => entry.date === day)
  const slotLabel = (slot: { startsAt: string; busy: boolean }) => `${formatSlotTime(slot.startsAt)}${slot.busy ? ' (ya hay una cita confirmada)' : ''}`

  async function confirm() {
    if (!time) {
      setFieldError('Elige el día y la hora que propones.')
      document.getElementById(`${id}-day`)?.focus()
      return
    }
    setFieldError(undefined)
    setError(undefined)
    const result = await send.run(time)
    if (result.status === 'ok') {
      if (result.value) setError(result.value)
      else onOpenChange(false)
    }
  }

  return (
    <Dialog open={open} onOpenChange={(next) => (send.pending ? undefined : onOpenChange(next))}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Proponer otra hora para {booking.code}</DialogTitle>
          <DialogDescription>El comprador decide si la acepta. Se muestran las horas de {booking.branch.name} para los próximos 14 días.</DialogDescription>
        </DialogHeader>
        {availability.status === 'error' ? (
          <QueryError error={availability.error} what="las horas disponibles" onRetry={availability.retry} />
        ) : availability.status === 'loading' ? (
          <p role="status" className="text-sm text-ap-muted">
            Cargando las horas disponibles…
          </p>
        ) : days.length === 0 ? (
          <p role="status" className="text-sm text-ap-muted">
            No hay horas disponibles en los próximos 14 días en esta sucursal. Revisa el horario de la sucursal o rechaza la solicitud con una nota.
          </p>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2">
            <SelectField
              id={`${id}-day`}
              label="Día"
              placeholder="Elige un día"
              value={day}
              error={fieldError && !day ? fieldError : undefined}
              onChange={(event) => {
                setDay(event.target.value)
                setTime('')
                setFieldError(undefined)
              }}
              options={days.map((entry) => ({ value: entry.date, label: formatDayLong(entry.date) }))}
            />
            <SelectField
              id={`${id}-time`}
              label="Hora"
              placeholder={day ? 'Elige una hora' : 'Elige primero el día'}
              value={time}
              disabled={!activeDay}
              error={fieldError && day ? fieldError : undefined}
              onChange={(event) => {
                setTime(event.target.value)
                setFieldError(undefined)
              }}
              options={(activeDay?.slots ?? []).map((slot) => ({ value: slot.startsAt, label: slotLabel(slot) }))}
            />
          </div>
        )}
        {error ? (
          <p role="alert" className="rounded-md border border-ap-danger/25 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
            {error}
          </p>
        ) : null}
        <DialogFooter>
          <Button type="button" variant="outline" className="h-11 px-4" disabled={send.pending} onClick={() => onOpenChange(false)}>
            Volver
          </Button>
          <Button type="button" className="h-11 px-4" disabled={send.pending || days.length === 0} onClick={() => void confirm()}>
            {send.pending ? 'Enviando…' : 'Proponer esta hora'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
