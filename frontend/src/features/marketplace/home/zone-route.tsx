import { Fragment, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode, type RefObject } from 'react'
import { Link } from 'react-router'
import {
  animate,
  m,
  useInView,
  useMotionValue,
  useMotionValueEvent,
  useReducedMotion,
  useScroll,
  useTransform,
} from 'motion/react'
import { StoreIcon, WrenchIcon } from 'lucide-react'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import type { NearbyStore, NearbyStores, ReferencePoint } from '@/features/location/api/types'
import { formatKm, formatKmValue } from '@/features/location/lib/geo'
import { pluralize } from '@/lib/format'
import type { AsyncResult } from '@/lib/use-async'
import { useMediaQuery } from '@/lib/use-media-query'
import { cn } from '@/lib/utils'
import { RecommendedMark } from '../components/product-meta'
import { storeProductsPath } from '../lib/paths'
import { CityLayer, RouteLayer } from './city-map'
import { buildRoute, countReached, MAP_H, MAP_W, toPercent, type MapPoint, type RouteGeometry } from './route-geometry'

/** Igual que la variante `scrolly` de index.css. */
const SCROLLY_QUERY = '(min-width: 64rem) and (min-height: 52.5rem) and (prefers-reduced-motion: no-preference)'
export const HERO_STOPS = 5
const EASE = [0.22, 1, 0.36, 1] as const
/** Tramo del scroll de la sección (0–1) en el que se dibuja la ruta. */
const SCROLL_START = 0.04
const SCROLL_END = 0.88

/**
 * - `scroll`: la ruta avanza con el scroll y el titular queda fijo (escritorio alto).
 * - `play`: la ruta se dibuja una vez al entrar en pantalla.
 * - `static`: movimiento reducido; la ruta se ve completa.
 */
type RouteMode = 'scroll' | 'play' | 'static'

function useRouteMode(): RouteMode {
  const scrolly = useMediaQuery(SCROLLY_QUERY)
  const reduced = useReducedMotion()
  if (reduced) return 'static'
  return scrolly ? 'scroll' : 'play'
}

function scrollToRoute(value: number): number {
  return Math.min(1, Math.max(0, (value - SCROLL_START) / (SCROLL_END - SCROLL_START)))
}

const PIN_VARIANTS = {
  idle: { scale: 1 },
  reached: { scale: [0, 1.2, 1], transition: { duration: 0.3, ease: EASE } },
}

function originName(origin: ReferencePoint | undefined): string {
  return origin?.label ?? 'tu ubicación'
}

function stopLabel(stop: NearbyStore, index: number, origin: ReferencePoint | undefined): string {
  const distance = stop.distanceKm !== undefined ? `, a ${formatKm(stop.distanceKm)} de ${originName(origin)}` : ''
  return `Parada ${index + 1}: ${stop.store.name}${distance}. Llevar la ruta hasta aquí`
}

/* ------------------------------------------------------------------ */
/* Piezas compartidas                                                 */
/* ------------------------------------------------------------------ */

function StopFacts({ stop }: { stop: NearbyStore }) {
  return (
    <>
      <div className="mt-2 flex flex-wrap items-center gap-x-3 gap-y-1">
        {stop.distanceKm !== undefined ? (
          <span className="rounded-sm bg-asphalt px-1.5 py-0.5 label-mono text-white tabular-nums">
            a {formatKm(stop.distanceKm)}
          </span>
        ) : null}
        {stop.store.recommended ? <RecommendedMark /> : null}
      </div>
      {stop.store.productCount !== undefined ? (
        <p className="mt-2 text-sm">{pluralize(stop.store.productCount, 'repuesto disponible', 'repuestos disponibles')}</p>
      ) : null}
      <Link
        to={storeProductsPath(stop.store.id)}
        className="mt-1 inline-flex min-h-10 items-center text-sm font-medium text-brand underline-offset-4 hover:underline pointer-coarse:min-h-11"
      >
        Ver sus repuestos
      </Link>
    </>
  )
}

function OriginMarker({ point, origin }: { point: MapPoint; origin: ReferencePoint | undefined }) {
  return (
    <div className="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-1/2" style={toPercent(point)} aria-hidden>
      <span className="block size-4 rounded-full border-[3px] border-card bg-asphalt shadow-[0_0_0_1px_rgb(23_27_32/0.4)]" />
      <span className="absolute top-full left-1/2 mt-1.5 -translate-x-1/2 rounded-sm bg-asphalt px-1.5 py-0.5 whitespace-nowrap label-mono text-white">
        Tú{origin ? `, ${origin.label}` : ''}
      </span>
    </div>
  )
}

/** Tamaño aproximado de la tarjeta en unidades del viewBox (para elegir dónde ponerla). */
const CARD = { width: 250, height: 200, gap: 24 }
const CARD_SIDES = ['right', 'left'] as const
const CARD_ALIGNS = [
  { name: 'center', shift: 0.5, css: '-50%' },
  { name: 'top', shift: 0.12, css: '-12%' },
  { name: 'bottom', shift: 0.88, css: '-88%' },
] as const

/**
 * Coloca la tarjeta al lado del pin donde tape menos pines (y la etiqueta de
 * origen) sin salirse del mapa por los lados, para no crear scroll horizontal.
 */
function cardStyle(point: MapPoint, obstacles: MapPoint[]): CSSProperties {
  let best = { side: 'right' as (typeof CARD_SIDES)[number], align: CARD_ALIGNS[0] as (typeof CARD_ALIGNS)[number] }
  let bestScore = Number.POSITIVE_INFINITY
  for (const side of CARD_SIDES) {
    for (const align of CARD_ALIGNS) {
      const x0 = side === 'right' ? point.x + CARD.gap : point.x - CARD.gap - CARD.width
      const x1 = x0 + CARD.width
      const y0 = point.y - CARD.height * align.shift
      const y1 = y0 + CARD.height
      let score = x0 < 0 || x1 > MAP_W ? 1000 : 0
      // Salirse por arriba o por abajo tapa el contador o el texto de abajo: cuesta más que tapar un pin.
      score += 3 * (Math.max(0, -y0) + Math.max(0, y1 - MAP_H))
      for (const obstacle of obstacles) {
        if (obstacle.x > x0 - 16 && obstacle.x < x1 + 16 && obstacle.y > y0 - 16 && obstacle.y < y1 + 16) score += 60
      }
      if (score < bestScore) {
        bestScore = score
        best = { side, align }
      }
    }
  }
  return {
    ...toPercent(point),
    transform: `translate(${best.side === 'left' ? 'calc(-100% - 1.5rem)' : '1.5rem'}, ${best.align.css})`,
  }
}

/** Tarjeta de la parada activa, al lado del pin. */
function StopCard({
  stop,
  index,
  total,
  point,
  obstacles,
}: {
  stop: NearbyStore
  index: number
  total: number
  point: MapPoint
  obstacles: MapPoint[]
}) {
  const style = cardStyle(point, obstacles)
  return (
    <div className="absolute z-20 w-56" style={style}>
      <m.div
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.25, ease: EASE }}
        className="rounded-xl bg-card p-3.5 shadow-[0_0_0_1px_rgb(0_0_0/0.06),0_2px_4px_rgb(0_0_0/0.04),0_16px_32px_-16px_rgb(23_27_32/0.35)]"
      >
        <p className="label-mono text-muted-foreground tabular-nums">
          Parada {index + 1} de {total}
        </p>
        <p className="mt-1 leading-snug font-semibold">{stop.store.name}</p>
        {stop.branchName ? <p className="text-sm text-muted-foreground">{stop.branchName}</p> : null}
        <StopFacts stop={stop} />
      </m.div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Mapa con ruta (tablet y escritorio)                                */
/* ------------------------------------------------------------------ */

interface RouteMapProps {
  stops: NearbyStore[]
  origin: ReferencePoint | undefined
  sectionRef: RefObject<HTMLElement | null>
}

function RouteMap({ stops, origin, sectionRef }: RouteMapProps) {
  const mode = useRouteMode()
  const geometry = useMemo(
    () => buildRoute(origin?.point, stops.map((stop) => stop.point)),
    [origin, stops],
  )
  const total = stops.length
  const containerRef = useRef<HTMLDivElement>(null)
  const inView = useInView(containerRef, { once: true, amount: 0.35 })

  const progress = useMotionValue(mode === 'static' ? 1 : 0)
  const [reached, setReached] = useState(() => countReached(geometry.stopAt, progress.get()))
  const [selected, setSelected] = useState(0)
  const [announcement, setAnnouncement] = useState('')

  useMotionValueEvent(progress, 'change', (value) => setReached(countReached(geometry.stopAt, value)))

  // Modo scroll: el avance sigue al scroll de la sección (ScrollTimeline nativo si existe).
  const { scrollYProgress } = useScroll({ target: sectionRef, offset: ['start start', 'end end'] })
  useMotionValueEvent(scrollYProgress, 'change', (value) => {
    if (mode === 'scroll') progress.set(scrollToRoute(value))
  })

  useEffect(() => {
    if (mode === 'static') progress.set(1)
    else if (mode === 'scroll') progress.set(scrollToRoute(scrollYProgress.get()))
  }, [mode, progress, scrollYProgress])

  // Modo play: se dibuja una sola vez al entrar en pantalla.
  useEffect(() => {
    if (mode !== 'play' || !inView) return
    const controls = animate(progress, 1, { duration: 0.5 + total * 0.45, ease: [0.65, 0, 0.35, 1] })
    return () => controls.stop()
  }, [mode, inView, progress, total])

  const hidden = useTransform(progress, (value) => 1000 * (1 - value))
  const distances = stops.map((stop) => stop.distanceKm)
  const hasDistances = total > 0 && distances.every((value) => value !== undefined)
  const kms = distances.map((value) => value ?? 0)
  const radius = useTransform(progress, [0, ...geometry.stopAt, 1], [0, ...kms, kms[kms.length - 1] ?? 0])
  const radiusText = useTransform(radius, (value) => formatKmValue(value))
  const endOpacity = useTransform(progress, [0.97, 1], [0, 1])
  const endScale = useTransform(progress, [0.97, 1], [0.6, 1])

  const active = mode === 'static' ? selected : reached - 1

  function goTo(index: number) {
    const stop = stops[index]
    if (!stop) return
    setSelected(index)
    setAnnouncement(
      `Ruta hasta ${stop.store.name}${stop.distanceKm !== undefined ? `, a ${formatKm(stop.distanceKm)} de ${originName(origin)}` : ''}.`,
    )
    const at = Math.min(1, (geometry.stopAt[index] ?? 1) + 0.002)
    if (mode === 'scroll') {
      const section = sectionRef.current
      if (!section) return
      const start = window.scrollY + section.getBoundingClientRect().top
      const scrollable = section.offsetHeight - window.innerHeight
      const target = SCROLL_START + at * (SCROLL_END - SCROLL_START)
      window.scrollTo({ top: start + target * scrollable, behavior: 'smooth' })
    } else if (mode === 'play') {
      animate(progress, at, { duration: 0.7, ease: EASE })
    }
  }

  return (
    <div ref={containerRef} className="flex flex-col gap-3">
      <div className="flex min-h-14 items-end justify-between gap-4" aria-hidden>
        <div>
          {hasDistances ? (
            <>
              <p className="label-mono text-muted-foreground">Radio desde {originName(origin)}</p>
              <p className="mt-1 font-mono text-3xl leading-none font-semibold tracking-tight tabular-nums">
                <m.span>{radiusText}</m.span>
                <span className="ml-1 text-base text-muted-foreground">km</span>
              </p>
            </>
          ) : (
            <p className="label-mono text-muted-foreground">Almacenes en Autopartco</p>
          )}
        </div>
        <p className="label-mono text-right text-muted-foreground tabular-nums">
          {reached} de {total} almacenes
          {mode === 'scroll' && reached < total ? (
            <span className="mt-1 block text-foreground">Desplázate para seguir la ruta</span>
          ) : null}
        </p>
      </div>

      <div className="relative aspect-[10/9] w-full">
        <CityLayer className="absolute inset-0 size-full [mask-image:radial-gradient(closest-side,#000_72%,transparent)]" />
        <RouteLayer geometry={geometry} hidden={hidden} className="absolute inset-0 size-full" />
        <OriginMarker point={geometry.origin} origin={origin} />

        {stops.map((stop, index) => {
          const point = geometry.stops[index]
          if (!point) return null
          const isReached = index < reached
          const isActive = index === active
          return (
            <Fragment key={stop.key}>
              <button
                type="button"
                className="group/pin absolute z-10 grid size-11 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full"
                style={toPercent(point)}
                aria-label={stopLabel(stop, index, origin)}
                aria-current={isActive ? 'step' : undefined}
                onClick={() => goTo(index)}
              >
                <m.span
                  initial={false}
                  variants={PIN_VARIANTS}
                  animate={isReached ? 'reached' : 'idle'}
                  className={cn(
                    'grid place-items-center rounded-full font-mono text-[0.6875rem] leading-none font-bold transition-colors duration-150',
                    isReached
                      ? 'size-6 bg-thread text-white shadow-[0_0_0_3px_var(--color-card)]'
                      : 'size-3.5 border-2 border-input bg-card text-transparent group-hover/pin:border-thread',
                    isActive && 'shadow-[0_0_0_3px_var(--color-card),0_0_0_6px_rgb(200_30_44/0.25)]',
                  )}
                >
                  {isReached ? index + 1 : null}
                </m.span>
              </button>
              {isActive && isReached ? (
                <StopCard
                  stop={stop}
                  index={index}
                  total={total}
                  point={point}
                  obstacles={[
                    geometry.origin,
                    { x: geometry.origin.x, y: geometry.origin.y + 28 },
                    geometry.end,
                    ...geometry.stops.filter((_, other) => other !== index),
                  ]}
                />
              ) : null}
            </Fragment>
          )
        })}

        <m.span
          className="pointer-events-none absolute z-10 grid size-7 -translate-x-1/2 -translate-y-1/2 place-items-center rounded-full bg-asphalt text-white"
          style={{ ...toPercent(geometry.end), opacity: endOpacity, scale: endScale }}
          aria-hidden
        >
          <WrenchIcon className="size-3.5" />
        </m.span>
      </div>

      <p className="sr-only" aria-live="polite">
        {announcement}
      </p>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Línea de tiempo (móvil)                                            */
/* ------------------------------------------------------------------ */

const LIST_VARIANTS = { hidden: {}, show: { transition: { staggerChildren: 0.075 } } }
const ITEM_VARIANTS = {
  hidden: { opacity: 0, y: 10 },
  show: { opacity: 1, y: 0, transition: { duration: 0.45, ease: EASE } },
}

function RouteTimeline({ stops, origin, geometry }: { stops: NearbyStore[]; origin: ReferencePoint | undefined; geometry: RouteGeometry }) {
  return (
    <div>
      <div className="relative h-36 overflow-hidden rounded-xl" aria-hidden>
        <CityLayer className="absolute inset-0 size-full" />
        <RouteLayer geometry={geometry} hidden={0} className="absolute inset-0 size-full" />
      </div>
      <m.ol
        className="relative mt-5 flex flex-col gap-3"
        aria-label={`Almacenes cerca de ${originName(origin)}`}
        initial="hidden"
        whileInView="show"
        viewport={{ once: true, amount: 0.15 }}
        variants={LIST_VARIANTS}
      >
        <span aria-hidden className="thread-v thread-v-scroll absolute top-3 bottom-5 left-[13px]" />
        <m.li variants={ITEM_VARIANTS} className="relative flex min-h-7 items-center pl-10">
          <span aria-hidden className="absolute left-[7px] size-4 rounded-full border-[3px] border-background bg-asphalt" />
          <p className="text-sm">
            <span className="font-semibold">Tu punto de partida</span>
            {origin ? <span className="text-muted-foreground">: {origin.label}</span> : null}
          </p>
        </m.li>
        {stops.map((stop, index) => (
          <m.li key={stop.key} variants={ITEM_VARIANTS} className="relative pl-10">
            <span
              aria-hidden
              className="absolute top-3 left-0 grid size-[30px] place-items-center rounded-full border-[3px] border-background bg-thread font-mono text-[0.6875rem] font-bold text-white"
            >
              {index + 1}
            </span>
            <article className="rounded-xl bg-card p-3.5 shadow-[0_0_0_1px_rgb(0_0_0/0.06),0_2px_6px_-2px_rgb(0_0_0/0.08)]">
              <h3 className="leading-snug font-semibold">{stop.store.name}</h3>
              {stop.branchName ? <p className="text-sm text-muted-foreground">{stop.branchName}</p> : null}
              <StopFacts stop={stop} />
            </article>
          </m.li>
        ))}
        <m.li variants={ITEM_VARIANTS} className="relative flex min-h-7 items-center pl-10">
          <span aria-hidden className="absolute left-[3px] grid size-6 place-items-center rounded-full bg-asphalt text-white">
            <WrenchIcon className="size-3" />
          </span>
          <Link to="/tiendas" className="inline-flex min-h-11 items-center text-sm font-medium text-brand underline-offset-4 hover:underline">
            Ver todas las tiendas
          </Link>
        </m.li>
      </m.ol>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/* Estados y contenedor                                               */
/* ------------------------------------------------------------------ */

/** Marco del mapa sin ruta, con el mismo tamaño que el mapa final (sin saltos). */
function MapFrame({ children }: { children: ReactNode }) {
  return (
    <div className="flex flex-col gap-3">
      <div className="hidden min-h-14 md:block" aria-hidden />
      <div className="relative h-36 overflow-hidden rounded-xl md:aspect-[10/9] md:h-auto md:rounded-none">
        <CityLayer className="absolute inset-0 size-full md:[mask-image:radial-gradient(closest-side,#000_72%,transparent)]" />
        <div className="absolute inset-0 grid place-items-center p-4">{children}</div>
      </div>
    </div>
  )
}

interface ZoneRouteProps {
  result: AsyncResult<NearbyStores>
  sectionRef: RefObject<HTMLElement | null>
}

/** Almacenes cercanos en el hero: mapa con ruta en tablet y escritorio, línea de tiempo en móvil. */
export function ZoneRoute({ result, sectionRef }: ZoneRouteProps) {
  const data = result.data
  const stops = useMemo(() => data?.stores.slice(0, HERO_STOPS) ?? [], [data])
  const origin = data?.origin
  const geometry = useMemo(() => buildRoute(origin?.point, stops.map((stop) => stop.point)), [origin, stops])
  const routeKey = `${data?.city?.id ?? 'all'}:${stops.map((stop) => stop.key).join('|')}`
  const place = data?.city ? `${origin ? `${origin.label}, ` : ''}${data.city.name}` : undefined

  let body: ReactNode
  if (result.status === 'error') {
    body = (
      <MapFrame>
        <ErrorState
          className="max-w-sm bg-card"
          title="No pudimos cargar los almacenes cercanos"
          description="Revisa tu conexión e inténtalo de nuevo. Mientras tanto, puedes buscar tu repuesto."
          onRetry={result.retry}
        />
      </MapFrame>
    )
  } else if (!data) {
    body = (
      <>
        <MapFrame>
          <p role="status" className="rounded-md bg-card px-3 py-2 label-mono text-muted-foreground shadow-sm">
            Buscando almacenes cercanos…
          </p>
        </MapFrame>
        <div className="mt-5 flex flex-col gap-3 md:hidden" aria-hidden>
          {Array.from({ length: 3 }, (_, index) => (
            <Skeleton key={index} className="ml-10 h-24 rounded-xl" />
          ))}
        </div>
      </>
    )
  } else if (stops.length === 0) {
    body = (
      <MapFrame>
        <div className="flex max-w-sm flex-col items-start gap-2 rounded-xl bg-card p-4 shadow-sm">
          <StoreIcon className="size-5 text-muted-foreground" aria-hidden />
          <p className="font-semibold">Aún no hay almacenes con sede en {data.city?.name ?? 'esta ciudad'}</p>
          <p className="text-sm text-muted-foreground">Elige otra ciudad arriba o mira todas las tiendas.</p>
          <Link to="/tiendas" className="inline-flex min-h-11 items-center text-sm font-medium text-brand underline-offset-4 hover:underline">
            Ver todas las tiendas
          </Link>
        </div>
      </MapFrame>
    )
  } else {
    body = (
      <>
        <div className="hidden md:block">
          <RouteMap key={routeKey} stops={stops} origin={origin} sectionRef={sectionRef} />
        </div>
        <div className="md:hidden">
          <RouteTimeline key={routeKey} stops={stops} origin={origin} geometry={geometry} />
        </div>
      </>
    )
  }

  const hasDistances = stops.some((stop) => stop.distanceKm !== undefined)
  return (
    <div className={cn('min-w-0 transition-opacity duration-200', result.isStale && data && 'opacity-60')} aria-busy={result.isStale || undefined}>
      <h2 className="sr-only">{place ? `Almacenes cerca de ${place}` : 'Almacenes en Autopartco'}</h2>
      {body}
      {data && stops.length > 0 ? (
        <p className="mt-3 max-w-md text-xs text-muted-foreground">
          {hasDistances && place
            ? `Distancias en línea recta desde ${place}, un punto de referencia mientras habilitamos tu ubicación.`
            : 'Pronto verás qué almacenes te quedan más cerca.'}
        </p>
      ) : null}
    </div>
  )
}
