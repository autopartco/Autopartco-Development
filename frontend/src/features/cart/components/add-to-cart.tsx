import { useState } from 'react'
import { Link } from 'react-router'
import { CheckIcon, ShoppingCartIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import type { Product } from '@/features/marketplace/api/types'
import { addCartLine } from '@/features/checkout/lib/cart-source'
import { QuantityStepper } from './quantity-stepper'

/** Existencias que el listado informa; sin dato, el tope es el del carrito (99). */
function maxFor(product: Product): number {
  const stock = product.availability?.quantity
  return Math.min(99, typeof stock === 'number' ? stock : 99)
}

/**
 * Cantidad + "Agregar al carrito". El carrito vive en el navegador (`localStorage`); el servidor
 * valida stock y precio al pagar. Sin sucursal o sin existencias no se puede comprar.
 */
export function AddToCart({ product }: { product: Product }) {
  const max = maxFor(product)
  const [quantity, setQuantity] = useState(1)
  const [added, setAdded] = useState<number>()

  if (!product.branchId || product.availability?.status === 'out_of_stock' || max < 1) {
    return (
      <p className="rounded-md border border-ap-line bg-ap-paper px-4 py-3 text-sm text-ap-ink">
        {product.availability?.status === 'out_of_stock'
          ? 'Este repuesto está agotado por ahora. Vuelve pronto o mira otras tiendas que lo tengan.'
          : 'Este listado todavía no se puede comprar en línea. Consulta con la tienda.'}
      </p>
    )
  }

  const branchId = product.branchId

  function handleAdd() {
    const total = addCartLine(
      {
        productId: product.id,
        branchId,
        name: product.name,
        storeId: product.store?.id ?? branchId,
        storeName: product.store?.name,
        branchName: product.availability?.branchName,
        unitPrice: product.price,
        imageUrl: product.images[0]?.thumbUrl,
      },
      quantity,
    )
    setAdded(total)
  }

  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-3">
        <QuantityStepper label={product.name} value={quantity} max={max} onChange={setQuantity} />
        <Button type="button" className="flex-1 px-5" onClick={handleAdd}>
          <ShoppingCartIcon aria-hidden />
          Agregar al carrito
        </Button>
      </div>
      <div aria-live="polite">
        {added !== undefined ? (
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-md border border-ap-success/25 bg-ap-success-soft px-3 py-1 text-sm text-ap-ink">
            <span className="flex items-center gap-1.5 font-semibold">
              <CheckIcon className="size-4 text-ap-success" aria-hidden />
              Agregado: {added === 1 ? '1 unidad' : `${added} unidades`} en tu carrito
            </span>
            <Link to="/carrito" className="inline-flex min-h-11 items-center font-semibold text-ap-red underline-offset-4 hover:text-ap-red-hover hover:underline">
              Ver carrito
            </Link>
          </p>
        ) : null}
      </div>
      <p className="text-xs text-ap-muted">El precio y las existencias se confirman al pagar.</p>
    </div>
  )
}
