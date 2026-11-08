import { Link } from 'react-router'
import { ChevronRightIcon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { pluralize } from '@/lib/format'
import { useCategories } from '../hooks'
import { categoryPath } from '../lib/paths'

/**
 * Índice de categorías: una fila por sistema del carro con descripción y número de
 * repuestos alineado a la derecha. Sin fotos genéricas repetidas (§9).
 */
export default function CategoriesPage() {
  const categories = useCategories()
  return (
    <Container className="py-6 sm:py-8">
      <title>Categorías de repuestos | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Categorías' }]}
        label={categories.data ? `Catálogo / ${pluralize(categories.data.length, 'categoría', 'categorías')}` : 'Catálogo / Categorías'}
        title="Categorías de repuestos"
        description={<p>Elige el sistema que vas a reparar para ver los repuestos que publican los almacenes.</p>}
      />

      {categories.status === 'error' ? (
        <ErrorState title="No pudimos cargar las categorías" onRetry={categories.retry} />
      ) : !categories.data ? (
        <ul className="grid border-t border-ap-graphite md:grid-cols-2 md:gap-x-10" aria-hidden>
          {Array.from({ length: 8 }, (_, index) => (
            <li key={index} className="flex flex-col gap-2 border-b border-ap-line py-4">
              <Skeleton className="h-5 w-1/3" />
              <Skeleton className="h-4 w-3/4" />
            </li>
          ))}
        </ul>
      ) : categories.data.length === 0 ? (
        <EmptyState
          title="Aún no hay categorías"
          description="Cuando las tiendas publiquen repuestos, las verás aquí. Mientras tanto, busca por nombre o referencia."
        >
          <Link to="/buscar" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
            Ver el catálogo
          </Link>
        </EmptyState>
      ) : (
        <ul className="grid border-t border-ap-graphite md:grid-cols-2 md:gap-x-10">
          {categories.data.map((category) => (
            <li key={category.id} className="border-b border-ap-line">
              <Link
                to={categoryPath(category.slug)}
                className="group grid min-h-16 grid-cols-[minmax(0,1fr)_auto_auto] items-center gap-x-4 py-4 transition-colors hover:bg-ap-paper sm:px-2"
              >
                <span className="flex min-w-0 flex-col gap-0.5">
                  <span className="font-semibold text-ap-ink group-hover:text-ap-red">{category.name}</span>
                  {category.description ? <span className="text-sm text-ap-muted">{category.description}</span> : null}
                </span>
                <span className="text-right text-sm text-ap-ink tabular-nums">
                  {category.productCount !== undefined ? pluralize(category.productCount, 'repuesto', 'repuestos') : null}
                </span>
                <ChevronRightIcon className="size-4 text-ap-steel group-hover:text-ap-red" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Container>
  )
}
