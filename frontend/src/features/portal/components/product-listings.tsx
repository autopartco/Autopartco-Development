import { useState, type KeyboardEvent } from 'react'
import { toast } from 'sonner'
import { PlusIcon, Trash2Icon } from 'lucide-react'
import { TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { ApiError } from '@/lib/api'
import { formatCOP, formatNumber } from '@/lib/format'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getProductService } from '../api/product-service'
import type { ProductRow } from '../api/types'
import { usePortal } from '../context'
import { actionErrorMessage } from '../lib/errors'
import { ConfirmDialog } from './confirm-dialog'
import { CopField } from './cop-field'
import { SelectField } from './form-controls'
import { StockBadge } from './status-badge'
import { newListingDraft, type ListingDraft } from '../lib/product-listings'

type Option = { value: string; label: string }

interface CreateListingsEditorProps {
  id: string
  listings: ListingDraft[]
  branches: Option[]
  onChange: (listings: ListingDraft[]) => void
  onBlur: () => void
  error?: string
  disabled?: boolean
}

/** Alta: una o varias sucursales con existencias y precio opcional por sucursal, sin repetir. */
export function CreateListingsEditor({ id, listings, branches, onChange, onBlur, error, disabled }: CreateListingsEditorProps) {
  const multiple = listings.length > 1
  const update = (key: string, patch: Partial<ListingDraft>) => onChange(listings.map((item) => (item.key === key ? { ...item, ...patch } : item)))
  const used = new Set(listings.map((item) => item.branchId).filter(Boolean))
  const free = branches.filter((branch) => !used.has(branch.value))

  return (
    <fieldset id={id} tabIndex={-1} className="flex flex-col gap-3 outline-none" aria-describedby={error ? `${id}-error` : `${id}-ayuda`} onBlur={onBlur}>
      <legend className="mb-1 text-sm font-semibold text-ap-ink">Sucursales y existencias</legend>
      <p id={`${id}-ayuda`} className="text-sm text-ap-muted">
        {branches.length > 1
          ? 'Agrega cada sucursal donde tienes el producto. Si dejas el precio vacío se usa el precio general.'
          : 'Unidades disponibles hoy. Con 0 puedes publicar, pero aparecerá como agotado.'}
      </p>
      <ul className="flex flex-col gap-3">
        {listings.map((listing, index) => {
          const options = branches.filter((branch) => branch.value === listing.branchId || !used.has(branch.value))
          const label = listings.length > 1 ? ` de la fila ${index + 1}` : ''
          return (
            <li key={listing.key} className="grid items-start gap-3 rounded-lg border border-ap-line p-3 sm:grid-cols-[minmax(0,1.4fr)_minmax(0,0.8fr)_minmax(0,1fr)_auto]">
              {branches.length > 1 ? (
                <SelectField
                  id={`${id}-${index}-sucursal`}
                  label={`Sucursal${listings.length > 1 ? ` ${index + 1}` : ''}`}
                  placeholder="Elige una sucursal"
                  options={options}
                  value={listing.branchId}
                  onChange={(event) => update(listing.key, { branchId: event.target.value })}
                  required
                  disabled={disabled}
                />
              ) : (
                <p className="text-sm text-ap-muted sm:pt-8">
                  Sucursal: <strong className="font-semibold text-ap-ink">{branches[0]?.label ?? 'sin sucursal activa'}</strong>
                </p>
              )}
              <TextField
                id={`${id}-${index}-existencias`}
                label={`Existencias${multiple ? ` ${index + 1}` : ''}`}
                inputMode="numeric"
                value={listing.quantity}
                onChange={(event) => update(listing.key, { quantity: event.target.value.replace(/\D/g, '').slice(0, 9) })}
                autoComplete="off"
                required
                disabled={disabled}
              />
              {multiple ? (
                <CopField id={`${id}-${index}-precio`} label={`Precio ${index + 1} (opcional)`} value={listing.price} onChange={(digits) => update(listing.key, { price: digits })} disabled={disabled} />
              ) : (
                <span aria-hidden className="hidden sm:block" />
              )}
              {multiple ? (
                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="size-11 sm:mt-6"
                  aria-label={`Quitar la sucursal${label}`}
                  disabled={disabled}
                  onClick={() => onChange(listings.filter((item) => item.key !== listing.key))}
                >
                  <Trash2Icon aria-hidden />
                </Button>
              ) : null}
            </li>
          )
        })}
      </ul>
      {error ? (
        <p id={`${id}-error`} className="text-sm font-medium text-ap-danger">
          {error}
        </p>
      ) : null}
      {free.length > 0 && listings.length < branches.length ? (
        <Button
          type="button"
          variant="outline"
          className="h-11 w-fit px-4"
          disabled={disabled}
          onClick={() => onChange([...listings, newListingDraft(free.length === 1 ? (free[0]?.value ?? '') : '')])}
        >
          <PlusIcon aria-hidden />
          Agregar otra sucursal
        </Button>
      ) : null}
    </fieldset>
  )
}

interface ListingsManagerProps {
  productId: string
  productName: string
  rows: ProductRow[]
  /** Sucursales activas del almacén. */
  branches: Option[]
  /** Precio sugerido para la nueva sucursal (el de la sucursal que se edita). */
  suggestedPrice: number
  /** Tras quitar una sucursal (para cambiar la que se edita si era esa). */
  onRemoved: (branchId: string) => void
}

/** Edición: agregar o quitar el producto de sucursales. Se aplica al momento, aparte del formulario. */
export function ListingsManager({ productId, productName, rows, branches, suggestedPrice, onRemoved }: ListingsManagerProps) {
  const { storeId, can, promptUpgrade, invalidateStore } = usePortal()
  const canEdit = can('PRODUCT_UPDATE')
  const listed = new Set(rows.map((row) => row.branchId))
  const free = branches.filter((branch) => !listed.has(branch.value))
  const [branchId, setBranchId] = useState('')
  const [price, setPrice] = useState(() => String(Math.round(suggestedPrice)))
  const [stock, setStock] = useState('')
  const [errors, setErrors] = useState<Partial<Record<'branchId' | 'price' | 'stock', string>>>({})
  const [formError, setFormError] = useState<string>()
  const [toRemove, setToRemove] = useState<ProductRow>()
  const add = useSubmitOnce(async (input: { branchId: string; price: number; stock: number }) => (await getProductService()).addListing(storeId, productId, input))
  const selectedBranch = branchId && free.some((branch) => branch.value === branchId) ? branchId : free.length === 1 ? (free[0]?.value ?? '') : ''

  async function handleAdd() {
    setFormError(undefined)
    const found: typeof errors = {}
    if (!selectedBranch) found.branchId = 'Elige la sucursal.'
    if (!price || Number(price) < 1) found.price = 'Escribe un precio mayor que 0.'
    if (!stock) found.stock = 'Escribe cuántas unidades hay (0 si no hay).'
    setErrors(found)
    const first = (['branchId', 'price', 'stock'] as const).find((field) => found[field])
    if (first) {
      document.getElementById(`sucursal-nueva-${first}`)?.focus()
      return
    }
    const result = await add.run({ branchId: selectedBranch, price: Number(price), stock: Number(stock) })
    if (result.status === 'ok') {
      const name = branches.find((branch) => branch.value === selectedBranch)?.label ?? 'la sucursal'
      toast.success(`“${productName}” ya está en ${name}.`)
      setBranchId('')
      setStock('')
      invalidateStore()
    } else if (result.status === 'error') {
      if (promptUpgrade(result.error)) return
      const error = result.error
      if (error instanceof ApiError && error.code === 'LISTING_ALREADY_EXISTS') setFormError('El producto ya está en esa sucursal. Actualiza la página para ver los datos al día.')
      else if (error instanceof ApiError && error.code === 'BRANCH_NOT_FOUND') setFormError('Esa sucursal ya no está activa. Elige otra.')
      else setFormError(actionErrorMessage(error, 'No pudimos agregar la sucursal. Inténtalo de nuevo.'))
    }
  }

  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    // Enter aquí agrega la sucursal; no avanza el asistente del formulario.
    if (event.key === 'Enter' && (event.target as HTMLElement).tagName === 'INPUT') {
      event.preventDefault()
      void handleAdd()
    }
  }

  return (
    <section aria-labelledby="sucursales-producto-titulo" className="flex flex-col gap-3 border-t border-ap-line-strong pt-4">
      <div className="flex flex-col gap-1">
        <h3 id="sucursales-producto-titulo" className="text-base font-semibold text-ap-ink">
          Sucursales del producto
        </h3>
        <p className="text-sm text-ap-muted">Agregar o quitar una sucursal se aplica al momento, sin pulsar «Guardar cambios».</p>
      </div>
      <ul className="flex flex-col divide-y divide-border">
        {rows.map((row) => (
          <li key={row.branchId} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-2 text-sm">
            <span className="min-w-0 flex-1 font-medium text-ap-ink">{row.branchName}</span>
            <span className="ap-price">{formatCOP(row.price)}</span>
            <span className="flex items-center gap-2 tabular-nums">
              {formatNumber(row.quantity)} u.
              <StockBadge quantity={row.quantity} />
            </span>
            {canEdit && rows.length > 1 ? (
              <Button type="button" variant="ghost" className="h-11 px-3 text-ap-danger" onClick={() => setToRemove(row)}>
                <Trash2Icon aria-hidden />
                Quitar
              </Button>
            ) : null}
          </li>
        ))}
      </ul>
      {canEdit && rows.length === 1 ? <p className="text-xs text-ap-muted">Es la única sucursal del producto: para quitarlo de ella, elimina el producto.</p> : null}

      {canEdit && free.length > 0 ? (
        <div className="flex flex-col gap-3 border-t border-border pt-3" onKeyDown={onKeyDown} aria-busy={add.pending || undefined}>
          <h4 className="text-sm font-semibold text-ap-ink">Agregar a otra sucursal</h4>
          {formError ? (
            <p role="alert" className="rounded-md border border-ap-danger/25 bg-ap-danger-soft p-3 text-sm font-medium text-ap-danger">
              {formError}
            </p>
          ) : null}
          <div className="grid items-start gap-3 sm:grid-cols-3">
            <SelectField
              id="sucursal-nueva-branchId"
              label="Sucursal"
              placeholder={free.length === 1 ? undefined : 'Elige una sucursal'}
              options={free}
              value={selectedBranch}
              onChange={(event) => setBranchId(event.target.value)}
              error={errors.branchId}
              disabled={add.pending}
              required
            />
            <CopField id="sucursal-nueva-price" label="Precio" value={price} onChange={setPrice} error={errors.price} disabled={add.pending} required />
            <TextField
              id="sucursal-nueva-stock"
              label="Existencias"
              inputMode="numeric"
              value={stock}
              onChange={(event) => setStock(event.target.value.replace(/\D/g, '').slice(0, 9))}
              error={errors.stock}
              autoComplete="off"
              disabled={add.pending}
              required
            />
          </div>
          <Button type="button" variant="outline" className="h-11 w-fit px-4" disabled={add.pending} onClick={() => void handleAdd()}>
            <PlusIcon aria-hidden />
            {add.pending ? 'Agregando…' : 'Agregar sucursal'}
          </Button>
        </div>
      ) : null}

      <ConfirmDialog
        open={toRemove !== undefined}
        onOpenChange={(open) => !open && setToRemove(undefined)}
        title={`¿Quitar “${productName}” de ${toRemove?.branchName ?? 'esta sucursal'}?`}
        description="Dejará de venderse en esa sucursal y sus existencias allí se dan de baja. El producto sigue en las demás sucursales."
        confirmLabel="Quitar de la sucursal"
        destructive
        onConfirm={async () => {
          if (!toRemove) return
          try {
            await (await getProductService()).removeListing(storeId, productId, toRemove.branchId)
          } catch (error) {
            if (error instanceof ApiError && error.code === 'LAST_LISTING') {
              throw new ApiError('Es la única sucursal del producto. Para quitarlo, elimina el producto.', { status: 409, code: 'LAST_LISTING' })
            }
            throw error
          }
          onRemoved(toRemove.branchId)
          invalidateStore()
          toast.success(`“${productName}” ya no está en ${toRemove.branchName}.`)
        }}
      />
    </section>
  )
}
