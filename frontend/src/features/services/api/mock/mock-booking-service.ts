import { ApiError, simulateLatency } from '@/lib/api'
import type { Availability, AvailabilityDay, Booking, BookingService, BookingStatus } from '../booking-types'
import { bogotaDayOf } from '../../lib/booking'

/**
 * Agenda simulada en memoria (fuente `mock`): horas de 9:00 a 16:00 de lunes a sábado, con algunas «ocupadas».
 * Las citas se crean `REQUESTED` y se pueden cancelar; el resto de estados solo los produce el servidor real.
 */
const SLOT_HOURS = [9, 10, 11, 14, 15, 16]
const bookings: Booking[] = []

function addDays(ymd: string, days: number): string {
  const date = new Date(`${ymd}T12:00:00-05:00`)
  date.setUTCDate(date.getUTCDate() + days)
  return bogotaDayOf(date.toISOString())
}

function day(date: string): AvailabilityDay {
  const weekday = new Date(`${date}T12:00:00-05:00`).getUTCDay()
  if (weekday === 0) return { date, slots: [] }
  const now = Date.now()
  const slots = SLOT_HOURS.map((hour, index) => ({
    startsAt: `${date}T${String(hour).padStart(2, '0')}:00:00-05:00`,
    busy: (index + date.charCodeAt(9)) % 5 === 0,
  })).filter((slot) => new Date(slot.startsAt).getTime() > now + 2 * 3600_000)
  return { date, slots }
}

function find(bookingId: string): Booking {
  const booking = bookings.find((item) => item.id === bookingId)
  if (!booking) throw new ApiError('No encontramos esta cita.', { status: 404, code: 'BOOKING_NOT_FOUND' })
  return booking
}

export function createMockBookingService(): BookingService {
  return {
    simulated: true,
    async availability({ from, days }) {
      await simulateLatency(350)
      const list = Array.from({ length: Math.min(Math.max(days, 1), 14) }, (_, index) => day(addDays(from, index)))
      return { slotMinutes: 60, days: list } satisfies Availability
    },
    async create(body) {
      await simulateLatency(500)
      const valid = day(bogotaDayOf(body.startsAt)).slots.some((slot) => new Date(slot.startsAt).getTime() === new Date(body.startsAt).getTime())
      if (!valid) throw new ApiError('La hora elegida no está disponible.', { status: 400, code: 'BOOKING_SLOT_INVALID' })
      if (bookings.filter((item) => item.status === 'REQUESTED').length >= 3) {
        throw new ApiError('Máximo de citas activas.', { status: 429, code: 'BOOKING_LIMIT_REACHED' })
      }
      const startsAt = body.startsAt
      const booking: Booking = {
        id: crypto.randomUUID(),
        code: `CIT-${String(bookings.length + 1).padStart(5, '0')}`,
        status: 'REQUESTED',
        startsAt,
        endsAt: new Date(new Date(startsAt).getTime() + 3600_000).toISOString(),
        proposedStartsAt: null,
        service: { id: body.serviceId, name: 'Servicio de ejemplo', category: 'MECHANICS', imageUrl: null },
        store: { id: 'store-demo', name: 'Taller de ejemplo', logoUrl: null },
        branch: { id: body.branchId, name: 'Sede principal', address: 'Calle 10 # 20-30', city: 'Bogotá', department: 'Bogotá D.C.', latitude: null, longitude: null, phone: null, whatsapp: null },
        quotedPrice: null,
        promotion: null,
        vehicle: body.vehicle ?? null,
        contactPhone: body.contactPhone ?? null,
        buyerNote: body.note ?? null,
        storeNote: null,
        createdAt: new Date().toISOString(),
      }
      bookings.unshift(booking)
      return structuredClone(booking)
    },
    async list({ status, page, size }) {
      await simulateLatency(300)
      const filtered = bookings.filter((item) => !status || item.status === status)
      const items = filtered.slice((page - 1) * size, page * size)
      return { items: structuredClone(items), page, size, total: filtered.length, totalPages: Math.max(1, Math.ceil(filtered.length / size)) }
    },
    async get(bookingId) {
      await simulateLatency(250)
      return structuredClone(find(bookingId))
    },
    async acceptProposal(bookingId) {
      await simulateLatency(300)
      const booking = find(bookingId)
      if (booking.status !== 'PROPOSED' || !booking.proposedStartsAt) {
        throw new ApiError('La cita no tiene una hora propuesta.', { status: 409, code: 'BOOKING_INVALID_TRANSITION' })
      }
      booking.startsAt = booking.proposedStartsAt
      booking.proposedStartsAt = null
      booking.status = 'CONFIRMED'
      return structuredClone(booking)
    },
    async cancel(bookingId) {
      await simulateLatency(300)
      const booking = find(bookingId)
      const active: BookingStatus[] = ['REQUESTED', 'PROPOSED', 'CONFIRMED']
      if (!active.includes(booking.status)) {
        throw new ApiError('La cita ya no se puede cancelar.', { status: 409, code: 'BOOKING_INVALID_TRANSITION' })
      }
      booking.status = 'CANCELLED_BY_BUYER'
      booking.proposedStartsAt = null
      return structuredClone(booking)
    },
  }
}

