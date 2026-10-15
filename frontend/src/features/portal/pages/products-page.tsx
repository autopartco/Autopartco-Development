import { useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { EyeIcon, EyeOffIcon, MoreHorizontalIcon, PackageIcon, PencilIcon, PlusIcon, SearchXIcon, Trash2Icon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { INPUT_CLASS } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { SmartImage } from '@/components/common/smart-image'
import { formatCOP } from '@/lib/format'
import { getProductService } from '../api/product-service'
import type { ProductRow } from '../api/types'
import { ConfirmDialog } from '../components/confirm-dialog'
import { SelectField } from '../components/form-controls'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge, StockBadge } from '../components/status-badge'
import { UsageMeter } from '../components/usage-meter'
import { usePortal } from '../context'
import { focusMain, usePublishProduct, useStoreQuery } from '../hooks'

const STATE_OPTIONS = [
  { value: 'publicados', label: 'Publicados' },
  { value: 'borradores', label: 'Sin publicar' },
]
const STOCK_OPTIONS = [
  { value: 'agotado', label: 'Agotados' },
  { value: 'bajo', label: 'Stock bajo (1 a 3)' },
  { value: 'disponible', label: 'Con existencias' },
]

const normalize = (value: string) => value.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase()

function matchesStock(row: ProductRow, stock: string): boolean {
  if (stock === 'agotado') return row.quantity <= 0
  if (stock === 'bajo') return row.quantity > 0 && row.quantity <= 3
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

function ProductsView({ rows }: { rows: ProductRow[] }) {
  const { storeId, can, subscription } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const [params, setParams] = useSearchParams()
  const { setPublished, busyId } = usePublishProduct()
  const { invalidateStore } = usePortal()
  const [toDelete, setToDelete] = useState<ProductRow>()

  const q = params.get('q') ?? ''
  const estado = params.get('estado') ?? ''
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
      if (estado === 'publicados' && row.state !== 'ACTIVE') return false
      if (estado === 'borradores' && row.state !== 'INACTIVE') return false
      if (sucursal && row.branchId !== sucursal) return false
      return matchesStock(row, stock)
    })
  }, [rows, q, estado, stock, sucursal])

  const columns: DataColumn<ProductRow>[] = [
    {
      id: 'producto',
      header: 'Producto',
      rowHeader: true,
      sortValue: (row) => row.name,
      cell: (row) => (
        <div className="flex items-center gap-3">
          <Thumb row={row} />
          <div className="min-w-0">
            <Link to={`${base}/productos/${encodeURIComponent(row.id)}`} className="font-semibold underline-offset-4 hover:underline">
              {row.name}
            </Link>
            <p className="text-xs font-normal text-muted-foreground">
              {[row.sku ? `SKU ${row.sku}` : null, row.partNumber ? `Ref. ${row.partNumber}` : null].filter(Boolean).join(' · ') || 'Sin SKU ni referencia'}
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
    { id: 'stock', header: 'Existencias', sortValue: (row) => row.quantity, cell: (row) => <StockBadge quantity={row.quantity} /> },
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
            {can('PRODUCT_PUBLISH') ? (
              <DropdownMenuItem disabled={busyId === row.id} onClick={() => void setPublished(row, row.state !== 'ACTIVE')}>
                {row.state === 'ACTIVE' ? <EyeOffIcon aria-hidden /> : <EyeIcon aria-hidden />}
                {row.state === 'ACTIVE' ? 'Retirar del marketplace' : 'Publicar'}
              </DropdownMenuItem>
            ) : null}
            {can('PRODUCT_DELETE') ? (
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

  const filtering = Boolean(q || estado || stock || sucursal)
  const productsUsage = subscription?.usage.products

  return (
    <div className="flex flex-col gap-5">
      {productsUsage && productsUsage.limit !== null && productsUsage.used / productsUsage.limit >= 0.8 ? (
        <UsageMeter capacity="PRODUCTS" used={productsUsage.used} limit={productsUsage.limit} upgradeTo={`${base}/plan`} className="max-w-md rounded-lg bg-card p-4 ring-1 ring-foreground/10" />
      ) : null}

      <form
        role="search"
        aria-label="Filtrar productos"
        onSubmit={(event) => event.preventDefault()}
        className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_repeat(3,minmax(0,1fr))]"
      >
        <div className="flex min-w-0 flex-col gap-1.5">
          <label htmlFor="filtro-q" className="text-sm font-semibold">
            Buscar
          </label>
          <input
            id="filtro-q"
            type="search"
            value={q}
            onChange={(event) => setFilter('q', event.target.value)}
            placeholder="Nombre, SKU o referencia"
            className={INPUT_CLASS}
            autoComplete="off"
          />
        </div>
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
      </form>

      <p className="text-sm text-muted-foreground" role="status">
        {filtering ? `${filtered.length} de ${rows.length} publicaciones` : `${rows.length} ${rows.length === 1 ? 'publicación' : 'publicaciones'}`}
      </p>

      {filtered.length === 0 ? (
        <EmptyState
          icon={<SearchXIcon />}
          title="Ningún producto coincide con los filtros"
          description="Prueba con otra palabra o quita algún filtro."
          className="py-8"
        >
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
    </div>
  )
}

/** `/portal/:storeId/productos`: tabla con filtros y orden en el cliente, y acciones por fila. */
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
