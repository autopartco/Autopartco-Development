import { useState } from 'react'
import { toast } from 'sonner'
import { CheckIcon, PackageCheckIcon, XIcon } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { getOrderService } from '../api/order-service'
import type { OrderDetail, OrderStatusTarget } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { ORDER_ACTION_LABEL, ORDER_NEXT } from '../lib/labels'
import { ConfirmDialog } from './confirm-dialog'

/**
 * Acciones de estado del pedido. Solo se ofrecen las transiciones que el servidor acepta
 * (pendiente → confirmado/cancelado, confirmado → preparación/cancelado); el servidor las vuelve a validar.
 */
export function OrderStatusActions({ order }: { order: OrderDetail }) {
  const { storeId, can, invalidateStore } = usePortal()
  const [busy, setBusy] = useState<OrderStatusTarget>()
  const [error, setError] = useState<string>()
  const [confirmCancel, setConfirmCancel] = useState(false)
  const next = ORDER_NEXT[order.status] ?? []

  if (!can('ORDER_UPDATE') || next.length === 0) return null

  async function change(status: OrderStatusTarget) {
    setBusy(status)
    setError(undefined)
    try {
      await (await getOrderService()).updateStatus(storeId, order.saleId, status)
      invalidateStore()
      toast.success(
        status === 'CONFIRMED' ? `Confirmaste el pedido #${order.orderNumber}.` : status === 'PREPARING' ? `El pedido #${order.orderNumber} está en preparación.` : `Cancelaste el pedido #${order.orderNumber}.`,
      )
    } catch (cause) {
      setError(actionErrorMessage(cause, 'No pudimos cambiar el estado del pedido.'))
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <div className="flex flex-col gap-3">
      {error ? (
        <p role="alert" className="rounded-lg border border-destructive/25 bg-destructive/5 p-3 text-sm font-medium text-destructive">
          {error}
        </p>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {next
          .filter((status) => status !== 'CANCELLED')
          .map((status) => (
            <Button key={status} className="h-11 px-4" disabled={busy !== undefined} onClick={() => void change(status)}>
              {status === 'CONFIRMED' ? <CheckIcon aria-hidden /> : <PackageCheckIcon aria-hidden />}
              {busy === status ? 'Guardando…' : ORDER_ACTION_LABEL[status]}
            </Button>
          ))}
        {next.includes('CANCELLED') ? (
          <Button variant="destructive" className="h-11 px-4" disabled={busy !== undefined} onClick={() => setConfirmCancel(true)}>
            <XIcon aria-hidden />
            {ORDER_ACTION_LABEL.CANCELLED}
          </Button>
        ) : null}
      </div>
      <ConfirmDialog
        open={confirmCancel}
        onOpenChange={setConfirmCancel}
        title={`¿Cancelar el pedido #${order.orderNumber}?`}
        description="Avisaremos al cliente. Un pedido cancelado no se puede reactivar."
        confirmLabel="Sí, cancelar pedido"
        destructive
        onConfirm={() => change('CANCELLED')}
      />
    </div>
  )
}
