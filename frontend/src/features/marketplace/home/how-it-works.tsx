import { useRef } from 'react'
import { Link } from 'react-router'
import { m, useInView } from 'motion/react'
import { CarFrontIcon, ScaleIcon, SearchIcon, StoreIcon, type LucideIcon } from 'lucide-react'
import { Container } from '@/components/common/page-header'
import { cn } from '@/lib/utils'
import { REVEAL_ITEM, REVEAL_ONCE } from './motion-presets'

interface Step {
  title: string
  body: string
  icon: LucideIcon
  /** Posición en la rejilla 2×2 para que el recorrido sea en el sentido del reloj. */
  place: string
}

const STEPS: Step[] = [
  {
    title: 'Busca',
    body: 'Escribe el repuesto o la referencia. Si quieres, agrega la marca y el modelo de tu carro.',
    icon: SearchIcon,
    place: 'sm:col-start-1 sm:row-start-1',
  },
  {
    title: 'Compara',
    body: 'Mira el precio, las existencias y la distancia de cada almacén que lo publica.',
    icon: ScaleIcon,
    place: 'sm:col-start-2 sm:row-start-1',
  },
  {
    title: 'Confirma compatibilidad',
    body: 'Revisa los vehículos para los que aplica la pieza y confirma la referencia con la tienda.',
    icon: CarFrontIcon,
    place: 'sm:col-start-2 sm:row-start-2',
  },
  {
    title: 'Recoge o contacta la tienda',
    body: 'Ve al almacén o consulta con la tienda antes de ir. La compra en línea llegará más adelante.',
    icon: StoreIcon,
    place: 'sm:col-start-1 sm:row-start-2',
  },
]

/**
 * Flecha circular del bucle: dos arcos con punta, en rojo, sobre una pista
 * discontinua. Gira una vuelta cada 20 s (CSS) y se pausa fuera de la vista.
 */
function LoopRing() {
  return (
    <svg viewBox="0 0 180 180" className="size-full" aria-hidden focusable="false">
      <circle cx="90" cy="90" r="72" fill="none" stroke="var(--color-map-dot)" strokeWidth="2" strokeDasharray="2 6" />
      <g className="loop-ring [transform-box:view-box] [transform-origin:90px_90px]">
        <g fill="none" stroke="var(--color-thread)" strokeWidth="2.5" strokeLinecap="round">
          <path d="M102.5 19.1A72 72 0 0 1 102.5 160.9" />
          <path d="M77.5 160.9A72 72 0 0 1 77.5 19.1" />
        </g>
        <g fill="var(--color-thread)">
          <polygon points="96.6,161.9 103.4,165.8 101.6,156" />
          <polygon points="83.4,18.1 76.6,14.2 78.4,24" />
        </g>
      </g>
    </svg>
  )
}

export function HowItWorksSection() {
  const loopRef = useRef<HTMLDivElement>(null)
  const loopVisible = useInView(loopRef)

  return (
    <section aria-labelledby="como-funciona-titulo" className="py-20 sm:py-28">
      {/* Dos columnas solo desde xl: en lg los pasos del bucle quedarían de ~200px y el texto se apretaría. */}
      <Container className="grid gap-14 xl:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] xl:items-center xl:gap-16">
        {/* Tablet y lg: texto y recuadro para almacenes lado a lado; en xl, apilados a la izquierda del bucle. */}
        <div className="grid gap-8 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] md:items-end md:gap-10 xl:grid-cols-1 xl:gap-10">
          <div className="flex flex-col gap-5">
            <p className="label-mono text-muted-foreground">4 pasos, del buscador al mostrador</p>
            <h2 id="como-funciona-titulo" className="text-section">
              Así funciona Autopartco
            </h2>
            <p className="max-w-md text-base text-muted-foreground sm:text-lg">
              Sirve igual si arreglas tu carro o si atiendes un taller. Con la próxima pieza, vuelves a empezar.
            </p>
          </div>

          <div className="surface-asphalt flex flex-col items-start gap-3 rounded-2xl p-6">
            <p className="text-xl font-bold tracking-tight">¿Tienes un almacén?</p>
            <p className="max-w-sm text-sm text-asphalt-muted">
              Muestra tu inventario a quienes buscan repuestos cerca de tu almacén. Revisamos cada solicitud antes de
              publicar.
            </p>
            <Link
              to="/vender"
              className="mt-1 inline-flex min-h-11 items-center rounded-lg bg-white px-4 py-2 font-semibold text-asphalt transition-colors hover:bg-white/90"
            >
              Vende en Autopartco
            </Link>
          </div>
        </div>

        <div ref={loopRef} data-loop-paused={loopVisible ? undefined : ''} className="relative mx-auto w-full max-w-3xl xl:max-w-none">
          <div className="pointer-events-none absolute top-1/2 left-1/2 hidden size-32 -translate-x-1/2 -translate-y-1/2 sm:block md:size-40">
            <LoopRing />
          </div>
          <m.ol {...REVEAL_ONCE} className="relative grid gap-4 sm:grid-cols-2 sm:gap-x-24 sm:gap-y-20 md:gap-x-28 md:gap-y-24">
            {STEPS.map((step, index) => (
              <m.li
                key={step.title}
                variants={REVEAL_ITEM}
                className={cn(
                  'flex flex-col gap-3 rounded-xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.06)]',
                  step.place,
                )}
              >
                <div className="flex items-center gap-3">
                  <span className="grid size-8 place-items-center rounded-full bg-thread font-mono text-xs font-bold text-white">
                    {index + 1}
                  </span>
                  <step.icon className="size-5 text-muted-foreground" aria-hidden />
                </div>
                <h3 className="text-lg leading-tight font-bold tracking-tight">{step.title}</h3>
                <p className="text-sm text-muted-foreground">{step.body}</p>
              </m.li>
            ))}
          </m.ol>
        </div>
      </Container>
    </section>
  )
}
