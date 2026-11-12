import { apiRequest } from '@/lib/api'
import { getAuthService } from '@/features/auth/api/auth-service'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import { requireAccessToken } from '@/features/checkout/api/http/http-checkout-service'
import type { PageResponseDto } from '@/features/marketplace/api/dto'
import type { ProductQuestion, QuestionsService } from '../types'

const base = (productId: string) => `/marketplace/products/${encodeURIComponent(productId)}/questions`

/** Consulta pública: con sesión se envía el token para ver también las propias pendientes. */
async function optionalToken(): Promise<string | undefined> {
  try {
    await getAuthService()
    return await getAccessToken()
  } catch {
    return undefined
  }
}

/** `ProductQuestionController`: `/marketplace/products/{productId}/questions`. */
export function createHttpQuestionsService(): QuestionsService {
  return {
    async list(productId, query) {
      const params = new URLSearchParams({ page: String(query.page), size: String(query.size ?? 10) })
      const text = query.q?.trim()
      if (text) params.set('q', text)
      return apiRequest<PageResponseDto<ProductQuestion>>(`${base(productId)}?${params}`, { token: await optionalToken() })
    },
    async ask(productId, question) {
      return apiRequest<ProductQuestion>(base(productId), { method: 'POST', body: { question }, token: await requireAccessToken() })
    },
    async remove(productId, questionId) {
      await apiRequest<void>(`${base(productId)}/${encodeURIComponent(questionId)}`, { method: 'DELETE', token: await requireAccessToken() })
    },
  }
}
