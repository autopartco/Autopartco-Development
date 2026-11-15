import { useMemo } from 'react'
import { CalendarXIcon, InfoIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'
import { AVAILABILITY_DAYS, useAvailability } from '../../booking-hooks'
import { bogotaDayOf, bookingErrorMessage, formatDayChip, formatDayLong, formatSlotTime } from '../../lib/booking'

interface SlotStepProps {
  serviceId: string
  branchId: string
  /** Hora elegida (ISO con zona). */
  startsAt: string | undefined
  /** Día elegido (`YYYY-MM-DD`); si falta, el de la hora o el primero con horas. */
  day: string | undefined
  onSelect: (selection: { day: string; startsAt?: string }) => void
  /** Error de la última solicitud (`BOOKING_SLOT_INVALID`…), junto a las horas. */
  submitError?: string
  /** Información que se ofrece cuando no hay horas (p. ej. otras vías de contacto). */
  emptyHint?: string
}

const CHIP =
  'inline-flex min-h-11 items-center justify-center rounded-md border px-3 text-sm font-medium transition-colors outline-none focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[var(--focus)]'

/** Pasos «Día» y «Hora»: las horas las decide el servidor; las ocupadas son orientativas y se pueden pedir igual. */
export function SlotStep({ serviceId, branchId, startsAt, day, onSelect, submitError, emptyHint }: SlotStepProps) {
  const availability = useAvailability(serviceId, branchId)
  const data = availability.isStale ? undefined : availability.data
  const days = useMemo(() => (data?.days ?? []).filter((item) => item.slots.length > 0), [data])
  const activeDay = days.find((item) => item.date === (day ?? (startsAt ? bogotaDayOf(startsAt) : ''))) ?? days[0]

  if (availability.status === 'error') {
    return <ErrorState title="No pudimos cargar las horas" description={bookingErrorMessage(availability.error, 'Revisa tu conexión e inténtalo de nuevo.')} onRetry={availability.retry} />
  }
  if (!data) {
    return (
      <div className="flex flex-col gap-4" aria-hidden>
        <span className="sr-only" role="status">
          Cargando horas disponibles
        </span>
        <div className="flex flex-wrap gap-2">
          {[0, 1, 2, 3, 4].map((key) => (
            <Skeleton key={key} className="h-11 w-24" />
          ))}
        </div>
        <div className="grid grid-cols-3 gap-2 sm:grid-cols-4">
          {[0, 1, 2, 3, 4, 5].map((key) => (
            <Skeleton key={key} className="h-11" />
          ))}
        </div>
      </div>
    )
  }
  if (!activeDay) {
    return (
      <div className="flex flex-col items-start gap-2 rounded-md border border-ap-line bg-ap-paper p-4">
        <p className="flex items-center gap-2 font-semibold text-ap-ink">
          <CalendarXIcon className="size-5 text-ap-muted" aria-hidden />
          Sin horas en los próximos {AVAILABILITY_DAYS} días
        </p>
        <p className="text-sm text-ap-muted">{emptyHint ?? 'Prueba con otra sede o escribe al taller para acordar una hora.'}</p>
      </div>
    )
  }

  const anyBusy = activeDay.slots.some((slot) => slot.busy)
  return (
    <div className="flex flex-col gap-5">
      <section aria-labelledby="slot-day" className="flex flex-col gap-2">
        <h3 id="slot-day" className="text-sm font-semibold text-ap-ink">
          Día
        </h3>
        <ul className="flex flex-wrap gap-2">
          {days.map((item) => {
            const selected = item.date === activeDay.date
            return (
              <li key={item.date}>
                <button
                  type="button"
                  aria-pressed={selected}
                  aria-label={formatDayLong(item.date)}
                  onClick={() => onSelect({ day: item.date, startsAt: startsAt && bogotaDayOf(startsAt) === item.date ? startsAt : undefined })}
                  className={cn(CHIP, 'capitalize', selected ? 'border-ap-graphite bg-ap-graphite text-ap-white' : 'border-ap-line bg-ap-white text-ap-ink hover:border-ap-graphite')}
                >
                  {formatDayChip(item.date)}
                </button>
              </li>
            )
          })}
        </ul>
      </section>

      <section aria-labelledby="slot-hour" className="flex flex-col gap-2">
        <h3 id="slot-hour" className="text-sm font-semibold text-ap-ink">
          Hora <span className="font-normal text-ap-muted">· {formatDayLong(activeDay.date)}</span>
        </h3>
        <ul className="grid grid-cols-3 gap-2 sm:grid-cols-4">
          {activeDay.slots.map((slot) => {
            const selected = slot.startsAt === startsAt
            return (
              <li key={slot.startsAt}>
                <button
                  type="button"
                  aria-pressed={selected}
                  onClick={() => onSelect({ day: activeDay.date, startsAt: slot.startsAt })}
                  className={cn(
                    CHIP,
                    'w-full flex-col gap-0 tabular-nums',
                    selected ? 'border-ap-graphite bg-ap-graphite text-ap-white' : 'bg-ap-white text-ap-ink hover:border-ap-graphite',
                    !selected && (slot.busy ? 'border-dashed border-ap-line-strong' : 'border-ap-line'),
                  )}
                >
                  {formatSlotTime(slot.startsAt)}
                  {slot.busy ? <span className="text-[0.6875rem] leading-tight font-normal">Ocupada<span className="sr-only">: ya tiene una cita confirmada</span></span> : null}
                </button>
              </li>
            )
          })}
        </ul>
        {anyBusy ? (
          <p className="flex items-start gap-2 text-xs text-ap-muted">
            <InfoIcon className="mt-0.5 size-3.5 shrink-0" aria-hidden />
            «Ocupada» significa que esa hora ya tiene una cita confirmada. Puedes pedirla igual: el taller decide y, si no puede, te propone otra hora.
          </p>
        ) : null}
      </section>

      {submitError ? (
        <p role="alert" className="rounded-md border border-ap-danger/25 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
          {submitError}
        </p>
      ) : null}
    </div>
  )
}
