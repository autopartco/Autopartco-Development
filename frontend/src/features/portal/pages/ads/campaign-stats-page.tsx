import { useState } from 'react'
import { useParams } from 'react-router'
import { BarChart3Icon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { TextField } from '@/components/common/form-fields'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/common/states'
import { formatCOP, formatNumber } from '@/lib/format'
import { useAsync } from '@/lib/use-async'
import { FigureList } from '@/features/admin/components/admin-kit'
import { adsApi, type AdStatsByPlacement, type AdStatsByProduct, type AdStatsDaily } from '../../api/ads-api'
import { AnalyticsChart, type ChartPoint } from '../../components/analytics-chart'
import { ChartSection } from '../../components/chart-section'
import { PageHeader } from '../../components/page-header'
import { QueryBoundary } from '../../components/query-boundary'
import { StatusBadge } from '../../components/status-badge'
import { usePortal } from '../../context'
import { campaignStatusView, formatDay, formatPercent, formatShortDay, placementLabel } from '../../lib/ads'

const num = (value: number) => <span className="ap-num">{formatNumber(value)}</span>
const money = (value: number) => <span className="ap-price">{formatCOP(value)}</span>

const DAILY_COLUMNS: DataColumn<AdStatsDaily>[] = [
  { id: 'day', header: 'Día', rowHeader: true, sortValue: (row) => row.day, cell: (row) => formatDay(row.day) },
  { id: 'impressions', header: 'Impresiones', align: 'end', sortValue: (row) => row.impressions, cell: (row) => num(row.impressions) },
  { id: 'reach', header: 'Alcance', align: 'end', sortValue: (row) => row.reach, cell: (row) => num(row.reach) },
  { id: 'clicks', header: 'Clics', align: 'end', sortValue: (row) => row.clicks, cell: (row) => num(row.clicks) },
  { id: 'whatsapp', header: 'WhatsApp', align: 'end', sortValue: (row) => row.whatsappClicks, cell: (row) => num(row.whatsappClicks) },
  { id: 'cart', header: 'Al carrito', align: 'end', sortValue: (row) => row.addToCart, cell: (row) => num(row.addToCart) },
  { id: 'orders', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => num(row.orders) },
  { id: 'revenue', header: 'Ingresos', align: 'end', sortValue: (row) => row.revenue, cell: (row) => money(row.revenue) },
]

const PLACEMENT_COLUMNS: DataColumn<AdStatsByPlacement>[] = [
  { id: 'placement', header: 'Ubicación', rowHeader: true, sortValue: (row) => placementLabel(row.placement), cell: (row) => placementLabel(row.placement) },
  { id: 'impressions', header: 'Impresiones', align: 'end', sortValue: (row) => row.impressions, cell: (row) => num(row.impressions) },
  { id: 'clicks', header: 'Clics', align: 'end', sortValue: (row) => row.clicks, cell: (row) => num(row.clicks) },
  { id: 'ctr', header: 'CTR', align: 'end', sortValue: (row) => row.ctr, cell: (row) => <span className="ap-num">{formatPercent(row.ctr)}</span> },
  { id: 'cart', header: 'Al carrito', align: 'end', sortValue: (row) => row.addToCart, cell: (row) => num(row.addToCart) },
  { id: 'orders', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => num(row.orders) },
  { id: 'revenue', header: 'Ingresos', align: 'end', sortValue: (row) => row.revenue, cell: (row) => money(row.revenue) },
]

const PRODUCT_COLUMNS: DataColumn<AdStatsByProduct>[] = [
  { id: 'product', header: 'Producto', rowHeader: true, sortValue: (row) => row.productName, cell: (row) => row.productName },
  { id: 'impressions', header: 'Impresiones', align: 'end', sortValue: (row) => row.impressions, cell: (row) => num(row.impressions) },
  { id: 'clicks', header: 'Clics', align: 'end', sortValue: (row) => row.clicks, cell: (row) => num(row.clicks) },
  { id: 'ctr', header: 'CTR', align: 'end', sortValue: (row) => row.ctr, cell: (row) => <span className="ap-num">{formatPercent(row.ctr)}</span> },
  { id: 'whatsapp', header: 'WhatsApp', align: 'end', sortValue: (row) => row.whatsappClicks, cell: (row) => num(row.whatsappClicks) },
  { id: 'cart', header: 'Al carrito', align: 'end', sortValue: (row) => row.addToCart, cell: (row) => num(row.addToCart) },
  { id: 'orders', header: 'Pedidos', align: 'end', sortValue: (row) => row.orders, cell: (row) => num(row.orders) },
  { id: 'revenue', header: 'Ingresos', align: 'end', sortValue: (row) => row.revenue, cell: (row) => money(row.revenue) },
]

/** `/portal/:storeId/publicidad/:campaignId/estadisticas`: resultados de una campaña. Las cifras son estimadas (§2). */
export default function PortalAdsCampaignStatsPage() {
  const { storeId } = usePortal()
  const { campaignId = '' } = useParams()
  const [range, setRange] = useState({ from: '', to: '' })
  const [draft, setDraft] = useState({ from: '', to: '' })
  const campaign = useAsync(`portal:${storeId}:ads:campaign:${campaignId}`, () => adsApi.get(storeId, campaignId))
  const stats = useAsync(`portal:${storeId}:ads:stats:${campaignId}:${range.from}:${range.to}`, () =>
    adsApi.stats(storeId, campaignId, range.from || undefined, range.to || undefined),
  )
  const rangeInvalid = draft.from !== '' && draft.to !== '' && draft.to < draft.from
  const status = campaign.data ? campaignStatusView(campaign.data) : undefined

  return (
    <>
      <title>Estadísticas de publicidad | Autopartco</title>
      <PageHeader
        label="Publicidad"
        title={campaign.data ? `Estadísticas · ${campaign.data.name}` : 'Estadísticas de la campaña'}
        description="Cuánto se vio tu anuncio, cuánto se tocó y qué vendió. Los pedidos se atribuyen a anuncios tocados en los 7 días previos a la compra."
        crumbs={[
          { label: 'Publicidad', to: `/portal/${encodeURIComponent(storeId)}/publicidad` },
          { label: 'Estadísticas' },
        ]}
        meta={status ? <StatusBadge tone={status.tone}>{status.label}</StatusBadge> : undefined}
      />

      <form
        noValidate
        className="mb-6 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault()
          if (!rangeInvalid) setRange(draft)
        }}
      >
        <TextField id="stats-from" label="Desde" type="date" value={draft.from} max={draft.to || undefined} onChange={(event) => setDraft((current) => ({ ...current, from: event.target.value }))} containerClassName="w-44" />
        <TextField id="stats-to" label="Hasta" type="date" value={draft.to} min={draft.from || undefined} error={rangeInvalid ? 'No puede ser anterior a «Desde».' : undefined} onChange={(event) => setDraft((current) => ({ ...current, to: event.target.value }))} containerClassName="w-44" />
        <Button type="submit" className="h-11 px-5" disabled={rangeInvalid}>
          Aplicar
        </Button>
      </form>

      <QueryBoundary result={stats} what="las estadísticas de la campaña" keepPrevious>
        {(data) => {
          const empty = data.impressions === 0 && data.clicks === 0
          const points: ChartPoint[] = data.daily.map((day) => ({
            label: formatShortDay(day.day),
            detail: `${formatDay(day.day)}: ${formatNumber(day.impressions)} impresiones, ${formatNumber(day.clicks)} clics`,
            bar: day.impressions,
          }))
          return (
            <div className="flex flex-col gap-10">
              <FigureList
                items={[
                  { label: 'Impresiones', value: formatNumber(data.impressions), hint: 'Veces que se mostró' },
                  { label: 'Alcance', value: formatNumber(data.reach), hint: 'Visitantes distintos por día' },
                  { label: 'Clics', value: formatNumber(data.clicks) },
                  { label: 'CTR', value: formatPercent(data.ctr), hint: 'Clics ÷ impresiones' },
                  { label: 'Clics a WhatsApp', value: formatNumber(data.whatsappClicks) },
                  { label: 'Al carrito', value: formatNumber(data.addToCart) },
                  { label: 'Pedidos', value: formatNumber(data.orders) },
                  { label: 'Ingresos', value: formatCOP(data.revenue) },
                  { label: 'Costo', value: formatCOP(data.cost), hint: 'Proporcional al periodo' },
                  { label: 'Costo por clic', value: data.clicks > 0 ? formatCOP(data.costPerClick) : '—' },
                  { label: 'ROAS', value: data.cost > 0 ? `${data.roas.toLocaleString('es-CO', { maximumFractionDigits: 2 })}×` : '—', hint: 'Ingresos ÷ costo' },
                ]}
              />

              {empty ? (
                <EmptyState
                  icon={<BarChart3Icon />}
                  title="Todavía no hay datos en este periodo"
                  description="Las estadísticas aparecen cuando la campaña está activa y los compradores ven tus anuncios. Si acabas de pagar, vuelve en unos minutos."
                />
              ) : (
                <>
                  <ChartSection
                    title="¿Cuántas veces se vio tu anuncio cada día?"
                    meta={`${formatDay(data.from)} al ${formatDay(data.to)} · impresiones`}
                    source="Fuente: eventos de impresión de Autopartco. Cifras estimadas."
                  >
                    <AnalyticsChart mode="bars" points={points} label="Impresiones diarias" formatAxis={formatNumber} tableHeaders={['Día', 'Impresiones']} />
                  </ChartSection>
                  <section aria-labelledby="stats-daily" className="flex flex-col gap-3">
                    <h2 id="stats-daily" className="text-block-title text-ap-ink">Por día</h2>
                    <DataTable caption="Resultados por día" columns={DAILY_COLUMNS} rows={data.daily} getRowId={(row) => row.day} defaultSort={{ columnId: 'day', direction: 'desc' }} pageSize={10} />
                  </section>
                  <section aria-labelledby="stats-placement" className="flex flex-col gap-3">
                    <h2 id="stats-placement" className="text-block-title text-ap-ink">Por ubicación</h2>
                    <DataTable caption="Resultados por ubicación" columns={PLACEMENT_COLUMNS} rows={data.byPlacement} getRowId={(row) => row.placement} defaultSort={{ columnId: 'impressions', direction: 'desc' }} />
                  </section>
                  <section aria-labelledby="stats-product" className="flex flex-col gap-3">
                    <h2 id="stats-product" className="text-block-title text-ap-ink">Por producto</h2>
                    <DataTable caption="Resultados por producto" columns={PRODUCT_COLUMNS} rows={data.byProduct} getRowId={(row) => row.productId} defaultSort={{ columnId: 'impressions', direction: 'desc' }} pageSize={10} />
                  </section>
                </>
              )}
            </div>
          )
        }}
      </QueryBoundary>
    </>
  )
}
