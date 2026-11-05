import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { CircleCheckIcon, DollarSignIcon, EyeIcon, EyeOffIcon, MoreHorizontalIcon, PackageIcon, PencilIcon, PlusIcon, SearchXIcon, Trash2Icon, XIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { SmartImage } from '@/components/common/smart-image'
import { formatCOP } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { getProductService } from '../api/product-service'
import type { ProductBulkAction, ProductBulkInput, ProductBulkResult, ProductRow } from '../api/types'
import { BulkPriceDialog, type BulkPriceInput } from '../components/bulk-price-dialog'
import { ConfirmDialog } from '../components/confirm-dialog'
import { SelectField } from '../components/form-controls'
import { ResultCount, SearchField } from '../components/search-field'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge, StockBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { focusMain, usePublishProduct, useStoreQuery } from '../hooks'
import { actionErrorMessage } from '../lib/errors'
import { humanizeCode } from '../lib/labels'
import { BULK_DELETE_LIMIT_TEXT, BULK_DELETE_MAX, BULK_DONE, BULK_MAX, isLowStock, minStockOf, skipReason } from '../lib/product-bulk'

const STATE_OPTIONS = [
  { value: 'publicados', label: 'Publicados' },
  { value: 'borradores', label: 'Sin publicar' },
]
const STOCK_OPTIONS = [
  { value: 'agotado', label: 'Agotados' },
  { value: 'bajo', label: 'Stock bajo (en o bajo el mínimo)' },
  { value: 'disponible', label: 'Con existencias' },
]
const IMAGE_OPTIONS = [
  { value: 'sin', label: 'Sin imágenes' },
  { value: 'con', label: 'Con imágenes' },
]

const normalize = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

function matchesStock(row: ProductRow, stock: string): boolean {
  if (stock === 'agotado') return row.quantity <= 0
  if (stock === 'bajo') return isLowStock(row)
  if (stock === 'disponible') return row.quantity > 0
  return true
}

function Thumb({ row }: { row: ProductRow }) {
  const url = row.images.find((image) => image.isMain)?.url ?? row.images[0]?.url
  return url ? (
    <SmartImage src={url} alt="" width={44} height={44} frameClassName="size-11 shrink-0 rounded-md" />
  ) : (
    <div aria-hidden className="grid size-11 shrink-0 place-items-center rounded-md bg-concrete text-muted-foreground">
      <PackageIcon className="size-5" />
    </div>
  )
}

interface BulkOutcome {
  action: ProductBulkAction
  result: ProductBulkResult
  /** Nombres conocidos al momento de la acción (los eliminados ya no estarán en la lista). */
  names: Map<string, string>
}

/** Resumen persistente de la última acción masiva: cuántos cambiaron y cuáles se omitieron y por qué. */
function BulkOutcomeNotice({ outcome, onDismiss }: { outcome: BulkOutcome; onDismiss: () => void }) {
  const { action, result, names } = outcome
  const done = BULK_DONE[action]
  return (
    <div role="status" className="flex flex-col gap-2 rounded-lg border border-foreground/10 bg-card p-4 text-sm">
      <div className="flex items-start justify-between gap-3">
        <p className="flex items-start gap-2 font-semibold">
          <CircleCheckIcon className="mt-0.5 size-4 shrink-0 text-stock" aria-hidden />
          {result.affected} {result.affected === 1 ? done.one : done.many}
          {result.skipped.length > 0 ? ` · ${result.skipped.length} sin cambios` : ''}.
        </p>
        <Button variant="ghost" size="icon" className="size-10 shrink-0 pointer-coarse:size-11" aria-label="Cerrar resumen" onClick={onDismiss}>
          <XIcon aria-hidden />
        </Button>
      </div>
      {result.skipped.length > 0 ? (
        <ul className="flex max-h-48 flex-col gap-1 overflow-y-auto pl-6 text-muted-foreground">
          {result.skipped.map((item) => (
            <li key={item.productId}>
              <span className="font-medium text-foreground">{names.get(item.productId) ?? 'Producto'}</span>: {skipReason(item.code)}.
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}

function ProductsView({ rows }: { rows: ProductRow[] }) {
  const { storeId, can, subscription, promptUpgrade, invalidateStore } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const [params, setParams] = useSearchParams()
  const { setPublished, busyId } = usePublishProduct()
  const options = useAsync('portal:product-options', async () => (await getProductService()).options())
  const [toDelete, setToDelete] = useState<ProductRow>()
  const [selected, setSelected] = useState<Set<string>>(() => new Set())
  const [bulkBusy, setBulkBusy] = useState<ProductBulkAction>()
  const [confirmBulkDelete, setConfirmBulkDelete] = useState(false)
  const [priceOpen, setPriceOpen] = useState(false)
  const [outcome, setOutcome] = useState<BulkOutcome>()

  const q = params.get('q') ?? ''
  const estado = params.get('estado') ?? ''
  const stock = params.get('stock') ?? ''
  const sucursal = params.get('sucursal') ?? ''
  const categoria = params.get('categoria') ?? ''
  const fabricante = params.get('fabricante') ?? ''
  const imagenes = params.get('imagenes') ?? ''

  function setFilter(name: string, value: string) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (value) next.set(name, value)
        else next.delete(name)
        return next
      },
      { replace: true },
    )
  }

  const branches = useMemo(() => [...new Map(rows.map((row) => [row.branchId, row.branchName])).entries()], [rows])
  const manufacturers = useMemo(
    () => [...new Map(rows.map((row) => [row.manufacturerId, row.manufacturerName])).entries()].toSorted((a, b) => a[1].localeCompare(b[1], 'es')),
    [rows],
  )
  const categoryOptions = useMemo(() => {
    const used = new Set(rows.map((row) => row.categoryId))
    const known = (options.data?.categories ?? []).filter((item) => used.has(item.id)).map((item) => ({ value: item.id, label: humanizeCode(item.code) }))
    return known.toSorted((a, b) => a.label.localeCompare(b.label, 'es'))
  }, [rows, options.data])

  const filtered = useMemo(() => {
    const term = normalize(q.trim())
    return rows.filter((row) => {
      if (term && !normalize(`${row.name} ${row.sku ?? ''} ${row.partNumber ?? ''}`).includes(term)) return false
      if (estado === 'publicados' && row.state !== 'ACTIVE') return false
      if (estado === 'borradores' && row.state !== 'INACTIVE') return false
      if (sucursal && row.branchId !== sucursal) return false
      if (categoria && row.categoryId !== categoria) return false
      if (fabricante && row.manufacturerId !== fabricante) return false
      if (imagenes === 'sin' && row.images.length > 0) return false
      if (imagenes === 'con' && row.images.length === 0) return false
      return matchesStock(row, stock)
    })
  }, [rows, q, estado, stock, sucursal, categoria, fabricante, imagenes])

  // Solo cuentan los seleccionados que siguen existiendo (tras eliminar o recargar).
  const existing = useMemo(() => new Set(rows.map((row) => row.id)), [rows])
  const selectedIds = [...selected].filter((id) => existing.has(id))
  const filteredIds = [...new Set(filtered.map((row) => row.id))]
  const allFilteredSelected = filteredIds.length > 0 && filteredIds.every((id) => selected.has(id))
  const someFilteredSelected = filteredIds.some((id) => selected.has(id))

  const canPublish = can('PRODUCT_PUBLISH')
  const canDelete = can('PRODUCT_DELETE')
  const canUpdate = can('PRODUCT_UPDATE')
  const canBulk = canPublish || canDelete || canUpdate
  const tooMany = selectedIds.length > BULK_MAX
  const tooManyToDelete = selectedIds.length > BULK_DELETE_MAX

  function toggle(id: string, checked: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      if (checked) next.add(id)
      else next.delete(id)
      return next
    })
  }

  function toggleFiltered(checked: boolean) {
    setSelected((current) => {
      const next = new Set(current)
      for (const id of filteredIds) {
        if (checked) next.add(id)
        else next.delete(id)
      }
      return next
    })
  }

  const nameOf = () => new Map(rows.map((row) => [row.id, row.name]))

  /** Ejecuta la acción; lanza si falla (para que los diálogos muestren el motivo). */
  async function runBulk(input: Omit<ProductBulkInput, 'productIds'>): Promise<void> {
    const names = nameOf()
    const result = await (await getProductService()).bulk(storeId, { ...input, productIds: selectedIds })
    setOutcome({ action: input.action, result, names })
    setSelected(new Set())
    invalidateStore()
    const done = BULK_DONE[input.action]
    toast.success(`${result.affected} ${result.affected === 1 ? done.one : done.many}${result.skipped.length > 0 ? `; ${result.skipped.length} sin cambios (mira el detalle)` : ''}.`)
  }

  async function publishSelected(action: 'PUBLISH' | 'UNPUBLISH') {
    setBulkBusy(action)
    try {
      await runBulk({ action })
    } catch (error) {
      if (!promptUpgrade(error)) toast.error(actionErrorMessage(error, 'No pudimos cambiar la publicación. Inténtalo de nuevo.'))
    } finally {
      setBulkBusy(undefined)
    }
  }

  async function adjustPrices(input: BulkPriceInput) {
    try {
      await runBulk({ action: 'PRICE', branchId: input.branchId, priceMode: input.priceMode, value: input.value })
      setPriceOpen(false)
    } catch (error) {
      if (promptUpgrade(error)) {
        setPriceOpen(false)
        return
      }
      throw error
    }
  }

  const columns: DataColumn<ProductRow>[] = [
    ...(canBulk
      ? [
          {
            id: 'seleccion',
            header: 'Seleccionar',
            actions: true,
            className: 'w-12',
            cell: (row: ProductRow) => (
              <label className="inline-flex size-10 cursor-pointer items-center justify-center pointer-coarse:size-11">
                <Checkbox checked={selected.has(row.id)} onCheckedChange={(checked) => toggle(row.id, checked === true)} aria-label={`Seleccionar ${row.name}`} />
              </label>
            ),
          },
        ]
      : []),
    {
      id: 'producto',
      header: 'Producto',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div className="flex items-center gap-3">
          <Thumb row={row} />
          <div className="min-w-0">
            <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
              <Link to={`${base}/productos/${encodeURIComponent(row.id)}`} className="font-semibold underline-offset-4 hover:underline">
                {row.name}
              </Link>
              {row.universal ? <StatusBadge tone="info">Universal</StatusBadge> : null}
            </div>
            <p className="text-xs font-normal text-muted-foreground">
              {[row.manufacturerName, row.sku ? `SKU ${row.sku}` : null, row.partNumber ? `Ref. ${row.partNumber}` : null].filter(Boolean).join(' · ') || 'Sin SKU ni referencia'}
            </p>
          </div>
        </div>
      ),
    },
    ...(branches.length > 1 ? [{ id: 'sucursal', header: 'Sucursal', sortValue: (row: ProductRow) => row.branchName, cell: (row: ProductRow) => row.branchName }] : []),
    {
      id: 'precio',
      header: 'Precio',
      align: 'end',
      sortValue: (row) => row.effectivePrice,
      cell: (row) => (
        <div className="tabular-nums">
          <span className="font-semibold">{formatCOP(row.effectivePrice)}</span>
          {row.effectivePrice < row.price ? (
            <p className="text-xs text-muted-foreground">
              <s>{formatCOP(row.price)}</s> · {row.appliedPromotionName ?? 'Promoción'}
            </p>
          ) : null}
        </div>
      ),
    },
    {
      id: 'stock',
      header: 'Existencias',
      sortValue: (row) => row.quantity,
      cell: (row) => (
        <div className="flex flex-col items-start gap-0.5">
          <StockBadge quantity={row.quantity} />
          {row.quantity > 0 && isLowStock(row) ? <span className="text-xs text-muted-foreground">Mínimo {minStockOf(row)}</span> : null}
        </div>
      ),
    },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => row.state,
      cell: (row) => (row.state === 'ACTIVE' ? <StatusBadge tone="success">Publicado</StatusBadge> : <StatusBadge>Sin publicar</StatusBadge>),
    },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={`Acciones para ${row.name}`}
            className="inline-flex size-10 items-center justify-center rounded-lg outline-none hover:bg-concrete data-popup-open:bg-concrete pointer-coarse:size-11"
          >
            <MoreHorizontalIcon className="size-5" aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent>
            <DropdownMenuItem render={<Link to={`${base}/productos/${encodeURIComponent(row.id)}`} />}>
              <PencilIcon aria-hidden />
              Editar
            </DropdownMenuItem>
            {canPublish ? (
              <DropdownMenuItem disabled={busyId === row.id} onClick={() => void setPublished(row, row.state !== 'ACTIVE')}>
                {row.state === 'ACTIVE' ? <EyeOffIcon aria-hidden /> : <EyeIcon aria-hidden />}
                {row.state === 'ACTIVE' ? 'Retirar del marketplace' : 'Publicar'}
              </DropdownMenuItem>
            ) : null}
            {canDelete ? (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem variant="destructive" onClick={() => setToDelete(row)}>
                  <Trash2Icon aria-hidden />
                  Eliminar
                </DropdownMenuItem>
              </>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>
      ),
    },
  ]

  const productsUsage = subscription?.usage.products
  const busy = bulkBusy !== undefined
  const count = selectedIds.length

  return (
    <div className="flex flex-col gap-5">
      {productsUsage && productsUsage.limit !== null && productsUsage.used / productsUsage.limit >= 0.8 ? (
        <UsageMeter capacity="PRODUCTS" used={productsUsage.used} limit={productsUsage.limit} upgradeTo={`${base}/plan`} className="max-w-md rounded-lg bg-card p-4 ring-1 ring-foreground/10" />
      ) : null}

      <form role="search" aria-label="Filtrar productos" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <SearchField value={q} onChange={(value) => setFilter('q', value)} placeholder="Nombre, SKU o referencia" />
        <SelectField id="filtro-estado" label="Estado" placeholder="Todos" options={STATE_OPTIONS} value={estado} onChange={(event) => setFilter('estado', event.target.value)} />
        <SelectField id="filtro-stock" label="Existencias" placeholder="Todas" options={STOCK_OPTIONS} value={stock} onChange={(event) => setFilter('stock', event.target.value)} />
        {branches.length > 1 ? (
          <SelectField
            id="filtro-sucursal"
            label="Sucursal"
            placeholder="Todas"
            options={branches.map(([value, label]) => ({ value, label }))}
            value={sucursal}
            onChange={(event) => setFilter('sucursal', event.target.value)}
          />
        ) : null}
        <SelectField
          id="filtro-categoria"
          label="Categoría"
          placeholder={options.status === 'error' ? 'No disponible' : 'Todas'}
          options={categoryOptions}
          value={categoria}
          disabled={options.status === 'error' && !categoria}
          onChange={(event) => setFilter('categoria', event.target.value)}
        />
        <SelectField
          id="filtro-fabricante"
          label="Fabricante"
          placeholder="Todos"
          options={manufacturers.map(([value, label]) => ({ value, label }))}
          value={fabricante}
          onChange={(event) => setFilter('fabricante', event.target.value)}
        />
        <SelectField id="filtro-imagenes" label="Imágenes" placeholder="Todas" options={IMAGE_OPTIONS} value={imagenes} onChange={(event) => setFilter('imagenes', event.target.value)} />
      </form>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <ResultCount shown={filtered.length} total={rows.length} singular="publicación" plural="publicaciones" />
        {canBulk && filtered.length > 0 ? (
          <label className="inline-flex min-h-11 cursor-pointer items-center gap-2.5 text-sm font-medium">
            <Checkbox checked={allFilteredSelected} indeterminate={someFilteredSelected && !allFilteredSelected} onCheckedChange={(checked) => toggleFiltered(checked === true)} />
            Seleccionar los {filteredIds.length} {filteredIds.length === 1 ? 'producto filtrado' : 'productos filtrados'}
          </label>
        ) : null}
      </div>

      {canBulk && count > 0 ? (
        <div
          role="region"
          aria-label="Acciones para los productos seleccionados"
          className="sticky top-2 z-20 flex flex-col gap-3 rounded-xl bg-card p-3 shadow-lg ring-2 ring-recommended/40 sm:flex-row sm:flex-wrap sm:items-center"
        >
          <p className="text-sm font-semibold" aria-live="polite">
            {count} {count === 1 ? 'producto seleccionado' : 'productos seleccionados'}
            {tooMany ? <span className="block font-normal text-muted-foreground">Puedes aplicar acciones a {BULK_MAX} productos a la vez. Quita algunos de la selección.</span> : null}
            {!tooMany && canDelete && tooManyToDelete ? <span className="block font-normal text-muted-foreground">{BULK_DELETE_LIMIT_TEXT}</span> : null}
          </p>
          <div className="flex flex-wrap gap-2 sm:ml-auto">
            {canPublish ? (
              <>
                <Button variant="outline" className="h-11 px-4" disabled={busy || tooMany} onClick={() => void publishSelected('PUBLISH')}>
                  <EyeIcon aria-hidden />
                  {bulkBusy === 'PUBLISH' ? 'Publicando…' : 'Publicar'}
                </Button>
                <Button variant="outline" className="h-11 px-4" disabled={busy || tooMany} onClick={() => void publishSelected('UNPUBLISH')}>
                  <EyeOffIcon aria-hidden />
                  {bulkBusy === 'UNPUBLISH' ? 'Retirando…' : 'Retirar'}
                </Button>
              </>
            ) : null}
            {canUpdate ? (
              <Button variant="outline" className="h-11 px-4" disabled={busy || tooMany} onClick={() => setPriceOpen(true)}>
                <DollarSignIcon aria-hidden />
                Ajustar precio
              </Button>
            ) : null}
            {canDelete ? (
              <Button variant="destructive" className="h-11 px-4" disabled={busy || tooMany || tooManyToDelete} onClick={() => setConfirmBulkDelete(true)}>
                <Trash2Icon aria-hidden />
                Eliminar
              </Button>
            ) : null}
            <Button variant="ghost" className="h-11 px-4" disabled={busy} onClick={() => setSelected(new Set())}>
              Quitar selección
            </Button>
          </div>
        </div>
      ) : null}

      {outcome ? <BulkOutcomeNotice outcome={outcome} onDismiss={() => setOutcome(undefined)} /> : null}

      {filtered.length === 0 ? (
        <EmptyState icon={<SearchXIcon />} title="Ningún producto coincide con los filtros" description="Prueba con otra palabra o quita algún filtro." className="py-8">
          <Button variant="outline" className="h-11 px-4" onClick={() => setParams({}, { replace: true })}>
            Quitar filtros
          </Button>
        </EmptyState>
      ) : (
        <DataTable
          caption="Productos del almacén, una fila por producto y sucursal"
          columns={columns}
          rows={filtered}
          getRowId={(row) => `${row.id}:${row.branchId}`}
          defaultSort={{ columnId: 'producto', direction: 'asc' }}
          pageSize={10}
        />
      )}

      <ConfirmDialog
        open={toDelete !== undefined}
        onOpenChange={(open) => !open && setToDelete(undefined)}
        title={`¿Eliminar “${toDelete?.name ?? ''}”?`}
        description="El producto dejará de verse en el marketplace y en tu inventario, con sus imágenes y existencias. Esta acción no se puede deshacer."
        confirmLabel="Eliminar producto"
        destructive
        onConfirm={async () => {
          if (!toDelete) return
          await (await getProductService()).remove(storeId, toDelete.id)
          invalidateStore()
          toast.success(`Eliminaste “${toDelete.name}”.`)
          focusMain()
        }}
      />

      <ConfirmDialog
        open={confirmBulkDelete}
        onOpenChange={setConfirmBulkDelete}
        title={`¿Eliminar ${count === 1 ? 'el producto seleccionado' : `${count} productos`}?`}
        description="Dejarán de verse en el marketplace y en tu inventario, con sus imágenes y existencias en todas las sucursales. Se eliminan todos o ninguno. Esta acción no se puede deshacer."
        confirmLabel={count === 1 ? 'Eliminar producto' : `Eliminar ${count} productos`}
        destructive
        onConfirm={async () => {
          await runBulk({ action: 'DELETE' })
          focusMain()
        }}
      />

      <BulkPriceDialog
        open={priceOpen}
        onOpenChange={setPriceOpen}
        count={count}
        branches={branches.map(([value, label]) => ({ value, label }))}
        onSubmit={adjustPrices}
      />
    </div>
  )
}

/** `/portal/:storeId/productos`: tabla con filtros, orden y selección múltiple en el cliente, y acciones por fila o en lote. */
export default function PortalProductsPage() {
  const { storeId, can } = usePortal()
  const products = useStoreQuery('products', async (id) => (await getProductService()).list(id))
  const base = `/portal/${encodeURIComponent(storeId)}`

  return (
    <>
      <title>Productos | Autopartco</title>
      <PageHeader
        title="Productos"
        description="Lo que vendes, por sucursal. Publica un producto para que los compradores lo vean."
        actions={
          can('PRODUCT_CREATE') ? (
            <Button className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/productos/nuevo`} />}>
              <PlusIcon aria-hidden />
              Agregar producto
            </Button>
          ) : null
        }
      />
      <QueryBoundary result={products} what="tus productos">
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState
              icon={<PackageIcon />}
              title="Aún no has cargado productos"
              description="Agrega tu primer repuesto con precio, existencias y los vehículos en los que sirve. Podrás publicarlo cuando esté listo."
            >
              {can('PRODUCT_CREATE') ? (
                <Button className="h-11 px-5" nativeButton={false} render={<Link to={`${base}/productos/nuevo`} />}>
                  <PlusIcon aria-hidden />
                  Agregar producto
                </Button>
              ) : null}
            </EmptyState>
          ) : (
            <ProductsView rows={rows} />
          )
        }
      </QueryBoundary>
    </>
  )
}
