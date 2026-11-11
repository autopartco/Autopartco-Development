import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { BadgeCheckIcon, CheckIcon, ChevronRightIcon, MessageSquareTextIcon, ShieldCheckIcon } from 'lucide-react'
import { Container } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { MarketplaceError, type HomeData } from '../api/types'
import { pluralize } from '@/lib/format'
import type { AsyncResult } from '@/lib/use-async'
import { SponsoredListing } from '../components/ads/sponsored-listing'
import { ProductCard, ProductCardSkeleton, ProductGrid, ProductGridSkeleton } from '../components/product-card'
import { categoryPath } from '../lib/paths'
import { humanizeCode } from '../lib/humanize-code'
import { nearbyUrl } from '../lib/search-query'
import { categoryIcon } from './category-icons'

/** Bloques de la portada bajo el hero: acceso directo, destacados, qué es Autopartco y vendedores. */

type Home = AsyncResult<HomeData>

interface SectionHeadingProps {
  id: string
  /** Índice `ap-index` con contexto real de la sección. */
  label: string
  title: string
  intro?: ReactNode
  link?: { to: string; label: string }
}

function SectionHeading({ id, label, title, intro, link }: SectionHeadingProps) {
  return (
    <div className="mb-6 flex flex-col gap-3 sm:mb-8 md:flex-row md:items-end md:justify-between md:gap-10">
      <div className="flex max-w-2xl flex-col gap-2">
        <p className="ap-index">{label}</p>
        <h2 id={id} className="text-section text-ap-ink">
          {title}
        </h2>
        {intro ? <p className="text-ap-muted">{intro}</p> : null}
      </div>
      {link ? (
        <Link
          to={link.to}
          className="inline-flex min-h-11 shrink-0 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
        >
          {link.label}
        </Link>
      ) : null}
    </div>
  )
}

/** Mensaje de error del inicio según el código del servidor. */
function homeErrorCopy(error: unknown): { title: string; description?: string } {
  const code = error instanceof MarketplaceError ? error.code : undefined
  if (code === 'HOME_RATE_LIMITED' || (error instanceof MarketplaceError && error.status === 429)) {
    return { title: 'Estás navegando muy rápido', description: 'Espera unos segundos y vuelve a intentarlo.' }
  }
  if (code === 'HOME_POINT_OUTSIDE_COVERAGE' || code === 'HOME_POINT_INCOMPLETE' || code === 'HOME_CITY_INVALID') {
    return {
      title: 'No tenemos datos para esa zona',
      description: 'Elige otra ciudad en la cabecera o busca tu repuesto por nombre o referencia.',
    }
  }
  return { title: 'No pudimos cargar esta sección' }
}

function HomeError({ home }: { home: Home }) {
  return <ErrorState {...homeErrorCopy(home.error)} onRetry={home.retry} />
}

/* ------------------------------------------------------------------ */
/* 2. Acceso directo: categorías con ícono y «Cerca de ti»             */
/* ------------------------------------------------------------------ */

function CategoriesIndex({ home }: { home: Home }) {
  const categories = home.data?.categories
  if (home.status === 'error') return <HomeError home={home} />
  if (!categories) {
    return (
      <ul className="grid gap-x-8 sm:grid-cols-2 lg:grid-cols-4" aria-hidden>
        {Array.from({ length: 8 }, (_, index) => (
          <li key={index} className="border-t border-ap-line py-4">
            <Skeleton className="h-5 w-2/3" />
          </li>
        ))}
      </ul>
    )
  }
  if (categories.length === 0) {
    return (
      <EmptyState
        title="Aún no hay categorías publicadas"
        description="Mientras tanto, busca tu repuesto por nombre o referencia en el buscador de arriba."
      />
    )
  }
  return (
    <ul className="grid gap-x-8 border-b border-ap-line sm:grid-cols-2 lg:grid-cols-4">
      {categories.map((category) => {
        const Icon = categoryIcon(category.code)
        return (
          <li key={category.id} className="border-t border-ap-line">
            <Link
              to={categoryPath(category.code.toLowerCase())}
              className="group flex min-h-14 items-center gap-3 py-3 transition-colors hover:text-ap-red"
            >
              <Icon className="size-5 shrink-0 text-ap-steel group-hover:text-ap-red" aria-hidden />
              <span className="min-w-0 flex-1">
                <span className="block font-semibold text-ap-ink group-hover:text-ap-red">{humanizeCode(category.code)}</span>
                <span className="text-sm text-ap-muted tabular-nums">{pluralize(category.productCount, 'repuesto', 'repuestos')}</span>
              </span>
              <ChevronRightIcon className="size-4 shrink-0 text-ap-steel group-hover:text-ap-red" aria-hidden />
            </Link>
          </li>
        )
      })}
    </ul>
  )
}

/** Carrusel con ajuste por tarjeta; es una región con foco para recorrerla con las flechas. */
function NearbyCarousel({ home }: { home: Home }) {
  const nearby = home.data?.nearby
  if (home.status === 'error') return <HomeError home={home} />
  if (!nearby) {
    return (
      <div className="grid grid-cols-2 gap-4 md:grid-cols-4" aria-hidden>
        {Array.from({ length: 4 }, (_, index) => (
          <ProductCardSkeleton key={index} />
        ))}
      </div>
    )
  }
  if (nearby.length === 0) {
    return (
      <EmptyState
        title="Aún no hay repuestos con ubicación cerca"
        description="Elige tu ciudad en la cabecera o permite tu ubicación en la búsqueda para ordenar por cercanía."
      >
        <Link to={nearbyUrl()} className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Buscar cerca de mí
        </Link>
      </EmptyState>
    )
  }
  return (
    <div
      role="region"
      tabIndex={0}
      aria-label="Repuestos cerca de ti"
      className="-mx-4 flex snap-x snap-mandatory gap-4 overflow-x-auto px-4 pb-3 sm:-mx-6 sm:px-6 lg:mx-0 lg:px-0"
    >
      <ul className="contents">
        {nearby.map((product) => (
          <li key={product.listingKey} className="w-[15rem] shrink-0 snap-start sm:w-[16.5rem]">
            {product.sponsored ? (
              <SponsoredListing product={product} variant="card" />
            ) : (
              <ProductCard product={product} distanceKm={product.distanceKm} />
            )}
          </li>
        ))}
      </ul>
    </div>
  )
}

export function QuickAccessSection({ home }: { home: Home }) {
  const total = home.data?.categories.length
  return (
    <section aria-labelledby="acceso-titulo" className="py-14 sm:py-20">
      <Container className="flex flex-col gap-14">
        <div>
          <SectionHeading
            id="acceso-titulo"
            label={total !== undefined ? `Categorías / ${pluralize(total, 'sistema', 'sistemas')}` : 'Categorías'}
            title="Ve directo a lo que buscas"
            link={{ to: '/categorias', label: 'Ver todas las categorías' }}
          />
          <CategoriesIndex home={home} />
        </div>
        <div>
          <div className="mb-4 flex flex-wrap items-end justify-between gap-x-6 gap-y-1">
            <h3 className="text-block-title text-ap-ink">Cerca de ti</h3>
            <Link to={nearbyUrl()} className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
              Ver más cerca de ti
            </Link>
          </div>
          <NearbyCarousel home={home} />
        </div>
      </Container>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* 4. Destacados                                                       */
/* ------------------------------------------------------------------ */

export function FeaturedSection({ home }: { home: Home }) {
  const featured = home.data?.featured
  return (
    <section aria-labelledby="destacados-titulo" className="border-t border-ap-line py-14 sm:py-20">
      <Container>
        <SectionHeading
          id="destacados-titulo"
          label="Catálogo / Destacados"
          title="Repuestos destacados"
          intro="Los anuncios llevan la etiqueta «Patrocinado»; el resto son repuestos publicados por los almacenes."
          link={{ to: '/buscar', label: 'Ver todo el catálogo' }}
        />
        {home.status === 'error' ? (
          <HomeError home={home} />
        ) : !featured ? (
          <ProductGridSkeleton count={8} className="lg:grid-cols-4" />
        ) : featured.length === 0 ? (
          <EmptyState
            title="Aún no hay repuestos destacados"
            description="Los almacenes están cargando su inventario. Mira las tiendas registradas mientras tanto."
          >
            <Link to="/tiendas" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
              Ver tiendas
            </Link>
          </EmptyState>
        ) : (
          <ProductGrid className="lg:grid-cols-4">
            {featured.map((product, index) =>
              product.sponsored ? (
                <SponsoredListing key={`${product.listingKey}:${index}`} product={product} variant="card" />
              ) : (
                <ProductCard key={`${product.listingKey}:${index}`} product={product} distanceKm={product.distanceKm} />
              ),
            )}
          </ProductGrid>
        )}
      </Container>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* 5. Qué es Autopartco                                                */
/* ------------------------------------------------------------------ */

const GUARANTEES = [
  {
    icon: ShieldCheckIcon,
    title: 'Pago seguro con Wompi',
    body: 'Los pagos en línea se procesan con Wompi y el estado de cada pago lo confirma el servidor.',
  },
  {
    icon: BadgeCheckIcon,
    title: 'Almacenes verificados',
    body: 'Revisamos los documentos de cada almacén antes de publicar su inventario.',
  },
  {
    icon: MessageSquareTextIcon,
    title: 'Reseñas de compradores',
    body: 'Solo quien recibió un pedido puede calificar al almacén; así las reseñas vienen de compras reales.',
  },
] as const

function PlatformFigures({ home }: { home: Home }) {
  if (home.status === 'error') return <HomeError home={home} />
  if (!home.data) {
    return (
      <div aria-hidden className="grid grid-cols-3 gap-4 border-y border-ap-line py-5">
        {[0, 1, 2].map((item) => (
          <Skeleton key={item} className="h-12 w-full" />
        ))}
      </div>
    )
  }
  const { stores, cities, products } = home.data.stats
  const figures = [
    { label: stores === 1 ? 'almacén' : 'almacenes', value: stores },
    { label: cities === 1 ? 'ciudad' : 'ciudades', value: cities },
    { label: products === 1 ? 'repuesto' : 'repuestos', value: products },
  ]
  return (
    <dl className="grid grid-cols-3 gap-4 border-y border-ap-line py-5">
      {figures.map((figure) => (
        <div key={figure.label} className="flex flex-col-reverse gap-1">
          <dt className="text-sm text-ap-muted">{figure.label}</dt>
          <dd className="ap-price text-2xl text-ap-ink tabular-nums sm:text-3xl">{new Intl.NumberFormat('es-CO').format(figure.value)}</dd>
        </div>
      ))}
    </dl>
  )
}

export function AboutSection({ home }: { home: Home }) {
  return (
    <section aria-labelledby="que-es-titulo" className="border-t border-ap-line bg-ap-paper py-14 sm:py-20">
      <Container className="grid gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-14">
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-2">
            <p className="ap-index">Plataforma / Colombia</p>
            <h2 id="que-es-titulo" className="text-section text-ap-ink">
              Qué es Autopartco
            </h2>
          </div>
          <p className="max-w-prose text-ap-muted">
            Autopartco reúne el inventario de almacenes de repuestos de Colombia en un solo lugar, para que compares precio,
            existencias y distancia antes de comprar o de ir al mostrador.
          </p>
          <PlatformFigures home={home} />
        </div>
        <ul className="grid gap-x-8 gap-y-6 self-start sm:grid-cols-3 lg:grid-cols-1 lg:gap-y-0">
          {GUARANTEES.map((item) => (
            <li key={item.title} className="flex gap-4 border-t border-ap-graphite py-5">
              <item.icon className="mt-0.5 size-5 shrink-0 text-ap-graphite" aria-hidden />
              <div className="flex flex-col gap-1">
                <h3 className="text-base font-semibold text-ap-ink">{item.title}</h3>
                <p className="text-sm text-ap-muted">{item.body}</p>
              </div>
            </li>
          ))}
        </ul>
      </Container>
    </section>
  )
}

/* ------------------------------------------------------------------ */
/* 6. Para vendedores                                                  */
/* ------------------------------------------------------------------ */

const SELLER_BENEFITS = [
  'Tu inventario aparece ante conductores y talleres que buscan repuestos en tu ciudad.',
  'Empiezas con una prueba gratuita y eliges tu plan después de la aprobación.',
  'Recibe pedidos y pagos en línea, y consulta tus ventas e inventario desde un solo portal.',
  'Destaca tus productos con campañas de publicidad y mide sus resultados.',
] as const

export function SellersSection({ home }: { home: Home }) {
  const stats = home.data?.stats
  return (
    <section aria-labelledby="vendedores-titulo" className="border-t border-ap-line py-14 sm:py-20">
      <Container className="grid gap-10 lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] lg:gap-14">
        <div className="flex flex-col gap-5">
          <div className="flex flex-col gap-2">
            <p className="ap-index">Para vendedores / Almacenes de repuestos</p>
            <h2 id="vendedores-titulo" className="text-section text-ap-ink">
              Vende tus repuestos a quienes ya los buscan
            </h2>
            {stats && stats.stores > 0 ? (
              <p className="text-ap-muted">
                Ya publican {pluralize(stats.stores, 'almacén', 'almacenes')} en {pluralize(stats.cities, 'ciudad', 'ciudades')}.
              </p>
            ) : null}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button size="lg" nativeButton={false} render={<Link to="/vender" />}>
              Registrar mi almacén
            </Button>
            <Button size="lg" variant="outline" nativeButton={false} render={<Link to="/tiendas" />}>
              Ver almacenes que ya publican
            </Button>
          </div>
        </div>
        <ul className="flex flex-col self-start">
          {SELLER_BENEFITS.map((benefit) => (
            <li key={benefit} className="flex items-start gap-3 border-t border-ap-graphite py-4 text-ap-ink">
              <CheckIcon className="mt-1 size-4 shrink-0 text-ap-red" aria-hidden />
              {benefit}
            </li>
          ))}
        </ul>
      </Container>
    </section>
  )
}
