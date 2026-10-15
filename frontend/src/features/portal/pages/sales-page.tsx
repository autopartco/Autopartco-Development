import { useSearchParams } from 'react-router'
import { LineChartIcon } from 'lucide-react'
import { EmptyState } from '@/components/common/states'
import { Skeleton } from '@/components/ui/skeleton'
import { formatCOP, formatNumber } from '@/lib/format'
import { cn } from '@/lib/utils'
import { getSalesService } from '../api/sales-service'
import type { SalesAnalytics, SalesEvent, SalesRange } from '../api/types'
import { AnimatedNumber, BentoTile, Delta } from '../components/bento'
import { LiveActivity } from '../components/live-activity'
import { OrderFunnel } from '../components/order-funnel'
import { OrderHeatmap } from '../components/order-heatmap'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { Sparkline } from '../components/sparkline'
import { useStoreQuery } from '../hooks'

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
      <div className="inline-flex rounded-lg bg-muted p-1">
        {RANGES.map((range) => (
          <label
            key={range}
            className={cn(
              'relative flex h-10 min-w-16 cursor-pointer items-center justify-center rounded-md px-3 text-sm font-semibold transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-offset-2 has-[:focus-visible]:outline-ring',
              value === range ? 'bg-card text-foreground shadow-sm' : 'text-muted-foreground hover:text-foreground',
            )}
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
    <div role="status">
      <span className="sr-only">Cargando las ventas…</span>
      <div aria-hidden className="grid gap-4 lg:grid-cols-12">
        <Skeleton className="h-52 lg:col-span-5" />
        <Skeleton className="h-52 lg:col-span-4" />
        <Skeleton className="h-52 lg:col-span-3" />
        <Skeleton className="h-72 lg:col-span-8" />
        <Skeleton className="h-72 lg:col-span-4" />
      </div>
    </div>
  )
}

function SalesView({ data, activity }: { data: SalesAnalytics; activity: SalesEvent[] | undefined }) {
  const revenuePoints = data.daily.map((point) => ({ label: dayLabel(point.date), value: point.revenue }))
  const ticketPoints = data.daily.map((point) => ({ label: dayLabel(point.date), value: point.averageTicket }))

  if (data.totals.orders === 0) {
    return (
      <EmptyState
        icon={<LineChartIcon />}
        title="Sin ventas en este rango"
        description="Cuando entren pedidos verás aquí ingresos, ticket promedio y las horas de más movimiento. Prueba con un rango más largo."
      />
    )
  }

  return (
    <div className="grid gap-4 lg:grid-cols-12">
      <BentoTile index={0} title="Ingresos" aside={`Últimos ${data.range} días`} className="lg:col-span-5">
        <p className="text-3xl leading-none font-extrabold tracking-[-0.035em]">
          <AnimatedNumber value={data.totals.revenue} format={formatCOP} />
        </p>
        <Delta current={data.totals.revenue} previous={data.previous.revenue} />
        <Sparkline points={revenuePoints} formatValue={formatCOP} label="Ventas diarias" />
      </BentoTile>

      <BentoTile index={1} title="Ticket promedio" aside="Por pedido" className="lg:col-span-4">
        <p className="text-3xl leading-none font-extrabold tracking-[-0.035em]">
          <AnimatedNumber value={data.totals.averageTicket} format={formatCOP} />
        </p>
        <Delta current={data.totals.averageTicket} previous={data.previous.averageTicket} />
        <Sparkline points={ticketPoints} formatValue={formatCOP} label="Ticket promedio diario" />
      </BentoTile>

      <BentoTile index={2} title="Pedidos" aside={`${formatNumber(data.totals.delivered)} entregados`} className="lg:col-span-3">
        <p className="text-3xl leading-none font-extrabold tracking-[-0.035em]">
          <AnimatedNumber value={data.totals.orders} format={formatNumber} />
        </p>
        <Delta current={data.totals.orders} previous={data.previous.orders} />
      </BentoTile>

      <BentoTile index={3} title="Pedidos por día y hora" aside="Toca una franja para ver sus pedidos" className="lg:col-span-8">
        <OrderHeatmap cells={data.heatmap} />
      </BentoTile>

      <BentoTile index={4} title="Actividad en vivo" className="lg:col-span-4 lg:row-span-2">
        {activity ? (
          <LiveActivity initial={activity} />
        ) : (
          <div role="status">
            <span className="sr-only">Cargando la actividad…</span>
            <Skeleton aria-hidden className="h-64 w-full" />
          </div>
        )}
      </BentoTile>

      <BentoTile index={5} title="Embudo de pedidos" aside="Del pedido a la entrega" className="lg:col-span-8">
        <OrderFunnel funnel={data.funnel} cancelled={data.cancelled} />
      </BentoTile>
    </div>
  )
}

/** `/portal/:storeId/ventas`: mosaicos de métricas, mapa de calor, embudo y actividad en vivo. */
export default function PortalSalesPage() {
  const [params, setParams] = useSearchParams()
  const days = parseRange(params.get('dias'))
  const sales = useStoreQuery(`sales:${days}`, async (id) => (await getSalesService()).sales(id, days))
  const activity = useStoreQuery('activity', async (id) => (await getSalesService()).activity(id))

  return (
    <>
      <title>Ventas | Autopartco</title>
      <PageHeader
        title="Ventas"
        description="Cómo van tus pedidos: ingresos, horas de más movimiento y en qué paso se quedan."
        actions={
          <RangeSelector
            value={days}
            onChange={(range) =>
              setParams(
                (current) => {
                  const next = new URLSearchParams(current)
                  next.set('dias', String(range))
                  return next
                },
                { replace: true },
              )
            }
          />
        }
      />
      <QueryBoundary result={sales} what="las ventas" keepPrevious skeleton={<SalesSkeleton />}>
        {(data) => <SalesView data={data} activity={activity.data} />}
      </QueryBoundary>
    </>
  )
}
