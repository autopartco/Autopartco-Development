import type { PortalQuestion, QuestionPortalService } from '../question-types'
import type { Page } from '../types'
import { portalRequest, storePath } from './portal-http'

const id = (value: string) => encodeURIComponent(value)

/** `StoreQuestionsController`: `/stores/{storeId}/questions`. */
export function createHttpQuestionPortalService(): QuestionPortalService {
  return {
    list(storeId, query) {
      const params = new URLSearchParams({ page: String(query.page) })
      if (query.answered !== undefined) params.set('answered', String(query.answered))
      if (query.branchId) params.set('branchId', query.branchId)
      return portalRequest<Page<PortalQuestion>>(storePath(storeId, `/questions?${params}`))
    },
    async pendingCount(storeId) {
      const dto = await portalRequest<{ pending: number }>(storePath(storeId, '/questions/summary'))
      return dto.pending ?? 0
    },
    answer: (storeId, questionId, text) =>
      portalRequest<PortalQuestion>(storePath(storeId, `/questions/${id(questionId)}/answer`), { method: 'PUT', body: { text } }),
    async removeAnswer(storeId, questionId) {
      await portalRequest<void>(storePath(storeId, `/questions/${id(questionId)}/answer`), { method: 'DELETE' })
    },
  }
}
