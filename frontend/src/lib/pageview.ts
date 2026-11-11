import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router'
import { API_BASE_URL, DATA_SOURCE } from './api'
import { getAnonymousSessionId } from './anonymous-session'

/**
 * Envía una visita (`POST /events/pageview`) en cada cambio de ruta del marketplace. Sin datos personales: id anónimo
 * de la pestaña, ruta y referente. No bloquea la navegación ni reintenta; con datos simulados no envía nada.
 */
export function usePageviews(): void {
  const { pathname } = useLocation()
  const first = useRef(true)

  useEffect(() => {
    if (DATA_SOURCE !== 'http') return
    const referrer = first.current ? document.referrer : ''
    first.current = false
    const body = JSON.stringify({ anonymousId: getAnonymousSessionId(), path: pathname, referrer: referrer || undefined })
    try {
      void fetch(`${API_BASE_URL}/events/pageview`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
        keepalive: true,
        credentials: 'include',
      }).catch(() => undefined)
    } catch {
      // La medición nunca debe romper la página.
    }
  }, [pathname])
}
