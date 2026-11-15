import { Link, useSearchParams } from 'react-router'
import { CalendarXIcon, ChevronLeftIcon, ChevronRightIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { OptionSelect } from '@/components/common/option-select'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import type { Booking } from '@/features/services/api/booking-types'
import { useBuyerBookings } from '@/features/services/booking-hooks'
import { BookingStatusBadge } from '@/features/services/components/booking-status-badge'
import { BOOKING_STATUSES, bookingStatus, formatBookingMoment, isBookingStatus } from '@/features/services/lib/booking'
import { SERVICES_PATH, bookingPath } from '@/features/services/lib/paths'

const PAGE_SIZE = 10

function BookingRow({ booking }: { booking: Booking }) {
  const proposed = booking.status === 'PROPOSED' && booking.proposedStartsAt
  return (
    <article className="group relative flex flex-col gap-2 px-2 py-4 transition-colors hover:bg-ap-paper sm:grid sm:grid-cols-[minmax(0,1fr)_minmax(0,14rem)_1.25rem] sm:items-center sm:gap-x-5 sm:px-3">
      <div className="flex min-w-0 flex-col gap-0.5">
        <h2 className="text-base font-semibold break-words text-ap-ink">
          <Link
            to={bookingPath(booking.id)}
            className="outline-none after:absolute after:inset-0 after:rounded-md focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[var(--focus)] group-hover:text-ap-red"
          >
            {booking.service.name}
            <span className="sr-only">, cita {booking.code}</span>
          </Link>
        </h2>
        <p className="text-sm break-words text-ap-muted">
          {booking.store.name} · {booking.branch.name}
        </p>
        <p className="font-mono text-xs text-ap-muted tabular-nums">{booking.code}</p>
      </div>
      <div className="flex min-w-0 flex-col items-start gap-1.5">
        <BookingStatusBadge status={booking.status} />
        <p className="text-sm text-ap-ink">
          <time dateTime={booking.startsAt}>{formatBookingMoment(booking.startsAt)}</time>
        </p>
        {proposed ? (
          <p className="text-sm font-medium text-ap-info">
            Propuesta: <time dateTime={booking.proposedStartsAt!}>{formatBookingMoment(booking.proposedStartsAt!)}</time>
          </p>
        ) : null}
      </div>
      <ChevronRightIcon className="hidden size-5 text-ap-muted group-hover:text-ap-red sm:block" aria-hidden />
    </article>
  )
}

/** `/mis-citas`: citas del comprador con filtro por estado y paginación (las pide al servidor). */
export default function BookingsPage() {
  const [params, setParams] = useSearchParams()
  const statusParam = params.get('estado')
  const status = isBookingStatus(statusParam) ? statusParam : undefined
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const result = useBuyerBookings(status, page, PAGE_SIZE)
  const data = result.data

  function update(patch: { estado?: string; pagina?: number }) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if ('estado' in patch) {
          if (patch.estado) next.set('estado', patch.estado)
          else next.delete('estado')
          next.delete('pagina')
        }
        if (patch.pagina !== undefined) {
          if (patch.pagina > 1) next.set('pagina', String(patch.pagina))
          else next.delete('pagina')
        }
        return next
      },
      { preventScrollReset: true },
    )
  }

  return (
    <Container className="py-6 sm:py-8">
      <title>Mis citas | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Mis citas' }]}
        label="Tu cuenta"
        title="Mis citas"
        description="Sigue las citas que pediste a talleres y responde cuando propongan otra hora."
      />

      <div className="mb-4 max-w-xs">
        <OptionSelect
          id="bookings-status"
          label="Filtrar por estado"
          emptyLabel="Todas las citas"
          value={status ?? ''}
          options={BOOKING_STATUSES.map((code) => ({ value: code, label: bookingStatus(code).label }))}
          onValueChange={(value) => update({ estado: value })}
        />
      </div>

      {result.status === 'error' ? (
        <ErrorState title="No pudimos cargar tus citas" onRetry={result.retry} />
      ) : !data ? (
        <div className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
          <span className="sr-only" role="status">
            Cargando tus citas
          </span>
          {[0, 1, 2].map((key) => (
            <div key={key} className="flex flex-col gap-2 py-4" aria-hidden>
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-6 w-32" />
            </div>
          ))}
        </div>
      ) : data.items.length === 0 ? (
        <EmptyState
          icon={<CalendarXIcon />}
          title={status ? `No tienes citas con estado «${bookingStatus(status).label}»` : 'Todavía no tienes citas'}
          description={status ? 'Prueba con otro estado o quita el filtro.' : 'Cuando agendes un servicio con un taller, tu cita aparecerá aquí.'}
        >
          {status ? (
            <Button variant="outline" onClick={() => update({ estado: '' })}>
              Ver todas las citas
            </Button>
          ) : (
            <Button nativeButton={false} render={<Link to={SERVICES_PATH} />}>
              Buscar servicios
            </Button>
          )}
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-4" aria-busy={result.isStale || undefined}>
          <ul className={`flex flex-col divide-y divide-ap-line border-y border-ap-line transition-opacity duration-150 ${result.isStale ? 'opacity-60' : ''}`}>
            {data.items.map((booking) => (
              <li key={booking.id}>
                <BookingRow booking={booking} />
              </li>
            ))}
          </ul>
          {data.totalPages > 1 ? (
            <nav aria-label="Paginación de citas" className="flex items-center justify-between gap-3">
              <Button variant="outline" disabled={page <= 1} onClick={() => update({ pagina: page - 1 })}>
                <ChevronLeftIcon aria-hidden />
                Anteriores
              </Button>
              <p className="font-mono text-sm text-ap-muted tabular-nums" aria-live="polite">
                Página {data.page} de {data.totalPages}
              </p>
              <Button variant="outline" disabled={page >= data.totalPages} onClick={() => update({ pagina: page + 1 })}>
                Siguientes
                <ChevronRightIcon aria-hidden />
              </Button>
            </nav>
          ) : null}
        </div>
      )}
    </Container>
  )
}
