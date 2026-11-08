import { COLOMBIA_BOUNDS } from '../lib/map-config'
import type { GeoPoint, MapArea, PlaceSuggestion } from './types'

/** Servicio público de geocodificación (sin clave). Solo se usa con resultados de Colombia. */
const PHOTON_URL = 'https://photon.komoot.io'
const MIN_CHARS = 3
const FALLBACK_DELTA = 0.05
const LAYERS = ['country', 'state', 'city', 'district', 'locality']

interface PhotonProperties {
  osm_type?: string
  osm_id?: number | string
  osm_value?: string
  osm_key?: string
  district?: string
  locality?: string
  suburb?: string
  type?: string
  name?: string
  city?: string
  county?: string
  state?: string
  countrycode?: string
  extent?: number[]
}

interface PhotonFeature {
  geometry?: { coordinates?: number[] }
  properties?: PhotonProperties
}

type Kind = PlaceSuggestion['kind']

function kindOf(props: PhotonProperties): Kind {
  switch (props.type) {
    case 'country':
    case 'state':
    case 'city':
    case 'district':
    case 'locality':
      return props.type
    case 'county':
      return 'city'
  }
  switch (props.osm_value) {
    case 'country':
      return 'country'
    case 'state':
      return 'state'
    case 'city':
    case 'town':
    case 'village':
    case 'municipality':
      return 'city'
    case 'suburb':
    case 'quarter':
    case 'neighbourhood':
    case 'district':
      return 'district'
    default:
      return 'locality'
  }
}

function composeLabel(props: PhotonProperties, withCountry: boolean): string {
  const parts: string[] = []
  for (const part of [props.name, props.city ?? props.county, props.state, withCountry ? 'Colombia' : undefined]) {
    const text = part?.trim()
    if (text && !parts.some((existing) => existing.toLowerCase() === text.toLowerCase())) parts.push(text)
  }
  return parts.join(', ')
}

/** Etiqueta corta del área: como máximo dos partes (nombre + ciudad o departamento). */
function shortLabel(props: PhotonProperties, kind: Kind): string {
  const name = props.name?.trim()
  if (kind === 'country' || kind === 'state') return name ?? ''
  const parent = (props.city ?? props.county ?? props.state)?.trim()
  if (!name) return parent ?? ''
  return parent && parent.toLowerCase() !== name.toLowerCase() ? `${name}, ${parent}` : name
}

const KIND_ORDER: Kind[] = ['country', 'state', 'city', 'district', 'locality']

function isFiniteNumber(value: unknown): value is number {
  return typeof value === 'number' && Number.isFinite(value)
}

/** Convierte la respuesta de Photon en sugerencias; descarta lo que no sea de Colombia. Pura, sin red. */
export function parsePhotonFeatures(json: unknown): PlaceSuggestion[] {
  const features = (json as { features?: PhotonFeature[] } | null)?.features
  if (!Array.isArray(features)) return []
  const results: PlaceSuggestion[] = []
  const seen = new Set<string>()
  for (const feature of features) {
    const props = feature.properties
    const coords = feature.geometry?.coordinates
    if (!props || props.countrycode !== 'CO' || !coords || !isFiniteNumber(coords[0]) || !isFiniteNumber(coords[1])) continue
    const [lng, lat] = coords
    const extent = props.extent
    const bounds =
      extent && extent.length === 4 && extent.every(isFiniteNumber)
        ? { west: extent[0], north: extent[1], east: extent[2], south: extent[3] }
        : { south: lat - FALLBACK_DELTA, west: lng - FALLBACK_DELTA, north: lat + FALLBACK_DELTA, east: lng + FALLBACK_DELTA }
    const label = composeLabel(props, true)
    if (!label) continue
    const id = props.osm_type && props.osm_id !== undefined ? `${props.osm_type}:${props.osm_id}` : `${lat},${lng}`
    if (seen.has(id)) continue
    seen.add(id)
    const kind = kindOf(props)
    const area: MapArea = {
      label: shortLabel(props, kind) || label,
      precision: 'zone',
      bounds,
      center: { lat, lng },
    }
    results.push({ id, label, kind, area })
  }
  // Orden estable por tipo: país, departamento, ciudad, barrio, localidad.
  return results
    .map((item, index) => ({ item, index }))
    .sort((a, b) => KIND_ORDER.indexOf(a.item.kind) - KIND_ORDER.indexOf(b.item.kind) || a.index - b.index)
    .map(({ item }) => item)
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim()
}

const cache = new Map<string, PlaceSuggestion[]>()

/** Autocompletado limitado a Colombia. Mínimo 3 caracteres; caché por texto normalizado. */
export async function searchPlaces(text: string, signal?: AbortSignal): Promise<PlaceSuggestion[]> {
  const key = normalize(text)
  if (key.length < MIN_CHARS) return []
  const cached = cache.get(key)
  if (cached) return cached
  const { south, west, north, east } = COLOMBIA_BOUNDS
  const params = new URLSearchParams({ q: text.trim(), limit: '8', bbox: `${west},${south},${east},${north}` })
  for (const layer of LAYERS) params.append('layer', layer)
  const response = await fetch(`${PHOTON_URL}/api/?${params}`, { signal, headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`Photon respondió ${response.status}`)
  const results = parsePhotonFeatures(await response.json())
  cache.set(key, results)
  return results
}

/** Confirma que el punto está en Colombia y devuelve su etiqueta (barrio, ciudad); `null` fuera de CO. */
export async function reverseInColombia(point: GeoPoint): Promise<{ label: string } | null> {
  const params = new URLSearchParams({ lat: String(point.lat), lon: String(point.lng), limit: '1' })
  const response = await fetch(`${PHOTON_URL}/reverse?${params}`, { headers: { Accept: 'application/json' } })
  if (!response.ok) throw new Error(`Photon respondió ${response.status}`)
  const features = ((await response.json()) as { features?: PhotonFeature[] }).features
  const props = features?.[0]?.properties
  if (!props || props.countrycode !== 'CO') return null
  const isStreet = props.osm_key === 'highway' || props.type === 'street'
  const city = props.city?.trim()
  const first = [props.district, props.locality, props.suburb, props.city, isStreet ? undefined : props.name]
    .map((part) => part?.trim())
    .find(Boolean)
  const label = first && city && city.toLowerCase() !== first.toLowerCase() ? `${first}, ${city}` : (first ?? city)
  return { label: label || props.state?.trim() || 'Colombia' }
}
