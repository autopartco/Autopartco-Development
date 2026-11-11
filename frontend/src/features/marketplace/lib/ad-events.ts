import { API_BASE_URL, DATA_SOURCE } from '@/lib/api'
import { getAnonymousSessionId } from '@/lib/anonymous-session'

/**
 * Eventos de anuncios (`POST /events/ads`). Sin datos personales: solo el token firmado que entregó el
 * servidor y el id anónimo del navegador (el mismo de `/events/visibility`). Nunca bloquea ni reintenta;
 * con datos simulados no envía nada. El servidor ignora tokens alterados y aplica el tope de frecuencia.
 */
const BATCH_DELAY_MS = 400
const MAX_BATCH = 24

/** Tokens ya enviados o en cola: una impresión por token. */
const seen = new Set<string>()
let queue: string[] = []
let timer: number | undefined

function post(body: Record<string, unknown>): void {
  if (DATA_SOURCE !== 'http') return
  try {
    void fetch(`${API_BASE_URL}/events/ads`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ...body, anonymousId: getAnonymousSessionId() }),
      keepalive: true,
      credentials: 'omit',
    }).catch(() => undefined)
  } catch {
    // La medición nunca debe romper la página.
  }
}

function flush(): void {
  timer = undefined
  const batch = queue
  queue = []
  for (let start = 0; start < batch.length; start += MAX_BATCH) {
    post({ tokens: batch.slice(start, start + MAX_BATCH), kind: 'IMPRESSION' })
  }
}

/** Registra la impresión del anuncio, una sola vez por token (los lotes salen juntos). */
export function trackAdImpression(token: string): void {
  if (seen.has(token)) return
  seen.add(token)
  queue.push(token)
  timer ??= window.setTimeout(flush, BATCH_DELAY_MS)
}

export function trackAdClick(token: string): void {
  post({ token, kind: 'CLICK' })
}
