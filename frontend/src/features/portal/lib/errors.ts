import { ApiError } from '@/lib/api'

export type ErrorKind = 'forbidden' | 'not-found' | 'network' | 'server' | 'unauthorized' | 'other'

export interface ErrorView {
  kind: ErrorKind
  title: string
  description: string
  /** `true` si reintentar tiene sentido (red o servidor). */
  retryable: boolean
}

/**
 * Traduce un error de la API a un mensaje accionable. Nunca devuelve un "algo salió mal"
 * sin causa: distingue falta de permiso, sesión vencida, caída de red y error del servidor.
 */
export function describeError(error: unknown, what = 'esta información'): ErrorView {
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return {
        kind: 'forbidden',
        title: 'No tienes acceso a este almacén',
        description:
          'Tu cuenta no tiene permiso para ver esto. Pídele al propietario del almacén que revise tus permisos o cambia de almacén desde el selector.',
        retryable: false,
      }
    }
    if (error.status === 401) {
      return {
        kind: 'unauthorized',
        title: 'Tu sesión terminó',
        description: 'Vuelve a ingresar para continuar donde ibas.',
        retryable: false,
      }
    }
    if (error.status === 404) {
      return {
        kind: 'not-found',
        title: 'No encontramos lo que buscas',
        description: error.message || 'Puede que se haya eliminado o que el enlace esté incompleto.',
        retryable: false,
      }
    }
    if (error.status === undefined) {
      return {
        kind: 'network',
        title: 'No hay conexión con Autopartco',
        description: `No pudimos cargar ${what}. Revisa tu internet e inténtalo de nuevo.`,
        retryable: true,
      }
    }
    if (error.status >= 500) {
      return {
        kind: 'server',
        title: 'Autopartco tuvo un problema',
        description: `No pudimos cargar ${what} por un fallo de nuestro lado. Tu información está segura; inténtalo de nuevo en unos minutos.`,
        retryable: true,
      }
    }
    return { kind: 'other', title: `No pudimos cargar ${what}`, description: error.message, retryable: true }
  }
  return {
    kind: 'other',
    title: `No pudimos cargar ${what}`,
    description: 'Ocurrió un error inesperado. Recarga la página; si sigue igual, escríbenos con la hora en que pasó.',
    retryable: true,
  }
}

/** Mensaje para fallos de acciones (guardar, borrar, cambiar estado). */
export function actionErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiError) {
    if (error.status === 403) return 'Tu rol no tiene permiso para hacer esta acción.'
    if (error.status === undefined) return 'No hay conexión con Autopartco. Revisa tu internet e inténtalo de nuevo.'
    if (error.status >= 500) return 'Autopartco tuvo un problema y no se guardó nada. Inténtalo de nuevo en unos minutos.'
    return error.message || fallback
  }
  return fallback
}

/** Códigos 409 que significan "tu plan no te deja": abren el diálogo de mejora. */
export const PLAN_ERROR_CODES = [
  'SELLER_PLAN_LIMIT_REACHED',
  'SELLER_PLAN_FEATURE_UNAVAILABLE',
  'SELLER_SUBSCRIPTION_EXPIRED',
] as const

export function isPlanError(error: unknown): error is ApiError {
  return error instanceof ApiError && error.status === 409 && PLAN_ERROR_CODES.some((code) => code === error.code)
}
