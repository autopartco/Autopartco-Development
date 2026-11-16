import { useState } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { ActivityIcon, SearchXIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { formatCOP, formatNumber } from '@/lib/format'
import { invalidateAsync, useAsync } from '@/lib/use-async'
import { SelectField } from '@/features/portal/components/form-controls'
import { ListSkeleton } from '@/features/portal/components/list-skeleton'
import { ServerPager } from '@/features/portal/components/server-pager'
import { actionErrorMessage } from '@/features/portal/lib/errors'
import { formatDateTime } from '@/features/portal/lib/labels'
import { formatFraction } from '@/features/portal/lib/promotions'
import { getAdminConsoleService } from '../api/admin-console-service'
import type { RiskAssessment, RiskOutcome } from '../api/console-types'
import { AdminBoundary, AdminPage, AdminSection, FigureList } from '../components/admin-kit'
import { KeyValues, ListMeta, Pill, QuerySearch } from '../components/console-kit'
import { useUrlFilters } from '../lib/use-url-filters'
import { ADMIN_CACHE_PREFIX } from '../hooks'
import { useAdminAccess } from '../lib/access'
import { DECISION, OUTCOME, PROVIDER_LABEL, STAGE_LABEL, paymentStatusLabel } from '../lib/console-labels'

const PAGE_SIZE = 25
const KEY = `${ADMIN_CACHE_PREFIX}payment-risk`

const STATUS_OPTIONS = [
  { value: 'PENDING', label: 'Por revisar' },
  { value: 'REVIEWED', label: 'Revisados' },
]
const DECISION_OPTIONS = Object.entries(DECISION).map(([value, item]) => ({ value, label: item.label }))

/** Puntuación 0..1 con barra proporcional y el umbral del modelo marcado (si lo hay). */
function ScoreBar({ score, threshold }: { score: number | null; threshold: number | null }) {
  if (score === null) return <span className="text-ap-muted">—</span>
  const over = threshold !== null && score > threshold
  return (
    <span className="inline-flex min-w-36 items-center gap-2">
      <span className="relative h-1.5 w-20 overflow-hidden rounded-full bg-ap-line" aria-hidden>
        <span className={over ? 'absolute inset-y-0 left-0 bg-ap-danger' : 'absolute inset-y-0 left-0 bg-ap-graphite'} style={{ width: `${Math.min(100, score * 100)}%` }} />
        {threshold !== null ? <span className="absolute inset-y-0 w-px bg-ap-ink" style={{ left: `${Math.min(100, threshold * 100)}%` }} /> : null}
      </span>
      <span className={over ? 'ap-num font-semibold text-ap-danger' : 'ap-num text-ap-ink'}>{formatNumber(Math.round(score * 1000) / 1000)}</span>
    </span>
  )
}

function ReviewSheet({ item, onClose, canReview }: { item?: RiskAssessment; onClose: () => void; canReview: boolean }) {
  const [busy, setBusy] = useState<RiskOutcome>()
  async function mark(outcome: RiskOutcome) {
    if (!item) return
    setBusy(outcome)
    try {
      await getAdminConsoleService().markRiskOutcome(item.id, outcome)
      toast.success(`Marcaste el pago del pedido ${item.orderNumber ?? ''} como ${OUTCOME[outcome].label.toLowerCase()}.`)
      invalidateAsync(KEY)
      onClose()
    } catch (error) {
      toast.error(actionErrorMessage(error, 'No pudimos guardar la revisión. Inténtalo de nuevo.'))
    } finally {
      setBusy(undefined)
    }
  }
  return (
    <Sheet open={item !== undefined} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" className="w-full gap-0 p-0 sm:max-w-lg">
        {item ? (
          <>
            <SheetHeader className="border-b border-ap-line p-4 pr-16">
              <p className="ap-index">Evaluación de riesgo · #{item.id}</p>
              <SheetTitle className="text-block-title text-ap-ink">Pago de {formatCOP(item.amount)}</SheetTitle>
              <SheetDescription>
                {item.customerEmail ?? 'Cliente sin cuenta'} · pedido {item.orderNumber ?? '—'}
              </SheetDescription>
              <div className="mt-1 flex flex-wrap gap-2">
                <Pill value={DECISION[item.decision] ?? { label: item.decision, tone: 'neutral' }} />
                {item.outcome ? <Pill value={OUTCOME[item.outcome] ?? { label: item.outcome, tone: 'neutral' }} /> : null}
              </div>
            </SheetHeader>
            <div className="flex flex-col gap-5 overflow-y-auto p-4">
              <div className="flex flex-col gap-1">
                <p className="text-sm text-ap-muted">Puntuación de anomalía</p>
                <ScoreBar score={item.riskScore} threshold={item.threshold} />
                <p className="text-xs text-ap-muted">
                  {item.threshold !== null
                    ? `Umbral del modelo: ${formatNumber(Math.round(item.threshold * 1000) / 1000)}. Por encima se marca para revisión. Una anomalía no prueba fraude: confírmalo con el pedido y el cliente.`
                    : 'Evaluación sin umbral de modelo registrado.'}
                </p>
              </div>
              <KeyValues
                items={[
                  ['Evaluado', formatDateTime(item.assessedAt)],
                  ['Etapa', STAGE_LABEL[item.stage] ?? item.stage],
                  ['Modelo', item.model ? `${item.model} · ${item.modelVersion ?? ''}` : 'Reglas o proveedor'],
                  ['Proveedor', PROVIDER_LABEL[item.provider] ?? item.provider],
                  ['Estado del pago', paymentStatusLabel(item.paymentStatus).label],
                  ['Intento', item.attemptNumber ?? 1],
                  ['ID del pago', item.paymentId, true],
                  ['Revisado por', item.reviewedBy ? `${item.reviewedBy} · ${formatDateTime(item.outcomeAt)}` : 'Sin revisar'],
                ]}
              />
              <Button variant="outline" className="h-11 self-start px-4" nativeButton={false} render={<Link to={`/admin/pedidos?pedido=${encodeURIComponent(item.orderId)}`} />}>
                Ver el pedido
              </Button>
            </div>
            {canReview ? (
              <div className="mt-auto flex flex-col gap-2 border-t border-ap-line p-4 sm:flex-row sm:justify-end">
                <Button variant="outline" className="h-11 px-4" loading={busy === 'UNKNOWN'} disabled={busy !== undefined} onClick={() => void mark('UNKNOWN')}>
                  Sin determinar
                </Button>
                <Button variant="outline" className="h-11 px-4" loading={busy === 'LEGIT'} disabled={busy !== undefined} onClick={() => void mark('LEGIT')}>
                  Es legítimo
                </Button>
                <Button variant="destructive" className="h-11 px-4" loading={busy === 'FRAUD_CONFIRMED'} disabled={busy !== undefined} onClick={() => void mark('FRAUD_CONFIRMED')}>
                  Confirmar fraude
                </Button>
              </div>
            ) : null}
          </>
        ) : null}
      </SheetContent>
    </Sheet>
  )
}

/**
 * `/admin/pagos-atipicos`: cola de pagos que el modelo Isolation Forest (u otra evaluación) marcó para revisión.
 * Pregunta: «¿qué pagos debo revisar primero?» → orden por pendientes y puntuación; la revisión guarda el resultado.
 */
export default function AdminPaymentRiskPage() {
  const { get, page, update } = useUrlFilters()
  const status = get('estado') || 'PENDING'
  const decision = get('decision')
  const q = get('q')
  const [selected, setSelected] = useState<RiskAssessment>()
  const canReview = useAdminAccess().can('PLATFORM_PAYMENT_RISK_REVIEW')

  const summary = useAsync(`${KEY}:summary`, () => getAdminConsoleService().paymentRiskSummary())
  const list = useAsync(`${KEY}:list:${status}:${decision}:${q}:${page}`, () =>
    getAdminConsoleService().paymentRisk({ status: status === 'ALL' ? '' : (status as 'PENDING' | 'REVIEWED'), decision, q, page, size: PAGE_SIZE }),
  )
  const activeFilters = [status !== 'PENDING' ? status : '', decision, q].filter(Boolean).length

  const columns: DataColumn<RiskAssessment>[] = [
    { id: 'fecha', header: 'Evaluado', cell: (row) => <span className="ap-ref whitespace-nowrap text-ap-ink">{formatDateTime(row.assessedAt)}</span> },
    {
      id: 'pago',
      header: 'Pago',
      rowHeader: true,
      cell: (row) => (
        <div className="min-w-0">
          <p className="ap-price text-ap-ink">{formatCOP(row.amount)}</p>
          <p className="ap-ref text-ap-muted">Pedido {row.orderNumber ?? '—'}</p>
        </div>
      ),
    },
    { id: 'cliente', header: 'Cliente', className: 'hidden md:table-cell', cell: (row) => <span className="break-all text-ap-ink">{row.customerEmail ?? '—'}</span> },
    { id: 'puntuacion', header: 'Puntuación', cell: (row) => <ScoreBar score={row.riskScore} threshold={row.threshold} /> },
    {
      id: 'estado',
      header: 'Decisión',
      cell: (row) => (
        <div className="flex flex-col items-start gap-1">
          <Pill value={DECISION[row.decision] ?? { label: row.decision, tone: 'neutral' }} />
          {row.outcome ? <span className="text-xs text-ap-muted">{OUTCOME[row.outcome]?.label ?? row.outcome}</span> : null}
        </div>
      ),
    },
    {
      id: 'ver',
      header: 'Revisar',
      actions: true,
      align: 'end',
      cell: (row) => (
        <Button variant="outline" className="h-11 px-3" aria-label={`Revisar el pago del pedido ${row.orderNumber ?? ''}`} onClick={() => setSelected(row)}>
          {row.decision === 'REVIEW' && !row.outcome ? 'Revisar' : 'Ver'}
        </Button>
      ),
    },
  ]

  return (
    <AdminPage title="Pagos atípicos" description="Pagos que el modelo de detección de anomalías (Isolation Forest) marcó para revisión, con su resultado.">
      <div className="flex flex-col gap-8">
        <AdminSection id="riesgo-resumen" title="Últimos 30 días" meta="Evaluaciones de riesgo de pagos">
          <AdminBoundary result={summary} what="el resumen de riesgo" skeleton={<ListSkeleton rows={1} label="Cargando el resumen" />}>
            {(data) => (
              <FigureList
                items={[
                  { label: 'Pagos evaluados', value: formatNumber(data.total) },
                  { label: 'Para revisión', value: formatNumber(data.review), hint: data.total > 0 ? `${formatFraction(data.review / data.total)} del total` : undefined },
                  { label: 'Por revisar', value: formatNumber(data.pendingReview), attention: data.pendingReview > 0, hint: data.pendingReview > 0 ? 'Esperan una decisión' : 'Al día' },
                  { label: 'Fraude confirmado', value: formatNumber(data.fraudConfirmed) },
                  { label: 'Monto en revisión', value: formatCOP(data.reviewAmount) },
                ]}
              />
            )}
          </AdminBoundary>
        </AdminSection>

        <section aria-label="Evaluaciones">
          <form role="search" aria-label="Filtrar evaluaciones" onSubmit={(event) => event.preventDefault()} className="grid gap-3 sm:grid-cols-3">
            <SelectField
              id="riesgo-estado"
              label="Revisión"
              options={[...STATUS_OPTIONS, { value: 'ALL', label: 'Todas' }]}
              value={status}
              onChange={(event) => update({ estado: event.target.value === 'PENDING' ? '' : event.target.value })}
            />
            <SelectField id="riesgo-decision" label="Decisión" placeholder="Todas" options={DECISION_OPTIONS} value={decision} onChange={(event) => update({ decision: event.target.value })} />
            <QuerySearch label="Cliente o pedido" placeholder="correo o número de pedido" value={q} onApply={(value) => update({ q: value })} />
          </form>
          <ListMeta total={list.data?.total} singular="evaluación" plural="evaluaciones" activeFilters={activeFilters} onClear={() => update({ estado: '', decision: '', q: '' })} />
          <AdminBoundary result={list} what="las evaluaciones de riesgo" keepPrevious skeleton={<ListSkeleton rows={8} label="Cargando las evaluaciones" />}>
            {(data) =>
              data.total === 0 ? (
                status === 'PENDING' && activeFilters === 0 ? (
                  <EmptyState icon={<ActivityIcon />} title="No hay pagos por revisar" description="Cuando el modelo marque un pago como atípico aparecerá aquí. Los pagos se evalúan cada 15 minutos." />
                ) : (
                  <EmptyState icon={<SearchXIcon />} title="Ninguna evaluación coincide" description="Prueba con otra decisión o quita la búsqueda." />
                )
              ) : (
                <div className="flex flex-col gap-4">
                  <DataTable caption="Evaluaciones de riesgo de pagos" columns={columns} rows={data.items} getRowId={(row) => String(row.id)} onRowClick={setSelected} />
                  <ServerPager page={data.page} totalPages={Math.max(1, data.totalPages)} total={data.total} singular="evaluación" plural="evaluaciones" busy={list.status === 'loading'} onPageChange={(next) => update({ pagina: String(next) }, false)} />
                </div>
              )
            }
          </AdminBoundary>
        </section>
      </div>
      <ReviewSheet item={selected} onClose={() => setSelected(undefined)} canReview={canReview} />
    </AdminPage>
  )
}
