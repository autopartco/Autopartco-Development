import { ReceiptTextIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { formatCOP } from '@/lib/format'
import type { SubscriptionPayment } from '../api/types'
import { formatDate, formatDateTime, paymentStatus } from '../lib/labels'
import { StatusBadge } from './status-badge'

const COLUMNS: DataColumn<SubscriptionPayment>[] = [
  {
    id: 'fecha',
    header: 'Fecha',
    rowHeader: true,
    sortValue: (row) => row.createdAt,
    cell: (row) => formatDateTime(row.paidAt ?? row.createdAt),
  },
  { id: 'plan', header: 'Plan', sortValue: (row) => row.planName, cell: (row) => row.planName },
  { id: 'meses', header: 'Meses', align: 'end', sortValue: (row) => row.months, cell: (row) => row.months },
  {
    id: 'monto',
    header: 'Monto',
    align: 'end',
    sortValue: (row) => row.amountCop,
    cell: (row) => <span className="tabular-nums">{formatCOP(row.amountCop)}</span>,
  },
  {
    id: 'periodo',
    header: 'Periodo cubierto',
    cell: (row) => (row.periodStartsAt && row.periodEndsAt ? `${formatDate(row.periodStartsAt)} – ${formatDate(row.periodEndsAt)}` : '—'),
  },
  {
    id: 'estado',
    header: 'Estado',
    cell: (row) => {
      const status = paymentStatus(row.status)
      return (
        <span className="flex flex-wrap items-center gap-1.5">
          <StatusBadge tone={status.tone}>{status.label}</StatusBadge>
          {row.provider === 'SIMULATED' ? <span className="text-xs text-muted-foreground">Prueba</span> : null}
        </span>
      )
    },
  },
  { id: 'referencia', header: 'Referencia', cell: (row) => <span className="font-mono text-xs">{row.reference}</span> },
]

/** Historial de pagos de la suscripción (`GET …/subscription/payments`). */
export function PaymentsTable({ payments }: { payments: SubscriptionPayment[] }) {
  if (payments.length === 0) {
    return (
      <EmptyState
        icon={<ReceiptTextIcon />}
        title="Todavía no tienes pagos"
        description="Cuando pagues un plan, cada pago aparecerá aquí con su periodo y su referencia."
        className="py-8"
      />
    )
  }
  return (
    <DataTable
      caption="Historial de pagos de la suscripción"
      columns={COLUMNS}
      rows={payments}
      getRowId={(row) => row.id}
      defaultSort={{ columnId: 'fecha', direction: 'desc' }}
    />
  )
}
