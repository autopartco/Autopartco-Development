import { Link } from 'react-router'
import { Container, PageIntro } from '@/components/common/page-header'
import { SmartImage } from '@/components/common/smart-image'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { pluralize } from '@/lib/format'
import { PLACEHOLDER_IMAGES } from '@/lib/images'
import { CategoryIcon } from '../components/category-icon'
import { useCategories } from '../hooks'
import { categoryPath } from '../lib/paths'

export default function CategoriesPage() {
  const categories = useCategories()
  return (
    <Container className="py-6 sm:py-8">
      <title>Categorías de repuestos | Autopartco</title>
      <PageIntro
        crumbs={[{ label: 'Inicio', to: '/' }, { label: 'Categorías' }]}
        label={categories.data ? pluralize(categories.data.length, 'sistema del carro', 'sistemas del carro') : 'Sistemas del carro'}
        title="Categorías de repuestos"
        description={<p>Elige el sistema que vas a reparar para ver los repuestos que publican los almacenes.</p>}
      />

      {categories.status === 'error' ? (
        <ErrorState title="No pudimos cargar las categorías" onRetry={categories.retry} />
      ) : !categories.data ? (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4" aria-hidden>
          {Array.from({ length: 8 }, (_, index) => (
            <li key={index} className="flex flex-col gap-3">
              <Skeleton className="aspect-[16/10] rounded-lg" />
              <Skeleton className="h-5 w-2/3" />
              <Skeleton className="h-4 w-full" />
            </li>
          ))}
        </ul>
      ) : categories.data.length === 0 ? (
        <EmptyState title="Aún no hay categorías" description="Cuando las tiendas publiquen repuestos, las verás aquí." />
      ) : (
        <ul className="grid gap-x-4 gap-y-8 sm:grid-cols-2 lg:grid-cols-4">
          {categories.data.map((category) => (
            <li key={category.id}>
              <article className="group relative flex flex-col gap-3">
                <div className="relative">
                  <SmartImage
                    src={PLACEHOLDER_IMAGES.category}
                    alt=""
                    width={400}
                    height={250}
                    frameClassName="aspect-[16/10] rounded-lg outline outline-1 -outline-offset-1 outline-black/10"
                    className="transition-transform duration-300 ease-out-snap group-hover:scale-[1.03]"
                  />
                  <span className="absolute bottom-3 left-3 grid size-10 place-items-center rounded-md bg-background text-brand shadow-sm">
                    <CategoryIcon slug={category.slug} className="size-5" />
                  </span>
                </div>
                <div className="flex flex-col gap-1">
                  <h2 className="text-xl leading-tight font-extrabold tracking-tight">
                    <Link
                      to={categoryPath(category.slug)}
                      data-stretched-link
                      className="after:absolute after:inset-0 after:rounded-lg group-hover:text-brand focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[var(--focus)]"
                    >
                      {category.name}
                    </Link>
                  </h2>
                  {category.description ? <p className="text-sm text-muted-foreground">{category.description}</p> : null}
                  {category.productCount !== undefined ? (
                    <p className="label-mono text-muted-foreground tabular-nums">
                      {pluralize(category.productCount, 'repuesto', 'repuestos')}
                    </p>
                  ) : null}
                </div>
              </article>
            </li>
          ))}
        </ul>
      )}
    </Container>
  )
}
