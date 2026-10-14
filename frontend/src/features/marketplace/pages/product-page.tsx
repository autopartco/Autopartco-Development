import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { AnimatePresence, m } from 'motion/react'
import { CarFrontIcon, CheckIcon, CopyIcon, InfoIcon } from 'lucide-react'
import { Breadcrumbs, Container, type Crumb } from '@/components/common/page-header'
import { SmartImage } from '@/components/common/smart-image'
import { ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import { isNotFoundError, type Product } from '../api/types'
import { ProductCard, ProductGrid } from '../components/product-card'
import { AvailabilityLabel, Price, RECOMMENDED_EXPLANATION, RecommendedMark } from '../components/product-meta'
import { useProduct, useRelatedProducts } from '../hooks'
import { categoryPath, storeProductsPath } from '../lib/paths'
import { NotFoundContent } from './not-found-page'
import { useNearbyStores, useStoreDistances } from '@/features/location/hooks'
import { formatKm } from '@/features/location/lib/geo'

function Gallery({ product }: { product: Product }) {
  const [index, setIndex] = useState(0)
  const images = product.images
  const current = images[index] ?? images[0]
  return (
    <div className="flex flex-col gap-3">
      <div className="relative aspect-[4/3] overflow-hidden rounded-xl bg-concrete outline outline-1 -outline-offset-1 outline-black/10">
        <AnimatePresence initial={false} mode="popLayout">
          <m.div
            key={current}
            className="absolute inset-0"
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
          >
            <SmartImage
              src={current}
              alt={`${product.name}, imagen ${index + 1} de ${images.length}`}
              width={800}
              height={600}
              loading="eager"
              fetchPriority="high"
              frameClassName="size-full"
            />
          </m.div>
        </AnimatePresence>
      </div>
      {images.length > 1 ? (
        // Rejilla de ancho fijo: las miniaturas se encogen con la pantalla en vez de desbordarla.
        <ul className="grid grid-cols-4 gap-2 sm:grid-cols-5" aria-label="Imágenes del repuesto">
          {images.map((image, imageIndex) => (
            <li key={`${image}-${imageIndex}`}>
              <button
                type="button"
                onClick={() => setIndex(imageIndex)}
                aria-label={`Ver imagen ${imageIndex + 1} de ${images.length}`}
                aria-pressed={imageIndex === index}
                className={cn(
                  'block w-full overflow-hidden rounded-md ring-2 ring-transparent ring-offset-2 ring-offset-background transition-shadow',
                  imageIndex === index ? 'ring-brand' : 'hover:ring-border',
                )}
              >
                <SmartImage src={image} alt="" width={96} height={72} frameClassName="aspect-[4/3] w-full" />
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function CopyReference({ value }: { value: string }) {
  const [copied, setCopied] = useState(false)
  useEffect(() => {
    if (!copied) return
    const timer = window.setTimeout(() => setCopied(false), 2000)
    return () => window.clearTimeout(timer)
  }, [copied])
  if (!('clipboard' in navigator)) return null
  return (
    <Button
      variant="ghost"
      size="sm"
      className="-ml-1 h-9 pointer-coarse:h-11"
      onClick={() => navigator.clipboard.writeText(value).then(() => setCopied(true), () => setCopied(false))}
    >
      {copied ? <CheckIcon aria-hidden /> : <CopyIcon aria-hidden />}
      <span aria-live="polite">{copied ? 'Referencia copiada' : 'Copiar referencia'}</span>
    </Button>
  )
}

function PurchasePanel({ product }: { product: Product }) {
  const distances = useStoreDistances()
  const nearby = useNearbyStores().data
  const distanceKm = product.store ? distances.get(product.store.id) : undefined
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-2">
        <Price product={product} size="lg" />
        {product.discountAmount ? (
          <p className="text-sm font-medium text-brand tabular-nums">
            Ahorras {formatCOP(product.discountAmount)}
            {product.promotionName ? ` con “${product.promotionName}”` : ''}
          </p>
        ) : null}
        {product.availability ? (
          <p className="flex flex-wrap items-center gap-x-2 text-sm">
            <AvailabilityLabel availability={product.availability} showQuantity />
            {product.availability.branchName ? (
              <span className="text-muted-foreground">en {product.availability.branchName}</span>
            ) : null}
          </p>
        ) : null}
      </div>

      {product.store ? (
        <div className="flex flex-col gap-4 rounded-xl bg-card p-4 shadow-[0_0_0_1px_rgb(0_0_0/0.07)] sm:p-5">
          <div className="flex flex-col gap-1">
            <p className="label-mono text-muted-foreground">Vendido por</p>
            <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-lg leading-tight font-bold tracking-tight">
              {product.store.name}
              {product.store.recommended ? <RecommendedMark /> : null}
            </p>
            {product.store.city ? <p className="text-sm text-muted-foreground">{product.store.city}</p> : null}
            {product.store.recommended ? (
              <p className="text-xs text-muted-foreground">{RECOMMENDED_EXPLANATION}</p>
            ) : null}
            {distanceKm !== undefined ? (
              <p className="mt-1.5 flex flex-wrap items-center gap-x-2 gap-y-1 text-sm">
                <span className="rounded-sm bg-asphalt px-1.5 py-0.5 label-mono text-white tabular-nums">
                  a {formatKm(distanceKm)}
                </span>
                <span className="text-muted-foreground">
                  Sede más cercana a {nearby?.origin?.label ?? 'tu punto de referencia'}
                  {nearby?.city ? `, ${nearby.city.name}` : ''}
                </span>
              </p>
            ) : null}
          </div>
          <Button
            variant="outline"
            className="h-11"
            nativeButton={false}
            render={<Link to={storeProductsPath(product.store.id)} />}
          >
            Ver más repuestos de esta tienda
          </Button>
        </div>
      ) : null}

      <p className="flex items-start gap-2 rounded-lg bg-concrete p-4 text-sm">
        <InfoIcon className="mt-0.5 size-4 shrink-0 text-muted-foreground" aria-hidden />
        <span>
          La compra en línea aún no está disponible en Autopartco. Guarda la referencia y consulta con la tienda
          para comprar este repuesto.
        </span>
      </p>
    </div>
  )
}

function CompatibilityTable({ product }: { product: Product }) {
  return (
    <section aria-labelledby="compatibilidad" className="flex flex-col gap-4">
      <div className="flex flex-col gap-1.5">
        <p className="label-mono text-muted-foreground tabular-nums">
          {fitsLabel(product.compatibilities.length)}
        </p>
        <h2 id="compatibilidad" className="text-block-title">
          Vehículos compatibles
        </h2>
      </div>
      {/* Móvil: una ficha por vehículo, sin tabla que obligue a desplazarse de lado. */}
      <ul className="flex flex-col divide-y divide-border rounded-xl bg-card shadow-[0_0_0_1px_rgb(0_0_0/0.07)] sm:hidden">
        {product.compatibilities.map((fit) => (
          <li key={fit.id} className="flex flex-col gap-1 px-4 py-3">
            <p className="font-semibold">
              {fit.make} {fit.model}
            </p>
            <p className="flex flex-wrap gap-x-4 gap-y-0.5 text-sm text-muted-foreground">
              <span>Versión: {fit.version ?? 'sin dato'}</span>
              <span className="tabular-nums">
                Años: {fit.yearFrom && fit.yearTo ? `${fit.yearFrom} a ${fit.yearTo}` : 'sin dato'}
              </span>
            </p>
          </li>
        ))}
      </ul>
      <div className="hidden overflow-x-auto rounded-xl bg-card shadow-[0_0_0_1px_rgb(0_0_0/0.07)] sm:block">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Vehículos para los que aplica {product.name}</caption>
          <thead className="label-mono text-muted-foreground">
            <tr>
              <th scope="col" className="px-4 py-3 font-normal">Marca</th>
              <th scope="col" className="px-4 py-3 font-normal">Modelo</th>
              <th scope="col" className="px-4 py-3 font-normal">Versión</th>
              <th scope="col" className="px-4 py-3 font-normal">Años</th>
            </tr>
          </thead>
          <tbody>
            {product.compatibilities.map((fit) => (
              <tr key={fit.id} className="border-t border-border">
                <td className="px-4 py-3">{fit.make}</td>
                <td className="px-4 py-3 font-medium">{fit.model}</td>
                <td className="px-4 py-3 text-muted-foreground">{fit.version ?? 'Sin dato'}</td>
                <td className="px-4 py-3 tabular-nums">
                  {fit.yearFrom && fit.yearTo ? `${fit.yearFrom} a ${fit.yearTo}` : 'Sin dato'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {/*
       * Honestidad: el catálogo todavía no informa si cada compatibilidad está
       * confirmada por catálogo, declarada por la tienda o inferida. Lo decimos.
       */}
      <p className="flex items-start gap-2 text-sm text-muted-foreground">
        <InfoIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>
          Aún no indicamos si cada vehículo de esta lista está confirmado por catálogo o lo declaró la tienda. Confirma
          la referencia antes de comprar: algunas piezas cambian según el motor o la versión.
        </span>
      </p>
    </section>
  )
}

function fitsLabel(count: number): string {
  return count === 1 ? '1 vehículo publicado' : `${count} vehículos publicados`
}

function ProductDetail({ product }: { product: Product }) {
  const related = useRelatedProducts(product)
  const distances = useStoreDistances()
  const crumbs: Crumb[] = [{ label: 'Inicio', to: '/' }]
  if (product.category) {
    crumbs.push({ label: product.category.name, to: categoryPath(product.category.slug) })
  }
  crumbs.push({ label: product.name })
  const fitCount = product.compatibilities.length

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${product.name}${product.partNumber ? ` ${product.partNumber}` : ''} | Autopartco`}</title>
      <Breadcrumbs items={crumbs} className="mb-6 sm:mb-8" />

      {/* Tablet: galería y compra lado a lado (mitad y mitad); escritorio con más aire para la imagen. */}
      <div className="grid gap-8 md:grid-cols-2 md:gap-8 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)] lg:gap-12">
        <div className="min-w-0 md:sticky md:top-[calc(var(--header-h)+1.5rem)] md:self-start">
          <Gallery product={product} />
        </div>
        <div className="flex min-w-0 flex-col gap-6">
          <div className="flex flex-col gap-3">
            <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
              {product.brand ? <span className="text-sm font-semibold">{product.brand.name}</span> : null}
              {product.partNumber ? (
                <span className="label-mono text-muted-foreground tabular-nums">Ref. {product.partNumber}</span>
              ) : null}
              {product.partNumber ? <CopyReference value={product.partNumber} /> : null}
            </p>
            <h1 className="text-page-title">{product.name}</h1>
            {fitCount > 0 ? (
              <a
                href="#compatibilidad"
                className="flex min-h-10 w-fit items-center gap-1.5 text-sm font-medium text-brand underline-offset-4 hover:underline"
              >
                <CarFrontIcon className="size-4" aria-hidden />
                Ver {fitCount} {fitCount === 1 ? 'vehículo publicado' : 'vehículos publicados'}
              </a>
            ) : null}
          </div>
          <PurchasePanel product={product} />
        </div>
      </div>

      <div className="mt-14 grid gap-12 sm:mt-20 lg:grid-cols-[minmax(0,1.1fr)_minmax(0,0.9fr)] lg:gap-12">
        <div className="flex min-w-0 flex-col gap-12">
          {fitCount > 0 ? <CompatibilityTable product={product} /> : null}
          {product.description ? (
            <section aria-labelledby="descripcion" className="flex flex-col gap-3">
              <h2 id="descripcion" className="text-block-title">
                Descripción
              </h2>
              <p className="max-w-prose text-foreground/85">{product.description}</p>
            </section>
          ) : null}
        </div>
        {product.specifications.length > 0 ? (
          <section aria-labelledby="especificaciones" className="flex min-w-0 flex-col gap-4">
            <h2 id="especificaciones" className="text-block-title">
              Especificaciones
            </h2>
            <dl className="divide-y divide-border rounded-xl bg-card shadow-[0_0_0_1px_rgb(0_0_0/0.07)]">
              {product.specifications.map((spec) => (
                <div key={spec.label} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-4 px-4 py-3 text-sm">
                  <dt className="text-muted-foreground">{spec.label}</dt>
                  <dd className="font-medium">{spec.value}</dd>
                </div>
              ))}
            </dl>
          </section>
        ) : null}
      </div>

      {related.data && related.data.length > 0 ? (
        <section aria-labelledby="relacionados" className="mt-16 sm:mt-24">
          <h2 id="relacionados" className="mb-8 text-section">
            También te puede servir
          </h2>
          <ProductGrid className="lg:grid-cols-4">
            {related.data.map((item) => (
              <ProductCard
                key={item.listingKey}
                product={item}
                distanceKm={item.store ? distances.get(item.store.id) : undefined}
              />
            ))}
          </ProductGrid>
        </section>
      ) : null}
    </Container>
  )
}

function ProductSkeleton() {
  return (
    <Container className="py-6 sm:py-8">
      <span className="sr-only" role="status">
        Cargando repuesto
      </span>
      <div aria-hidden className="flex flex-col gap-6">
        <Skeleton className="h-4 w-64" />
        <div className="grid gap-8 md:grid-cols-2 lg:grid-cols-[1.1fr_0.9fr] lg:gap-12">
          <Skeleton className="aspect-[4/3] rounded-xl" />
          <div className="flex flex-col gap-4">
            <Skeleton className="h-4 w-40" />
            <Skeleton className="h-10 w-5/6" />
            <Skeleton className="h-10 w-1/2" />
            <Skeleton className="h-28 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        </div>
      </div>
    </Container>
  )
}

export default function ProductPage() {
  const { id = '' } = useParams()
  const product = useProduct(id)

  if (product.status === 'error') {
    if (isNotFoundError(product.error)) {
      return (
        <NotFoundContent
          title="No encontramos este repuesto"
          description="Puede que la tienda lo haya retirado o que ya no tenga existencias. Busca otra referencia o explora la categoría."
        />
      )
    }
    return (
      <Container className="py-10">
        <ErrorState title="No pudimos cargar este repuesto" onRetry={product.retry} />
      </Container>
    )
  }
  if (!product.data || product.isStale) return <ProductSkeleton />
  return <ProductDetail key={product.data.id} product={product.data} />
}
