import { NOTE_MAX, isValidPlate, normalizePhone } from './booking'

/** Datos que escribe la persona. `phone` es `undefined` hasta que lo edita (entonces vale el del perfil). */
export interface BookingDraft {
  makeId: string
  modelId: string
  year: string
  plate: string
  phone: string | undefined
  note: string
}

export const EMPTY_DRAFT: BookingDraft = { makeId: '', modelId: '', year: '', plate: '', phone: undefined, note: '' }

export type DraftErrors = Partial<Record<'vehicle' | 'year' | 'plate' | 'phone' | 'note', string>>

/** Validación local de formato; el servidor vuelve a validar. */
export function validateDraft(draft: BookingDraft, phone: string): DraftErrors {
  const errors: DraftErrors = {}
  const year = draft.year.trim()
  const plate = draft.plate.trim()
  const hasVehicle = draft.makeId && draft.modelId
  if ((draft.makeId || draft.modelId) && !hasVehicle) errors.vehicle = 'Elige la marca y el modelo, o deja el vehículo en blanco.'
  if (year && (!/^\d{4}$/.test(year) || Number(year) < 1950 || Number(year) > 2100)) errors.year = 'Escribe un año entre 1950 y 2100.'
  if (plate && !isValidPlate(plate)) errors.plate = 'La placa debe tener de 5 a 7 letras o números, sin guiones.'
  if ((year || plate) && !hasVehicle) errors.vehicle = 'Elige la marca y el modelo para guardar el año o la placa.'
  if (!normalizePhone(phone)) errors.phone = 'Escribe un celular de 10 dígitos (o con +57), por ejemplo 3001234567.'
  if (draft.note.length > NOTE_MAX) errors.note = `La nota admite hasta ${NOTE_MAX} caracteres.`
  return errors
}
