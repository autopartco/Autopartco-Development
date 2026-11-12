import { useEffect, useRef, useState } from 'react'
import { LoaderCircleIcon, MapPinIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'
import { geocodeAddress } from '../api/photon-geocoder'
import { isInColombia, loadMapLibre, MAP_STYLE_URL } from '../lib/map-config'

type MapInstance = import('maplibre-gl').Map
type MarkerInstance = import('maplibre-gl').Marker

/** Punto de una sede. `approximate`: salió de la dirección y nadie lo ajustó en el mapa. */
export interface PickedPoint {
  lat: number
  lng: number
  approximate: boolean
}

interface PointPickerProps {
  id: string
  value: PickedPoint | null
  onChange: (point: PickedPoint) => void
  /** Dirección para ubicar el pin la primera vez. */
  address: { line: string; city: string; department: string }
  error?: string
}

const MAP_LOCALE = {
  'NavigationControl.ZoomIn': 'Acercar',
  'NavigationControl.ZoomOut': 'Alejar',
  'AttributionControl.ToggleAttribution': 'Ver atribución',
}

const round = (value: number) => Math.round(value * 1e6) / 1e6

/**
 * Ubicación de una sede en el mapa: «Ubicar con la dirección» pone el pin (aproximado) y se ajusta arrastrándolo o
 * tocando el mapa (exacto). Sin pin no se puede guardar: el mapa público de sedes lo necesita.
 */
export function PointPicker({ id, value, onChange, address, error }: PointPickerProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const mapRef = useRef<MapInstance | null>(null)
  const markerRef = useRef<MarkerInstance | null>(null)
  const changeRef = useRef(onChange)
  const [status, setStatus] = useState<string>()
  const [locating, setLocating] = useState(false)
  const [mapFailed, setMapFailed] = useState(false)
  useEffect(() => {
    changeRef.current = onChange
  })

  const hasPoint = value !== null

  // Crea el mapa cuando ya hay un punto; después solo se mueve el marcador.
  useEffect(() => {
    if (!hasPoint || mapRef.current || !value) return
    let cancelled = false
    void (async () => {
      try {
        const lib = await loadMapLibre()
        const container = containerRef.current
        if (cancelled || !container) return
        const map = new lib.Map({
          container,
          style: MAP_STYLE_URL,
          center: [value.lng, value.lat],
          zoom: value.approximate ? 14 : 16,
          dragRotate: false,
          pitchWithRotate: false,
          touchPitch: false,
          attributionControl: false,
          locale: MAP_LOCALE,
        })
        map.addControl(new lib.NavigationControl({ showCompass: false }), 'top-right')
        const attribution = new lib.AttributionControl({ compact: true })
        map.addControl(attribution)
        // Igual que el mapa de sedes: la atribución empieza plegada para no tapar el mapa pequeño.
        const collapse = () => attribution._container?.classList.remove('maplibregl-compact-show')
        map.once('load', collapse)
        map.once('idle', collapse)
        map.touchZoomRotate.disableRotation()
        const marker = new lib.Marker({ draggable: true, color: '#c9272c' }).setLngLat([value.lng, value.lat]).addTo(map)
        const pick = (lat: number, lng: number) => {
          if (!isInColombia({ lat, lng })) {
            setStatus('El punto debe quedar dentro de Colombia.')
            return
          }
          setStatus('Ubicación ajustada.')
          changeRef.current({ lat: round(lat), lng: round(lng), approximate: false })
        }
        marker.on('dragend', () => {
          const { lat, lng } = marker.getLngLat()
          pick(lat, lng)
        })
        map.on('click', (event) => {
          marker.setLngLat(event.lngLat)
          pick(event.lngLat.lat, event.lngLat.lng)
        })
        mapRef.current = map
        markerRef.current = marker
      } catch {
        if (!cancelled) setMapFailed(true)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [hasPoint, value])

  useEffect(
    () => () => {
      markerRef.current?.remove()
      mapRef.current?.remove()
      mapRef.current = null
      markerRef.current = null
    },
    [],
  )

  // El valor cambió desde fuera (p. ej. «Ubicar con la dirección»): mueve marcador y cámara.
  useEffect(() => {
    if (!value || !markerRef.current || !mapRef.current) return
    const current = markerRef.current.getLngLat()
    if (round(current.lat) === value.lat && round(current.lng) === value.lng) return
    markerRef.current.setLngLat([value.lng, value.lat])
    mapRef.current.jumpTo({ center: [value.lng, value.lat], zoom: value.approximate ? 14 : 16 })
  }, [value])

  async function locate() {
    if (!address.line.trim() || !address.city.trim()) {
      setStatus('Escribe la dirección y la ciudad para ubicarla en el mapa.')
      return
    }
    setLocating(true)
    setStatus(undefined)
    try {
      const point = await geocodeAddress(address.line, address.city, address.department)
      if (!point || !isInColombia(point)) {
        setStatus('No encontramos esa dirección. Revisa la ciudad o mueve el mapa hasta tu almacén.')
        return
      }
      changeRef.current({ lat: round(point.lat), lng: round(point.lng), approximate: true })
      setStatus('Ubicamos la dirección de forma aproximada. Arrastra el pin hasta la puerta del almacén.')
    } catch {
      setStatus('No pudimos buscar la dirección. Inténtalo de nuevo.')
    } finally {
      setLocating(false)
    }
  }

  const hintId = `${id}-ayuda`
  const errorId = `${id}-error`
  return (
    <div className="flex flex-col gap-2" role="group" aria-labelledby={`${id}-titulo`} aria-describedby={error ? `${hintId} ${errorId}` : hintId}>
      <div className="flex flex-wrap items-center justify-between gap-2">
        <span id={`${id}-titulo`} className="text-sm font-medium text-ap-ink">
          Ubicación en el mapa
        </span>
        <Button id={id} type="button" variant="outline" size="sm" className="h-11 gap-2 sm:h-9" onClick={() => void locate()} disabled={locating}>
          {locating ? <LoaderCircleIcon className="size-4 animate-spin motion-reduce:animate-none" aria-hidden /> : <MapPinIcon className="size-4" aria-hidden />}
          {hasPoint ? 'Volver a ubicar con la dirección' : 'Ubicar con la dirección'}
        </Button>
      </div>
      <p id={hintId} className="text-sm text-ap-muted">
        Los compradores ven este punto en el mapa de sedes. Arrastra el pin o toca el mapa para marcar la entrada.
      </p>
      {hasPoint && !mapFailed ? (
        <div
          ref={containerRef}
          role="region"
          aria-label="Mapa para ajustar la ubicación de la sucursal"
          className={cn('h-56 w-full overflow-hidden rounded-lg border bg-ap-paper', error ? 'border-destructive' : 'border-ap-line')}
        />
      ) : null}
      {value ? (
        <p className="text-xs text-ap-muted tabular-nums">
          {value.approximate ? 'Aproximada' : 'Ajustada'} · {value.lat.toFixed(5)}, {value.lng.toFixed(5)}
        </p>
      ) : null}
      <p role="status" className={cn('text-sm text-ap-ink', status ? '' : 'sr-only')}>
        {status}
      </p>
      {error ? (
        <p id={errorId} className="text-sm font-medium text-destructive">
          {error}
        </p>
      ) : null}
    </div>
  )
}
