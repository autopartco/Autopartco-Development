/**
 * Solicitud de cotización del plan Empresarial.
 *
 * TODO(backend): hoy NO se guarda ni se envía a ningún servidor; solo se arma un correo (`mailto:`) o
 * un texto para copiar. Contrato futuro sugerido: `POST /api/v1/stores/{id}/quote-requests` con
 * `QuoteRequestValues` (contacto, tamaño, ERP, necesidades, mensaje) y respuesta `201 { id, createdAt }`.
 */

export const ERP_OPTIONS = [
  { value: 'NONE', label: 'Ninguno' },
  { value: 'ALEGRA', label: 'Alegra' },
  { value: 'SIIGO', label: 'Siigo' },
  { value: 'OTHER', label: 'Otro' },
] as const
export type ErpValue = (typeof ERP_OPTIONS)[number]['value']

export const NEED_OPTIONS = [
  { value: 'CATALOG_IMPORT', label: 'Importar catálogo' },
  { value: 'INVENTORY_SYNC', label: 'Sincronizar inventario' },
  { value: 'E_INVOICING', label: 'Facturación electrónica' },
  { value: 'API_WEBHOOKS', label: 'API y webhooks' },
  { value: 'CUSTOM_DOMAIN', label: 'Dominio propio' },
  { value: 'MIGRATION_SUPPORT', label: 'Acompañamiento de migración' },
] as const
export type NeedValue = (typeof NEED_OPTIONS)[number]['value']

export const MESSAGE_MAX = 1000
const NUMBER_MAX = 1_000_000

export interface QuoteRequestValues {
  contactName: string
  email: string
  phone: string
  branches: string
  products: string
  users: string
  erp: ErpValue
  needs: NeedValue[]
  message: string
}

export interface QuoteContext {
  storeName: string
  storeId: string
  planName: string
  usage: { label: string; used: number; limit: number | null }[]
}

export type QuoteErrors = Partial<Record<'contactName' | 'email' | 'phone' | 'branches' | 'products' | 'users' | 'message', string>>

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/

function checkCount(value: string, what: string): string | undefined {
  const text = value.trim()
  if (!text) return `Indica aproximadamente cuántos ${what} manejas.`
  if (!/^\d+$/.test(text)) return 'Escribe un número entero, sin puntos ni letras.'
  if (Number(text) > NUMBER_MAX) return `Escribe un número menor o igual a ${NUMBER_MAX.toLocaleString('es-CO')}.`
  return undefined
}

/** Teléfono colombiano flexible: celular (3xx) o fijo, con o sin +57, espacios, guiones o paréntesis. */
export function isValidPhone(value: string): boolean {
  if (!/^[\d\s()+-]+$/.test(value)) return false
  let digits = value.replace(/\D/g, '')
  if (digits.startsWith('57') && digits.length > 10) digits = digits.slice(2)
  return digits.length === 10 || digits.length === 7 || digits.length === 8
}

export function validateQuote(values: QuoteRequestValues): QuoteErrors {
  const errors: QuoteErrors = {}
  if (values.contactName.trim().length < 2) errors.contactName = 'Escribe el nombre de la persona de contacto.'
  if (!values.email.trim()) errors.email = 'Escribe un correo para enviarte la cotización.'
  else if (!EMAIL_PATTERN.test(values.email.trim())) errors.email = 'El correo debe tener un formato válido, por ejemplo nombre@tualmacen.co.'
  if (values.phone.trim() && !isValidPhone(values.phone.trim())) errors.phone = 'Revisa el teléfono, por ejemplo 300 123 4567 o +57 300 123 4567.'
  errors.branches = checkCount(values.branches, 'sedes')
  errors.products = checkCount(values.products, 'productos')
  errors.users = checkCount(values.users, 'usuarios')
  if (values.message.length > MESSAGE_MAX) errors.message = `El mensaje admite hasta ${MESSAGE_MAX} caracteres.`
  for (const key of Object.keys(errors) as (keyof QuoteErrors)[]) if (!errors[key]) delete errors[key]
  return errors
}

export function quoteSubject(storeName: string): string {
  return `Cotización plan Empresarial · ${storeName}`
}

const MAILTO_MAX_LENGTH = 1800

function body(values: QuoteRequestValues, context: QuoteContext, message: string): string {
  const erp = ERP_OPTIONS.find((option) => option.value === values.erp)?.label ?? values.erp
  const needs = NEED_OPTIONS.filter((option) => values.needs.includes(option.value)).map((option) => option.label)
  const usage = context.usage.map((item) => `${item.label}: ${item.used} de ${item.limit ?? 'sin límite'}`)
  return [
    'Solicitud de cotización · Plan Empresarial',
    '',
    'CONTACTO',
    `Nombre: ${values.contactName.trim()}`,
    `Correo: ${values.email.trim()}`,
    `Teléfono: ${values.phone.trim() || 'No indicado'}`,
    '',
    'TAMAÑO APROXIMADO',
    `Sedes: ${values.branches.trim()}`,
    `Productos: ${values.products.trim()}`,
    `Usuarios: ${values.users.trim()}`,
    '',
    'NECESIDADES',
    `ERP actual: ${erp}`,
    `Necesita: ${needs.length ? needs.join(', ') : 'Sin especificar'}`,
    `Mensaje: ${message.trim() || 'Sin mensaje'}`,
    '',
    'ALMACÉN',
    `Nombre: ${context.storeName}`,
    `Id: ${context.storeId}`,
    `Plan actual: ${context.planName}`,
    ...usage.map((line) => `Uso actual · ${line}`),
  ].join('\n')
}

/** Resumen en texto plano (para copiar o como cuerpo del correo). */
export function buildQuoteText(values: QuoteRequestValues, context: QuoteContext): string {
  return body(values, context, values.message)
}

/** `mailto:` con asunto y cuerpo codificados; recorta el mensaje libre si la URL queda demasiado larga. */
export function buildQuoteMailto(email: string, values: QuoteRequestValues, context: QuoteContext): { href: string; truncated: boolean } {
  const subject = encodeURIComponent(quoteSubject(context.storeName))
  const make = (message: string) => `mailto:${email}?subject=${subject}&body=${encodeURIComponent(body(values, context, message))}`
  let message = values.message
  let href = make(message)
  let truncated = false
  while (href.length > MAILTO_MAX_LENGTH && message.length > 0) {
    truncated = true
    const excess = href.length - MAILTO_MAX_LENGTH
    message = message.slice(0, Math.max(0, message.length - Math.max(20, Math.ceil(excess / 3))))
    href = make(message.length < values.message.length ? `${message.trimEnd()}… (mensaje recortado; lo completo te lo enviamos aparte)` : message)
  }
  return { href, truncated }
}
