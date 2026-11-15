import { Link } from 'react-router'
import { pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { StoresStrip as StoresStripData } from '../../api/types'
import { StoreResultRow } from '../store-card'

/**
 * Franja de hasta 4 almacenes de la búsqueda unificada («Todo»), análoga a la de servicios.
 * «Ver todas las tiendas» abre la pestaña Tiendas con la misma búsqueda.
 */
export function StoresStrip({ strip, allHref, className }: { strip: StoresStripData; allHref: string; className?: string }) {
  if (strip.items.length === 0) return null
  return (
    <section aria-labelledby="franja-tiendas-titulo" className={cn('flex flex-col gap-3 border-t border-ap-graphite pt-5', className)}>
      <div className="flex flex-wrap items-end justify-between gap-x-4 gap-y-1">
        <div className="flex flex-col gap-1">
          <p className="ap-index">Tiendas / Coincidencias</p>
          <h2 id="franja-tiendas-titulo" className="text-block-title text-ap-ink">
            Tiendas para tu búsqueda
          </h2>
        </div>
        <Link to={allHref} className="inline-flex min-h-11 items-center text-sm font-semibold text-ap-red underline-offset-4 hover:underline">
          Ver todas las tiendas ({strip.total})
          <span className="sr-only">: {pluralize(strip.total, 'tienda coincide', 'tiendas coinciden')}</span>
        </Link>
      </div>
      <ul className="flex flex-col divide-y divide-ap-line border-y border-ap-line">
        {strip.items.map((store) => (
          <li key={store.id}>
            <StoreResultRow store={store} />
          </li>
        ))}
      </ul>
    </section>
  )
}
