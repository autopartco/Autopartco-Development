import { TriangleAlertIcon } from 'lucide-react'
import { formatCOP } from '@/lib/format'
import type { ServiceDetail } from '../../api/types'
import { formatBookingMoment } from '../../lib/booking'

interface ConfirmStepProps {
  service: ServiceDetail
  branchName: string
  startsAt: string
  vehicleLabel?: string
  year?: string
  plate?: string
  phone: string
  note?: string
  /** Error del envío que no corresponde a otro paso (límite de citas, agenda cerrada…). */
  error?: string
}

function Row({ term, children }: { term: string; children: React.ReactNode }) {
  return (
    <div className="flex flex-col gap-0.5 py-2 sm:flex-row sm:gap-4">
      <dt className="shrink-0 text-sm text-ap-muted sm:w-32">{term}</dt>
      <dd className="min-w-0 text-sm font-medium break-words text-ap-ink">{children}</dd>
    </div>
  )
}

/** Paso «Confirmar»: resumen y aviso de precio de referencia antes de enviar la solicitud. */
export function ConfirmStep({ service, branchName, startsAt, vehicleLabel, year, plate, phone, note, error }: ConfirmStepProps) {
  return (
    <div className="flex flex-col gap-4">
      <dl className="divide-y divide-ap-line border-y border-ap-line">
        <Row term="Servicio">{service.name}</Row>
        {service.store ? <Row term="Almacén">{service.store.name}</Row> : null}
        <Row term="Sede">{branchName}</Row>
        <Row term="Fecha y hora">
          <span className="capitalize first-letter:uppercase">{formatBookingMoment(startsAt)}</span> <span className="font-normal text-ap-muted">(hora de Bogotá)</span>
        </Row>
        {vehicleLabel ? (
          <Row term="Vehículo">
            {[vehicleLabel, year, plate].filter(Boolean).join(' · ')}
          </Row>
        ) : null}
        <Row term="Teléfono">{phone}</Row>
        {note ? <Row term="Nota">{note}</Row> : null}
        <Row term="Precio">
          {service.priceFrom !== undefined ? `Desde ${formatCOP(service.priceFrom)}` : 'A consultar con el taller'}
          {service.promotion ? <span className="ml-2 font-normal text-ap-muted">({service.promotion.label} aplicado)</span> : null}
        </Row>
      </dl>

      <p className="flex items-start gap-2 border-l-2 border-ap-warning bg-ap-warning-soft px-3 py-2.5 text-sm text-ap-ink">
        <TriangleAlertIcon className="mt-0.5 size-4 shrink-0 text-ap-warning" aria-hidden />
        <span>
          El precio es de referencia y está sujeto a revisión del vehículo. El taller confirma el valor final antes de empezar. Tu cita queda solicitada hasta que el
          almacén la confirme.
        </span>
      </p>

      {error ? (
        <p role="alert" className="rounded-md border border-ap-danger/25 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
          {error}
        </p>
      ) : null}
    </div>
  )
}
