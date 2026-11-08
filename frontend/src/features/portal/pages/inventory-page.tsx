import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ArrowLeftRightIcon, DownloadIcon, HistoryIcon, PackageIcon, PlusIcon, SearchXIcon, SlidersHorizontalIcon, UploadIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatNumber, pluralize } from '@/lib/format'
import { useSubmitOnce } from '@/lib/use-submit-once'
import { getInventoryService } from '../api/inventory-service'
import { getProductService } from '../api/product-service'
import type { LowStockItem, ProductRow } from '../api/types'
import { CsvImportDialog } from '../components/csv-import-dialog'
import { SelectField } from '../components/form-controls'
import { InventoryTabs, MIN_BRANCHES_TO_TRANSFER, TransfersRequirement } from '../components/inventory-tabs'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { ResultCount, SearchField } from '../components/search-field'
import { StockAdjustDialog, type AdjustTarget } from '../components/stock-adjust-dialog'
import { StockQuantity } from '../components/status-badge'
import { SEGMENT_GROUP_CLASS, segmentClass } from '../components/segmented'
import { usePortal } from '../context'
import { useActiveBranches, useStoreQuery } from '../hooks'
import { inventoryErrorMessage, saveFile } from '../lib/inventory'
import { isLowStock } from '../lib/product-bulk'

const STOCK_OPTIONS = [
  { value: 'agotado', label: 'Agotados' },
  { value: 'disponible', label: 'Con existencias' },
]

const normalize = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
const rowKey = (row: ProductRow) => `${row.id}:${row.branchId}`

function matchesStock(row: ProductRow, stock: string): boolean {
  if (stock === 'agotado') return row.quantity <= 0
  if (stock === 'disponible') return row.quantity > 0
  return true
}

/** Entero >= 0 hasta 1.000.000, o `undefined` si el texto no es una cantidad válida. */
function parseCount(text: string): number | undefined {
  if (!/^\d{1,7}$/.test(text.trim())) return undefined
  const value = Number(text)
  return value <= 1_000_000 ? value : undefined
}

/** Edición del stock mínimo de un listado: valor propio o el mínimo general. */
function MinStockEditor({ item }: { item: LowStockItem }) {
  const { storeId, invalidateStore } = usePortal()
  const [draft, setDraft] = useState(String(item.minStock))
  const save = useSubmitOnce(async (minStock: number | null) => (await getInventoryService()).setMinStock(storeId, item.branchId, item.productId, minStock))
  const parsed = parseCount(draft)
  const invalid = parsed === undefined
  const key = `${item.productId}:${item.branchId}`

  async function run(value: number | null) {
    const result = await save.run(value)
    if (result.status === 'ok') {
      invalidateStore()
      toast.success(value === null ? `“${item.productName}” usa el mínimo general.` : `Mínimo de “${item.productName}” en ${item.branchName}: ${value}.`)
    } else if (result.status === 'error') {
      toast.error(inventoryErrorMessage(result.error, 'No pudimos guardar el stock mínimo. Inténtalo de nuevo.'))
    }
  }

  return (
    <form
      className="flex flex-wrap items-start justify-end gap-2"
      onSubmit={(event) => {
        event.preventDefault()
        if (parsed !== undefined && parsed !== item.minStock) void run(parsed)
      }}
    >
      <div className="flex flex-col gap-1">
        <label htmlFor={`min-${key}`} className="sr-only">
          Stock mínimo de {item.productName} en {item.branchName}
        </label>
        <input
          id={`min-${key}`}
          inputMode="numeric"
          autoComplete="off"
          value={draft}
          disabled={save.pending}
          aria-invalid={invalid || undefined}
          aria-describedby={invalid ? `min-err-${key}` : undefined}
          onChange={(event) => setDraft(event.target.value)}
          className={`${INPUT_CLASS} w-24 text-right tabular-nums`}
        />
        {invalid ? (
          <p id={`min-err-${key}`} className="text-xs font-medium text-ap-danger">
            Escribe un entero, 0 o más.
          </p>
        ) : null}
      </div>
      <Button type="submit" variant="outline" className="h-11 px-3" disabled={save.pending || invalid || parsed === item.minStock}>
        Guardar
      </Button>
      <Button type="button" variant="ghost" className="h-11 px-3" disabled={save.pending} onClick={() => void run(null)}>
        Usar el mínimo general
      </Button>
    </form>
  )
}

function LowStockView({ q, branchId, canEditMin, onAdjust }: { q: string; branchId: string; canEditMin: boolean; onAdjust?: (target: AdjustTarget) => void }) {
  const low = useStoreQuery('low-stock', async (id) => (await getInventoryService()).listLowStock(id))
  return (
    <QueryBoundary result={low} what="el stock bajo" keepPrevious>
      {(items) => {
        const term = normalize(q.trim())
        const shown = items.filter(
          (item) => (!branchId || item.branchId === branchId) && (!term || normalize(`${item.productName} ${item.sku ?? ''}`).includes(term)),
        )
        const columns: DataColumn<LowStockItem>[] = [
          {
            id: 'producto',
            header: 'Producto',
            rowHeader: true,
            sortValue: (item) => item.productName,
            cell: (item) => (
              <div className="min-w-0">
                <p className="font-semibold text-ap-ink">{item.productName}</p>
                <p className="ap-ref text-ap-muted">{item.sku ?? 'Sin SKU'}</p>
              </div>
            ),
          },
          { id: 'sucursal', header: 'Sucursal', sortValue: (item) => item.branchName, cell: (item) => item.branchName },
          { id: 'stock', header: 'Existencias', align: 'end', sortValue: (item) => item.stock, cell: (item) => <StockQuantity quantity={item.stock} /> },
          {
            id: 'minimo',
            header: 'Stock mínimo',
            align: 'end',
            cell: (item) => (canEditMin ? <MinStockEditor key={`${item.productId}:${item.branchId}:${item.minStock}`} item={item} /> : <span className="ap-num">{item.minStock}</span>),
          },
          ...(onAdjust
            ? [
                {
                  id: 'acciones',
                  header: 'Acciones',
                  actions: true,
                  align: 'end' as const,
                  cell: (item: LowStockItem) => (
                    <Button
                      variant="outline"
                      className="h-11 px-3"
                      onClick={() => onAdjust({ productId: item.productId, productName: item.productName, branchId: item.branchId, branchName: item.branchName, stock: item.stock })}
                    >
                      <SlidersHorizontalIcon aria-hidden />
                      Ajustar<span className="sr-only"> existencias de {item.productName} en {item.branchName}</span>
                    </Button>
                  ),
                },
              ]
            : []),
        ]
        if (items.length === 0) {
          return <EmptyState icon={<PackageIcon />} title="Nada por reponer" description="Ningún producto está en o por debajo de su stock mínimo." />
        }
        if (shown.length === 0) {
          return <EmptyState icon={<SearchXIcon />} title="Nada coincide con los filtros" description="Prueba con otra sucursal o con otro nombre." />
        }
        return (
          <div className="flex flex-col gap-3">
            <p className="text-sm text-ap-muted">
              {pluralize(shown.length, 'publicación', 'publicaciones')} en o por debajo de su stock mínimo. Sin un mínimo propio se usa 3 unidades.
            </p>
            <DataTable
              caption="Productos con stock bajo por sucursal"
              columns={columns}
              rows={shown}
              getRowId={(item) => `${item.productId}:${item.branchId}`}
              defaultSort={{ columnId: 'stock', direction: 'asc' }}
              pageSize={10}
            />
          </div>
        )
      }}
    </QueryBoundary>
  )
}

function InventoryView({ rows }: { rows: ProductRow[] }) {
  const { storeId, can } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const [params, setParams] = useSearchParams()
  const [adjusting, setAdjusting] = useState<AdjustTarget>()
  const canAdjust = can('INVENTORY_ADJUST')

  const q = params.get('q') ?? ''
  const stock = params.get('stock') ?? ''
  const sucursal = params.get('sucursal') ?? ''
  // `?stock=bajo` (enlaces anteriores, paleta de comandos) abre la vista de stock bajo.
  const lowView = params.get('vista') === 'bajo' || stock === 'bajo'

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

  function setView(low: boolean) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.delete('stock')
        if (low) next.set('vista', 'bajo')
        else next.delete('vista')
        return next
      },
      { replace: true },
    )
  }

  const branches = useMemo(() => [...new Map(rows.map((row) => [row.branchId, row.branchName])).entries()], [rows])
  const filtered = useMemo(() => {
    const term = normalize(q.trim())
    return rows.filter((row) => {
      if (term && !normalize(`${row.name} ${row.sku ?? ''} ${row.partNumber ?? ''}`).includes(term)) return false
      if (sucursal && row.branchId !== sucursal) return false
      return matchesStock(row, stock)
    })
  }, [rows, q, stock, sucursal])

  const totals = useMemo(() => ({ out: rows.filter((row) => row.quantity <= 0).length, low: rows.filter((row) => isLowStock(row)).length }), [rows])

  const columns: DataColumn<ProductRow>[] = [
    {
      id: 'producto',
      header: 'Producto',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div className="min-w-0">
          <Link to={`${base}/productos/${encodeURIComponent(row.id)}`} className="font-semibold text-ap-ink underline-offset-4 hover:underline">
            {row.name}
          </Link>
          <p className="ap-ref text-ap-muted">{row.sku ?? row.partNumber ?? 'Sin SKU'}</p>
        </div>
      ),
    },
    { id: 'sucursal', header: 'Sucursal', sortValue: (row) => row.branchName, cell: (row) => row.branchName },
    { id: 'stock', header: 'Existencias', align: 'end', sortValue: (row) => row.quantity, cell: (row) => <StockQuantity quantity={row.quantity} /> },
    {
      id: 'acciones',
      header: 'Acciones',
      actions: true,
      align: 'end',
      cell: (row) => (
        <div className="flex flex-wrap justify-end gap-2">
          {canAdjust ? (
            <Button
              variant="outline"
              className="h-11 px-3"
              onClick={() => setAdjusting({ productId: row.id, productName: row.name, branchId: row.branchId, branchName: row.branchName, stock: row.quantity })}
            >
              <SlidersHorizontalIcon aria-hidden />
              Ajustar<span className="sr-only"> existencias de {row.name} en {row.branchName}</span>
            </Button>
          ) : null}
          <Button
            variant="ghost"
            className="h-11 px-3"
            nativeButton={false}
            render={<Link to={`${base}/inventario/movimientos?producto=${encodeURIComponent(row.id)}&sucursal=${encodeURIComponent(row.branchId)}`} />}
          >
            <HistoryIcon aria-hidden />
            Historial<span className="sr-only"> de {row.name} en {row.branchName}</span>
          </Button>
        </div>
      ),
    },
  ]

  const filtering = Boolean(q || stock || sucursal)

  return (
    <div className="flex flex-col gap-5">
      <p className="text-sm text-ap-muted tabular-nums">
        {pluralize(rows.length, 'publicación', 'publicaciones')} · {formatNumber(totals.low)} en stock bajo · {pluralize(totals.out, 'agotada', 'agotadas')}
      </p>

      <div className={SEGMENT_GROUP_CLASS} role="group" aria-label="Vista del inventario">
        <button type="button" className={segmentClass(!lowView)} aria-pressed={!lowView} onClick={() => setView(false)}>
          Todas las existencias
        </button>
        <button type="button" className={segmentClass(lowView)} aria-pressed={lowView} onClick={() => setView(true)}>
          Stock bajo <span className="tabular-nums">({formatNumber(totals.low)})</span>
        </button>
      </div>

      <form
        role="search"
        aria-label="Filtrar inventario"
        onSubmit={(event) => event.preventDefault()}
        className={lowView ? 'grid gap-3 sm:grid-cols-2' : 'grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(2,minmax(0,1fr))]'}
      >
        <SearchField value={q} onChange={(value) => setFilter('q', value)} placeholder="Nombre, SKU o referencia" />
        <SelectField id="inv-sucursal" label="Sucursal" placeholder="Todas" options={branches.map(([value, label]) => ({ value, label }))} value={sucursal} onChange={(event) => setFilter('sucursal', event.target.value)} />
        {lowView ? null : (
          <SelectField id="inv-stock" label="Existencias" placeholder="Todas" options={STOCK_OPTIONS} value={stock} onChange={(event) => setFilter('stock', event.target.value)} />
        )}
      </form>

      {lowView ? (
        <LowStockView q={q} branchId={sucursal} canEditMin={can('PRODUCT_UPDATE')} onAdjust={canAdjust ? setAdjusting : undefined} />
      ) : (
        <>
          <ResultCount shown={filtered.length} total={rows.length} singular="publicación" plural="publicaciones" />
          {filtered.length === 0 ? (
            <EmptyState icon={<SearchXIcon />} title="Nada coincide con los filtros" description="Prueba con otra sucursal o quita el filtro de existencias.">
              {filtering ? (
                <Button variant="outline" className="h-11 px-4" onClick={() => setParams({}, { replace: true })}>
                  Quitar filtros
                </Button>
              ) : null}
            </EmptyState>
          ) : (
            <DataTable caption="Existencias por sucursal y producto" columns={columns} rows={filtered} getRowId={rowKey} defaultSort={{ columnId: 'stock', direction: 'asc' }} pageSize={10} />
          )}
        </>
      )}

      <StockAdjustDialog target={adjusting} onOpenChange={(open) => !open && setAdjusting(undefined)} />
    </div>
  )
}

/** `/portal/:storeId/inventario`: existencias por sucursal, ajustes con motivo, stock bajo y CSV. */
export default function PortalInventoryPage() {
  const { storeId, can } = usePortal()
  const [params] = useSearchParams()
  const [importing, setImporting] = useState(false)
  const { branches, known } = useActiveBranches()
  const products = useStoreQuery('products', async (id) => (await getProductService()).list(id))
  const base = `/portal/${encodeURIComponent(storeId)}`
  const exporter = useSubmitOnce(async (branchId: string | undefined) => {
    const file = await (await getInventoryService()).exportCsv(storeId, branchId)
    saveFile(file.blob, file.filename)
  })

  async function exportCsv() {
    const result = await exporter.run(params.get('sucursal') || undefined)
    if (result.status === 'ok') toast.success('Descargamos el inventario en un archivo CSV.')
    else if (result.status === 'error') toast.error(inventoryErrorMessage(result.error, 'No pudimos descargar el inventario. Inténtalo de nuevo.'))
  }

  const canTransfer = known && branches.length >= MIN_BRANCHES_TO_TRANSFER && can('INVENTORY_TRANSFER')

  return (
    <>
      <title>Inventario | Autopartco</title>
      <PageHeader
        title="Inventario"
        description="Existencias por sucursal. Ajusta con motivo, revisa el stock bajo y mueve o actualiza tu inventario."
        actions={
          <>
            <Button variant="outline" className="h-11 px-4" disabled={exporter.pending} onClick={() => void exportCsv()}>
              <DownloadIcon aria-hidden />
              {exporter.pending ? 'Descargando…' : 'Exportar CSV'}
            </Button>
            {can('PRODUCT_UPDATE') && can('INVENTORY_ADJUST') ? (
              <Button variant="outline" className="h-11 px-4" onClick={() => setImporting(true)}>
                <UploadIcon aria-hidden />
                Importar CSV
              </Button>
            ) : null}
            {canTransfer ? (
              <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/inventario/transferencias`} />}>
                <ArrowLeftRightIcon aria-hidden />
                Transferir
              </Button>
            ) : null}
            {can('PRODUCT_CREATE') ? (
              <Button className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/productos/nuevo`} />}>
                <PlusIcon aria-hidden />
                Agregar producto
              </Button>
            ) : null}
          </>
        }
      />
      <InventoryTabs />
      <QueryBoundary result={products} what="el inventario" keepPrevious>
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState icon={<PackageIcon />} title="Aún no hay productos" description="Agrega tu primer producto con sus existencias para verlo aquí por sucursal.">
              {can('PRODUCT_CREATE') ? (
                <Button variant="outline" className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/productos/nuevo`} />}>
                  <PlusIcon aria-hidden />
                  Agregar producto
                </Button>
              ) : null}
            </EmptyState>
          ) : (
            <InventoryView rows={rows} />
          )
        }
      </QueryBoundary>
      {known && branches.length < MIN_BRANCHES_TO_TRANSFER && can('INVENTORY_TRANSFER') ? <TransfersRequirement className="mt-8" /> : null}
      <CsvImportDialog open={importing} onOpenChange={setImporting} />
    </>
  )
}
