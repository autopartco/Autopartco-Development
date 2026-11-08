import { useRef, useState, type FormEvent } from 'react'
import { FormAlert, TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError } from '@/lib/api'
import { formatCOP } from '@/lib/format'
import { useSubmitOnce } from '@/lib/use-submit-once'
import type { ProductBulkPriceMode } from '../api/types'
import { actionErrorMessage } from '../lib/errors'
import { CopField } from './cop-field'
import { SelectField } from './form-controls'

export interface BulkPriceInput {
  branchId: string
  priceMode: ProductBulkPriceMode
  /** `PERCENT`: variación con signo (-90 a +500; negativo baja). `AMOUNT`: nuevo precio en pesos (mayor que 0). */
  value: number
}

interface BulkPriceDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** Productos seleccionados. */
  count: number
  branches: { value: string; label: string }[]
  /** Si rechaza, el diálogo sigue abierto y muestra el motivo. */
  onSubmit: (input: BulkPriceInput) => Promise<void>
}

type Direction = 'UP' | 'DOWN'

const MODE_OPTIONS = [
  { value: 'PERCENT', label: 'Subir o bajar un porcentaje' },
  { value: 'AMOUNT', label: 'Fijar precio en pesos' },
]
const DIRECTION_OPTIONS = [
  { value: 'UP', label: 'Subir' },
  { value: 'DOWN', label: 'Bajar' },
]

function priceErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === 'LISTING_NOT_FOUND') return 'Algún producto seleccionado no está en esa sucursal. Elige otra sucursal o quita esos productos de la selección.'
    if (error.code === 'BRANCH_NOT_FOUND') return 'Esa sucursal ya no está activa en tu almacén. Elige otra.'
    if (error.code === 'PRODUCT_NOT_FOUND') return 'Algún producto seleccionado ya no existe en tu almacén. Recarga la lista y vuelve a seleccionar.'
    if (error.code === 'INVALID_PRICE_VALUE') return 'El valor no es válido: el porcentaje va de -90 % a +500 % y el precio debe ser mayor que 0.'
    if (error.code === 'INVALID_PRICE') return 'Con ese ajuste algún precio quedaría fuera del rango permitido (de 1 a 9.999.999.999 pesos). Usa un ajuste menor.'
  }
  return actionErrorMessage(error, 'No pudimos ajustar los precios. Inténtalo de nuevo.')
}

function PriceForm({ count, branches, onSubmit, onCancel }: Omit<BulkPriceDialogProps, 'open' | 'onOpenChange'> & { onCancel: () => void }) {
  const [branchId, setBranchId] = useState(branches.length === 1 ? (branches[0]?.value ?? '') : '')
  const [mode, setMode] = useState<ProductBulkPriceMode>('PERCENT')
  const [direction, setDirection] = useState<Direction>('UP')
  const [amount, setAmount] = useState('')
  const [errors, setErrors] = useState<Partial<Record<'branchId' | 'amount', string>>>({})
  const [formError, setFormError] = useState<string>()
  const alertRef = useRef<HTMLDivElement>(null)
  const submit = useSubmitOnce(onSubmit)
  const pending = submit.pending

  const number = Number(amount.replace(',', '.'))
  const signed = direction === 'DOWN' ? -number : number
  const example = 100000
  const preview =
    amount && Number.isFinite(number) && number > 0 ? Math.round(mode === 'PERCENT' ? example * (1 + signed / 100) : number) : undefined

  function validate(): typeof errors {
    const next: typeof errors = {}
    if (!branchId) next.branchId = 'Elige la sucursal donde cambia el precio.'
    if (!amount || !Number.isFinite(number) || number <= 0) next.amount = mode === 'PERCENT' ? 'Escribe un porcentaje mayor que 0.' : 'Escribe un precio en pesos mayor que 0.'
    else if (mode === 'PERCENT' && direction === 'DOWN' && number > 90) next.amount = 'Puedes bajar el precio hasta un 90 %.'
    else if (mode === 'PERCENT' && direction === 'UP' && number > 500) next.amount = 'Puedes subir el precio hasta un 500 %.'
    return next
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setFormError(undefined)
    const found = validate()
    setErrors(found)
    if (found.branchId) return document.getElementById('precio-masivo-sucursal')?.focus()
    if (found.amount) return document.getElementById('precio-masivo-valor')?.focus()
    const result = await submit.run({ branchId, priceMode: mode, value: mode === 'PERCENT' ? signed : number })
    if (result.status === 'error') {
      const error = result.error
      const fieldMessage = error instanceof ApiError ? (error.fieldErrors.value ?? error.fieldErrors.branchId) : undefined
      setFormError(fieldMessage ?? priceErrorMessage(error))
      requestAnimationFrame(() => alertRef.current?.focus())
    }
  }

  return (
    <form noValidate onSubmit={(event) => void handleSubmit(event)} className="flex flex-col gap-4" aria-busy={pending || undefined}>
      <DialogHeader>
        <DialogTitle>Ajustar precio</DialogTitle>
        <DialogDescription>
          Cambia el precio de {count === 1 ? 'el producto seleccionado' : `los ${count} productos seleccionados`} en una sucursal. Todos deben estar en esa sucursal; si alguno no está, no se cambia ningún precio.
        </DialogDescription>
      </DialogHeader>
      {formError ? (
        <FormAlert ref={alertRef} title="No se cambió ningún precio">
          {formError}
        </FormAlert>
      ) : null}
      <SelectField
        id="precio-masivo-sucursal"
        label="Sucursal"
        placeholder={branches.length === 1 ? undefined : 'Elige una sucursal'}
        options={branches}
        value={branchId}
        onChange={(event) => setBranchId(event.target.value)}
        error={errors.branchId}
        disabled={pending}
        required
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField id="precio-masivo-modo" label="Cómo cambiar el precio" options={MODE_OPTIONS} value={mode} onChange={(event) => setMode(event.target.value as ProductBulkPriceMode)} disabled={pending} />
        {mode === 'PERCENT' ? (
          <SelectField id="precio-masivo-direccion" label="Dirección" options={DIRECTION_OPTIONS} value={direction} onChange={(event) => setDirection(event.target.value as Direction)} disabled={pending} />
        ) : null}
      </div>
      {mode === 'PERCENT' ? (
        <TextField
          id="precio-masivo-valor"
          label="Porcentaje"
          inputMode="decimal"
          value={amount}
          onChange={(event) => setAmount(event.target.value.replace(/[^\d.,]/g, '').slice(0, 7))}
          error={errors.amount}
          autoComplete="off"
          disabled={pending}
          required
          hint="Ej.: 10 para un 10 %. Subir hasta 500 %, bajar hasta 90 %. El resultado se redondea al peso."
        />
      ) : (
        <CopField
          id="precio-masivo-valor"
          label="Fijar precio en"
          value={amount}
          onChange={setAmount}
          error={errors.amount}
          disabled={pending}
          required
          hint="Nuevo precio, en pesos, que tendrá cada producto seleccionado en esa sucursal."
        />
      )}
      {preview !== undefined ? (
        <p role="status" className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-ink">
          {mode === 'PERCENT' ? (
            <>
              Ejemplo: un producto de {formatCOP(example)} quedaría en <strong className="font-semibold">{preview > 0 ? formatCOP(preview) : 'un precio no válido'}</strong>.
            </>
          ) : (
            <>
              Todos los productos seleccionados quedarán en <strong className="font-semibold">{formatCOP(preview)}</strong>.
            </>
          )}
        </p>
      ) : null}
      <DialogFooter>
        <Button type="button" variant="outline" className="h-11 px-4" disabled={pending} onClick={onCancel}>
          Cancelar
        </Button>
        <Button type="submit" className="h-11 px-4" disabled={pending}>
          {pending ? 'Aplicando…' : 'Aplicar ajuste'}
        </Button>
      </DialogFooter>
    </form>
  )
}

/** Ajuste masivo de precio (variación en % o precio fijo) en una sucursal; el servidor lo aplica a todos o a ninguno. */
export function BulkPriceDialog({ open, onOpenChange, count, branches, onSubmit }: BulkPriceDialogProps) {
  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg">
        {/* El formulario se monta al abrir: estado limpio cada vez. */}
        {open ? <PriceForm count={count} branches={branches} onSubmit={onSubmit} onCancel={() => onOpenChange(false)} /> : null}
      </DialogContent>
    </Dialog>
  )
}
