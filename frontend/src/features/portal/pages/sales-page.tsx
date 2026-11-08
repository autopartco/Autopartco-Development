import { useSearchParams } from 'react-router'
import { LineChartIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber } from '@/lib/format'
import type { AsyncResult } from '@/lib/use-async'
import { getSalesService } from '../api/sales-service'
import type { SalesAnalytics, SalesEvent, SalesRange } from '../api/types'
import { BentoTile, Delta } from '../components/bento'
import { ChartSection, ChartSkeleton } from '../components/chart-section'
import { LiveActivity } from '../components/live-activity'
import { OrderFunnel } from '../components/order-funnel'
import { OrderHeatmap } from '../components/order-heatmap'
import { PageHeader } from '../components/page-header'
import { QueryBoundary, QueryError } from '../components/query-boundary'
import { SEGMENT_GROUP_CLASS, segmentClass } from '../components/segmented'
import { Sparkline } from '../components/sparkline'
import { useStoreQuery } from '../hooks'
import { formatDateTime } from '../lib/labels'

const RANGES: SalesRange[] = [7, 30, 90]

const dayFormatter = new Intl.DateTimeFormat('es-CO', { weekday: 'short', day: 'numeric', month: 'short' })

function dayLabel(key: string): string {
  const [year = 0, month = 1, day = 1] = key.split('-').map(Number)
  return dayFormatter.format(new Date(year, month - 1, day))
}

function parseRange(value: string | null): SalesRange {
  const days = Number(value)
  return days === 7 || days === 90 ? days : 30
}

function RangeSelector({ value, onChange }: { value: SalesRange; onChange: (range: SalesRange) => void }) {
  return (
    <fieldset className="flex flex-col gap-1.5">
      <legend className="sr-only">Rango de días</legend>
      <div className={SEGMENT_GROUP_CLASS}>
        {RANGES.map((range) => (
          <label
            key={range}
            className={segmentClass(value === range, 'relative min-w-16 px-3 tabular-nums has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ap-red')}
          >
            <input type="radio" name="rango" value={range} checked={value === range} onChange={() => onChange(range)} className="sr-only" />
            {range} días
          </label>
        ))}
      </div>
    </fieldset>
  )
}

function SalesSkeleton() {
  return (
    <div className="grid gap-x-8 gap-y-8 lg:grid-cols-12">
      <div className="flex flex-col gap-3 lg:col-span-8">
        <Skeleton aria-hidden className="h-10 w-48" />
        <ChartSkeleton kind="line" label="Cargando las ventas" className="border-t-0 pt-0" />
      </div>
      <ChartSkeleton kind="list" label="Cargando la actividad" className="lg:col-span-4 lg:row-span-2" />
      <ChartSkeleton kind="matrix" label="Cargando los pedidos por hora" className="lg:col-span-8" />
    </div>
  )
}

/** Valores exactos de la serie diaria: alternativa accesible a los minigráficos (§20.11). */
function DailyTable({ data }: { data: SalesAnalytics }) {
  return (
    <details className="text-sm">
      <summary className="inline-flex min-h-11 cursor-pointer items-center font-semibold text-ap-ink underline underline-offset-4">Ver las ventas de cada día en una tabla</summary>
      <div className="max-h-72 overflow-auto rounded-md border border-ap-line" tabIndex={0} role="region" aria-label="Ventas por día">
        <table className="w-full text-left text-sm">
          <caption className="sr-only">Ventas por día de los últimos {data.range} días</caption>
          <thead className="sticky top-0 bg-ap-paper">
            <tr>
              <th scope="col" className="px-3 py-2 font-semibold text-ap-ink">
                Día
              </th>
              <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">
                Ingresos
              </th>
              <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">
                Pedidos
              </th>
              <th scope="col" className="px-3 py-2 text-right font-semibold text-ap-ink">
                Ticket promedio
              </th>
            </tr>
          </thead>
          <tbody>
            {data.daily.map((point) => (
              <tr key={point.date} className="border-t border-ap-line">
                <th scope="row" className="ap-ref px-3 py-1.5 font-medium text-ap-ink">
                  {dayLabel(point.date)}
                </th>
                <td className="ap-num px-3 py-1.5">{formatCOP(point.revenue)}</td>
                <td className="ap-num px-3 py-1.5">{formatNumber(point.orders)}</td>
                <td className="ap-num px-3 py-1.5">{formatCOP(point.averageTicket)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </details>
  )
}

function ActivityPanel({ activity }: { activity: AsyncResult<SalesEvent[]> }) {
  if (activity.status === 'error' && activity.data === undefined) {
    return <QueryError error={activity.error} what="la actividad reciente" onRetry={activity.retry} />
  }
  if (activity.data === undefined) {
    return (
      <div role="status" className="flex flex-col gap-2">
        <span className="sr-only">Cargando la actividad…</span>
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} aria-hidden className="h-11 w-full" />
        ))}
      </div>
    )
  }
  return <LiveActivity initial={activity.data} />
}

/**
 * Panel de ventas (§20.6). Pregunta principal: «¿cómo van mis ventas en este rango?» → ingresos con su
 * tendencia diaria y la comparación con el periodo anterior. Le siguen «¿a qué horas entran los pedidos?»
 * (mapa de calor) y «¿en qué paso se quedan?» (embudo); la actividad reciente acompaña a un lado.
 */
function SalesView({ data, activity, onWiden }: { data: SalesAnalytics; activity: AsyncResult<SalesEvent[]>; onWiden: () => void }) {
  const revenuePoints = data.daily.map((point) => ({ label: dayLabel(point.date), value: point.revenue }))
  const ticketPoints = data.daily.map((point) => ({ label: dayLabel(point.date), value: point.averageTicket }))
  const period = data.daily.length > 0 ? `${dayLabel(data.daily[0]?.date ?? '')} – ${dayLabel(data.daily[data.daily.length - 1]?.date ?? '')}` : `Últimos ${data.range} días`
  const source = `Fuente: pedidos de tu almacén en Autopartco. Calculado el ${formatDateTime(data.generatedAt)}. La comparación es con los ${data.range} días anteriores.`

  if (data.totals.orders === 0) {
    return (
      <EmptyState
        icon={<LineChartIcon />}
        title="Sin ventas en este rango"
        description="Cuando entren pedidos verás aquí ingresos, ticket promedio y las horas de más movimiento."
      >
        {data.range !== 90 ? (
          <Button variant="outline" className="h-11 px-4" onClick={onWiden}>
            Ver los últimos 90 días
          </Button>
        ) : null}
      </EmptyState>
    )
  }

  return (
    <div className="grid items-start gap-x-8 gap-y-8 lg:grid-cols-12">
      <ChartSection
        className="lg:col-span-8"
        title={`¿Cuánto vendiste en los últimos ${data.range} días?`}
        meta={`${period} · ingresos diarios en COP`}
        source={source}
        lead={
          <div className="flex flex-col gap-1">
            <p className="ap-price text-3xl leading-none text-ap-ink">{formatCOP(data.totals.revenue)}</p>
            <Delta current={data.totals.revenue} previous={data.previous.revenue} />
          </div>
        }
      >
        <Sparkline points={revenuePoints} formatValue={formatCOP} label="Ingresos diarios" className="mt-1" />
        <dl className="grid gap-x-8 gap-y-5 border-t border-ap-line pt-4 sm:grid-cols-2">
          <div className="flex flex-col gap-1 border-l-2 border-ap-line-strong pl-3">
            <dt className="text-sm font-medium text-ap-muted">Ticket promedio por pedido</dt>
            <dd className="flex flex-col gap-1">
              <span className="ap-price text-xl text-ap-ink">{formatCOP(data.totals.averageTicket)}</span>
              <Delta current={data.totals.averageTicket} previous={data.previous.averageTicket} />
              <Sparkline points={ticketPoints} formatValue={formatCOP} label="Ticket promedio diario" tone="secondary" className="mt-2" />
            </dd>
          </div>
          <div className="flex flex-col gap-1 border-l-2 border-ap-line-strong pl-3">
            <dt className="text-sm font-medium text-ap-muted">Pedidos</dt>
            <dd className="flex flex-col gap-1">
              <span className="ap-price text-xl text-ap-ink">{formatNumber(data.totals.orders)}</span>
              <Delta current={data.totals.orders} previous={data.previous.orders} />
              <span className="text-sm text-ap-muted">{formatNumber(data.totals.delivered)} ya entregados</span>
            </dd>
          </div>
        </dl>
        <DailyTable data={data} />
      </ChartSection>

      <BentoTile title="Actividad reciente" aside="Pedidos, pagos y stock" className="lg:col-span-4 lg:row-span-3">
        <ActivityPanel activity={activity} />
      </BentoTile>

      <ChartSection
        className="lg:col-span-8"
        title="¿A qué horas entran más pedidos?"
        meta={`${period} · pedidos por día de la semana y hora`}
        source="Toca una franja para ver sus pedidos."
      >
        <OrderHeatmap cells={data.heatmap} />
      </ChartSection>

      <ChartSection className="lg:col-span-8" title="¿En qué paso se quedan los pedidos?" meta={`${period} · pedidos por etapa, del pedido a la entrega`}>
        <OrderFunnel funnel={data.funnel} cancelled={data.cancelled} />
      </ChartSection>
    </div>
  )
}

/** `/portal/:storeId/ventas`: ingresos frente al periodo anterior, horas de más pedidos, embudo y actividad. */
export default function PortalSalesPage() {
  const [params, setParams] = useSearchParams()
  const days = parseRange(params.get('dias'))
  const sales = useStoreQuery(`sales:${days}`, async (id) => (await getSalesService()).sales(id, days))
  const activity = useStoreQuery('activity', async (id) => (await getSalesService()).activity(id))
  const setRange = (range: SalesRange) =>
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        next.set('dias', String(range))
        return next
      },
      { replace: true },
    )

  return (
    <>
      <title>Ventas | Autopartco</title>
      <PageHeader
        title="Ventas"
        description="Cómo van tus pedidos: ingresos, horas de más movimiento y en qué paso se quedan."
        actions={<RangeSelector value={days} onChange={setRange} />}
      />
      <QueryBoundary result={sales} what="las ventas" keepPrevious skeleton={<SalesSkeleton />}>
        {(data) => <SalesView data={data} activity={activity} onWiden={() => setRange(90)} />}
      </QueryBoundary>
    </>
  )
}
