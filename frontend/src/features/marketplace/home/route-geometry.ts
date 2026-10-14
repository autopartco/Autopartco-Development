import type { GeoPoint } from '@/features/location/api/types'
import { offsetKm } from '@/features/location/lib/geo'

/**
 * Geometría del mapa de barrio del hero. El mapa es estilizado: una rejilla de
 * manzanas (4×4 puntos) separadas por calles. Las paradas se proyectan desde
 * coordenadas reales y se ajustan a los cruces de calles, y el hilo va por las
 * calles con tramos horizontales y verticales.
 */

export const MAP_W = 600
export const MAP_H = 540
/** Lado de una manzana con su calle, en unidades del viewBox. */
export const BLOCK = 50
/** Las calles pasan por x, y ≡ 45 (mód. 50): el hueco entre manzanas. */
const STREET = 45
const BOUNDS = { minX: 95, maxX: 545, minY: 95, maxY: 445 }
const MAX_DISTORTION = 2

export interface MapPoint {
  x: number
  y: number
}

export interface RouteGeometry {
  origin: MapPoint
  stops: MapPoint[]
  /** Remate del hilo, una calle después de la última parada (donde va la llave). */
  end: MapPoint
  d: string
  /** Fracción del recorrido (0–1) en la que el hilo llega a cada parada. Estrictamente creciente. */
  stopAt: number[]
}

/** Posiciones de respaldo cuando el servicio no informa coordenadas (API real, hoy). */
const FALLBACK_ORIGIN: MapPoint = { x: 145, y: 345 }
const FALLBACK_SLOTS: MapPoint[] = [
  { x: 245, y: 245 },
  { x: 395, y: 195 },
  { x: 445, y: 345 },
  { x: 295, y: 445 },
  { x: 495, y: 95 },
  { x: 145, y: 145 },
]

/** Desplazamientos para buscar un cruce libre si dos paradas caen en el mismo. */
const NEIGHBORS: [number, number][] = [
  [0, 0],
  [BLOCK, 0],
  [-BLOCK, 0],
  [0, BLOCK],
  [0, -BLOCK],
  [BLOCK, BLOCK],
  [-BLOCK, -BLOCK],
  [BLOCK, -BLOCK],
  [-BLOCK, BLOCK],
  [2 * BLOCK, 0],
  [-2 * BLOCK, 0],
]

function snap(value: number, min: number, max: number): number {
  const snapped = Math.round((value - STREET) / BLOCK) * BLOCK + STREET
  return Math.min(max, Math.max(min, snapped))
}

function placer() {
  const taken = new Set<string>()
  return (point: MapPoint): MapPoint => {
    const base = { x: snap(point.x, BOUNDS.minX, BOUNDS.maxX), y: snap(point.y, BOUNDS.minY, BOUNDS.maxY) }
    for (const [dx, dy] of NEIGHBORS) {
      const candidate = { x: base.x + dx, y: base.y + dy }
      if (candidate.x < BOUNDS.minX || candidate.x > BOUNDS.maxX || candidate.y < BOUNDS.minY || candidate.y > BOUNDS.maxY) {
        continue
      }
      const key = `${candidate.x},${candidate.y}`
      if (!taken.has(key)) {
        taken.add(key)
        return candidate
      }
    }
    return base
  }
}

/** Proyecta el origen y las paradas al viewBox, conservando proporciones y centrando. */
function project(origin: GeoPoint | undefined, points: (GeoPoint | undefined)[]): { origin: MapPoint; stops: MapPoint[] } {
  const place = placer()
  const known = points.filter((point): point is GeoPoint => point !== undefined)
  if (!origin || known.length !== points.length) {
    return {
      origin: place(FALLBACK_ORIGIN),
      stops: points.map((_, index) => place(FALLBACK_SLOTS[index % FALLBACK_SLOTS.length] ?? FALLBACK_ORIGIN)),
    }
  }

  const offsets = known.map((point) => offsetKm(origin, point))
  const xs = [0, ...offsets.map((offset) => offset.east)]
  const ys = [0, ...offsets.map((offset) => offset.north)]
  const minX = Math.min(...xs)
  const maxY = Math.max(...ys)
  const spanX = Math.max(Math.max(...xs) - minX, 0.5)
  const spanY = Math.max(maxY - Math.min(...ys), 0.5)
  const width = BOUNDS.maxX - BOUNDS.minX
  const height = BOUNDS.maxY - BOUNDS.minY
  // Escala por eje con distorsión acotada (×2): el mapa es estilizado y así las
  // paradas no quedan apiñadas en una franja. Las cifras en km siguen siendo reales.
  let scaleX = width / spanX
  let scaleY = height / spanY
  if (scaleX > scaleY * MAX_DISTORTION) scaleX = scaleY * MAX_DISTORTION
  if (scaleY > scaleX * MAX_DISTORTION) scaleY = scaleX * MAX_DISTORTION
  const left = BOUNDS.minX + (width - spanX * scaleX) / 2
  const top = BOUNDS.minY + (height - spanY * scaleY) / 2
  const toMap = (east: number, north: number): MapPoint => ({
    x: left + (east - minX) * scaleX,
    y: top + (maxY - north) * scaleY,
  })

  return {
    origin: place(toMap(0, 0)),
    stops: offsets.map((offset) => place(toMap(offset.east, offset.north))),
  }
}

/** Ruta por calles: tramos H/V alternando el orden en cada parada, más el remate final. */
export function buildRoute(origin: GeoPoint | undefined, points: (GeoPoint | undefined)[]): RouteGeometry {
  const projected = project(origin, points)
  let d = `M${projected.origin.x} ${projected.origin.y}`
  let length = 0
  let previous = projected.origin
  const reachedAt: number[] = []

  projected.stops.forEach((stop, index) => {
    const dx = stop.x - previous.x
    const dy = stop.y - previous.y
    if (index % 2 === 0) {
      if (dx) d += ` H${stop.x}`
      if (dy) d += ` V${stop.y}`
    } else {
      if (dy) d += ` V${stop.y}`
      if (dx) d += ` H${stop.x}`
    }
    length += Math.abs(dx) + Math.abs(dy)
    reachedAt.push(length)
    previous = stop
  })

  const end: MapPoint = { x: previous.x + (previous.x > MAP_W / 2 ? -BLOCK : BLOCK), y: previous.y }
  d += ` H${end.x}`
  length += BLOCK

  return {
    origin: projected.origin,
    stops: projected.stops,
    end,
    d,
    stopAt: reachedAt.map((value) => value / length),
  }
}

/** Cuántas paradas alcanzó el hilo con este avance. */
export function countReached(stopAt: number[], progress: number): number {
  let count = 0
  for (const at of stopAt) {
    if (progress + 1e-4 >= at) count += 1
    else break
  }
  return count
}

/** Posición en porcentaje del contenedor (mismo aspecto que el viewBox). */
export function toPercent(point: MapPoint): { left: string; top: string } {
  return { left: `${(point.x / MAP_W) * 100}%`, top: `${(point.y / MAP_H) * 100}%` }
}
