import { useMemo } from 'react'
import { Link, useSearchParams } from 'react-router'
import { EyeIcon } from 'lucide-react'
import { DataTable, type DataColumn } from '@/components/common/data-table'
import { EmptyState } from '@/components/common/states'
import { formatNumber } from '@/lib/format'
import { getAnalyticsService } from '../api/analytics-service'
import type { TrafficSource, VisibilityReport } from '../api/analytics-types'
import { AnalyticsChart, type ChartPoint } from '../components/analytics-chart'
import { ChartEmpty, ChartSection, ChartSkeleton } from '../components/chart-section'
import { SelectField } from '../components/form-controls'
import { IntelligenceTabs } from '../components/intelligence-shell'
import { PageHeader } from '../components/page-header'
import { QueryBoundary } from '../components/query-boundary'
import { useStoreQuery } from '../hooks'
import { dateLabel, isRangePreset, RANGE_PRESETS, resolveRange, type RangePreset } from '../lib/analytics'

const SOURCE_LABELS: Record<TrafficSource, string> = {
  WHATSAPP: 'WhatsApp',
  FACEBOOK: 'Facebook',
  INSTAGRAM: 'Instagram',
  TIKTOK: 'TikTok',
  X: 'X (Twitter)',
  TELEGRAM: 'Telegram',
  CHATGPT: 'ChatGPT',
  OTHER_AI: 'Otros asistentes de IA',
  GOOGLE: 'Google',
  OTHER_SEARCH: 'Otros buscadores',
  COPY_LINK: 'Enlace copiado',
  INTERNAL: 'Dentro de Autopartco',
  DIRECT: 'Directo o sin origen',
  OTHER: 'Otros sitios',
}

/** Sin «personalizado»: la visibilidad se revisa por periodos cortos y comparables. */
const PRESETS = RANGE_PRESETS.filter((preset) => preset.value !== 'personalizado')

const percent = new Intl.NumberFormat('es-CO', { style: 'percent', maximumFractionDigits: 0 })
const dayShort = new Intl.DateTimeFormat('es-CO', { day: 'numeric', month: 'short' })

function shortDay(value: string): string {
  const [year, month, day] = value.split('-').map(Number)
  return dayShort.format(new Date(year ?? 1970, (month ?? 1) - 1, day ?? 1))
}

type SourceRow = VisibilityReport['bySource'][number] & { share: number }
type ProductRow = VisibilityReport['topProducts'][number]

const SOURCE_COLUMNS: DataColumn<SourceRow>[] = [
  { id: 'origen', header: 'Origen', rowHeader: true, cell: (row) => SOURCE_LABELS[row.source] ?? row.source, sortValue: (row) => SOURCE_LABELS[row.source] ?? row.source },
  { id: 'visitas', header: 'Visitas', align: 'end', cell: (row) => formatNumber(row.views), sortValue: (row) => row.views },
  { id: 'participacion', header: '% de visitas', align: 'end', cell: (row) => percent.format(row.share), sortValue: (row) => row.share },
  { id: 'visitantes', header: 'Visitantes diarios', align: 'end', cell: (row) => formatNumber(row.visitors), sortValue: (row) => row.visitors },
  { id: 'compartidos', header: 'Compartidos', align: 'end', cell: (row) => formatNumber(row.shares), sortValue: (row) => row.shares },
]

const PRODUCT_COLUMNS: DataColumn<ProductRow>[] = [
  {
    id: 'producto',
    header: 'Publicación',
    rowHeader: true,
    cell: (row) => (
      <Link to={`/producto/${encodeURIComponent(row.productId)}`} target="_blank" rel="noopener" className="font-medium underline-offset-4 hover:text-ap-red hover:underline">
        {row.name}
      </Link>
    ),
    sortValue: (row) => row.name,
  },
  { id: 'visitas', header: 'Visitas', align: 'end', cell: (row) => formatNumber(row.views), sortValue: (row) => row.views },
  { id: 'visitantes', header: 'Visitantes diarios', align: 'end', cell: (row) => formatNumber(row.visitors), sortValue: (row) => row.visitors },
  { id: 'compartidos', header: 'Compartidos', align: 'end', cell: (row) => formatNumber(row.shares), sortValue: (row) => row.shares },
]

/** Resumen en una fila de datos (no tarjetas KPI, §20.6): cifra, unidad y visitantes. */
function Summary({ report }: { report: VisibilityReport }) {
  const items = [
    { label: 'Visitas a tu perfil', value: report.profileViews, detail: `${formatNumber(report.profileVisitors)} visitantes diarios` },
    { label: 'Visitas a tus publicaciones', value: report.productViews, detail: `${formatNumber(report.productVisitors)} visitantes diarios` },
    { label: 'Veces compartido', value: report.shares, detail: 'Desde el botón Compartir' },
  ]
  return (
    <dl className="grid gap-x-8 gap-y-4 border-y border-ap-line-strong py-4 sm:grid-cols-3">
      {items.map((item) => (
        <div key={item.label} className="flex flex-col gap-1">
          <dt className="ap-index">{item.label}</dt>
          <dd className="ap-price text-3xl leading-none text-ap-ink tabular-nums">{formatNumber(item.value)}</dd>
          <dd className="text-sm text-ap-muted">{item.detail}</dd>
        </div>
      ))}
    </dl>
  )
}

function VisibilityView({ report }: { report: VisibilityReport }) {
  const period = `${dateLabel(report.from)} – ${dateLabel(report.to)}`
  const points = useMemo<ChartPoint[]>(
    () =>
      report.daily.map((row) => ({
        label: shortDay(row.day),
        detail: `${shortDay(row.day)}: ${formatNumber(row.profileViews + row.productViews)} visitas (${formatNumber(row.profileViews)} al perfil, ${formatNumber(row.productViews)} a publicaciones)`,
        bar: row.profileViews + row.productViews,
      })),
    [report.daily],
  )
  const totalViews = report.profileViews + report.productViews
  const sources = useMemo<SourceRow[]>(
    () => report.bySource.map((row) => ({ ...row, share: totalViews > 0 ? row.views / totalViews : 0 })),
    [report.bySource, totalViews],
  )

  if (totalViews === 0 && report.shares === 0) {
    return (
      <EmptyState
        icon={<EyeIcon />}
        title="Aún no hay visitas en este periodo"
        description={`No registramos visitas a tu perfil ni a tus publicaciones entre el ${dateLabel(report.from)} y el ${dateLabel(report.to)}. Comparte tu perfil o tus repuestos con el botón Compartir para empezar a medir de dónde llegan tus compradores.`}
      />
    )
  }

  return (
    <div className="flex flex-col gap-8">
      <Summary report={report} />
      <ChartSection
        title="¿Cuántas visitas recibiste cada día?"
        meta={`${period} · visitas al perfil y a publicaciones por día`}
        source="Fuente: visitas registradas en Autopartco, sin datos personales. El eje parte de 0."
      >
        {points.length > 0 ? (
          <AnalyticsChart mode="bars" points={points} label="Visitas por día" formatAxis={(value) => formatNumber(value)} tableHeaders={['Día', 'Visitas']} />
        ) : (
          <ChartEmpty>No hay días en este periodo.</ChartEmpty>
        )}
      </ChartSection>
      <section aria-labelledby="vis-origen" className="flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4">
        <h2 id="vis-origen" className="text-base font-semibold text-ap-ink">
          ¿De dónde llegan tus visitas?
        </h2>
        <p className="max-w-prose text-sm text-ap-muted">
          El origen es estimado: lo sabemos con certeza cuando la visita llega por un enlace del botón Compartir; en el resto, por la página de la que
          viene la persona. WhatsApp, TikTok e Instagram suelen aparecer como «Directo» si el enlace no se compartió desde Autopartco.
        </p>
        <DataTable caption="Visitas y compartidos por origen" columns={SOURCE_COLUMNS} rows={sources} getRowId={(row) => row.source} defaultSort={{ columnId: 'visitas', direction: 'desc' }} />
      </section>
      <section aria-labelledby="vis-productos" className="flex min-w-0 flex-col gap-3 border-t border-ap-line-strong pt-4">
        <h2 id="vis-productos" className="text-base font-semibold text-ap-ink">
          ¿Qué publicaciones miran más?
        </h2>
        {report.topProducts.length > 0 ? (
          <DataTable
            caption="Publicaciones con más visitas del periodo"
            columns={PRODUCT_COLUMNS}
            rows={report.topProducts}
            getRowId={(row) => row.productId}
            defaultSort={{ columnId: 'visitas', direction: 'desc' }}
          />
        ) : (
          <ChartEmpty>Ninguna publicación recibió visitas en este periodo.</ChartEmpty>
        )}
      </section>
    </div>
  )
}

function VisibilitySkeleton() {
  return (
    <div className="flex flex-col gap-8">
      <ChartSkeleton kind="bars" label="Cargando las visitas" />
      <ChartSkeleton kind="list" label="Cargando el origen de las visitas" />
    </div>
  )
}

/**
 * `/portal/:storeId/inteligencia/visibilidad`: visitas al perfil y a las publicaciones, compartidos y origen.
 * A diferencia del resto de Inteligencia no depende del plan Empresarial: solo exige `ANALYTICS_READ`.
 */
export default function IntelligenceVisibilityPage() {
  const [params, setParams] = useSearchParams()
  const raw = params.get('rango')
  const preset: RangePreset = isRangePreset(raw) && raw !== 'personalizado' ? raw : '30'
  const range = resolveRange(preset, {})
  const result = useStoreQuery(`analytics:visibility:${range.from}:${range.to}`, async (id) => (await getAnalyticsService()).visibility(id, range))

  return (
    <>
      <title>Visibilidad | Inteligencia | Autopartco</title>
      <PageHeader title="Inteligencia" description="Cuántas personas ven tu perfil y tus publicaciones, cuántas veces las comparten y de dónde llegan." />
      <IntelligenceTabs />
      <h2 className="sr-only">Visibilidad</h2>
      <form role="search" aria-label="Periodo de visibilidad" className="mb-6 flex flex-col gap-3 sm:flex-row sm:items-end" onSubmit={(event) => event.preventDefault()}>
        <SelectField
          id="vis-rango"
          label="Periodo"
          value={preset}
          onChange={(event) =>
            setParams(
              (current) => {
                const next = new URLSearchParams(current)
                if (event.target.value === '30') next.delete('rango')
                else next.set('rango', event.target.value)
                return next
              },
              { replace: true },
            )
          }
          options={PRESETS}
          containerClassName="w-full sm:w-48"
        />
      </form>
      <QueryBoundary result={result} what="la visibilidad de tu tienda" keepPrevious skeleton={<VisibilitySkeleton />}>
        {(report) => <VisibilityView report={report} />}
      </QueryBoundary>
    </>
  )
}
