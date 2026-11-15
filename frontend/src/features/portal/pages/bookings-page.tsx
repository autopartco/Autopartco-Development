import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { CalendarCheckIcon, PhoneIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { useVehicleFilterLabel } from '@/features/marketplace/hooks'
import { BookingStatusBadge } from '@/features/services/components/booking-status-badge'
import type { BookingStatus } from '@/features/services/api/booking-types'
import {
  BOOKING_STATUSES,
  bogotaDayOf,
  bookingErrorMessage,
  bookingStatus,
  formatBookingMoment,
  formatDayLong,
  formatSlotTime,
  isBookingStatus,
  todayInBogota,
} from '@/features/services/lib/booking'
import { ApiError } from '@/lib/api'
import { formatCOP, formatNumber } from '@/lib/format'
import { invalidateAsync } from '@/lib/use-async'
import { getStoreBookingsService, type BookingDecisionBody, type StoreBooking, type StoreBookingAction } from '../api/store-bookings-service'
import { ConfirmDialog } from '../components/confirm-dialog'
import { NoteDecisionDialog, ProposeDialog } from '../components/booking-decision-dialogs'
import { SelectField } from '../components/form-controls'
import { ListSkeleton } from '../components/list-skeleton'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { SEGMENT_GROUP_CLASS, segmentClass } from '../components/segmented'
import { ServerPager } from '../components/server-pager'
import { usePortal } from '../context'
import { ALL_BRANCHES_PARAM, branchParamValue, useBranchSearchParam, useStoreQuery } from '../hooks'

const PAGE_SIZE = 20
const DAY_SIZE = 100

type Dialog = { kind: 'reject' | 'cancel' | 'propose'; booking: StoreBooking }

/** Acciones que ofrece cada estado (espejo de `BookingRules`; el servidor valida la transición). */
function actionsFor(booking: StoreBooking, now: number): StoreBookingAction[] {
  if (booking.status === 'REQUESTED') return Date.parse(booking.startsAt) > now ? ['confirm', 'propose', 'reject'] : ['propose', 'reject']
  if (booking.status === 'CONFIRMED') return Date.parse(booking.startsAt) <= now ? ['complete', 'no-show', 'cancel'] : ['cancel']
  return []
}

const ACTION_LABEL: Record<StoreBookingAction, string> = {
  confirm: 'Confirmar',
  propose: 'Proponer otra hora',
  reject: 'Rechazar',
  cancel: 'Cancelar cita',
  complete: 'Marcar atendida',
  'no-show': 'No asistió',
}

type QuickAction = 'confirm' | 'complete' | 'no-show'

/** Texto de la confirmación de las acciones que no piden nota. */
const QUICK_COPY: Record<QuickAction, { title: string; confirm: string; description: (buyer?: string) => string }> = {
  confirm: {
    title: '¿Confirmar',
    confirm: 'Confirmar cita',
    description: (buyer) => `${buyer ?? 'El cliente'} recibirá un aviso de que su cita quedó confirmada y ese horario se reserva para él.`,
  },
  complete: {
    title: '¿Marcar como atendida',
    confirm: 'Marcar atendida',
    description: (buyer) => `Cerraremos la cita como atendida${buyer ? ` para ${buyer}` : ''}. No se puede deshacer.`,
  },
  'no-show': {
    title: '¿Marcar que no asistió a',
    confirm: 'Marcar no asistió',
    description: (buyer) => `Registraremos que ${buyer ?? 'el cliente'} no llegó a la cita y la cerraremos. No se puede deshacer.`,
  },
}

function Detail({ term, children }: { term: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5">
      <dt className="ap-index">{term}</dt>
      <dd className="text-sm text-ap-ink">{children}</dd>
    </div>
  )
}

function VehicleText({ booking }: { booking: StoreBooking }) {
  const vehicle = booking.vehicle
  const label = useVehicleFilterLabel({ makeId: vehicle?.makeId, modelId: vehicle?.modelId })
  if (!vehicle) return <span className="text-ap-muted">No indicó vehículo</span>
  return <>{[label ?? 'Vehículo indicado', vehicle.year, vehicle.plate].filter(Boolean).join(' · ')}</>
}

interface BookingCardProps {
  booking: StoreBooking
  canManage: boolean
  busy: boolean
  /** Reloj de la pantalla (ms): «atendida» y «no asistió» solo después de la hora de la cita. */
  now: number
  onAction: (booking: StoreBooking, action: StoreBookingAction) => void
}

function BookingCard({ booking, canManage, busy, now, onAction }: BookingCardProps) {
  const actions = canManage ? actionsFor(booking, now) : []
  const proposed = booking.status === 'PROPOSED' && booking.proposedStartsAt
  const promotion = booking.promotion ? [booking.promotion.name, booking.promotion.label].filter(Boolean).join(' · ') : ''
  return (
    <li className="flex flex-col gap-4 rounded-lg border border-ap-line-strong bg-ap-white p-4">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="flex min-w-0 flex-col gap-0.5">
          <p className="text-base font-semibold text-ap-ink">
            {formatBookingMoment(booking.startsAt)} <span className="font-normal text-ap-muted">· {formatSlotTime(booking.endsAt)}</span>
          </p>
          <p className="text-sm text-ap-ink">{booking.service.name}</p>
          <p className="ap-ref text-xs text-ap-muted">
            {booking.code} · {booking.branch.name}
          </p>
        </div>
        <BookingStatusBadge status={booking.status} />
      </div>
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-2">
        <Detail term="Comprador">
          {booking.buyer?.name ?? 'Sin nombre'}
          {booking.buyer?.phone ? (
            <a href={`tel:${booking.buyer.phone}`} className="mt-0.5 flex min-h-11 w-fit items-center gap-1.5 font-medium underline-offset-4 hover:text-ap-red hover:underline sm:min-h-0">
              <PhoneIcon className="size-3.5" aria-hidden />
              {booking.buyer.phone}
            </a>
          ) : (
            <span className="block text-ap-muted">Teléfono no disponible en esta cita</span>
          )}
        </Detail>
        <Detail term="Vehículo">
          <VehicleText booking={booking} />
        </Detail>
        <Detail term="Precio cotizado">
          {booking.quotedPrice !== null ? <span className="ap-num">{formatCOP(booking.quotedPrice)}</span> : 'A cotizar'}
          {promotion ? <span className="block text-ap-muted">Promoción: {promotion}</span> : null}
        </Detail>
        {proposed ? <Detail term="Hora propuesta">{formatBookingMoment(booking.proposedStartsAt!)} · espera la respuesta del comprador</Detail> : null}
        {booking.buyerNote ? (
          <Detail term="Nota del comprador">
            <span className="whitespace-pre-line">{booking.buyerNote}</span>
          </Detail>
        ) : null}
        {booking.storeNote ? (
          <Detail term="Tu nota">
            <span className="whitespace-pre-line">{booking.storeNote}</span>
          </Detail>
        ) : null}
      </dl>
      {actions.length > 0 ? (
        <div className="flex flex-wrap gap-2 border-t border-ap-line pt-3">
          {actions.map((action, index) => (
            <Button
              key={action}
              variant={index === 0 ? 'default' : action === 'reject' || action === 'cancel' ? 'destructive' : 'outline'}
              className="h-11 px-4"
              disabled={busy}
              onClick={() => onAction(booking, action)}
            >
              {ACTION_LABEL[action]}
              <span className="sr-only"> {booking.code}</span>
            </Button>
          ))}
        </div>
      ) : null}
    </li>
  )
}

/** `/portal/:storeId/citas`: bandeja de citas por estado, sucursal y fechas, con vista de día. */
export default function PortalBookingsPage() {
  const { storeId, can, activeBranches } = usePortal()
  const [params, setParams] = useSearchParams()
  const branchId = useBranchSearchParam()
  const dayView = params.get('vista') === 'dia'
  const day = params.get('dia') || todayInBogota()
  const rawStatus = params.get('estado')
  const status: BookingStatus | undefined = isBookingStatus(rawStatus) ? rawStatus : undefined
  const from = params.get('desde') ?? ''
  const to = params.get('hasta') ?? ''
  const page = Math.max(1, Number(params.get('pagina')) || 1)
  const canManage = can('BOOKING_MANAGE')
  const [dialog, setDialog] = useState<Dialog>()
  const [busyId, setBusyId] = useState<string>()
  const [quick, setQuick] = useState<{ action: StoreBookingAction; booking: StoreBooking }>()
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60_000)
    return () => clearInterval(timer)
  }, [])

  const query = dayView
    ? { status, branchId: branchId || undefined, from: day, to: day, page: 1, size: DAY_SIZE }
    : { status, branchId: branchId || undefined, from: from || undefined, to: to || undefined, page, size: PAGE_SIZE }
  const result = useStoreQuery(`bookings:${JSON.stringify(query)}`, async (id) => (await getStoreBookingsService()).list(id, query))
  const pending = useStoreQuery('bookings:pending', async (id) => (await getStoreBookingsService()).list(id, { status: 'REQUESTED', page: 1, size: 1 }))
  const pendingCount = pending.data?.total ?? 0

  function update(changes: Record<string, string | undefined>, resetPage = true) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        if (resetPage) next.delete('pagina')
        return next
      },
      { replace: true },
    )
  }

  const refresh = () => invalidateAsync(`portal:${storeId}:bookings`)

  /** Devuelve el mensaje de error (el diálogo lo muestra) o `undefined` si se aplicó. */
  async function decide(booking: StoreBooking, action: StoreBookingAction, body: Omit<BookingDecisionBody, 'version'> = {}): Promise<string | undefined> {
    setBusyId(booking.id)
    try {
      await (await getStoreBookingsService()).decide(storeId, booking.id, action, { ...body, version: booking.version })
      refresh()
      toast.success(`${ACTION_LABEL[action]}: cita ${booking.code} actualizada.`)
      return undefined
    } catch (error) {
      if (error instanceof ApiError && (error.code === 'BOOKING_VERSION_CONFLICT' || error.code === 'BOOKING_INVALID_TRANSITION')) {
        refresh()
        const message = 'La cita cambió mientras la revisabas. Ya cargamos su estado actual: revísala y vuelve a intentarlo.'
        toast.warning(message)
        return message
      }
      return bookingErrorMessage(error, 'No pudimos actualizar la cita. Inténtalo de nuevo.')
    } finally {
      setBusyId(undefined)
    }
  }

  async function onAction(booking: StoreBooking, action: StoreBookingAction) {
    if (action === 'reject' || action === 'cancel' || action === 'propose') {
      setDialog({ kind: action, booking })
      return
    }
    setQuick({ action, booking })
  }

  const quickCopy = quick ? QUICK_COPY[quick.action as QuickAction] : undefined

  const branchOptions = activeBranches.map((branch) => ({ value: branch.id, label: branch.name }))

  return (
    <>
      <title>Citas | Autopartco</title>
      <PageHeader
        title="Citas"
        description="Solicitudes de agenda de tus servicios. Confirma, propone otra hora o rechaza con una nota para el comprador."
        actions={
          <div className={SEGMENT_GROUP_CLASS} role="group" aria-label="Vista">
            <button type="button" aria-pressed={!dayView} className={segmentClass(!dayView)} onClick={() => update({ vista: undefined })}>
              Lista
            </button>
            <button type="button" aria-pressed={dayView} className={segmentClass(dayView)} onClick={() => update({ vista: 'dia' })}>
              Día
            </button>
          </div>
        }
      />
      <form role="search" aria-label="Filtrar citas" onSubmit={(event) => event.preventDefault()} className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <SelectField
          id="citas-estado"
          label="Estado"
          placeholder="Todos"
          value={status ?? ''}
          onChange={(event) => update({ estado: event.target.value || undefined })}
          options={BOOKING_STATUSES.map((code) => ({ value: code, label: bookingStatus(code).label }))}
        />
        {activeBranches.length > 1 ? (
          <SelectField
            id="citas-sucursal"
            label="Sucursal"
            placeholder="Todas"
            value={branchId}
            onChange={(event) => update({ sucursal: branchParamValue(event.target.value) })}
            options={branchOptions}
          />
        ) : null}
        {dayView ? (
          <TextField id="citas-dia" type="date" label="Día" value={day} onChange={(event) => update({ dia: event.target.value || undefined })} />
        ) : (
          <>
            <TextField id="citas-desde" type="date" label="Desde" value={from} max={to || undefined} onChange={(event) => update({ desde: event.target.value || undefined })} />
            <TextField id="citas-hasta" type="date" label="Hasta" value={to} min={from || undefined} onChange={(event) => update({ hasta: event.target.value || undefined })} />
          </>
        )}
      </form>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <Button
          variant={status === 'REQUESTED' ? 'default' : 'outline'}
          className="h-11 px-4"
          aria-pressed={status === 'REQUESTED'}
          onClick={() => update({ estado: status === 'REQUESTED' ? undefined : 'REQUESTED' })}
        >
          Pendientes de respuesta
          <span className="ml-1 inline-flex min-w-6 items-center justify-center rounded-full bg-ap-red px-1.5 text-xs font-semibold text-ap-white tabular-nums" aria-label={`${pendingCount} pendientes`}>
            {formatNumber(pendingCount)}
          </span>
        </Button>
        {status || from || to || branchId ? (
          <Button variant="ghost" className="h-11 px-3" onClick={() => setParams(new URLSearchParams(dayView ? { vista: 'dia', sucursal: ALL_BRANCHES_PARAM } : { sucursal: ALL_BRANCHES_PARAM }), { replace: true })}>
            Quitar filtros
          </Button>
        ) : null}
      </div>
      <QueryBoundary result={result} what="tus citas" keepPrevious skeleton={<ListSkeleton rows={3} label="Cargando tus citas" />}>
        {(data) => {
          const items = dayView ? data.items.toSorted((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt)) : data.items
          if (items.length === 0) {
            return (
              <EmptyState
                icon={<CalendarCheckIcon />}
                title={dayView ? `Sin citas el ${formatDayLong(day)}` : status || from || to || branchId ? 'Ninguna cita coincide' : 'Aún no tienes citas'}
                description={
                  status || from || to || branchId || dayView
                    ? 'Prueba con otro día, estado o sucursal.'
                    : 'Cuando un comprador pida cita para uno de tus servicios con agenda en línea, aparecerá aquí.'
                }
              />
            )
          }
          return (
            <div className="flex flex-col gap-4">
              {dayView ? (
                <h2 className="text-base font-semibold text-ap-ink">
                  {formatDayLong(bogotaDayOf(items[0]?.startsAt ?? '') || day)} · {items.length === 1 ? '1 cita' : `${items.length} citas`}
                </h2>
              ) : null}
              <ul className="flex flex-col gap-3" aria-label="Citas">
                {items.map((booking) => (
                  <BookingCard key={booking.id} booking={booking} canManage={canManage} busy={busyId === booking.id} now={now} onAction={(b, a) => void onAction(b, a)} />
                ))}
              </ul>
              {dayView ? null : (
                <ServerPager
                  page={data.page}
                  totalPages={data.totalPages}
                  total={data.total}
                  singular="cita"
                  plural="citas"
                  busy={result.status === 'loading'}
                  onPageChange={(next) => update({ pagina: next > 1 ? String(next) : undefined }, false)}
                />
              )}
            </div>
          )
        }}
      </QueryBoundary>
      <ConfirmDialog
        open={quick !== undefined}
        onOpenChange={(open) => !open && setQuick(undefined)}
        title={quick && quickCopy ? `${quickCopy.title} la cita ${quick.booking.code}?` : ''}
        description={quick && quickCopy ? `${quickCopy.description(quick.booking.buyer?.name)}` : ''}
        confirmLabel={quickCopy?.confirm ?? ''}
        loadingText="Guardando…"
        destructive={quick?.action === 'no-show'}
        errorMessage={(error) => (error instanceof Error ? error.message : 'No pudimos actualizar la cita. Inténtalo de nuevo.')}
        onConfirm={async () => {
          if (!quick) return
          const message = await decide(quick.booking, quick.action)
          if (message) throw new Error(message)
        }}
      />
      {dialog && dialog.kind !== 'propose' ? (
        <NoteDecisionDialog
          key={`${dialog.kind}-${dialog.booking.id}`}
          kind={dialog.kind}
          booking={dialog.booking}
          open
          onOpenChange={(open) => !open && setDialog(undefined)}
          onSubmit={({ note }) => decide(dialog.booking, dialog.kind, { note })}
        />
      ) : null}
      {dialog?.kind === 'propose' ? (
        <ProposeDialog
          key={`propose-${dialog.booking.id}`}
          booking={dialog.booking}
          open
          onOpenChange={(open) => !open && setDialog(undefined)}
          onSubmit={({ startsAt }) => decide(dialog.booking, 'propose', { startsAt })}
        />
      ) : null}
    </>
  )
}
