import { reverseInColombia } from '../api/photon-geocoder'
import type { GeoPoint } from '../api/types'
import { setMapArea } from '../location-store'
import { roundCoord } from './geo'
import { isInColombia } from './map-config'

export type LocateResult = { ok: true; point: GeoPoint } | { ok: false; reason: 'unsupported' | 'denied' | 'outside' }

function getPosition(): Promise<GeoPoint> {
  return new Promise((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(
      (position) => resolve({ lat: position.coords.latitude, lng: position.coords.longitude }),
      reject,
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 300000 },
    )
  })
}

export function canLocate(): boolean {
  return typeof navigator !== 'undefined' && 'geolocation' in navigator
}

/**
 * Pide la ubicación al navegador (con el permiso de la persona) y la guarda como área exacta, en memoria:
 * nunca se persiste. Las coordenadas se redondean a ~110 m antes de usarse.
 */
export async function locateMe(): Promise<LocateResult> {
  if (!canLocate()) return { ok: false, reason: 'unsupported' }
  const raw = await getPosition().catch(() => null)
  if (!raw) return { ok: false, reason: 'denied' }
  const point = { lat: roundCoord(raw.lat), lng: roundCoord(raw.lng) }
  if (!isInColombia(point)) return { ok: false, reason: 'outside' }
  const place = await reverseInColombia(point).catch(() => ({ label: 'Tu ubicación' }))
  if (!place) return { ok: false, reason: 'outside' }
  const dLat = 0.45
  const dLng = 0.45 / Math.cos((point.lat * Math.PI) / 180)
  setMapArea({
    label: place.label,
    precision: 'exact',
    user: point,
    center: point,
    bounds: { south: point.lat - dLat, north: point.lat + dLat, west: point.lng - dLng, east: point.lng + dLng },
  })
  return { ok: true, point }
}
