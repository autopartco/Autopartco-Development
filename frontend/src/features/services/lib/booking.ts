import { ApiError } from '@/lib/api'
import type { Tone } from '@/features/portal/lib/labels'
import type { BookingStatus } from '../api/booking-types'

const TIME_ZONE = 'America/Bogota'

/** Etiquetas de estado: siempre con texto, el color solo refuerza. */
const STATUS: Record<BookingStatus, { label: string; tone: Tone }> = {
  REQUESTED: { label: 'Solicitada', tone: 'warning' },
  PROPOSED: { label: 'Nueva hora propuesta', tone: 'info' },
  CONFIRMED: { label: 'Confirmada', tone: 'success' },
  CANCELLED_BY_BUYER: { label: 'Cancelada por ti', tone: 'neutral' },
  REJECTED: { label: 'Rechazada', tone: 'danger' },
  CANCELLED_BY_STORE: { label: 'Cancelada por el almacén', tone: 'danger' },
  COMPLETED: { label: 'Atendida', tone: 'success' },
  NO_SHOW: { label: 'No asististe', tone: 'neutral' },
  EXPIRED: { label: 'Vencida', tone: 'neutral' },
}

export const BOOKING_STATUSES = Object.keys(STATUS) as BookingStatus[]

export function bookingStatus(code: string): { label: string; tone: Tone } {
  return STATUS[code as BookingStatus] ?? { label: code, tone: 'neutral' }
}

/** Estados en los que el comprador todavía puede cancelar (el servidor valida la transición). */
export function isBookingActive(status: BookingStatus): boolean {
  return status === 'REQUESTED' || status === 'PROPOSED' || status === 'CONFIRMED'
}

export function isBookingStatus(value: string | null | undefined): value is BookingStatus {
  return !!value && value in STATUS
}

// ------------------------------------------------------------------ fechas (siempre hora de Bogotá)

const ymdFormatter = new Intl.DateTimeFormat('en-CA', { timeZone: TIME_ZONE, year: 'numeric', month: '2-digit', day: '2-digit' })

/** Hoy en Bogotá como `YYYY-MM-DD`. */
export function todayInBogota(now = new Date()): string {
  return ymdFormatter.format(now)
}

function atNoon(ymd: string): Date {
  return new Date(`${ymd}T12:00:00-05:00`)
}

/** «jue 20 nov» para el selector de día. */
export function formatDayChip(ymd: string): string {
  const date = atNoon(ymd)
  if (Number.isNaN(date.getTime())) return ymd
  return new Intl.DateTimeFormat('es-CO', { timeZone: TIME_ZONE, weekday: 'short', day: 'numeric', month: 'short' }).format(date).replace(/\./g, '')
}

/** «jueves 20 de noviembre». */
export function formatDayLong(ymd: string): string {
  const date = atNoon(ymd)
  if (Number.isNaN(date.getTime())) return ymd
  return new Intl.DateTimeFormat('es-CO', { timeZone: TIME_ZONE, weekday: 'long', day: 'numeric', month: 'long' }).format(date)
}

/** «9:00 a. m.». */
export function formatSlotTime(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat('es-CO', { timeZone: TIME_ZONE, hour: 'numeric', minute: '2-digit' }).format(date)
}

/** «jueves 20 de noviembre de 2026, 9:00 a. m.» */
export function formatBookingMoment(iso: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return iso
  return new Intl.DateTimeFormat('es-CO', { timeZone: TIME_ZONE, dateStyle: 'full', timeStyle: 'short' }).format(date)
}

/** Día en Bogotá (`YYYY-MM-DD`) de una fecha con zona. */
export function bogotaDayOf(iso: string): string {
  const date = new Date(iso)
  return Number.isNaN(date.getTime()) ? '' : ymdFormatter.format(date)
}

// ------------------------------------------------------------------ validación (el servidor vuelve a validar)

export const NOTE_MAX = 500

/** 10 dígitos o +57 y 10 dígitos; ignora espacios y guiones. `undefined` si no es válido. */
export function normalizePhone(value: string): string | undefined {
  const compact = value.replace(/[\s-]/g, '')
  return /^(\+57)?\d{10}$/.test(compact) ? compact : undefined
}

export function isValidPlate(value: string): boolean {
  return /^[A-Za-z0-9]{5,7}$/.test(value)
}

// ------------------------------------------------------------------ errores

/** Mensaje accionable por código de error del servidor. */
export function bookingErrorMessage(error: unknown, fallback = 'No pudimos completar la acción. Inténtalo de nuevo.'): string {
  if (!(error instanceof ApiError)) return fallback
  switch (error.code) {
    case 'BOOKING_SLOT_INVALID':
      return 'Esa hora ya no está disponible. Elige otra hora para tu cita.'
    case 'BOOKING_LIMIT_REACHED':
      return 'Ya tienes el máximo de citas activas con este almacén. Cancela una o espera a que la atiendan para pedir otra.'
    case 'BOOKING_DISABLED':
      return 'Este servicio ya no admite agenda en línea. Escribe por WhatsApp o visita la sede para pedir tu cita.'
    case 'BRANCH_NOT_IN_SERVICE':
      return 'Esta sede ya no presta el servicio. Elige otra sede o escribe al almacén.'
    case 'CONTACT_PHONE_REQUIRED':
      return 'Escribe un teléfono de contacto para que el taller pueda avisarte.'
    case 'BOOKING_INVALID_TRANSITION':
      return 'Esta cita cambió mientras la mirabas. Actualizamos su estado.'
    case 'BOOKING_NOT_FOUND':
      return 'No encontramos esta cita en tu cuenta.'
    case 'SEARCH_RATE_LIMITED':
      return 'Hiciste muchas consultas seguidas. Espera un minuto e inténtalo de nuevo.'
    default:
      return error.status === 429 ? 'Hiciste muchas solicitudes seguidas. Espera un momento e inténtalo de nuevo.' : error.message || fallback
  }
}
