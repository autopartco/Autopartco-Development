import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'
import type { StoreBranch, StoreProfile } from '../../api/types'
import { summarizeOpen } from '../../lib/store-links'

function Fact({ label, children, detail }: { label: string; children: ReactNode; detail?: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1 rounded-lg border border-ap-line bg-ap-white px-4 py-3">
      <dt className="ap-index">{label}</dt>
      <dd className="text-lg leading-tight font-semibold text-ap-ink">{children}</dd>
      {detail ? <dd className="truncate text-xs text-ap-muted">{detail}</dd> : null}
    </div>
  )
}

/**
 * Datos destacados en una franja: repuestos, sedes, servicios y horario de hoy. Solo aparecen los que tienen dato
 * (sin horario publicado no hay tarjeta de horario) y las tarjetas se reparten el ancho sin dejar huecos.
 */
export function StoreFacts({ store, branches }: { store: StoreProfile; branches: StoreBranch[] }) {
  const open = summarizeOpen(branches)
  const branchCount = branches.length > 0 ? branches.length : store.branchCount
  const items: ReactNode[] = []

  items.push(
    <Fact key="products" label="Repuestos">
      <span className="tabular-nums">{store.publishedProducts}</span>
    </Fact>,
  )
  if (branchCount > 0) {
    items.push(
      <Fact key="branches" label={branchCount === 1 ? 'Sede' : 'Sedes'} detail={store.cities.length > 0 ? store.cities.join(', ') : undefined}>
        <span className="tabular-nums">{branchCount}</span>
      </Fact>,
    )
  }
  if (store.services.length > 0) {
    items.push(
      <Fact key="services" label="Servicios">
        <span className="tabular-nums">{store.services.length}</span>
      </Fact>,
    )
  }
  if (open) {
    items.push(
      <Fact key="open" label="Hoy" detail={open.detail}>
        <span className={cn(open.state === 'open' ? 'text-ap-success' : 'text-ap-muted')}>
          {open.state === 'open' ? 'Abierto ahora' : 'Cerrado'}
        </span>
      </Fact>,
    )
  }

  return (
    <dl
      aria-label="Datos de la tienda"
      className="grid grid-cols-2 gap-3 max-lg:[&>*:last-child:nth-child(odd)]:col-span-2 lg:grid-flow-col lg:auto-cols-fr"
    >
      {items}
    </dl>
  )
}
