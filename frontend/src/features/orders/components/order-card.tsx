import { Link } from 'react-router'
import { ChevronRightIcon, StoreIcon } from 'lucide-react'
import { formatCOP } from '@/lib/format'
import { formatOrderDate } from '@/features/checkout/lib/labels'
import { StatusBadge } from '@/features/portal/components/status-badge'
import type { BuyerOrderSummaryDto } from '../api/types'
import { orderDetailPath, orderStatus } from '../lib/labels'

/** Resumen de un pedido en el listado; toda la tarjeta lleva al detalle. */
export function OrderCard({ order }: { order: BuyerOrderSummaryDto }) {
  const status = orderStatus(order.status)
  return (
    <Link
      to={orderDetailPath(order.orderId)}
      className="group flex flex-col gap-3 rounded-2xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07),0_18px_40px_-28px_rgb(23_27_32/0.35)] transition-shadow hover:shadow-[0_0_0_1px_rgb(0_0_0/0.18),0_18px_40px_-28px_rgb(23_27_32/0.45)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <p className="label-mono text-muted-foreground">Pedido</p>
          <h2 className="font-mono text-lg font-bold">#{order.orderNumber}</h2>
          <p className="text-sm text-muted-foreground">{formatOrderDate(order.createdAt)}</p>
        </div>
        <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
      </div>

      <ul className="flex flex-col gap-1.5">
        {order.sales.map((sale) => (
          <li key={sale.saleId} className="flex flex-wrap items-center justify-between gap-2 text-sm">
            <span className="flex min-w-0 items-center gap-2">
              <StoreIcon className="size-4 shrink-0 text-muted-foreground" aria-hidden />
              <span className="truncate">{sale.storeName}</span>
            </span>
            <span className="text-muted-foreground">{orderStatus(sale.status).label}</span>
          </li>
        ))}
      </ul>

      <div className="flex items-center justify-between gap-3 border-t border-border pt-3">
        <p className="text-sm text-muted-foreground">
          {order.itemCount} {order.itemCount === 1 ? 'unidad' : 'unidades'} ·{' '}
          <span className="font-bold text-foreground tabular-nums">{formatCOP(order.total)}</span>
        </p>
        <span className="inline-flex items-center gap-1 text-sm font-semibold">
          Ver detalle
          <ChevronRightIcon className="size-4 transition-transform group-hover:translate-x-0.5" aria-hidden />
        </span>
      </div>
    </Link>
  )
}
