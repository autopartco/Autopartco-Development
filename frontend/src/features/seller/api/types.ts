import type { CustomerRegistration } from '@/features/auth/api/types'

/**
 * Solicitud de almacén. Contrato real en
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

/**
 * Solicitud con la cuenta ya iniciada: `POST /me/store-applications` (multipart, con token).
 * Parte `request` = `StoreApplicationRequest` { store, legalType, planId? }: igual que la
 * pública pero sin `account`.
 */
export type CurrentUserApplicationInput = Omit<SellerApplicationInput, 'account'>

/** `StoreApplicationCreatedResponse` { applicationId, storeId, status, planCode }. */
export interface CurrentUserApplicationResult {
  applicationId: string
  storeId: string
  status: StoreApplicationStatus
  planCode?: string
}

/** Lo que la UI usa de `storesuite.onboarding.StoreApplicationResponse` (`GET /me/store-applications`). */
export interface MyStoreApplication {
  applicationId: string
  storeId: string
  businessName: string
  status: StoreApplicationStatus
  reviewNotes?: string
  /** `LocalDateTime` sin zona (`AAAA-MM-DDTHH:mm:ss`). */
  createdAt: string
}

/** Estados que el backend considera "en curso" (`StoreApplications.requireNoOpenApplication`). */
export const OPEN_APPLICATION_STATUSES: readonly StoreApplicationStatus[] = ['PENDING_REVIEW', 'CONFIGURATION_PENDING', 'ACTIVE']

/** Datos de la configuración del almacén (`POST /me/store-applications/{id}/setup`, multipart). */
export interface SetupDetails {
  businessDescription?: string
  logo?: File
  banner?: File
}

export interface SetupResult {
  storeId: string
  status: StoreApplicationStatus
  trialEndsAt: string | null
}

export interface SellerApplicationService {
  readonly simulated: boolean
  /** Registro público: crea la cuenta y la solicitud (`POST /store-applications`). */
  submit(input: SellerApplicationInput): Promise<SellerApplicationResult>
  /** Cuenta de cliente con sesión iniciada (`POST /me/store-applications`). */
  applyAsCurrentUser(input: CurrentUserApplicationInput): Promise<CurrentUserApplicationResult>
  /** Solicitudes de la cuenta con sesión (`GET /me/store-applications`). */
  myApplications(): Promise<MyStoreApplication[]>
  /**
   * Configuración con la sesión iniciada (`POST /me/store-applications/{id}/setup`); el servidor
   * devuelve tokens nuevos por el cambio de rol.
   * Errores: 404 `SELLER_APPLICATION_NOT_FOUND`, 409 `SELLER_SETUP_NOT_PENDING` y `SELLER_EMAIL_NOT_VERIFIED`.
   */
  completeSetupAuthenticated(applicationId: string, input: SetupDetails): Promise<SetupResult>
  /**
   * Reenvía el correo de configuración (`POST /me/store-applications/{id}/setup-link`, 204).
   * 404 otra cuenta, 409 `SELLER_SETUP_NOT_PENDING`, 429 `SELLER_SETUP_LINK_COOLDOWN` (espera 5 min).
   */
  requestSetupLink(applicationId: string): Promise<void>
}
