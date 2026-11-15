import { useEffect, useRef, useState } from 'react'
import { Link, useLocation, useSearchParams } from 'react-router'
import { Stepper, type StepperStep } from '@/components/common/stepper'
import { Button, buttonVariants } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { Skeleton } from '@/components/ui/skeleton'
import { getProfile } from '@/features/account/api'
import { loginPathFor } from '@/features/auth/lib/return-path'
import { useSessionState } from '@/features/auth/hooks'
import { useVehicleFilterLabel } from '@/features/marketplace/hooks'
import { ApiError } from '@/lib/api'
import { useAsync } from '@/lib/use-async'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getBookingService } from '../../api/booking-service'
import type { Booking, BookingRequestBody } from '../../api/booking-types'
import type { ServiceDetail } from '../../api/types'
import { refreshAvailability, refreshBuyerBookings } from '../../booking-hooks'
import { bookingErrorMessage, normalizePhone } from '../../lib/booking'
import { hasWeeklyHours } from '../../lib/opening-hours'
import { BranchStep } from './branch-step'
import { ConfirmStep } from './confirm-step'
import { EMPTY_DRAFT, validateDraft, type BookingDraft, type DraftErrors } from '../../lib/booking-draft'
import { DetailsStep } from './details-step'
import { DoneStep } from './done-step'
import { SlotStep } from './slot-step'

type Phase = 'sede' | 'hora' | 'datos' | 'confirmar' | 'listo'

const BOOKING_PARAMS = ['agendar', 'sede', 'dia', 'hora'] as const

const PHASE_TITLE: Record<Phase, string> = {
  sede: 'Elige la sede',
  hora: 'Elige el día y la hora',
  datos: 'Tu vehículo y tu contacto',
  confirmar: 'Revisa y confirma',
  listo: 'Cita solicitada',
}

function validStart(value: string | null): string | undefined {
  return value && !Number.isNaN(new Date(value).getTime()) ? value : undefined
}

/** Teléfono del perfil para precargar el contacto; solo se pide con sesión y en los pasos que lo usan. */
function useProfilePhone(enabled: boolean): { phone: string; loading: boolean } {
  const result = useAsync(enabled ? 'portal:booking-profile-phone' : 'portal:booking-profile-off', () =>
    enabled ? getProfile().then((profile) => profile.numberPhone ?? '').catch(() => '') : Promise.resolve(''),
  )
  return { phone: result.status === 'success' && !result.isStale ? (result.data ?? '') : '', loading: enabled && result.status === 'loading' }
}

/**
 * Agenda del comprador en un diálogo con pasos: sede (se omite con una sola), día y hora, vehículo y contacto, y
 * confirmación. La sede, el día y la hora viajan en la URL (`?agendar=1&sede=&dia=&hora=`): sin sesión, «Ingresar» vuelve
 * al mismo paso. Los datos personales (vehículo, teléfono, nota) solo viven en memoria. Las horas y el resultado de la
 * solicitud los decide el servidor.
 */
export function BookingFlow({ service }: { service: ServiceDetail }) {
  const [params, setParams] = useSearchParams()
  const location = useLocation()
  const { session, ready } = useSessionState()
  const branches = service.branches.filter((item) => hasWeeklyHours(item.openingHours))
  const single = branches.length === 1

  const branchId = single ? branches[0].id : branches.find((branch) => branch.id === params.get('sede'))?.id
  const branch = branches.find((item) => item.id === branchId)
  const startsAt = validStart(params.get('hora'))
  const day = params.get('dia') ?? undefined

  const [explicit, setExplicit] = useState<Phase>()
  const [draft, setDraft] = useState<BookingDraft>(EMPTY_DRAFT)
  const [errors, setErrors] = useState<DraftErrors>({})
  const [slotError, setSlotError] = useState<string>()
  const [sendError, setSendError] = useState<string>()
  const [created, setCreated] = useState<Booking>()

  // Solo si se llegó con la hora ya elegida (vuelta del inicio de sesión) se salta directo a los datos.
  const [arrivedWithHour] = useState(() => !!validStart(params.get('hora')))
  const auto: Phase = !branchId ? 'sede' : arrivedWithHour && startsAt && session ? 'datos' : 'hora'
  let phase: Phase = created ? 'listo' : (explicit ?? auto)
  if (phase !== 'listo') {
    if (!branchId) phase = 'sede'
    else if ((phase === 'datos' || phase === 'confirmar') && !(startsAt && session)) phase = 'hora'
  }

  const profile = useProfilePhone(!!session && (phase === 'datos' || phase === 'confirmar'))
  const phone = draft.phone ?? profile.phone
  const vehicleLabel = useVehicleFilterLabel({ makeId: draft.makeId || undefined, modelId: draft.modelId || undefined })

  // El foco pasa al título del paso al cambiar de paso (no al abrir: lo gestiona el diálogo).
  const headingRef = useRef<HTMLHeadingElement>(null)
  const firstPhase = useRef(true)
  useEffect(() => {
    if (firstPhase.current) {
      firstPhase.current = false
      return
    }
    headingRef.current?.focus()
  }, [phase])

  function patchParams(patch: Partial<Record<'sede' | 'dia' | 'hora', string | undefined>>) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.set('agendar', '1')
        for (const [key, value] of Object.entries(patch)) {
          if (value) next.set(key, value)
          else next.delete(key)
        }
        return next
      },
      { replace: true, preventScrollReset: true },
    )
  }

  function close() {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const key of BOOKING_PARAMS) next.delete(key)
        return next
      },
      { replace: true, preventScrollReset: true },
    )
  }

  const submit = useSubmitOnce(async (body: BookingRequestBody) => (await getBookingService()).create(body))

  async function send() {
    if (!branchId || !startsAt) return
    const year = draft.year.trim()
    const plate = draft.plate.trim()
    const body: BookingRequestBody = {
      serviceId: service.id,
      branchId,
      startsAt,
      contactPhone: normalizePhone(phone),
      note: draft.note.trim() || undefined,
      vehicle: draft.makeId && draft.modelId ? { makeId: draft.makeId, modelId: draft.modelId, year: year ? Number(year) : undefined, plate: plate || undefined } : undefined,
    }
    setSendError(undefined)
    setSlotError(undefined)
    const result = await submit.run(body)
    if (result.status === 'ok') {
      refreshBuyerBookings()
      refreshAvailability()
      setCreated(result.value)
      return
    }
    if (result.status === 'busy') return
    const code = result.error instanceof ApiError ? result.error.code : undefined
    const message = bookingErrorMessage(result.error)
    if (code === 'BOOKING_SLOT_INVALID') {
      refreshAvailability()
      patchParams({ hora: undefined })
      setSlotError(message)
      setExplicit('hora')
    } else if (code === 'BRANCH_NOT_IN_SERVICE' && !single) {
      patchParams({ sede: undefined, dia: undefined, hora: undefined })
      setSlotError(message)
      setExplicit('sede')
    } else if (code === 'CONTACT_PHONE_REQUIRED') {
      setErrors({ phone: message })
      setExplicit('datos')
    } else if (result.error instanceof ApiError && result.error.status === 401) {
      setSendError('Tu sesión venció. Ingresa de nuevo para pedir la cita.')
    } else {
      setSendError(message)
    }
  }

  function goDetails() {
    const found = validateDraft(draft, phone)
    setErrors(found)
    if (Object.keys(found).length === 0) setExplicit('confirmar')
  }

  const order: Phase[] = single ? ['hora', 'datos', 'confirmar'] : ['sede', 'hora', 'datos', 'confirmar']
  const labels: Record<string, string> = { sede: 'Sede', hora: 'Día y hora', datos: 'Vehículo y contacto', confirmar: 'Confirmar' }
  const current = order.indexOf(phase)
  const steps: StepperStep[] = order.map((id, index) => ({
    id,
    label: labels[id],
    status: index < current ? 'complete' : 'idle',
    locked: index > current,
  }))

  const waitingSession = !ready && !!startsAt && !!branchId && phase === 'hora'
  const loginHref = loginPathFor(`${location.pathname}${location.search}`)

  return (
    <Dialog open onOpenChange={(open) => !open && close()}>
      <DialogContent className="max-w-xl">
        <DialogHeader>
          <DialogTitle>Agendar cita</DialogTitle>
          <DialogDescription>
            {service.name}
            {service.store ? ` · ${service.store.name}` : ''}
          </DialogDescription>
        </DialogHeader>

        {phase !== 'listo' ? <Stepper steps={steps} current={phase} onSelect={(id) => setExplicit(id as Phase)} label="Pasos para agendar la cita" /> : null}

        <div className="flex min-w-0 flex-col gap-4">
          <h3 ref={headingRef} tabIndex={-1} className="text-base font-semibold text-ap-ink outline-none">
            {PHASE_TITLE[phase]}
          </h3>

          {phase === 'sede' ? (
            <>
              {slotError ? (
                <p role="alert" className="rounded-md border border-ap-danger/25 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
                  {slotError}
                </p>
              ) : null}
              <BranchStep
                branches={branches}
                value={branchId}
                onChange={(id) => {
                  setSlotError(undefined)
                  patchParams({ sede: id, dia: undefined, hora: undefined })
                }}
              />
            </>
          ) : null}

          {phase === 'hora' && branchId ? (
            waitingSession ? (
              <div aria-hidden>
                <span className="sr-only" role="status">
                  Comprobando tu sesión
                </span>
                <Skeleton className="h-24 w-full" />
              </div>
            ) : (
              <>
                {branch && !single ? <p className="text-sm text-ap-muted">Sede: {branch.name}</p> : null}
                <SlotStep
                  serviceId={service.id}
                  branchId={branchId}
                  startsAt={startsAt}
                  day={day}
                  submitError={slotError}
                  emptyHint={!single ? 'Prueba con otra sede o escribe al taller para acordar una hora.' : 'Escribe al taller por WhatsApp o visita la sede para acordar una hora.'}
                  onSelect={(selection) => {
                    setSlotError(undefined)
                    patchParams({ dia: selection.day, hora: selection.startsAt })
                  }}
                />
                {startsAt && ready && !session ? (
                  <p className="text-sm text-ap-muted">Para enviar la solicitud necesitas ingresar. Conservamos la sede, el día y la hora que elegiste.</p>
                ) : null}
              </>
            )
          ) : null}

          {phase === 'datos' ? <DetailsStep draft={draft} onChange={(patch) => setDraft((value) => ({ ...value, ...patch }))} phone={phone} phoneLoading={profile.loading} errors={errors} /> : null}

          {phase === 'confirmar' && startsAt ? (
            <ConfirmStep
              service={service}
              branchName={branch?.name ?? ''}
              startsAt={startsAt}
              vehicleLabel={draft.makeId && draft.modelId ? vehicleLabel : undefined}
              year={draft.year.trim() || undefined}
              plate={draft.plate.trim() || undefined}
              phone={phone}
              note={draft.note.trim() || undefined}
              error={sendError}
            />
          ) : null}

          {phase === 'listo' && created ? <DoneStep booking={created} /> : null}
        </div>

        <DialogFooter>
          {phase === 'sede' ? (
            <Button className="h-11 px-4" disabled={!branchId} onClick={() => setExplicit('hora')}>
              Continuar
            </Button>
          ) : null}
          {phase === 'hora' ? (
            <>
              {!single ? (
                <Button variant="outline" className="h-11 px-4" onClick={() => setExplicit('sede')}>
                  Atrás
                </Button>
              ) : null}
              {startsAt && ready && !session ? (
                <Link to={loginHref} className={buttonVariants({ className: 'h-11 px-4' })}>
                  Ingresar para continuar
                </Link>
              ) : (
                <Button className="h-11 px-4" disabled={!startsAt || !session} onClick={() => setExplicit('datos')}>
                  Continuar
                </Button>
              )}
            </>
          ) : null}
          {phase === 'datos' ? (
            <>
              <Button variant="outline" className="h-11 px-4" onClick={() => setExplicit('hora')}>
                Atrás
              </Button>
              <Button className="h-11 px-4" onClick={goDetails}>
                Continuar
              </Button>
            </>
          ) : null}
          {phase === 'confirmar' ? (
            <>
              <Button variant="outline" className="h-11 px-4" disabled={submit.pending} onClick={() => setExplicit('datos')}>
                Atrás
              </Button>
              <Button className="h-11 px-4" disabled={submit.pending} onClick={() => void send()}>
                {submit.pending ? 'Enviando…' : 'Confirmar cita'}
              </Button>
            </>
          ) : null}
          {phase === 'listo' ? (
            <Button variant="outline" className="h-11 px-4" onClick={close}>
              Cerrar
            </Button>
          ) : null}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
