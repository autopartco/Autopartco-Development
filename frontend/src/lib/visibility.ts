import { API_BASE_URL, DATA_SOURCE } from './api'
import { getAnonymousSessionId } from './anonymous-session'

/**
 * Métricas de visibilidad del almacén (`POST /events/visibility`): visitas al perfil, a una publicación y
 * compartidos. Sin datos personales: id anónimo de la pestaña, códigos de origen y referente. Nunca bloquea ni
 * reintenta; con datos simulados no envía nada.
 *
 * El origen solo se informa en la página de llegada (la URL con la que se abrió la pestaña): `?ref=`,
 * `utm_source` y `document.referrer`. En la navegación interna `document.referrer` no cambia (SPA), así que se envía
 * el propio dominio y el servidor la clasifica como `INTERNAL`.
 */
export type VisibilityKind = 'PROFILE_VIEW' | 'PRODUCT_VIEW' | 'SHARE'

/** Códigos de `?ref=` que entiende el servidor (`TrafficSource.REF_CODES`). */
export type ShareChannel = 'wa' | 'fb' | 'ig' | 'tt' | 'x' | 'tg' | 'link'

export const REF_PARAM = 'ref'

let landingConsumed = false
let lastView = { key: '', at: 0 }

function landingUrl(): string | undefined {
  try {
    const [entry] = performance.getEntriesByType('navigation') as PerformanceNavigationTiming[]
    return entry?.name
  } catch {
    return undefined
  }
}

function withoutHash(url: string): string {
  return url.split('#', 1)[0] ?? url
}

/** `true` una sola vez: en la primera vista medida si la ruta actual es la de llegada. */
function takeLanding(): boolean {
  if (landingConsumed) return false
  landingConsumed = true
  const landing = landingUrl()
  return landing !== undefined && withoutHash(landing) === withoutHash(window.location.href)
}

/** Quita `?ref=` de la barra de direcciones para que, si el visitante vuelve a compartir, no herede el origen. */
function dropRefParam(): void {
  const url = new URL(window.location.href)
  if (!url.searchParams.has(REF_PARAM)) return
  url.searchParams.delete(REF_PARAM)
  window.history.replaceState(window.history.state, '', `${url.pathname}${url.search}${url.hash}`)
}

function send(body: Record<string, unknown>): void {
  if (DATA_SOURCE !== 'http') return
  try {
    void fetch(`${API_BASE_URL}/events/visibility`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      keepalive: true,
      credentials: 'include',
    }).catch(() => undefined)
  } catch {
    // La medición nunca debe romper la página.
  }
}

/** Visita al perfil o a una publicación. Llamar una vez por vista (en un efecto). */
export function trackView(kind: Exclude<VisibilityKind, 'SHARE'>, storeId: string, productId?: string): void {
  // Un mismo render puede ejecutar el efecto dos veces (StrictMode): la misma vista en menos de 2 s cuenta una vez.
  const key = `${kind}:${storeId}:${productId ?? ''}:${window.location.pathname}`
  const now = Date.now()
  if (key === lastView.key && now - lastView.at < 2000) return
  lastView = { key, at: now }
  const landing = takeLanding()
  const params = new URLSearchParams(window.location.search)
  send({
    anonymousId: getAnonymousSessionId(),
    kind,
    storeId,
    productId,
    ref: landing ? (params.get(REF_PARAM) ?? undefined) : undefined,
    utmSource: landing ? (params.get('utm_source') ?? undefined) : undefined,
    referrer: landing ? document.referrer || undefined : window.location.origin,
  })
  dropRefParam()
}

export function trackShare(channel: ShareChannel, storeId: string, productId?: string): void {
  send({ anonymousId: getAnonymousSessionId(), kind: 'SHARE', storeId, productId, channel })
}

/** Enlace absoluto a una ruta del marketplace con el código del canal. */
export function shareUrl(path: string, channel: ShareChannel): string {
  const url = new URL(path, window.location.origin)
  url.searchParams.set(REF_PARAM, channel)
  return url.toString()
}
