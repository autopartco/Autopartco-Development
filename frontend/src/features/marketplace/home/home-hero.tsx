import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { CoverageMap } from '@/features/location/components/coverage-map'
import { useMapArea } from '@/features/location/hooks'
import { HeroSearch } from '../components/hero-search'
import { searchUrl } from '../lib/product-query'

/** Ejemplos de búsqueda: atajos al buscador, no un ranking. */
const SEARCH_EXAMPLES = ['Pastillas de freno', 'Filtro de aceite', 'Kit de embrague', 'Bujías', 'Amortiguador']

/**
 * Inicio (§7.1): titular a la izquierda, el buscador como herramienta principal con
 * ejemplos, y al lado la cobertura real (almacenes con sede en el área elegida).
 * En móvil se apila: titular, buscador, ejemplos y almacenes.
 */
export function HomeHero() {
  const { label } = useMapArea()
  const city = label === 'Colombia' ? undefined : label

  return (
    <section aria-labelledby="hero-title" className="border-b border-ap-line bg-ap-paper">
      <Container className="grid gap-10 py-10 sm:py-12 lg:grid-cols-[minmax(0,7fr)_minmax(0,5fr)] lg:gap-14 lg:py-16">
        <div className="flex min-w-0 flex-col gap-5">
          <p className="ap-index">Catálogo / Repuestos en {city ?? 'Colombia'}</p>
          <h1 id="hero-title" className="max-w-2xl text-display text-ap-graphite">
            Tu repuesto, en el almacén más cercano.
          </h1>
          <p className="max-w-xl text-base text-ap-muted sm:text-lg">
            Para conductores y talleres: busca por nombre o referencia y compara precio, existencias y distancia entre
            los almacenes de {city ?? 'tu ciudad'}.
          </p>
          <HeroSearch className="max-w-2xl" />
          <div className="flex max-w-2xl flex-wrap items-center gap-x-1 gap-y-1 text-sm">
            <span className="mr-2 text-ap-muted">Prueba con:</span>
            <ul className="contents">
              {SEARCH_EXAMPLES.map((term, index) => (
                <li key={term} className="flex items-center">
                  {index > 0 ? (
                    <span className="px-1 text-ap-steel" aria-hidden>
                      ·
                    </span>
                  ) : null}
                  <Link
                    to={searchUrl({ q: term })}
                    className="inline-flex min-h-11 items-center px-1 font-medium text-ap-ink underline decoration-ap-line-strong underline-offset-4 hover:text-ap-red hover:decoration-ap-red"
                  >
                    {term}
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        </div>
        <CoverageMap />
      </Container>
    </section>
  )
}
