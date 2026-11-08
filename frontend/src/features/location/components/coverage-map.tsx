import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { storeProductsPath } from '@/features/marketplace/lib/paths'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { MapArea, MapBranch } from '../api/types'
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
const ROW_PLACEHOLDERS = [0, 1, 2, 3]

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

/** Marcador de sede: botón de 44 px de objetivo táctil con un punto grafito; abre el popup de la sede. */
function createBranchElement(branch: MapBranch): HTMLButtonElement {
  const button = document.createElement('button')
  button.type = 'button'
  button.setAttribute('aria-label', `${branch.storeName}, ${branch.branchName}`)
  button.setAttribute('aria-expanded', 'false')
  button.style.cssText =
    'display:flex;align-items:center;justify-content:center;width:44px;height:44px;padding:0;border:0;background:transparent;cursor:pointer;'
  const dot = document.createElement('span')
  dot.setAttribute('aria-hidden', 'true')
  dot.style.cssText = `width:20px;height:20px;box-sizing:border-box;border-radius:9999px;background:${GRAPHITE};border:2px solid #fff;transition:box-shadow 150ms,background-color 150ms;`
  button.append(dot)
  return button
}

function textElement(tag: 'p' | 'a', className: string, text: string): HTMLElement {
  const element = document.createElement(tag)
  element.className = className
  element.textContent = text
  return element
}

/** Contenido del popup: tienda, zona, distancia (si existe) y acceso a sus repuestos. */
function createPopupContent(branch: MapBranch, onOpen: (path: string) => void): HTMLElement {
  const path = storeProductsPath(branch.storeId)
  const box = document.createElement('div')
  box.className = 'flex flex-col gap-1'
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

/**
 * Mini mapa de cobertura del inicio: sedes de almacenes del área elegida sobre un mapa
 * monocromo de MapLibre (cargado en diferido) y el detalle de cada sede en un popup.
 * Si el mapa falla (WebGL, estilo, red) queda una lista de respaldo.
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

  // Marcadores, popups y cámara: se actualizan al cambiar área o sedes sin recrear el mapa.
  useEffect(() => {
    const map = mapRef.current
    const lib = libRef.current
    if (!mapReady || !map || !lib || !branches) return
    const go = (path: string) => void navigateRef.current(path)
    const popup = new lib.Popup({
      closeButton: false,
      closeOnClick: false,
      offset: 20,
      maxWidth: '260px',
      className: 'ap-map-popup',
      focusAfterOpen: false,
    })
    const buttons: HTMLButtonElement[] = []
    const markers: MarkerInstance[] = []
    let current = -1
    let pinned = -1
    let timer: ReturnType<typeof setTimeout> | undefined
    const cancelHide = () => clearTimeout(timer)

    const setActive = (index: number, active: boolean) => {
      const button = buttons[index]
      if (!button) return
      button.setAttribute('aria-expanded', String(active))
      const disc = button.firstElementChild as HTMLElement | null
      if (disc) {
        disc.style.background = active ? 'var(--ap-red, #c9272c)' : GRAPHITE
        disc.style.boxShadow = active ? '0 0 0 4px rgba(201,39,44,0.25)' : 'none'
      }
    }
    const hide = () => {
      cancelHide()
      if (current < 0) return
      setActive(current, false)
      current = -1
      popup.remove()
    }
    const scheduleHide = (index: number) => {
      cancelHide()
      timer = setTimeout(() => {
        if (current === index && pinned !== index) hide()
      }, 150)
    }
    const inside = (target: EventTarget | null, index: number) =>
      target instanceof Node && (popup.getElement()?.contains(target) || buttons[index]?.contains(target))
    const show = (index: number) => {
      cancelHide()
      if (current === index) return
      hide()
      const branch = branches[index]
      current = index
      popup
        .setLngLat([branch.point.lng, branch.point.lat])
        .setDOMContent(createPopupContent(branch, go))
        .addTo(map)
      const element = popup.getElement()
      element.addEventListener('mouseenter', cancelHide)
      element.addEventListener('mouseleave', () => scheduleHide(index))
      element.addEventListener('focusout', (event) => {
        if (pinned !== index && !inside(event.relatedTarget, index)) hide()
      })
      element.querySelector('a')?.addEventListener('keydown', (event) => {
        if (event.key !== 'Tab') return
        if (event.shiftKey) {
          event.preventDefault()
          buttons[index]?.focus({ preventScroll: true })
        } else if (buttons[index + 1]) {
          event.preventDefault()
          buttons[index + 1].focus({ preventScroll: true })
        }
      })
      setActive(index, true)
    }

    branches.forEach((branch, index) => {
      const button = createBranchElement(branch)
      button.addEventListener('mouseenter', () => {
        if (pinned !== index) pinned = -1
        show(index)
      })
      button.addEventListener('mouseleave', () => scheduleHide(index))
      button.addEventListener('focus', () => {
        if (pinned !== index) pinned = -1
        show(index)
      })
      button.addEventListener('blur', (event) => {
        if (pinned !== index && !inside(event.relatedTarget, index)) hide()
      })
      button.addEventListener('keydown', (event) => {
        if (event.key !== 'Tab' || event.shiftKey || current !== index) return
        const link = popup.getElement()?.querySelector('a')
        if (!link) return
        event.preventDefault()
        link.focus({ preventScroll: true })
      })
      button.addEventListener('click', (event) => {
        event.stopPropagation()
        if (pinned === index) {
          pinned = -1
          hide()
        } else {
          pinned = index
          show(index)
        }
      })
      buttons.push(button)
      markers.push(new lib.Marker({ element: button }).setLngLat([branch.point.lng, branch.point.lat]).addTo(map))
    })
    if (user) {
      markers.push(new lib.Marker({ element: createUserElement() }).setLngLat([user.lng, user.lat]).addTo(map))
    }

    const onMapClick = (event: { originalEvent: MouseEvent }) => {
      const target = event.originalEvent.target
      if (target instanceof Element && target.closest('.maplibregl-marker, .maplibregl-popup')) return
      pinned = -1
      hide()
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape' || current < 0) return
      const button = buttons[current]
      pinned = -1
      hide()
      button?.focus({ preventScroll: true })
    }
    const container = map.getContainer()
    map.on('click', onMapClick)
    container.addEventListener('keydown', onKeyDown)

    const duration = prefersReducedMotion() ? 0 : 400
    if (branches.length === 0 && !user) {
      map.fitBounds(toBounds(area), { duration })
    } else {
      const bounds = new lib.LngLatBounds()
      for (const branch of branches) bounds.extend([branch.point.lng, branch.point.lat])
      if (user) bounds.extend([user.lng, user.lat])
      map.fitBounds(bounds, { padding: { top: 40, right: 56, bottom: 48, left: 32 }, maxZoom: 14, duration })
    }
    return () => {
      hide()
      popup.remove()
      map.off('click', onMapClick)
      container.removeEventListener('keydown', onKeyDown)
      for (const marker of markers) marker.remove()
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
          <p className="text-sm text-ap-muted tabular-nums">{pluralize(branches.length, 'almacén', 'almacenes')} en el mapa</p>
        ) : (
          <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
            {branches.map((branch) => (
              <li key={branch.key} className="group relative flex items-start justify-between gap-4 py-3">
                <div className="flex min-w-0 flex-col gap-1">
                  <Link
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
        <div className="flex flex-col gap-1 text-xs text-ap-muted">
          {exact ? (
            <>
              {user ? (
                <p className="flex items-center gap-2">
                  <UserDot />
                  Tu ubicación aproximada
                </p>
              ) : null}
              {hasDistances ? <p>Distancia en línea recta desde tu ubicación</p> : null}
            </>
          ) : (
            <p>Muestra de almacenes en {area.label}; cambia en cada visita</p>
          )}
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
