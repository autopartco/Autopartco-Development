import type { CustomerRegistration } from '@/features/auth/api/types'

/**
 * Solicitud pública de almacén. Contrato real en
 * `backend/.../storesuite/onboarding/StoreApplicationController` (`POST /store-applications`,
 * multipart): parte `request` = `SellerSignupRequest` { account: RegisterRequest,
 * store: StoreRegistrationRequest, legalType, planId? } y una parte por documento.
 */

/** `core.organization.LegalType`. */
export type LegalType = 'NATURAL_PERSON' | 'LEGAL_ENTITY'

/** Nombres de las partes multipart de documentos (`@RequestPart`). */
export type SellerDocumentKey = 'identityDocument' | 'rut' | 'legalRepresentativeId' | 'certificateOfExistence'

/** Documentos que exige `SubmitStoreApplication.validateDocuments`, ni más ni menos. */
export const REQUIRED_DOCUMENTS: Record<LegalType, readonly SellerDocumentKey[]> = {
  NATURAL_PERSON: ['identityDocument', 'rut'],
  LEGAL_ENTITY: ['rut', 'legalRepresentativeId', 'certificateOfExistence'],
}

export const DOCUMENT_LABELS: Record<SellerDocumentKey, string> = {
  identityDocument: 'Cédula de quien registra',
  rut: 'RUT',
  legalRepresentativeId: 'Cédula del representante legal',
  certificateOfExistence: 'Certificado de existencia y representación legal',
}

/** `StoreFileStorage`: PDF, PNG o JPEG (lo comprueba por contenido) y máximo 10 MB por archivo. */
export const DOCUMENT_ACCEPT = 'application/pdf,image/png,image/jpeg,.pdf,.png,.jpg,.jpeg'
export const DOCUMENT_MAX_BYTES = 10 * 1024 * 1024
/** `spring.servlet.multipart.max-request-size`. */
export const REQUEST_MAX_BYTES = 35 * 1024 * 1024

/** `StoreRegistrationRequest`. */
export interface StoreRegistration {
  businessName: string
  nit: string
  businessEmail: string
}

export interface SellerApplicationInput {
  account: CustomerRegistration
  store: StoreRegistration
  legalType: LegalType
  documents: Partial<Record<SellerDocumentKey, File>>
  // Sin `planId`: el backend asigna la prueba gratuita (`PlanService.requireSelectable(null)`).
  // TODO(flujo): elegir plan (y pagar con Wompi) va después de la aprobación; no se implementa aquí.
}

/** `core.organization.StoreApplicationStatus`. */
export type StoreApplicationStatus = 'PENDING_REVIEW' | 'CONFIGURATION_PENDING' | 'REJECTED' | 'ACTIVE'

/** Lo que la UI usa de `SellerSignupResponse`. */
export interface SellerApplicationResult {
  email: string
  businessName: string
  verificationEmailSent: boolean
  status: StoreApplicationStatus
  planCode?: string
}

export interface SellerApplicationService {
  readonly simulated: boolean
  submit(input: SellerApplicationInput): Promise<SellerApplicationResult>
}
