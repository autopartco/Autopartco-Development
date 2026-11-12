import type { Page } from './types'

/**
 * Preguntas sobre los productos del almacén en el portal (`StoreQuestionsController`). El servidor limita el
 * alcance: dueño y equipo con permisos, o colaboradores de una sucursal con el producto. Sin alcance responde 403.
 */

export interface PortalQuestionAnswer {
  text: string
  answeredAt: string
}

export interface PortalQuestion {
  id: string
  question: string
  /** «Ana P.»: nombre + inicial del apellido. */
  author: string
  createdAt: string
  answer: PortalQuestionAnswer | null
  /** La ocultó Autopartco: no se muestra al público. El motivo no se expone. */
  hidden: boolean
  productId: string
  productName: string
}

export interface PortalQuestionQuery {
  /** `false` = sin responder, `true` = respondidas, ausente = todas. */
  answered?: boolean
  branchId?: string
  page: number
}

export interface QuestionPortalService {
  /** `GET /stores/{id}/questions?answered&branchId&page`. */
  list(storeId: string, query: PortalQuestionQuery): Promise<Page<PortalQuestion>>
  /** `GET /stores/{id}/questions/summary`: preguntas visibles sin responder en el alcance de quien consulta. */
  pendingCount(storeId: string): Promise<number>
  /** `PUT /stores/{id}/questions/{qid}/answer {text}` (máx. 1000, sin datos de contacto). 404 `QUESTION_NOT_FOUND`. */
  answer(storeId: string, questionId: string, text: string): Promise<PortalQuestion>
  /** `DELETE /stores/{id}/questions/{qid}/answer`. */
  removeAnswer(storeId: string, questionId: string): Promise<void>
}
