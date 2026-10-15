import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router'
import { ClipboardListIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { formatCOP } from '@/lib/format'
import { getOrderService } from '../api/order-service'
import type { OrderStatus, OrderSummary } from '../api/types'
import { SelectField } from '../components/form-controls'
import { ResultCount, SearchField } from '../components/search-field'
import { normalizeText } from '../lib/fuzzy'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { StatusBadge } from '../components/status-badge'
import { usePortal } from '../context'
import { useStoreQuery } from '../hooks'
import { formatDateTime, ORDER_STATUS_LABEL, ORDER_STATUS_TONE, paymentStatus, SHIPMENT_STATUS_LABEL } from '../lib/labels'

const STATUS_OPTIONS = (Object.keys(ORDER_STATUS_LABEL) as OrderStatus[])
  .filter((status) => status !== 'REJECTED')
  .map((status) => ({ value: status, label: ORDER_STATUS_LABEL[status] }))

function OrdersView({ rows }: { rows: OrderSummary[] }) {
  const { storeId } = usePortal()
  const base = `/portal/${encodeURIComponent(storeId)}`
  const [params, setParams] = useSearchParams()
  const estado = params.get('estado') ?? ''
  const q = params.get('q') ?? ''

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

  const filtered = useMemo(() => {
    const term = normalizeText(q).replace(/^#/, '')
    return rows.filter((row) => {
      if (estado && row.status !== estado) return false
      return !term || normalizeText(`${row.orderNumber} ${row.customerName ?? ''}`).includes(term)
    })
  }, [rows, estado, q])
  const pending = rows.filter((row) => row.status === 'PENDING').length

  const columns: DataColumn<OrderSummary>[] = [
    {
      id: 'numero',
      header: 'Pedido',
      rowHeader: true,
      sortValue: (row) => row.orderNumber,
      cell: (row) => (
        <Link to={`${base}/pedidos/${encodeURIComponent(row.saleId)}`} className="font-semibold tabular-nums underline-offset-4 hover:underline">
          #{row.orderNumber}
        </Link>
      ),
    },
    { id: 'fecha', header: 'Fecha', sortValue: (row) => row.createdAt, cell: (row) => formatDateTime(row.createdAt) },
    { id: 'cliente', header: 'Cliente', sortValue: (row) => row.customerName ?? '', cell: (row) => row.customerName ?? 'Cliente sin nombre' },
    { id: 'items', header: 'Unidades', align: 'end', sortValue: (row) => row.itemCount, cell: (row) => row.itemCount },
    { id: 'total', header: 'Total', align: 'end', sortValue: (row) => row.total, cell: (row) => <span className="font-semibold tabular-nums">{formatCOP(row.total)}</span> },
    {
      id: 'estado',
      header: 'Estado',
      sortValue: (row) => ORDER_STATUS_LABEL[row.status],
      cell: (row) => <StatusBadge tone={ORDER_STATUS_TONE[row.status]}>{ORDER_STATUS_LABEL[row.status]}</StatusBadge>,
    },
    {
      id: 'pago',
      header: 'Pago',
      cell: (row) => {
        const payment = paymentStatus(row.paymentStatus)
        return <StatusBadge tone={payment.tone}>{payment.label}</StatusBadge>
      },
    },
    {
      id: 'envio',
      header: 'Envío',
      cell: (row) =>
        row.shipmentStatus && row.shipmentStatus in SHIPMENT_STATUS_LABEL
          ? SHIPMENT_STATUS_LABEL[row.shipmentStatus as keyof typeof SHIPMENT_STATUS_LABEL]
          : '—',
    },
  ]

  return (
    <div className="flex flex-col gap-5">
      {pending > 0 ? (
        <p role="status" className="rounded-lg border border-stock-low/30 bg-stock-low/10 p-3 text-sm">
          <strong className="font-semibold">{pending === 1 ? 'Tienes 1 pedido pendiente.' : `Tienes ${pending} pedidos pendientes.`}</strong> Confírmalos o cancélalos para que el cliente sepa qué pasa.
        </p>
      ) : null}
      <form role="search" aria-label="Filtrar pedidos" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-[minmax(0,2fr)_minmax(0,1fr)]">
        <SearchField value={q} onChange={(value) => setFilter('q', value)} placeholder="Número de pedido o cliente" />
        <SelectField
          id="filtro-estado-pedido"
          label="Estado del pedido"
          placeholder="Todos"
          options={STATUS_OPTIONS}
          value={estado}
          onChange={(event) => setFilter('estado', event.target.value)}
        />
      </form>
      <ResultCount shown={filtered.length} total={rows.length} singular="pedido" plural="pedidos" />
      {filtered.length === 0 ? (
        <EmptyState icon={<SearchXIcon />} title="Ningún pedido coincide" description="Revisa el número o el nombre, o prueba con otro estado." className="py-8">
          <Button variant="outline" className="h-11 px-4" onClick={() => setParams({}, { replace: true })}>
            Quitar filtros
          </Button>
        </EmptyState>
      ) : (
        <DataTable
          caption="Pedidos recibidos por el almacén"
          columns={columns}
          rows={filtered}
          getRowId={(row) => row.saleId}
          defaultSort={{ columnId: 'fecha', direction: 'desc' }}
          pageSize={10}
        />
      )}
    </div>
  )
}

/** `/portal/:storeId/pedidos`: pedidos recibidos con filtro por estado. */
export default function PortalOrdersPage() {
  const orders = useStoreQuery('orders', async (id) => (await getOrderService()).list(id))
  return (
    <>
      <title>Pedidos | Autopartco</title>
      <PageHeader title="Pedidos" description="Las compras que te hicieron en el marketplace, de la más reciente a la más antigua." />
      <QueryBoundary result={orders} what="tus pedidos">
        {(rows) =>
          rows.length === 0 ? (
            <EmptyState
              icon={<ClipboardListIcon />}
              title="Todavía no has recibido pedidos"
              description="Cuando alguien compre uno de tus productos publicados, el pedido aparecerá aquí para que lo confirmes y lo envíes."
            />
          ) : (
            <OrdersView rows={rows} />
          )
        }
      </QueryBoundary>
    </>
  )
}
