import { useEffect, useId, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useLocation, useParams, useSearchParams } from 'react-router'
import { StarIcon } from 'lucide-react'
import { toast } from 'sonner'
import { Breadcrumbs, Container } from '@/components/common/page-header'
import { FieldShell, FormAlert } from '@/components/common/form-fields'
import { EmptyState, ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { useSession } from '@/features/auth/hooks'
import { useStoreDistances } from '@/features/location/hooks'
import { pluralize } from '@/lib/format'
import { invalidateAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { trackView } from '@/lib/visibility'
import { getMarketplaceService } from '../api/marketplace-service'
import { isNotFoundError, type StoreBranch, type StoreProfile, type StoreReview } from '../api/types'
import { ProductCard, ProductGrid, ProductGridSkeleton } from '../components/product-card'
import { ResultsPagination } from '../components/results-pagination'
import { BranchesPanel } from '../components/store/branches-panel'
import { ServicesList } from '../components/store/services-list'
import { Stars } from '../components/store/stars'
import { StoreFacts } from '../components/store/store-facts'
import { StoreHeader } from '../components/store/store-header'
import { useProductSearch, useReviewEligibility, useStoreBranches, useStoreProfile, useStoreReviews } from '../hooks'
import { storePath, storeProductsPath } from '../lib/paths'
import { PAGE_SIZE, parseProductQuery, QUERY_PARAMS } from '../lib/product-query'
import { NotFoundContent } from './not-found-page'

const dateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' })
const ratingFormatter = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

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

function ReviewForm({ storeId, orderIds }: { storeId: string; orderIds: string[] }) {
  const formId = useId()
  const [orderId, setOrderId] = useState(orderIds[0] ?? '')
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [error, setError] = useState<string>()
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (rating < 1) {
      setError('Elige de 1 a 5 estrellas.')
      return
    }
    setError(undefined)
    setPending(true)
    try {
      const service = await getMarketplaceService()
      await service.createStoreReview(storeId, { orderId, rating, comment: comment.trim() || undefined })
      toast.success('Gracias por calificar la tienda')
      invalidateAsync(`store-reviews:${storeId}`)
      invalidateAsync(`store-profile:${storeId}`)
      invalidateAsync(`store-review-eligibility:${storeId}`)
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : 'No pudimos guardar tu reseña. Inténtalo de nuevo.')
    } finally {
      setPending(false)
    }
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-4 border-y border-ap-graphite py-5" aria-labelledby={`${formId}-titulo`}>
      <div className="flex flex-col gap-1">
        <h3 id={`${formId}-titulo`} className="text-block-title text-ap-ink">
          Califica tu compra
        </h3>
        <p className="text-sm text-ap-muted">Solo pueden calificar quienes recibieron un pedido de esta tienda.</p>
      </div>
      {error ? <FormAlert title={error} /> : null}
      {orderIds.length > 1 ? (
        <FieldShell id={`${formId}-pedido`} label="Pedido">
          {(describedBy) => (
            <select
              id={`${formId}-pedido`}
              value={orderId}
              aria-describedby={describedBy}
              onChange={(event) => setOrderId(event.target.value)}
              className="h-11 w-full max-w-xs rounded-md border border-input bg-ap-white px-3 text-base text-ap-ink sm:text-sm"
            >
              {orderIds.map((id) => (
                <option key={id} value={id}>
                  Pedido {id.slice(0, 8).toUpperCase()}
                </option>
              ))}
            </select>
          )}
        </FieldShell>
      ) : null}
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-1.5 text-sm font-semibold text-ap-ink">Calificación</legend>
        <div className="flex flex-wrap gap-1">
          {[1, 2, 3, 4, 5].map((value) => (
            <label
              key={value}
              className={cn(
                'inline-flex min-h-11 cursor-pointer items-center gap-1.5 rounded-md border px-3 text-sm font-medium transition-colors has-focus-visible:outline-2 has-focus-visible:outline-offset-2 has-focus-visible:outline-ap-red',
                rating === value ? 'border-ap-graphite bg-ap-paper text-ap-ink' : 'border-input bg-ap-white text-ap-muted hover:border-ap-graphite',
              )}
            >
              <input type="radio" name={`${formId}-rating`} value={value} checked={rating === value} onChange={() => setRating(value)} className="sr-only" />
              <StarIcon aria-hidden className={cn('size-4', rating >= value ? 'fill-ap-graphite text-ap-graphite' : 'text-ap-line-strong')} />
              {value}
            </label>
          ))}
        </div>
      </fieldset>
      <FieldShell id={`${formId}-comentario`} label="Comentario" optional hint={`${comment.length} de 1000 caracteres.`}>
        {(describedBy) => (
          <textarea
            id={`${formId}-comentario`}
            value={comment}
            maxLength={1000}
            rows={3}
            aria-describedby={describedBy}
            onChange={(event) => setComment(event.target.value)}
            placeholder="Ej.: entregaron a tiempo y la referencia era la correcta."
            className="w-full rounded-md border border-input bg-ap-white px-3 py-2 text-base text-ap-ink placeholder:text-ap-muted hover:border-ap-graphite focus-visible:border-ap-graphite sm:text-sm"
          />
        )}
      </FieldShell>
      <Button type="submit" className="w-fit" disabled={pending}>
        {pending ? 'Publicando…' : 'Publicar reseña'}
      </Button>
    </form>
  )
}

function ReviewRow({ review }: { review: StoreReview }) {
  return (
    <li className="flex flex-col gap-2 py-4">
      <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
        <Stars value={review.rating} />
        <span className="text-sm font-semibold text-ap-ink">{review.authorName}</span>
        <time dateTime={review.createdAt} className="text-sm text-ap-muted">
          {dateFormatter.format(new Date(review.createdAt))}
        </time>
      </div>
      {review.comment ? <p className="max-w-prose whitespace-pre-line text-sm text-ap-ink">{review.comment}</p> : null}
    </li>
  )
}

function StoreReviews({ store }: { store: StoreProfile }) {
  const [page, setPage] = useState(1)
  const reviews = useStoreReviews(store.id, page)
  const session = useSession()
  const location = useLocation()
  const eligibility = useReviewEligibility(store.id, session?.user.email)
  const eligibleOrders = eligibility.data ?? []

  return (
    <section id="resenas" aria-labelledby="resenas-titulo" className="flex scroll-mt-24 flex-col gap-5">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Tienda / Reseñas</p>
        <h2 id="resenas-titulo" className="text-section text-ap-ink">
          Reseñas de compradores
        </h2>
        {/*
          TODO(reseñas): aquí irán el resumen de calificación (promedio, distribución por estrellas) y las insignias
          de la tienda; este bloque solo muestra el promedio y el total que ya entrega el perfil. La lógica de
          reseñas llega en otra tarea: no añadir aquí cálculos ni estado nuevos.
        */}
        {store.ratingAverage !== undefined && store.ratingCount > 0 ? (
          <p className="flex flex-wrap items-center gap-2 text-sm text-ap-muted">
            <Stars value={store.ratingAverage} />
            <span className="font-semibold text-ap-ink tabular-nums">{ratingFormatter.format(store.ratingAverage)} de 5</span>
            <span>· {pluralize(store.ratingCount, 'reseña', 'reseñas')} de pedidos entregados</span>
          </p>
        ) : null}
      </div>

      {eligibleOrders.length > 0 ? <ReviewForm key={eligibleOrders.join(',')} storeId={store.id} orderIds={eligibleOrders} /> : null}

      {reviews.status === 'error' ? (
        <ErrorState title="No pudimos cargar las reseñas" onRetry={reviews.retry} />
      ) : !reviews.data ? (
        <div className="flex flex-col gap-3" aria-hidden>
          <Skeleton className="h-4 w-48" />
          <Skeleton className="h-4 w-3/4" />
        </div>
      ) : reviews.data.items.length === 0 ? (
        <div className="flex flex-col gap-2 border-y border-ap-line py-5 text-sm text-ap-muted">
          <p className="text-base font-semibold text-ap-ink">Sé el primero en opinar después de tu compra</p>
          <p>Las reseñas las publican compradores que ya recibieron un pedido de esta tienda, para que otros compren con confianza.</p>
          {!session ? (
            <p>
              ¿Ya le compraste?{' '}
              <Link to="/ingresar" state={{ from: location }} className="font-semibold text-ap-red underline-offset-4 hover:underline">
                Ingresa para calificarla
              </Link>
            </p>
          ) : null}
        </div>
      ) : (
        <>
          <ul className={cn('flex flex-col divide-y divide-ap-line border-y border-ap-line', reviews.isStale && 'opacity-60')}>
            {reviews.data.items.map((review) => (
              <ReviewRow key={review.id} review={review} />
            ))}
          </ul>
          {reviews.data.totalPages > 1 ? (
            <nav aria-label="Páginas de reseñas" className="flex items-center gap-2">
              <Button variant="outline" disabled={page <= 1} onClick={() => setPage((current) => current - 1)}>
                Anteriores
              </Button>
              <span className="text-sm text-ap-muted tabular-nums" aria-live="polite">
                Página {reviews.data.page} de {reviews.data.totalPages}
              </span>
              <Button variant="outline" disabled={page >= reviews.data.totalPages} onClick={() => setPage((current) => current + 1)}>
                Siguientes
              </Button>
            </nav>
          ) : null}
        </>
      )}
    </section>
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

