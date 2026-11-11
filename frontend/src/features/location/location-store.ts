import { COLOMBIA_BOUNDS } from './lib/map-config'
import type { MapArea } from './api/types'
import { clearAreaCookie, readAreaCookie, roundAreaCoord, writeAreaCookie } from '@/lib/area-cookie'
import { clearHttpCache } from '@/lib/http'

/**
 * Área de mapa elegida, compartida entre la cabecera y el inicio.
 * Solo se persiste un área de zona, en la cookie `ap_area` (centro redondeado a 0,01° y código de ciudad):
 * la ubicación exacta (`user`) vive en memoria y nunca se guarda.
 * La cookie no lleva la etiqueta ni los límites del mapa: se guardan solo para mostrarlos en `localStorage`
 * y se descartan si ya no coinciden con el centro de la cookie.
 */
const LEGACY_STORAGE_KEY = 'autopartco:area:v1'
const VIEW_STORAGE_KEY = 'autopartco:area-view:v1'
/** Medio lado (grados) de los límites cuando solo se conoce el centro. */
const FALLBACK_HALF_SPAN = 0.25

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

function readJson(key: string): unknown {
  try {
    const raw = window.localStorage.getItem(key)
    return raw ? JSON.parse(raw) : undefined
  } catch {
    return undefined
  }
}

function saveView(area: MapArea): void {
  try {
    const { label, precision, bounds, center } = area
    window.localStorage.setItem(VIEW_STORAGE_KEY, JSON.stringify({ label, precision, bounds, center }))
  } catch {
    // Almacenamiento no disponible (modo privado): la etiqueta se reconstruye como «Tu zona».
  }
}

function dropView(): void {
  try {
    window.localStorage.removeItem(VIEW_STORAGE_KEY)
  } catch {
    // Sin almacenamiento: no hay nada que quitar.
  }
}

/** Escribe la cookie `ap_area` con el centro redondeado de una zona (el código de ciudad no se conoce aquí). */
function persistZone(area: MapArea): void {
  writeAreaCookie({ lat: area.center.lat, lng: area.center.lng })
  saveView(area)
}

function readStored(): MapArea | undefined {
  // Migración única: el área de localStorage pasa a la cookie y se quita la clave anterior.
  try {
    if (window.localStorage.getItem(LEGACY_STORAGE_KEY) !== null) {
      const legacy = parseZoneArea(readJson(LEGACY_STORAGE_KEY))
      if (legacy && !readAreaCookie()) persistZone(legacy)
      window.localStorage.removeItem(LEGACY_STORAGE_KEY)
    }
  } catch {
    // Sin almacenamiento: no hay nada que migrar.
  }

  const cookie = readAreaCookie()
  if (!cookie) return undefined
  const view = parseZoneArea(readJson(VIEW_STORAGE_KEY))
  if (view && roundAreaCoord(view.center.lat) === roundAreaCoord(cookie.lat) && roundAreaCoord(view.center.lng) === roundAreaCoord(cookie.lng)) {
    return view
  }
  return parseZoneArea({
    label: 'Tu zona',
    precision: 'zone',
    center: { lat: cookie.lat, lng: cookie.lng },
    bounds: {
      south: cookie.lat - FALLBACK_HALF_SPAN,
      north: cookie.lat + FALLBACK_HALF_SPAN,
      west: cookie.lng - FALLBACK_HALF_SPAN,
      east: cookie.lng + FALLBACK_HALF_SPAN,
    },
  })
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
    if (area.label === DEFAULT_MAP_AREA.label) {
      // «Colombia» es no tener área: el servidor no recibe punto por defecto.
      clearAreaCookie()
      dropView()
    } else {
      persistZone(area)
    }
  }
  // La respuesta del servidor puede depender del área: se descartan las respuestas guardadas.
  clearHttpCache()
  for (const listener of listeners) listener()
}
