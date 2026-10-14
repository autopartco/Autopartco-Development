/**
 * Cliente HTTP mínimo para la API de Autopartco (cuentas y solicitudes de almacén).
 * La fuente de datos es la misma variable que usa el marketplace: con `mock`, las
 * pantallas usan servicios simulados y no llaman al servidor.
 */
export const DATA_SOURCE: 'mock' | 'http' = import.meta.env.VITE_MARKETPLACE_DATA_SOURCE === 'http' ? 'http' : 'mock'

/** Incluye el prefijo `ApiPaths.BASE` (`/api/v1`). Solo es una URL pública: no hay secretos en el cliente. */
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL ?? '/api/v1').replace(/\/+$/, '')

/**
 * Error de la API. El backend responde `{ error: { code, message, details } }`
 * (`shared/error/GlobalExceptionHandler`); en `VALIDATION_ERROR`, `details` es un
 * mapa campo -> mensaje (p. ej. `email` o, en multipart, `account.email`).
 */
export class ApiError extends Error {
  readonly status?: number
  readonly code?: string
  readonly fieldErrors: Readonly<Record<string, string>>

  constructor(
    message: string,
    options: { status?: number; code?: string; fieldErrors?: Record<string, string>; cause?: unknown } = {},
  ) {
    super(message, { cause: options.cause })
    this.name = 'ApiError'
    this.status = options.status
    this.code = options.code
    this.fieldErrors = options.fieldErrors ?? {}
  }
}

interface ErrorBody {
  error?: { code?: string; message?: string; details?: unknown }
}

function readFieldErrors(details: unknown): Record<string, string> {
  if (!details || typeof details !== 'object' || Array.isArray(details)) return {}
  return Object.fromEntries(
    Object.entries(details as Record<string, unknown>).filter((entry): entry is [string, string] => typeof entry[1] === 'string'),
  )
}

interface RequestOptions {
  method?: 'GET' | 'POST'
  /** Objeto (se envía como JSON) o `FormData` (multipart; el navegador pone el boundary). */
  body?: unknown
  /** Access token para rutas autenticadas. */
  token?: string
  signal?: AbortSignal
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' }
  let body: BodyInit | undefined
  if (options.body instanceof FormData) {
    body = options.body
  } else if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(options.body)
  }
  if (options.token) headers.Authorization = `Bearer ${options.token}`

  let response: Response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? (body ? 'POST' : 'GET'),
      headers,
      body,
      signal: options.signal,
    })
  } catch (cause) {
    throw new ApiError('No hay conexión con el servidor de Autopartco. Revisa tu internet e inténtalo de nuevo.', { cause })
  }

  if (!response.ok) {
    const payload = (await response.json().catch(() => null)) as ErrorBody | null
    throw new ApiError(payload?.error?.message ?? `El servidor respondió ${response.status}. Inténtalo de nuevo en unos minutos.`, {
      status: response.status,
      code: payload?.error?.code,
      fieldErrors: readFieldErrors(payload?.error?.details),
    })
  }
  if (response.status === 204) return undefined as T
  return (await response.json()) as T
}

/** Espera simulada para los servicios `mock` (deja ver los estados de carga). */
export function simulateLatency(ms = 650): Promise<void> {
  return new Promise((resolve) => window.setTimeout(resolve, ms))
}
