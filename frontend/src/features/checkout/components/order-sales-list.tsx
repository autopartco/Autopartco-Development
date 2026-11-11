import { StoreIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { formatCOP } from '@/lib/format'
import type { BuyerSaleDto } from '../api/types'
import { statusLabel } from '../lib/labels'

/**
 * Una sección por venta (tienda + sucursal) con sus ítems en filas y los importes alineados a la
 * derecha. El estado va en texto (no solo color). La reutiliza "Mis pedidos".
 */
export function OrderSalesList({ sales }: { sales: BuyerSaleDto[] }) {
  return (
    <ul className="flex flex-col gap-8">
      {sales.map((sale) => (
        <li key={sale.saleId}>
          <section aria-label={`${sale.storeName}, ${sale.branchName}`} className="flex flex-col">
            <header className="flex flex-wrap items-start justify-between gap-2 border-b border-ap-graphite pb-2">
              <div className="flex min-w-0 items-start gap-2.5">
                <StoreIcon className="mt-0.5 size-5 shrink-0 text-ap-muted" aria-hidden />
                <div className="min-w-0">
                  <h3 className="font-semibold text-ap-ink">{sale.storeName}</h3>
                  <p className="text-sm text-ap-muted">{/^sucursal\b/i.test(sale.branchName) ? sale.branchName : `Sucursal ${sale.branchName}`}</p>
                </div>
              </div>
              <Badge variant="neutral">
                <span className="sr-only">Estado: </span>
                {statusLabel(sale.status)}
              </Badge>
            </header>

            <ul className="flex flex-col divide-y divide-ap-line">
              {sale.items.map((item) => (
                <li key={item.productId} className="flex justify-between gap-3 py-3">
                  <div className="min-w-0">
                    {item.partNumber ? <p className="ap-ref text-xs text-ap-muted">REF. {item.partNumber}</p> : null}
                    <p className="text-sm font-medium text-ap-ink">{item.name}</p>
                    <p className="font-mono text-xs text-ap-muted tabular-nums">
                      {item.quantity} × {formatCOP(item.unitPrice)}
                    </p>
                  </div>
                  <p className="ap-price ap-num shrink-0 text-sm text-ap-ink">{formatCOP(item.lineTotal)}</p>
                </li>
              ))}
            </ul>

            <dl className="flex flex-col gap-1 border-t border-ap-line pt-3 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-ap-muted">Subtotal</dt>
                <dd className="ap-num text-ap-ink">{formatCOP(sale.subtotal)}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-ap-muted">Envío</dt>
                <dd className="ap-num text-ap-ink">{sale.shippingCost > 0 ? formatCOP(sale.shippingCost) : 'Sin costo'}</dd>
              </div>
              <div className="flex justify-between gap-3 font-semibold text-ap-ink">
                <dt>Total de esta tienda</dt>
                <dd className="ap-price ap-num">{formatCOP(sale.total)}</dd>
              </div>
            </dl>
          </section>
        </li>
      ))}
    </ul>
  )
}
