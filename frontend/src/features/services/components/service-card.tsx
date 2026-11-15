import { memo, type ReactNode } from 'react'
import { Link } from 'react-router'
import { ClockIcon, StarIcon } from 'lucide-react'
import { SmartImage } from '@/components/common/smart-image'
import { Badge } from '@/components/ui/badge'
import { buttonVariants } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatKm } from '@/features/location/lib/geo'
import { DirectionsMenu } from '@/features/marketplace/components/store/directions-menu'
import type { StoreBranch } from '@/features/marketplace/api/types'
import { formatCOP } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { ServiceSummary } from '../api/types'
import { serviceCategoryLabel } from '../lib/categories'
import { CategoryIcon } from './category-icon'
import { formatDuration, formatPromotionEnd } from '../lib/format'
import { servicePath } from '../lib/paths'

/** Imagen del servicio o, sin ella, el ícono de su categoría sobre fondo papel (no se inventa una foto). */
export function ServiceThumb({ service, eager = false, className }: { service: ServiceSummary; eager?: boolean; className?: string }) {
  if (service.imageUrl) {
    return (
      <SmartImage
        src={service.imageUrl}
        alt=""
        width={400}
        height={300}
        loading={eager ? 'eager' : 'lazy'}
        frameClassName={cn('border border-ap-line', className)}
        className="p-1.5"
      />
    )
  }
  return (
    <div className={cn('flex items-center justify-center border border-ap-line bg-ap-paper text-ap-steel', className)} aria-hidden>
      <CategoryIcon category={service.category} className="size-8" />
    </div>
  )
}

/**
 * Precio «desde». Con promoción: precio anterior tachado, «desde» vigente y la etiqueta. Sin precio: «Precio a consultar»
 * y, si hay promoción, solo su etiqueta. El servidor decide los montos; aquí solo se muestran.
 */
export function ServicePrice({ service, size = 'md', className }: { service: ServiceSummary; size?: 'md' | 'lg'; className?: string }) {
  const { priceFrom, basePriceFrom, promotion } = service
  const until = promotion?.endsAt ? formatPromotionEnd(promotion.endsAt) : undefined
  const promoTag = promotion ? (
    <Badge variant="destructive">
      <span className="sr-only">Promoción </span>
      {promotion.label}
    </Badge>
  ) : null
  return (
    <div className={cn('flex flex-col gap-1', className)}>
      {priceFrom !== undefined ? (
        <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
          <span className="text-sm text-ap-muted">Desde</span>
          <span className={cn('ap-price text-ap-ink', size === 'lg' ? 'text-[1.75rem] leading-none' : 'text-lg leading-tight')}>
            {formatCOP(priceFrom)}
          </span>
          {basePriceFrom !== undefined && basePriceFrom > priceFrom ? (
            <span className="text-sm text-ap-muted tabular-nums line-through">
              <span className="sr-only">Antes </span>
              {formatCOP(basePriceFrom)}
            </span>
          ) : null}
        </div>
      ) : (
        <p className="text-base leading-tight font-semibold text-ap-ink">Precio a consultar</p>
      )}
      {promoTag || until ? (
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ap-muted">
          {promoTag}
          {until ? <span>Hasta {until}</span> : null}
        </p>
      ) : null}
    </div>
  )
}

function OpenNow({ open }: { open: boolean | undefined }) {
  if (open === undefined) return null
  return <Badge variant={open ? 'success' : 'neutral'}>{open ? 'Abierto ahora' : 'Cerrado ahora'}</Badge>
}

/** Sede del servicio con la forma que espera `DirectionsMenu` (solo se usan nombre, dirección y punto). */
function toDirectionsBranch(branch: NonNullable<ServiceSummary['branch']>): StoreBranch {
  return { id: branch.id, name: branch.name, address: branch.address ?? '', city: branch.city, point: branch.point, serviceIds: [] }
}

function StoreLine({ service }: { service: ServiceSummary }) {
  const { store, branch, distanceKm } = service
  if (!store && !branch) return null
  return (
    <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ap-muted">
      {store ? <span className="font-medium text-ap-ink">{store.name}</span> : null}
      {branch?.city ? <span>· {branch.city}</span> : null}
      {distanceKm !== undefined ? <span className="tabular-nums">· sede a {formatKm(distanceKm)}</span> : null}
      {store?.rating !== undefined ? (
        <span className="inline-flex items-center gap-1 tabular-nums">
          <span aria-hidden>·</span>
          <StarIcon className="size-3.5 fill-current text-ap-warning" aria-hidden />
          <span className="sr-only">Calificación de la tienda </span>
          {store.rating.toFixed(1).replace('.', ',')}
        </span>
      ) : null}
    </p>
  )
}

interface ServiceCardProps {
  service: ServiceSummary
  /** `row`: fila de catálogo (resultados). `card`: unidad autónoma (inicio, franja, tienda, relacionados). */
  layout?: 'row' | 'card'
  eager?: boolean
  /** Resaltada por el marcador elegido en el mapa. */
  active?: boolean
  className?: string
}

/**
 * Servicio como resultado. Se distingue de un repuesto por la etiqueta «Servicio» con el ícono de su categoría y por
 * mostrar duración y agenda en lugar de referencia y existencias. El nombre es el destino de tabulación.
 */
export const ServiceCard = memo(function ServiceCard({ service, layout = 'card', eager = false, active = false, className }: ServiceCardProps) {
  const href = servicePath(service.id)
  const kind = (
    <p className="ap-index flex items-center gap-1.5">
      <CategoryIcon category={service.category} className="size-3.5 shrink-0" />
      <span>Servicio / {serviceCategoryLabel(service.category)}</span>
    </p>
  )
  const facts = (
    <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-ap-muted">
      {service.durationMinutes ? (
        <span className="inline-flex items-center gap-1 tabular-nums">
          <ClockIcon className="size-3.5" aria-hidden />
          Aprox. {formatDuration(service.durationMinutes)}
        </span>
      ) : null}
      {service.bookingEnabled ? <span>Agenda en línea</span> : null}
      <OpenNow open={service.branch?.openNow} />
    </p>
  )
  const directions =
    service.store && service.branch ? (
      <DirectionsMenu storeId={service.store.id} branches={[toDirectionsBranch(service.branch)]} serviceId={service.id} variant="ghost" size="sm" className="-ml-2.5 w-fit" />
    ) : null

  if (layout === 'row') {
    return (
      <article
        data-branch-id={service.branch?.id}
        className={cn(
          'grid grid-cols-[5rem_minmax(0,1fr)] gap-x-4 px-3 py-5 transition-colors sm:grid-cols-[7rem_minmax(0,1fr)_11.5rem] sm:gap-x-5',
          active && 'bg-ap-paper shadow-[inset_3px_0_0_var(--ap-red)]',
          className,
        )}
      >
        <Link to={href} tabIndex={-1} aria-hidden className="self-start">
          <ServiceThumb service={service} eager={eager} className="aspect-square w-full rounded-md" />
        </Link>
        <div className="flex min-w-0 flex-col gap-1.5">
          {kind}
          <h3 className="text-base leading-snug font-semibold text-ap-ink">
            <Link to={href} className="underline-offset-4 hover:text-ap-red hover:underline">
              {service.name}
            </Link>
          </h3>
          <StoreLine service={service} />
          {facts}
          {directions}
        </div>
        <div className="col-span-2 mt-4 flex items-end justify-between gap-3 border-t border-ap-line pt-3 sm:col-span-1 sm:mt-0 sm:flex-col sm:items-end sm:justify-start sm:border-t-0 sm:border-l sm:pt-0 sm:pl-5">
          <ServicePrice service={service} className="sm:items-end sm:text-right" />
          <Link to={href} tabIndex={-1} aria-hidden className={cn(buttonVariants({ variant: 'outline' }), 'shrink-0 sm:mt-auto sm:w-full')}>
            Ver servicio
          </Link>
        </div>
      </article>
    )
  }

  return (
    <article className={cn('flex h-full flex-col overflow-hidden rounded-lg border border-ap-line bg-ap-white', className)}>
      <Link to={href} tabIndex={-1} aria-hidden>
        <ServiceThumb service={service} eager={eager} className="aspect-[16/10] w-full border-0 border-b" />
      </Link>
      <div className="flex flex-1 flex-col gap-2 p-4">
        {kind}
        <h3 className="text-base leading-snug font-semibold text-ap-ink">
          <Link to={href} className="underline-offset-4 hover:text-ap-red hover:underline">
            {service.name}
          </Link>
        </h3>
        <StoreLine service={service} />
        {facts}
        <div className="mt-auto flex flex-col gap-3 pt-2">
          <ServicePrice service={service} />
          <Link to={href} tabIndex={-1} aria-hidden className={cn(buttonVariants({ variant: 'outline' }), 'w-full')}>
            Ver servicio
          </Link>
        </div>
      </div>
    </article>
  )
})

export function ServiceCardSkeleton({ className }: { className?: string }) {
  return (
    <div aria-hidden className={cn('flex flex-col overflow-hidden rounded-lg border border-ap-line bg-ap-white', className)}>
      <Skeleton className="aspect-[16/10] w-full rounded-none" />
      <div className="flex flex-col gap-2 p-4">
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-5 w-4/5" />
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="mt-3 h-6 w-1/3" />
        <Skeleton className="h-11 w-full" />
      </div>
    </div>
  )
}

export function ServiceRowSkeleton() {
  return (
    <div aria-hidden className="grid grid-cols-[5rem_minmax(0,1fr)] gap-x-4 px-3 py-5 sm:grid-cols-[7rem_minmax(0,1fr)_11.5rem] sm:gap-x-5">
      <Skeleton className="aspect-square w-full rounded-md" />
      <div className="flex flex-col gap-2">
        <Skeleton className="h-3 w-1/2" />
        <Skeleton className="h-5 w-4/5" />
        <Skeleton className="h-4 w-2/3" />
      </div>
      <Skeleton className="col-span-2 mt-3 h-12 w-full sm:col-span-1 sm:mt-0" />
    </div>
  )
}

/** Rejilla de tarjetas de servicio (inicio, franja, tienda, relacionados). */
export function ServiceGrid({ children, className }: { children: ReactNode; className?: string }) {
  return <ul className={cn('grid gap-4 sm:grid-cols-2', className)}>{children}</ul>
}
