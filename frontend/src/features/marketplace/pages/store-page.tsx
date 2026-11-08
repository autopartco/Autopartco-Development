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
import { initials, pluralize } from '@/lib/format'
import { invalidateAsync } from '@/lib/use-async'
import { cn } from '@/lib/utils'
import { trackView } from '@/lib/visibility'
import { getMarketplaceService } from '../api/marketplace-service'
import { isNotFoundError, type StoreContact, type StoreProfile, type StoreReview } from '../api/types'
import { ProductCard, ProductGrid, ProductGridSkeleton } from '../components/product-card'
import { ResultsPagination } from '../components/results-pagination'
import { ShareButton } from '../components/share-button'
import { useProductSearch, useReviewEligibility, useStoreProfile, useStoreReviews } from '../hooks'
import { storePath, storeProductsPath } from '../lib/paths'
import { PAGE_SIZE, parseProductQuery } from '../lib/product-query'
import { NotFoundContent } from './not-found-page'

const dateFormatter = new Intl.DateTimeFormat('es-CO', { dateStyle: 'long' })
const monthFormatter = new Intl.DateTimeFormat('es-CO', { month: 'long', year: 'numeric' })
const ratingFormatter = new Intl.NumberFormat('es-CO', { minimumFractionDigits: 1, maximumFractionDigits: 1 })

/** `YYYY-MM-DD` como fecha local (sin desplazar el día por la zona horaria). */
function localDate(value: string): Date {
  const [year, month, day] = value.slice(0, 10).split('-').map(Number)
  return new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1)
}

function StoreLogo({ store }: { store: StoreProfile }) {
  const [failed, setFailed] = useState(false)
  if (store.logoUrl && !failed) {
    return (
      <img
        src={store.logoUrl}
        alt={`Logo de ${store.name}`}
        width={80}
        height={80}
        className="size-16 shrink-0 rounded-lg border border-ap-line bg-ap-white object-contain sm:size-20"
        onError={() => setFailed(true)}
      />
    )
  }
  return (
    <span className="grid size-16 shrink-0 place-items-center rounded-lg bg-ap-graphite text-xl font-semibold text-ap-white sm:size-20" aria-hidden>
      {initials(store.name) || store.name.charAt(0)}
    </span>
  )
}

function StoreBanner({ store }: { store: StoreProfile }) {
  const [failed, setFailed] = useState(false)
  if (!store.bannerUrl || failed) return null
  return (
    <img
      src={store.bannerUrl}
      alt=""
      width={1200}
      height={240}
      className="mb-6 aspect-[3/1] w-full rounded-lg border border-ap-line bg-ap-paper object-cover sm:aspect-[5/1]"
      onError={() => setFailed(true)}
    />
  )
}

/** Estrellas de solo lectura; el valor va en texto para lectores de pantalla. */
function Stars({ value, className }: { value: number; className?: string }) {
  return (
    <span className={cn('inline-flex items-center gap-0.5', className)} role="img" aria-label={`${value} de 5`}>
      {Array.from({ length: 5 }, (_, index) => (
        <StarIcon
          key={index}
          aria-hidden
          className={cn('size-4', index < Math.round(value) ? 'fill-ap-graphite text-ap-graphite' : 'text-ap-line-strong')}
        />
      ))}
    </span>
  )
}

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-0.5">
      <dt className="ap-index">{label}</dt>
      <dd className="text-sm text-ap-ink">{children}</dd>
    </div>
  )
}

/** «+573001234567» → «+57 300 123 4567». */
function formatPhone(value: string): string {
  const digits = value.replace(/\D/g, '').replace(/^57/, '')
  return digits.length === 10 ? `+57 ${digits.slice(0, 3)} ${digits.slice(3, 6)} ${digits.slice(6)}` : value
}

function websiteLabel(value: string): string {
  try {
    return new URL(value).host.replace(/^www\./, '')
  } catch {
    return value
  }
}

/** Contacto que la tienda decidió publicar; enlaces construidos solo con datos validados por el servidor. */
function contactLinks(contact: StoreContact): { label: string; text: string; href: string }[] {
  const links: { label: string; text: string; href: string }[] = []
  if (contact.whatsapp) {
    links.push({ label: 'WhatsApp', text: formatPhone(contact.whatsapp), href: `https://wa.me/${contact.whatsapp.replace(/\D/g, '')}` })
  }
  if (contact.phone) links.push({ label: 'Teléfono', text: formatPhone(contact.phone), href: `tel:${contact.phone}` })
  if (contact.website?.startsWith('https://')) {
    links.push({ label: 'Sitio web', text: websiteLabel(contact.website), href: contact.website })
  }
  if (contact.instagram) {
    links.push({ label: 'Instagram', text: `@${contact.instagram}`, href: `https://www.instagram.com/${encodeURIComponent(contact.instagram)}` })
  }
  if (contact.facebook) {
    links.push({ label: 'Facebook', text: contact.facebook, href: `https://www.facebook.com/${encodeURIComponent(contact.facebook)}` })
  }
  if (contact.tiktok) {
    links.push({ label: 'TikTok', text: `@${contact.tiktok}`, href: `https://www.tiktok.com/@${encodeURIComponent(contact.tiktok)}` })
  }
  return links
}

function StoreHeader({ store }: { store: StoreProfile }) {
  const links = contactLinks(store.contact)
  return (
    <header className="flex flex-col gap-6">
      <StoreBanner store={store} />
      <div className="flex items-start gap-4 sm:gap-5">
        <StoreLogo store={store} />
        <div className="flex min-w-0 flex-1 flex-col gap-1">
          <p className="ap-index">Tienda / Perfil</p>
          <h1 className="text-page-title break-words text-ap-ink">{store.name}</h1>
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ap-muted">
            {store.ratingAverage !== undefined && store.ratingCount > 0 ? (
              <>
                <Stars value={store.ratingAverage} />
                <span className="font-semibold text-ap-ink tabular-nums">{ratingFormatter.format(store.ratingAverage)}</span>
                <a href="#resenas" className="underline-offset-4 hover:text-ap-red hover:underline">
                  {pluralize(store.ratingCount, 'reseña', 'reseñas')}
                </a>
              </>
            ) : (
              <span>Sin reseñas todavía</span>
            )}
            <span aria-hidden>·</span>
            <span>Tienda verificada por Autopartco</span>
          </p>
        </div>
        <ShareButton path={storePath(store.id)} title={`${store.name} en Autopartco`} storeId={store.id} />
      </div>

      <dl className="grid grid-cols-2 gap-x-6 gap-y-4 border-y border-ap-line py-4 sm:grid-cols-4">
        <Fact label="Repuestos publicados">
          <span className="tabular-nums">{store.publishedProducts}</span>
        </Fact>
        <Fact label={store.cities.length === 1 ? 'Ciudad' : 'Ciudades'}>{store.cities.length > 0 ? store.cities.join(', ') : '—'}</Fact>
        <Fact label="Sedes">
          <span className="tabular-nums">{store.branchCount}</span>
        </Fact>
        {store.memberSince ? <Fact label="En Autopartco desde">{monthFormatter.format(localDate(store.memberSince))}</Fact> : null}
      </dl>

      {store.description || links.length > 0 ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)] lg:gap-12">
          {store.description ? <p className="max-w-prose whitespace-pre-line text-ap-ink">{store.description}</p> : <span />}
          {links.length > 0 ? (
            <section aria-labelledby="contacto" className="flex flex-col gap-2">
              <h2 id="contacto" className="ap-index">
                Contacto
              </h2>
              <dl className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
                {links.map((link) => (
                  <div key={link.label} className="grid grid-cols-[6.5rem_minmax(0,1fr)] items-center gap-3 py-2">
                    <dt className="text-sm text-ap-muted">{link.label}</dt>
                    <dd className="min-w-0">
                      <a
                        href={link.href}
                        target={link.href.startsWith('tel:') ? undefined : '_blank'}
                        rel="noopener noreferrer"
                        className="inline-flex min-h-11 items-center break-all text-sm font-medium text-ap-ink underline-offset-4 hover:text-ap-red hover:underline"
                      >
                        {link.text}
                      </a>
                    </dd>
                  </div>
                ))}
              </dl>
            </section>
          ) : null}
        </div>
      ) : null}
    </header>
  )
}

function StoreProducts({ store }: { store: StoreProfile }) {
  const [searchParams] = useSearchParams()
  const page = parseProductQuery(searchParams).page
  const query = { ...parseProductQuery(new URLSearchParams()), store: store.id, page, pageSize: PAGE_SIZE }
  const results = useProductSearch(query)
  const distances = useStoreDistances()

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
    content = (
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
    <section aria-labelledby="resenas" className="flex scroll-mt-24 flex-col gap-5">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Tienda / Reseñas</p>
        <h2 id="resenas" className="text-section text-ap-ink">
          Reseñas de compradores
        </h2>
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
          <p>Esta tienda aún no tiene reseñas. Las publican compradores que recibieron un pedido suyo.</p>
          {!session ? (
            <p>
              ¿Le compraste?{' '}
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

function StoreDetail({ store }: { store: StoreProfile }) {
  useEffect(() => {
    trackView('PROFILE_VIEW', store.id)
  }, [store.id])

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${store.name} | Tiendas Autopartco`}</title>
      <Breadcrumbs items={[{ label: 'Inicio', to: '/' }, { label: 'Tiendas', to: '/tiendas' }, { label: store.name }]} className="mb-6" />
      <StoreHeader store={store} />
      <div className="mt-10 flex flex-col gap-12 border-t border-ap-graphite pt-8 sm:mt-12 sm:gap-16">
        <StoreProducts store={store} />
        <StoreReviews store={store} />
      </div>
    </Container>
  )
}

function StoreSkeleton() {
  return (
    <Container className="py-6 sm:py-8">
      <span className="sr-only" role="status">
        Cargando tienda
      </span>
      <div aria-hidden className="flex flex-col gap-6">
        <Skeleton className="h-4 w-56" />
        <div className="flex items-start gap-4">
          <Skeleton className="size-16 rounded-lg sm:size-20" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-8 w-2/3" />
            <Skeleton className="h-4 w-1/3" />
          </div>
        </div>
        <Skeleton className="h-16 w-full" />
      </div>
    </Container>
  )
}

/** Perfil público del almacén: identidad, contacto, repuestos y reseñas. `/tiendas/:storeId`. */
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

