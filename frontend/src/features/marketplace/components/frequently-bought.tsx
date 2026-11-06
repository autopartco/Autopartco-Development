import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { CheckIcon, ShoppingCartIcon } from 'lucide-react'
import { SmartImage } from '@/components/common/smart-image'
import { Button } from '@/components/ui/button'
import { addCartLine } from '@/features/checkout/lib/cart-source'
import { formatCOP } from '@/lib/format'
import { getMarketplaceService } from '../api/marketplace-service'
import type { FrequentlyBoughtItem, FrequentlyBoughtReason } from '../api/types'
import { useFrequentlyBought } from '../hooks'
import { productPath } from '../lib/paths'

const REASON_TEXT: Record<FrequentlyBoughtReason, string> = {
  ORDERS: 'Comprado junto por otros clientes',
  COMPATIBILITY: 'Compatible con el mismo vehículo',
  CATEGORY: 'De la misma categoría',
}

function Suggestion({ item }: { item: FrequentlyBoughtItem }) {
  const [state, setState] = useState<'idle' | 'adding' | 'added'>('idle')

  async function add() {
    setState('adding')
    try {
      // El carrito necesita sucursal y tienda: se toman de la ficha del producto, que además confirma existencias.
      const product = await (await getMarketplaceService()).getProduct(item.productId)
      if (!product.branchId || product.availability?.status === 'out_of_stock') {
        toast.error(`“${product.name}” no está disponible para comprar en línea por ahora.`)
        setState('idle')
        return
      }
      addCartLine(
        {
          productId: product.id,
          branchId: product.branchId,
          name: product.name,
          storeId: product.store?.id ?? product.branchId,
          storeName: product.store?.name,
          branchName: product.availability?.branchName,
          unitPrice: product.price,
          imageUrl: product.images[0]?.thumbUrl,
        },
        1,
      )
      setState('added')
    } catch {
      toast.error('No pudimos agregar el producto al carrito. Inténtalo de nuevo.')
      setState('idle')
    }
  }

  return (
    <li className="flex gap-3 rounded-xl bg-card p-3 shadow-[0_0_0_1px_rgb(0_0_0/0.07)]">
      <SmartImage src={item.image} alt="" width={96} height={72} frameClassName="aspect-[4/3] w-24 shrink-0 rounded-md" />
      <div className="flex min-w-0 flex-1 flex-col gap-1.5">
        <Link to={productPath(item.productId)} className="line-clamp-2 text-sm leading-snug font-semibold underline-offset-4 hover:underline">
          {item.name}
        </Link>
        <p className="text-xs text-muted-foreground">{REASON_TEXT[item.reason]}</p>
        <p className="font-semibold tabular-nums">{formatCOP(item.price)}</p>
        <div className="mt-auto flex flex-wrap items-center gap-x-3 gap-y-1">
          <Button type="button" variant="outline" className="h-11 px-3" disabled={state === 'adding'} onClick={() => void add()}>
            {state === 'added' ? <CheckIcon aria-hidden /> : <ShoppingCartIcon aria-hidden />}
            {state === 'adding' ? 'Agregando…' : state === 'added' ? 'Agregado' : 'Agregar al carrito'}
          </Button>
          {state === 'added' ? (
            <Link to="/carrito" className="inline-flex min-h-11 items-center text-sm font-semibold text-brand underline-offset-4 hover:underline">
              Ver carrito
            </Link>
          ) : null}
        </div>
      </div>
    </li>
  )
}

/** «Se compran juntos» en la ficha: hasta 6 productos que suelen llevarse con este. Sin sugerencias, no se muestra. */
export function FrequentlyBought({ productId }: { productId: string }) {
  const items = useFrequentlyBought(productId)
  if (!items.data || items.data.length === 0) return null
  return (
    <section aria-labelledby="se-compran-juntos" className="flex flex-col gap-4">
      <h2 id="se-compran-juntos" className="text-block-title">
        Se compran juntos
      </h2>
      <ul className="grid gap-3 sm:grid-cols-2">
        {items.data.map((item) => (
          <Suggestion key={item.productId} item={item} />
        ))}
      </ul>
    </section>
  )
}
