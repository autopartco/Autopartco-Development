import { createElement } from 'react'
import { Link } from 'react-router'
import { ArrowRightIcon, ChevronRightIcon } from 'lucide-react'
import { CountUp } from '@/components/common/count-up'
import { Reveal } from '@/components/common/reveal'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { ServiceCategories } from '@/features/services/components/service-categories'
import { useServiceCategories } from '@/features/services/hooks'
import { SERVICES_PATH } from '@/features/services/lib/paths'
import { pluralize } from '@/lib/format'
import type { HomeCategory } from '../api/types'
import { SearchBox } from '../components/search/search-box'
import { categoryIcon } from '../home/category-icons'
import { useHome } from '../hooks'
import { humanizeCode } from '../lib/humanize-code'
import { categoryPath } from '../lib/paths'

const FEATURED_COUNT = 4
const NO_QUERY = {}

function Glyph({ code, className }: { code: string; className: string }) {
  return createElement(categoryIcon(code), { className, 'aria-hidden': true })
}

const linkFocus = 'focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-[var(--focus)]'

function Featured({ category }: { category: HomeCategory }) {
  return (
    <Reveal as="li" className="border-t-2 border-ap-graphite">
      <Link
        to={categoryPath(category.code.toLowerCase())}
        className={`group flex h-full min-h-36 flex-col justify-between gap-6 p-3 transition-colors hover:bg-ap-paper sm:p-4 ${linkFocus}`}
      >
        <span className="flex items-start justify-between">
          <Glyph code={category.code} className="size-7 text-ap-graphite group-hover:text-ap-red" />
          <ArrowRightIcon className="ap-nudge size-4 text-ap-steel group-hover:text-ap-graphite" aria-hidden />
        </span>
        <span className="flex flex-col gap-1">
          <span className="text-lg leading-snug font-semibold text-ap-ink underline-offset-4 group-hover:text-ap-red group-hover:underline">{humanizeCode(category.code)}</span>
          <span className="text-sm text-ap-muted tabular-nums">
            <strong className="font-semibold text-ap-ink"><CountUp value={category.productCount} /></strong>{' '}
            {category.productCount === 1 ? 'repuesto' : 'repuestos'}
          </span>
        </span>
      </Link>
    </Reveal>
  )
}

function Compact({ category }: { category: HomeCategory }) {
  return (
    <Reveal as="li" className="border-t border-ap-line">
      <Link
        to={categoryPath(category.code.toLowerCase())}
        className={`group flex min-h-14 items-center gap-3 px-1 py-3 transition-colors hover:bg-ap-paper sm:px-2 ${linkFocus}`}
      >
        <Glyph code={category.code} className="size-5 shrink-0 text-ap-steel group-hover:text-ap-red" />
        <span className="min-w-0 flex-1">
          <span className="block leading-snug font-semibold text-ap-ink underline-offset-4 group-hover:text-ap-red group-hover:underline">{humanizeCode(category.code)}</span>
          <span className="text-sm text-ap-muted tabular-nums">
            {category.productCount > 0 ? (
              <>
                <CountUp value={category.productCount} /> {category.productCount === 1 ? 'repuesto' : 'repuestos'}
              </>
            ) : (
              'Sin repuestos publicados'
            )}
          </span>
        </span>
        <ChevronRightIcon className="ap-nudge size-4 shrink-0 text-ap-steel group-hover:text-ap-red" aria-hidden />
      </Link>
    </Reveal>
  )
}

function CategoriesSkeleton() {
  return (
    <div>
      <span className="sr-only" role="status">
        Cargando categorías
      </span>
      <ul className="grid grid-cols-2 gap-x-4 lg:grid-cols-4" aria-hidden>
        {Array.from({ length: 4 }, (_, index) => (
          <li key={index} className="flex min-h-36 flex-col justify-between border-t-2 border-ap-graphite p-3 sm:p-4">
            <Skeleton className="size-7" />
            <span className="flex flex-col gap-2">
              <Skeleton className="h-5 w-3/4" />
              <Skeleton className="h-4 w-1/2" />
            </span>
          </li>
        ))}
      </ul>
      <ul className="mt-10 grid grid-cols-2 gap-x-4 lg:grid-cols-4" aria-hidden>
        {Array.from({ length: 8 }, (_, index) => (
          <li key={index} className="flex flex-col gap-2 border-t border-ap-line py-4">
            <Skeleton className="h-5 w-3/4" />
            <Skeleton className="h-4 w-1/2" />
          </li>
        ))}
      </ul>
    </div>
  )
}

/**
 * Índice de categorías de repuestos. Nombres, enlaces y conteos salen de `GET /marketplace/home` (una sola llamada, ya
 * usada por el inicio): las 4 con más repuestos van destacadas y el resto en índice compacto por nombre. Debajo, los
 * servicios para el vehículo y una salida a la búsqueda. Sin conteo no se inventa ninguno.
 */
export default function CategoriesPage() {
  const home = useHome(NO_QUERY)
  const services = useServiceCategories()
  const categories = home.data?.categories

  const featured =
    categories
      ?.filter((item) => item.productCount > 0)
      .toSorted((a, b) => b.productCount - a.productCount || a.code.localeCompare(b.code, 'es'))
      .slice(0, FEATURED_COUNT) ?? []
  const featuredIds = new Set(featured.map((item) => item.id))
  const rest =
    categories
      ?.filter((item) => !featuredIds.has(item.id))
      .toSorted((a, b) => humanizeCode(a.code).localeCompare(humanizeCode(b.code), 'es')) ?? []
  const total = categories?.reduce((sum, item) => sum + item.productCount, 0)

  return (
    <Container className="py-6 sm:py-8">
      <title>Categorías de repuestos | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Categorías' }]}
        label={categories ? `Catálogo / ${pluralize(categories.length, 'categoría', 'categorías')}` : 'Catálogo / Categorías'}
        title="Categorías de repuestos"
        description={
          <p>
            Elige el sistema que vas a reparar.
            {total ? ` Hay ${pluralize(total, 'repuesto publicado', 'repuestos publicados')} por almacenes verificados.` : null}
          </p>
        }
      />

      {home.status === 'error' ? (
        <ErrorState title="No pudimos cargar las categorías" onRetry={home.retry} />
      ) : !categories ? (
        <CategoriesSkeleton />
      ) : categories.length === 0 ? (
        <EmptyState
          title="Aún no hay categorías"
          description="Cuando las tiendas publiquen repuestos, las verás aquí. Mientras tanto, busca por nombre o referencia."
        >
          <Link to="/buscar" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
            Ver el catálogo
          </Link>
        </EmptyState>
      ) : (
        <div className="flex flex-col gap-10">
          {featured.length > 0 ? (
            <section aria-labelledby="cat-destacadas">
              <h2 id="cat-destacadas" className="ap-index mb-3">
                Más repuestos publicados
              </h2>
              <ul className="grid grid-cols-2 gap-x-4 lg:grid-cols-4">
                {featured.map((category) => (
                  <Featured key={category.id} category={category} />
                ))}
              </ul>
            </section>
          ) : null}
          {rest.length > 0 ? (
            <section aria-labelledby="cat-todas">
              <h2 id="cat-todas" className="ap-index mb-3">
                {featured.length > 0 ? 'Más categorías' : 'Todas las categorías'}
              </h2>
              <ul className="grid grid-cols-2 gap-x-4 border-b border-ap-line sm:gap-x-6 lg:grid-cols-4">
                {rest.map((category) => (
                  <Compact key={category.id} category={category} />
                ))}
              </ul>
            </section>
          ) : null}
        </div>
      )}

      <section aria-labelledby="cat-servicios" className="mt-12">
        <div className="mb-3 flex flex-wrap items-end justify-between gap-x-4 gap-y-1 border-b border-ap-graphite pb-2">
          <h2 id="cat-servicios" className="text-xl font-semibold text-ap-ink">
            Servicios para tu vehículo
          </h2>
          <Link
            to={SERVICES_PATH}
            className="inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
          >
            Ver todos los servicios
            <ArrowRightIcon className="size-4" aria-hidden />
          </Link>
        </div>
        <ServiceCategories
          categories={services.data}
          failed={services.status === 'error'}
          onRetry={services.retry}
          label="Categorías de servicio"
        />
      </section>

      <section
        aria-labelledby="cat-buscar"
        className="mt-12 grid gap-3 border-t border-ap-graphite pt-6 md:grid-cols-[minmax(0,1fr)_minmax(0,28rem)] md:items-center md:gap-8"
      >
        <div>
          <h2 id="cat-buscar" className="text-lg font-semibold text-ap-ink">
            ¿No sabes la categoría?
          </h2>
          <p className="text-sm text-ap-muted">Busca por referencia, nombre de la pieza o vehículo.</p>
        </div>
        <SearchBox />
      </section>
    </Container>
  )
}
