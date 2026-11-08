import { useState } from 'react'
import { Link } from 'react-router'
import { ChevronRightIcon } from 'lucide-react'
import { Skeleton } from '@/components/ui/skeleton'
import { formatKm } from '@/features/location/lib/geo'
import { initials, pluralize } from '@/lib/format'
import type { StoreSummary } from '../api/types'
import { storePath } from '../lib/paths'
import { RecommendedMark } from './product-meta'

function NoData() {
  return (
    <>
      <span className="text-ap-muted" aria-hidden>
        —
      </span>
      <span className="sr-only">Sin dato</span>
    </>
  )
}

function StoreAvatar({ store }: { store: StoreSummary }) {
  const [failed, setFailed] = useState(false)
  if (store.logoUrl && !failed) {
    return (
      <img
        src={store.logoUrl}
        alt=""
        width={44}
        height={44}
        loading="lazy"
        className="size-11 shrink-0 rounded-md border border-ap-line bg-ap-white object-contain"
        onError={() => setFailed(true)}
      />
    )
  }
  return (
    <span className="grid size-11 shrink-0 place-items-center rounded-md bg-ap-graphite text-sm font-semibold text-ap-white" aria-hidden>
      {initials(store.name) || store.name.charAt(0)}
    </span>
  )
}

/**
 * Tienda como fila de directorio: nombre, ciudad, distintivo y cifras a la derecha.
 * Ciudad, distintivo, número de repuestos y distancia solo si el servicio los informa.
 * Toda la fila es clicable mediante el enlace del nombre.
 */
export function StoreRow({ store, distanceKm }: { store: StoreSummary; distanceKm?: number }) {
  return (
    <article className="group relative grid grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-x-4 gap-y-1 px-1 py-4 transition-colors hover:bg-ap-paper sm:grid-cols-[2.75rem_minmax(0,1fr)_9rem_7rem_1.25rem] sm:px-3">
      <StoreAvatar store={store} />
      <div className="flex min-w-0 flex-col gap-1">
        <h2 className="text-base leading-snug font-semibold text-ap-ink">
          <Link
            to={storePath(store.id)}
            data-stretched-link
            className="after:absolute after:inset-0 focus-visible:after:outline-2 focus-visible:after:-outline-offset-2 focus-visible:after:outline-[var(--focus)] group-hover:text-ap-red"
          >
            {store.name}
          </Link>
        </h2>
        <p className="flex flex-wrap items-center gap-x-2 gap-y-1 text-sm text-ap-muted">
          {store.city ? <span>{store.city}</span> : null}
          {store.recommended ? <RecommendedMark /> : null}
          {/* En móvil las cifras van bajo el nombre; desde sm tienen su columna. */}
          {store.productCount !== undefined ? (
            <span className="tabular-nums sm:hidden">· {pluralize(store.productCount, 'repuesto', 'repuestos')}</span>
          ) : null}
          {distanceKm !== undefined ? <span className="tabular-nums sm:hidden">· sede a {formatKm(distanceKm)}</span> : null}
        </p>
      </div>
      <p className="hidden text-right text-sm text-ap-ink tabular-nums sm:block">
        {store.productCount !== undefined ? pluralize(store.productCount, 'repuesto', 'repuestos') : <NoData />}
      </p>
      <p className="hidden text-right text-sm text-ap-ink tabular-nums sm:block">
        {distanceKm !== undefined ? (
          <>
            {formatKm(distanceKm)}
            <span className="sr-only"> hasta la sede más cercana</span>
          </>
        ) : (
          <NoData />
        )}
      </p>
      <ChevronRightIcon className="size-5 text-ap-steel group-hover:text-ap-red" aria-hidden />
    </article>
  )
}

export function StoreRowSkeleton() {
  return (
    <div className="flex items-center gap-4 px-1 py-4 sm:px-3" aria-hidden>
      <Skeleton className="size-11 rounded-md" />
      <div className="flex flex-1 flex-col gap-2">
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="h-3 w-1/4" />
      </div>
    </div>
  )
}
