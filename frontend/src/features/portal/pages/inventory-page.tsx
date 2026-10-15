import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { PackageIcon, PlusIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatNumber, pluralize } from '@/lib/format'
import { getProductService } from '../api/product-service'
import type { ProductRow } from '../api/types'
import { ConfirmDialog } from '../components/confirm-dialog'
import { SelectField } from '../components/form-controls'
import { ResultCount, SearchField } from '../components/search-field'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StockBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'

/*
 * Inventario usa la API de productos que ya existe (una fila por producto y sucursal):
 *   GET   /stores/{id}/products                                    -> ProductRow[] (quantity por sucursal)
 *   PATCH /stores/{id}/branches/{branchId}/products/{productId}    -> { quantity } (permiso PRODUCT_UPDATE)
 * Falta en el backend un historial de movimientos de inventario (quién, cuándo, motivo) y un
 * endpoint de ajuste con motivo, p. ej. POST /stores/{id}/branches/{branchId}/inventory/{productId}/adjustments.
 */

const STOCK_OPTIONS = [
  { value: 'bajo', label: 'Stock bajo (1 a 3)' },
  { value: 'agotado', label: 'Agotados' },
  { value: 'disponible', label: 'Con existencias' },
]

const normalize = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()
const rowKey = (row: ProductRow) => `${row.id}:${row.branchId}`

function matchesStock(row: ProductRow, stock: string): boolean {
  if (stock === 'agotado') return row.quantity <= 0
  if (stock === 'bajo') return row.quantity > 0 && row.quantity <= 3
  if (stock === 'disponible') return row.quantity > 0
  return true
}

/** Entero >= 0 o `undefined` si el texto no es una cantidad válida. */
function parseQuantity(text: string): number | undefined {
  if (!/^\d{1,6}$/.test(text.trim())) return undefined
  return Number(text)
}

function InventoryView({ rows }: { rows: ProductRow[] }) {
  const { storeId, can, invalidateStore } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const [params, setParams] = useSearchParams()
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [pending, setPending] = useState<{ row: ProductRow; quantity: number }>()
  const canEdit = can('PRODUCT_UPDATE')

  const q = params.get('q') ?? ''
  const stock = params.get('stock') ?? ''
  const sucursal = params.get('sucursal') ?? ''

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
  const filtered = useMemo(() => {
    const term = normalize(q.trim())
    return rows.filter((row) => {
      if (term && !normalize(`${row.name} ${row.sku ?? ''} ${row.partNumber ?? ''}`).includes(term)) return false
      if (sucursal && row.branchId !== sucursal) return false
      return matchesStock(row, stock)
    })
  }, [rows, q, stock, sucursal])

  const totals = useMemo(
    () => ({
      out: rows.filter((row) => row.quantity <= 0).length,
      low: rows.filter((row) => row.quantity > 0 && row.quantity <= 3).length,
    }),
    [rows],
  )

  async function save() {
    if (!pending) return
    await (await getProductService()).update(storeId, pending.row.branchId, pending.row.id, { quantity: pending.quantity })
    setDrafts((current) => {
      const next = { ...current }
      delete next[rowKey(pending.row)]
      return next
    })
    invalidateStore()
    toast.success(`Existencias de “${pending.row.name}” actualizadas a ${pending.quantity}.`)
  }

  const columns: DataColumn<ProductRow>[] = [
    {
      id: 'producto',
      header: 'Producto',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div className="min-w-0">
          <Link to={`${base}/productos/${encodeURIComponent(row.id)}`} className="font-semibold underline-offset-4 hover:underline">
            {row.name}
          </Link>
          <p className="text-xs text-muted-foreground">{row.sku ?? row.partNumber ?? 'Sin SKU'}</p>
        </div>
      ),
    },
    { id: 'sucursal', header: 'Sucursal', sortValue: (row) => row.branchName, cell: (row) => row.branchName },
    { id: 'stock', header: 'Existencias', sortValue: (row) => row.quantity, cell: (row) => <StockBadge quantity={row.quantity} /> },
  ]

  if (canEdit) {
    columns.push({
      id: 'editar',
      header: 'Ajustar cantidad',
      cell: (row) => {
        const key = rowKey(row)
        const draft = drafts[key] ?? String(row.quantity)
        const parsed = parseQuantity(draft)
        const invalid = parsed === undefined
        const changed = !invalid && parsed !== row.quantity
        return (
          <form
            className="flex items-start gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              if (changed) setPending({ row, quantity: parsed })
            }}
          >
            <div className="flex flex-col gap-1">
              <label htmlFor={`qty-${key}`} className="sr-only">
                Cantidad de {row.name} en {row.branchName}
              </label>
              <input
                id={`qty-${key}`}
                inputMode="numeric"
                autoComplete="off"
                value={draft}
                aria-invalid={invalid || undefined}
                aria-describedby={invalid ? `qty-err-${key}` : undefined}
                onChange={(event) => setDrafts((current) => ({ ...current, [key]: event.target.value }))}
                className={`${INPUT_CLASS} w-24 text-right tabular-nums`}
              />
              {invalid ? (
                <p id={`qty-err-${key}`} className="text-xs font-medium text-destructive">
                  Escribe un número entero, 0 o más.
                </p>
              ) : null}
            </div>
            <Button type="submit" variant="outline" className="h-11 px-3" disabled={!changed}>
              Guardar
            </Button>
          </form>
        )
      },
    })
  }

  const filtering = Boolean(q || stock || sucursal)

  return (
    <div className="flex flex-col gap-5">
      <p className="text-sm text-muted-foreground">
        {pluralize(rows.length, 'publicación', 'publicaciones')} · {formatNumber(totals.low)} con stock bajo ·{' '}
        {pluralize(totals.out, 'agotada', 'agotadas')}
      </p>

      <form role="search" aria-label="Filtrar inventario" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(2,minmax(0,1fr))]">
        <SearchField value={q} onChange={(value) => setFilter('q', value)} placeholder="Nombre, SKU o referencia" />
        <SelectField id="inv-sucursal" label="Sucursal" placeholder="Todas" options={branches.map(([value, label]) => ({ value, label }))} value={sucursal} onChange={(event) => setFilter('sucursal', event.target.value)} />
        <SelectField id="inv-stock" label="Existencias" placeholder="Todas" options={STOCK_OPTIONS} value={stock} onChange={(event) => setFilter('stock', event.target.value)} />
      </form>

      <ResultCount shown={filtered.length} total={rows.length} singular="publicación" plural="publicaciones" />

      {filtered.length === 0 ? (
        <EmptyState
          icon={<SearchXIcon />}
          title="Nada coincide con los filtros"
          description="Prueba con otra sucursal o quita el filtro de existencias."
        >
          {filtering ? (
            <Button variant="outline" className="h-11 px-4" onClick={() => setParams({}, { replace: true })}>
              Quitar filtros
            </Button>
          ) : null}
        </EmptyState>
      ) : (
        <DataTable caption="Existencias por sucursal y producto" columns={columns} rows={filtered} getRowId={rowKey} defaultSort={{ columnId: 'stock', direction: 'asc' }} pageSize={10} />
      )}

      <ConfirmDialog
        open={pending !== undefined}
        onOpenChange={(open) => !open && setPending(undefined)}
        title="Cambiar existencias"
        description={
          pending ? (
            <>
              <strong>{pending.row.name}</strong> en {pending.row.branchName} pasará de {pending.row.quantity} a {pending.quantity}{' '}
              {pluralize(pending.quantity, 'unidad', 'unidades')}. Los compradores verán la nueva cantidad.
            </>
          ) : null
        }
        confirmLabel="Cambiar existencias"
        onConfirm={save}
      />
    </div>
  )
}

/** `/portal/:storeId/inventario`: existencias por sucursal con edición rápida y confirmación. */
export default function PortalInventoryPage() {
  const { storeId, can } = usePortal()
  const products = useStoreQuery('products', async (id) => (await getProductService()).list(id))
  const base = `/portal/${encodeURIComponent(storeId)}`

  return (
    <>
      <title>Inventario | Autopartco</title>
      <PageHeader
        title="Inventario"
        description="Existencias por sucursal. Ajusta la cantidad aquí y confirma el cambio."
        actions={
          can('PRODUCT_CREATE') ? (
            <Button className="h-11 px-4" nativeButton={false} render={<Link to={`${base}/productos/nuevo`} />}>
              <PlusIcon aria-hidden />
              Agregar producto
            </Button>
          ) : null
        }
      />
      <QueryBoundary result={products} what="el inventario" keepPrevious>
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState icon={<PackageIcon />} title="Aún no hay productos" description="Agrega tu primer producto con sus existencias para verlo aquí por sucursal." />
          ) : (
            <InventoryView rows={rows} />
          )
        }
      </QueryBoundary>
    </>
  )
}
