import { useState } from 'react'
import { Link } from 'react-router'
import { MapPinIcon } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { initials, pluralize } from '@/lib/format'
import { formatKm } from '@/features/location/lib/geo'
import type { StoreSummary } from '../api/types'
import { storeProductsPath } from '../lib/paths'
import { RecommendedMark } from './product-meta'

function StoreAvatar({ store }: { store: StoreSummary }) {
  const [failed, setFailed] = useState(false)
  if (store.logoUrl && !failed) {
    return (
      <img
        src={store.logoUrl}
        alt=""
        width={48}
        height={48}
        loading="lazy"
        className="size-12 rounded-md bg-concrete object-contain"
        onError={() => setFailed(true)}
      />
    )
  }
  return (
    <span className="grid size-12 shrink-0 place-items-center rounded-md bg-asphalt text-sm font-semibold text-white" aria-hidden>
      {initials(store.name) || store.name.charAt(0)}
    </span>
  )
}

/**
 * Tienda con enlace a sus repuestos. Ciudad, verificación y distancia solo si
 * el servicio las informa.
 */
export function StoreCard({ store, distanceKm }: { store: StoreSummary; distanceKm?: number }) {
  return (
    <article className="relative flex h-full items-start gap-4 rounded-xl bg-card p-4 shadow-[0_0_0_1px_rgb(0_0_0/0.07)] transition-shadow hover:shadow-[0_0_0_1px_rgb(23_27_32/0.3)] has-[a:focus-visible]:shadow-[0_0_0_1px_rgb(23_27_32/0.3)]">
      <StoreAvatar store={store} />
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        {distanceKm !== undefined ? (
          <span className="w-fit rounded-sm bg-asphalt px-1.5 py-0.5 label-mono text-white tabular-nums">
            Sede a {formatKm(distanceKm)}
            <span className="sr-only"> de tu punto de referencia</span>
          </span>
        ) : null}
        <h2 className="text-lg leading-tight font-bold tracking-tight">
          <Link
            to={storeProductsPath(store.id)}
            data-stretched-link
            className="after:absolute after:inset-0 after:rounded-lg focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[var(--focus)]"
          >
            {store.name}
          </Link>
        </h2>
        <p className="flex flex-wrap items-center gap-x-3 gap-y-1 text-sm text-muted-foreground">
          {store.city ? (
            <span className="inline-flex items-center gap-1">
              <MapPinIcon className="size-3.5" aria-hidden />
              {store.city}
            </span>
          ) : null}
          {store.recommended ? <RecommendedMark /> : null}
          {store.productCount !== undefined ? <span>{pluralize(store.productCount, 'repuesto', 'repuestos')}</span> : null}
        </p>
      </div>
    </article>
  )
}

export function StoreCardSkeleton() {
  return (
    <div className="flex items-start gap-4 rounded-xl bg-card p-4 shadow-[0_0_0_1px_rgb(0_0_0/0.07)]" aria-hidden>
      <Skeleton className="size-12 rounded-md" />
      <div className="flex flex-1 flex-col gap-2">
        <Skeleton className="h-4 w-2/3" />
        <Skeleton className="h-3 w-1/3" />
      </div>
    </div>
  )
}
