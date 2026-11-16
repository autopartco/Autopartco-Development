import { Link } from 'react-router'
import { SearchXIcon, WalletIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { formatCOP } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { SelectField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ServerPager } from '@/features/portal/components/server-pager'
import { formatDateTime, humanizeCode } from '@/features/portal/lib/labels'
import { getAdminConsoleService } from '../api/admin-console-service'
import type { AdminPaymentRow } from '../api/console-types'
import { AdminBoundary, AdminPage } from '../components/admin-kit'
import { ListMeta, Pill, QuerySearch } from '../components/console-kit'
import { useUrlFilters } from '../lib/use-url-filters'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { DECISION, PROVIDER_LABEL, paymentStatusLabel } from '../lib/console-labels'

const PAGE_SIZE = 25
const STATUS_OPTIONS = ['APPROVED', 'PENDING', 'DECLINED', 'VOIDED', 'ERROR'].map((value) => ({ value, label: paymentStatusLabel(value).label }))
const PROVIDER_OPTIONS = Object.entries(PROVIDER_LABEL).map(([value, label]) => ({ value, label }))

/** `/admin/pagos`: todos los cobros con su estado, proveedor, rechazo y evaluación de riesgo. */
export default function AdminPaymentsPage() {
  const { get, page, update } = useUrlFilters()
  const q = get('q')
  const status = get('estado')
  const provider = get('proveedor')
  const list = useAsync(`${ADMIN_CACHE_PREFIX}payments:${q}:${status}:${provider}:${page}`, () =>
    getAdminConsoleService().payments({ q, status, provider, page, size: PAGE_SIZE }),
  )
  const activeFilters = [q, status, provider].filter(Boolean).length

  const columns: DataColumn<AdminPaymentRow>[] = [
    { id: 'fecha', header: 'Fecha', cell: (row) => <span className="ap-ref whitespace-nowrap text-ap-ink">{formatDateTime(row.createdAt)}</span> },
    {
      id: 'pago',
      header: 'Monto',
      rowHeader: true,
      cell: (row) => (
        <div>
          <p className="ap-price text-ap-ink">{formatCOP(row.amount)}</p>
          {row.refunded > 0 ? <p className="text-xs text-ap-muted">Reembolsado {formatCOP(row.refunded)}</p> : null}
        </div>
      ),
    },
    {
      id: 'pedido',
      header: 'Pedido',
      cell: (row) => (
        <Link to={`/admin/pedidos?pedido=${encodeURIComponent(row.orderId)}`} className="ap-ref font-semibold text-ap-ink underline-offset-4 hover:underline">
          {row.orderNumber}
        </Link>
      ),
    },
    { id: 'cliente', header: 'Cliente', className: 'hidden md:table-cell', cell: (row) => <span className="break-all text-ap-ink">{row.customerEmail ?? '—'}</span> },
    { id: 'proveedor', header: 'Proveedor', className: 'hidden lg:table-cell', cell: (row) => <span className="text-ap-ink">{PROVIDER_LABEL[row.provider] ?? row.provider} · intento {row.attempt}</span> },
    {
      id: 'estado',
      header: 'Estado',
      cell: (row) => (
        <div className="flex flex-col items-start gap-1">
          <Pill value={paymentStatusLabel(row.status)} />
          {row.failureCode ? <span className="text-xs text-ap-muted">{humanizeCode(row.failureCode)}</span> : null}
          {row.riskDecision === 'REVIEW' ? <Pill value={DECISION.REVIEW} /> : null}
        </div>
      ),
    },
  ]

  return (
    <AdminPage title="Pagos" description="Cobros de todos los pedidos: aprobados, rechazados, pendientes y reembolsos, con su evaluación de riesgo.">
      <form role="search" aria-label="Filtrar pagos" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        <QuerySearch label="Pedido o correo" placeholder="7001234 o correo" value={q} onApply={(value) => update({ q: value })} />
        <SelectField id="pagos-estado" label="Estado" placeholder="Todos" options={STATUS_OPTIONS} value={status} onChange={(event) => update({ estado: event.target.value })} />
        <SelectField id="pagos-proveedor" label="Proveedor" placeholder="Todos" options={PROVIDER_OPTIONS} value={provider} onChange={(event) => update({ proveedor: event.target.value })} />
      </form>
      <ListMeta total={list.data?.total} singular="pago" plural="pagos" activeFilters={activeFilters} onClear={() => update({ q: '', estado: '', proveedor: '' })} />
      <AdminBoundary result={list} what="los pagos" keepPrevious skeleton={<ListSkeleton rows={8} label="Cargando los pagos" />}>
        {(data) =>
          data.total === 0 ? (
            activeFilters > 0 ? (
              <EmptyState icon={<SearchXIcon />} title="Ningún pago coincide" description="Prueba con otro estado, proveedor o búsqueda." />
            ) : (
              <EmptyState icon={<WalletIcon />} title="Aún no hay pagos" description="Los cobros de los pedidos aparecerán aquí." />
            )
          ) : (
            <div className="flex flex-col gap-4">
              <DataTable caption="Pagos de la plataforma" columns={columns} rows={data.items} getRowId={(row) => row.id} />
              <ServerPager page={data.page} totalPages={Math.max(1, data.totalPages)} total={data.total} singular="pago" plural="pagos" busy={list.status === 'loading'} onPageChange={(next) => update({ pagina: String(next) }, false)} />
            </div>
          )
        }
      </AdminBoundary>
    </AdminPage>
  )
}
