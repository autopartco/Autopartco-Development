import { Link } from 'react-router'
import { ShoppingCartIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useCartLines } from '@/features/checkout/lib/cart-source'
import { cn } from '@/lib/utils'

/** Carrito en la cabecera carbón: enlace a /carrito con el número de unidades. */
export function CartIndicator({ className }: { className?: string }) {
  const units = useCartLines().reduce((sum, line) => sum + line.quantity, 0)
  return (
    <Button
      variant="ghost"
      className={cn('relative size-11 text-white hover:bg-header-hover hover:text-white lg:size-10', className)}
      nativeButton={false}
      render={<Link to="/carrito" aria-label={units > 0 ? `Carrito, ${units} ${units === 1 ? 'unidad' : 'unidades'}` : 'Carrito vacío'} />}
    >
      <ShoppingCartIcon className="size-5" aria-hidden />
      {units > 0 ? (
        <span
          className="absolute top-1 right-0.5 grid min-w-5 place-items-center rounded-full bg-brand px-1 text-[0.6875rem] leading-5 font-bold text-white tabular-nums lg:top-0.5"
          aria-hidden
        >
          {units > 99 ? '99+' : units}
        </span>
      ) : null}
    </Button>
  )
}
