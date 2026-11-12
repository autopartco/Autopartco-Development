/**
 * Preguntas sobre un producto (`ProductQuestionController`). Las respondidas las ve cualquiera; las pendientes,
 * solo su autor (`mine`). El servidor decide qué se muestra y qué se acepta.
 */

export interface QuestionAnswer {
  text: string
  answeredAt: string
}

export interface ProductQuestion {
  id: string
  question: string
  /** «Ana P.»: nombre + inicial del apellido. */
  author: string
  createdAt: string
  answer: QuestionAnswer | null
  /** La hizo quien consulta. */
  mine: boolean
}

export interface QuestionsPage {
  items: ProductQuestion[]
  page: number
  size: number
  total: number
  totalPages: number
}

export interface QuestionsQuery {
  page: number
  /** Búsqueda en el texto de las preguntas (máx. 80 caracteres). */
  q?: string
  size?: number
}

export interface QuestionsService {
  /** `GET /marketplace/products/{id}/questions?page&size&q`. 400 `QUESTION_QUERY_INVALID`. Con sesión incluye las propias pendientes. */
  list(productId: string, query: QuestionsQuery): Promise<QuestionsPage>
  /** `POST …/questions {question}`. 400 `QUESTION_INVALID`/`QUESTION_CONTACT_NOT_ALLOWED`, 429 `QUESTION_RATE_LIMITED`. */
  ask(productId: string, question: string): Promise<ProductQuestion>
  /** `DELETE …/questions/{qid}`. 404 `QUESTION_NOT_FOUND`, 409 `QUESTION_ALREADY_ANSWERED`. */
  remove(productId: string, questionId: string): Promise<void>
}
