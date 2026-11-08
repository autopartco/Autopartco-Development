import { useEffect, useState, type ReactNode } from 'react'
import { Link, useParams } from 'react-router'
import { CheckIcon, CopyIcon, InfoIcon } from 'lucide-react'
import { Breadcrumbs, Container, type Crumb } from '@/components/common/page-header'
import { SmartImage } from '@/components/common/smart-image'
import { ErrorState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { AddToCart } from '@/features/cart/components/add-to-cart'
import { useNearbyStores, useStoreDistances } from '@/features/location/hooks'
import { formatKm } from '@/features/location/lib/geo'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import { isNotFoundError, type Product } from '../api/types'
import { FrequentlyBought } from '../components/frequently-bought'
import { ProductCard, ProductGrid } from '../components/product-card'
import { AvailabilityLabel, BrandName, Price, RECOMMENDED_EXPLANATION, RecommendedMark, UniversalTag } from '../components/product-meta'
import { useProduct, useRelatedProducts } from '../hooks'
import { categoryPath, storeProductsPath } from '../lib/paths'
import { NotFoundContent } from './not-found-page'

/** Galería: imagen grande con `contain` sobre fondo papel y miniaturas seleccionables. */
function Gallery({ product }: { product: Product }) {
  const [index, setIndex] = useState(0)
  const images = product.images
  const current = images[index] ?? images[0]
  return (
    <div className="flex flex-col gap-3">
      <SmartImage
        key={current?.url ?? 'sin-imagen'}
        src={current?.url}
        alt={images.length > 0 ? `${product.name}, imagen ${index + 1} de ${images.length}` : product.name}
        width={800}
        height={600}
        loading="eager"
        fetchPriority="high"
        frameClassName="aspect-[4/3] w-full rounded-xl border border-ap-line"
        className="p-4"
      />
      {images.length > 1 ? (
        // Rejilla de ancho fijo: las miniaturas se encogen con la pantalla en vez de desbordarla.
        <ul className="grid grid-cols-4 gap-2 sm:grid-cols-5" aria-label="Imágenes del repuesto">
          {images.map((image, imageIndex) => (
            <li key={`${image.url}-${imageIndex}`}>
              <button
                type="button"
                onClick={() => setIndex(imageIndex)}
                aria-label={`Ver imagen ${imageIndex + 1} de ${images.length}`}
                aria-pressed={imageIndex === index}
                className={cn(
                  'block w-full overflow-hidden rounded-md border-2 transition-colors',
                  imageIndex === index ? 'border-ap-red' : 'border-ap-line hover:border-ap-line-strong',
                )}
              >
                <SmartImage src={image.thumbUrl} alt="" width={96} height={72} frameClassName="aspect-[4/3] w-full" className="p-1" />
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
      className="h-9 pointer-coarse:h-11"
      onClick={() => navigator.clipboard.writeText(value).then(() => setCopied(true), () => setCopied(false))}
    >
      {copied ? <CheckIcon aria-hidden /> : <CopyIcon aria-hidden />}
      <span aria-live="polite">{copied ? 'Copiada' : 'Copiar'}</span>
      <span className="sr-only"> referencia</span>
    </Button>
  )
}

/** Fila de la ficha técnica: etiqueta a la izquierda, dato a la derecha. */
function FactRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="grid grid-cols-[7.5rem_minmax(0,1fr)] items-center gap-4 py-2.5 sm:grid-cols-[9rem_minmax(0,1fr)]">
      <dt className="text-sm text-ap-muted">{label}</dt>
      <dd className="min-w-0 text-sm text-ap-ink">{children}</dd>
    </div>
  )
}

function compatibilitySummary(product: Product): ReactNode {
  if (product.universal) {
    return (
      <span className="flex flex-wrap items-center gap-2">
        <UniversalTag />
        <span className="text-ap-muted">Sirve para cualquier vehículo</span>
      </span>
    )
  }
  const count = product.vehicleRules.length
  if (count === 0) return <span className="text-ap-muted">Sin compatibilidad publicada: confirma con la tienda</span>
  return (
    <a href="#compatibilidad" className="font-medium text-ap-red underline-offset-4 hover:underline">
      {count === 1 ? '1 vehículo publicado' : `${count} vehículos publicados`}
    </a>
  )
}

/** Datos de identificación de la pieza en filas, como una ficha técnica. */
function ProductFacts({ product }: { product: Product }) {
  return (
    <dl className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
      {product.brand ? (
        <FactRow label="Marca">
          <BrandName brand={product.brand} />
        </FactRow>
      ) : null}
      {product.category ? (
        <FactRow label="Categoría">
          <Link to={categoryPath(product.category.slug)} className="underline-offset-4 hover:text-ap-red hover:underline">
            {product.category.name}
          </Link>
        </FactRow>
      ) : null}
      <FactRow label="Compatibilidad">{compatibilitySummary(product)}</FactRow>
    </dl>
  )
}

/** Precio, existencias, tienda y la acción principal (agregar al carrito), juntos. */
function PurchasePanel({ product }: { product: Product }) {
  const distances = useStoreDistances()
  const nearby = useNearbyStores().data
  const distanceKm = product.store ? distances.get(product.store.id) : undefined
  return (
    <section aria-label="Precio y compra" className="flex flex-col gap-5 rounded-xl border border-ap-line-strong bg-ap-white p-5">
      <div className="flex flex-col gap-2">
        <Price product={product} size="lg" />
        {product.discountAmount ? (
          <p className="text-sm font-medium text-ap-red tabular-nums">
            Ahorras {formatCOP(product.discountAmount)}
            {product.promotionName ? ` con “${product.promotionName}”` : ''}
          </p>
        ) : null}
        {product.availability ? (
          <p className="flex flex-wrap items-center gap-x-2 text-sm">
            <AvailabilityLabel availability={product.availability} showQuantity />
            {product.availability.branchName ? <span className="text-ap-muted">en {product.availability.branchName}</span> : null}
          </p>
        ) : null}
      </div>

      {product.store ? (
        <div className="flex flex-col gap-1 border-t border-ap-line pt-4">
          <p className="ap-index">Vendido por</p>
          <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-base font-semibold text-ap-ink">
            {product.store.name}
            {product.store.recommended ? <RecommendedMark /> : null}
          </p>
          <p className="flex flex-wrap gap-x-2 text-sm text-ap-muted">
            {product.store.city ? <span>{product.store.city}</span> : null}
            {distanceKm !== undefined ? (
              <span className="tabular-nums">
                · sede a {formatKm(distanceKm)} de {nearby?.origin?.label ?? 'tu punto de referencia'}
              </span>
            ) : null}
          </p>
          {product.store.recommended ? <p className="text-xs text-ap-muted">{RECOMMENDED_EXPLANATION}</p> : null}
          <Link
            to={storeProductsPath(product.store.id)}
            className="inline-flex min-h-11 w-fit items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline"
          >
            Ver más repuestos de esta tienda
          </Link>
        </div>
      ) : null}

      <AddToCart product={product} />
    </section>
  )
}

function CompatibilityTable({ product }: { product: Product }) {
  return (
    <section aria-labelledby="compatibilidad" className="flex scroll-mt-24 flex-col gap-3">
      <div className="flex flex-col gap-1">
        <p className="ap-index">{product.universal ? 'Compatibilidad / Universal' : 'Compatibilidad / Vehículos'}</p>
        <h2 id="compatibilidad" className="text-block-title text-ap-ink">
          {product.universal ? 'Compatibilidad' : 'Vehículos compatibles'}
        </h2>
      </div>
      {product.universal ? (
        <p className="flex flex-wrap items-center gap-2 border-y border-ap-line py-3 text-sm text-ap-ink">
          <UniversalTag />
          No depende del vehículo: sirve para cualquier marca y modelo, por eso aparece en todas las búsquedas por vehículo.
        </p>
      ) : (
        <div className="overflow-x-auto" tabIndex={0} role="region" aria-label={`Vehículos para los que aplica ${product.name}`}>
          <Table className="min-w-[24rem]">
            <TableHeader>
              <TableRow>
                <TableHead>Marca</TableHead>
                <TableHead>Modelo</TableHead>
                <TableHead>Versión</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {product.vehicleRules.map((rule) => (
                <TableRow key={`${rule.makeId}:${rule.modelId ?? ''}:${rule.versionId ?? ''}`}>
                  <TableCell className="font-medium">{rule.makeName}</TableCell>
                  <TableCell>{rule.modelName ?? <span className="text-ap-muted">Todos los modelos</span>}</TableCell>
                  <TableCell>
                    {rule.modelName ? (rule.versionLabel ?? <span className="text-ap-muted">Todas las versiones</span>) : <span className="text-ap-muted">Todas</span>}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {/*
       * Honestidad: el catálogo todavía no informa si cada compatibilidad está
       * confirmada por catálogo, declarada por la tienda o inferida. Lo decimos.
       */}
      <p className="flex items-start gap-2 text-sm text-ap-muted">
        <InfoIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
        <span>
          Compatibilidad publicada por la tienda; aún no indicamos si está confirmada por catálogo. Confirma la referencia
          antes de comprar: algunas piezas cambian según el motor o la versión.
        </span>
      </p>
    </section>
  )
}

function Specifications({ product }: { product: Product }) {
  return (
    <section aria-labelledby="especificaciones" className="flex min-w-0 flex-col gap-3">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Ficha técnica / Especificaciones</p>
        <h2 id="especificaciones" className="text-block-title text-ap-ink">
          Especificaciones
        </h2>
      </div>
      <dl className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
        {product.specifications.map((spec) => (
          <div key={spec.label} className="grid grid-cols-[minmax(0,2fr)_minmax(0,3fr)] gap-4 py-2.5 text-sm">
            <dt className="text-ap-muted">{spec.label}</dt>
            <dd className="font-medium text-ap-ink">{spec.value}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function ProductDetail({ product }: { product: Product }) {
  const related = useRelatedProducts(product)
  const distances = useStoreDistances()
  const crumbs: Crumb[] = [{ label: 'Inicio', to: '/' }]
  if (product.category) {
    crumbs.push({ label: product.category.name, to: categoryPath(product.category.slug) })
  }
  crumbs.push({ label: product.name })
  const hasFitment = product.universal || product.vehicleRules.length > 0
  const hasSpecs = product.specifications.length > 0

  return (
    <Container className="py-6 sm:py-8">
      <title>{`${product.name}${product.partNumber ? ` ${product.partNumber}` : ''} | Autopartco`}</title>
      <Breadcrumbs items={crumbs} className="mb-6" />

      {/* Tablet: galería y ficha lado a lado; móvil: imagen, ficha y compra apiladas. */}
      <div className="grid gap-8 md:grid-cols-2 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)] lg:gap-12">
        <div className="min-w-0 md:sticky md:top-[calc(var(--header-h)+1.5rem)] md:self-start">
          <Gallery product={product} />
        </div>
        <div className="flex min-w-0 flex-col gap-6">
          <div className="flex flex-col gap-2">
            <p className="ap-index">Ficha técnica{product.category ? ` / ${product.category.name}` : ''}</p>
            <h1 className="text-page-title text-ap-ink">{product.name}</h1>
            {product.partNumber ? (
              <p className="flex flex-wrap items-center gap-x-2">
                <span className="ap-ref text-sm text-ap-ink">REF. {product.partNumber}</span>
                <CopyReference value={product.partNumber} />
              </p>
            ) : null}
          </div>
          <ProductFacts product={product} />
          <PurchasePanel product={product} />
        </div>
      </div>

      {hasFitment || hasSpecs || product.description ? (
        <div className="mt-12 grid gap-10 border-t border-ap-graphite pt-8 sm:mt-16 lg:grid-cols-2 lg:gap-12">
          <div className="flex min-w-0 flex-col gap-10">
            {hasFitment ? <CompatibilityTable product={product} /> : null}
            {product.description ? (
              <section aria-labelledby="descripcion" className="flex flex-col gap-2">
                <h2 id="descripcion" className="text-block-title text-ap-ink">
                  Descripción
                </h2>
                <p className="max-w-prose text-ap-ink">{product.description}</p>
              </section>
            ) : null}
          </div>
          {hasSpecs ? <Specifications product={product} /> : null}
        </div>
      ) : null}

      <div className="mt-12 sm:mt-16">
        <FrequentlyBought productId={product.id} />
      </div>

      {related.data && related.data.length > 0 ? (
        <section aria-labelledby="relacionados" className="mt-12 sm:mt-16">
          <div className="mb-6 flex flex-col gap-1">
            <p className="ap-index">Catálogo / Relacionados</p>
            <h2 id="relacionados" className="text-section text-ap-ink">
              También te puede servir
            </h2>
          </div>
          <ProductGrid className="lg:grid-cols-4">
            {related.data.map((item) => (
              <ProductCard key={item.listingKey} product={item} distanceKm={item.store ? distances.get(item.store.id) : undefined} />
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
        <div className="grid gap-8 md:grid-cols-2 lg:gap-12">
          <Skeleton className="aspect-[4/3] rounded-xl" />
          <div className="flex flex-col gap-4">
            <Skeleton className="h-3 w-40" />
            <Skeleton className="h-9 w-5/6" />
            <Skeleton className="h-32 w-full" />
            <Skeleton className="h-48 w-full rounded-xl" />
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

