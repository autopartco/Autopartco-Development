import { useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ArrowLeftRightIcon, ArrowRightIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { FormAlert, INPUT_CLASS, TextField } from '@/components/common/form-fields'
import { AccessState, EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { ApiError } from '@/lib/api'
import { formatNumber, pluralize } from '@/lib/format'
import { getInventoryService } from '../api/inventory-service'
import { getProductService } from '../api/product-service'
import type { StockTransfer, StockTransferSummary, TransferInput } from '../api/types'
import { ConfirmDialog } from '../components/confirm-dialog'
import { SelectField, TextAreaField } from '../components/form-controls'
import { InventoryTabs, MIN_BRANCHES_TO_TRANSFER, TransfersRequirement } from '../components/inventory-tabs'
import { PageHeader } from '../components/page-header'
import { QueryBoundary, QueryError } from '../components/query-boundary'
import { SearchField } from '../components/search-field'
import { ServerPager } from '../components/server-pager'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { focusMain, useActiveBranches, useStoreQuery } from '../hooks'
import { actorLabel, inventoryErrorMessage } from '../lib/inventory'
import { formatDateTime } from '../lib/labels'
import { normalizeText } from '../lib/fuzzy'

type UiMode = 'ALL' | 'SELECTION' | 'ONE'

const MODE_OPTIONS: { value: UiMode; label: string; hint: string }[] = [
  { value: 'ALL', label: 'Todo el stock de la sucursal', hint: 'Mueve todos los productos que tengan existencias.' },
  { value: 'SELECTION', label: 'Productos que elijas', hint: 'Marca varios productos y define la cantidad de cada uno.' },
  { value: 'ONE', label: 'Un solo producto', hint: 'Mueve unidades de un producto.' },
]

const MODE_LABEL: Record<StockTransferSummary['mode'], string> = { ALL: 'Todo el stock', ITEMS: 'Productos elegidos' }

function parseQuantity(text: string, max: number): number | undefined {
  if (!/^\d{1,7}$/.test(text.trim())) return undefined
  const value = Number(text)
  return value >= 1 && value <= max ? value : undefined
}

function NewTransfer({ branches }: { branches: { id: string; name: string }[] }) {
  const { storeId, invalidateStore, branchId: activeBranchId } = usePortal()
  const [params] = useSearchParams()
  const products = useStoreQuery('products', async (id) => (await getProductService()).list(id))
  // Origen: el del enlace (`?origen=`) o, si no hay, la sucursal activa de la barra de contexto.
  const preselected = params.get('origen') ?? activeBranchId ?? ''
  const [from, setFrom] = useState(branches.some((branch) => branch.id === preselected) ? preselected : '')
  const [to, setTo] = useState('')
  const [mode, setMode] = useState<UiMode>('ALL')
  const [selected, setSelected] = useState<Record<string, string>>({})
  const [oneId, setOneId] = useState('')
  const [oneQty, setOneQty] = useState('')
  const [notes, setNotes] = useState('')
  const [q, setQ] = useState('')
  const [touched, setTouched] = useState(false)
  const [confirming, setConfirming] = useState(false)
  // Una clave por intento: se reutiliza mientras la solicitud sea la misma y no haya terminado bien.
  const attempt = useRef<{ signature: string; key: string }>(undefined)

  const originRows = useMemo(
    () => (products.data ?? []).filter((row) => row.branchId === from && row.quantity > 0).toSorted((a, b) => a.name.localeCompare(b.name, 'es')),
    [products.data, from],
  )
  const byId = useMemo(() => new Map(originRows.map((row) => [row.id, row])), [originRows])
  const names = useMemo(() => new Map((products.data ?? []).map((row) => [row.id, row.name])), [products.data])
  const term = normalizeText(q)
  const visible = originRows.filter((row) => !term || normalizeText(`${row.name} ${row.sku ?? ''} ${row.partNumber ?? ''}`).includes(term))

  const items: { productId: string; quantity: number }[] =
    mode === 'SELECTION'
      ? Object.entries(selected).flatMap(([productId, text]) => {
          const row = byId.get(productId)
          const quantity = row ? parseQuantity(text, row.quantity) : undefined
          return quantity === undefined ? [] : [{ productId, quantity }]
        })
      : mode === 'ONE'
        ? (() => {
            const row = byId.get(oneId)
            const quantity = row ? parseQuantity(oneQty, row.quantity) : undefined
            return row && quantity !== undefined ? [{ productId: row.id, quantity }] : []
          })()
        : []
  const selectedCount = Object.keys(selected).length
  const units = mode === 'ALL' ? originRows.reduce((sum, row) => sum + row.quantity, 0) : items.reduce((sum, item) => sum + item.quantity, 0)
  const productCount = mode === 'ALL' ? originRows.length : items.length

  function problem(): string | undefined {
    if (!from) return 'Elige la sucursal de origen.'
    if (!to) return 'Elige la sucursal de destino.'
    if (from === to) return 'La sucursal de origen y la de destino deben ser distintas.'
    if (products.status !== 'success') return 'Espera a que carguen los productos de la sucursal.'
    if (mode === 'ALL' && originRows.length === 0) return 'La sucursal de origen no tiene existencias para transferir.'
    if (mode === 'SELECTION' && selectedCount === 0) return 'Marca al menos un producto.'
    if (mode === 'SELECTION' && items.length !== selectedCount) return 'Revisa las cantidades marcadas: cada una debe ser un entero de 1 hasta las existencias del producto.'
    if (mode === 'ONE' && !oneId) return 'Elige el producto.'
    if (mode === 'ONE' && items.length === 0) return 'Escribe una cantidad válida: un entero de 1 hasta las existencias del producto.'
    return undefined
  }
  const error = touched ? problem() : undefined
  const fromName = branches.find((branch) => branch.id === from)?.name ?? ''
  const toName = branches.find((branch) => branch.id === to)?.name ?? ''

  function changeFrom(value: string) {
    setFrom(value)
    setSelected({})
    setOneId('')
    setOneQty('')
    if (value === to) setTo('')
  }

  async function send() {
    const body: Omit<TransferInput, 'idempotencyKey'> = {
      fromBranchId: from,
      toBranchId: to,
      mode: mode === 'ALL' ? 'ALL' : 'ITEMS',
      ...(mode === 'ALL' ? {} : { items }),
      ...(notes.trim() ? { notes: notes.trim() } : {}),
    }
    const signature = JSON.stringify(body)
    if (attempt.current?.signature !== signature) attempt.current = { signature, key: crypto.randomUUID() }
    try {
      const transfer = await (await getInventoryService()).transfer(storeId, { ...body, idempotencyKey: attempt.current.key })
      attempt.current = undefined
      invalidateStore()
      toast.success(`Transferiste ${pluralize(transfer.totalUnits, 'unidad', 'unidades')} de ${transfer.fromBranchName} a ${transfer.toBranchName}.`)
      setSelected({})
      setOneId('')
      setOneQty('')
      setNotes('')
      setTouched(false)
      focusMain()
    } catch (failure) {
      // Se queda en el diálogo con el motivo; reintentar reutiliza la misma clave.
      const status = failure instanceof ApiError ? failure.status : undefined
      const message = inventoryErrorMessage(failure, 'No pudimos transferir el inventario. Inténtalo de nuevo.', names)
      throw new ApiError(message, { status: status !== undefined && status >= 500 ? status : 409, cause: failure })
    }
  }

  return (
    <section aria-labelledby="nueva-transferencia" className="flex flex-col gap-5 border-t border-ap-line-strong pt-4">
      <h2 id="nueva-transferencia" className="text-block-title text-ap-ink">
        Nueva transferencia
      </h2>
      <form
        noValidate
        className="flex flex-col gap-5"
        onSubmit={(event) => {
          event.preventDefault()
          setTouched(true)
          if (!problem()) setConfirming(true)
        }}
      >
        <div className="grid items-end gap-3 sm:grid-cols-[1fr_auto_1fr]">
          <SelectField id="transf-origen" label="Sucursal de origen" placeholder="Elige el origen" options={branches.map((branch) => ({ value: branch.id, label: branch.name }))} value={from} onChange={(event) => changeFrom(event.target.value)} />
          <ArrowRightIcon className="mb-3 hidden size-5 text-ap-muted sm:block" aria-hidden />
          <SelectField
            id="transf-destino"
            label="Sucursal de destino"
            placeholder="Elige el destino"
            options={branches.filter((branch) => branch.id !== from).map((branch) => ({ value: branch.id, label: branch.name }))}
            value={to}
            onChange={(event) => setTo(event.target.value)}
          />
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-semibold text-ap-ink">Qué quieres mover</legend>
          {MODE_OPTIONS.map((option) => (
            <label key={option.value} className="flex min-h-11 cursor-pointer items-start gap-3 rounded-md border border-ap-line-strong p-3 transition-colors hover:bg-ap-paper has-checked:border-ap-graphite has-checked:bg-ap-paper has-checked:shadow-[inset_3px_0_0_var(--ap-red)]">
              <input type="radio" name="transf-modo" value={option.value} checked={mode === option.value} onChange={() => setMode(option.value)} className="mt-1 size-4 accent-ap-red" />
              <span className="flex flex-col">
                <span className="text-sm font-semibold text-ap-ink">{option.label}</span>
                <span className="text-xs text-ap-muted">{option.hint}</span>
              </span>
            </label>
          ))}
        </fieldset>

        {from && products.status === 'loading' && !products.data ? <p role="status" className="text-sm text-ap-muted">Cargando los productos de la sucursal…</p> : null}
        {products.status === 'error' ? <QueryError error={products.error} what="los productos" onRetry={products.retry} /> : null}

        {from && mode === 'SELECTION' && products.data ? (
          originRows.length === 0 ? (
            <p className="text-sm text-ap-muted">La sucursal de origen no tiene productos con existencias.</p>
          ) : (
            <div className="flex flex-col gap-3">
              <SearchField label="Buscar producto" value={q} onChange={setQ} placeholder="Nombre, SKU o referencia" />
              <ul aria-label="Productos de la sucursal de origen" className="max-h-96 divide-y divide-border overflow-auto rounded-lg border border-border">
                {visible.map((row) => {
                  const checked = row.id in selected
                  const text = selected[row.id] ?? ''
                  const invalid = checked && parseQuantity(text, row.quantity) === undefined
                  return (
                    <li key={row.id} className={`flex flex-wrap items-center gap-3 p-3 ${checked ? 'bg-ap-red-tint shadow-[inset_3px_0_0_var(--ap-red)]' : ''}`}>
                      <label className="flex min-h-11 min-w-0 flex-1 cursor-pointer items-center gap-3">
                        <Checkbox
                          checked={checked}
                          onCheckedChange={(next) =>
                            setSelected((current) => {
                              const copy = { ...current }
                              if (next === true) copy[row.id] = '1'
                              else delete copy[row.id]
                              return copy
                            })
                          }
                        />
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-semibold text-ap-ink">{row.name}</span>
                          <span className="block text-xs text-ap-muted">
                            <span className="ap-ref">{row.sku ?? row.partNumber ?? 'Sin SKU'}</span> · <span className="tabular-nums">{pluralize(row.quantity, 'unidad disponible', 'unidades disponibles')}</span>
                          </span>
                        </span>
                      </label>
                      {checked ? (
                        <div className="flex flex-col gap-1">
                          <label htmlFor={`transf-cant-${row.id}`} className="sr-only">
                            Cantidad de {row.name} a transferir
                          </label>
                          <input
                            id={`transf-cant-${row.id}`}
                            inputMode="numeric"
                            autoComplete="off"
                            value={text}
                            aria-invalid={invalid || undefined}
                            onChange={(event) => setSelected((current) => ({ ...current, [row.id]: event.target.value }))}
                            className={`${INPUT_CLASS} w-24 text-right tabular-nums`}
                          />
                          {invalid ? <p className="text-xs font-medium text-ap-danger">1 a {formatNumber(row.quantity)}</p> : null}
                        </div>
                      ) : null}
                    </li>
                  )
                })}
                {visible.length === 0 ? (
                  <li className="flex items-center gap-2 p-4 text-sm text-ap-muted">
                    <SearchXIcon className="size-4" aria-hidden />
                    Ningún producto coincide con la búsqueda.
                  </li>
                ) : null}
              </ul>
            </div>
          )
        ) : null}

        {from && mode === 'ONE' && products.data ? (
          <div className="grid gap-3 sm:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
            <SelectField
              id="transf-producto"
              label="Producto"
              placeholder="Elige el producto"
              options={originRows.map((row) => ({ value: row.id, label: `${row.name} (${row.quantity} disponibles)` }))}
              value={oneId}
              onChange={(event) => setOneId(event.target.value)}
            />
            <TextField id="transf-cantidad" label="Cantidad" inputMode="numeric" autoComplete="off" value={oneQty} onChange={(event) => setOneQty(event.target.value.replace(/\D/g, '').slice(0, 7))} />
          </div>
        ) : null}

        <TextAreaField id="transf-notas" label="Notas" optional rows={2} maxLength={255} value={notes} onChange={(event) => setNotes(event.target.value)} />

        {from && to && productCount > 0 ? (
          <p role="status" className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-ink">
            Moverás <strong className="font-semibold">{pluralize(units, 'unidad', 'unidades')}</strong> de {pluralize(productCount, 'producto', 'productos')} desde <strong className="font-semibold">{fromName}</strong> hacia <strong className="font-semibold">{toName}</strong>.
          </p>
        ) : null}
        {error ? <FormAlert title="Revisa la transferencia">{error}</FormAlert> : null}
        <div>
          <Button type="submit" className="h-11 px-5">
            <ArrowLeftRightIcon aria-hidden />
            Revisar y transferir
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={confirming}
        onOpenChange={setConfirming}
        title="¿Transferir inventario?"
        description={
          <>
            Se moverán <strong>{pluralize(units, 'unidad', 'unidades')}</strong> ({pluralize(productCount, 'producto', 'productos')}) de <strong>{fromName}</strong> a <strong>{toName}</strong>. Las existencias cambian de inmediato en las dos sucursales y el movimiento queda registrado.
          </>
        }
        confirmLabel="Transferir ahora"
        onConfirm={send}
      />
    </section>
  )
}

function TransferDetail({ transferId }: { transferId: string }) {
  const result = useStoreQuery(`transfer:${transferId}`, async (id) => (await getInventoryService()).getTransfer(id, transferId))
  return (
    <QueryBoundary result={result} what="la transferencia">
      {(transfer: StockTransfer) => (
        <div className="flex flex-col gap-3">
          <p className="text-sm text-ap-ink">
            <time dateTime={transfer.createdAt} className="ap-ref">{formatDateTime(transfer.createdAt)}</time> · {MODE_LABEL[transfer.mode]} · {pluralize(transfer.totalUnits, 'unidad', 'unidades')} · {actorLabel(transfer.actorName)}
          </p>
          {transfer.notes ? <p className="rounded-md border border-ap-line bg-ap-paper p-3 text-sm text-ap-ink">{transfer.notes}</p> : null}
          <div className="max-h-80 overflow-auto rounded-lg border border-ap-line" tabIndex={0} role="region" aria-label={`Productos movidos (${transfer.items.length})`}>
            <Table>
              <caption className="sr-only">Productos movidos y existencias resultantes</caption>
              <TableHeader className="sticky top-0">
                <TableRow>
                  <TableHead>Producto</TableHead>
                  <TableHead numeric>Cantidad</TableHead>
                  <TableHead numeric>Quedó en {transfer.fromBranchName}</TableHead>
                  <TableHead numeric>Quedó en {transfer.toBranchName}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {transfer.items.map((item) => (
                  <TableRow key={item.productId}>
                    <TableHead scope="row" className="h-auto border-b-ap-line bg-transparent py-3 text-sm font-medium whitespace-normal text-ap-ink">
                      {item.productName}
                    </TableHead>
                    <TableCell numeric className="font-semibold">{formatNumber(item.quantity)}</TableCell>
                    <TableCell numeric>{formatNumber(item.fromStock)}</TableCell>
                    <TableCell numeric>{formatNumber(item.toStock)}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </div>
      )}
    </QueryBoundary>
  )
}

function History({ branches }: { branches: { id: string; name: string }[] }) {
  const { branchId: activeBranchId } = usePortal()
  // Arranca con la sucursal activa de la barra de contexto; el selector del historial puede cambiarla.
  const [branchId, setBranchId] = useState(() => (activeBranchId && branches.some((branch) => branch.id === activeBranchId) ? activeBranchId : ''))
  const [page, setPage] = useState(1)
  const [openId, setOpenId] = useState<string>()
  const result = useStoreQuery(`transfers:${branchId}:${page}`, async (id) =>
    (await getInventoryService()).listTransfers(id, { branchId: branchId || undefined, page, size: 10 }),
  )

  const columns: DataColumn<StockTransferSummary>[] = [
    { id: 'fecha', header: 'Fecha', rowHeader: true, cell: (row) => <time dateTime={row.createdAt} className="ap-ref text-ap-ink">{formatDateTime(row.createdAt)}</time> },
    {
      id: 'ruta',
      header: 'Origen y destino',
      cell: (row) => (
        <span className="inline-flex flex-wrap items-center gap-1.5">
          {row.fromBranchName}
          <ArrowRightIcon className="size-3.5 text-ap-muted" aria-label="hacia" />
          {row.toBranchName}
        </span>
      ),
    },
    { id: 'modo', header: 'Qué se movió', cell: (row) => <StatusBadge tone={row.mode === 'ALL' ? 'info' : 'neutral'}>{MODE_LABEL[row.mode]}</StatusBadge> },
    { id: 'unidades', header: 'Unidades', align: 'end', cell: (row) => <span className="ap-num font-semibold text-ap-ink">{formatNumber(row.totalUnits)}</span> },
    { id: 'productos', header: 'Productos', align: 'end', cell: (row) => <span className="ap-num">{formatNumber(row.itemCount)}</span> },
    { id: 'responsable', header: 'Responsable', cell: (row) => actorLabel(row.actorName) },
    {
      id: 'detalle',
      header: 'Detalle',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-11 px-3" onClick={() => setOpenId(row.id)}>
          Ver detalle<span className="sr-only"> de la transferencia del {formatDateTime(row.createdAt)}</span>
        </Button>
      ),
    },
  ]

  return (
    <section aria-labelledby="historial-transferencias" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 id="historial-transferencias" className="text-block-title text-ap-ink">
          Historial
        </h2>
        <SelectField
          id="hist-sucursal"
          label="Sucursal"
          placeholder="Todas"
          containerClassName="w-full sm:w-64"
          options={branches.map((branch) => ({ value: branch.id, label: branch.name }))}
          value={branchId}
          onChange={(event) => {
            setBranchId(event.target.value)
            setPage(1)
          }}
        />
      </div>
      <QueryBoundary result={result} what="las transferencias" keepPrevious>
        {(data) =>
          data.items.length === 0 ? (
            <EmptyState
              icon={<ArrowLeftRightIcon />}
              title={branchId ? 'Esa sucursal no tiene transferencias' : 'Aún no hay transferencias'}
              description="Cuando muevas inventario entre sucursales, cada transferencia aparecerá aquí con su detalle."
              className="py-8"
            />
          ) : (
            <div className="flex flex-col gap-4">
              <DataTable caption="Transferencias de inventario, de la más reciente a la más antigua" columns={columns} rows={data.items} getRowId={(row) => row.id} />
              <ServerPager page={data.page} totalPages={data.totalPages} total={data.total} singular="transferencia" plural="transferencias" onPageChange={setPage} busy={result.status === 'loading'} />
            </div>
          )
        }
      </QueryBoundary>
      <Dialog open={openId !== undefined} onOpenChange={(open) => !open && setOpenId(undefined)}>
        <DialogContent className="sm:max-w-2xl">
          <DialogHeader>
            <DialogTitle>Detalle de la transferencia</DialogTitle>
            <DialogDescription>Productos movidos y existencias que quedaron en cada sucursal.</DialogDescription>
          </DialogHeader>
          {openId ? <TransferDetail transferId={openId} /> : null}
        </DialogContent>
      </Dialog>
    </section>
  )
}

/** `/portal/:storeId/inventario/transferencias`: mover inventario entre sucursales e historial. */
export default function PortalInventoryTransfersPage() {
  const { can } = usePortal()
  const { branches, known, result } = useActiveBranches()
  const mayTransfer = can('INVENTORY_TRANSFER')

  return (
    <>
      <title>Transferencias de inventario | Autopartco</title>
      <PageHeader title="Transferencias" description="Mueve existencias de una sucursal a otra. El cambio es inmediato y queda en el historial de movimientos." />
      <InventoryTabs />
      <QueryBoundary result={result} what="tus sucursales">
        {() => (
          <div className="flex flex-col gap-8">
            {!known ? (
              <AccessState title="No puedes ver las sucursales" description="Para transferir inventario necesitas permiso para ver las sucursales del almacén. Pídelo a quien administra tu almacén." />
            ) : branches.length < MIN_BRANCHES_TO_TRANSFER ? (
              <TransfersRequirement />
            ) : mayTransfer ? (
              <NewTransfer branches={branches} />
            ) : (
              <AccessState title="Solo puedes consultar el historial" description="Tu cuenta no tiene permiso para transferir inventario. Si lo necesitas, pídelo a quien administra tu almacén." />
            )}
            {known ? <History branches={branches} /> : null}
          </div>
        )}
      </QueryBoundary>
    </>
  )
}
