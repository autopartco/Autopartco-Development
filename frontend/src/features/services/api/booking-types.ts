import type { ServiceCategory } from '@/features/marketplace/api/types'

/**
 * Contrato de la agenda del comprador (`core/booking/*`, `BuyerBookingController`, `ServiceAvailabilityController`).
 * Las fechas llegan con zona (ISO-8601) y se muestran siempre en hora de Bogotá; el servidor decide estado y precio.
 */

export type BookingStatus =
  | 'REQUESTED'
  | 'PROPOSED'
  | 'CONFIRMED'
  | 'CANCELLED_BY_BUYER'
  | 'REJECTED'
  | 'CANCELLED_BY_STORE'
  | 'COMPLETED'
  | 'NO_SHOW'
  | 'EXPIRED'

export interface AvailabilitySlot {
  /** ISO-8601 con zona, p. ej. `2026-11-20T09:00:00-05:00`. */
  startsAt: string
  /** Ya hay una cita confirmada que se cruza: orientativo, el almacén decide. */
  busy: boolean
}

export interface AvailabilityDay {
  /** `YYYY-MM-DD` (día en Bogotá). */
  date: string
  slots: AvailabilitySlot[]
}

export interface Availability {
  slotMinutes: number
  days: AvailabilityDay[]
}

export interface AvailabilityQuery {
  serviceId: string
  branchId: string
  /** `YYYY-MM-DD`. */
  from: string
  /** De 1 a 14. */
  days: number
}

export interface BookingVehicle {
  makeId: string
  modelId: string
  year?: number | null
  /** Ausente en una cita cancelada, rechazada o vencida. */
  plate?: string | null
}

export interface BookingRequestBody {
  serviceId: string
  branchId: string
  startsAt: string
  contactPhone?: string
  vehicle?: BookingVehicle
  note?: string
}

export interface Booking {
  id: string
  code: string
  status: BookingStatus
  startsAt: string
  endsAt: string
  /** Solo con estado `PROPOSED`. */
  proposedStartsAt: string | null
  service: { id: string; name: string; category: ServiceCategory; imageUrl: string | null }
  store: { id: string; name: string; logoUrl: string | null }
  branch: {
    id: string
    name: string
    address: string | null
    city: string | null
    department: string | null
    latitude: number | null
    longitude: number | null
    phone: string | null
    whatsapp: string | null
  }
  quotedPrice: number | null
  /** Promoción vigente al pedir; `label` es el texto del descuento (p. ej. `-15%`). */
  promotion: { id: string; name?: string | null; label?: string | null } | null
  vehicle: BookingVehicle | null
  contactPhone: string | null
  buyerNote: string | null
  storeNote: string | null
  createdAt: string
}

export interface BookingPage {
  items: Booking[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface BookingService {
  /** `true` con la fuente `mock`. */
  readonly simulated: boolean
  /** Público: no exige sesión. 400 `BRANCH_NOT_IN_SERVICE` / `BOOKING_DISABLED`. */
  availability(query: AvailabilityQuery): Promise<Availability>
  /** 201. Errores: 400 `BOOKING_SLOT_INVALID`, `BRANCH_NOT_IN_SERVICE`, `BOOKING_DISABLED`, `CONTACT_PHONE_REQUIRED`; 429 `BOOKING_LIMIT_REACHED`. */
  create(body: BookingRequestBody): Promise<Booking>
  /** `page` desde 1; `status` opcional. */
  list(query: { status?: BookingStatus; page: number; size: number }): Promise<BookingPage>
  /** 404 `BOOKING_NOT_FOUND` si no existe o es ajena. */
  get(bookingId: string): Promise<Booking>
  /** 409 `BOOKING_INVALID_TRANSITION` si ya no aplica. */
  acceptProposal(bookingId: string): Promise<Booking>
  cancel(bookingId: string, reason?: string): Promise<Booking>
}
