import { useEffect, useMemo, useRef, useState } from 'react'
import { loadMapLibre, MAP_STYLE_URL } from '@/features/location/lib/map-config'
import { cn } from '@/lib/utils'
import type { StoreBranch } from '../../api/types'

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

function reducedMotion(): boolean {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/** Marcador de sede: botón de 44 px con un punto grafito (rojo si es la sede elegida). */
function createMarker(branch: StoreBranch, onSelect: () => void): HTMLButtonElement {
  const button = document.createElement('button')
  button.type = 'button'
  button.setAttribute('aria-label', `Ver ${branch.name} en la lista`)
  button.setAttribute('aria-pressed', 'false')
  button.style.cssText = 'display:flex;align-items:center;justify-content:center;width:44px;height:44px;padding:0;border:0;background:transparent;cursor:pointer;'
  const dot = document.createElement('span')
  dot.setAttribute('aria-hidden', 'true')
  dot.style.cssText = `width:20px;height:20px;box-sizing:border-box;border-radius:9999px;background:${GRAPHITE};border:2px solid #fff;transition:box-shadow 150ms,background-color 150ms;`
  button.append(dot)
  button.addEventListener('click', onSelect)
  return button
}

function paint(button: HTMLButtonElement, active: boolean): void {
  button.setAttribute('aria-pressed', String(active))
  const dot = button.firstElementChild as HTMLElement | null
  if (!dot) return
  dot.style.background = active ? 'var(--ap-red, #c9272c)' : GRAPHITE
  dot.style.boxShadow = active ? '0 0 0 4px rgba(201,39,44,0.25)' : 'none'
}

interface BranchMapProps {
  branches: StoreBranch[]
  activeId?: string
  onSelect: (branchId: string) => void
  className?: string
  /** Se avisa si el mapa no pudo cargar; la lista de sedes sigue siendo la alternativa. */
  onFail?: () => void
}

/**
 * Mapa de sucursales con MapLibre. No se carga hasta que el contenedor entra en pantalla (no compite con el LCP del
 * perfil) y reserva su alto desde el inicio para no mover el diseño. Solo pinta sedes con coordenadas.
 */
export function BranchMap({ branches, activeId, onSelect, className, onFail }: BranchMapProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapInstance | null>(null)
  const markersRef = useRef<Map<string, { marker: MarkerInstance; button: HTMLButtonElement }>>(new Map())
  const [visible, setVisible] = useState(false)
  const [ready, setReady] = useState(false)
  const [failed, setFailed] = useState(false)
  const selectRef = useRef(onSelect)
  const failRef = useRef(onFail)
  useEffect(() => {
    selectRef.current = onSelect
    failRef.current = onFail
  })

  const located = useMemo(() => branches.filter((branch) => branch.point), [branches])

  // Carga diferida: solo al acercarse el mapa a la pantalla.
  useEffect(() => {
    const node = containerRef.current
    if (!node || visible) return
    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) {
          setVisible(true)
          observer.disconnect()
        }
      },
      { rootMargin: '200px' },
    )
    observer.observe(node)
    return () => observer.disconnect()
  }, [visible])

  // Crea el mapa una sola vez.
  useEffect(() => {
    if (!visible || located.length === 0) return
    let cancelled = false
    let map: MapInstance | null = null
    void (async () => {
      try {
        const lib = await loadMapLibre()
        const container = containerRef.current
        if (cancelled || !container) return
        const first = located[0]!.point!
        const instance = new lib.Map({
          container,
          style: MAP_STYLE_URL,
          center: [first.lng, first.lat],
          zoom: 14,
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
          if (!loaded && !cancelled) {
            setFailed(true)
            failRef.current?.()
          }
        })
        instance.addControl(new lib.NavigationControl({ showCompass: false }), 'top-right')
        const attribution = new lib.AttributionControl({ compact: true })
        instance.addControl(attribution)
        const collapse = () => attribution._container?.classList.remove('maplibregl-compact-show')
        instance.once('load', collapse)
        instance.once('idle', collapse)
        instance.touchZoomRotate.disableRotation()

        const markers = markersRef.current
        const bounds = new lib.LngLatBounds()
        for (const branch of located) {
          const { lat, lng } = branch.point!
          const button = createMarker(branch, () => selectRef.current(branch.id))
          markers.set(branch.id, { marker: new lib.Marker({ element: button }).setLngLat([lng, lat]).addTo(instance), button })
          bounds.extend([lng, lat])
        }
        if (located.length > 1) instance.fitBounds(bounds, { padding: 48, maxZoom: 15, duration: 0 })
        mapRef.current = instance
        setReady(true)
      } catch {
        if (!cancelled) {
          setFailed(true)
          failRef.current?.()
        }
      }
    })()
    const markers = markersRef.current
    return () => {
      cancelled = true
      setReady(false)
      for (const { marker } of markers.values()) marker.remove()
      markers.clear()
      mapRef.current = null
      map?.remove()
    }
  }, [visible, located])

  // Sede elegida: marcador rojo y cámara hacia ella.
  useEffect(() => {
    if (!ready) return
    for (const [id, { button }] of markersRef.current) paint(button, id === activeId)
    const branch = located.find((item) => item.id === activeId)
    if (branch?.point && mapRef.current) {
      mapRef.current.easeTo({ center: [branch.point.lng, branch.point.lat], duration: reducedMotion() ? 0 : 400 })
    }
  }, [ready, activeId, located])

  if (failed || located.length === 0) return null
  return (
    <div
      ref={containerRef}
      id="mapa-sedes"
      role="region"
      aria-label="Mapa de sedes. La lista de sedes está debajo."
      className={cn('aspect-[4/3] min-h-56 w-full overflow-hidden rounded-lg border border-ap-line bg-ap-paper', className)}
    />
  )
}
