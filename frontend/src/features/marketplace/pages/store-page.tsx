import { useEffect, useId, useState, type ReactNode } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router'
import { Breadcrumbs, Container } from '@/components/common/page-header'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useSession } from '@/features/auth/hooks'
import { ReviewDialog } from '@/features/reviews/components/review-dialog'
import { useStoreDistances } from '@/features/location/hooks'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import { trackView } from '@/lib/visibility'
import { isNotFoundError, type StoreBranch, type StoreProfile } from '../api/types'
import { ProductCard, ProductGrid, ProductGridSkeleton } from '../components/product-card'
import { ResultsPagination } from '../components/results-pagination'
import { BranchesPanel } from '../components/store/branches-panel'
import { ServicesList } from '../components/store/services-list'
import { ReviewsSection } from '../components/reviews/reviews-section'
import { StoreFacts } from '../components/store/store-facts'
import { StoreHeader } from '../components/store/store-header'
import { useProductSearch, useReviewEligibility, useStoreBranches, useStoreProfile } from '../hooks'
import { storePath, storeProductsPath } from '../lib/paths'
import { PAGE_SIZE, parseProductQuery, QUERY_PARAMS } from '../lib/product-query'
import { NotFoundContent } from './not-found-page'

/** Hasta 6 sedes se filtran con botones; con más, un selector (no desborda en 360 px). */
const MAX_BRANCH_CHIPS = 6

function BranchFilter({ branches, value, onChange }: { branches: StoreBranch[]; value: string | undefined; onChange: (branchId: string | undefined) => void }) {
  const selectId = useId()
  if (branches.length > MAX_BRANCH_CHIPS) {
    return (
      <div className="flex flex-col gap-1.5">
        <label htmlFor={selectId} className="text-sm font-semibold text-ap-ink">
          Ver repuestos de la sede
        </label>
        <select
          id={selectId}
          value={value ?? ''}
          onChange={(event) => onChange(event.target.value || undefined)}
          className="h-11 w-full max-w-sm rounded-md border border-input bg-ap-white px-3 text-base text-ap-ink sm:text-sm"
        >
          <option value="">Todas las sedes</option>
          {branches.map((branch) => (
            <option key={branch.id} value={branch.id}>
              {branch.name}
            </option>
          ))}
        </select>
      </div>
    )
  }
  const chip = (selected: boolean) =>
    cn(
      'inline-flex min-h-11 items-center rounded-md border px-3 text-sm font-medium transition-colors',
      selected ? 'border-ap-graphite bg-ap-graphite text-ap-white' : 'border-input bg-ap-white text-ap-ink hover:border-ap-graphite hover:bg-ap-paper',
    )
  return (
    <div role="group" aria-label="Filtrar repuestos por sede" className="flex flex-wrap gap-2">
      <button type="button" aria-pressed={!value} className={chip(!value)} onClick={() => onChange(undefined)}>
        Todas las sedes
      </button>
      {branches.map((branch) => (
        <button key={branch.id} type="button" aria-pressed={value === branch.id} className={chip(value === branch.id)} onClick={() => onChange(branch.id)}>
          {branch.name}
        </button>
      ))}
    </div>
  )
}

function StoreProducts({ store, branches }: { store: StoreProfile; branches: StoreBranch[] }) {
  const [searchParams, setSearchParams] = useSearchParams()
  const parsed = parseProductQuery(searchParams)
  const page = parsed.page
  // Un `?sucursal=` que no es de esta tienda se ignora (el servidor respondería 400).
  const branchId = branches.some((branch) => branch.id === parsed.branch) ? parsed.branch : undefined
  const query = { ...parseProductQuery(new URLSearchParams()), store: store.id, branch: branchId, page, pageSize: PAGE_SIZE }
  const results = useProductSearch(query)
  const distances = useStoreDistances()

  function selectBranch(next: string | undefined) {
    setSearchParams(
      (current) => {
        const params = new URLSearchParams(current)
        params.delete(QUERY_PARAMS.page)
        if (next) params.set(QUERY_PARAMS.branch, next)
        else params.delete(QUERY_PARAMS.branch)
        return params
      },
      { replace: true, preventScrollReset: true },
    )
  }

  let content: ReactNode
  if (results.status === 'error') {
    content = <ErrorState title="No pudimos cargar los repuestos de esta tienda" onRetry={results.retry} />
  } else if (!results.data) {
    content = (
      <>
        <span className="sr-only" role="status">
          Cargando repuestos
        </span>
        <ProductGridSkeleton count={6} className="lg:grid-cols-4" />
      </>
    )
  } else if (results.data.items.length === 0) {
    content = branchId ? (
      <EmptyState
        title="Esta sede no tiene repuestos publicados"
        description="Prueba con otra sede o mira todo el catálogo de la tienda."
      >
        <Button variant="outline" onClick={() => selectBranch(undefined)}>
          Ver todas las sedes
        </Button>
      </EmptyState>
    ) : (
      <EmptyState
        title="Esta tienda aún no tiene repuestos disponibles"
        description="Cuando publique repuestos con existencias aparecerán aquí. Mientras tanto, busca la pieza en otras tiendas."
      >
        <Link to="/buscar" className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Buscar en todas las tiendas
        </Link>
      </EmptyState>
    )
  } else {
    content = (
      <>
        <ProductGrid className="lg:grid-cols-4" busy={results.isStale}>
          {results.data.items.map((product) => (
            <ProductCard key={product.listingKey} product={product} distanceKm={product.store ? distances.get(product.store.id) : undefined} />
          ))}
        </ProductGrid>
        <ResultsPagination page={results.data.page} totalPages={results.data.totalPages} />
      </>
    )
  }

  return (
    <section aria-labelledby="repuestos" className="flex scroll-mt-24 flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <p className="ap-index">Tienda / Repuestos</p>
          <h2 id="repuestos" className="text-section text-ap-ink">
            Repuestos publicados
          </h2>
          {results.data ? (
            <p className="text-sm text-ap-muted tabular-nums">{pluralize(results.data.total, 'repuesto disponible', 'repuestos disponibles')}</p>
          ) : null}
        </div>
        <Link
          to={storeProductsPath(store.id)}
          className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
        >
          Buscar en esta tienda con filtros
        </Link>
      </div>
      {branches.length > 1 ? <BranchFilter branches={branches} value={branchId} onChange={selectBranch} /> : null}
      {content}
    </section>
  )
}

/** Invitación a calificar al almacén: abre el diálogo de reseña con los pedidos entregados sin calificar. */
function RateStoreAction({ store, orderIds }: { store: StoreProfile; orderIds: string[] }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="flex flex-col items-start gap-2 rounded-xl border border-ap-line-strong bg-ap-white p-4 sm:flex-row sm:items-center sm:justify-between">
      <p className="text-sm text-ap-ink">Recibiste un pedido de esta tienda. Cuéntanos cómo te fue; tu opinión ayuda a otros compradores.</p>
      <Button className="shrink-0" onClick={() => setOpen(true)}>
        Califica a {store.name}
      </Button>
      {open ? (
        <ReviewDialog
          open
          onOpenChange={setOpen}
          target={{ type: 'store', id: store.id }}
          targetName={store.name}
          orders={orderIds.map((id) => ({ id, label: `Pedido ${id.slice(0, 8).toUpperCase()}` }))}
        />
      ) : null}
    </div>
  )
}

/**
 * «Lo que dicen sus clientes»: resumen, categorías, insignias y lista, más la invitación a calificar para quien
 * recibió un pedido del almacén.
 */
function StoreReviews({ store }: { store: StoreProfile }) {
  const session = useSession()
  const location = useLocation()
  const eligibility = useReviewEligibility(store.id, session?.user.email)
  const eligibleOrders = eligibility.data ?? []

  return (
    <ReviewsSection
      target={{ type: 'store', id: store.id }}
      id="resenas"
      index="Tienda / Opiniones"
      title="Lo que dicen sus clientes"
      storeName={store.name}
      emptyText="Las opiniones las publican compradores que ya recibieron un pedido de esta tienda, para que otros compren con confianza."
      emptyExtra={
        !session ? (
          <p className="text-sm text-ap-muted">
            ¿Ya le compraste?{' '}
            <Link to="/ingresar" state={{ from: location }} className="font-semibold text-ap-red underline-offset-4 hover:underline">
              Ingresa para calificarla
            </Link>
          </p>
        ) : null
      }
      action={eligibleOrders.length > 0 ? <RateStoreAction store={store} orderIds={eligibleOrders} /> : null}
    />
  )
}

function AboutSection({ store }: { store: StoreProfile }) {
  if (!store.description) return null
  return (
    <section aria-labelledby="sobre-titulo" className="flex flex-col gap-3">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Tienda / Perfil</p>
        <h2 id="sobre-titulo" className="text-block-title text-ap-ink">
          Sobre la tienda
        </h2>
      </div>
      <p className="max-w-prose whitespace-pre-line text-ap-ink">{store.description}</p>
    </section>
  )
}

function ServicesSection({ store, branches, wide }: { store: StoreProfile; branches: StoreBranch[]; wide: boolean }) {
  if (store.services.length === 0) return null
  return (
    <section id="servicios" aria-labelledby="servicios-titulo" className="flex scroll-mt-24 flex-col gap-4">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Tienda / Servicios</p>
        <h2 id="servicios-titulo" className="text-block-title text-ap-ink">
          Servicios que ofrece
        </h2>
        <p className="text-sm text-ap-muted">Los precios son de referencia; confirma el valor final con la tienda.</p>
      </div>
      <ServicesList services={store.services} branches={branches} wide={wide} />
    </section>
  )
}

function BranchesSkeleton() {
  return (
    <div aria-hidden className="flex flex-col gap-4">
      <Skeleton className="h-6 w-40" />
      <Skeleton className="aspect-[4/3] w-full rounded-lg" />
      <Skeleton className="h-24 w-full" />
    </div>
  )
}

function StoreDetail({ store }: { store: StoreProfile }) {
  useEffect(() => {
    trackView('PROFILE_VIEW', store.id)
  }, [store.id])

  const branchesResult = useStoreBranches(store.id)
  const branches = branchesResult.data ?? []
  const loadingBranches = !branchesResult.data && branchesResult.status !== 'error' && store.branchCount > 0
  const hasBranches = branches.length > 0
  const hasLeft = !!store.description || store.services.length > 0
  const hasRight = hasBranches || loadingBranches
  // Una sola columna con contenido: ocupa todo el ancho; con las dos, el mapa queda fijo a la derecha.
  const twoColumns = hasLeft && hasRight

  return (
    <>
      <title>{`${store.name} | Tiendas Autopartco`}</title>
      <StoreHeader
        store={store}
        branches={branches}
        crumbs={<Breadcrumbs items={[{ label: 'Inicio', to: '/' }, { label: 'Tiendas', to: '/tiendas' }, { label: store.name }]} />}
      />
      <Container className="flex flex-col gap-10 pt-6 pb-8 sm:gap-12 sm:pb-12">
        <StoreFacts store={store} branches={branches} />

        {hasLeft || hasRight ? (
          <div className={cn('grid gap-10', twoColumns && 'lg:grid-cols-[minmax(0,1fr)_minmax(0,26rem)] lg:gap-12')}>
            {hasLeft ? (
              <div className="flex min-w-0 flex-col gap-10">
                <AboutSection store={store} />
                <ServicesSection store={store} branches={branches} wide={!twoColumns} />
              </div>
            ) : null}
            {hasBranches ? (
              <BranchesPanel
                storeId={store.id}
                storeName={store.name}
                storePath={storePath(store.id)}
                branches={branches}
                whatsappMessage={store.whatsappMessage}
                whatsappEnabled={store.whatsappEnabled}
                wide={!twoColumns}
              />
            ) : loadingBranches ? (
              <BranchesSkeleton />
            ) : null}
          </div>
        ) : null}

        <div className="flex flex-col gap-12 border-t border-ap-graphite pt-8 sm:gap-16">
          <StoreProducts store={store} branches={branches} />
          <StoreReviews store={store} />
        </div>
      </Container>
    </>
  )
}

function StoreSkeleton() {
  return (
    <>
      <span className="sr-only" role="status">
        Cargando tienda
      </span>
      <div aria-hidden>
        <Container className="py-6">
          <Skeleton className="h-4 w-56" />
        </Container>
        <Skeleton className="h-36 w-full rounded-none sm:h-48 lg:h-60" />
        <Container>
          <div className="flex flex-col gap-4 sm:flex-row sm:gap-6">
            <Skeleton className="-mt-12 size-24 rounded-xl sm:-mt-16 sm:size-32" />
            <div className="flex flex-1 flex-col gap-2 sm:pt-3">
              <Skeleton className="h-8 w-2/3" />
              <Skeleton className="h-4 w-1/3" />
              <Skeleton className="h-4 w-1/4" />
            </div>
          </div>
          <Skeleton className="mt-5 h-11 w-64" />
          <Skeleton className="mt-6 h-20 w-full" />
        </Container>
      </div>
    </>
  )
}

/**
 * Perfil público del almacén `/tiendas/:storeId`: portada con acciones, datos destacados, «Sobre la tienda» y servicios
 * junto al mapa de sedes, repuestos (con filtro por sede) y reseñas. Una sección sin datos no se dibuja.
 */
export default function StorePage() {
  const { storeId = '' } = useParams()
  const profile = useStoreProfile(storeId)

  if (profile.status === 'error') {
    if (isNotFoundError(profile.error)) {
      return (
        <NotFoundContent
          title="No encontramos esta tienda"
          description="Puede que ya no publique en Autopartco o que el enlace esté incompleto. Revisa el directorio de tiendas."
        />
      )
    }
    return (
      <Container className="py-10">
        <ErrorState title="No pudimos cargar esta tienda" onRetry={profile.retry} />
      </Container>
    )
  }
  // Al recargar tras publicar una reseña se mantiene la página; el esqueleto solo al cambiar de tienda.
  if (!profile.data || (profile.isStale && profile.data.id !== storeId)) return <StoreSkeleton />
  return <StoreDetail key={profile.data.id} store={profile.data} />
}

