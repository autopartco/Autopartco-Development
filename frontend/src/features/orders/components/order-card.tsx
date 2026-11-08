import { Link } from 'react-router'
import { ChevronRightIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { formatCOP } from '@/lib/format'
import { formatOrderDate } from '@/features/checkout/lib/labels'
import type { BuyerOrderSummaryDto } from '../api/types'
import { buyerOrderStatus, isUnpaidOrder, orderDetailPath, orderStatus } from '../lib/labels'

/** Columnas de la lista de pedidos desde `sm` (la cabecera de la lista usa las mismas). */
export const ORDER_ROW_GRID = 'sm:grid-cols-[9rem_minmax(0,1fr)_minmax(0,13rem)_8rem_1.25rem] sm:items-center sm:gap-x-5'

/**
 * Fila de un pedido en "Mis pedidos": número, fecha, tiendas, estado en texto y total alineado
 * a la derecha. Un solo destino de tabulación (el número), que extiende su área a toda la fila.
 */
export function OrderCard({ order }: { order: BuyerOrderSummaryDto }) {
  const status = buyerOrderStatus(order.status)
  // Sin pago aprobado las tiendas aún no tienen la venta: su estado no aporta.
  const unpaid = isUnpaidOrder(order.status)
  return (
    <article className={`group relative grid grid-cols-[minmax(0,1fr)_auto] gap-x-4 gap-y-2 px-2 py-4 transition-colors hover:bg-ap-paper sm:px-3 ${ORDER_ROW_GRID}`}>
      <div className="order-1 min-w-0">
        <h2 className="font-mono text-base font-semibold text-ap-ink tabular-nums">
          <Link
            to={orderDetailPath(order.orderId)}
            data-stretched-link
            className="outline-none after:absolute after:inset-0 after:rounded-md focus-visible:after:outline-2 focus-visible:after:outline-offset-2 focus-visible:after:outline-[var(--focus)] group-hover:text-ap-red"
          >
            <span className="sr-only">Pedido </span>#{order.orderNumber}
          </Link>
        </h2>
        <p className="text-sm text-ap-muted">
          <time dateTime={order.createdAt}>{formatOrderDate(order.createdAt)}</time>
        </p>
      </div>

      <ul className="order-3 col-span-2 flex min-w-0 flex-col gap-1 text-sm sm:order-none sm:col-span-1">
        {order.sales.map((sale) => (
          <li key={sale.saleId} className="flex min-w-0 flex-wrap items-baseline gap-x-2">
            <span className="truncate font-medium text-ap-ink">{sale.storeName}</span>
            {unpaid ? null : <span className="text-ap-muted">{orderStatus(sale.status).label}</span>}
          </li>
        ))}
      </ul>

      <div className="order-2 justify-self-end sm:order-none sm:justify-self-start">
        <Badge variant={status.tone} className="h-auto min-h-5 py-0.5 whitespace-normal">
          <span className="sr-only">Estado: </span>
          {status.label}
        </Badge>
      </div>

      <p className="order-4 col-span-2 flex items-baseline justify-between gap-3 border-t border-ap-line pt-2 sm:order-none sm:col-span-1 sm:block sm:border-0 sm:pt-0 sm:text-right">
        <span className="font-mono text-xs text-ap-muted tabular-nums sm:block">
          {order.itemCount} {order.itemCount === 1 ? 'unidad' : 'unidades'}
        </span>
        <span className="ap-price text-ap-ink">
          <span className="sr-only">Total: </span>
          {formatCOP(order.total)}
        </span>
      </p>

      <ChevronRightIcon className="hidden size-5 text-ap-muted group-hover:text-ap-red sm:block" aria-hidden />
    </article>
  )
}
