import { Link } from 'react-router'
import { ShoppingCartIcon, Trash2Icon } from 'lucide-react'
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
          <Button nativeButton={false} render={<Link to="/buscar" />}>
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

      <div className="grid items-start gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex flex-col gap-8">
          {groupByStore(lines).map((group) => (
            <section key={group.id} aria-label={`Repuestos de ${group.name}`}>
              <h2 className="flex flex-wrap items-baseline gap-x-2 border-b border-ap-graphite pb-2 text-sm font-semibold text-ap-ink">
                {group.name}
                {group.branch ? <span className="font-normal text-ap-muted">{/^sucursal\b/i.test(group.branch) ? group.branch : `Sucursal ${group.branch}`}</span> : null}
              </h2>
              <ul className="flex flex-col divide-y divide-ap-line border-b border-ap-line">
                {group.lines.map((line) => (
                  <li
                    key={lineKey(line)}
                    className="grid grid-cols-[4.5rem_minmax(0,1fr)] gap-x-4 gap-y-3 py-4 sm:grid-cols-[4.5rem_minmax(0,1fr)_auto_7.5rem_2.75rem] sm:items-center"
                  >
                    <SmartImage
                      src={line.imageUrl}
                      alt=""
                      width={72}
                      height={72}
                      frameClassName="row-span-2 size-[4.5rem] shrink-0 rounded-md border border-ap-line sm:row-span-1"
                      className="p-1"
                    />
                    <div className="flex min-w-0 flex-col gap-1">
                      <Link
                        to={productPath(line.productId)}
                        className="line-clamp-2 font-semibold text-ap-ink underline-offset-4 hover:text-ap-red hover:underline"
                      >
                        {line.name}
                      </Link>
                      <p className="text-sm text-ap-muted">
                        <span className="tabular-nums">{formatCOP(line.unitPrice)}</span> c/u
                      </p>
                    </div>
                    {/* En móvil: cantidad, subtotal y quitar en una fila bajo el nombre; desde sm, columnas propias. */}
                    <div className="col-start-2 flex items-center justify-between gap-3 sm:contents">
                      <QuantityStepper label={line.name} value={line.quantity} onChange={(value) => setCartLineQuantity(line, value)} />
                      <p className="ap-price ap-num text-ap-ink">
                        <span className="sr-only">Subtotal: </span>
                        {formatCOP(line.unitPrice * line.quantity)}
                      </p>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        className="text-ap-muted hover:text-ap-danger"
                        aria-label={`Quitar ${line.name} del carrito`}
                        onClick={() => removeCartLines([line])}
                      >
                        <Trash2Icon aria-hidden />
                      </Button>
                    </div>
                  </li>
                ))}
              </ul>
            </section>
          ))}
          <div>
            <Button type="button" variant="ghost" className="px-3 text-ap-muted" onClick={clearCart}>
              Vaciar carrito
            </Button>
          </div>
        </div>

        <aside
          aria-labelledby="carrito-resumen"
          className="flex flex-col gap-4 rounded-xl border border-ap-line bg-ap-white p-5 lg:sticky lg:top-[calc(var(--header-h)+1rem)]"
        >
          <h2 id="carrito-resumen" className="text-block-title text-ap-ink">
            Resumen
          </h2>
          <dl className="flex flex-col divide-y divide-ap-line border-y border-ap-line text-sm">
            <div className="flex items-baseline justify-between gap-3 py-2.5">
              <dt className="text-ap-muted">Unidades</dt>
              <dd className="font-mono tabular-nums text-ap-ink">{units}</dd>
            </div>
            <div className="flex items-baseline justify-between gap-3 py-3">
              <dt className="font-medium text-ap-ink">Total estimado</dt>
              <dd className="ap-price text-xl text-ap-ink">{formatCOP(total)}</dd>
            </div>
          </dl>
          <p className="text-xs text-ap-muted">Sin costo de envío por ahora. El total final lo confirma Autopartco al pagar.</p>
          <Button nativeButton={false} render={<Link to={checkoutPath()} />}>
            Continuar al pago
          </Button>
          <Button variant="outline" nativeButton={false} render={<Link to="/buscar" />}>
            Seguir comprando
          </Button>
        </aside>
      </div>
    </Container>
  )
}
