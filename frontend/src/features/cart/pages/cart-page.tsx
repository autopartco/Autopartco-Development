import { Link } from 'react-router'
import { PackageIcon, ShoppingCartIcon, Trash2Icon } from 'lucide-react'
import { Container, PageIntro } from '@/components/common/page-header'
import { SmartImage } from '@/components/common/smart-image'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import type { CartLine } from '@/features/checkout/api/types'
import { lineKey } from '@/features/checkout/lib/checkout-errors'
import { clearCart, removeCartLines, setCartLineQuantity, useCartLines } from '@/features/checkout/lib/cart-source'
import { checkoutPath } from '@/features/checkout/lib/paths'
import { productPath } from '@/features/marketplace/lib/paths'
import { formatCOP, pluralize } from '@/lib/format'
import { QuantityStepper } from '../components/quantity-stepper'

const CRUMBS = [{ label: 'Inicio', to: '/' }, { label: 'Carrito' }]
const CARD = 'rounded-2xl bg-card shadow-[0_0_0_1px_rgb(0_0_0/0.07),0_18px_40px_-28px_rgb(23_27_32/0.35)]'

function groupByStore(lines: CartLine[]) {
  const groups = new Map<string, { id: string; name: string; branch?: string; lines: CartLine[] }>()
  for (const line of lines) {
    const id = `${line.storeId}:${line.branchId}`
    const group = groups.get(id) ?? { id, name: line.storeName ?? line.storeId, branch: line.branchName, lines: [] }
    group.lines.push(line)
    groups.set(id, group)
  }
  return [...groups.values()]
}

export default function CartPage() {
  const lines = useCartLines()
  const units = lines.reduce((sum, line) => sum + line.quantity, 0)
  const total = lines.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0)

  if (lines.length === 0) {
    return (
      <Container className="py-6 sm:py-8">
        <title>Carrito | Autopartco</title>
        <PageIntro crumbs={CRUMBS} label="Carrito" title="Tu carrito está vacío" />
        <EmptyState icon={<ShoppingCartIcon />} title="Aún no has agregado repuestos" description="Busca el repuesto que necesitas, compara tiendas y agrégalo al carrito.">
          <Button className="h-11 px-5" nativeButton={false} render={<Link to="/buscar" />}>
            Buscar repuestos
          </Button>
        </EmptyState>
      </Container>
    )
  }

  return (
    <Container className="py-6 sm:py-8">
      <title>Carrito | Autopartco</title>
      <PageIntro crumbs={CRUMBS} label={pluralize(units, 'repuesto', 'repuestos')} title="Tu carrito" description="Revisa las cantidades. El precio y las existencias se confirman al pagar." />

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_22rem] lg:gap-8">
        <div className="flex flex-col gap-4">
          {groupByStore(lines).map((group) => (
            <section key={group.id} aria-label={`Repuestos de ${group.name}`} className={`${CARD} p-5`}>
              <p className="label-mono mb-3 text-muted-foreground">
                {group.name}
                {group.branch ? ` · ${group.branch}` : ''}
              </p>
              <ul className="flex flex-col divide-y divide-border">
                {group.lines.map((line) => (
                  <li key={lineKey(line)} className="flex flex-col gap-3 py-4 first:pt-0 last:pb-0 sm:flex-row sm:items-center">
                    <div className="flex min-w-0 flex-1 gap-3">
                      {line.imageUrl ? (
                        <SmartImage src={line.imageUrl} alt="" width={72} height={72} frameClassName="size-[4.5rem] shrink-0 rounded-lg" />
                      ) : (
                        <div className="grid size-[4.5rem] shrink-0 place-items-center rounded-lg bg-concrete text-muted-foreground" aria-hidden>
                          <PackageIcon className="size-7" />
                        </div>
                      )}
                      <div className="flex min-w-0 flex-col gap-0.5">
                        <Link to={productPath(line.productId)} className="line-clamp-2 font-medium underline-offset-4 hover:underline">
                          {line.name}
                        </Link>
                        <p className="text-sm text-muted-foreground">{formatCOP(line.unitPrice)} c/u</p>
                      </div>
                    </div>
                    <div className="flex items-center justify-between gap-3 sm:justify-end">
                      <QuantityStepper label={line.name} value={line.quantity} onChange={(value) => setCartLineQuantity(line, value)} />
                      <p className="w-24 text-right font-semibold tabular-nums">{formatCOP(line.unitPrice * line.quantity)}</p>
                      <Button type="button" variant="ghost" className="size-11" aria-label={`Quitar ${line.name} del carrito`} onClick={() => removeCartLines([line])}>
                        <Trash2Icon aria-hidden />
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          ))}
          <div>
            <Button type="button" variant="ghost" className="h-11 px-3 text-muted-foreground" onClick={clearCart}>
              Vaciar carrito
            </Button>
          </div>
        </div>

        <aside aria-labelledby="carrito-resumen" className={`${CARD} flex flex-col gap-4 p-5 lg:sticky lg:top-[calc(var(--header-h)+1rem)]`}>
          <h2 id="carrito-resumen" className="text-block-title">
            Resumen
          </h2>
          <div className="flex items-baseline justify-between gap-3">
            <p className="font-medium">Total estimado</p>
            <p className="text-2xl font-extrabold tracking-tight tabular-nums">{formatCOP(total)}</p>
          </div>
          <p className="text-xs text-muted-foreground">Sin costo de envío por ahora. El total final lo confirma Autopartco al pagar.</p>
          <Button className="h-12 text-base" nativeButton={false} render={<Link to={checkoutPath()} />}>
            Continuar al pago
          </Button>
          <Button variant="outline" className="h-11" nativeButton={false} render={<Link to="/buscar" />}>
            Seguir comprando
          </Button>
        </aside>
      </div>
    </Container>
  )
}
