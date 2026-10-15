import { ApiError, apiRequest, type RequestOptions } from '@/lib/api'
import { getAccessToken } from '@/features/auth/api/http/http-auth-service'
import { setSession } from '@/features/auth/session-store'

/**
 * Petición autenticada del portal. Un 401 significa que la sesión ya no vale (token vencido o
 * revocado): se limpia la sesión local y la guarda de ruta (`RequireSession`) lleva al login
 * conservando el destino. Un 403 se deja pasar tal cual para que cada pantalla diga
 * "no tienes acceso" sin tocar la sesión.
 */
export async function portalRequest<T>(path: string, options: Omit<RequestOptions, 'token'> = {}): Promise<T> {
  try {
    return await apiRequest<T>(path, { ...options, token: await getAccessToken() })
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) setSession(undefined)
    throw error
  }
}

export const storePath = (storeId: string, suffix = '') => `/stores/${encodeURIComponent(storeId)}${suffix}`
