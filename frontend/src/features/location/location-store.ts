import { COLOMBIA_BOUNDS } from './lib/map-config'
import type { MapArea } from './api/types'

/**
 * Área de mapa elegida, compartida entre la cabecera y el inicio.
 * Solo se guarda en localStorage un área de zona: la ubicación exacta (`user`) vive en memoria.
 */
const STORAGE_KEY = 'autopartco:area:v1'

export const DEFAULT_MAP_AREA: MapArea = {
  label: 'Colombia',
  precision: 'zone',
  bounds: { ...COLOMBIA_BOUNDS },
  center: { lat: 4.6, lng: -74.1 },
}

const isNum = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value)

function parseZoneArea(value: unknown): MapArea | undefined {
  if (!value || typeof value !== 'object') return undefined
  const { label, precision, bounds, center } = value as Record<string, unknown>
  const b = bounds as Record<string, unknown> | null
  const c = center as Record<string, unknown> | null
  if (typeof label !== 'string' || !label.trim() || precision !== 'zone') return undefined
  if (!b || !isNum(b.south) || !isNum(b.west) || !isNum(b.north) || !isNum(b.east)) return undefined
  if (b.south >= b.north || b.west >= b.east) return undefined
  if (!c || !isNum(c.lat) || !isNum(c.lng)) return undefined
  if (b.south > COLOMBIA_BOUNDS.north || b.north < COLOMBIA_BOUNDS.south || b.west > COLOMBIA_BOUNDS.east || b.east < COLOMBIA_BOUNDS.west) {
    return undefined
  }
  return {
    label: label.trim().slice(0, 120),
    precision: 'zone',
    bounds: { south: b.south, west: b.west, north: b.north, east: b.east },
    center: { lat: c.lat, lng: c.lng },
  }
}

function readStored(): MapArea | undefined {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    return raw ? parseZoneArea(JSON.parse(raw)) : undefined
  } catch {
    return undefined
  }
}

let current: MapArea = (typeof window === 'undefined' ? undefined : readStored()) ?? DEFAULT_MAP_AREA
const listeners = new Set<() => void>()

export function subscribeMapArea(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function getMapArea(): MapArea {
  return current
}

export function setMapArea(area: MapArea): void {
  current = area
  // Una ubicación exacta nunca se persiste: al recargar vuelve al último área de zona guardada.
  if (area.precision === 'zone') {
    try {
      const { label, precision, bounds, center } = area
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify({ label, precision, bounds, center }))
    } catch {
      // Almacenamiento no disponible (modo privado): la elección dura la sesión.
    }
  }
  for (const listener of listeners) listener()
}
