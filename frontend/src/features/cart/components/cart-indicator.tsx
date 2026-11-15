import { useState } from 'react'
import { Link } from 'react-router'
import { ShoppingCartIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useCartLines } from '@/features/checkout/lib/cart-source'
import { cn } from '@/lib/utils'

/**
 * Carrito en la cabecera carbón: enlace a /carrito con el número de unidades. Al cambiar el número, el contador hace
 * un «bump» (1 → 1,25 → 1 en 300 ms) y la cifra entra con un pequeño desplazamiento vertical (solo después del primer
 * render: al cargar la página no se anima).
 */
export function CartIndicator({ className }: { className?: string }) {
  const units = useCartLines().reduce((sum, line) => sum + line.quantity, 0)
  const [previous, setPrevious] = useState(units)
  const [changes, setChanges] = useState(0)
  if (units !== previous) {
    setPrevious(units)
    setChanges((count) => count + 1)
  }
  return (
    <Button
      variant="ghost"
      className={cn('relative size-11 text-white hover:bg-sidebar-accent hover:text-white', className)}
      nativeButton={false}
      render={<Link to="/carrito" aria-label={units > 0 ? `Carrito, ${units} ${units === 1 ? 'unidad' : 'unidades'}` : 'Carrito vacío'} />}
    >
      <ShoppingCartIcon className="size-5" aria-hidden />
      {units > 0 ? (
        <span
          key={changes}
          className={cn(
            'absolute top-1 right-0.5 grid min-w-5 place-items-center overflow-hidden rounded-sm bg-ap-red px-1 text-[0.6875rem] leading-5 font-bold text-white tabular-nums',
            changes > 0 && 'ap-bump',
          )}
          aria-hidden
        >
          <span className={cn('block', changes > 0 && 'ap-number-in')}>{units > 99 ? '99+' : units}</span>
        </span>
      ) : null}
    </Button>
  )
}
