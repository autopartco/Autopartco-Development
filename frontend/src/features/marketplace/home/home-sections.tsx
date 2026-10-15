import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Container } from '@/components/common/page-header'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { useNearbyStores, useStoreDistances } from '@/features/location/hooks'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { CategoryIcon } from '../components/category-icon'
import { ProductCard, ProductGrid, ProductGridSkeleton } from '../components/product-card'
import { useCategories, useProductSearch } from '../hooks'
import { categoryPath } from '../lib/paths'
import { emptyQuery } from '../lib/product-query'
import { HowItWorksSection } from './how-it-works'
import { PassportSection } from './part-passport'
import { ComparisonSection } from './price-comparison'

/**
 * Secciones bajo el pliegue de la página de inicio. Se descargan aparte (lazy)
 * y las más largas usan `content-visibility` para no pintar lo que no se ve.
 */

const FEATURED_COUNT = 8
const FEATURED_QUERY = emptyQuery({ pageSize: FEATURED_COUNT })
const OFFSCREEN = '[content-visibility:auto] [contain-intrinsic-size:auto_56rem]'

interface SectionHeadingProps {
  id: string
  /** Etiqueta mono con un dato real de la sección (no decoración). */
  label?: ReactNode
  title: string
  intro?: ReactNode
  link?: { to: string; label: string }
}

function SectionHeading({ id, label, title, intro, link }: SectionHeadingProps) {
  return (
    <div className="mb-10 flex flex-col gap-4 md:mb-12 md:flex-row md:items-end md:justify-between md:gap-10">
      <div className="flex max-w-2xl flex-col gap-4">
        {label ? <p className="label-mono text-muted-foreground tabular-nums">{label}</p> : null}
        <h2 id={id} className="text-section">
          {title}
        </h2>
        {intro ? <p className="text-base text-muted-foreground sm:text-lg">{intro}</p> : null}
      </div>
      {link ? (
        <Link
          to={link.to}
          className="inline-flex min-h-11 shrink-0 items-center font-medium text-brand underline-offset-4 hover:underline"
        >
          {link.label}
        </Link>
      ) : null}
    </div>
  )
}

function FeaturedSection() {
  const result = useProductSearch(FEATURED_QUERY)
  const distances = useStoreDistances()
  const place = useNearbyStores().data?.origin?.label
  const items = result.data?.items.slice(0, FEATURED_COUNT)
  return (
    <section aria-labelledby="destacados-titulo" className={cn('border-y border-border bg-card py-20 sm:py-28', OFFSCREEN)}>
      <Container>
        <SectionHeading
          id="destacados-titulo"
          label={result.data ? `${pluralize(result.data.total, 'repuesto publicado', 'repuestos publicados')}` : 'Catálogo'}
          title="Repuestos destacados"
          intro={
            distances.size > 0 && place
              ? `Cada pieza indica a cuántos kilómetros de ${place} está la sede más cercana de la tienda.`
              : 'Piezas publicadas hoy por los almacenes de Autopartco.'
          }
          link={{ to: '/buscar', label: 'Ver todos los repuestos' }}
        />
        {result.status === 'error' ? (
          <ErrorState title="No pudimos cargar los repuestos destacados" onRetry={result.retry} />
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
            <p className="text-muted-foreground">Aún no hay repuestos publicados. Vuelve pronto.</p>
          )
        ) : (
          <ProductGridSkeleton count={FEATURED_COUNT} className="lg:grid-cols-4" />
        )}
      </Container>
    </section>
  )
}

function CategoriesSection() {
  const categories = useCategories()
  return (
    <section aria-labelledby="categorias-titulo" className={cn('border-y border-border bg-card py-20 sm:py-28', OFFSCREEN)}>
      <Container>
        <SectionHeading
          id="categorias-titulo"
          label={categories.data ? pluralize(categories.data.length, 'categoría', 'categorías') : 'Categorías'}
          title="Busca por sistema del carro"
          link={{ to: '/categorias', label: 'Ver todas las categorías' }}
        />
        {categories.status === 'error' ? (
          <ErrorState title="No pudimos cargar las categorías" onRetry={categories.retry} />
        ) : (
          <ul className="grid grid-cols-1 gap-2 min-[420px]:grid-cols-2 lg:grid-cols-4">
            {categories.data
              ? categories.data.map((category) => (
                  <li key={category.id}>
                    <Link
                      to={categoryPath(category.slug)}
                      className="group flex h-full min-h-16 items-center gap-3 rounded-xl bg-paper p-3 shadow-[0_0_0_1px_rgb(0_0_0/0.05)] transition-shadow hover:shadow-[0_0_0_1px_rgb(200_30_44/0.45)] sm:p-4"
                    >
                      <span className="grid size-10 shrink-0 place-items-center rounded-lg bg-concrete text-brand transition-colors group-hover:bg-brand group-hover:text-primary-foreground">
                        <CategoryIcon slug={category.slug} className="size-5" />
                      </span>
                      <span className="min-w-0">
                        <span className="block leading-tight font-semibold">{category.name}</span>
                        {category.productCount !== undefined ? (
                          <span className="text-sm text-muted-foreground tabular-nums">
                            {pluralize(category.productCount, 'repuesto', 'repuestos')}
                          </span>
                        ) : null}
                      </span>
                    </Link>
                  </li>
                ))
              : Array.from({ length: 8 }, (_, index) => (
                  <li key={index} aria-hidden>
                    <Skeleton className="h-[4.5rem] rounded-xl" />
                  </li>
                ))}
          </ul>
        )}
      </Container>
    </section>
  )
}

export default function HomeSections() {
  return (
    <>
      {/* Orden del recorrido: la ficha de una pieza, la vitrina, la comparación, los sistemas y cómo funciona. */}
      <PassportSection />
      <FeaturedSection />
      <ComparisonSection />
      <CategoriesSection />
      <HowItWorksSection />
    </>
  )
}
