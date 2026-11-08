import type { Page } from '@/features/portal/api/types'
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

/** `StoreApplicationResponse.Reviewer`: cuenta ROOT que decidió la solicitud. */
export interface ApplicationReviewer {
  accountId: string
  name: string
  email: string
}

/** `operations.storeverification.StoreApplicationResponse`. */
export interface StoreApplication {
  applicationId: string
  storeId: string
  businessName: string
  nit: string
  businessEmail: string
  /** `null` en el listado general si la cuenta solicitante fue eliminada. */
  applicantEmail: string | null
  legalType: LegalType
  status: StoreApplicationStatus
  plan: ApplicationPlan | null
  selectedPlan: ApplicationPlan | null
  documents: ApplicationDocument[]
  reviewNotes: string | null
  /** `LocalDateTime` sin zona (`AAAA-MM-DDTHH:mm:ss`). */
  createdAt: string
  /** Fecha de la revisión (`LocalDateTime` sin zona); `null` si sigue pendiente o no quedó registrada. */
  reviewedAt: string | null
  /** `null` si sigue pendiente o la cuenta revisora ya no existe. */
  reviewer: ApplicationReviewer | null
}

/** `GET /admin/store-applications?status=&page=&size=` (página desde 1, `size` máx. 100). */
export interface ApplicationListQuery {
  /** Sin estado = todas. */
  status?: StoreApplicationStatus
  page: number
  size: number
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
  /**
   * `GET /admin/store-applications`: listado paginado (pendientes más antiguas primero; luego revisadas
   * por fecha de revisión descendente). 400 `INVALID_APPLICATION_STATUS` si el estado no existe.
   */
  listApplications(query: ApplicationListQuery): Promise<Page<StoreApplication>>
  /** `PATCH /admin/store-applications/{id}/review`. Devuelve la solicitud ya revisada. */
  review(applicationId: string, input: ReviewInput): Promise<StoreApplication>
  /** `GET /admin/store-applications/{id}/documents/{documentId}` (binario). */
  downloadDocument(applicationId: string, document: ApplicationDocument): Promise<Blob>
}
