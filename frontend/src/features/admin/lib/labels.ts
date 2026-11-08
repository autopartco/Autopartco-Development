import type { Tone } from '@/features/portal/lib/labels'
import type { LegalType, StoreApplicationStatus } from '@/features/seller/api/types'
import type { ApplicationDocumentType } from '../api/types'

export const LEGAL_TYPE_LABEL: Record<LegalType, string> = {
  NATURAL_PERSON: 'Persona natural',
  LEGAL_ENTITY: 'Persona jurídica',
}

export const DOCUMENT_TYPE_LABEL: Record<ApplicationDocumentType, string> = {
  IDENTITY_DOCUMENT: 'Cédula de quien registra',
  RUT: 'RUT',
  LEGAL_REPRESENTATIVE_ID: 'Cédula del representante legal',
  CERTIFICATE_OF_EXISTENCE: 'Certificado de existencia y representación legal',
}

/** "1,8 MB" / "268 KB" (formato colombiano). */
export function formatBytes(bytes: number): string {
  if (bytes >= 1_000_000) return `${(bytes / 1_000_000).toLocaleString('es-CO', { maximumFractionDigits: 1 })} MB`
  return `${Math.max(1, Math.round(bytes / 1000)).toLocaleString('es-CO')} KB`
}

/** `LocalDateTime` del servidor a milisegundos (se interpreta como hora local). */
export const timeOf = (createdAt: string): number => new Date(createdAt).getTime()

export const DAY_MS = 86_400_000
/** Inicio del día local. */
export function startOfDay(ms: number): number {
  const date = new Date(ms)
  date.setHours(0, 0, 0, 0)
  return date.getTime()
}

/** Estado de una solicitud en la consola: texto siempre visible, el tono solo acompaña. */
export const APPLICATION_STATUS: Record<StoreApplicationStatus, { label: string; tone: Tone; decision: string }> = {
  PENDING_REVIEW: { label: 'En revisión', tone: 'warning', decision: 'Pendiente' },
  CONFIGURATION_PENDING: { label: 'Aprobada, por configurar', tone: 'info', decision: 'Aprobada' },
  ACTIVE: { label: 'Activa', tone: 'success', decision: 'Aprobada' },
  REJECTED: { label: 'Rechazada', tone: 'danger', decision: 'Rechazada' },
}
