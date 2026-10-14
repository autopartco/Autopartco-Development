import type { GeoPoint } from '../api/types'

const EARTH_RADIUS_KM = 6371
const RAD = Math.PI / 180

/** Distancia en línea recta (haversine), en km. */
export function haversineKm(a: GeoPoint, b: GeoPoint): number {
  const dLat = (b.lat - a.lat) * RAD
  const dLng = (b.lng - a.lng) * RAD
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * RAD) * Math.cos(b.lat * RAD) * Math.sin(dLng / 2) ** 2
  return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(h))
}

/** Desplazamiento en km (este, norte) de `point` respecto a `origin`. Suficiente a escala de ciudad. */
export function offsetKm(origin: GeoPoint, point: GeoPoint): { east: number; north: number } {
  return {
    east: (point.lng - origin.lng) * 111.32 * Math.cos(origin.lat * RAD),
    north: (point.lat - origin.lat) * 110.57,
  }
}

const kmFormatter = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** "1,6". */
export function formatKmValue(km: number): string {
  return kmFormatter.format(km)
}

/** "1,6 km". */
export function formatKm(km: number): string {
  return `${formatKmValue(km)} km`
}
