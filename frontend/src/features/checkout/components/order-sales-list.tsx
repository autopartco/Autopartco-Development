import { StoreIcon } from 'lucide-react'
import { Badge } from '@/components/ui/badge'
import { formatCOP } from '@/lib/format'
import type { BuyerSaleDto } from '../api/types'
import { statusLabel } from '../lib/labels'

/** Una tarjeta por venta (tienda + sucursal) con sus ítems. La reutilizará "Mis pedidos" (F5). */
export function OrderSalesList({ sales }: { sales: BuyerSaleDto[] }) {
  return (
    <ul className="flex flex-col gap-4">
      {sales.map((sale) => (
        <li key={sale.saleId}>
          <section
            aria-label={`${sale.storeName}, ${sale.branchName}`}
            className="flex flex-col gap-4 rounded-2xl bg-card p-5 shadow-[0_0_0_1px_rgb(0_0_0/0.07),0_18px_40px_-28px_rgb(23_27_32/0.35)]"
          >
            <header className="flex flex-wrap items-start justify-between gap-2">
              <div className="flex min-w-0 items-start gap-2.5">
                <StoreIcon className="mt-0.5 size-5 shrink-0 text-muted-foreground" aria-hidden />
                <div className="min-w-0">
                  <h3 className="font-semibold">{sale.storeName}</h3>
                  <p className="text-sm text-muted-foreground">{sale.branchName}</p>
                </div>
              </div>
              <Badge variant="secondary">{statusLabel(sale.status)}</Badge>
            </header>

            <ul className="flex flex-col divide-y divide-border">
              {sale.items.map((item) => (
                <li key={item.productId} className="flex justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{item.name}</p>
                    <p className="text-sm text-muted-foreground">
                      {item.quantity} × {formatCOP(item.unitPrice)}
                      {item.partNumber ? <span className="label-mono ml-2">Ref. {item.partNumber}</span> : null}
                    </p>
                  </div>
                  <p className="shrink-0 text-sm font-semibold tabular-nums">{formatCOP(item.lineTotal)}</p>
                </li>
              ))}
            </ul>

            <dl className="flex flex-col gap-1 border-t border-border pt-3 text-sm">
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Subtotal</dt>
                <dd className="tabular-nums">{formatCOP(sale.subtotal)}</dd>
              </div>
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Envío</dt>
                <dd className="tabular-nums">{sale.shippingCost > 0 ? formatCOP(sale.shippingCost) : 'Sin costo'}</dd>
              </div>
              <div className="flex justify-between gap-3 font-semibold">
                <dt>Total de esta tienda</dt>
                <dd className="tabular-nums">{formatCOP(sale.total)}</dd>
              </div>
            </dl>
          </section>
        </li>
      ))}
    </ul>
  )
}
