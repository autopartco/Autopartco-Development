import { WEEK_DAYS, type WeekDay } from '@/features/marketplace/lib/store-links'

/** Franja de un día en el formulario: horas `HH:mm` tal como las entrega `<input type="time">`. */
export interface SlotDraft {
  from: string
  to: string
}

export interface DayDraft {
  open: boolean
  slots: SlotDraft[]
}

/** Horario de una sucursal en el formulario (objetos planos: `useDraftForm` los compara por estructura). */
export interface HoursDraft {
  days: Record<WeekDay, DayDraft>
  notes: string
}

export const MAX_SLOTS = 2
export const NOTES_MAX = 120
export const DEFAULT_SLOT: SlotDraft = { from: '08:00', to: '18:00' }

const TIME = /^([01]\d|2[0-3]):[0-5]\d$/

export function emptyHours(): HoursDraft {
  const days = {} as Record<WeekDay, DayDraft>
  for (const day of WEEK_DAYS) days[day] = { open: false, slots: [] }
  return { days, notes: '' }
}

/** Horario del servidor sin confiar en su forma: solo franjas `HH:mm` bien formadas, hasta 2 por día. */
export function hoursFromServer(raw: Record<string, unknown> | null | undefined): HoursDraft {
  const draft = emptyHours()
  if (!raw) return draft
  for (const day of WEEK_DAYS) {
    const value = raw[day]
    if (!Array.isArray(value)) continue
    const slots = value
      .flatMap((slot): SlotDraft[] =>
        Array.isArray(slot) && typeof slot[0] === 'string' && typeof slot[1] === 'string' ? [{ from: slot[0], to: slot[1] }] : [],
      )
      .slice(0, MAX_SLOTS)
    draft.days[day] = { open: slots.length > 0, slots }
  }
  if (typeof raw.notes === 'string') draft.notes = raw.notes
  return draft
}

export interface HoursErrors {
  days: Partial<Record<WeekDay, string>>
  notes?: string
}

/** Mismas reglas que `OpeningHours.validate` del servidor (que valida otra vez). */
export function validateHours(hours: HoursDraft): HoursErrors {
  const errors: HoursErrors = { days: {} }
  for (const day of WEEK_DAYS) {
    const { open, slots } = hours.days[day]
    if (!open) continue
    if (slots.length === 0) {
      errors.days[day] = 'Añade una franja de apertura y cierre, o desmarca «Abierto».'
      continue
    }
    let previousEnd = ''
    for (const [index, slot] of slots.entries()) {
      const label = slots.length > 1 ? `la franja ${index + 1}` : 'la franja'
      if (!TIME.test(slot.from) || !TIME.test(slot.to)) {
        errors.days[day] = `Escribe la hora de apertura y de cierre de ${label}.`
        break
      }
      if (slot.from >= slot.to) {
        errors.days[day] = `El cierre de ${label} debe ser posterior a la apertura.`
        break
      }
      if (previousEnd && slot.from < previousEnd) {
        errors.days[day] = 'La segunda franja debe empezar después de que termine la primera.'
        break
      }
      previousEnd = slot.to
    }
  }
  const notes = hours.notes.trim()
  if (notes.length > NOTES_MAX) errors.notes = `Las notas no pueden superar ${NOTES_MAX} caracteres.`
  else if (/[<>]/.test(notes)) errors.notes = 'Las notas no pueden contener < ni >.'
  return errors
}

export function firstHoursError(errors: HoursErrors): string | undefined {
  return WEEK_DAYS.map((day) => errors.days[day]).find(Boolean) ?? errors.notes
}

/**
 * Cuerpo `openingHours` para el servidor. Sin días abiertos no se envían días (la sede queda sin horario semanal);
 * `undefined` si tampoco hay notas.
 */
export function hoursToServer(hours: HoursDraft): Record<string, unknown> | undefined {
  const result: Record<string, unknown> = {}
  if (WEEK_DAYS.some((day) => hours.days[day].open)) {
    for (const day of WEEK_DAYS) {
      const { open, slots } = hours.days[day]
      result[day] = open ? slots.map((slot) => [slot.from, slot.to]) : []
    }
  }
  const notes = hours.notes.trim()
  if (notes) result.notes = notes
  return Object.keys(result).length > 0 ? result : undefined
}

/** Copia las franjas del lunes a martes–viernes. */
export function copyMondayToWeekdays(hours: HoursDraft): HoursDraft {
  const monday = hours.days.mon
  const days = { ...hours.days }
  for (const day of ['tue', 'wed', 'thu', 'fri'] as const) days[day] = { open: monday.open, slots: monday.slots.map((slot) => ({ ...slot })) }
  return { ...hours, days }
}

export const hoursDayId = (prefix: string, day: WeekDay) => `${prefix}-${day}`
export const hoursNotesId = (prefix: string) => `${prefix}-notas`
