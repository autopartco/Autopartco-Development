import { useRef, useState, type ChangeEvent } from 'react'
import { FileDownIcon, FileUpIcon, Trash2Icon } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { saveBlob } from '@/lib/download'
import { getOrderService } from '../api/order-service'
import type { OrderDetail } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { formatDateTime } from '../lib/labels'

const MAX_BYTES = 5 * 1024 * 1024

function size(bytes: number): string {
  return bytes >= 1024 * 1024 ? `${(bytes / 1024 / 1024).toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

/**
 * Factura electrónica del almacén para esta venta (PDF, máx. 5 MB). El comprador la descarga desde «Mis pedidos».
 * Subir otra reemplaza la anterior.
 */
export function InvoicePanel({ order }: { order: OrderDetail }) {
  const { storeId, can, invalidateStore } = usePortal()
  const inputRef = useRef<HTMLInputElement>(null)
  const [busy, setBusy] = useState<'upload' | 'delete' | 'download'>()
  const invoice = order.invoice ?? null
  const editable = can('ORDER_UPDATE')

  async function upload(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    if (file.type && file.type !== 'application/pdf') {
      toast.error('La factura debe ser un archivo PDF.')
      return
    }
    if (file.size > MAX_BYTES) {
      toast.error('La factura no puede superar 5 MB.')
      return
    }
    setBusy('upload')
    try {
      await (await getOrderService()).uploadInvoice(storeId, order.saleId, file)
      toast.success(invoice ? 'Factura reemplazada.' : 'Factura adjuntada. El cliente ya puede descargarla.')
      invalidateStore()
    } catch (error) {
      toast.error(actionErrorMessage(error, 'No pudimos subir la factura. Inténtalo de nuevo.'))
    } finally {
      setBusy(undefined)
    }
  }

  async function remove() {
    setBusy('delete')
    try {
      await (await getOrderService()).deleteInvoice(storeId, order.saleId)
      toast.success('Factura quitada.')
      invalidateStore()
    } catch (error) {
      toast.error(actionErrorMessage(error, 'No pudimos quitar la factura.'))
    } finally {
      setBusy(undefined)
    }
  }

  async function download() {
    if (!invoice) return
    setBusy('download')
    try {
      saveBlob(await (await getOrderService()).downloadInvoice(storeId, order.saleId), invoice.fileName)
    } catch (error) {
      toast.error(actionErrorMessage(error, 'No pudimos descargar la factura.'))
    } finally {
      setBusy(undefined)
    }
  }

  return (
    <div className="flex flex-col gap-3 text-sm">
      {invoice ? (
        <div className="flex flex-col gap-0.5">
          <p className="font-semibold break-all text-ap-ink">{invoice.fileName}</p>
          <p className="font-mono text-xs text-ap-muted tabular-nums">
            {size(invoice.sizeBytes)} · {formatDateTime(invoice.uploadedAt)}
          </p>
        </div>
      ) : (
        <p className="text-ap-muted">Adjunta la factura electrónica en PDF para que el cliente la descargue desde su pedido.</p>
      )}
      <div className="flex flex-wrap gap-2">
        {invoice ? (
          <Button variant="outline" className="h-11 sm:h-9" disabled={busy !== undefined} onClick={() => void download()}>
            <FileDownIcon aria-hidden />
            {busy === 'download' ? 'Descargando…' : 'Descargar'}
          </Button>
        ) : null}
        {editable ? (
          <>
            <input ref={inputRef} type="file" accept="application/pdf,.pdf" className="sr-only" tabIndex={-1} aria-hidden onChange={(event) => void upload(event)} />
            <Button variant={invoice ? 'outline' : 'default'} className="h-11 sm:h-9" disabled={busy !== undefined} onClick={() => inputRef.current?.click()}>
              <FileUpIcon aria-hidden />
              {busy === 'upload' ? 'Subiendo…' : invoice ? 'Reemplazar' : 'Adjuntar factura'}
            </Button>
            {invoice ? (
              <Button variant="ghost" className="h-11 text-ap-muted sm:h-9" disabled={busy !== undefined} onClick={() => void remove()}>
                <Trash2Icon aria-hidden />
                {busy === 'delete' ? 'Quitando…' : 'Quitar'}
              </Button>
            ) : null}
          </>
        ) : null}
      </div>
    </div>
  )
}
