import { useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { TruckIcon } from 'lucide-react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { getOrderService } from '../api/order-service'
import type { OrderDetail, ShipmentStatus } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { focusById } from '../lib/forms'
import { formatDateTime, SHIPMENT_ACTION_LABEL, SHIPMENT_NEXT, SHIPMENT_STATUS_LABEL } from '../lib/labels'
import { TextAreaField } from './form-controls'
import { StatusBadge } from './status-badge'

/** Registro del envío: solo con el pedido en preparación (`SELLER_SHIPMENT_ORDER_NOT_PREPARING` en el servidor). */
function ShipmentCreateForm({ order }: { order: OrderDetail }) {
  const { storeId, invalidateStore } = usePortal()
  const [carrier, setCarrier] = useState('')
  const [tracking, setTracking] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [pending, setPending] = useState(false)
  const [formError, setFormError] = useState<string>()
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})
  const alertRef = useRef<HTMLDivElement>(null)

  const errors = {
    carrier: !carrier.trim() ? 'Escribe la transportadora.' : carrier.trim().length > 100 ? 'Máximo 100 caracteres.' : undefined,
    trackingNumber: !tracking.trim() ? 'Escribe el número de guía.' : tracking.trim().length > 100 ? 'Máximo 100 caracteres.' : undefined,
  }
  const shown = (field: 'carrier' | 'trackingNumber') => serverErrors[field] ?? (submitted ? errors[field] : undefined)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    setSubmitted(true)
    setFormError(undefined)
    if (errors.carrier || errors.trackingNumber) {
      focusById(errors.carrier ? 'envio-carrier' : 'envio-tracking')
      return
    }
    setPending(true)
    try {
      await (await getOrderService()).createShipment(storeId, order.saleId, { carrier: carrier.trim(), trackingNumber: tracking.trim() })
      invalidateStore()
      toast.success('Envío registrado. El cliente recibirá un aviso.')
    } catch (cause) {
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) setServerErrors(cause.fieldErrors)
      setFormError(actionErrorMessage(cause, 'No pudimos registrar el envío.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    } finally {
      setPending(false)
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-4" aria-busy={pending || undefined}>
      <p className="text-sm text-muted-foreground">El pedido está listo para salir. Registra la transportadora y la guía para que el cliente pueda seguirlo.</p>
      {formError ? <FormAlert ref={alertRef} title="No se registró el envío">{formError}</FormAlert> : null}
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField id="envio-carrier" label="Transportadora" value={carrier} onChange={(event) => setCarrier(event.target.value)} error={shown('carrier')} maxLength={100} autoComplete="off" required hint="Ej.: Servientrega, Coordinadora." />
        <TextField id="envio-tracking" label="Número de guía" value={tracking} onChange={(event) => setTracking(event.target.value)} error={shown('trackingNumber')} maxLength={100} autoComplete="off" required />
      </div>
      <Button type="submit" className="h-11 w-fit px-5" disabled={pending}>
        <TruckIcon aria-hidden />
        {pending ? 'Registrando…' : 'Registrar envío'}
      </Button>
    </form>
  )
}

function ShipmentUpdateForm({ order, shipmentStatus }: { order: OrderDetail; shipmentStatus: ShipmentStatus }) {
  const { storeId, invalidateStore } = usePortal()
  const [notes, setNotes] = useState('')
  const [busy, setBusy] = useState<string>()
  const [error, setError] = useState<string>()
  const next = SHIPMENT_NEXT[shipmentStatus]
  if (next.length === 0) return null

  async function change(status: (typeof next)[number]) {
    setBusy(status)
    setError(undefined)
    try {
      await (await getOrderService()).updateShipment(storeId, order.saleId, { status, notes: notes.trim() || undefined })
      setNotes('')
      invalidateStore()
      toast.success(status === 'DELIVERED' ? 'Marcaste el pedido como entregado.' : 'Actualizaste el envío.')
    } catch (cause) {
      setError(actionErrorMessage(cause, 'No pudimos actualizar el envío.'))
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      {error ? (
        <p role="alert" className="rounded-lg border border-destructive/25 bg-destructive/5 p-3 text-sm font-medium text-destructive">
          {error}
        </p>
      ) : null}
      <TextAreaField
        id="envio-notas"
        label="Nota para el seguimiento"
        optional
        value={notes}
        onChange={(event) => setNotes(event.target.value)}
        maxLength={255}
        className="min-h-16"
        hint="Se guarda en el historial del envío. Máximo 255 caracteres."
      />
      <div className="flex flex-wrap gap-2">
        {next.map((status) => (
          <Button key={status} variant={status === 'DELIVERED' ? 'default' : 'outline'} className="h-11 px-4" disabled={busy !== undefined} onClick={() => void change(status)}>
            {busy === status ? 'Guardando…' : SHIPMENT_ACTION_LABEL[status]}
          </Button>
        ))}
      </div>
    </div>
  )
}

/** Envío del pedido: formulario de registro, avance de estados e historial de eventos. */
export function ShipmentPanel({ order }: { order: OrderDetail }) {
  const { can } = usePortal()
  const canUpdate = can('SHIPMENT_UPDATE')
  const shipment = order.shipment

  if (!shipment) {
    if (order.status === 'PREPARING' && canUpdate) return <ShipmentCreateForm order={order} />
    return (
      <p className="text-sm text-muted-foreground">
        {order.status === 'PREPARING'
          ? 'Todavía no se registra el envío.'
          : order.status === 'CANCELLED' || order.status === 'REJECTED'
            ? 'El pedido no tiene envío.'
            : 'El envío se registra cuando el pedido esté en preparación.'}
      </p>
    )
  }

  const shipmentStatus = shipment.status
  return (
    <div className="flex flex-col gap-5">
      <dl className="grid gap-x-6 gap-y-3 sm:grid-cols-3">
        <div>
          <dt className="text-sm text-muted-foreground">Transportadora</dt>
          <dd className="font-semibold">{shipment.carrier}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Guía</dt>
          <dd className="font-mono font-semibold">{shipment.trackingNumber}</dd>
        </div>
        <div>
          <dt className="text-sm text-muted-foreground">Estado</dt>
          <dd>
            <StatusBadge tone={shipmentStatus === 'DELIVERED' ? 'success' : 'info'}>{SHIPMENT_STATUS_LABEL[shipmentStatus]}</StatusBadge>
          </dd>
        </div>
      </dl>

      <div>
        <h3 className="mb-2 text-sm font-semibold">Historial</h3>
        <ol className="flex flex-col gap-2 border-l-2 border-border pl-4">
          {shipment.events.map((event, index) => (
            <li key={`${event.status}-${index}`} className="text-sm">
              <span className="font-semibold">{event.status in SHIPMENT_STATUS_LABEL ? SHIPMENT_STATUS_LABEL[event.status as ShipmentStatus] : event.status}</span>
              <span className="text-muted-foreground"> · {formatDateTime(event.occurredAt)}</span>
              {event.notes ? <p className="text-muted-foreground">{event.notes}</p> : null}
            </li>
          ))}
        </ol>
      </div>

      {canUpdate ? <ShipmentUpdateForm order={order} shipmentStatus={shipmentStatus} /> : null}
    </div>
  )
}
