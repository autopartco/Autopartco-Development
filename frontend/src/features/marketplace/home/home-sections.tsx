import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { ChevronRightIcon } from 'lucide-react'
import { Container } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { useDistanceOrigin, useStoreDistances } from '@/features/location/hooks'
import { pluralize } from '@/lib/format'
import { ProductCard, ProductGrid, ProductGridSkeleton } from '../components/product-card'
import { useCategories, useProductSearch } from '../hooks'
import { categoryPath } from '../lib/paths'
import { emptyQuery } from '../lib/product-query'
import { HowItWorksSection } from './how-it-works'
import { ComparisonSection } from './price-comparison'

/**
 * Bloques de la portada bajo el buscador, con ritmos distintos: índice denso de
 * categorías, vitrina de repuestos publicados, tabla comparativa y pasos.
 */

const FEATURED_COUNT = 8
const FEATURED_QUERY = emptyQuery({ pageSize: FEATURED_COUNT })

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

/** Índice de categorías: filas con divisor en columnas, nombre y número de repuestos. */
function CategoriesSection() {
  const categories = useCategories()
  const total = categories.data?.length
  return (
    <section aria-labelledby="categorias-titulo" className="py-14 sm:py-20">
      <Container>
        <SectionHeading
          id="categorias-titulo"
          label={total !== undefined ? `Categorías / ${pluralize(total, 'sistema', 'sistemas')}` : 'Categorías'}
          title="Busca por sistema del carro"
          link={{ to: '/categorias', label: 'Ver todas las categorías' }}
        />
        {categories.status === 'error' ? (
          <ErrorState title="No pudimos cargar las categorías" onRetry={categories.retry} />
        ) : !categories.data ? (
          <ul className="grid gap-x-8 sm:grid-cols-2 lg:grid-cols-4" aria-hidden>
            {Array.from({ length: 8 }, (_, index) => (
              <li key={index} className="border-t border-ap-line py-4">
                <Skeleton className="h-5 w-2/3" />
              </li>
            ))}
          </ul>
        ) : categories.data.length === 0 ? (
          <EmptyState
            title="Aún no hay categorías publicadas"
            description="Mientras tanto, busca tu repuesto por nombre o referencia en el buscador de arriba."
          />
        ) : (
          <ul className="grid gap-x-8 border-b border-ap-line sm:grid-cols-2 lg:grid-cols-4">
            {categories.data.map((category) => (
              <li key={category.id} className="border-t border-ap-line">
                <Link
                  to={categoryPath(category.slug)}
                  className="group flex min-h-14 items-center justify-between gap-3 py-3 transition-colors hover:text-ap-red"
                >
                  <span className="min-w-0">
                    <span className="block font-semibold text-ap-ink group-hover:text-ap-red">{category.name}</span>
                    {category.productCount !== undefined ? (
                      <span className="text-sm text-ap-muted tabular-nums">
                        {pluralize(category.productCount, 'repuesto', 'repuestos')}
                      </span>
                    ) : null}
                  </span>
                  <ChevronRightIcon className="size-4 shrink-0 text-ap-steel group-hover:text-ap-red" aria-hidden />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Container>
    </section>
  )
}

/** Repuestos publicados: prueba de catálogo real, en vitrina compacta con foto. */
function FeaturedSection() {
  const result = useProductSearch(FEATURED_QUERY)
  const distances = useStoreDistances()
  const place = useDistanceOrigin()
  const items = result.data?.items.slice(0, FEATURED_COUNT)
  return (
    <section aria-labelledby="destacados-titulo" className="border-t border-ap-line py-14 sm:py-20">
      <Container>
        <SectionHeading
          id="destacados-titulo"
          label={result.data ? `Catálogo / ${pluralize(result.data.total, 'repuesto publicado', 'repuestos publicados')}` : 'Catálogo'}
          title="Repuestos publicados por los almacenes"
          intro={
            distances.size > 0 && place
              ? `Cada pieza indica a qué distancia de ${place} está la sede más cercana de la tienda.`
              : undefined
          }
          link={{ to: '/buscar', label: 'Ver todo el catálogo' }}
        />
        {result.status === 'error' ? (
          <ErrorState title="No pudimos cargar los repuestos" onRetry={result.retry} />
        ) : items ? (
          items.length > 0 ? (
            <ProductGrid className="lg:grid-cols-4">
              {items.map((product) => (
                <ProductCard
                  key={product.listingKey}
                  product={product}
                  distanceKm={product.store ? distances.get(product.store.id) : undefined}
                />
              ))}
            </ProductGrid>
          ) : (
            <EmptyState
              title="Aún no hay repuestos publicados"
              description="Los almacenes están cargando su inventario. Mira las tiendas registradas mientras tanto."
            >
              <Link to="/tiendas" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
                Ver tiendas
              </Link>
            </EmptyState>
          )
        ) : (
          <ProductGridSkeleton count={FEATURED_COUNT} className="lg:grid-cols-4" />
        )}
      </Container>
    </section>
  )
}

export function HomeSections() {
  return (
    <>
      <CategoriesSection />
      <FeaturedSection />
      <div className="border-t border-ap-line">
        <ComparisonSection />
      </div>
      <HowItWorksSection />
    </>
  )
}
