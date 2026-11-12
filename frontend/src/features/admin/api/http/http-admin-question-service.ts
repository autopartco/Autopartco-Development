import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { Page } from '@/features/portal/api/types'
import type { AdminQuestion, AdminQuestionService } from '../question-types'

const id = (value: string) => encodeURIComponent(value)

/** `QuestionAdminController`: `/admin/questions`. */
export function createHttpAdminQuestionService(): AdminQuestionService {
  return {
    list(query) {
      const params = new URLSearchParams({ page: String(query.page), size: '20' })
      if (query.hidden !== undefined) params.set('hidden', String(query.hidden))
      return portalRequest<Page<AdminQuestion>>(`/admin/questions?${params}`)
    },
    hide: (questionId, reason) => portalRequest<AdminQuestion>(`/admin/questions/${id(questionId)}/hide`, { method: 'POST', body: { reason } }),
    unhide: (questionId) => portalRequest<AdminQuestion>(`/admin/questions/${id(questionId)}/unhide`, { method: 'POST' }),
  }
}
