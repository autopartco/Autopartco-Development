import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { m } from 'motion/react'
import { Container } from '@/components/common/page-header'
import { SmartImage } from '@/components/common/smart-image'
import { ErrorState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { useNearbyStores, useStoreDistances } from '@/features/location/hooks'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { Product } from '../api/types'
import { productPath } from '../lib/paths'
import { CountUp } from './count-up'
import { EASE_THREAD, REVEAL_ITEM, REVEAL_ONCE } from './motion-presets'
import { featuredOffer, useMostOfferedReference, type ReferenceOffers } from './reference-offers'

/** Sello que cae de golpe: rotado -8°, de 1.4 a 1 en 180 ms, después de las cifras. */
function Stamp({ title, detail, tone, order }: { title: string; detail?: string; tone: 'brand' | 'ink'; order: number }) {
  return (
    <m.li
      initial={{ opacity: 0, scale: 1.4, rotate: -8 }}
      whileInView={{ opacity: 1, scale: 1, rotate: -8 }}
      viewport={{ once: true, amount: 0.8 }}
      transition={{ duration: 0.18, ease: 'easeIn', delay: 0.85 + order * 0.12 }}
      className={cn(
        'flex max-w-[13rem] flex-col rounded-md border-2 px-3 py-2 outline outline-1 -outline-offset-[6px] outline-dashed',
        tone === 'brand' ? 'border-brand text-brand outline-brand/40' : 'border-asphalt text-asphalt outline-asphalt/30',
      )}
    >
      <span className="label-mono font-bold">{title}</span>
      {detail ? <span className="mt-0.5 text-xs leading-snug font-medium">{detail}</span> : null}
    </m.li>
  )
}

function Field({ label, children, note, className }: { label: string; children: ReactNode; note?: string; className?: string }) {
  return (
    <div className={cn('flex min-w-0 flex-col gap-1', className)}>
      <dt className="label-mono text-muted-foreground">{label}</dt>
      <dd className="text-2xl leading-none font-extrabold tracking-tight sm:text-[1.75rem]">{children}</dd>
      {note ? <dd className="truncate text-xs text-muted-foreground">{note}</dd> : null}
    </div>
  )
}

function vehicleList(product: Product): string | undefined {
  const models = product.compatibilities.map((fit) => fit.model)
  if (models.length === 0) return undefined
  if (models.length === 1) return models[0]
  return `${models.slice(0, -1).join(', ')} y ${models[models.length - 1]}`
}

/** Línea tipo zona de lectura mecánica de un pasaporte. Decorativa. */
function machineLine(reference: ReferenceOffers): string {
  const clean = (value: string) => value.toUpperCase().replace(/[^A-Z0-9]/g, '')
  const line = `P<AUTOPARTCO<<${clean(reference.partNumber)}<<${clean(reference.brand?.name ?? '')}`
  return line.padEnd(44, '<')
}

function PassportCard({ reference }: { reference: ReferenceOffers }) {
  const distances = useStoreDistances()
  const origin = useNearbyStores().data?.origin
  const featured = featuredOffer(reference.offers, distances)
  if (!featured) return null

  const available = reference.offers.filter((offer) => offer.availability?.status !== 'out_of_stock')
  const quantities = reference.offers.map((offer) => offer.availability?.quantity)
  const units = quantities.every((value) => value !== undefined)
    ? quantities.reduce<number>((sum, value) => sum + (value ?? 0), 0)
    : undefined
  const nearestKm = featured.store ? distances.get(featured.store.id) : undefined
  const vehicles = vehicleList(featured)
  const inStock = featured.availability && featured.availability.status !== 'out_of_stock'
  const stamps = [
    // "Publicado para", no "Compatible": aún no sabemos si la compatibilidad está confirmada por catálogo.
    vehicles ? { title: 'Publicado para', detail: vehicles, tone: 'brand' as const } : undefined,
    featured.store?.recommended ? { title: 'Tienda recomendada', detail: featured.store.name, tone: 'ink' as const } : undefined,
    inStock
      ? {
          title: 'En stock',
          detail:
            featured.availability?.quantity !== undefined
              ? `${pluralize(featured.availability.quantity, 'unidad', 'unidades')} en esta tienda`
              : undefined,
          tone: 'brand' as const,
        }
      : undefined,
  ].filter((stamp) => stamp !== undefined)

  return (
    <article
      aria-labelledby="pasaporte-repuesto"
      className="relative rounded-2xl bg-card p-5 text-foreground shadow-[0_0_0_1px_rgb(255_255_255/0.08),0_30px_60px_-30px_rgb(0_0_0/0.8)] sm:p-8"
    >
      <header className="flex flex-wrap items-start justify-between gap-x-6 gap-y-2 border-b border-dashed border-input/60 pb-5">
        <div className="min-w-0">
          <p className="label-mono text-muted-foreground">Pasaporte del repuesto</p>
          <h3 id="pasaporte-repuesto" className="mt-1.5 text-2xl leading-tight font-extrabold tracking-tight sm:text-3xl">
            {reference.name}
          </h3>
          <p className="mt-1 text-sm text-muted-foreground">
            {reference.brand ? `${reference.brand.name}, ` : ''}referencia {reference.partNumber}
          </p>
        </div>
        <p className="label-mono rounded-sm bg-concrete px-2 py-1 text-foreground">N.º {reference.partNumber}</p>
      </header>

      <div className="grid gap-6 pt-6 sm:grid-cols-[8.5rem_minmax(0,1fr)]">
        <SmartImage
          src={featured.images[0]}
          alt=""
          width={272}
          height={340}
          frameClassName="hidden aspect-[4/5] rounded-lg outline outline-1 -outline-offset-1 outline-black/10 sm:block"
        />
        {/* El precio ocupa toda la fila: con cifras de millones no cabe en media columna en móvil. */}
        <dl className="grid grid-cols-2 gap-x-6 gap-y-6">
          <Field label={featured.store ? `Precio en ${featured.store.name}` : 'Precio'} className="col-span-2">

            <CountUp value={featured.price} format="cop" />
          </Field>
          {units !== undefined ? (
            <Field label="Unidades disponibles">
              <CountUp value={units} format="int" />
            </Field>
          ) : null}
          <Field label="Almacenes que lo tienen">
            <CountUp value={available.length} format="int" />
          </Field>
          {nearestKm !== undefined ? (
            <Field label="Almacén más cercano" note={origin ? `Desde ${origin.label}` : undefined}>
              <CountUp value={nearestKm} format="km" />
            </Field>
          ) : null}
        </dl>
      </div>

      {stamps.length > 0 ? (
        <ul className="mt-7 flex flex-wrap gap-4 border-t border-dashed border-input/60 pt-6" aria-label="Sellos del repuesto">
          {stamps.map((stamp, index) => (
            <Stamp key={stamp.title} order={index} {...stamp} />
          ))}
        </ul>
      ) : null}

      <p aria-hidden className="mt-6 truncate font-mono text-[0.625rem] tracking-[0.18em] text-muted-foreground/70">
        {machineLine(reference)}
      </p>

      <Link
        to={productPath(featured.id)}
        className="mt-6 inline-flex h-11 items-center rounded-lg bg-brand px-5 font-semibold text-primary-foreground transition-colors hover:bg-brand-deep"
      >
        Ver ficha del repuesto
      </Link>
    </article>
  )
}

function PassportSkeleton() {
  return (
    <div className="rounded-2xl bg-card p-5 sm:p-8" aria-hidden>
      <Skeleton className="h-3 w-40" />
      <Skeleton className="mt-3 h-8 w-3/4" />
      <div className="mt-8 grid grid-cols-2 gap-6">
        {Array.from({ length: 4 }, (_, index) => (
          <div key={index} className="flex flex-col gap-2">
            <Skeleton className="h-3 w-24" />
            <Skeleton className="h-7 w-32" />
          </div>
        ))}
      </div>
    </div>
  )
}

export function PassportSection() {
  const { status, loading, reference, retry } = useMostOfferedReference()

  let card: ReactNode
  if (status === 'error') {
    card = (
      <div className="rounded-2xl bg-card p-2 text-foreground">
        <ErrorState title="No pudimos cargar el pasaporte del repuesto" onRetry={retry} />
      </div>
    )
  } else if (loading) {
    card = (
      <>
        <span className="sr-only" role="status">
          Cargando pasaporte del repuesto
        </span>
        <PassportSkeleton />
      </>
    )
  } else if (!reference) {
    card = (
      <p className="rounded-2xl border border-asphalt-line p-6 text-asphalt-muted">
        Cuando los almacenes publiquen repuestos, aquí verás la ficha de uno de ellos con sus vehículos, existencias y
        distancia.
      </p>
    )
  } else {
    card = <PassportCard reference={reference} />
  }

  return (
    <section aria-labelledby="pasaporte-titulo" className="surface-asphalt py-20 sm:py-28">
      <Container>
        {/* Dos columnas desde xl; en lg la tarjeta tendría ~520px y las cifras no caben en dos columnas. */}
        <m.div {...REVEAL_ONCE} className="grid gap-10 sm:gap-12 xl:grid-cols-[minmax(0,5fr)_minmax(0,7fr)] xl:items-center xl:gap-16">
          <m.div variants={REVEAL_ITEM} className="flex min-w-0 flex-col gap-5">
            <p className="label-mono text-asphalt-muted">La referencia que más almacenes publican</p>
            <h2 id="pasaporte-titulo" className="text-section">
              Pasaporte del repuesto
            </h2>
            <p className="max-w-md text-base text-asphalt-muted sm:text-lg">
              Antes de salir, revisa la ficha: para qué carros se publicó, en cuántos almacenes está y cuál te queda
              más cerca.
            </p>
          </m.div>
          <m.div
            className="min-w-0 xl:max-w-none md:max-w-3xl"
            variants={{ hidden: { opacity: 0, y: 24 }, show: { opacity: 1, y: 0, transition: { duration: 0.6, ease: EASE_THREAD } } }}
          >
            {card}
          </m.div>
        </m.div>
      </Container>
    </section>
  )
}
