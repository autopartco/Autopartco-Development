import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { storeProductsPath } from '@/features/marketplace/lib/paths'
import { StarIcon } from 'lucide-react'
import { SponsoredLabel } from '@/features/marketplace/components/ads/sponsored-listing'
import { trackAdClick, trackAdImpression } from '@/features/marketplace/lib/ad-events'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { MAP_LIMIT_EXACT, MAP_LIMIT_ZONE, type MapArea, type MapBranch } from '../api/types'
import { useMapArea, useMapBranches } from '../hooks'
import { formatKm } from '../lib/geo'
import { loadMapLibre, MAP_STYLE_URL } from '../lib/map-config'

type MapLibreModule = typeof import('maplibre-gl')
type MapInstance = import('maplibre-gl').Map
type MarkerInstance = import('maplibre-gl').Marker

const MAP_LOCALE = {
  'CooperativeGesturesHandler.WindowsHelpText': 'Usa Ctrl + rueda para acercar el mapa',
  'CooperativeGesturesHandler.MacHelpText': 'Usa ⌘ + rueda para acercar el mapa',
  'CooperativeGesturesHandler.MobileHelpText': 'Usa dos dedos para mover el mapa',
  'NavigationControl.ZoomIn': 'Acercar',
  'NavigationControl.ZoomOut': 'Alejar',
  'AttributionControl.ToggleAttribution': 'Ver atribución',
}

const GRAPHITE = '#202326'
const RED = 'var(--ap-red, #c9272c)'
const ROW_PLACEHOLDERS = [0, 1, 2, 3]
/** Radio (px) de agrupación de sedes cercanas en pantalla. */
const CLUSTER_RADIUS_PX = 40
/** Desplazamiento (px) de un marcador normal que caería debajo de un recomendado, para que ambos se vean. */
const OVERLAP_SHIFT_PX = 30
/** Sedes de la lista de respaldo (sin mapa) antes de remitir al directorio. */
const FALLBACK_LIST_MAX = 12
/** Con ubicación exacta, el encuadre inicial cubre las sedes más cercanas, no todas. */
const EXACT_FIT_NEAREST = 20
/** Tiempo que el mapa debe verse antes de contar la impresión de los recomendados. */
const IMPRESSION_VISIBLE_MS = 1000

function prefersReducedMotion(): boolean {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

function toBounds(area: MapArea): [[number, number], [number, number]] {
  const { south, west, north, east } = area.bounds
  return [
    [west, south],
    [east, north],
  ]
}

function markerButton(label: string): HTMLButtonElement {
  const button = document.createElement('button')
  button.type = 'button'
  button.setAttribute('aria-label', label)
  button.style.cssText =
    'display:flex;align-items:center;justify-content:center;width:44px;height:44px;padding:0;border:0;background:transparent;cursor:pointer;'
  return button
}

const SVG_NS = 'http://www.w3.org/2000/svg'
/** Trazo de la estrella de Lucide (`star`). */
const STAR_PATH =
  'M11.525 2.295a.53.53 0 0 1 .95 0l2.31 4.679a2.123 2.123 0 0 0 1.595 1.16l5.166.756a.53.53 0 0 1 .294.904l-3.736 3.638a2.123 2.123 0 0 0-.611 1.878l.882 5.14a.53.53 0 0 1-.771.56l-4.618-2.428a2.122 2.122 0 0 0-1.973 0L6.396 21.01a.53.53 0 0 1-.77-.56l.881-5.139a2.122 2.122 0 0 0-.611-1.879L2.16 9.795a.53.53 0 0 1 .294-.906l5.165-.755a2.122 2.122 0 0 0 1.597-1.16z'

function createStar(): SVGSVGElement {
  const svg = document.createElementNS(SVG_NS, 'svg')
  svg.setAttribute('width', '14')
  svg.setAttribute('height', '14')
  svg.setAttribute('viewBox', '0 0 24 24')
  svg.setAttribute('fill', '#fff')
  svg.setAttribute('stroke', '#fff')
  svg.setAttribute('stroke-width', '2')
  svg.setAttribute('stroke-linejoin', 'round')
  svg.setAttribute('aria-hidden', 'true')
  const path = document.createElementNS(SVG_NS, 'path')
  path.setAttribute('d', STAR_PATH)
  svg.append(path)
  return svg
}

/**
 * Marcador de sede: botón de 44 px de objetivo táctil. Sede normal: punto grafito de 20 px. Recomendada: disco
 * de 28 px con estrella (la forma la distingue, no solo el color). Abre el popup de la sede.
 */
function createBranchElement(branch: MapBranch): HTMLButtonElement {
  const label = branch.recommended
    ? `${branch.storeName}, ${branch.branchName}. Recomendado, patrocinado`
    : `${branch.storeName}, ${branch.branchName}`
  const button = markerButton(label)
  button.setAttribute('aria-expanded', 'false')
  const disc = document.createElement('span')
  disc.setAttribute('aria-hidden', 'true')
  const size = branch.recommended ? 28 : 20
  disc.style.cssText = `display:flex;align-items:center;justify-content:center;width:${size}px;height:${size}px;box-sizing:border-box;border-radius:9999px;background:${GRAPHITE};border:2px solid #fff;transition:box-shadow 150ms,background-color 150ms;`
  if (branch.recommended) disc.append(createStar())
  button.append(disc)
  return button
}

/**
 * Grupo de sedes cercanas: círculo con el número; al tocarlo, el mapa se acerca. Los grupos de recomendados son
 * grafito con estrella y número; los demás, blancos con borde grafito.
 */
function createClusterElement(count: number, recommended: boolean): HTMLButtonElement {
  const button = markerButton(
    recommended
      ? `${count} sedes recomendadas (patrocinadas) agrupadas. Acercar el mapa`
      : `${count} almacenes agrupados. Acercar el mapa`,
  )
  const disc = document.createElement('span')
  disc.setAttribute('aria-hidden', 'true')
  const size = count >= 100 ? 40 : count >= 10 ? 34 : 30
  const colors = recommended ? `background:${GRAPHITE};border:2px solid #fff;color:#fff;` : `background:#fff;border:2px solid ${GRAPHITE};color:${GRAPHITE};`
  disc.style.cssText = `display:flex;align-items:center;justify-content:center;gap:2px;min-width:${size}px;height:${size}px;padding:0 6px;box-sizing:border-box;border-radius:9999px;${colors}font:600 13px/1 var(--font-sans);font-variant-numeric:tabular-nums;`
  if (recommended) {
    const star = createStar()
    star.setAttribute('width', '11')
    star.setAttribute('height', '11')
    disc.append(star)
  }
  disc.append(String(count))
  button.append(disc)
  return button
}

function textElement(tag: 'p' | 'a', className: string, text: string): HTMLElement {
  const element = document.createElement(tag)
  element.className = className
  element.textContent = text
  return element
}

/** Contenido del popup: etiqueta de patrocinio (recomendados), tienda, zona, distancia y acceso a sus repuestos. */
function createPopupContent(branch: MapBranch, areaLabel: string, onOpen: (path: string) => void): HTMLElement {
  const path = storeProductsPath(branch.storeId)
  const box = document.createElement('div')
  box.className = 'flex flex-col gap-1'
  if (branch.recommended) {
    box.append(textElement('p', 'text-xs font-semibold text-ap-muted', `Patrocinado · Recomendado en ${areaLabel}`))
  }
  box.append(
    textElement('p', 'text-sm font-semibold text-ap-ink', branch.storeName),
    textElement('p', 'text-sm text-ap-muted', [branch.area ?? branch.branchName, branch.city].filter(Boolean).join(', ')),
  )
  if (branch.distanceKm !== undefined) {
    box.append(textElement('p', 'ap-ref text-sm text-ap-ink', `a ${formatKm(branch.distanceKm)} en línea recta`))
  }
  const link = textElement('a', 'inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline', 'Ver repuestos') as HTMLAnchorElement
  link.href = path
  link.addEventListener('click', (event) => {
    if (branch.adImpressionToken) trackAdClick(branch.adImpressionToken)
    if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey) return
    event.preventDefault()
    onOpen(path)
  })
  box.append(link)
  return box
}

/** Punto rojo con halo y borde blanco; decorativo, la leyenda lo describe. */
function createUserElement(): HTMLDivElement {
  const dot = document.createElement('div')
  dot.setAttribute('aria-hidden', 'true')
  dot.style.cssText =
    'width:14px;height:14px;border-radius:9999px;background:var(--ap-red,#c9272c);border:2px solid #fff;box-sizing:border-box;box-shadow:0 0 0 6px rgba(201,39,44,0.2);'
  return dot
}

function UserDot() {
  return <span aria-hidden className="inline-block size-3 shrink-0 rounded-full bg-ap-red ring-4 ring-ap-red/20" />
}

function RecommendedDot() {
  return (
    <span aria-hidden className="inline-flex size-4 shrink-0 items-center justify-center rounded-full bg-ap-ink">
      <StarIcon className="size-2.5 fill-white text-white" />
    </span>
  )
}

function ClusterDot() {
  return (
    <span aria-hidden className="inline-flex size-4 shrink-0 items-center justify-center rounded-full border-2 border-ap-ink bg-ap-white text-[9px] font-semibold leading-none text-ap-ink">
      3
    </span>
  )
}

interface Cluster {
  members: MapBranch[]
  x: number
  y: number
}

/** Agrupación voraz en pantalla: cada sede se une al primer grupo a menos de CLUSTER_RADIUS_PX. */
function clusterInScreen(map: MapInstance, branches: MapBranch[]): Cluster[] {
  const clusters: Cluster[] = []
  for (const branch of branches) {
    const { x, y } = map.project([branch.point.lng, branch.point.lat])
    const near = clusters.find((cluster) => Math.hypot(cluster.x - x, cluster.y - y) <= CLUSTER_RADIUS_PX)
    if (near) {
      const n = near.members.length
      near.x = (near.x * n + x) / (n + 1)
      near.y = (near.y * n + y) / (n + 1)
      near.members.push(branch)
    } else {
      clusters.push({ members: [branch], x, y })
    }
  }
  return clusters
}

/**
 * Mini mapa de cobertura del inicio: todas (o casi todas) las sedes del área elegida sobre un mapa monocromo de
 * MapLibre (cargado en diferido). Las sedes cercanas se agrupan; los almacenes recomendados (campaña «Mapa del
 * inicio») nunca se agrupan, salen primero y llevan la etiqueta «Patrocinado». Si el mapa falla (WebGL, estilo, red)
 * queda una lista de respaldo.
 */
export function CoverageMap() {
  const area = useMapArea()
  const result = useMapBranches()
  const navigate = useNavigate()
  const branches = result.data
  const exact = area.precision === 'exact'
  const user = exact ? area.user : undefined

  const [mapFailed, setMapFailed] = useState(false)
  const [mapReady, setMapReady] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapInstance | null>(null)
  const libRef = useRef<MapLibreModule | null>(null)
  const navigateRef = useRef(navigate)
  const areaRef = useRef(area)
  useEffect(() => {
    navigateRef.current = navigate
    areaRef.current = area
  })

  const showMap = result.status !== 'error' && !!branches && branches.length > 0 && !mapFailed

  // Crea el mapa una sola vez mientras haya sedes que mostrar; lo destruye al desmontar.
  useEffect(() => {
    if (!showMap) return
    let cancelled = false
    let map: MapInstance | null = null
    void (async () => {
      try {
        const lib = await loadMapLibre()
        const container = containerRef.current
        if (cancelled || !container) return
        libRef.current = lib
        const instance = new lib.Map({
          container,
          style: MAP_STYLE_URL,
          bounds: toBounds(areaRef.current),
          cooperativeGestures: true,
          dragRotate: false,
          pitchWithRotate: false,
          touchPitch: false,
          attributionControl: false,
          locale: MAP_LOCALE,
        })
        map = instance
        let loaded = false
        instance.on('load', () => {
          loaded = true
        })
        instance.on('error', () => {
          if (!loaded && !cancelled) setMapFailed(true)
        })
        instance.addControl(new lib.NavigationControl({ showCompass: false }), 'top-right')
        const attribution = new lib.AttributionControl({ compact: true })
        instance.addControl(attribution)
        // La atribución arranca plegada (solo el botón) también en anchos grandes.
        const collapse = () => attribution._container?.classList.remove('maplibregl-compact-show')
        instance.once('load', collapse)
        instance.once('idle', collapse)
        instance.touchZoomRotate.disableRotation()
        mapRef.current = instance
        setMapReady(true)
      } catch {
        if (!cancelled) setMapFailed(true)
      }
    })()
    return () => {
      cancelled = true
      setMapReady(false)
      mapRef.current = null
      map?.remove()
    }
  }, [showMap])

  // Marcadores, grupos, popups y cámara: se actualizan al cambiar área o sedes sin recrear el mapa. Los grupos se
  // recalculan al terminar cada movimiento (el agrupamiento depende del zoom).
  useEffect(() => {
    const map = mapRef.current
    const lib = libRef.current
    if (!mapReady || !map || !lib || !branches) return
    const go = (path: string) => void navigateRef.current(path)
    const reduced = prefersReducedMotion()
    const popup = new lib.Popup({
      closeButton: false,
      closeOnClick: false,
      offset: 20,
      maxWidth: '260px',
      className: 'ap-map-popup',
      focusAfterOpen: false,
    })
    const recommended = branches.filter((branch) => branch.recommended)
    const regular = branches.filter((branch) => !branch.recommended)

    // Sedes con marcador propio en este momento, en orden de tabulación (recomendados primero). Los recomendados
    // se agrupan solo entre sí, para que nunca queden ocultos dentro de un grupo de sedes normales.
    let shown: { branch: MapBranch; button: HTMLButtonElement }[] = []
    const fixedMarkers: MarkerInstance[] = []
    let dynamicMarkers: MarkerInstance[] = []
    let currentKey: string | undefined
    let pinnedKey: string | undefined
    let timer: ReturnType<typeof setTimeout> | undefined
    const cancelHide = () => clearTimeout(timer)
    const indexOf = (key: string | undefined) => (key === undefined ? -1 : shown.findIndex((entry) => entry.branch.key === key))

    const setActive = (key: string | undefined, active: boolean) => {
      const entry = shown[indexOf(key)]
      if (!entry) return
      entry.button.setAttribute('aria-expanded', String(active))
      const disc = entry.button.firstElementChild as HTMLElement | null
      if (disc) {
        disc.style.background = active ? RED : GRAPHITE
        disc.style.boxShadow = active ? '0 0 0 4px rgba(201,39,44,0.25)' : 'none'
      }
    }
    const hide = () => {
      cancelHide()
      if (currentKey === undefined) return
      setActive(currentKey, false)
      currentKey = undefined
      popup.remove()
    }
    const scheduleHide = (key: string) => {
      cancelHide()
      timer = setTimeout(() => {
        if (currentKey === key && pinnedKey !== key) hide()
      }, 150)
    }
    const inside = (target: EventTarget | null, key: string) =>
      target instanceof Node && (popup.getElement()?.contains(target) || shown[indexOf(key)]?.button.contains(target))
    const show = (branch: MapBranch) => {
      cancelHide()
      if (currentKey === branch.key) return
      hide()
      const key = branch.key
      currentKey = key
      popup
        .setLngLat([branch.point.lng, branch.point.lat])
        .setDOMContent(createPopupContent(branch, areaRef.current.label, go))
        .addTo(map)
      const element = popup.getElement()
      element.addEventListener('mouseenter', cancelHide)
      element.addEventListener('mouseleave', () => scheduleHide(key))
      element.addEventListener('focusout', (event) => {
        if (pinnedKey !== key && !inside(event.relatedTarget, key)) hide()
      })
      element.querySelector('a')?.addEventListener('keydown', (event) => {
        if (event.key !== 'Tab') return
        const index = indexOf(key)
        if (event.shiftKey) {
          event.preventDefault()
          shown[index]?.button.focus({ preventScroll: true })
        } else if (shown[index + 1]) {
          event.preventDefault()
          shown[index + 1].button.focus({ preventScroll: true })
        }
      })
      setActive(key, true)
    }

    const branchMarker = (branch: MapBranch, offset: [number, number] = [0, 0]): MarkerInstance => {
      const button = createBranchElement(branch)
      const key = branch.key
      button.addEventListener('mouseenter', () => {
        if (pinnedKey !== key) pinnedKey = undefined
        show(branch)
      })
      button.addEventListener('mouseleave', () => scheduleHide(key))
      button.addEventListener('focus', () => {
        if (pinnedKey !== key) pinnedKey = undefined
        show(branch)
      })
      button.addEventListener('blur', (event) => {
        if (pinnedKey !== key && !inside(event.relatedTarget, key)) hide()
      })
      button.addEventListener('keydown', (event) => {
        if (event.key !== 'Tab' || event.shiftKey || currentKey !== key) return
        const link = popup.getElement()?.querySelector('a')
        if (!link) return
        event.preventDefault()
        link.focus({ preventScroll: true })
      })
      button.addEventListener('click', (event) => {
        event.stopPropagation()
        if (pinnedKey === key) {
          pinnedKey = undefined
          hide()
        } else {
          pinnedKey = key
          show(branch)
        }
      })
      shown.push({ branch, button })
      const marker = new lib.Marker({ element: button, offset })
        .setLngLat([branch.point.lng, branch.point.lat])
        .addTo(map)
      // Los recomendados quedan por encima de grupos y sedes normales.
      if (branch.recommended) marker.getElement().style.zIndex = '2'
      return marker
    }

    const clusterMarker = (cluster: Cluster, isRecommended: boolean, offset: [number, number] = [0, 0]): MarkerInstance => {
      const button = createClusterElement(cluster.members.length, isRecommended)
      button.addEventListener('click', (event) => {
        event.stopPropagation()
        const bounds = new lib.LngLatBounds()
        for (const member of cluster.members) bounds.extend([member.point.lng, member.point.lat])
        map.fitBounds(bounds, {
          padding: 48,
          maxZoom: Math.max(map.getZoom() + 2, 14),
          duration: reduced ? 0 : 400,
        })
      })
      const marker = new lib.Marker({ element: button, offset }).setLngLat(map.unproject([cluster.x, cluster.y])).addTo(map)
      if (isRecommended) marker.getElement().style.zIndex = '2'
      return marker
    }

    if (user) {
      fixedMarkers.push(new lib.Marker({ element: createUserElement() }).setLngLat([user.lng, user.lat]).addTo(map))
    }

    const renderRegular = () => {
      const keep = currentKey
      for (const marker of dynamicMarkers) marker.remove()
      dynamicMarkers = []
      shown = []
      const featured = clusterInScreen(map, recommended)
      for (const cluster of featured) {
        dynamicMarkers.push(cluster.members.length === 1 ? branchMarker(cluster.members[0]) : clusterMarker(cluster, true))
      }
      for (const cluster of clusterInScreen(map, regular)) {
        const covered = featured.some((top) => Math.hypot(top.x - cluster.x, top.y - cluster.y) < OVERLAP_SHIFT_PX)
        const offset: [number, number] = covered ? [OVERLAP_SHIFT_PX, 0] : [0, 0]
        dynamicMarkers.push(
          cluster.members.length === 1 ? branchMarker(cluster.members[0], offset) : clusterMarker(cluster, false, offset),
        )
      }
      // El popup abierto sigue si su sede conserva marcador propio; si quedó dentro de un grupo, se cierra.
      if (keep === undefined) return
      if (indexOf(keep) < 0) {
        currentKey = undefined
        pinnedKey = undefined
        popup.remove()
      } else {
        setActive(keep, true)
      }
    }

    const onMapClick = (event: { originalEvent: MouseEvent }) => {
      const target = event.originalEvent.target
      if (target instanceof Element && target.closest('.maplibregl-marker, .maplibregl-popup')) return
      pinnedKey = undefined
      hide()
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || currentKey === undefined) return
      const button = shown[indexOf(currentKey)]?.button
      pinnedKey = undefined
      hide()
      button?.focus({ preventScroll: true })
    }

    // Impresión de recomendados: mapa visible (≥ 50 %) y en reposo durante 1 s, con la sede dentro del encuadre.
    const container = map.getContainer()
    let containerVisible = false
    let impressionTimer: ReturnType<typeof setTimeout> | undefined
    const observer =
      typeof IntersectionObserver === 'undefined'
        ? undefined
        : new IntersectionObserver(
            ([entry]) => {
              containerVisible = !!entry?.isIntersecting && entry.intersectionRatio >= 0.5
            },
            { threshold: [0, 0.5, 1] },
          )
    observer?.observe(container)
    const onIdle = () => {
      if (recommended.length === 0) return
      clearTimeout(impressionTimer)
      impressionTimer = setTimeout(() => {
        if (!containerVisible || document.visibilityState !== 'visible') return
        const view = map.getBounds()
        for (const branch of recommended) {
          if (branch.adImpressionToken && view.contains([branch.point.lng, branch.point.lat])) {
            trackAdImpression(branch.adImpressionToken)
          }
        }
      }, IMPRESSION_VISIBLE_MS)
    }

    map.on('click', onMapClick)
    map.on('moveend', renderRegular)
    map.on('idle', onIdle)
    container.addEventListener('keydown', onKeyDown)

    const duration = reduced ? 0 : 400
    if (branches.length === 0 && !user) {
      map.fitBounds(toBounds(area), { duration })
    } else {
      // Con ubicación exacta, encuadra al usuario y sus sedes más cercanas; en zona, todas las sedes.
      const framed = user ? [...recommended, ...regular.slice(0, EXACT_FIT_NEAREST)] : branches
      const bounds = new lib.LngLatBounds()
      for (const branch of framed) bounds.extend([branch.point.lng, branch.point.lat])
      if (user) bounds.extend([user.lng, user.lat])
      map.fitBounds(bounds, { padding: { top: 40, right: 56, bottom: 48, left: 32 }, maxZoom: 14, duration })
    }
    renderRegular()
    return () => {
      hide()
      popup.remove()
      clearTimeout(impressionTimer)
      observer?.disconnect()
      map.off('click', onMapClick)
      map.off('moveend', renderRegular)
      map.off('idle', onIdle)
      container.removeEventListener('keydown', onKeyDown)
      for (const marker of [...fixedMarkers, ...dynamicMarkers]) marker.remove()
    }
  }, [mapReady, branches, area, user])

  let body: ReactNode
  if (result.status === 'error') {
    body = (
      <ErrorState
        title="No pudimos cargar los almacenes cercanos"
        description="Revisa tu conexión e inténtalo de nuevo. Mientras tanto, puedes buscar tu repuesto."
        onRetry={result.retry}
      />
    )
  } else if (!branches) {
    body = (
      <div aria-hidden className="flex flex-col gap-3">
        <Skeleton className="aspect-[4/3] min-h-60 w-full rounded-lg" />
        <div className="flex flex-col divide-y divide-ap-line">
          {ROW_PLACEHOLDERS.map((row) => (
            <div key={row} className="flex items-center justify-between gap-4 py-3">
              <Skeleton className="h-4 w-1/2" />
              <Skeleton className="h-4 w-14" />
            </div>
          ))}
        </div>
      </div>
    )
  } else if (branches.length === 0) {
    body = (
      <div className="flex flex-col items-start gap-2 py-2">
        <p className="font-semibold text-ap-ink">Aún no hay almacenes en {area.label}</p>
        <p className="text-sm text-ap-muted">Elige otra zona en la cabecera o mira el directorio completo.</p>
      </div>
    )
  } else {
    const hasDistances = exact && branches.some((branch) => branch.distanceKm !== undefined)
    const hasRecommended = branches.some((branch) => branch.recommended)
    const regularCount = branches.filter((branch) => !branch.recommended).length
    // Si la respuesta llegó al tope, no son todas las sedes del área.
    const truncated = regularCount >= (exact ? MAP_LIMIT_EXACT : MAP_LIMIT_ZONE)
    const listed = branches.slice(0, FALLBACK_LIST_MAX)
    body = (
      <>
        {showMap ? (
          <div
            ref={containerRef}
            role="region"
            aria-label="Mapa de almacenes"
            className="aspect-[4/3] min-h-60 w-full overflow-hidden rounded-lg border border-ap-line bg-ap-paper"
          />
        ) : null}
        {showMap ? (
          <p className="text-sm text-ap-muted tabular-nums">
            {truncated
              ? `Mostrando ${pluralize(branches.length, 'almacén', 'almacenes')} de ${area.label}. Elige una zona más precisa para ver más.`
              : `${pluralize(branches.length, 'almacén', 'almacenes')} en ${exact ? 'el mapa' : area.label}`}
          </p>
        ) : (
          <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
            {listed.map((branch) => (
              <li key={branch.key} className="group relative flex items-start justify-between gap-4 py-3">
                <div className="flex min-w-0 flex-col gap-1">
                  {branch.recommended ? <SponsoredLabel /> : null}
                  <Link
                    onClick={() => {
                      if (branch.adImpressionToken) trackAdClick(branch.adImpressionToken)
                    }}
                    to={storeProductsPath(branch.storeId)}
                    data-stretched-link
                    className="font-semibold text-ap-ink after:absolute after:inset-0 focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-[var(--focus)] group-hover:text-ap-red"
                  >
                    {branch.storeName}
                  </Link>
                  <p className="text-sm text-ap-muted">
                    {branch.area ?? branch.branchName}
                    {branch.city ? `, ${branch.city}` : ''}
                  </p>
                </div>
                {branch.distanceKm !== undefined ? (
                  <span className="ap-ref shrink-0 pt-0.5 text-ap-ink">
                    {formatKm(branch.distanceKm)}
                    <span className="sr-only"> en línea recta</span>
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        {!showMap && branches.length > listed.length ? (
          <p className="text-sm text-ap-muted tabular-nums">
            y {pluralize(branches.length - listed.length, 'almacén más', 'almacenes más')} en el directorio
          </p>
        ) : null}
        <div className="flex flex-col gap-1 text-xs text-ap-muted">
          {showMap && hasRecommended ? (
            <p className="flex items-center gap-2">
              <RecommendedDot />
              Recomendado (patrocinado)
            </p>
          ) : null}
          {showMap && branches.length > 1 ? (
            <p className="flex items-center gap-2">
              <ClusterDot />
              Varios almacenes juntos; tócalo para acercar
            </p>
          ) : null}
          {exact && user ? (
            <p className="flex items-center gap-2">
              <UserDot />
              Tu ubicación aproximada
            </p>
          ) : null}
          {exact && hasDistances ? <p>Distancia en línea recta desde tu ubicación</p> : null}
          {!exact && truncated ? <p>Muestra de almacenes en {area.label}; cambia en cada visita</p> : null}
        </div>
      </>
    )
  }

  return (
    <section
      aria-labelledby="cerca-titulo"
      aria-busy={result.isStale || undefined}
      className={cn(
        'flex min-w-0 flex-col gap-4 self-start rounded-xl border border-ap-line bg-ap-white p-5 transition-opacity duration-150 sm:p-6',
        result.isStale && branches && 'opacity-60',
      )}
    >
      <div className="flex flex-col gap-1">
        <p className="ap-index">Cobertura / {area.label}</p>
        <h2 id="cerca-titulo" className="text-block-title text-ap-ink">
          {exact
            ? 'Almacenes cerca de ti'
            : area.label === 'Colombia'
              ? 'Almacenes en Colombia'
              : `Almacenes cerca de ${area.label}`}
        </h2>
      </div>
      {body}
      <Link
        to="/tiendas"
        className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
      >
        Ver el directorio de tiendas
      </Link>
    </section>
  )
}
