import type { Page } from '@/features/portal/api/types'

/** Moderación de preguntas de producto (`QuestionAdminController`, solo ROOT; cada acción queda en auditoría). */

export interface AdminQuestion {
  id: string
  question: string
  /** «Ana P.»: nombre + inicial del apellido (nunca el correo). */
  author: string
  createdAt: string
  answer: { text: string; answeredAt: string } | null
  storeId: string
  storeName: string
  productId: string
  productName: string
  hidden: boolean
  hiddenAt: string | null
  hiddenReason: string | null
}

export interface AdminQuestionQuery {
  /** `undefined` = visibles y ocultas. */
  hidden?: boolean
  page: number
}

export interface AdminQuestionService {
  /** `GET /admin/questions?hidden&page&size`. */
  list(query: AdminQuestionQuery): Promise<Page<AdminQuestion>>
  /** `POST /admin/questions/{id}/hide {reason}`. Motivo de 3 a 200 caracteres. */
  hide(questionId: string, reason: string): Promise<AdminQuestion>
  /** `POST /admin/questions/{id}/unhide`. */
  unhide(questionId: string): Promise<AdminQuestion>
}
