import type { OpeningHours, StoreBranch } from '../api/types'

/** Mensaje por defecto si el almacén no personalizó el suyo (mismo texto que el del servidor). */
export const DEFAULT_WHATSAPP_MESSAGE = 'Hola, vi {producto} en Autopartco y quiero cotizarlo: {enlace}'

/** URL canónica de una ruta del marketplace: absoluta y sin `?ref=` (el origen de compartido no se hereda). */
export function canonicalUrl(path: string): string {
  const url = new URL(path, window.location.origin)
  url.searchParams.delete('ref')
  return url.toString()
}

/**
 * `https://wa.me/<dígitos>?text=<mensaje>`. `{producto}` es el repuesto, o el nombre de la tienda si el mensaje es
 * genérico; `{enlace}` la URL canónica. Sin número válido devuelve `undefined`.
 */
export function whatsappUrl(phone: string | undefined, template: string | undefined, subject: string, link: string): string | undefined {
  const digits = phone?.replace(/\D/g, '') ?? ''
  if (digits.length < 10) return undefined
  const message = (template?.trim() || DEFAULT_WHATSAPP_MESSAGE).replaceAll('{producto}', subject).replaceAll('{enlace}', link)
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`
}

export interface DirectionsLink {
  id: 'google' | 'waze' | 'apple'
  label: string
  href: string
}

/** Enlaces «Cómo llegar»: con coordenadas al punto exacto; sin ellas, a la dirección escrita. */
export function directionsLinks(branch: StoreBranch): DirectionsLink[] {
  if (branch.point) {
    const ll = `${branch.point.lat},${branch.point.lng}`
    return [
      { id: 'google', label: 'Google Maps', href: `https://www.google.com/maps/dir/?api=1&destination=${ll}` },
      { id: 'waze', label: 'Waze', href: `https://waze.com/ul?ll=${ll}&navigate=yes` },
      { id: 'apple', label: 'Apple Maps', href: `https://maps.apple.com/?daddr=${ll}` },
    ]
  }
  const address = [branch.address, branch.city, 'Colombia'].filter(Boolean).join(', ')
  if (!branch.address) return []
  const q = encodeURIComponent(address)
  return [
    { id: 'google', label: 'Google Maps', href: `https://www.google.com/maps/dir/?api=1&destination=${q}` },
    { id: 'waze', label: 'Waze', href: `https://waze.com/ul?q=${q}&navigate=yes` },
    { id: 'apple', label: 'Apple Maps', href: `https://maps.apple.com/?daddr=${q}` },
  ]
}

export const WEEK_DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'] as const
export type WeekDay = (typeof WEEK_DAYS)[number]

export const DAY_LABELS: Record<WeekDay, string> = {
  mon: 'Lunes',
  tue: 'Martes',
  wed: 'Miércoles',
  thu: 'Jueves',
  fri: 'Viernes',
  sat: 'Sábado',
  sun: 'Domingo',
}

/** Día de la semana en Bogotá (el horario de las sedes se publica en hora colombiana). */
export function todayInBogota(now = new Date()): WeekDay {
  const short = new Intl.DateTimeFormat('en-US', { weekday: 'short', timeZone: 'America/Bogota' }).format(now).toLowerCase()
  return (WEEK_DAYS.find((day) => day === short.slice(0, 3)) ?? 'mon') as WeekDay
}

/** «08:00» → «8:00 a. m.»; «18:30» → «6:30 p. m.». */
export function formatTime(value: string): string {
  const [hours = 0, minutes = 0] = value.split(':').map(Number)
  const suffix = hours >= 12 ? 'p. m.' : 'a. m.'
  const hour12 = hours % 12 === 0 ? 12 : hours % 12
  return `${hour12}:${String(minutes).padStart(2, '0')} ${suffix}`
}

/** «8:00 a. m. – 12:00 m. · …» o «Cerrado»; `undefined` si el día no se publicó. */
export function daySlots(hours: OpeningHours | undefined, day: WeekDay): string | undefined {
  const slots = hours?.[day]
  if (!slots) return undefined
  return slots.length === 0 ? 'Cerrado' : slots.map(([from, to]) => `${formatTime(from)} – ${formatTime(to)}`).join(' · ')
}

export function hasWeeklyHours(hours: OpeningHours | undefined): boolean {
  return !!hours && WEEK_DAYS.some((day) => hours[day] !== undefined)
}

export interface OpenSummary {
  state: 'open' | 'closed'
  /** Complemento: horario de hoy (una sola sede) o cuántas sedes están abiertas. */
  detail?: string
}

/** Estado conjunto de las sedes con horario; `undefined` si ninguna publicó uno. */
export function summarizeOpen(branches: StoreBranch[]): OpenSummary | undefined {
  const known = branches.filter((branch) => branch.openNow !== undefined)
  if (known.length === 0) return undefined
  const open = known.filter((branch) => branch.openNow)
  if (known.length === 1 && branches.length === 1) {
    return { state: open.length > 0 ? 'open' : 'closed', detail: daySlots(known[0]?.openingHours, todayInBogota()) }
  }
  return open.length > 0
    ? { state: 'open', detail: `${open.length} de ${branches.length} sedes` }
    : { state: 'closed', detail: 'Ninguna sede abierta' }
}
