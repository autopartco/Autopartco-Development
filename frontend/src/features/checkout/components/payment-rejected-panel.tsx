import { Link } from 'react-router'
import { CircleXIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { formatCOP } from '@/lib/format'

interface PaymentRejectedPanelProps {
  amount?: number
  onTryAnotherMethod: () => void
}

/**
 * Pago rechazado. El servidor revirtió todo (sin pedido, cobro ni descuento de stock):
 * el carrito sigue igual y se puede reintentar. El contenedor con `role="alert"` lo pone la página.
 */
export function PaymentRejectedPanel({ amount, onTryAnotherMethod }: PaymentRejectedPanelProps) {
  return (
    <div className="flex flex-col gap-3">
      <div className="flex items-center gap-2 font-semibold text-destructive">
        <CircleXIcon className="size-5 shrink-0" aria-hidden />
        <p>Pago rechazado</p>
      </div>
      <p className="max-w-prose text-sm text-foreground/85">
        No se hizo ningún cobro y tu carrito sigue igual.
        {amount !== undefined ? ` El intento era por ${formatCOP(amount)}.` : ''} Prueba con otro método o vuelve a intentarlo.
      </p>
      <div className="flex flex-wrap gap-2">
        <Button type="button" className="h-11 px-4" onClick={onTryAnotherMethod}>
          Intentar con otro método
        </Button>
        <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to="/carrito" />}>
          Volver al carrito
        </Button>
      </div>
    </div>
  )
}
