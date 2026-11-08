import { useMemo } from 'react'
import { useSearchParams } from 'react-router'
import { ArrowDownIcon, ArrowUpIcon, HistoryIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { TextField } from '@/components/common/form-fields'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { getInventoryService } from '../api/inventory-service'
import { getProductService } from '../api/product-service'
import type { StockMovement } from '../api/types'
import { SelectField } from '../components/form-controls'
import { InventoryTabs } from '../components/inventory-tabs'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { ServerPager } from '../components/server-pager'
import { StatusBadge } from '../components/status-badge'
import { useActiveBranches, useStoreQuery } from '../hooks'
import { actorLabel, endOfDay, startOfDay, SYSTEM_REASONS } from '../lib/inventory'
import { formatDateTime } from '../lib/labels'

const PAGE_SIZE = 20

interface Filters {
  sucursal: string
  producto: string
  motivo: string
  desde: string
  hasta: string
}

function MovementsList({ filters, page, onPageChange }: { filters: Filters; page: number; onPageChange: (page: number) => void }) {
  const key = JSON.stringify([filters, page])
  const result = useStoreQuery(`movements:${key}`, async (id) =>
    (await getInventoryService()).listMovements(id, {
      branchId: filters.sucursal || undefined,
      productId: filters.producto || undefined,
      reasonCode: filters.motivo || undefined,
      from: filters.desde ? startOfDay(filters.desde) : undefined,
      to: filters.hasta ? endOfDay(filters.hasta) : undefined,
      page,
      size: PAGE_SIZE,
    }),
  )
  const filtering = Object.values(filters).some(Boolean)

  const columns: DataColumn<StockMovement>[] = [
    {
      id: 'fecha',
      header: 'Fecha',
      rowHeader: true,
      cell: (row) => <time dateTime={row.occurredAt} className="ap-ref text-ap-ink">{formatDateTime(row.occurredAt)}</time>,
    },
    {
      id: 'producto',
      header: 'Producto',
      cell: (row) => (
        <div className="min-w-0">
          <p className="font-semibold text-ap-ink">{row.productName}</p>
          <p className="ap-ref text-ap-muted">{row.sku ?? 'Sin SKU'}</p>
        </div>
      ),
    },
    { id: 'sucursal', header: 'Sucursal', cell: (row) => row.branchName },
    {
      id: 'movimiento',
      header: 'Movimiento',
      cell: (row) =>
        row.movementType === 'IN' ? (
          <StatusBadge tone="success">
            <ArrowUpIcon className="size-3" aria-hidden />
            Entrada
          </StatusBadge>
        ) : (
          <StatusBadge tone="warning">
            <ArrowDownIcon className="size-3" aria-hidden />
            Salida
          </StatusBadge>
        ),
    },
    {
      id: 'cantidad',
      header: 'Cantidad',
      align: 'end',
      cell: (row) => <span className="ap-num font-semibold text-ap-ink">{row.movementType === 'IN' ? '+' : '−'}{row.quantity}</span>,
    },
    {
      id: 'existencias',
      header: 'Existencias',
      align: 'end',
      cell: (row) => (
        <span className="ap-num whitespace-nowrap text-ap-muted">
          {row.previousStock} <span aria-label="pasa a">→</span> <strong className="font-semibold text-ap-ink">{row.newStock}</strong>
        </span>
      ),
    },
    {
      id: 'motivo',
      header: 'Motivo',
      cell: (row) => (
        <div className="min-w-0">
          <p>{row.reasonLabel}</p>
          {row.notes ? <p className="text-xs text-ap-muted">{row.notes}</p> : null}
        </div>
      ),
    },
    { id: 'responsable', header: 'Responsable', cell: (row) => actorLabel(row.actorName) },
  ]

  return (
    <QueryBoundary result={result} what="los movimientos" keepPrevious>
      {(data) =>
        data.items.length === 0 ? (
          <EmptyState
            icon={filtering ? <SearchXIcon /> : <HistoryIcon />}
            title={filtering ? 'Ningún movimiento coincide' : 'Aún no hay movimientos'}
            description={filtering ? 'Prueba con otro rango de fechas, otro producto o quita algún filtro.' : 'Cada ajuste, transferencia, importación y venta que cambie tus existencias quedará registrado aquí.'}
          />
        ) : (
          <div className="flex flex-col gap-4">
            <DataTable caption="Movimientos de inventario, del más reciente al más antiguo" columns={columns} rows={data.items} getRowId={(row) => row.id} />
            <ServerPager page={data.page} totalPages={data.totalPages} total={data.total} singular="movimiento" plural="movimientos" onPageChange={onPageChange} busy={result.status === 'loading'} />
          </div>
        )
      }
    </QueryBoundary>
  )
}

/** `/portal/:storeId/inventario/movimientos`: historial de movimientos filtrable y paginado por el servidor. */
export default function PortalInventoryMovementsPage() {
  const [params, setParams] = useSearchParams()
  const { branches } = useActiveBranches()
  const products = useStoreQuery('products', async (id) => (await getProductService()).list(id))
  const reasons = useStoreQuery('inventory-reasons', async (id) => (await getInventoryService()).listReasons(id))

  const filters: Filters = {
    sucursal: params.get('sucursal') ?? '',
    producto: params.get('producto') ?? '',
    motivo: params.get('motivo') ?? '',
    desde: params.get('desde') ?? '',
    hasta: params.get('hasta') ?? '',
  }
  const page = Math.max(1, Number.parseInt(params.get('pagina') ?? '1', 10) || 1)
  const badRange = Boolean(filters.desde && filters.hasta && filters.desde > filters.hasta)

  function setParam(name: keyof Filters | 'pagina', value: string) {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (value) next.set(name, value)
        else next.delete(name)
        if (name !== 'pagina') next.delete('pagina')
        return next
      },
      { replace: true },
    )
  }

  const productOptions = useMemo(
    () =>
      [...new Map((products.data ?? []).map((row) => [row.id, row.name])).entries()]
        .map(([value, label]) => ({ value, label }))
        .toSorted((a, b) => a.label.localeCompare(b.label, 'es')),
    [products.data],
  )
  const reasonOptions = useMemo(() => {
    const all = [...(reasons.data ?? []).map(({ code, label }) => ({ value: code, label })), ...SYSTEM_REASONS.map(({ code, label }) => ({ value: code, label }))]
    return [...new Map(all.map((option) => [option.value, option])).values()]
  }, [reasons.data])
  const branchOptions = branches.map((branch) => ({ value: branch.id, label: branch.name }))
  const filtering = Object.values(filters).some(Boolean)

  return (
    <>
      <title>Movimientos de inventario | Autopartco</title>
      <PageHeader title="Movimientos" description="Quién cambió las existencias, cuándo y por qué. Del más reciente al más antiguo." />
      <InventoryTabs />
      <div className="flex flex-col gap-5">
        <form role="search" aria-label="Filtrar movimientos" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          <SelectField id="mov-sucursal" label="Sucursal" placeholder="Todas" options={branchOptions} value={filters.sucursal} onChange={(event) => setParam('sucursal', event.target.value)} />
          <SelectField id="mov-producto" label="Producto" placeholder="Todos" options={productOptions} value={filters.producto} onChange={(event) => setParam('producto', event.target.value)} />
          <SelectField id="mov-motivo" label="Motivo" placeholder="Todos" options={reasonOptions} value={filters.motivo} onChange={(event) => setParam('motivo', event.target.value)} />
          <TextField id="mov-desde" type="date" label="Desde" value={filters.desde} max={filters.hasta || undefined} onChange={(event) => setParam('desde', event.target.value)} />
          <TextField
            id="mov-hasta"
            type="date"
            label="Hasta"
            value={filters.hasta}
            min={filters.desde || undefined}
            error={badRange ? 'La fecha final debe ser igual o posterior a la inicial.' : undefined}
            onChange={(event) => setParam('hasta', event.target.value)}
          />
          {filtering ? (
            <div className="flex items-end">
              <Button type="button" variant="outline" className="h-11 px-4" onClick={() => setParams({}, { replace: true })}>
                Quitar filtros
              </Button>
            </div>
          ) : null}
        </form>
        {badRange ? null : <MovementsList filters={filters} page={page} onPageChange={(next) => setParam('pagina', next > 1 ? String(next) : '')} />}
      </div>
    </>
  )
}
