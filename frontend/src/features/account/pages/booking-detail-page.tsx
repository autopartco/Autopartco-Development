import { useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { CalendarCheckIcon, CalendarSearchIcon, XIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import type { StoreBranch } from '@/features/marketplace/api/types'
import { DirectionsMenu } from '@/features/marketplace/components/store/directions-menu'
import { WhatsappButton } from '@/features/marketplace/components/store/whatsapp-button'
import { directionsLinks } from '@/features/marketplace/lib/store-links'
import { storePath } from '@/features/marketplace/lib/paths'
import { useVehicleFilterLabel } from '@/features/marketplace/hooks'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getBookingService } from '@/features/services/api/booking-service'
import type { Booking } from '@/features/services/api/booking-types'
import { refreshBuyerBookings, useBuyerBooking } from '@/features/services/booking-hooks'
import { BookingStatusBadge } from '@/features/services/components/booking-status-badge'
import { CancelBookingDialog } from '@/features/services/components/cancel-booking-dialog'
import { bookingErrorMessage, formatBookingMoment, isBookingActive } from '@/features/services/lib/booking'
import { BOOKINGS_PATH, servicePath } from '@/features/services/lib/paths'

function Row({ term, children }: { term: string; children: ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 py-3 sm:flex-row sm:gap-4">
      <dt className="shrink-0 text-sm text-ap-muted sm:w-40">{term}</dt>
      <dd className="min-w-0 text-sm font-medium break-words text-ap-ink">{children}</dd>
    </div>
  )
}

/** Sede de la cita llevada a la forma que usan «Cómo llegar» y WhatsApp. */
function toStoreBranch(booking: Booking): StoreBranch {
  const { branch } = booking
  const hasPoint = branch.latitude !== null && branch.longitude !== null
  return {
    id: branch.id,
    name: branch.name,
    address: branch.address ?? '',
    city: branch.city ?? undefined,
    department: branch.department ?? undefined,
    point: hasPoint ? { lat: Number(branch.latitude), lng: Number(branch.longitude) } : undefined,
    phone: branch.phone ?? undefined,
    whatsapp: branch.whatsapp ?? undefined,
    serviceIds: [booking.service.id],
  }
}

function VehicleRow({ booking }: { booking: Booking }) {
  const vehicle = booking.vehicle
  const label = useVehicleFilterLabel({ makeId: vehicle?.makeId, modelId: vehicle?.modelId })
  if (!vehicle) return null
  return (
    <Row term="Vehículo">
      {[label ?? 'Vehículo indicado', vehicle.year, vehicle.plate].filter(Boolean).join(' · ')}
    </Row>
  )
}

function Skeletons() {
  return (
    <Container className="py-6 sm:py-8">
      <span className="sr-only" role="status">
        Cargando la cita
      </span>
      <div className="flex flex-col gap-4" aria-hidden>
        <Skeleton className="h-4 w-48" />
        <Skeleton className="h-9 w-2/3 max-w-xl" />
        <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
          <Skeleton className="h-64 w-full" />
          <Skeleton className="h-40 w-full" />
        </div>
      </div>
    </Container>
  )
}

function BookingDetail({ booking }: { booking: Booking }) {
  const [cancelOpen, setCancelOpen] = useState(false)
  const accept = useSubmitOnce(async () => (await getBookingService()).acceptProposal(booking.id))
  const [acceptError, setAcceptError] = useState<string>()

  const branch = toStoreBranch(booking)
  const proposed = booking.status === 'PROPOSED' && booking.proposedStartsAt ? booking.proposedStartsAt : undefined
  const whatsapp = booking.branch.whatsapp
    ? [{ branchId: booking.branch.id, name: booking.branch.name, phone: booking.branch.whatsapp }]
    : []
  const canDirect = directionsLinks(branch).length > 0

  async function acceptProposal() {
    setAcceptError(undefined)
    const result = await accept.run()
    if (result.status === 'ok') {
      refreshBuyerBookings()
      toast.success('Aceptaste la nueva hora. Tu cita quedó confirmada.')
    } else if (result.status === 'error') {
      if (result.error instanceof ApiError && result.error.code === 'BOOKING_INVALID_TRANSITION') refreshBuyerBookings()
      setAcceptError(bookingErrorMessage(result.error, 'No pudimos aceptar la nueva hora. Inténtalo de nuevo.'))
    }
  }

  return (
    <Container className="py-6 sm:py-8">
      <title>{`Cita ${booking.code} | Autopartco`}</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Mis citas', to: BOOKINGS_PATH }, { label: booking.code }]}
        label={
          <>
            Cita <span className="font-mono tabular-nums">{booking.code}</span> · pedida el <time dateTime={booking.createdAt}>{formatBookingMoment(booking.createdAt)}</time>
          </>
        }
        title={booking.service.name}
      >
        <BookingStatusBadge status={booking.status} className="text-sm" />
      </PageIntro>

      {proposed ? (
        <section aria-labelledby="propuesta" className="mb-6 flex flex-col gap-3 border-l-2 border-ap-info bg-ap-info-soft px-4 py-3">
          <h2 id="propuesta" className="text-base font-semibold text-ap-ink">
            {booking.store.name} propone otra hora
          </h2>
          <p className="text-sm text-ap-ink">
            Nueva hora: <time dateTime={proposed} className="font-semibold">{formatBookingMoment(proposed)}</time> (hora de Bogotá). Si no te sirve, puedes cancelar la cita.
          </p>
          {acceptError ? (
            <p role="alert" className="text-sm font-medium text-ap-danger">
              {acceptError}
            </p>
          ) : null}
          <Button className="h-11 w-full sm:w-fit" loading={accept.pending} onClick={() => void acceptProposal()}>
            <CalendarCheckIcon aria-hidden />
            Aceptar la nueva hora
          </Button>
        </section>
      ) : null}

      <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <section aria-labelledby="detalle-cita" className="flex min-w-0 flex-col gap-2">
          <h2 id="detalle-cita" className="text-block-title text-ap-ink">
            Detalle de la cita
          </h2>
          <dl className="divide-y divide-ap-line border-y border-ap-line">
            <Row term="Fecha y hora">
              <time dateTime={booking.startsAt}>{formatBookingMoment(booking.startsAt)}</time> <span className="font-normal text-ap-muted">(hora de Bogotá)</span>
            </Row>
            <Row term="Servicio">
              <Link to={servicePath(booking.service.id)} className="underline-offset-4 hover:text-ap-red hover:underline">
                {booking.service.name}
              </Link>
            </Row>
            <Row term="Almacén">
              <Link to={storePath(booking.store.id)} className="underline-offset-4 hover:text-ap-red hover:underline">
                {booking.store.name}
              </Link>
            </Row>
            <Row term="Sede">
              {booking.branch.name}
              {booking.branch.address ? <span className="block font-normal text-ap-muted">{[booking.branch.address, booking.branch.city].filter(Boolean).join(', ')}</span> : null}
            </Row>
            <Row term="Precio cotizado">
              {booking.quotedPrice !== null ? formatCOP(booking.quotedPrice) : 'A consultar con el taller'}
              <span className="block font-normal text-ap-muted">Precio de referencia, sujeto a revisión del vehículo.</span>
            </Row>
            {booking.promotion ? (
              <Row term="Promoción">
                {[booking.promotion.name, booking.promotion.label].filter(Boolean).join(' · ') || 'Promoción vigente'}
                <span className="block font-normal text-ap-muted">Aplicada al precio de referencia al pedir la cita.</span>
              </Row>
            ) : null}
            <VehicleRow booking={booking} />
            {booking.contactPhone ? <Row term="Teléfono">{booking.contactPhone}</Row> : null}
            {booking.buyerNote ? <Row term="Tu nota"><span className="whitespace-pre-line">{booking.buyerNote}</span></Row> : null}
            {booking.storeNote ? <Row term="Nota del almacén"><span className="whitespace-pre-line">{booking.storeNote}</span></Row> : null}
          </dl>
        </section>

        <aside aria-label="Acciones de la cita" className="flex flex-col gap-2">
          {whatsapp.length > 0 ? (
            <WhatsappButton
              storeId={booking.store.id}
              targets={whatsapp}
              subject={booking.service.name}
              path={servicePath(booking.service.id)}
              service={{ id: booking.service.id }}
              label="Escribir por WhatsApp a la sede"
              variant="outline"
              className="w-full"
            />
          ) : null}
          {canDirect ? <DirectionsMenu storeId={booking.store.id} branches={[branch]} serviceId={booking.service.id} className="w-full" /> : null}
          {isBookingActive(booking.status) ? (
            <>
              <Button variant="outline" className="h-11 w-full text-ap-danger" onClick={() => setCancelOpen(true)}>
                <XIcon aria-hidden />
                Cancelar la cita
              </Button>
              <CancelBookingDialog booking={booking} open={cancelOpen} onOpenChange={setCancelOpen} />
            </>
          ) : null}
        </aside>
      </div>
    </Container>
  )
}

/** `/mis-citas/:id`: estado, hora en Bogotá, sede y acciones (aceptar la hora propuesta, cancelar, WhatsApp, cómo llegar). */
export default function BookingDetailPage() {
  const { id = '' } = useParams()
  const result = useBuyerBooking(id)
  const booking = result.data

  if (result.status === 'error') {
    const notFound = result.error instanceof ApiError && result.error.status === 404
    return (
      <Container className="py-6 sm:py-8">
        <title>{notFound ? 'Cita no encontrada | Autopartco' : 'Mis citas | Autopartco'}</title>
        {notFound ? (
          <EmptyState icon={<CalendarSearchIcon />} title="No encontramos esta cita" description="Puede que el enlace esté incompleto o que la cita no sea de tu cuenta.">
            <Button nativeButton={false} render={<Link to={BOOKINGS_PATH} />}>
              Ver mis citas
            </Button>
          </EmptyState>
        ) : (
          <ErrorState title="No pudimos cargar esta cita" onRetry={result.retry} />
        )}
      </Container>
    )
  }
  if (!booking || result.isStale) return <Skeletons />
  return <BookingDetail key={booking.id} booking={booking} />
}
