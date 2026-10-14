import { memo, useId } from 'react'
import { m, type MotionValue } from 'motion/react'
import { MAP_H, MAP_W, type RouteGeometry } from './route-geometry'

/**
 * Mapa de barrio estilizado (decorativo). La rejilla de puntos de 3px cada 10px
 * forma manzanas de 4×4 con un patrón SVG: miles de puntos con un solo nodo.
 * Avenidas, un parque y un río se insinúan quitando puntos con una máscara.
 */

/** 16 puntos de una manzana como un único `path` (círculos de r = 1.5). */
const BLOCK_DOTS = Array.from({ length: 16 }, (_, index) => {
  const cx = 5 + (index % 4) * 10
  const cy = 5 + Math.floor(index / 4) * 10
  return `M${cx - 1.5} ${cy}a1.5 1.5 0 1 0 3 0a1.5 1.5 0 1 0-3 0`
}).join('')

const RIVER = 'M-20 478C70 440 120 530 230 500S380 418 470 468 590 540 640 498'
const AVENUES = ['M-10 310 610 30', 'M0 245H600', 'M295 0V540']
const PARK = { cx: 470, cy: 118, r: 30 }

function svgId(raw: string): string {
  return raw.replace(/[^a-zA-Z0-9_-]/g, '')
}

/** Capa de ciudad: manzanas, avenidas, parque y río. Sin props que cambien: no se vuelve a pintar. */
export const CityLayer = memo(function CityLayer({ className }: { className?: string }) {
  const id = svgId(useId())
  return (
    <svg
      viewBox={`0 0 ${MAP_W} ${MAP_H}`}
      preserveAspectRatio="xMidYMid slice"
      className={className}
      aria-hidden
      focusable="false"
    >
      <defs>
        <pattern id={`${id}-blocks`} width="50" height="50" patternUnits="userSpaceOnUse">
          <path d={BLOCK_DOTS} fill="var(--color-map-dot)" />
        </pattern>
        <pattern id={`${id}-water`} width="6" height="6" patternUnits="userSpaceOnUse">
          <circle cx="3" cy="3" r="1" fill="var(--color-map-water)" />
        </pattern>
        <mask id={`${id}-city`} maskUnits="userSpaceOnUse" x="0" y="0" width={MAP_W} height={MAP_H}>
          <rect width={MAP_W} height={MAP_H} fill="#fff" />
          <g fill="none" stroke="#000">
            {AVENUES.map((d) => (
              <path key={d} d={d} strokeWidth="14" />
            ))}
            <path d={RIVER} strokeWidth="40" />
          </g>
          <circle {...PARK} fill="#000" />
        </mask>
      </defs>
      <rect width={MAP_W} height={MAP_H} fill={`url(#${id}-blocks)`} mask={`url(#${id}-city)`} />
      <path d={RIVER} fill="none" stroke={`url(#${id}-water)`} strokeWidth="26" strokeLinecap="round" />
      <circle {...PARK} fill="none" stroke="var(--color-map-dot)" strokeWidth="1.5" strokeDasharray="3 4" />
    </svg>
  )
})

interface RouteLayerProps {
  geometry: RouteGeometry
  /** Parte del hilo que falta por dibujar, de 1000 (nada) a 0 (completo). */
  hidden: MotionValue<number> | number
  className?: string
}

/**
 * Hilo de la ruta: trazado previsto en gris y, encima, el hilo rojo con textura
 * de tejido (dos trazos discontinuos entrelazados, desplazados 3px). Se revela
 * con una máscara cuyo `stroke-dashoffset` sigue el avance.
 */
export function RouteLayer({ geometry, hidden, className }: RouteLayerProps) {
  const id = svgId(useId())
  return (
    <svg
      viewBox={`0 0 ${MAP_W} ${MAP_H}`}
      preserveAspectRatio="xMidYMid slice"
      className={className}
      aria-hidden
      focusable="false"
    >
      <defs>
        <mask id={`${id}-reveal`} maskUnits="userSpaceOnUse" x="0" y="0" width={MAP_W} height={MAP_H}>
          <m.path
            d={geometry.d}
            fill="none"
            stroke="#fff"
            strokeWidth="16"
            pathLength={1000}
            strokeDasharray="1000 1000"
            style={{ strokeDashoffset: hidden }}
          />
        </mask>
      </defs>
      <path d={geometry.d} fill="none" stroke="var(--color-map-water)" strokeWidth="2" strokeDasharray="2 5" />
      <g mask={`url(#${id}-reveal)`} fill="none" stroke="var(--color-thread)" strokeWidth="2" strokeLinejoin="round">
        <path d={geometry.d} strokeDasharray="7 5" transform="translate(-1.5 -1.5)" />
        <path d={geometry.d} strokeDasharray="7 5" strokeDashoffset="6" transform="translate(1.5 1.5)" />
      </g>
    </svg>
  )
}
