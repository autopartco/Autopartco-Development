import { SmartImage } from '@/components/common/smart-image'
import { Button } from '@/components/ui/button'
import { formatCOP, pluralize } from '@/lib/format'
import { cn } from '@/lib/utils'
import type { CartLine } from '../api/types'
import { lineKey } from '../lib/checkout-errors'

/** Aviso por línea tras un 409 o un 404 del servidor. */
export type LineIssue = { kind: 'stock'; available: number } | { kind: 'gone' }

interface StoreGroup {
  id: string
  name: string
  branch?: string
  lines: CartLine[]
}

function groupByStore(lines: CartLine[]): StoreGroup[] {
  const groups = new Map<string, StoreGroup>()
  for (const line of lines) {
    const id = `${line.storeId}:${line.branchId}`
    const group = groups.get(id) ?? { id, name: line.storeName ?? line.storeId, branch: line.branchName, lines: [] }
    group.lines.push(line)
    groups.set(id, group)
  }
  return [...groups.values()]
}

interface CheckoutSummaryProps {
  lines: CartLine[]
  issues: ReadonlyMap<string, LineIssue>
  /** Total informativo: el servidor recalcula al pagar. */
  total: number
  canPay: boolean
  busy: boolean
  onPay: () => void
  /** Texto bajo el botón cuando no se puede pagar (qué falta). */
  blockedReason?: string
}

export function PayButton({ total, canPay, busy, onPay, className }: Pick<CheckoutSummaryProps, 'total' | 'canPay' | 'busy' | 'onPay'> & { className?: string }) {
  return (
    <Button type="button" size="lg" className={cn('w-full', className)} disabled={!canPay || busy} aria-busy={busy || undefined} onClick={onPay}>
      {busy ? 'Procesando el pago…' : `Pagar ${formatCOP(total)}`}
    </Button>
  )
}

/** Resumen del pedido: filas con divisores por tienda y precios alineados a la derecha. */
export function CheckoutSummary({ lines, issues, total, canPay, busy, onPay, blockedReason }: CheckoutSummaryProps) {
  const units = lines.reduce((sum, line) => sum + line.quantity, 0)
  return (
    <section aria-labelledby="resumen-titulo" className="flex flex-col gap-5 rounded-xl border border-ap-line bg-ap-white p-5">
      <div className="flex flex-col gap-1">
        <p className="ap-index">Paso 03 · Confirmar</p>
        <h2 id="resumen-titulo" className="text-block-title text-ap-ink">
          Resumen · {pluralize(units, 'repuesto', 'repuestos')}
        </h2>
      </div>

      <div className="flex flex-col gap-5">
        {groupByStore(lines).map((group) => (
          <div key={group.id} className="flex flex-col">
            <p className="border-b border-ap-line pb-2 text-sm font-semibold text-ap-ink">
              {group.name}
              {group.branch ? <span className="font-normal text-ap-muted"> · {group.branch}</span> : null}
            </p>
            <ul className="flex flex-col divide-y divide-ap-line">
              {group.lines.map((line) => {
                const issue = issues.get(lineKey(line))
                return (
                  <li key={lineKey(line)} className="flex gap-3 py-3">
                    <SmartImage
                      src={line.imageUrl}
                      alt=""
                      width={56}
                      height={56}
                      frameClassName="size-14 shrink-0 rounded-md border border-ap-line"
                      className="p-1"
                    />
                    <div className="flex min-w-0 flex-1 flex-col gap-0.5">
                      <p className="line-clamp-2 text-sm font-medium text-ap-ink">{line.name}</p>
                      <p className="font-mono text-xs text-ap-muted tabular-nums">
                        {line.quantity} × {formatCOP(line.unitPrice)}
                      </p>
                      {issue ? (
                        <p className="text-sm font-semibold text-ap-danger">
                          {issue.kind === 'stock' ? (issue.available > 0 ? `Solo quedan ${issue.available}` : 'Sin unidades disponibles') : 'Ya no está disponible'}
                        </p>
                      ) : null}
                    </div>
                    <p className="ap-price ap-num shrink-0 text-sm text-ap-ink">{formatCOP(line.unitPrice * line.quantity)}</p>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </div>

      <div className="flex flex-col gap-1.5 border-t border-ap-graphite pt-4">
        <div className="flex items-baseline justify-between gap-3">
          <p className="font-medium text-ap-ink">Total</p>
          <p className="ap-price text-xl text-ap-ink">{formatCOP(total)}</p>
        </div>
        <p className="text-xs text-ap-muted">El envío no tiene costo por ahora. El total final lo confirma Autopartco al pagar.</p>
      </div>

      <div className="hidden flex-col gap-2 lg:flex">
        <PayButton total={total} canPay={canPay} busy={busy} onPay={onPay} />
        {!canPay && blockedReason ? <p className="text-center text-sm text-ap-muted">{blockedReason}</p> : null}
      </div>
    </section>
  )
}
