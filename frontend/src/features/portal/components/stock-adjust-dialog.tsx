import { useRef, useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { pluralize } from '@/lib/format'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getInventoryService } from '../api/inventory-service'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { inventoryErrorMessage, reasonOptionLabel } from '../lib/inventory'
import { SelectField, TextAreaField } from './form-controls'

/** Listado (producto en una sucursal) cuyas existencias se ajustan. */
export interface AdjustTarget {
  productId: string
  productName: string
  branchId: string
  branchName: string
  stock: number
}

type Kind = 'SET' | 'IN' | 'OUT'

const KIND_OPTIONS = [
  { value: 'SET', label: 'Fijar las existencias en (conteo físico)' },
  { value: 'IN', label: 'Agregar unidades (entrada)' },
  { value: 'OUT', label: 'Quitar unidades (salida)' },
]

function AdjustForm({ target, onClose }: { target: AdjustTarget; onClose: () => void }) {
  const { storeId, invalidateStore } = usePortal()
  const reasons = useStoreQuery('inventory-reasons', async (id) => (await getInventoryService()).listReasons(id))
  const [kind, setKind] = useState<Kind>('SET')
  const [quantity, setQuantity] = useState('')
  const [reasonCode, setReasonCode] = useState('')
  const [notes, setNotes] = useState('')
  const [errors, setErrors] = useState<Partial<Record<'quantity' | 'reason' | 'notes', string>>>({})
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)
  const submit = useSubmitOnce(async (input: Parameters<Awaited<ReturnType<typeof getInventoryService>>['adjust']>[1]) =>
    (await getInventoryService()).adjust(storeId, input),
  )
  const pending = submit.pending

  const parsed = /^\d{1,7}$/.test(quantity.trim()) ? Number(quantity) : undefined
  const next = parsed === undefined ? undefined : kind === 'SET' ? parsed : kind === 'IN' ? target.stock + parsed : target.stock - parsed
  const direction = next === undefined || next === target.stock ? undefined : next > target.stock ? 'IN' : 'OUT'
  const compatible = (reasons.data ?? []).filter((reason) => reason.direction === 'ANY' || !direction || reason.direction === direction)
  const reason = compatible.some((candidate) => candidate.code === reasonCode) ? reasonCode : ''

  function validate(): typeof errors {
    const found: typeof errors = {}
    if (parsed === undefined || parsed > 1_000_000) found.quantity = 'Escribe un número entero, de 0 a 1.000.000.'
    else if (kind !== 'SET' && parsed === 0) found.quantity = 'Escribe una cantidad mayor que 0.'
    else if (next !== undefined && next < 0) found.quantity = `No puedes quitar más de las ${pluralize(target.stock, 'unidad', 'unidades')} que hay.`
    else if (next === target.stock) found.quantity = `Las existencias ya son ${target.stock}. Escribe un valor distinto.`
    if (!reason) found.reason = 'Elige el motivo del ajuste.'
    if (notes.length > 255) found.notes = 'Las notas admiten hasta 255 caracteres.'
    return found
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(undefined)
    const found = validate()
    setErrors(found)
    if (found.quantity) return document.getElementById('ajuste-cantidad')?.focus()
    if (found.reason) return document.getElementById('ajuste-motivo')?.focus()
    if (found.notes) return document.getElementById('ajuste-notas')?.focus()
    const result = await submit.run({
      branchId: target.branchId,
      productId: target.productId,
      mode: kind === 'SET' ? 'SET' : 'DELTA',
      quantity: kind === 'OUT' ? -(parsed as number) : (parsed as number),
      reasonCode: reason,
      notes: notes.trim() || undefined,
    })
    if (result.status === 'ok') {
      invalidateStore()
      toast.success(`Existencias de “${target.productName}” en ${target.branchName}: de ${target.stock} a ${result.value.newStock}.`)
      onClose()
    } else if (result.status === 'error') {
      setFormError(inventoryErrorMessage(result.error, 'No pudimos ajustar las existencias. Inténtalo de nuevo.'))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-4" aria-busy={pending || undefined}>
      <DialogHeader>
        <DialogTitle>Ajustar existencias</DialogTitle>
        <DialogDescription>
          <strong>{target.productName}</strong> en {target.branchName}: hoy hay {pluralize(target.stock, 'unidad', 'unidades')}. El ajuste queda registrado con su motivo en el historial.
        </DialogDescription>
      </DialogHeader>
      {formError ? (
        <FormAlert ref={alertRef} title="No se ajustaron las existencias">
          {formError}
        </FormAlert>
      ) : null}
      <SelectField id="ajuste-tipo" label="Tipo de ajuste" options={KIND_OPTIONS} value={kind} onChange={(event) => setKind(event.target.value as Kind)} disabled={pending} />
      <TextField
        id="ajuste-cantidad"
        label={kind === 'SET' ? 'Nuevas existencias' : 'Unidades'}
        inputMode="numeric"
        autoComplete="off"
        value={quantity}
        onChange={(event) => setQuantity(event.target.value.replace(/\D/g, '').slice(0, 7))}
        error={errors.quantity}
        disabled={pending}
        required
      />
      {next !== undefined && next >= 0 && next !== target.stock ? (
        <p role="status" className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-ink">
          Quedarán <strong className="font-semibold">{pluralize(next, 'unidad', 'unidades')}</strong> (hoy hay {target.stock}).
        </p>
      ) : null}
      {reasons.status === 'error' ? (
        <FormAlert title="No pudimos cargar los motivos">Cierra este panel y vuelve a abrirlo. Si sigue igual, recarga la página.</FormAlert>
      ) : (
        <SelectField
          id="ajuste-motivo"
          label="Motivo"
          placeholder={reasons.status === 'loading' ? 'Cargando motivos…' : 'Elige un motivo'}
          options={compatible.map((item) => ({ value: item.code, label: reasonOptionLabel(item) }))}
          value={reason}
          onChange={(event) => setReasonCode(event.target.value)}
          error={errors.reason}
          disabled={pending || reasons.status === 'loading'}
          required
        />
      )}
      <TextAreaField id="ajuste-notas" label="Notas" optional rows={2} maxLength={255} value={notes} onChange={(event) => setNotes(event.target.value)} error={errors.notes} disabled={pending} />
      <DialogFooter>
        <Button type="button" variant="outline" className="h-11 px-4" disabled={pending} onClick={onClose}>
          Cancelar
        </Button>
        <Button type="submit" className="h-11 px-4" disabled={pending || reasons.status === 'error'}>
          {pending ? 'Guardando…' : 'Guardar ajuste'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Ajuste de existencias de un listado con motivo (`POST /inventory/adjustments`). El servidor valida todo. */
export function StockAdjustDialog({ target, onOpenChange }: { target: AdjustTarget | undefined; onOpenChange: (open: boolean) => void }) {
  return (
    <Dialog open={target !== undefined} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">{target ? <AdjustForm key={`${target.productId}:${target.branchId}`} target={target} onClose={() => onOpenChange(false)} /> : null}</DialogContent>
    </Dialog>
  )
}
