import { API_BASE_URL, ApiError } from '@/lib/api'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import { setSession } from '@/features/auth/session-store'
import { portalRequest } from '@/features/portal/api/http/portal-http'
import type { AdminApplicationService, StoreApplication } from '../types'

const BASE = '/admin/store-applications'

/** `StoreVerificationController`. Un 401 limpia la sesión (igual que el portal); un 403 se muestra como "sin acceso". */
export function createHttpAdminApplicationService(): AdminApplicationService {
  return {
    simulated: false,
    pending: () => portalRequest<StoreApplication[]>(`${BASE}/pending`),
    review: (applicationId, input) =>
      portalRequest<StoreApplication>(`${BASE}/${encodeURIComponent(applicationId)}/review`, { method: 'PATCH', body: input }),
    async downloadDocument(applicationId, document) {
      const token = await getAccessToken()
      let response: Response
      try {
        response = await fetch(`${API_BASE_URL}${BASE}/${encodeURIComponent(applicationId)}/documents/${encodeURIComponent(document.id)}`, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
      } catch (cause) {
        throw new ApiError('No hay conexión con el servidor de Autopartco. Revisa tu internet e inténtalo de nuevo.', { cause })
      }
      if (!response.ok) {
        if (response.status === 401) setSession(undefined)
        throw new ApiError(`No pudimos descargar el documento (el servidor respondió ${response.status}).`, { status: response.status })
      }
      return response.blob()
    },
  }
}
