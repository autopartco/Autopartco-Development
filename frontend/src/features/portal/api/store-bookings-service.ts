import { DATA_SOURCE } from '@/lib/api'
import type { Booking, BookingStatus } from '@/features/services/api/booking-types'

/**
 * Cita vista por el almacén (`core/booking/Booking` en el portal): `version` para la concurrencia y `buyer`
 * (`phone` es nulo en una cita cancelada, rechazada o vencida; también `contactPhone` y la placa).
 */
export interface StoreBooking extends Booking {
  version: number
  buyer?: { name: string; phone: string | null }
}

export interface StoreBookingPage {
  items: StoreBooking[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface StoreBookingQuery {
  status?: BookingStatus
  branchId?: string
  /** `YYYY-MM-DD` (día en Bogotá sobre la hora de la cita). */
  from?: string
  to?: string
  /** Desde 1. */
  page: number
  size: number
}

/** Acciones del almacén (`StoreAction`); el servidor valida cada transición. */
export type StoreBookingAction = 'confirm' | 'propose' | 'reject' | 'cancel' | 'complete' | 'no-show'

/** Cuerpo de la acción (`BookingDecision`): `startsAt` solo en `propose`, `note` obligatoria en `reject` y `cancel`. */
export interface BookingDecisionBody {
  startsAt?: string
  note?: string
  version?: number
}

/** `storesuite/bookings/StoreBookingController`. Leer exige `BOOKING_READ`; actuar, `BOOKING_MANAGE`. */
export interface StoreBookingsService {
  list(storeId: string, query: StoreBookingQuery): Promise<StoreBookingPage>
  /**
   * `POST /stores/{id}/bookings/{bookingId}/{action}`. Errores: 400 `BOOKING_SLOT_INVALID`, `BOOKING_NOTE_REQUIRED`;
   * 404 `BOOKING_NOT_FOUND`; 409 `BOOKING_INVALID_TRANSITION`, `BOOKING_VERSION_CONFLICT`.
   */
  decide(storeId: string, bookingId: string, action: StoreBookingAction, body: BookingDecisionBody): Promise<StoreBooking>
}

let servicePromise: Promise<StoreBookingsService> | undefined

export function getStoreBookingsService(): Promise<StoreBookingsService> {
  servicePromise ??=
    DATA_SOURCE === 'http'
      ? import('./http/http-store-bookings-service').then((module) => module.createHttpStoreBookingsService())
      : import('./mock/mock-store-bookings-service').then((module) => module.createMockStoreBookingsService())
  return servicePromise
}
