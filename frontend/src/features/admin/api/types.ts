import type { LegalType, StoreApplicationStatus } from '@/features/seller/api/types'

/**
 * Contrato de `backend/.../operations/storeverification/StoreVerificationController`
 * (`/api/v1/admin/store-applications`, solo `ROLE_ROOT`). El frontend solo muestra lo que
 * devuelve el servidor: la autorización y el estado final de la solicitud son del backend.
 */

/** `core.organization.ApplicationDocumentType`. */
export type ApplicationDocumentType = 'IDENTITY_DOCUMENT' | 'RUT' | 'LEGAL_REPRESENTATIVE_ID' | 'CERTIFICATE_OF_EXISTENCE'

/** `StoreApplicationResponse.Document`. */
export interface ApplicationDocument {
  id: string
  type: ApplicationDocumentType
  filename: string
  contentType: string
  sizeBytes: number
}

/** Lo que la UI usa de `core.billing.PlanResponse`. */
export interface ApplicationPlan {
  code: string
  name: string
}

/** `operations.storeverification.StoreApplicationResponse`. */
export interface StoreApplication {
  applicationId: string
  storeId: string
  businessName: string
  nit: string
  businessEmail: string
  applicantEmail: string
  legalType: LegalType
  status: StoreApplicationStatus
  plan: ApplicationPlan | null
  selectedPlan: ApplicationPlan | null
  documents: ApplicationDocument[]
  reviewNotes: string | null
  /** `LocalDateTime` sin zona (`AAAA-MM-DDTHH:mm:ss`). */
  createdAt: string
}

/** `core.organization.StoreApplicationReviewRequest` (`notes` máx. 2000 caracteres). */
export interface ReviewInput {
  approved: boolean
  notes?: string
}

export const REVIEW_NOTES_MAX = 2000

export interface AdminApplicationService {
  readonly simulated: boolean
  /** `GET /admin/store-applications/pending`. */
  pending(): Promise<StoreApplication[]>
  /** `PATCH /admin/store-applications/{id}/review`. Devuelve la solicitud ya revisada. */
  review(applicationId: string, input: ReviewInput): Promise<StoreApplication>
  /** `GET /admin/store-applications/{id}/documents/{documentId}` (binario). */
  downloadDocument(applicationId: string, document: ApplicationDocument): Promise<Blob>
}
