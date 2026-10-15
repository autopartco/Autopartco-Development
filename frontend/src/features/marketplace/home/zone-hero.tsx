import { useRef } from 'react'
import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { useNearbyStores } from '@/features/location/hooks'
import { HeroSearch } from '../components/hero-search'
import { searchUrl } from '../lib/product-query'
import { ZoneRoute } from './zone-route'

const POPULAR_SEARCHES = ['Pastillas de freno', 'Filtro de aceite', 'Kit de embrague', 'Bujías', 'Amortiguador']

/**
 * Hero "tu zona". El titular y el buscador se pintan de inmediato (son el LCP) y
 * nunca dependen de la animación. En escritorio alto la sección mide 300vh y el
 * contenido queda fijo mientras el hilo recorre los almacenes cercanos.
 *
 * - Móvil: titular, buscador y la ruta como línea de tiempo vertical.
 * - Tablet (md): una columna; el mapa se limita a 36rem para no ocupar toda la pantalla.
 * - Escritorio (lg): dos columnas; fijo durante el recorrido solo si la pantalla es alta.
 */
export function ZoneHero() {
  const sectionRef = useRef<HTMLElement>(null)
  const nearby = useNearbyStores()
  const city = nearby.data?.city?.name

  return (
    <section ref={sectionRef} aria-labelledby="hero-title" className="relative overflow-x-clip scrolly:h-[300vh]">
      <div className="scrolly:sticky scrolly:top-[var(--header-h)] scrolly:h-[calc(100svh-var(--header-h))]">
        <Container className="grid gap-10 pt-8 pb-14 sm:pt-12 md:gap-12 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:items-center lg:gap-12 lg:py-14 xl:gap-16 scrolly:h-full scrolly:py-6">
          <div className="flex min-w-0 flex-col gap-5 sm:gap-6">
            <p className="label-mono text-muted-foreground">Repuestos en almacenes de {city ?? 'Colombia'}</p>
            <h1 id="hero-title" className="text-display">
              <span className="block">Tu repuesto,</span>
              <span className="block">en el almacén</span>
              <span className="block">más cercano.</span>
            </h1>
            <p className="max-w-lg text-base text-muted-foreground sm:text-lg">
              Para conductores y talleres: busca por nombre o referencia y compara precio, existencias y distancia
              entre los almacenes de {city ?? 'tu ciudad'}.
            </p>
            <HeroSearch className="max-w-xl" />
            <div className="flex max-w-xl flex-wrap items-center gap-2 text-sm">
              <span className="label-mono text-muted-foreground">Lo más buscado</span>
              <ul className="contents">
                {POPULAR_SEARCHES.map((term) => (
                  <li key={term}>
                    <Link
                      to={searchUrl({ q: term })}
                      className="inline-flex min-h-9 items-center rounded-full bg-card px-3.5 shadow-[0_0_0_1px_rgb(0_0_0/0.08)] transition-shadow hover:shadow-[0_0_0_1px_rgb(23_27_32/0.35)] pointer-coarse:min-h-11"
                    >
                      {term}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          </div>
          <div className="min-w-0 md:mx-auto md:w-full md:max-w-xl lg:mx-0 lg:max-w-none">
            <ZoneRoute result={nearby} sectionRef={sectionRef} />
          </div>
        </Container>
      </div>
    </section>
  )
}
