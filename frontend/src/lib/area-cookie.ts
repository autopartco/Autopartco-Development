/**
 * Cookie `ap_area`: área elegida para que el servidor use una ubicación por defecto en `/search` y `/home`.
 * Formato `lat,lng,cityCode` (lat/lng a 0,01°, ~1 km; `cityCode` vacío si no se conoce). Dura 30 días,
 * `SameSite=Lax`, `Secure` en https, `path=/` y es legible por JS. Es una cookie funcional, sin datos de identidad.
 * El punto exacto de geolocalización nunca se escribe aquí: solo el redondeado.
 *
 * El valor se escribe codificado (`4.60%2C-74.10%2C`): la coma no es válida en una cookie según RFC 6265 y
 * Tomcat descarta las cookies con comas sin codificar. El servidor debe decodificarlo.
 */
export const AREA_COOKIE = 'ap_area'
const MAX_AGE_SECONDS = 30 * 24 * 60 * 60

export interface AreaCookie {
  lat: number
  lng: number
  cityCode: string
}

/** Redondeo a 0,01° para no persistir más precisión de la necesaria. */
export const roundAreaCoord = (value: number): number => Math.round(value * 100) / 100

function secureAttribute(): string {
  return typeof location !== 'undefined' && location.protocol === 'https:' ? '; Secure' : ''
}

export function readAreaCookie(): AreaCookie | undefined {
  if (typeof document === 'undefined') return undefined
  try {
    const entry = document.cookie.split('; ').find((part) => part.startsWith(`${AREA_COOKIE}=`))
    if (!entry) return undefined
    const [lat, lng, cityCode = ''] = decodeURIComponent(entry.slice(AREA_COOKIE.length + 1)).split(',')
    const parsed = { lat: Number(lat), lng: Number(lng) }
    if (!lat || !lng || !Number.isFinite(parsed.lat) || !Number.isFinite(parsed.lng)) return undefined
    return { ...parsed, cityCode: cityCode.trim() }
  } catch {
    return undefined
  }
}

export function writeAreaCookie(area: { lat: number; lng: number; cityCode?: string }): void {
  if (typeof document === 'undefined') return
  const value = [roundAreaCoord(area.lat).toFixed(2), roundAreaCoord(area.lng).toFixed(2), area.cityCode ?? ''].join(',')
  document.cookie = `${AREA_COOKIE}=${encodeURIComponent(value)}; Max-Age=${MAX_AGE_SECONDS}; Path=/; SameSite=Lax${secureAttribute()}`
}

export function clearAreaCookie(): void {
  if (typeof document === 'undefined') return
  document.cookie = `${AREA_COOKIE}=; Max-Age=0; Path=/; SameSite=Lax${secureAttribute()}`
}
