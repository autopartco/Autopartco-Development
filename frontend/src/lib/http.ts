/**
 * GET públicos con caché en memoria y revalidación por ETag.
 *
 * - Solo se cachean GET sin `Authorization` cuya respuesta trae `ETag` (el servidor decide qué es público).
 * - Se envía `If-None-Match`; ante un 304 se devuelve el cuerpo guardado.
 * - Las peticiones GET idénticas en curso se comparten.
 * - Al volver el foco a la ventana las entradas pasan a «viejas»: la siguiente lectura revalida con
 *   `If-None-Match`, sin refetch masivo.
 * - Nunca se cachea lo que lleva tokens de anuncio de un solo uso: `Cache-Control: no-store`,
 *   `/marketplace/home`, `/marketplace/ads` ni `/marketplace/search` con `sponsored` no vacío.
 */
const MAX_ENTRIES = 200
/** Ventana en la que una entrada se sirve sin ir a la red (coincide con `max-age=30` del servidor). */
const FRESH_MS = 30_000
const NEVER_CACHE_PATHS = ['/marketplace/home', '/marketplace/ads']

interface Entry {
  etag: string
  body: unknown
  headers: Headers
  storedAt: number
  stale: boolean
}

export interface HttpResult<T> {
  /** 200 o 304 resueltos desde la caché. */
  status: number
  headers: Headers
  data: T
}

const cache = new Map<string, Entry>()
const inflight = new Map<string, Promise<HttpResult<unknown>>>()

function remember(url: string, entry: Entry): void {
  cache.delete(url)
  cache.set(url, entry)
  if (cache.size > MAX_ENTRIES) {
    const oldest = cache.keys().next()
    if (!oldest.done) cache.delete(oldest.value)
  }
}

/** Vacía la caché (p. ej. al cambiar el área elegida, de la que puede depender la respuesta). */
export function clearHttpCache(): void {
  cache.clear()
}

if (typeof window !== 'undefined') {
  const markStale = () => {
    for (const entry of cache.values()) entry.stale = true
  }
  window.addEventListener('focus', markStale)
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'visible') markStale()
  })
}

function hasAdTokens(url: string, body: unknown): boolean {
  const path = url.split('?')[0] ?? url
  if (NEVER_CACHE_PATHS.some((blocked) => path.endsWith(blocked))) return true
  if (body && typeof body === 'object' && 'sponsored' in body) {
    const sponsored = (body as { sponsored?: unknown }).sponsored
    return Array.isArray(sponsored) && sponsored.length > 0
  }
  return false
}

function neverCached(url: string): boolean {
  const path = url.split('?')[0] ?? url
  return NEVER_CACHE_PATHS.some((blocked) => path.endsWith(blocked))
}

/** Rechaza con `AbortError` cuando `signal` se cancela, sin cancelar la petición compartida. */
function withSignal<T>(promise: Promise<T>, signal: AbortSignal | undefined): Promise<T> {
  if (!signal) return promise
  if (signal.aborted) return Promise.reject(signal.reason ?? new DOMException('Aborted', 'AbortError'))
  return new Promise<T>((resolve, reject) => {
    const onAbort = () => reject(signal.reason ?? new DOMException('Aborted', 'AbortError'))
    signal.addEventListener('abort', onAbort, { once: true })
    promise.then(
      (value) => {
        signal.removeEventListener('abort', onAbort)
        resolve(value)
      },
      (error: unknown) => {
        signal.removeEventListener('abort', onAbort)
        reject(error as Error)
      },
    )
  })
}

export interface CachedGetOptions {
  headers?: Record<string, string>
  signal?: AbortSignal
  credentials?: RequestCredentials
}

/**
 * Devuelve la respuesta cruda cuando no es cacheable (errores incluidos: `response.ok` false) o un
 * resultado ya leído. Los errores HTTP no se cachean y los gestiona quien llama.
 */
export async function cachedGet<T>(url: string, options: CachedGetOptions = {}): Promise<HttpResult<T> | Response> {
  const headers = { ...options.headers }
  const authorized = Object.keys(headers).some((name) => name.toLowerCase() === 'authorization')
  const cacheable = !authorized && !neverCached(url)

  if (!cacheable) {
    const response = await fetch(url, { headers, signal: options.signal, credentials: options.credentials })
    if (!response.ok) return response
    return { status: response.status, headers: response.headers, data: (await response.json()) as T }
  }

  const known = cache.get(url)
  if (known && !known.stale && Date.now() - known.storedAt < FRESH_MS) {
    remember(url, known)
    return { status: 200, headers: known.headers, data: known.body as T }
  }

  let shared = inflight.get(url)
  if (!shared) {
    const request = (async (): Promise<HttpResult<unknown>> => {
      const sent = { ...headers }
      if (known) sent['If-None-Match'] = known.etag
      const response = await fetch(url, { headers: sent, credentials: options.credentials })
      if (response.status === 304 && known) {
        known.stale = false
        known.storedAt = Date.now()
        remember(url, known)
        return { status: 304, headers: known.headers, data: known.body }
      }
      if (!response.ok) throw response
      const body: unknown = await response.json()
      const etag = response.headers.get('ETag')
      const noStore = /no-store/i.test(response.headers.get('Cache-Control') ?? '')
      if (etag && !noStore && !hasAdTokens(url, body)) {
        remember(url, { etag, body, headers: response.headers, storedAt: Date.now(), stale: false })
      } else {
        cache.delete(url)
      }
      return { status: response.status, headers: response.headers, data: body }
    })().finally(() => inflight.delete(url))
    inflight.set(url, request)
    shared = request
  }

  try {
    return (await withSignal(shared, options.signal)) as HttpResult<T>
  } catch (error) {
    // Cada quien lee su propia copia: el cuerpo de una `Response` solo se consume una vez.
    if (error instanceof Response) return error.clone()
    throw error
  }
}
